package me.languageplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LanguageCommand implements CommandExecutor, TabCompleter {
    private static final List<String> LANGUAGES = Arrays.asList("english", "hebrew", "french", "spanish");

    private final LanguagePlugin plugin;

    public LanguageCommand(LanguagePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.msg(sender, "only-player", null);
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) {
            plugin.msg(player, "usage-language", null);
            return true;
        }
        String lang = normalize(args[0]);
        if (lang == null) {
            plugin.msg(player, "language-unknown", null);
            return true;
        }
        plugin.setLanguage(player.getUniqueId(), lang);
        plugin.msg(player, "language-set", Collections.singletonMap("language", pretty(lang)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : LANGUAGES) {
            if (option.startsWith(prefix)) {
                out.add(option);
            }
        }
        return out;
    }

    static String normalize(String input) {
        switch (input.toLowerCase(Locale.ROOT)) {
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

    static String pretty(String code) {
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
