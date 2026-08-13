package dev.adamk.checkpoints;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A registered plugin-managed plate/button.
 *
 * <p>Everything tweakable lives in {@link #options()} keyed by the constants
 * below, so new settings only need an accessor here and a command line -
 * storage, item copying and persistence pick them up automatically.
 */
public record Checkpoint(String world, int x, int y, int z,
		boolean invisible, Type type, UUID display, Map<String, String> options) {

	public static final String NAME = "name";
	public static final String PARTICLE = "particle";
	public static final String PARTICLE_MODE = "particle-mode";
	public static final String DEATH_MESSAGE = "death-message";
	public static final String MESSAGE = "message";
	public static final String MESSAGE_MODE = "message-mode";
	public static final String SOUND = "sound";
	public static final String HEAL = "heal";
	public static final String ANIMATION = "anim";

	// what the special types act on
	public static final String TARGET = "target";
	public static final String COMMAND = "command";
	public static final String COMMAND_AS = "command-as";
	public static final String EFFECT = "effect";
	public static final String LAUNCH = "launch";

	// who may use it, and how often
	public static final String PERMISSION = "permission";
	public static final String COOLDOWN = "cooldown";
	public static final String ONCE = "once";

	// parkour courses
	public static final String COURSE = "course";
	public static final String ROLE = "role";

	/** Every option a command or an item copy may carry. */
	public static final String[] KEYS = {
			NAME, PARTICLE, PARTICLE_MODE, DEATH_MESSAGE, MESSAGE, MESSAGE_MODE, SOUND, HEAL, ANIMATION,
			TARGET, COMMAND, COMMAND_AS, EFFECT, LAUNCH,
			PERMISSION, COOLDOWN, ONCE, COURSE, ROLE
	};

	public enum Type {
		/** Sets the player's respawn. */
		CHECKPOINT,
		/** Clears the player's respawn back to the world default. */
		RESET,
		/** Kills whoever touches it. */
		KILL,
		/** Sends the player somewhere else. */
		TELEPORT,
		/** Runs a command. */
		COMMAND,
		/** Grants a potion effect. */
		EFFECT,
		/** Throws the player through the air. */
		LAUNCH,
		/** Does nothing on its own - just a working, hideable plate. */
		PLAIN;

		public static Type parse(String raw) {
			if (raw == null) {
				return CHECKPOINT;
			}
			String text = raw.trim().toUpperCase(Locale.ROOT);
			if (text.equals("CHECK") || text.equals("CP")) {
				return CHECKPOINT;
			}
			if (text.equals("TP")) {
				return TELEPORT;
			}
			if (text.equals("CMD")) {
				return COMMAND;
			}
			if (text.equals("JUMP") || text.equals("PAD")) {
				return LAUNCH;
			}
			try {
				return valueOf(text);
			} catch (IllegalArgumentException e) {
				return CHECKPOINT;
			}
		}

		/** True when stepping on it should not touch the player's respawn. */
		public boolean isRespawnPoint() {
			return this == CHECKPOINT;
		}
	}

	/** A point's part in a parkour course. */
	public enum Role {
		/** Starts (or restarts) the clock. */
		START,
		/** A split along the way. */
		CHECK,
		/** Stops the clock and records the time. */
		FINISH;

		public static Role parse(String raw) {
			if (raw == null) {
				return CHECK;
			}
			try {
				return valueOf(raw.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return CHECK;
			}
		}
	}

	/** When the particles are shown. */
	public enum ParticleMode {
		/** Continuously, for anyone within ~48 blocks. */
		ALWAYS,
		/** Continuously, but only within ~16 blocks. The default. */
		NEAR,
		/** A single burst when somebody activates the point. */
		TRIGGER,
		/** Never. */
		NONE;

		public static ParticleMode parse(String raw) {
			if (raw == null) {
				return NEAR;
			}
			try {
				return valueOf(raw.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NEAR;
			}
		}
	}

	/** Where the activation message appears. */
	public enum MessageMode {
		CHAT,
		ACTIONBAR,
		/** Chat and action bar together. The default. */
		BOTH,
		/** Big title on screen. */
		TITLE,
		NONE;

		public static MessageMode parse(String raw) {
			if (raw == null) {
				return BOTH;
			}
			try {
				return valueOf(raw.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return BOTH;
			}
		}
	}

	public Checkpoint {
		options = Map.copyOf(options);
	}

	public String key() {
		return key(world, x, y, z);
	}

	public static String key(String world, int x, int y, int z) {
		return world + ";" + x + ";" + y + ";" + z;
	}

	/** A copy with one option set, or removed when the value is null. */
	public Checkpoint with(String option, String value) {
		Map<String, String> copy = new LinkedHashMap<>(options);
		if (value == null) {
			copy.remove(option);
		} else {
			copy.put(option, value);
		}
		return new Checkpoint(world, x, y, z, invisible, type, display, copy);
	}

	public Checkpoint withDisplay(UUID id) {
		return new Checkpoint(world, x, y, z, invisible, type, id, options);
	}

	public String option(String key) {
		return options.get(key);
	}

	// ---- typed accessors, each with its default ----

	public String name() {
		return options.get(NAME);
	}

	public String deathMessage() {
		return options.get(DEATH_MESSAGE);
	}

	/** The stored particle, or a sensible default for the type. */
	public String particle() {
		String stored = options.get(PARTICLE);
		if (stored != null) {
			return stored;
		}
		// Invisible blocks stay quiet by default so they remain hidden.
		if (invisible) {
			return ParticleSpec.NONE;
		}
		return switch (type) {
			case CHECKPOINT -> "HAPPY_VILLAGER";
			case RESET -> "ENCHANT";
			case KILL -> "FLAME";
			case TELEPORT -> "PORTAL";
			case COMMAND -> "ENCHANT";
			case EFFECT -> "WITCH";
			case LAUNCH -> "CLOUD";
			case PLAIN -> ParticleSpec.NONE;
		};
	}

	public ParticleMode particleMode() {
		return ParticleMode.parse(options.get(PARTICLE_MODE));
	}

	public MessageMode messageMode() {
		return MessageMode.parse(options.get(MESSAGE_MODE));
	}

	/** The custom activation message, or null to use the built-in wording. */
	public String message() {
		return options.get(MESSAGE);
	}

	/** Sound played on activation, or null for the type default. */
	public String sound() {
		String stored = options.get(SOUND);
		if (stored != null) {
			return stored;
		}
		return type == Type.CHECKPOINT ? "ENTITY_EXPERIENCE_ORB_PICKUP" : "NONE";
	}

	/** Whether reaching this point refills health and hunger. */
	public boolean heal() {
		return Boolean.parseBoolean(options.getOrDefault(HEAL, "false"));
	}

	public Animation animation() {
		return Animation.parse(options.get(ANIMATION));
	}

	// ---- the special types ----

	/** Teleport destination as {@code world;x;y;z;yaw;pitch}, or null. */
	public String target() {
		return options.get(TARGET);
	}

	public String command() {
		return options.get(COMMAND);
	}

	/** True when the command runs from console rather than as the player. */
	public boolean commandAsConsole() {
		return !"PLAYER".equalsIgnoreCase(options.getOrDefault(COMMAND_AS, "CONSOLE"));
	}

	/** Potion effect as {@code TYPE:amplifier:seconds}, or null. */
	public String effect() {
		return options.get(EFFECT);
	}

	/** Launch strength as {@code forward,up}; defaults to a gentle hop. */
	public String launch() {
		return options.getOrDefault(LAUNCH, "0,1.0");
	}

	// ---- access control ----

	/** Permission needed to set this point off, or null for everyone. */
	public String permission() {
		return options.get(PERMISSION);
	}

	/** Seconds before the same player may use it again. */
	public int cooldown() {
		try {
			return Math.max(0, Integer.parseInt(options.getOrDefault(COOLDOWN, "0")));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** True when each player may use it only once, ever. */
	public boolean once() {
		return Boolean.parseBoolean(options.getOrDefault(ONCE, "false"));
	}

	// ---- parkour ----

	/** The course this point belongs to, or null. */
	public String course() {
		return options.get(COURSE);
	}

	public Role role() {
		return Role.parse(options.get(ROLE));
	}

	/**
	 * How the floating name moves. Colour per character is the finest the game
	 * allows, so smoothness comes from shifting it over time.
	 *
	 * <p>The first group keeps the colours you wrote; the rest bring their own.
	 */
	public enum Animation {
		/** Static, exactly as written. */
		NONE("סטטי - בדיוק כמו שכתבת"),

		// -- your own colours --
		FLOW("הצבעים שלך זורמים לאורך השם"),
		FLOW_REVERSE("כמו FLOW, בכיוון ההפוך"),
		CYCLE("כל השם מחליף צבע אחד בכל פעם"),
		WAVE("גל בהירות עובר על הצבעים שלך"),
		SHIMMER("נצנוץ לבן חולף לאורך השם"),

		// -- full spectrum --
		RAINBOW("קשת מלאה זורמת"),
		RAINBOW_FAST("קשת מהירה"),
		RAINBOW_SLOW("קשת איטית ורגועה"),
		SPIN("כל השם בצבע אחד שמתחלף"),
		NEON("ניאון - צבע מתחלף עם נשימה"),

		// -- brightness --
		PULSE("נשימה בין עמום לבוהק"),
		HEARTBEAT("שני פעימות ואז מנוחה"),
		STROBE("הבהוב מהיר וחד"),
		BLINK("הבהוב איטי"),

		// -- a highlight running along --
		CHASE("אות בוהקת רצה לאורך השם"),
		BOUNCE("אות בוהקת הלוך ושוב"),
		SCAN("פס אור סורק את השם"),
		SPARKLE("אותיות אקראיות מנצנצות"),

		// -- themed palettes --
		FIRE("אש - אדום, כתום וצהוב מרצדים"),
		ICE("קרח - תכלת ולבן"),
		TOXIC("רעל - ירוק זוהר"),
		GOLD("זהב נוצץ"),
		BLOOD("דם - אדום כהה"),
		OCEAN("ים - כחול וטורקיז"),
		SUNSET("שקיעה - כתום, ורוד וסגול"),
		GALAXY("גלקסיה - סגול וכחול עם ניצוצות"),

		// -- text tricks --
		TYPEWRITER("השם נכתב אות אחרי אות"),
		GLITCH("אותיות אקראיות מתערבלות"),

		// -- the display itself --
		FADE("השם נעלם ומופיע (שקיפות אמיתית)"),
		ZOOM("השם גדל וקטן");

		private final String description;

		Animation(String description) {
			this.description = description;
		}

		public String description() {
			return description;
		}

		/** True when the effect needs the colours the author wrote. */
		public boolean usesOwnColours() {
			return this == FLOW || this == FLOW_REVERSE || this == CYCLE;
		}

		public static Animation parse(String raw) {
			if (raw == null) {
				return NONE;
			}
			try {
				return valueOf(raw.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return NONE;
			}
		}
	}
}
