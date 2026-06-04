package me.tpaplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public final class TpaPlugin extends JavaPlugin {
    private TpaRequestManager requestManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.requestManager = new TpaRequestManager(this);

        TpaCommand commandHandler = new TpaCommand(this, requestManager);
        getServer().getPluginManager().registerEvents(commandHandler, this);
        getCommand("tpa").setExecutor(commandHandler);
        getCommand("tpa").setTabCompleter(commandHandler);
        getCommand("tpahere").setExecutor(commandHandler);
        getCommand("tpahere").setTabCompleter(commandHandler);
        getCommand("tpaccept").setExecutor(commandHandler);
        getCommand("tpdeny").setExecutor(commandHandler);
        getCommand("tpacancel").setExecutor(commandHandler);
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
