package me.languageplugin;

import java.util.HashMap;
import java.util.Map;

public final class Translations {
    private static final Map<String, Map<String, String>> DATA = new HashMap<>();

    static {
        Map<String, String> en = new HashMap<>();
        en.put("prefix", "&8[&7Language&8] &7");
        en.put("only-player", "&c✘ &7Players only.");
        en.put("usage-language", "&eUsage: &f/language <english|hebrew|french|spanish>");
        en.put("language-set", "&a✔ &7Language set to &f{language}&7.");
        en.put("language-unknown", "&c✘ &7Unknown language. Use: &fenglish, hebrew, french, spanish");
        DATA.put("en", en);

        Map<String, String> he = new HashMap<>();
        he.put("prefix", "&8[&7שפה&8] &7");
        he.put("only-player", "&c✘ &7לשחקנים בלבד.");
        he.put("usage-language", "&eשימוש: &f/language <english|hebrew|french|spanish>");
        he.put("language-set", "&a✔ &7השפה הוגדרה ל־&f{language}&7.");
        he.put("language-unknown", "&c✘ &7שפה לא מוכרת. אפשר: &fenglish, hebrew, french, spanish");
        DATA.put("he", he);

        Map<String, String> fr = new HashMap<>();
        fr.put("prefix", "&8[&7Langue&8] &7");
        fr.put("only-player", "&c✘ &7Joueurs uniquement.");
        fr.put("usage-language", "&eUtilisation: &f/language <english|hebrew|french|spanish>");
        fr.put("language-set", "&a✔ &7Langue definie: &f{language}&7.");
        fr.put("language-unknown", "&c✘ &7Langue inconnue.");
        DATA.put("fr", fr);

        Map<String, String> es = new HashMap<>();
        es.put("prefix", "&8[&7Idioma&8] &7");
        es.put("only-player", "&c✘ &7Solo jugadores.");
        es.put("usage-language", "&eUso: &f/language <english|hebrew|french|spanish>");
        es.put("language-set", "&a✔ &7Idioma: &f{language}&7.");
        es.put("language-unknown", "&c✘ &7Idioma desconocido.");
        DATA.put("es", es);
    }

    private Translations() {
    }

    public static String resolve(String languageCode, String key) {
        Map<String, String> lang = DATA.getOrDefault(languageCode, DATA.get("en"));
        String value = lang.get(key);
        if (value != null) {
            return value;
        }
        return DATA.get("en").getOrDefault(key, key);
    }
}
