package me.adam.specialitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ItemFactory {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final NamespacedKey itemIdKey;
    private final Plugin plugin;

    public ItemFactory(Plugin plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "special_item");
    }

    public NamespacedKey itemIdKey() {
        return itemIdKey;
    }

    public Set<String> itemIds() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("items");
        if (root == null) {
            return Set.of();
        }
        return root.getKeys(false);
    }

    public @Nullable String idFromStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public @Nullable ItemStack create(String id) {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("items");
        if (items == null) {
            return null;
        }
        ConfigurationSection sec = items.getConfigurationSection(id);
        if (sec == null) {
            return null;
        }

        String matName = sec.getString("material", "STONE").toUpperCase(Locale.ROOT);
        Material material;
        try {
            material = Material.valueOf(matName);
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        String display = sec.getString("display-name");
        if (display != null) {
            meta.displayName(MM.deserialize(display));
        }

        List<String> loreLines = sec.getStringList("lore");
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>(loreLines.size());
            for (String line : loreLines) {
                lore.add(MM.deserialize(line));
            }
            meta.lore(lore);
        }

        if (sec.isInt("custom-model-data") || sec.isLong("custom-model-data")) {
            meta.setCustomModelData(sec.getInt("custom-model-data"));
        }
        if (meta instanceof FireworkMeta fireworkMeta && sec.isInt("firework-power")) {
            fireworkMeta.setPower(Math.max(0, sec.getInt("firework-power")));
            meta = fireworkMeta;
        }

        if (sec.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        applyAttributeModifiers(meta, id, sec);

        ConfigurationSection enchSec = sec.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(
                        NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchantment != null) {
                    int level = enchSec.getInt(key, 1);
                    meta.addEnchant(enchantment, Math.max(1, level), true);
                }
            }
        }

        if (sec.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }

        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyAttributeModifiers(ItemMeta meta, String itemId, ConfigurationSection sec) {
        List<?> raw = sec.getList("attribute-modifiers");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        int index = 0;
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object attrObj = map.get("attribute");
            if (attrObj == null) {
                continue;
            }
            String attrKey = String.valueOf(attrObj).trim().toLowerCase(Locale.ROOT);
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attrKey));
            if (attribute == null) {
                continue;
            }
            double amount = 0;
            Object amountObj = map.get("amount");
            if (amountObj instanceof Number n) {
                amount = n.doubleValue();
            }
            String opRaw = "ADD_NUMBER";
            Object opObj = map.get("operation");
            if (opObj != null) {
                opRaw = String.valueOf(opObj).trim().toUpperCase(Locale.ROOT);
            }
            AttributeModifier.Operation operation;
            try {
                operation = AttributeModifier.Operation.valueOf(opRaw);
            } catch (IllegalArgumentException e) {
                operation = AttributeModifier.Operation.ADD_NUMBER;
            }
            EquipmentSlotGroup slotGroup = parseSlotGroup(map.get("slot"));
            NamespacedKey modKey = new NamespacedKey(plugin, itemId + "_attr_" + index + "_" + attrKey.replace('.', '_'));
            index++;
            meta.addAttributeModifier(attribute, new AttributeModifier(modKey, amount, operation, slotGroup));
        }
    }

    private static EquipmentSlotGroup parseSlotGroup(Object slotObj) {
        if (slotObj == null) {
            return EquipmentSlotGroup.CHEST;
        }
        String raw = String.valueOf(slotObj).trim().toLowerCase(Locale.ROOT);
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw);
        return group != null ? group : EquipmentSlotGroup.CHEST;
    }
}
