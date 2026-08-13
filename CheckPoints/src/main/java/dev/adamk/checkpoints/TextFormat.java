package dev.adamk.checkpoints;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.permissions.Permissible;

/**
 * Turns a free-form string (typed in an anvil, a name tag, config, anywhere)
 * into a formatted {@link Component}.
 *
 * <p>Accepted syntax, all mixable in one string:
 * <ul>
 *   <li>legacy codes - {@code &a}, {@code &l}, {@code &r}</li>
 *   <li>legacy hex - {@code &#FF5555} and the Spigot {@code &x&F&F&5&5&5&5} form</li>
 *   <li>MiniMessage - {@code <red>}, {@code <bold>}, {@code <#FF5555>},
 *       {@code <gradient:#ff0000:#0000ff>}, {@code <rainbow>}</li>
 * </ul>
 *
 * <p>Everything is normalised to MiniMessage first, so a single parser handles
 * all of it and permissions can filter the syntax before it is parsed.
 */
public final class TextFormat {
	/** Any colour at all. Without it the text stays plain. */
	public static final String PERM_BASIC = "checkpoints.color.basic";
	/** Hex RGB colours. */
	public static final String PERM_HEX = "checkpoints.color.hex";
	/** Gradients, rainbow and transitions. */
	public static final String PERM_GRADIENT = "checkpoints.color.gradient";

	private static final MiniMessage MINI = MiniMessage.miniMessage();

	private static final Pattern LEGACY_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
	private static final Pattern SPIGOT_HEX =
			Pattern.compile("(?i)&x((?:&[0-9a-f]){6})");
	private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&([0-9a-fk-or])");

	private static final Pattern HEX_TAG = Pattern.compile("(?i)<#[0-9a-f]{6}>");
	private static final Pattern GRADIENT_TAG =
			Pattern.compile("(?i)</?(?:gradient|rainbow|transition)(?::[^<>]*)?>");
	private static final Pattern ANY_TAG = Pattern.compile("<[^<>]+>");

	private TextFormat() {
	}

	/**
	 * Parses {@code raw} into a component, dropping any syntax the author is not
	 * allowed to use. Never throws - malformed input falls back to plain text.
	 */
	public static Component parse(String raw, Permissible author) {
		if (raw == null) {
			return null;
		}

		String text = toMiniMessage(raw);

		if (author != null && !author.hasPermission(PERM_BASIC)) {
			text = ANY_TAG.matcher(text).replaceAll("");
		} else if (author != null) {
			if (!author.hasPermission(PERM_GRADIENT)) {
				text = GRADIENT_TAG.matcher(text).replaceAll("");
			}
			if (!author.hasPermission(PERM_HEX)) {
				text = HEX_TAG.matcher(text).replaceAll("");
			}
		}

		try {
			// Display entities render italics oddly; start from a clean slate.
			return MINI.deserialize(text).decoration(TextDecoration.ITALIC, false);
		} catch (RuntimeException e) {
			return Component.text(stripAll(raw));
		}
	}

	/** The visible characters only, with every colour/format code removed. */
	public static String stripAll(String raw) {
		if (raw == null) {
			return null;
		}
		String text = toMiniMessage(raw);
		return ANY_TAG.matcher(text).replaceAll("");
	}

	/** True if the string carries any colour or formatting instruction. */
	public static boolean hasFormatting(String raw) {
		return raw != null && ANY_TAG.matcher(toMiniMessage(raw)).find();
	}

	/** Normalises every supported legacy form into MiniMessage tags. */
	private static String toMiniMessage(String raw) {
		String text = raw;

		// &x&F&F&5&5&5&5 -> <#FF5555>
		Matcher spigot = SPIGOT_HEX.matcher(text);
		StringBuilder out = new StringBuilder();
		while (spigot.find()) {
			String hex = spigot.group(1).replace("&", "");
			spigot.appendReplacement(out, "<#" + hex + ">");
		}
		spigot.appendTail(out);
		text = out.toString();

		// &#FF5555 -> <#FF5555>
		text = LEGACY_HEX.matcher(text).replaceAll("<#$1>");

		// &a, &l, &r ... -> <green>, <bold>, <reset> ...
		Matcher legacy = LEGACY_CODE.matcher(text);
		StringBuilder result = new StringBuilder();
		while (legacy.find()) {
			String tag = tagFor(Character.toLowerCase(legacy.group(1).charAt(0)));
			legacy.appendReplacement(result, Matcher.quoteReplacement(tag));
		}
		legacy.appendTail(result);
		return result.toString();
	}

	private static String tagFor(char code) {
		return switch (code) {
			case '0' -> "<black>";
			case '1' -> "<dark_blue>";
			case '2' -> "<dark_green>";
			case '3' -> "<dark_aqua>";
			case '4' -> "<dark_red>";
			case '5' -> "<dark_purple>";
			case '6' -> "<gold>";
			case '7' -> "<gray>";
			case '8' -> "<dark_gray>";
			case '9' -> "<blue>";
			case 'a' -> "<green>";
			case 'b' -> "<aqua>";
			case 'c' -> "<red>";
			case 'd' -> "<light_purple>";
			case 'e' -> "<yellow>";
			case 'f' -> "<white>";
			case 'k' -> "<obfuscated>";
			case 'l' -> "<bold>";
			case 'm' -> "<strikethrough>";
			case 'n' -> "<underlined>";
			case 'o' -> "<italic>";
			case 'r' -> "<reset>";
			default -> "";
		};
	}
}
