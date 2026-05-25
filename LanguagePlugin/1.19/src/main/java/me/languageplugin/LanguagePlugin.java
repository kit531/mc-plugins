package me.languageplugin;

import me.languageplugin.api.LanguageAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import java.util.Map;
import java.util.UUID;

public final class LanguagePlugin extends JavaPlugin implements LanguageAPI {
    private LanguageStorage storage;

    @Override
    public void onEnable() {
        this.storage = new LanguageStorage(this);

        LanguageCommand handler = new LanguageCommand(this);
        getCommand("language").setExecutor(handler);
        getCommand("language").setTabCompleter(handler);

        getServer().getServicesManager().register(LanguageAPI.class, this, this, ServicePriority.Normal);
    }

    public void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        String lang = "en";
        if (sender instanceof Player) {
            lang = getLanguage(((Player) sender).getUniqueId());
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

    @Override
    public String getLanguage(UUID playerId) {
        return storage.getLanguage(playerId, "en");
    }

    @Override
    public void setLanguage(UUID playerId, String languageCode) {
        storage.setLanguage(playerId, languageCode);
    }

    private static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
