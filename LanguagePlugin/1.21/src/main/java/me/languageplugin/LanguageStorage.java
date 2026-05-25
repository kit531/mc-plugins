package me.languageplugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class LanguageStorage {
    private static final String PLAYERS_PATH = "players.";

    private final Plugin plugin;
    private final File file;

    public LanguageStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = sharedFile(plugin);
        ensureParentExists(file.getParentFile());
        migrateLegacy(plugin, file);
    }

    public static File sharedFile(Plugin plugin) {
        return new File(plugin.getDataFolder().getParentFile(), "SharedLanguage/player-languages.yml");
    }

    public synchronized String getLanguage(UUID uuid, String defaultLang) {
        return load().getString(PLAYERS_PATH + uuid, defaultLang);
    }

    public synchronized void setLanguage(UUID uuid, String languageCode) {
        FileConfiguration data = load();
        data.set(PLAYERS_PATH + uuid, languageCode);
        save(data);
    }

    private FileConfiguration load() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void save(FileConfiguration data) {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player-languages.yml: " + e.getMessage());
        }
    }

    private static void ensureParentExists(File dir) {
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    private static void migrateLegacy(Plugin plugin, File target) {
        if (target.exists()) {
            return;
        }
        File pluginsDir = plugin.getDataFolder().getParentFile();
        YamlConfiguration merged = new YamlConfiguration();
        merge(merged, new File(pluginsDir, "HomePlugin/player-languages.yml"));
        merge(merged, new File(pluginsDir, "TpaPlugin/player-languages.yml"));
        if (!merged.isConfigurationSection("players")) {
            return;
        }
        ensureParentExists(target.getParentFile());
        try {
            merged.save(target);
            plugin.getLogger().info("Migrated languages to " + target.getPath());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate languages: " + e.getMessage());
        }
    }

    private static void merge(YamlConfiguration merged, File legacy) {
        if (!legacy.exists()) {
            return;
        }
        YamlConfiguration old = YamlConfiguration.loadConfiguration(legacy);
        ConfigurationSection players = old.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuid : players.getKeys(false)) {
            merged.set(PLAYERS_PATH + uuid, players.getString(uuid));
        }
    }
}
