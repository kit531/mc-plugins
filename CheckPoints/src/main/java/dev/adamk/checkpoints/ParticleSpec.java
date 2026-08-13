package dev.adamk.checkpoints;

import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * A particle effect stored as a single string, so it fits in one config value.
 *
 * <p>Formats: {@code NONE}, a particle name such as {@code FLAME}, or a
 * coloured dust such as {@code DUST:#FF5555}.
 */
public record ParticleSpec(Particle particle, Color color) {
	public static final String NONE = "NONE";

	/** Parses a spec, or returns null for "none" and anything unusable. */
	public static ParticleSpec parse(String raw) {
		if (raw == null || raw.isBlank() || raw.equalsIgnoreCase(NONE) || raw.equalsIgnoreCase("off")) {
			return null;
		}

		String text = raw.trim();
		String name = text;
		Color color = null;

		int colon = text.indexOf(':');
		if (colon > 0) {
			name = text.substring(0, colon);
			String hex = text.substring(colon + 1).replace("#", "").trim();
			try {
				color = Color.fromRGB(Integer.parseInt(hex, 16));
			} catch (IllegalArgumentException e) {
				color = null;
			}
		}

		Particle particle;
		try {
			particle = Particle.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}

		if (particle == Particle.DUST) {
			return new ParticleSpec(particle, color != null ? color : Color.WHITE);
		}
		// Anything that needs extra data we cannot supply is rejected.
		return particle.getDataType() == Void.class ? new ParticleSpec(particle, null) : null;
	}

	/** True if this name can be used - handy for validating command input. */
	public static boolean isValid(String raw) {
		return raw != null
				&& (raw.equalsIgnoreCase(NONE) || raw.equalsIgnoreCase("off") || parse(raw) != null);
	}

	public void spawn(World world, double x, double y, double z) {
		Location loc = new Location(world, x, y, z);
		try {
			if (particle == Particle.DUST) {
				world.spawnParticle(particle, loc, 4, 0.15, 0.1, 0.15, 0.0,
						new Particle.DustOptions(color, 1.0f));
			} else {
				world.spawnParticle(particle, loc, 3, 0.15, 0.1, 0.15, 0.0);
			}
		} catch (RuntimeException ignored) {
			// A particle that turns out to need data is simply skipped.
		}
	}
}
