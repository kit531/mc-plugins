package me.adam.tpa;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class TpaPlugin extends JavaPlugin {
    private LanguageStorage languageStorage;
    private TpaRequestManager requestManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.languageStorage = new LanguageStorage(this);
        this.requestManager = new TpaRequestManager(this);

        TpaCommand commandHandler = new TpaCommand(this, requestManager);
        getServer().getPluginManager().registerEvents(commandHandler, this);
        getCommand("tpa").setExecutor(commandHandler);
        getCommand("tpa").setTabCompleter(commandHandler);
        getCommand("tpaccept").setExecutor(commandHandler);
        getCommand("tpdeny").setExecutor(commandHandler);
        getCommand("tpacancel").setExecutor(commandHandler);
        getCommand("language").setExecutor(commandHandler);
        getCommand("language").setTabCompleter(commandHandler);
    }

    public void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        String lang = "en";
        if (sender instanceof Player) {
            Player player = (Player) sender;
            lang = getPlayerLanguage(player.getUniqueId());
        }
        String prefix = color(Translations.resolve(lang, "prefix"));
        String body = color(Translations.resolve(lang, key));
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                body = body.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        sender.sendMessage(prefix + body);
    }

    public String getPlayerLanguage(UUID uuid) {
        return languageStorage.getLanguage(uuid, "en");
    }

    public void setPlayerLanguage(UUID uuid, String langCode) {
        languageStorage.setLanguage(uuid, langCode);
    }

    private static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
