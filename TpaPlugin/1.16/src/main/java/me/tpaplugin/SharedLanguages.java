package me.tpaplugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public final class SharedLanguages {
    private SharedLanguages() {
    }

    public static String get(JavaPlugin plugin, UUID uuid) {
        File file = new File(plugin.getDataFolder().getParentFile(), "SharedLanguage/player-languages.yml");
        if (!file.exists()) {
            return "en";
        }
        return YamlConfiguration.loadConfiguration(file).getString("players." + uuid, "en");
    }
}
