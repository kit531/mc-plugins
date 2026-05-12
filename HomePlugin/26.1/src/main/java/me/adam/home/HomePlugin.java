package me.adam.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class HomePlugin extends JavaPlugin {
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    private HomeStorage storage;
    private LanguageStorage languageStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.storage = new HomeStorage(this);
        this.languageStorage = new LanguageStorage(this);

        HomeCommand commandHandler = new HomeCommand(this, storage);
        getServer().getPluginManager().registerEvents(commandHandler, this);
        getCommand("home").setExecutor(commandHandler);
        getCommand("home").setTabCompleter(commandHandler);
        getCommand("sethome").setExecutor(commandHandler);
        getCommand("sethome").setTabCompleter(commandHandler);
        getCommand("listhome").setExecutor(commandHandler);
        getCommand("listhome").setTabCompleter(commandHandler);
        getCommand("delhome").setExecutor(commandHandler);
        getCommand("delhome").setTabCompleter(commandHandler);
        getCommand("language").setExecutor(commandHandler);
        getCommand("language").setTabCompleter(commandHandler);
    }

    public void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        String lang = "en";
        if (sender instanceof Player player) {
            lang = getPlayerLanguage(player.getUniqueId());
        }
        String prefix = Translations.resolve(lang, "prefix");
        String body = Translations.resolve(lang, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                body = body.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        Component message = LEGACY_AMPERSAND.deserialize((prefix == null ? "" : prefix) + (body == null ? "" : body));
        sender.sendMessage(message);
    }

    public String getPlayerLanguage(UUID uuid) {
        return languageStorage.getLanguage(uuid, "en");
    }

    public void setPlayerLanguage(UUID uuid, String langCode) {
        languageStorage.setLanguage(uuid, langCode);
    }
}
