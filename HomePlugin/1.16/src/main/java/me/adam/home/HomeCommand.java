package me.adam.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HomeCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String DEFAULT_HOME_NAME = "home";

    private final HomePlugin plugin;
    private final HomeStorage storage;
    private final Map<UUID, Long> lastHomeUseMs = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    public HomeCommand(HomePlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    private static final class PendingTeleport {
        final BukkitTask task;
        final Location startLocation;
        final String homeName;

        PendingTeleport(BukkitTask task, Location startLocation, String homeName) {
            this.task = task;
            this.startLocation = startLocation;
            this.homeName = homeName;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "only-player", null);
            return true;
        }
        Player player = (Player) sender;

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "sethome":
                return handleSetHome(player, args);
            case "home":
                return handleHome(player, args);
            case "listhome":
                return handleListHome(player);
            case "delhome":
                return handleDelHome(player, args);
            case "language":
                return handleLanguage(player, args);
            default:
                return true;
        }
    }

    private boolean handleLanguage(Player player, String[] args) {
        if (args.length < 2 || !"home".equalsIgnoreCase(args[0])) {
            plugin.msg(player, "usage-language", null);
            return true;
        }
        String lang = normalizeLanguage(args[1]);
        if (lang == null) {
            plugin.msg(player, "language-unknown", null);
            return true;
        }
        plugin.setPlayerLanguage(player.getUniqueId(), lang);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("language", prettyLanguage(lang));
        plugin.msg(player, "language-set", placeholders);
        return true;
    }

    private boolean handleSetHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : DEFAULT_HOME_NAME;
        if (!HomeStorage.isValidHomeName(name)) {
            plugin.msg(player, "invalid-home-name", null);
            return true;
        }
        int maxHomes = plugin.getConfig().getInt("max-homes", -1);
        Set<String> homes = storage.listHomes(player.getUniqueId());
        String normalized = HomeStorage.normalizeName(name);

        if (maxHomes >= 0 && !homes.contains(normalized) && homes.size() >= maxHomes) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(maxHomes));
            plugin.msg(player, "home-limit", placeholders);
            return true;
        }

        storage.setHome(player.getUniqueId(), name, player.getLocation());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("home", normalized);
        plugin.msg(player, "home-set", placeholders);
        return true;
    }

    private boolean handleHome(Player player, final String[] args) {
        final String name = args.length > 0 ? args[0] : DEFAULT_HOME_NAME;
        if (!HomeStorage.isValidHomeName(name)) {
            plugin.msg(player, "invalid-home-name", null);
            return true;
        }
        final String normalized = HomeStorage.normalizeName(name);

        Location location = storage.getHome(player.getUniqueId(), normalized);
        if (location == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("home", normalized);
            plugin.msg(player, "home-not-found", placeholders);
            return true;
        }

        int cooldownSeconds = plugin.getConfig().getInt("cooldown-seconds", 0);
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            long last = lastHomeUseMs.getOrDefault(player.getUniqueId(), 0L);
            long elapsed = (now - last) / 1000;
            long remaining = cooldownSeconds - elapsed;
            if (remaining > 0) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("remaining", String.valueOf(remaining));
                plugin.msg(player, "cooldown", placeholders);
                return true;
            }
            lastHomeUseMs.put(player.getUniqueId(), now);
        }

        int delaySeconds = plugin.getConfig().getInt("teleport-delay-seconds", 5);
        Map<String, String> startPlaceholders = new HashMap<>();
        startPlaceholders.put("home", normalized);
        startPlaceholders.put("seconds", String.valueOf(delaySeconds));
        plugin.msg(player, "teleport-start", startPlaceholders);
        cancelPendingTeleport(player.getUniqueId(), false);
        Location start = player.getLocation().clone();
        final Player finalPlayer = player;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!finalPlayer.isOnline()) {
                    return;
                }
                PendingTeleport pending = pendingTeleports.remove(finalPlayer.getUniqueId());
                if (pending == null) {
                    return;
                }
                Location latest = storage.getHome(finalPlayer.getUniqueId(), normalized);
                if (latest == null) {
                    Map<String, String> p = new HashMap<>();
                    p.put("home", normalized);
                    plugin.msg(finalPlayer, "home-not-found", p);
                    return;
                }
                finalPlayer.teleport(latest);
                Map<String, String> p = new HashMap<>();
                p.put("home", normalized);
                plugin.msg(finalPlayer, "teleported", p);
            }
        }, Math.max(0, delaySeconds) * 20L);
        pendingTeleports.put(player.getUniqueId(), new PendingTeleport(task, start, normalized));

        return true;
    }

    private boolean handleListHome(Player player) {
        Set<String> homes = storage.listHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.msg(player, "home-list-empty", null);
            return true;
        }
        List<String> sorted = new ArrayList<>(homes);
        Collections.sort(sorted);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("list", String.join(", ", sorted));
        plugin.msg(player, "home-list", placeholders);
        return true;
    }

    private boolean handleDelHome(Player player, String[] args) {
        if (args.length == 0) {
            plugin.msg(player, "usage-delhome", null);
            return true;
        }
        if (!HomeStorage.isValidHomeName(args[0])) {
            plugin.msg(player, "invalid-home-name", null);
            return true;
        }
        String normalized = HomeStorage.normalizeName(args[0]);
        boolean deleted = storage.deleteHome(player.getUniqueId(), normalized);
        if (!deleted) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("home", normalized);
            plugin.msg(player, "home-not-found", placeholders);
            return true;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("home", normalized);
        plugin.msg(player, "home-deleted", placeholders);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("language")) {
            if (args.length == 1) {
                return partial(Collections.singletonList("home"), args[0]);
            }
            if (args.length == 2 && "home".equalsIgnoreCase(args[0])) {
                return partial(Arrays.asList("english", "hebrew", "french", "spanish"), args[1]);
            }
            return Collections.emptyList();
        }
        if ((cmd.equals("home") || cmd.equals("delhome")) && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String home : storage.listHomes(player.getUniqueId())) {
                if (home.startsWith(prefix)) {
                    out.add(home);
                }
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PendingTeleport pending = pendingTeleports.get(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        cancelPendingTeleport(event.getPlayer().getUniqueId(), true);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("home", pending.homeName);
        plugin.msg(event.getPlayer(), "teleport-cancelled-move", placeholders);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPendingTeleport(event.getPlayer().getUniqueId(), false);
    }

    private void cancelPendingTeleport(UUID uuid, boolean cancelTask) {
        PendingTeleport pending = pendingTeleports.remove(uuid);
        if (pending == null) {
            return;
        }
        if (cancelTask) {
            pending.task.cancel();
        }
    }

    private static List<String> partial(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(normalized)) {
                out.add(option);
            }
        }
        return out;
    }

    private static String normalizeLanguage(String input) {
        String raw = input.toLowerCase(Locale.ROOT);
        switch (raw) {
            case "english":
            case "en":
                return "en";
            case "hebrew":
            case "he":
            case "ivrit":
                return "he";
            case "french":
            case "fr":
                return "fr";
            case "spanish":
            case "es":
                return "es";
            default:
                return null;
        }
    }

    private static String prettyLanguage(String code) {
        switch (code) {
            case "he":
                return "Hebrew";
            case "fr":
                return "French";
            case "es":
                return "Spanish";
            default:
                return "English";
        }
    }
}
