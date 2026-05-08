package me.adam.home;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HomeCommand implements CommandExecutor, TabCompleter {
    private static final String DEFAULT_HOME_NAME = "home";

    private final HomePlugin plugin;
    private final HomeStorage storage;
    private final Map<UUID, Long> lastHomeUseMs = new HashMap<>();

    public HomeCommand(HomePlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg(sender, "only-player", null);
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        return switch (cmd) {
            case "sethome" -> handleSetHome(player, args);
            case "home" -> handleHome(player, args);
            case "listhome" -> handleListHome(player);
            case "delhome" -> handleDelHome(player, args);
            case "language" -> handleLanguage(player, args);
            default -> true;
        };
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
        plugin.msg(player, "language-set", Map.of("language", prettyLanguage(lang)));
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
            plugin.msg(player, "home-limit", Map.of("max", String.valueOf(maxHomes)));
            return true;
        }

        storage.setHome(player.getUniqueId(), name, player.getLocation());
        plugin.msg(player, "home-set", Map.of("home", normalized));
        return true;
    }

    private boolean handleHome(Player player, String[] args) {
        String name = args.length > 0 ? args[0] : DEFAULT_HOME_NAME;
        if (!HomeStorage.isValidHomeName(name)) {
            plugin.msg(player, "invalid-home-name", null);
            return true;
        }
        String normalized = HomeStorage.normalizeName(name);

        var location = storage.getHome(player.getUniqueId(), normalized);
        if (location == null) {
            plugin.msg(player, "home-not-found", Map.of("home", normalized));
            return true;
        }

        int cooldownSeconds = plugin.getConfig().getInt("cooldown-seconds", 0);
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            long last = lastHomeUseMs.getOrDefault(player.getUniqueId(), 0L);
            long elapsed = (now - last) / 1000;
            long remaining = cooldownSeconds - elapsed;
            if (remaining > 0) {
                plugin.msg(player, "cooldown", Map.of("remaining", String.valueOf(remaining)));
                return true;
            }
            lastHomeUseMs.put(player.getUniqueId(), now);
        }

        int delaySeconds = plugin.getConfig().getInt("teleport-delay-seconds", 5);
        plugin.msg(player, "teleport-start", Map.of("home", normalized, "seconds", String.valueOf(delaySeconds)));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            var latest = storage.getHome(player.getUniqueId(), normalized);
            if (latest == null) {
                plugin.msg(player, "home-not-found", Map.of("home", normalized));
                return;
            }
            player.teleport(latest);
            plugin.msg(player, "teleported", Map.of("home", normalized));
        }, Math.max(0, delaySeconds) * 20L);

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
        plugin.msg(player, "home-list", Map.of("list", String.join(", ", sorted)));
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
            plugin.msg(player, "home-not-found", Map.of("home", normalized));
            return true;
        }
        plugin.msg(player, "home-deleted", Map.of("home", normalized));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("language")) {
            if (args.length == 1) {
                return partial(List.of("home"), args[0]);
            }
            if (args.length == 2 && "home".equalsIgnoreCase(args[0])) {
                return partial(List.of("english", "hebrew", "french", "spanish"), args[1]);
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
        return switch (raw) {
            case "english", "en" -> "en";
            case "hebrew", "he", "ivrit" -> "he";
            case "french", "fr" -> "fr";
            case "spanish", "es" -> "es";
            default -> null;
        };
    }

    private static String prettyLanguage(String code) {
        return switch (code) {
            case "he" -> "Hebrew";
            case "fr" -> "French";
            case "es" -> "Spanish";
            default -> "English";
        };
    }
}
