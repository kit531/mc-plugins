package me.adam.specialitems;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class InfiniteRocketListener implements Listener {
    private static final String INFINITE_ROCKET_ID = "infinite_rocket";
    private static final int ROCKET_COOLDOWN_TICKS = 10; // 0.5s

    private final Plugin plugin;
    private final ItemFactory items;

    public InfiniteRocketListener(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
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
        if (!INFINITE_ROCKET_ID.equals(id)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasCooldown(Material.FIREWORK_ROCKET)) {
            event.setCancelled(true);
            return;
        }
        player.setCooldown(Material.FIREWORK_ROCKET, ROCKET_COOLDOWN_TICKS);

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        int originalAmount = used.getAmount();

        plugin.getServer().getScheduler().runTask(plugin, () -> refillIfConsumed(player, hand, originalAmount));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        String id = items.idFromStack(event.getItemStack());
        if (!INFINITE_ROCKET_ID.equals(id)) {
            return;
        }
        event.setShouldConsume(false);
        event.getPlayer().setCooldown(Material.FIREWORK_ROCKET, ROCKET_COOLDOWN_TICKS);
    }

    private void refillIfConsumed(Player player, EquipmentSlot hand, int originalAmount) {
        ItemStack current = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        String idNow = items.idFromStack(current);

        if (INFINITE_ROCKET_ID.equals(idNow) && current.getAmount() >= originalAmount) {
            return;
        }

        if (INFINITE_ROCKET_ID.equals(idNow) && current.getAmount() < originalAmount) {
            current.setAmount(originalAmount);
            return;
        }

        ItemStack refill = items.create(INFINITE_ROCKET_ID);
        if (refill == null) {
            return;
        }
        if (hand == EquipmentSlot.HAND) {
            player.getInventory().setItemInMainHand(refill);
        } else {
            player.getInventory().setItemInOffHand(refill);
        }
        // Safety: avoid ending with empty hand if replacement failed due to plugins.
        ItemStack verify = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (verify == null || verify.getType() == Material.AIR) {
            player.getInventory().addItem(refill);
        }
    }
}
