package dev.adamk.checkpoints;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Builds the moving version of a floating name.
 *
 * <p>Minecraft colours text one character at a time - there is no way to shade
 * across a single glyph - so smoothness comes from motion: the same palette,
 * nudged along on every frame. A few effects reach past colour and drive the
 * display's own opacity or scale instead.
 */
public final class NameAnimator {
	/** Ticks in one full cycle of the steady effects. */
	public static final int PERIOD = 60;

	private NameAnimator() {
	}

	/**
	 * The text for this frame, or null to leave whatever is already shown
	 * (used by the effects that animate opacity or scale instead of colour).
	 *
	 * @param raw   the name as written, colour codes and all
	 * @param frame a counter that increases on every update
	 */
	public static Component render(String raw, Checkpoint.Animation animation, int frame) {
		String plain = TextFormat.stripAll(raw);
		if (plain == null || plain.isEmpty() || animation == Checkpoint.Animation.NONE) {
			return null;
		}
		boolean bold = raw.contains("<bold>") || raw.contains("&l") || raw.contains("&L");
		double phase = (frame % PERIOD) / (double) PERIOD;

		return switch (animation) {
			// ---- the author's own colours ----
			case FLOW -> slide(raw, plain, frame, bold, false);
			case FLOW_REVERSE -> slide(raw, plain, frame, bold, true);
			case CYCLE -> cycle(raw, plain, frame, bold);
			case WAVE -> wave(raw, plain, frame, bold);
			case SHIMMER -> shimmer(raw, plain, frame, bold);

			// ---- the full spectrum ----
			case RAINBOW -> rainbow(plain, phase, bold, 0.8);
			case RAINBOW_FAST -> rainbow(plain, (frame % 20) / 20.0, bold, 0.8);
			case RAINBOW_SLOW -> rainbow(plain, (frame % 160) / 160.0, bold, 0.8);
			case SPIN -> solid(plain, colour(phase, 1.0, 1.0), bold);
			case NEON -> solid(plain, colour(phase, 0.85, 0.75 + breath(phase) * 0.25), bold);

			// ---- brightness ----
			case PULSE -> brightness(raw, plain, bold, 0.45 + breath(phase) * 0.55);
			case HEARTBEAT -> brightness(raw, plain, bold, heartbeat(phase));
			case STROBE -> brightness(raw, plain, bold, (frame / 2) % 2 == 0 ? 1.0 : 0.15);
			case BLINK -> brightness(raw, plain, bold, (frame / 10) % 2 == 0 ? 1.0 : 0.2);

			// ---- a highlight running along the text ----
			case CHASE -> highlight(raw, plain, bold, (frame / 2) % plain.length(), 0);
			case BOUNCE -> highlight(raw, plain, bold, pingPong(frame / 2, plain.length()), 0);
			case SCAN -> highlight(raw, plain, bold, (frame / 2) % plain.length(), 1);
			case SPARKLE -> sparkle(raw, plain, bold, frame);

			// ---- themed palettes ----
			case FIRE -> themed(plain, frame, bold, 0.00, 0.14, true);
			case ICE -> themed(plain, frame, bold, 0.47, 0.58, false);
			case TOXIC -> themed(plain, frame, bold, 0.22, 0.36, true);
			case GOLD -> themed(plain, frame, bold, 0.09, 0.16, false);
			case BLOOD -> themed(plain, frame, bold, 0.95, 1.02, false);
			case OCEAN -> themed(plain, frame, bold, 0.45, 0.62, false);
			case SUNSET -> themed(plain, frame, bold, 0.92, 1.12, false);
			case GALAXY -> themed(plain, frame, bold, 0.66, 0.86, true);

			// ---- text tricks ----
			case TYPEWRITER -> typewriter(raw, plain, bold, frame);
			case GLITCH -> glitch(raw, plain, bold, frame);

			// ---- driven by the display, not the text ----
			case FADE, ZOOM, NONE -> null;
		};
	}

	/** Text opacity for this frame, 0-255, or -1 to leave it alone. */
	public static int opacity(Checkpoint.Animation animation, int frame) {
		if (animation != Checkpoint.Animation.FADE) {
			return -1;
		}
		double phase = (frame % PERIOD) / (double) PERIOD;
		return (int) Math.round(40 + breath(phase) * 215);
	}

	/** Scale for this frame, or -1 to leave it alone. */
	public static float scale(Checkpoint.Animation animation, int frame) {
		if (animation != Checkpoint.Animation.ZOOM) {
			return -1f;
		}
		double phase = (frame % PERIOD) / (double) PERIOD;
		return (float) (0.75 + breath(phase) * 0.55);
	}

	// =========================== effects ===========================

	/**
	 * The written colours sliding along the text. The palette is mirrored
	 * before it repeats, so the two ends never sit side by side and the
	 * movement has no visible seam.
	 */
	private static Component slide(String raw, String plain, int frame, boolean bold, boolean reverse) {
		List<TextColor> cycle = mirrored(paletteOf(raw, plain.length()));
		if (cycle == null) {
			return null;
		}
		int size = cycle.size();
		int step = (frame / 2) % size;
		int offset = reverse ? size - step : step;

		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			builder.append(styled(plain.charAt(i), cycle.get((i + offset) % size), bold));
		}
		return finish(builder);
	}

	/** The whole name takes one palette colour at a time. */
	private static Component cycle(String raw, String plain, int frame, boolean bold) {
		List<TextColor> cycle = mirrored(paletteOf(raw, plain.length()));
		if (cycle == null) {
			return null;
		}
		return solid(plain, cycle.get((frame / 4) % cycle.size()), bold);
	}

	/** A brightness wave travelling over the written colours. */
	private static Component wave(String raw, String plain, int frame, boolean bold) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			double local = (frame / 30.0) - (i / (double) plain.length()) * 2;
			double factor = 0.4 + (Math.sin(local * Math.PI * 2) + 1) / 2 * 0.6;
			builder.append(styled(plain.charAt(i), scaleColour(palette.get(i), factor), bold));
		}
		return finish(builder);
	}

	/** A single bright glint sweeping across the written colours. */
	private static Component shimmer(String raw, String plain, int frame, boolean bold) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		int length = plain.length();
		double head = ((frame / 2.0) % (length + 6)) - 3;

		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < length; i++) {
			double distance = Math.abs(i - head);
			double lift = Math.max(0, 1 - distance / 2.0);
			TextColor base = palette.get(i);
			builder.append(styled(plain.charAt(i), blend(base, NamedTextColor.WHITE, lift * 0.85), bold));
		}
		return finish(builder);
	}

	private static Component rainbow(String text, double phase, boolean bold, double spread) {
		TextComponent.Builder builder = Component.text();
		int length = text.length();
		for (int i = 0; i < length; i++) {
			builder.append(styled(text.charAt(i), colour(phase + (i / (double) length) * spread, 1.0, 1.0), bold));
		}
		return finish(builder);
	}

	/** The written colours, all dimmed or lifted together. */
	private static Component brightness(String raw, String plain, boolean bold, double factor) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			builder.append(styled(plain.charAt(i), scaleColour(palette.get(i), factor), bold));
		}
		return finish(builder);
	}

	/** One bright character (plus {@code halo} neighbours) over a dimmed name. */
	private static Component highlight(String raw, String plain, boolean bold, int head, int halo) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			boolean lit = Math.abs(i - head) <= halo;
			builder.append(styled(plain.charAt(i),
					lit ? blend(palette.get(i), NamedTextColor.WHITE, 0.6) : scaleColour(palette.get(i), 0.35),
					bold));
		}
		return finish(builder);
	}

	/** Random characters catch the light for a moment. */
	private static Component sparkle(String raw, String plain, boolean bold, int frame) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			boolean lit = noise(frame / 3, i) % 100 < 18;
			builder.append(styled(plain.charAt(i),
					lit ? NamedTextColor.WHITE : scaleColour(palette.get(i), 0.75), bold));
		}
		return finish(builder);
	}

	/** A hue band drifting across the text, optionally flickering. */
	private static Component themed(String plain, int frame, boolean bold,
			double from, double to, boolean flicker) {
		int length = plain.length();
		double drift = frame / 45.0;

		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < length; i++) {
			double t = length == 1 ? 0 : i / (double) (length - 1);
			double hue = from + (to - from) * ((t + drift) % 1.0);
			double value = 1.0;
			if (flicker) {
				value = 0.72 + (noise(frame / 2, i) % 28) / 100.0;
			}
			builder.append(styled(plain.charAt(i), colour(hue, 0.9, value), bold));
		}
		return finish(builder);
	}

	/** The name types itself out, then starts over. */
	private static Component typewriter(String raw, String plain, boolean bold, int frame) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		int span = plain.length() + 8; // a pause once the word is complete
		int shown = Math.min(plain.length(), (frame / 3) % span);

		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < shown; i++) {
			builder.append(styled(plain.charAt(i), palette.get(i), bold));
		}
		return finish(builder);
	}

	/** Random characters scramble, the rest stay put. */
	private static Component glitch(String raw, String plain, boolean bold, int frame) {
		List<TextColor> palette = paletteOf(raw, plain.length());
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < plain.length(); i++) {
			Component piece = styled(plain.charAt(i), palette.get(i), bold);
			if (noise(frame / 2, i) % 100 < 12) {
				piece = piece.decorate(TextDecoration.OBFUSCATED);
			}
			builder.append(piece);
		}
		return finish(builder);
	}

	// =========================== helpers ===========================

	private static Component solid(String text, TextColor colour, boolean bold) {
		TextComponent.Builder builder = Component.text();
		for (int i = 0; i < text.length(); i++) {
			builder.append(styled(text.charAt(i), colour, bold));
		}
		return finish(builder);
	}

	private static Component styled(char character, TextColor colour, boolean bold) {
		Component component = Component.text(String.valueOf(character), colour);
		return bold ? component.decorate(TextDecoration.BOLD) : component;
	}

	private static Component finish(TextComponent.Builder builder) {
		return builder.build().decoration(TextDecoration.ITALIC, false);
	}

	/** The palette laid out again in reverse, or null when there is nothing to slide. */
	private static List<TextColor> mirrored(List<TextColor> palette) {
		if (palette.stream().distinct().count() < 2) {
			return null;
		}
		List<TextColor> cycle = new ArrayList<>(palette);
		for (int i = palette.size() - 2; i > 0; i--) {
			cycle.add(palette.get(i));
		}
		return cycle;
	}

	/** The colour of each character of the name as written. */
	private static List<TextColor> paletteOf(String raw, int length) {
		List<TextColor> colours = new ArrayList<>();
		collect(TextFormat.parse(raw, null), null, colours);
		while (colours.size() < length) {
			colours.add(colours.isEmpty() ? NamedTextColor.WHITE : colours.get(colours.size() - 1));
		}
		return colours;
	}

	private static void collect(Component component, TextColor inherited, List<TextColor> out) {
		TextColor colour = component.color() != null ? component.color() : inherited;
		if (component instanceof TextComponent text) {
			for (int i = 0; i < text.content().length(); i++) {
				out.add(colour != null ? colour : NamedTextColor.WHITE);
			}
		}
		for (Component child : component.children()) {
			collect(child, colour, out);
		}
	}

	/** A smooth 0..1 rise and fall over one cycle. */
	private static double breath(double phase) {
		return (Math.sin(phase * 2 * Math.PI - Math.PI / 2) + 1) / 2;
	}

	/** Two quick thumps, then a rest. */
	private static double heartbeat(double phase) {
		double p = phase % 1.0;
		if (p < 0.12) {
			return 0.35 + Math.sin(p / 0.12 * Math.PI) * 0.65;
		}
		if (p > 0.2 && p < 0.32) {
			return 0.35 + Math.sin((p - 0.2) / 0.12 * Math.PI) * 0.45;
		}
		return 0.35;
	}

	private static int pingPong(int step, int length) {
		if (length <= 1) {
			return 0;
		}
		int span = (length - 1) * 2;
		int position = step % span;
		return position < length ? position : span - position;
	}

	/** Deterministic pseudo-noise, so every player sees the same sparkle. */
	private static int noise(int frame, int index) {
		int h = frame * 374761393 + index * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return Math.abs(h ^ (h >>> 16));
	}

	private static TextColor scaleColour(TextColor colour, double factor) {
		double f = Math.max(0, Math.min(1.5, factor));
		return TextColor.color(
				clamp(colour.red() * f), clamp(colour.green() * f), clamp(colour.blue() * f));
	}

	private static TextColor blend(TextColor from, TextColor to, double amount) {
		double a = Math.max(0, Math.min(1, amount));
		return TextColor.color(
				clamp(from.red() + (to.red() - from.red()) * a),
				clamp(from.green() + (to.green() - from.green()) * a),
				clamp(from.blue() + (to.blue() - from.blue()) * a));
	}

	private static int clamp(double value) {
		return (int) Math.max(0, Math.min(255, Math.round(value)));
	}

	/** HSV to a text colour; hue wraps, so callers may pass any value. */
	static TextColor colour(double hue, double saturation, double value) {
		double h = hue - Math.floor(hue);
		int sector = (int) (h * 6) % 6;
		double f = h * 6 - Math.floor(h * 6);
		double p = value * (1 - saturation);
		double q = value * (1 - f * saturation);
		double t = value * (1 - (1 - f) * saturation);

		double r;
		double g;
		double b;
		switch (sector) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return TextColor.color(clamp(r * 255), clamp(g * 255), clamp(b * 255));
	}
}
