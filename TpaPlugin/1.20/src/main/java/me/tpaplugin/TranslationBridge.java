package me.tpaplugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Resolves messages from LanguagePlugin remote pack when available, else embedded defaults.
 */
public final class TranslationBridge {
    private static final String API_CLASS = "me.languageplugin.api.LanguageAPI";
    private static final String PLUGIN_ID = "TpaPlugin";

    private TranslationBridge() {
    }

    public static String resolve(JavaPlugin plugin, UUID playerId, String languageCode, String key) {
        if (playerId != null) {
            String remote = resolveRemote(playerId, key);
            if (remote != null) {
                return remote;
            }
        }
        return Translations.resolve(languageCode, key);
    }

    private static String resolveRemote(UUID playerId, String key) {
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Object api = Bukkit.getServicesManager().load(apiClass);
            if (api == null) {
                return null;
            }
            Method method = apiClass.getMethod("resolvePluginMessage", UUID.class, String.class, String.class);
            return (String) method.invoke(api, playerId, PLUGIN_ID, key);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
