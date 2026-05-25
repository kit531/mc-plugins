package me.languageplugin.api;

import java.util.UUID;

public interface LanguageAPI {
    String getLanguage(UUID playerId);

    void setLanguage(UUID playerId, String languageCode);
}
