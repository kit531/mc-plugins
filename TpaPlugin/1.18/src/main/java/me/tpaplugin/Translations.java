package me.tpaplugin;

import java.util.HashMap;
import java.util.Map;

public final class Translations {
    private static final Map<String, Map<String, String>> DATA = new HashMap<>();

    static {
        Map<String, String> en = new HashMap<>();
        en.put("prefix", "&8[&d&lTPA&8] &7");
        en.put("only-player", "&c✘ &7This command is only for players.");
        en.put("usage-tpa", "&eUsage: &f/tpa <player>");
        en.put("usage-tpahere", "&eUsage: &f/tpahere <player>");
        en.put("language-prefix", "&8[&7Language&8] &7");
        en.put("usage-language", "&eUsage: &f/language <english|hebrew|french|spanish>");
        en.put("language-set", "&a✔ &7Language set to &f{language}&7.");
        en.put("language-unknown", "&c✘ &7Unknown language. Use: &fenglish/hebrew/french/spanish");
        en.put("player-not-found", "&c✘ &7Player &f{player} &7is not online.");
        en.put("player-offline", "&c✘ &7The other player is no longer online.");
        en.put("tpa-self", "&c✘ &7You cannot send a request to yourself.");
        en.put("tpa-already-sent", "&c✘ &7You already have an outgoing request. Use &f/tpacancel&7.");
        en.put("tpa-sent", "&a✔ &7Teleport request sent to &f{player}&7.");
        en.put("tpa-received", "&b📨 &f{player} &7wants to teleport to you. &f/tpaccept &7or &f/tpdeny&7.");
        en.put("tpahere-sent", "&a✔ &7Asked &f{player} &7to teleport to you.");
        en.put("tpahere-received", "&b📨 &f{player} &7wants you to teleport to them. &f/tpaccept &7or &f/tpdeny&7.");
        en.put("tpahere-accepted-target", "&a✔ &7You will teleport to &f{player}&7.");
        en.put("tpahere-accepted-sender", "&a✔ &f{player} &7is teleporting to you.");
        en.put("tpa-none-incoming", "&c✘ &7You have no incoming teleport requests.");
        en.put("tpa-none-outgoing", "&c✘ &7You have no outgoing teleport request.");
        en.put("tpa-accepted-target", "&a✔ &7You accepted &f{player}&7's request.");
        en.put("tpa-accepted-sender", "&a✔ &f{player} &7accepted your request.");
        en.put("tpa-denied-target", "&c✘ &7Request denied.");
        en.put("tpa-denied-sender", "&c✘ &f{player} &7denied your teleport request.");
        en.put("tpa-cancelled-sender", "&eℹ &7Your teleport request was cancelled.");
        en.put("tpa-cancelled-target", "&eℹ &f{player} &7cancelled their teleport request.");
        en.put("teleport-start", "&b⌛ &7Teleporting to &f{player} &7in &f{seconds} &7seconds...");
        en.put("teleported", "&a✔ &7Teleported to &f{player}&7.");
        en.put("teleport-cancelled-move", "&c✘ &7Teleport to &f{player} &7cancelled because you moved.");
        en.put("cooldown", "&c⏳ &7You can send another request in &f{remaining} &7seconds.");
        DATA.put("en", en);

        Map<String, String> he = new HashMap<>();
        he.put("prefix", "&8[&d&lTPA&8] &7");
        he.put("only-player", "&c✘ &7הפקודה זמינה רק לשחקנים.");
        he.put("usage-tpa", "&eשימוש: &f/tpa <player>");
        he.put("usage-tpahere", "&eשימוש: &f/tpahere <player>");
        he.put("language-prefix", "&8[&7שפה&8] &7");
        he.put("usage-language", "&eשימוש: &f/language <english|hebrew|french|spanish>");
        he.put("language-set", "&a✔ &7השפה הוגדרה ל־&f{language}&7.");
        he.put("language-unknown", "&c✘ &7שפה לא מוכרת. אפשר: &fenglish/hebrew/french/spanish");
        he.put("player-not-found", "&c✘ &7השחקן &f{player} &7לא מחובר.");
        he.put("player-offline", "&c✘ &7השחקן השני כבר לא מחובר.");
        he.put("tpa-self", "&c✘ &7אי אפשר לשלוח בקשה לעצמך.");
        he.put("tpa-already-sent", "&c✘ &7כבר יש לך בקשה פעילה. השתמש ב־&f/tpacancel&7.");
        he.put("tpa-sent", "&a✔ &7בקשת שיגור נשלחה אל &f{player}&7.");
        he.put("tpa-received", "&b📨 &f{player} &7רוצה להשתגר אליך. &f/tpaccept &7או &f/tpdeny&7.");
        he.put("tpahere-sent", "&a✔ &7ביקשת מ־&f{player} &7להשתגר אליך.");
        he.put("tpahere-received", "&b📨 &f{player} &7רוצה שתשתגר אליו. &f/tpaccept &7או &f/tpdeny&7.");
        he.put("tpahere-accepted-target", "&a✔ &7תשתגר אל &f{player}&7.");
        he.put("tpahere-accepted-sender", "&a✔ &f{player} &7משתגר אליך.");
        he.put("tpa-none-incoming", "&c✘ &7אין לך בקשות שיגור נכנסות.");
        he.put("tpa-none-outgoing", "&c✘ &7אין לך בקשת שיגור יוצאת.");
        he.put("tpa-accepted-target", "&a✔ &7אישרת את הבקשה של &f{player}&7.");
        he.put("tpa-accepted-sender", "&a✔ &f{player} &7אישר את הבקשה שלך.");
        he.put("tpa-denied-target", "&c✘ &7הבקשה נדחתה.");
        he.put("tpa-denied-sender", "&c✘ &f{player} &7דחה את בקשת השיגור שלך.");
        he.put("tpa-cancelled-sender", "&eℹ &7בקשת השיגור שלך בוטלה.");
        he.put("tpa-cancelled-target", "&eℹ &f{player} &7ביטל את בקשת השיגור.");
        he.put("teleport-start", "&b⌛ &7משתגר אל &f{player} &7בעוד &f{seconds} &7שניות...");
        he.put("teleported", "&a✔ &7הגעת אל &f{player}&7.");
        he.put("teleport-cancelled-move", "&c✘ &7השיגור אל &f{player} &7בוטל כי זזת.");
        he.put("cooldown", "&c⏳ &7אפשר לשלוח בקשה נוספת בעוד &f{remaining} &7שניות.");
        DATA.put("he", he);

        Map<String, String> fr = new HashMap<>();
        fr.put("prefix", "&8[&d&lTPA&8] &7");
        fr.put("only-player", "&c✘ &7Commande reservee aux joueurs.");
        fr.put("usage-tpa", "&eUtilisation: &f/tpa <player>");
        fr.put("usage-tpahere", "&eUtilisation: &f/tpahere <player>");
        fr.put("language-prefix", "&8[&7Langue&8] &7");
        fr.put("usage-language", "&eUtilisation: &f/language <english|hebrew|french|spanish>");
        fr.put("language-set", "&a✔ &7Langue definie sur &f{language}&7.");
        fr.put("language-unknown", "&c✘ &7Langue inconnue. Utilisez: &fenglish/hebrew/french/spanish");
        fr.put("player-not-found", "&c✘ &7Le joueur &f{player} &7n'est pas en ligne.");
        fr.put("player-offline", "&c✘ &7L'autre joueur n'est plus en ligne.");
        fr.put("tpa-self", "&c✘ &7Vous ne pouvez pas vous envoyer une demande.");
        fr.put("tpa-already-sent", "&c✘ &7Vous avez deja une demande active. Utilisez &f/tpacancel&7.");
        fr.put("tpa-sent", "&a✔ &7Demande envoyee a &f{player}&7.");
        fr.put("tpa-received", "&b📨 &f{player} &7veut se teleporter vers vous. &f/tpaccept &7ou &f/tpdeny&7.");
        fr.put("tpahere-sent", "&a✔ &7Vous avez demande a &f{player} &7de venir vers vous.");
        fr.put("tpahere-received", "&b📨 &f{player} &7veut que vous vous teleportiez vers lui. &f/tpaccept &7ou &f/tpdeny&7.");
        fr.put("tpahere-accepted-target", "&a✔ &7Vous allez vous teleporter vers &f{player}&7.");
        fr.put("tpahere-accepted-sender", "&a✔ &f{player} &7se teleporte vers vous.");
        fr.put("tpa-none-incoming", "&c✘ &7Vous n'avez aucune demande entrante.");
        fr.put("tpa-none-outgoing", "&c✘ &7Vous n'avez aucune demande sortante.");
        fr.put("tpa-accepted-target", "&a✔ &7Vous avez accepte la demande de &f{player}&7.");
        fr.put("tpa-accepted-sender", "&a✔ &f{player} &7a accepte votre demande.");
        fr.put("tpa-denied-target", "&c✘ &7Demande refusee.");
        fr.put("tpa-denied-sender", "&c✘ &f{player} &7a refuse votre demande.");
        fr.put("tpa-cancelled-sender", "&eℹ &7Votre demande a ete annulee.");
        fr.put("tpa-cancelled-target", "&eℹ &f{player} &7a annule sa demande.");
        fr.put("teleport-start", "&b⌛ &7Teleportation vers &f{player} &7dans &f{seconds} &7secondes...");
        fr.put("teleported", "&a✔ &7Teleporte vers &f{player}&7.");
        fr.put("teleport-cancelled-move", "&c✘ &7Teleportation vers &f{player} &7annulee car vous avez bouge.");
        fr.put("cooldown", "&c⏳ &7Reessayez dans &f{remaining} &7secondes.");
        DATA.put("fr", fr);

        Map<String, String> es = new HashMap<>();
        es.put("prefix", "&8[&d&lTPA&8] &7");
        es.put("only-player", "&c✘ &7Este comando es solo para jugadores.");
        es.put("usage-tpa", "&eUso: &f/tpa <player>");
        es.put("usage-tpahere", "&eUso: &f/tpahere <player>");
        es.put("language-prefix", "&8[&7Idioma&8] &7");
        es.put("usage-language", "&eUso: &f/language <english|hebrew|french|spanish>");
        es.put("language-set", "&a✔ &7Idioma cambiado a &f{language}&7.");
        es.put("language-unknown", "&c✘ &7Idioma desconocido. Usa: &fenglish/hebrew/french/spanish");
        es.put("player-not-found", "&c✘ &7El jugador &f{player} &7no esta en linea.");
        es.put("player-offline", "&c✘ &7El otro jugador ya no esta en linea.");
        es.put("tpa-self", "&c✘ &7No puedes enviarte una solicitud a ti mismo.");
        es.put("tpa-already-sent", "&c✘ &7Ya tienes una solicitud activa. Usa &f/tpacancel&7.");
        es.put("tpa-sent", "&a✔ &7Solicitud enviada a &f{player}&7.");
        es.put("tpa-received", "&b📨 &f{player} &7quiere teletransportarse hacia ti. &f/tpaccept &7o &f/tpdeny&7.");
        es.put("tpahere-sent", "&a✔ &7Pediste a &f{player} &7que se teletransporte hacia ti.");
        es.put("tpahere-received", "&b📨 &f{player} &7quiere que te teletransportes hacia el. &f/tpaccept &7o &f/tpdeny&7.");
        es.put("tpahere-accepted-target", "&a✔ &7Te teletransportaras a &f{player}&7.");
        es.put("tpahere-accepted-sender", "&a✔ &f{player} &7se esta teletransportando hacia ti.");
        es.put("tpa-none-incoming", "&c✘ &7No tienes solicitudes entrantes.");
        es.put("tpa-none-outgoing", "&c✘ &7No tienes una solicitud saliente.");
        es.put("tpa-accepted-target", "&a✔ &7Aceptaste la solicitud de &f{player}&7.");
        es.put("tpa-accepted-sender", "&a✔ &f{player} &7acepto tu solicitud.");
        es.put("tpa-denied-target", "&c✘ &7Solicitud denegada.");
        es.put("tpa-denied-sender", "&c✘ &f{player} &7rechazo tu solicitud.");
        es.put("tpa-cancelled-sender", "&eℹ &7Tu solicitud fue cancelada.");
        es.put("tpa-cancelled-target", "&eℹ &f{player} &7cancelo su solicitud.");
        es.put("teleport-start", "&b⌛ &7Teletransportando a &f{player} &7en &f{seconds} &7segundos...");
        es.put("teleported", "&a✔ &7Teletransportado a &f{player}&7.");
        es.put("teleport-cancelled-move", "&c✘ &7El teletransporte a &f{player} &7se cancelo porque te moviste.");
        es.put("cooldown", "&c⏳ &7Puedes enviar otra solicitud en &f{remaining} &7segundos.");
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
