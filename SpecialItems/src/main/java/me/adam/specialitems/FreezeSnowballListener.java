package me.adam.specialitems;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FreezeSnowballListener implements Listener {
    private static final String FREEZE_SNOWBALL_ID = "freeze_snowball";
    private static final int FREEZE_TICKS = 20 * 30;

    private final Plugin plugin;
    private final ItemFactory items;
    private final Map<UUID, BukkitTask> releaseTasks = new HashMap<>();
    private final Map<UUID, Float> originalWalkSpeed = new HashMap<>();
    private final Map<UUID, Boolean> originalAllowFlight = new HashMap<>();

    public FreezeSnowballListener(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack used = event.getItem();
        if (used == null) {
            return;
        }
        String id = items.idFromStack(used);
        if (!FREEZE_SNOWBALL_ID.equals(id)) {
            return;
        }

        // Block throwing entirely for this special snowball.
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrowableProjectile throwable)) {
            return;
        }
        String id = items.idFromStack(throwable.getItem());
        if (!FREEZE_SNOWBALL_ID.equals(id)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (event.getDamager() instanceof Player attacker) {
            String main = items.idFromStack(attacker.getInventory().getItemInMainHand());
            String off = items.idFromStack(attacker.getInventory().getItemInOffHand());
            if (FREEZE_SNOWBALL_ID.equals(main) || FREEZE_SNOWBALL_ID.equals(off)) {
                freezePlayer(target);
                return;
            }
        }
        if (!releaseTasks.containsKey(target.getUniqueId())) {
            return;
        }
        if (event.getDamager() instanceof Player || event.getDamager() instanceof Projectile) {
            unfreeze(target);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrowableProjectile throwable)) {
            return;
        }
        String id = items.idFromStack(throwable.getItem());
        if (!FREEZE_SNOWBALL_ID.equals(id)) {
            return;
        }
        Entity hit = event.getHitEntity();
        if (hit instanceof Player player) {
            freezePlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!releaseTasks.containsKey(player.getUniqueId())) {
            return;
        }
        if (event.getFrom().getY() < event.getTo().getY()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (releaseTasks.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask old = releaseTasks.remove(uuid);
        if (old != null) {
            old.cancel();
        }

        originalWalkSpeed.putIfAbsent(uuid, player.getWalkSpeed());
        originalAllowFlight.putIfAbsent(uuid, player.getAllowFlight());
        player.setWalkSpeed(0f);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFreezeTicks(FREEZE_TICKS);
        player.sendMessage("§bהוקפאת ל-30 שניות. פגיעה תשחרר אותך.");

        BukkitTask release = plugin.getServer().getScheduler().runTaskLater(plugin, () -> unfreeze(player), FREEZE_TICKS);
        releaseTasks.put(uuid, release);
    }

    private void unfreeze(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = releaseTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        Float speed = originalWalkSpeed.remove(uuid);
        if (speed != null) {
            player.setWalkSpeed(speed);
        }
        Boolean allowFlight = originalAllowFlight.remove(uuid);
        if (allowFlight != null) {
            player.setAllowFlight(allowFlight);
        }
        player.setFreezeTicks(0);
        player.sendMessage("§aההקפאה הוסרה.");
    }
}
