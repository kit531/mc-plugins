package me.adam.specialitems;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class VoidCookieListener implements Listener {

    private static final String VOID_COOKIE_ID = "void_cookie";

    private final SpecialItemsPlugin plugin;
    private final ItemFactory items;

    public VoidCookieListener(SpecialItemsPlugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String id = items.idFromStack(item);
        if (!VOID_COOKIE_ID.equals(id)) {
            return;
        }

        Player player = event.getPlayer();
        Vector dir = player.getLocation().getDirection().normalize();
        player.setVelocity(dir.multiply(1.35).setY(Math.max(0.25, dir.getY() * 1.35 + 0.15)));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setFallDistance(0f);
            }
        }, 1L);
    }
}
