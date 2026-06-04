package me.homeplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class HomePlugin extends JavaPlugin {
    private HomeStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.storage = new HomeStorage(this);

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
    }

    public void msg(CommandSender sender, String key, Map<String, String> placeholders) {
        String lang = "en";
        UUID playerId = null;
        if (sender instanceof Player) {
            Player player = (Player) sender;
            playerId = player.getUniqueId();
            lang = getPlayerLanguage(playerId);
        }
        String prefix = color(TranslationBridge.resolve(this, playerId, lang, "prefix"));
        String body = color(TranslationBridge.resolve(this, playerId, lang, key));
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                body = body.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        sender.sendMessage(prefix + body);
    }

    public String getPlayerLanguage(UUID uuid) {
        return SharedLanguages.get(this, uuid);
    }

    private static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
