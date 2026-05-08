package me.adam.specialitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MasterSwordListener implements Listener {
    private static final String MASTER_SWORD_ID = "master_sword";
    private static final int MAX_STRENGTH_LEVEL = 5;
    private static final int STRENGTH_DURATION_TICKS = 20 * 4;
    private static final int MAX_REACH_BONUS = 5;

    private final Plugin plugin;
    private final ItemFactory items;
    private final NamespacedKey modeKey;
    private final NamespacedKey reachModKey;
    private final Map<UUID, Integer> streak = new HashMap<>();

    private enum Mode {
        DAMAGE,
        REACH
    }

    public MasterSwordListener(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
        this.modeKey = new NamespacedKey(plugin, "master_sword_mode");
        this.reachModKey = new NamespacedKey(plugin, "master_sword_reach");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggle(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        ItemStack used = event.getPlayer().getInventory().getItemInMainHand();
        if (!isMasterSword(used)) {
            return;
        }
        event.setCancelled(true);
        ItemStack handItem = used.clone();
        Mode next = toggleMode(handItem);
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
            event.getPlayer().getInventory().setItemInMainHand(handItem);
        } else {
            event.getPlayer().getInventory().setItemInOffHand(handItem);
        }
        resetPlayerState(event.getPlayer(), true);
        if (next == Mode.DAMAGE) {
            event.getPlayer().sendMessage(Component.text("Master Sword: מצב דמג' כפול מצטבר", NamedTextColor.RED));
        } else {
            event.getPlayer().sendMessage(Component.text("Master Sword: מצב טווח מצטבר", NamedTextColor.AQUA));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isMasterSword(hand)) {
            return;
        }

        Mode mode = getMode(hand);
        int level = Math.min(streak.getOrDefault(player.getUniqueId(), 0) + 1, MAX_REACH_BONUS);
        streak.put(player.getUniqueId(), level);

        if (mode == Mode.DAMAGE) {
            int strengthLevel = Math.min(level, MAX_STRENGTH_LEVEL);
            // amplifier 0 => Strength I, amplifier 1 => Strength II, etc.
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.STRENGTH, STRENGTH_DURATION_TICKS, strengthLevel - 1, true, false, true));
            player.sendActionBar(Component.text("כוח תקיפה +" + strengthLevel, NamedTextColor.RED));
            return;
        }

        applyReachBonus(player, level);
        player.sendActionBar(Component.text("+" + level + " טווח", NamedTextColor.AQUA));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onGetHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) {
            return;
        }
        if (!streak.containsKey(player.getUniqueId())) {
            return;
        }
        resetPlayerState(player, false);
        player.sendActionBar(Component.text("Master Sword התאפס", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resetPlayerState(event.getPlayer(), true);
    }

    private boolean isMasterSword(ItemStack stack) {
        String id = items.idFromStack(stack);
        return MASTER_SWORD_ID.equals(id);
    }

    private Mode getMode(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) {
            return Mode.DAMAGE;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(modeKey, PersistentDataType.STRING);
        if ("reach".equalsIgnoreCase(raw)) {
            return Mode.REACH;
        }
        return Mode.DAMAGE;
    }

    private Mode toggleMode(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta == null) {
            return Mode.DAMAGE;
        }
        Mode next = getMode(stack) == Mode.DAMAGE ? Mode.REACH : Mode.DAMAGE;
        meta.getPersistentDataContainer().set(
                modeKey, PersistentDataType.STRING, next == Mode.DAMAGE ? "damage" : "reach");
        stack.setItemMeta(meta);
        return next;
    }

    private void applyReachBonus(Player player, int bonusBlocks) {
        var attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null) {
            return;
        }
        removeReachBonus(player);
        int bonus = Math.min(bonusBlocks, MAX_REACH_BONUS);
        AttributeModifier modifier = new AttributeModifier(
                reachModKey, bonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        attr.addModifier(modifier);
    }

    private void removeReachBonus(Player player) {
        var attr = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null) {
            return;
        }
        AttributeModifier existing = attr.getModifier(reachModKey);
        if (existing != null) {
            attr.removeModifier(existing);
        }
    }

    private void resetPlayerState(Player player, boolean clearAlways) {
        UUID uuid = player.getUniqueId();
        if (!clearAlways && !streak.containsKey(uuid)) {
            return;
        }
        streak.remove(uuid);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        removeReachBonus(player);
    }
}
