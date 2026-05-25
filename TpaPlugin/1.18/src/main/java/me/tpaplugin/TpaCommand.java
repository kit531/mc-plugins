package me.tpaplugin;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TpaCommand implements CommandExecutor, TabCompleter, Listener {
    private final TpaPlugin plugin;
    private final TpaRequestManager requestManager;
    private final Map<UUID, Long> lastTpaUseMs = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    public TpaCommand(TpaPlugin plugin, TpaRequestManager requestManager) {
        this.plugin = plugin;
        this.requestManager = requestManager;
    }

    private static final class PendingTeleport {
        private final BukkitTask task;
        private final Location startLocation;
        private final String targetName;

        private PendingTeleport(BukkitTask task, Location startLocation, String targetName) {
            this.task = task;
            this.startLocation = startLocation;
            this.targetName = targetName;
        }

        private BukkitTask task() {
            return task;
        }

        private Location startLocation() {
            return startLocation;
        }

        private String targetName() {
            return targetName;
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
            case "tpa":
                return handleTpa(player, args, TpaRequestManager.RequestType.TO_TARGET);
            case "tpahere":
                return handleTpa(player, args, TpaRequestManager.RequestType.TO_SENDER);
            case "tpaccept":
                return handleTpAccept(player);
            case "tpdeny":
                return handleTpDeny(player);
            case "tpacancel":
                return handleTpCancel(player);
            default:
                return true;
        }
    }

    private boolean handleTpa(Player sender, String[] args, TpaRequestManager.RequestType type) {
        if (args.length < 1) {
            plugin.msg(sender, type == TpaRequestManager.RequestType.TO_TARGET ? "usage-tpa" : "usage-tpahere", null);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.msg(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            plugin.msg(sender, "tpa-self", null);
            return true;
        }

        int cooldownSeconds = plugin.getConfig().getInt("tpa-cooldown-seconds", 0);
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            long last = lastTpaUseMs.getOrDefault(sender.getUniqueId(), 0L);
            long elapsed = (now - last) / 1000;
            long remaining = cooldownSeconds - elapsed;
            if (remaining > 0) {
                plugin.msg(sender, "cooldown", Map.of("remaining", String.valueOf(remaining)));
                return true;
            }
            lastTpaUseMs.put(sender.getUniqueId(), now);
        }

        if (requestManager.hasOutgoing(sender.getUniqueId())) {
            plugin.msg(sender, "tpa-already-sent", null);
            return true;
        }

        requestManager.putRequest(sender.getUniqueId(), target.getUniqueId(), type);
        if (type == TpaRequestManager.RequestType.TO_TARGET) {
            plugin.msg(sender, "tpa-sent", Map.of("player", target.getName()));
            plugin.msg(target, "tpa-received", Map.of("player", sender.getName()));
        } else {
            plugin.msg(sender, "tpahere-sent", Map.of("player", target.getName()));
            plugin.msg(target, "tpahere-received", Map.of("player", sender.getName()));
        }
        return true;
    }

    private boolean handleTpAccept(Player target) {
        TpaRequestManager.TpaRequest request = requestManager.getIncoming(target.getUniqueId());
        if (request == null) {
            plugin.msg(target, "tpa-none-incoming", null);
            return true;
        }

        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null || !sender.isOnline()) {
            requestManager.removeIncoming(target.getUniqueId());
            plugin.msg(target, "player-offline", Map.of("player", "sender"));
            return true;
        }

        requestManager.removeIncoming(target.getUniqueId());
        if (request.type() == TpaRequestManager.RequestType.TO_TARGET) {
            plugin.msg(target, "tpa-accepted-target", Map.of("player", sender.getName()));
            plugin.msg(sender, "tpa-accepted-sender", Map.of("player", target.getName()));
            startTeleport(sender, target);
        } else {
            plugin.msg(target, "tpahere-accepted-target", Map.of("player", sender.getName()));
            plugin.msg(sender, "tpahere-accepted-sender", Map.of("player", target.getName()));
            startTeleport(target, sender);
        }
        return true;
    }

    private boolean handleTpDeny(Player target) {
        TpaRequestManager.TpaRequest request = requestManager.removeIncoming(target.getUniqueId());
        if (request == null) {
            plugin.msg(target, "tpa-none-incoming", null);
            return true;
        }

        Player sender = Bukkit.getPlayer(request.sender());
        plugin.msg(target, "tpa-denied-target", null);
        if (sender != null && sender.isOnline()) {
            plugin.msg(sender, "tpa-denied-sender", Map.of("player", target.getName()));
        }
        return true;
    }

    private boolean handleTpCancel(Player sender) {
        TpaRequestManager.TpaRequest request = requestManager.removeOutgoing(sender.getUniqueId());
        if (request == null) {
            plugin.msg(sender, "tpa-none-outgoing", null);
            return true;
        }

        Player target = Bukkit.getPlayer(request.target());
        plugin.msg(sender, "tpa-cancelled-sender", null);
        if (target != null && target.isOnline()) {
            plugin.msg(target, "tpa-cancelled-target", Map.of("player", sender.getName()));
        }
        return true;
    }

    private void startTeleport(Player teleporter, Player destination) {
        int delaySeconds = plugin.getConfig().getInt("teleport-delay-seconds", 5);
        plugin.msg(teleporter, "teleport-start",
                Map.of("player", destination.getName(), "seconds", String.valueOf(delaySeconds)));
        cancelPendingTeleport(teleporter.getUniqueId(), false);

        Location start = teleporter.getLocation().clone();
        String destName = destination.getName();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!teleporter.isOnline()) {
                return;
            }
            PendingTeleport pending = pendingTeleports.remove(teleporter.getUniqueId());
            if (pending == null) {
                return;
            }
            Player dest = Bukkit.getPlayerExact(destName);
            if (dest == null || !dest.isOnline()) {
                plugin.msg(teleporter, "player-not-found", Map.of("player", destName));
                return;
            }
            teleporter.teleport(dest.getLocation());
            plugin.msg(teleporter, "teleported", Map.of("player", dest.getName()));
        }, Math.max(0, delaySeconds) * 20L);

        pendingTeleports.put(teleporter.getUniqueId(), new PendingTeleport(task, start, destName));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if ((cmd.equals("tpa") || cmd.equals("tpahere")) && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(online.getName());
                }
            }
            Collections.sort(out);
            return out;
        }
        return Collections.emptyList();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("cancel-teleport-on-move", true)) {
            return;
        }
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
        plugin.msg(event.getPlayer(), "teleport-cancelled-move",
                Map.of("player", pending.targetName()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelPendingTeleport(uuid, false);
        requestManager.clearPlayer(uuid);
    }

    private void cancelPendingTeleport(UUID uuid, boolean cancelTask) {
        PendingTeleport pending = pendingTeleports.remove(uuid);
        if (pending == null) {
            return;
        }
        if (cancelTask) {
            pending.task().cancel();
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

}
