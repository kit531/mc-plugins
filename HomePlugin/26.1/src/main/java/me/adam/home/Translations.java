package me.adam.home;

import java.util.HashMap;
import java.util.Map;

public final class Translations {
    private static final Map<String, Map<String, String>> DATA = new HashMap<>();

    static {
        Map<String, String> en = new HashMap<>();
        en.put("prefix", "&8[&b&lHOME&8] &7");
        en.put("home-set", "&a✔ &7Home &f{home} &7saved.");
        en.put("home-deleted", "&a✔ &7Home &f{home} &7deleted.");
        en.put("home-not-found", "&c✘ &7Home &f{home} &7was not found.");
        en.put("home-limit", "&c✘ &7You reached max homes: &f{max}&7.");
        en.put("home-list-empty", "&eℹ &7You do not have any saved homes yet.");
        en.put("home-list", "&b📍 &7Your homes: &f{list}");
        en.put("teleport-start", "&b⌛ &7Teleporting to &f{home} &7in &f{seconds} &7seconds...");
        en.put("teleported", "&a✔ &7Teleported to &f{home}&7.");
        en.put("cooldown", "&c⏳ &7You can use this again in &f{remaining} &7seconds.");
        en.put("teleport-cancelled-move", "&c✘ &7Teleport to &f{home} &7cancelled because you moved.");
        en.put("only-player", "&c✘ &7This command is only for players.");
        en.put("usage-sethome", "&eUsage: &f/sethome [name]");
        en.put("usage-home", "&eUsage: &f/home [name]");
        en.put("usage-delhome", "&eUsage: &f/delhome <name>");
        en.put("usage-language", "&eUsage: &f/language home <english|hebrew|french|spanish>");
        en.put("language-set", "&a✔ &7Home language set to &f{language}&7.");
        en.put("language-unknown", "&c✘ &7Unknown language. Use: &fenglish/hebrew/french/spanish");
        en.put("invalid-home-name", "&c✘ &7Invalid home name. Use only: a-z, 0-9, _ or - (max 32).");
        DATA.put("en", en);

        Map<String, String> he = new HashMap<>();
        he.put("prefix", "&8[&b&lHOME&8] &7");
        he.put("home-set", "&a✔ &7הבית &f{home} &7נשמר בהצלחה.");
        he.put("home-deleted", "&a✔ &7הבית &f{home} &7נמחק.");
        he.put("home-not-found", "&c✘ &7לא נמצא בית בשם &f{home}&7.");
        he.put("home-limit", "&c✘ &7הגעת למקסימום בתים: &f{max}&7.");
        he.put("home-list-empty", "&eℹ &7עדיין אין לך בתים שמורים.");
        he.put("home-list", "&b📍 &7הבתים שלך: &f{list}");
        he.put("teleport-start", "&b⌛ &7משתגר אל &f{home} &7בעוד &f{seconds} &7שניות...");
        he.put("teleported", "&a✔ &7הגעת אל &f{home}&7.");
        he.put("cooldown", "&c⏳ &7אפשר להשתמש שוב בעוד &f{remaining} &7שניות.");
        he.put("teleport-cancelled-move", "&c✘ &7השיגור אל &f{home} &7בוטל כי זזת.");
        he.put("only-player", "&c✘ &7הפקודה זמינה רק לשחקנים.");
        he.put("usage-sethome", "&eשימוש: &f/sethome [name]");
        he.put("usage-home", "&eשימוש: &f/home [name]");
        he.put("usage-delhome", "&eשימוש: &f/delhome <name>");
        he.put("usage-language", "&eשימוש: &f/language home <english|hebrew|french|spanish>");
        he.put("language-set", "&a✔ &7שפת הפלאגין הוגדרה ל־&f{language}&7.");
        he.put("language-unknown", "&c✘ &7שפה לא מוכרת. אפשר: &fenglish/hebrew/french/spanish");
        he.put("invalid-home-name", "&c✘ &7שם בית לא תקין. מותר רק a-z, 0-9, _ או - (עד 32 תווים).");
        DATA.put("he", he);

        Map<String, String> fr = new HashMap<>();
        fr.put("prefix", "&8[&b&lHOME&8] &7");
        fr.put("home-set", "&a✔ &7Le home &f{home} &7a ete enregistre.");
        fr.put("home-deleted", "&a✔ &7Le home &f{home} &7a ete supprime.");
        fr.put("home-not-found", "&c✘ &7Le home &f{home} &7est introuvable.");
        fr.put("home-limit", "&c✘ &7Vous avez atteint le maximum de homes: &f{max}&7.");
        fr.put("home-list-empty", "&eℹ &7Vous n'avez encore aucun home enregistre.");
        fr.put("home-list", "&b📍 &7Vos homes: &f{list}");
        fr.put("teleport-start", "&b⌛ &7Teleportation vers &f{home} &7dans &f{seconds} &7secondes...");
        fr.put("teleported", "&a✔ &7Teleporte vers &f{home}&7.");
        fr.put("cooldown", "&c⏳ &7Reessayez dans &f{remaining} &7secondes.");
        fr.put("teleport-cancelled-move", "&c✘ &7Teleportation vers &f{home} &7annulee car vous avez bouge.");
        fr.put("only-player", "&c✘ &7Commande reservee aux joueurs.");
        fr.put("usage-sethome", "&eUtilisation: &f/sethome [name]");
        fr.put("usage-home", "&eUtilisation: &f/home [name]");
        fr.put("usage-delhome", "&eUtilisation: &f/delhome <name>");
        fr.put("usage-language", "&eUtilisation: &f/language home <english|hebrew|french|spanish>");
        fr.put("language-set", "&a✔ &7Langue du plugin definie sur &f{language}&7.");
        fr.put("language-unknown", "&c✘ &7Langue inconnue. Utilisez: &fenglish/hebrew/french/spanish");
        fr.put("invalid-home-name", "&c✘ &7Nom de home invalide. Autorises: a-z, 0-9, _ ou - (max 32).");
        DATA.put("fr", fr);

        Map<String, String> es = new HashMap<>();
        es.put("prefix", "&8[&b&lHOME&8] &7");
        es.put("home-set", "&a✔ &7Home &f{home} &7guardado.");
        es.put("home-deleted", "&a✔ &7Home &f{home} &7eliminado.");
        es.put("home-not-found", "&c✘ &7No se encontro el home &f{home}&7.");
        es.put("home-limit", "&c✘ &7Alcanzaste el maximo de homes: &f{max}&7.");
        es.put("home-list-empty", "&eℹ &7Aun no tienes homes guardados.");
        es.put("home-list", "&b📍 &7Tus homes: &f{list}");
        es.put("teleport-start", "&b⌛ &7Teletransportando a &f{home} &7en &f{seconds} &7segundos...");
        es.put("teleported", "&a✔ &7Teletransportado a &f{home}&7.");
        es.put("cooldown", "&c⏳ &7Puedes usarlo de nuevo en &f{remaining} &7segundos.");
        es.put("teleport-cancelled-move", "&c✘ &7El teletransporte a &f{home} &7se cancelo porque te moviste.");
        es.put("only-player", "&c✘ &7Este comando es solo para jugadores.");
        es.put("usage-sethome", "&eUso: &f/sethome [name]");
        es.put("usage-home", "&eUso: &f/home [name]");
        es.put("usage-delhome", "&eUso: &f/delhome <name>");
        es.put("usage-language", "&eUso: &f/language home <english|hebrew|french|spanish>");
        es.put("language-set", "&a✔ &7Idioma del plugin cambiado a &f{language}&7.");
        es.put("language-unknown", "&c✘ &7Idioma desconocido. Usa: &fenglish/hebrew/french/spanish");
        es.put("invalid-home-name", "&c✘ &7Nombre de home invalido. Solo: a-z, 0-9, _ o - (max 32).");
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
