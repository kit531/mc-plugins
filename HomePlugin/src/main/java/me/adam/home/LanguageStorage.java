package me.adam.home;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class LanguageStorage {
    private final Plugin plugin;
    private final File file;
    private final FileConfiguration data;

    public LanguageStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-languages.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized String getLanguage(UUID uuid, String defaultLang) {
        return data.getString("players." + uuid, defaultLang);
    }

    public synchronized void setLanguage(UUID uuid, String languageCode) {
        data.set("players." + uuid, languageCode);
        saveNow();
    }

    private void saveNow() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save player-languages.yml: " + e.getMessage());
        }
    }
}
