package me.adam.specialitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class SpecialItemsCommand implements CommandExecutor, TabCompleter {

    private final SpecialItemsPlugin plugin;
    private final ItemFactory items;

    public SpecialItemsCommand(SpecialItemsPlugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("שימוש: /" + label + " list|give <item> [שחקן]", NamedTextColor.GRAY));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(sub)) {
            var ids = items.itemIds();
            if (ids.isEmpty()) {
                sender.sendMessage(Component.text("אין אייטמים מוגדרים ב-config.yml.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("אייטמים מיוחדים: " + String.join(", ", ids), NamedTextColor.AQUA));
            return true;
        }

        if ("give".equals(sub)) {
            if (!sender.hasPermission("specialitems.give")) {
                sender.sendMessage(Component.text("אין הרשאה.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("שימוש: /" + label + " give <item> [שחקן]", NamedTextColor.GRAY));
                return true;
            }
            String itemId = args[1].toLowerCase(Locale.ROOT);
            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("שחקן לא נמצא.", NamedTextColor.RED));
                    return true;
                }
            } else {
                if (!(sender instanceof Player self)) {
                    sender.sendMessage(Component.text("ציין שחקן מהקונסול.", NamedTextColor.RED));
                    return true;
                }
                target = self;
            }

            ItemStack stack = items.create(itemId);
            if (stack == null) {
                sender.sendMessage(Component.text("אייטם לא קיים: " + itemId, NamedTextColor.RED));
                return true;
            }

            var leftover = target.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), drop);
            }
            sender.sendMessage(Component.text("ניתן " + itemId + " ל־" + target.getName(), NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("פקודה לא ידועה.", NamedTextColor.RED));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return partial(List.of("list", "give"), args[0]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return partial(new ArrayList<>(items.itemIds()), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0]) && sender.hasPermission("specialitems.give")) {
            return partial(
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()),
                    args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> partial(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
