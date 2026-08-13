package dev.adamk.checkpoints;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permissible;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

/**
 * Checkpoints built from pressure plates and buttons.
 *
 * <p>Every point carries a set of options (name, particles, sound, message ...)
 * which travel with the item: copying a placed point in creative hands back an
 * item that rebuilds it exactly.
 */
public final class CheckPointsPlugin extends JavaPlugin implements Listener {
	private static final String DEFAULT_INVS_NAME = "invs check point";
	private static final String DEFAULT_RESET_NAME = "invs reset point";
	private static final String DEFAULT_PLAIN_NAME = "invs pressure plate";
	private static final String DEFAULT_KILL_NAME = "invs kill plate";

	private NamespacedKey markerKey;
	private NamespacedKey invisibleKey;
	private NamespacedKey typeKey;
	private NamespacedKey legacyResetKey;
	private final Map<String, NamespacedKey> optionKeys = new LinkedHashMap<>();

	private CheckpointManager manager;
	private CourseManager courses;
	private AutoUpdater updater;

	// Death reason to apply to the next death of each player killed by a plate.
	private final Map<UUID, String> pendingDeathMessages = new ConcurrentHashMap<>();

	@Override
	public void onEnable() {
		markerKey = new NamespacedKey(this, "checkpoint");
		invisibleKey = new NamespacedKey(this, "invisible");
		typeKey = new NamespacedKey(this, "type");
		legacyResetKey = new NamespacedKey(this, "reset");
		for (String option : Checkpoint.KEYS) {
			optionKeys.put(option, new NamespacedKey(this, "opt_" + option));
		}

		manager = new CheckpointManager(this);
		manager.load();

		courses = new CourseManager(this);
		courses.load();

		updater = new AutoUpdater(this);
		getServer().getPluginManager().registerEvents(updater, this);
		updater.checkOnStartup();

		LockManager locks = new LockManager(this);
		locks.start();
		getServer().getPluginManager().registerEvents(locks, this);
		getCommand("lock").setExecutor(locks);
		getCommand("unlock").setExecutor(locks);

		getServer().getPluginManager().registerEvents(this, this);
		getCommand("checkpoint").setExecutor(this::onCheckpointCommand);
		getCommand("checkpoint").setTabCompleter(this::completeCheckpoint);
		getCommand("respawn").setExecutor(this::onRespawnCommand);
		getCommand("resetrespawn").setExecutor(this::onResetRespawnCommand);
		getCommand("guide").setExecutor(this::onGuideCommand);
		getCommand("invisibleplate").setExecutor(this::onInvisiblePlateCommand);

		for (Player player : Bukkit.getOnlinePlayers()) {
			manager.updateAllFor(player);
		}

		// Stepping on a plate makes the server broadcast its pressed/released
		// block states, which un-hides invisible plates. Chunk updates can do
		// the same - so re-hide them every second as a catch-all.
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (Checkpoint checkpoint : manager.all()) {
				if (checkpoint.invisible()) {
					manager.hideFromAll(checkpoint);
				}
			}
		}, 20L, 20L);

		Bukkit.getScheduler().runTaskTimer(this, this::showParticles, 10L, 10L);

		// Moving names: one nudge every other tick reads as a smooth flow.
		Bukkit.getScheduler().runTaskTimer(this, this::animateNames, 2L, 2L);

		// Primary detection: check what each player is standing on. Events can
		// be unreliable for invisible plates (the client believes it is air).
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				Block feet = player.getLocation().getBlock();
				Checkpoint checkpoint = manager.get(feet);
				if (checkpoint != null && Tag.PRESSURE_PLATES.isTagged(feet.getType())) {
					trigger(player, feet, checkpoint);
				}
			}
		}, 5L, 5L);
	}

	// =========================== commands ===========================

	private boolean onCheckpointCommand(CommandSender sender, Command command, String label, String[] args) {
		String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

		// No player state needed, so this also works from the server console.
		if (sub.equals("update")) {
			if (!sender.hasPermission("checkpoints.create")) {
				sender.sendMessage(Component.text("אין לך הרשאה לפקודה הזו", NamedTextColor.RED));
				return true;
			}
			sender.sendMessage(Component.text("Checking GitHub for a newer CheckPoints build...", NamedTextColor.GRAY));
			updater.checkNow(sender::sendMessage);
			return true;
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only."));
			return true;
		}

		// Everything here builds or edits a point; viewing a leaderboard should
		// not require the same access as placing and configuring checkpoints.
		boolean viewingLeaderboard = sub.equals("course") && args.length >= 2 && args[1].equalsIgnoreCase("top");
		if (!viewingLeaderboard && !player.hasPermission("checkpoints.create")) {
			player.sendMessage(Component.text("אין לך הרשאה לפקודה הזו", NamedTextColor.RED));
			return true;
		}

		switch (sub) {
			case "new", "create":
				return createCommand(player, args);
			case "name":
				return editText(player, Checkpoint.NAME, join(args, 1), "השם");
			case "deathmsg", "deathmessage":
				return editDeathMessage(player, join(args, 1));
			case "message", "msg":
				return editText(player, Checkpoint.MESSAGE, join(args, 1), "ההודעה");
			case "particle", "particles":
				return editParticle(player, join(args, 1));
			case "particlemode", "pmode":
				return editEnum(player, Checkpoint.PARTICLE_MODE, join(args, 1),
						"מצב החלקיקים", Checkpoint.ParticleMode.values());
			case "msgmode", "messagemode":
				return editEnum(player, Checkpoint.MESSAGE_MODE, join(args, 1),
						"מצב ההודעה", Checkpoint.MessageMode.values());
			case "sound":
				return editSound(player, join(args, 1));
			case "anim", "animation":
				return editAnimation(player, join(args, 1));
			case "target":
				return editTarget(player);
			case "command", "cmd":
				return editCommand(player, join(args, 1));
			case "effect":
				return editEffect(player, join(args, 1));
			case "launch", "jump":
				return editLaunch(player, join(args, 1));
			case "permission", "perm":
				return editPermission(player, join(args, 1));
			case "cooldown", "cd":
				return editCooldown(player, join(args, 1));
			case "once":
				return editOnce(player, join(args, 1));
			case "course":
				return courseCommand(player, args);
			case "role":
				return editEnum(player, Checkpoint.ROLE, join(args, 1), "התפקיד", Checkpoint.Role.values());
			case "list":
				return listCommand(player);
			case "copy":
				return copyCommand(player);
			case "paste":
				return pasteCommand(player);
			case "undo":
				return undoCommand(player);
			case "heal":
				return editHeal(player, join(args, 1));
			case "info":
				return infoCommand(player);
			default:
				return giveItemCommand(player, args);
		}
	}

	/** The original form: /checkpoint [reset|kill] using the item in hand. */
	private boolean giveItemCommand(Player player, String[] args) {
		Checkpoint.Type type = Checkpoint.Type.CHECKPOINT;
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("reset")) {
				type = Checkpoint.Type.RESET;
			} else if (args[0].equalsIgnoreCase("kill")) {
				type = Checkpoint.Type.KILL;
			}
		}

		ItemStack held = player.getInventory().getItemInMainHand();
		boolean fromHand = !held.getType().isAir() && CheckpointManager.isCheckpointBlock(held.getType());
		// The classic form: an empty hand means "give me an invisible one".
		giveOrConvert(player, type, !fromHand, !fromHand, Map.of());

		player.sendMessage(Component.text("טיפ: תן לו שם בסדן או עם /checkpoint name", NamedTextColor.GRAY));
		if (type == Checkpoint.Type.KILL) {
			player.sendMessage(Component.text(
					"טיפ: /checkpoint deathmsg קובע את סיבת המוות", NamedTextColor.GRAY));
		}
		return true;
	}

	/**
	 * /checkpoint new &lt;type&gt; [-i] [-p spec] [-s sound] [-a anim] [-d "reason"]
	 * [-c course] [-r role] [-perm node] [-cd seconds] [-once] [-heal] [name...]
	 */
	private boolean createCommand(Player player, String[] args) {
		if (args.length < 2) {
			player.sendMessage(Component.text(
					"שימוש: /checkpoint new <check|reset|kill|teleport|command|effect|launch|plain> "
							+ "[-i] [-p חלקיק] [-s צליל] [-a אנימציה] [-d \"סיבת מוות\"]",
					NamedTextColor.RED));
			player.sendMessage(Component.text(
					"[-c מסלול] [-r START|CHECK|FINISH] [-perm הרשאה] [-cd שניות] [-once] [-heal] [שם]",
					NamedTextColor.RED));
			player.sendMessage(Component.text(
					"דוגמה: /checkpoint new kill -p DUST:#FF0000 -a FIRE <red><bold>death point",
					NamedTextColor.GRAY));
			return true;
		}

		Checkpoint.Type type = args[1].equalsIgnoreCase("plain")
				? Checkpoint.Type.PLAIN
				: Checkpoint.Type.parse(args[1]);

		// Quoted values keep their spaces, so -d can hold a whole sentence.
		List<String> tokens = tokenize(join(args, 2));
		boolean invisible = false;
		Map<String, String> options = new LinkedHashMap<>();
		int index = 0;

		while (index < tokens.size() && tokens.get(index).startsWith("-") && tokens.get(index).length() > 1) {
			String flag = tokens.get(index).toLowerCase(Locale.ROOT);
			String value = index + 1 < tokens.size() ? tokens.get(index + 1) : null;

			switch (flag) {
				case "-i" -> {
					invisible = true;
					index++;
				}
				case "-heal" -> {
					options.put(Checkpoint.HEAL, "true");
					index++;
				}
				case "-p" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסר סוג חלקיק אחרי -p", NamedTextColor.RED));
						return true;
					}
					if (!ParticleSpec.isValid(value)) {
						player.sendMessage(Component.text("סוג חלקיק לא מוכר: " + value, NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.PARTICLE, value.toUpperCase(Locale.ROOT));
					index += 2;
				}
				case "-s" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסר שם צליל אחרי -s", NamedTextColor.RED));
						return true;
					}
					if (resolveSound(value) == null && !isNone(value)) {
						player.sendMessage(Component.text("צליל לא מוכר: " + value, NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.SOUND, value.toUpperCase(Locale.ROOT));
					index += 2;
				}
				case "-d" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסרה סיבת מוות אחרי -d", NamedTextColor.RED));
						return true;
					}
					if (type != Checkpoint.Type.KILL) {
						player.sendMessage(Component.text(
								"סיבת מוות אפשרית רק על לוחית הרג (kill)", NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.DEATH_MESSAGE, value);
					index += 2;
				}
				case "-a" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסר שם אנימציה אחרי -a", NamedTextColor.RED));
						return true;
					}
					Checkpoint.Animation animation = Checkpoint.Animation.parse(value);
					if (!animation.name().equalsIgnoreCase(value.trim())) {
						player.sendMessage(Component.text(
								"אנימציה לא מוכרת: " + value + " - /checkpoint anim list מציג את כולן",
								NamedTextColor.RED));
						return true;
					}
					if (animation != Checkpoint.Animation.NONE) {
						options.put(Checkpoint.ANIMATION, animation.name());
					}
					index += 2;
				}
				case "-c" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסר שם מסלול אחרי -c", NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.COURSE, value);
					index += 2;
				}
				case "-r" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסר תפקיד אחרי -r", NamedTextColor.RED));
						return true;
					}
					if (!List.of("START", "CHECK", "FINISH").contains(value.trim().toUpperCase(Locale.ROOT))) {
						player.sendMessage(Component.text(
								"תפקיד לא מוכר: " + value + " - START | CHECK | FINISH", NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.ROLE, value.trim().toUpperCase(Locale.ROOT));
					index += 2;
				}
				case "-perm" -> {
					if (value == null) {
						player.sendMessage(Component.text("חסרה הרשאה אחרי -perm", NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.PERMISSION, value);
					index += 2;
				}
				case "-cd" -> {
					if (value == null || parseInt(value, -1) < 0) {
						player.sendMessage(Component.text("צריך מספר שניות אחרי -cd", NamedTextColor.RED));
						return true;
					}
					options.put(Checkpoint.COOLDOWN, value.trim());
					index += 2;
				}
				case "-once" -> {
					options.put(Checkpoint.ONCE, "true");
					index++;
				}
				default -> {
					// Not a flag we know - treat it as the start of the name.
					index = tokens.size() + 1;
				}
			}
		}

		if (index <= tokens.size()) {
			String name = String.join(" ", tokens.subList(Math.min(index, tokens.size()), tokens.size()));
			if (!name.isBlank()) {
				options.put(Checkpoint.NAME, name);
			}
		}

		// The animation can only be judged once the name is known.
		Checkpoint.Animation animation = Checkpoint.Animation.parse(options.get(Checkpoint.ANIMATION));
		if (animation != Checkpoint.Animation.NONE) {
			String name = options.get(Checkpoint.NAME);
			if (name == null) {
				player.sendMessage(Component.text(
						"אנימציה צריכה שם - תוסיף אותו בסוף הפקודה", NamedTextColor.RED));
				return true;
			}
			if (animation.usesOwnColours() && NameAnimator.render(name, animation, 0) == null) {
				player.sendMessage(Component.text(
						"ל-" + animation.name() + " צריך שם עם כמה צבעים - למשל "
								+ "<gradient:#33ccff:#001f7d>טקסט</gradient>", NamedTextColor.RED));
				return true;
			}
		}

		ItemStack held = player.getInventory().getItemInMainHand();
		boolean fromHand = !held.getType().isAir() && CheckpointManager.isCheckpointBlock(held.getType());
		giveOrConvert(player, type, !fromHand, invisible, options);

		player.sendMessage(Component.text("הכל מוכן - פשוט תניח את הפריט.", NamedTextColor.GRAY));
		return true;
	}

	/** Splits on spaces, but keeps "quoted values" in one piece. */
	private static List<String> tokenize(String input) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean quoted = false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (c == ' ' && !quoted) {
				if (current.length() > 0) {
					tokens.add(current.toString());
					current.setLength(0);
				}
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) {
			tokens.add(current.toString());
		}
		return tokens;
	}

	/** Tags the held plate/button, or hands over a new one. */
	private void giveOrConvert(Player player, Checkpoint.Type type, boolean giveNew,
			boolean invisible, Map<String, String> options) {
		NamedTextColor color = switch (type) {
			case RESET -> NamedTextColor.GOLD;
			case KILL -> NamedTextColor.RED;
			case TELEPORT, EFFECT -> NamedTextColor.LIGHT_PURPLE;
			case COMMAND -> NamedTextColor.YELLOW;
			case LAUNCH -> NamedTextColor.WHITE;
			case PLAIN -> NamedTextColor.WHITE;
			case CHECKPOINT -> NamedTextColor.AQUA;
		};
		String label = switch (type) {
			case RESET -> "נקודת איפוס ריספאון";
			case KILL -> "לוחית הרג";
			case TELEPORT -> "לוחית טלפורט";
			case COMMAND -> "לוחית פקודה";
			case EFFECT -> "לוחית אפקט";
			case LAUNCH -> "לוחית שיגור";
			case PLAIN -> "לוחית רגילה";
			case CHECKPOINT -> "צ'קפוינט";
		};

		if (!giveNew) {
			ItemStack held = player.getInventory().getItemInMainHand();
			markItem(held, invisible, type, options);
			player.getInventory().setItemInMainHand(held);
			player.sendMessage(Component.text("הפריט שביד הפך ל" + label
					+ (invisible ? " בלתי נראה" : "") + "!", color));
			return;
		}

		String defaultName = switch (type) {
			case RESET -> DEFAULT_RESET_NAME;
			case KILL -> DEFAULT_KILL_NAME;
			case TELEPORT -> "invs teleport point";
			case COMMAND -> "invs command point";
			case EFFECT -> "invs effect point";
			case LAUNCH -> "invs launch point";
			case PLAIN -> DEFAULT_PLAIN_NAME;
			case CHECKPOINT -> DEFAULT_INVS_NAME;
		};
		ItemStack plate = new ItemStack(Material.STONE_PRESSURE_PLATE);
		plate.editMeta(meta -> meta.displayName(Component.text(defaultName, color)));
		markItem(plate, invisible, type, options);
		player.getInventory().addItem(plate);
		player.sendMessage(Component.text("קיבלת " + label + (invisible ? " בלתי נראה" : ""), color));
	}

	private boolean onInvisiblePlateCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only."));
			return true;
		}
		ItemStack held = player.getInventory().getItemInMainHand();
		boolean fromHand = !held.getType().isAir() && CheckpointManager.isCheckpointBlock(held.getType());
		if (fromHand) {
			markItem(held, true, Checkpoint.Type.PLAIN, Map.of());
			player.getInventory().setItemInMainHand(held);
			player.sendMessage(Component.text(
					"הפריט שביד יהיה בלתי נראה כשתניח אותו (עדיין עובד עם רדסטון)", NamedTextColor.GREEN));
			return true;
		}
		giveOrConvert(player, Checkpoint.Type.PLAIN, true, true, Map.of());
		return true;
	}

	private boolean onRespawnCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only."));
			return true;
		}
		Location target = player.getRespawnLocation();
		if (target == null) {
			player.teleport(player.getWorld().getSpawnLocation());
			player.sendMessage(Component.text("שוגרת לספאון של העולם", NamedTextColor.YELLOW));
			return true;
		}
		player.teleport(target);
		player.sendMessage(Component.text("שוגרת לנקודת הריספאון שלך", NamedTextColor.AQUA));
		return true;
	}

	private boolean onResetRespawnCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only."));
			return true;
		}
		boolean had = player.getRespawnLocation() != null;
		player.setRespawnLocation(null);
		manager.clearPlayerSpawn(player.getUniqueId());
		player.sendMessage(had
				? Component.text("הריספאון שלך אופס - תחזור לספאון של העולם", NamedTextColor.GOLD)
				: Component.text("ממילא לא היה לך ריספאון מיוחד", NamedTextColor.GRAY));
		return true;
	}

	// Points to the Gemini conversation that turns Hebrew requests into
	// ready-to-paste commands; update this if the shared link ever changes.
	private static final String GUIDE_LINK = "https://share.gemini.google/iYzAHfZLYF3p";

	private boolean onGuideCommand(CommandSender sender, Command command, String label, String[] args) {
		Component link = Component.text("📖 CheckPoints Guide (Gemini)", NamedTextColor.AQUA)
				.decorate(TextDecoration.UNDERLINED)
				.clickEvent(ClickEvent.openUrl(GUIDE_LINK))
				.hoverEvent(HoverEvent.showText(Component.text("Click to open: " + GUIDE_LINK, NamedTextColor.GRAY)));
		sender.sendMessage(Component.text("── ", NamedTextColor.DARK_GRAY)
				.append(link)
				.append(Component.text(" ──", NamedTextColor.DARK_GRAY)));
		return true;
	}

	// ------------------------ per-point editing ------------------------

	/** The registered point the player is looking at, or standing on. */
	private Checkpoint targeted(Player player) {
		return targeted(player, 8);
	}

	/**
	 * Wider range for /checkpoint target: you stand at the destination and look
	 * back at the plate, which is often far from where you are placing it.
	 */
	private Checkpoint targeted(Player player, int range) {
		Checkpoint feet = manager.get(player.getLocation().getBlock());
		if (feet != null) {
			return feet;
		}
		for (Block block : player.getLineOfSight(null, range)) {
			Checkpoint checkpoint = manager.get(block);
			if (checkpoint != null) {
				return checkpoint;
			}
		}
		return null;
	}

	private Checkpoint requireTarget(Player player) {
		return requireTarget(player, 8);
	}

	private Checkpoint requireTarget(Player player, int range) {
		Checkpoint checkpoint = targeted(player, range);
		if (checkpoint == null) {
			player.sendMessage(Component.text(
					"תעמוד על הנקודה או תסתכל עליה ואז תריץ את הפקודה", NamedTextColor.RED));
		}
		return checkpoint;
	}

	private boolean editText(Player player, String option, String raw, String label) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (raw.isBlank()) {
			manager.setOption(checkpoint, option, null, player);
			player.sendMessage(Component.text(label + " הוסר", NamedTextColor.YELLOW));
			return true;
		}
		if (isNone(raw)) {
			manager.setOption(checkpoint, option, "NONE", player);
			player.sendMessage(Component.text(label + " כובה", NamedTextColor.YELLOW));
			return true;
		}
		manager.setOption(checkpoint, option, raw, player);
		player.sendMessage(Component.text(label + " עודכן: ", NamedTextColor.GREEN)
				.append(TextFormat.parse(raw, player)));
		return true;
	}

	private boolean editDeathMessage(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (checkpoint.type() != Checkpoint.Type.KILL) {
			player.sendMessage(Component.text("זו לא לוחית הרג", NamedTextColor.RED));
			return true;
		}
		if (raw.isBlank()) {
			manager.setOption(checkpoint, Checkpoint.DEATH_MESSAGE, null, player);
			player.sendMessage(Component.text(
					"סיבת המוות אופסה - עכשיו זו הודעת המוות הרגילה", NamedTextColor.YELLOW));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.DEATH_MESSAGE, raw, player);
		player.sendMessage(Component.text("סיבת המוות נקבעה: ", NamedTextColor.GREEN)
				.append(TextFormat.parse(raw, player).colorIfAbsent(NamedTextColor.RED)));
		return true;
	}

	private boolean editParticle(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		String value = raw.trim();
		if (value.isEmpty() || value.equalsIgnoreCase("default")) {
			manager.setOption(checkpoint, Checkpoint.PARTICLE, null, player);
			player.sendMessage(Component.text("החלקיקים חזרו לברירת המחדל ("
					+ checkpoint.with(Checkpoint.PARTICLE, null).particle() + ")", NamedTextColor.YELLOW));
			return true;
		}
		if (!ParticleSpec.isValid(value)) {
			player.sendMessage(Component.text("סוג חלקיק לא מוכר: " + value, NamedTextColor.RED));
			player.sendMessage(Component.text(
					"דוגמאות: FLAME | HEART | HAPPY_VILLAGER | ENCHANT | DUST:#FF5555 | none",
					NamedTextColor.GRAY));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.PARTICLE, value.toUpperCase(Locale.ROOT), player);
		player.sendMessage(ParticleSpec.parse(value) == null
				? Component.text("החלקיקים כובו על הנקודה הזו", NamedTextColor.YELLOW)
				: Component.text("החלקיקים עודכנו: " + value, NamedTextColor.GREEN));
		return true;
	}

	private boolean editEnum(Player player, String option, String raw, String label, Enum<?>[] values) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		String value = raw.trim().toUpperCase(Locale.ROOT);
		boolean known = false;
		for (Enum<?> candidate : values) {
			if (candidate.name().equals(value)) {
				known = true;
				break;
			}
		}
		if (!known) {
			List<String> names = new ArrayList<>();
			for (Enum<?> candidate : values) {
				names.add(candidate.name());
			}
			player.sendMessage(Component.text(
					"ערך לא מוכר. אפשרויות: " + String.join(" | ", names), NamedTextColor.RED));
			return true;
		}
		manager.setOption(checkpoint, option, value, player);
		player.sendMessage(Component.text(label + " עודכן: " + value, NamedTextColor.GREEN));
		return true;
	}

	private boolean editSound(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		String value = raw.trim();
		if (value.isEmpty() || value.equalsIgnoreCase("default")) {
			manager.setOption(checkpoint, Checkpoint.SOUND, null, player);
			player.sendMessage(Component.text("הצליל חזר לברירת המחדל", NamedTextColor.YELLOW));
			return true;
		}
		if (isNone(value)) {
			manager.setOption(checkpoint, Checkpoint.SOUND, "NONE", player);
			player.sendMessage(Component.text("הצליל כובה", NamedTextColor.YELLOW));
			return true;
		}
		Sound sound = resolveSound(value);
		if (sound == null) {
			player.sendMessage(Component.text("צליל לא מוכר: " + value, NamedTextColor.RED));
			player.sendMessage(Component.text(
					"דוגמאות: ENTITY_EXPERIENCE_ORB_PICKUP | BLOCK_NOTE_BLOCK_PLING | ENTITY_PLAYER_LEVELUP",
					NamedTextColor.GRAY));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.SOUND, value.toUpperCase(Locale.ROOT), player);
		player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
		player.sendMessage(Component.text("הצליל עודכן: " + value.toUpperCase(Locale.ROOT), NamedTextColor.GREEN));
		return true;
	}

	private boolean editHeal(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		boolean on = raw.isBlank() ? !checkpoint.heal() : raw.trim().equalsIgnoreCase("true")
				|| raw.trim().equalsIgnoreCase("on")
				|| raw.trim().equals("1");
		manager.setOption(checkpoint, Checkpoint.HEAL, on ? "true" : null, player);
		player.sendMessage(on
				? Component.text("הנקודה תמלא לב ואוכל", NamedTextColor.GREEN)
				: Component.text("הנקודה לא תמלא לב ואוכל", NamedTextColor.YELLOW));
		return true;
	}

	// ------------------------ the special types ------------------------

	// Setting a target means standing far from the plate and looking back at
	// it, so this needs much more reach than editing a point up close.
	private static final int TARGET_RANGE = 120;

	private boolean editTarget(Player player) {
		Checkpoint checkpoint = requireTarget(player, TARGET_RANGE);
		if (checkpoint == null) {
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.TARGET, formatLocation(player.getLocation()), player);
		player.sendMessage(Component.text("היעד נקבע למקום שאתה עומד בו", NamedTextColor.GREEN));
		player.sendMessage(Component.text(
				"טיפ: תעמוד ביעד, תסתכל על הלוחית ותריץ שוב אם תרצה לשנות", NamedTextColor.GRAY));
		return true;
	}

	private boolean editCommand(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (raw.isBlank()) {
			manager.setOption(checkpoint, Checkpoint.COMMAND, null, player);
			player.sendMessage(Component.text("הפקודה הוסרה", NamedTextColor.YELLOW));
			return true;
		}

		String command = raw;
		boolean asPlayer = false;
		if (command.toLowerCase(Locale.ROOT).startsWith("player ")) {
			asPlayer = true;
			command = command.substring(7);
		}
		manager.setOption(checkpoint, Checkpoint.COMMAND, command, player);
		manager.setOption(manager.byKey(checkpoint.key()), Checkpoint.COMMAND_AS,
				asPlayer ? "PLAYER" : null, player);

		player.sendMessage(Component.text("הפקודה נקבעה: ", NamedTextColor.GREEN)
				.append(Component.text(command, NamedTextColor.WHITE)));
		player.sendMessage(Component.text(asPlayer
				? "תרוץ בשם השחקן שדורך" : "תרוץ מהקונסולה. %player% יוחלף בשם השחקן", NamedTextColor.GRAY));
		return true;
	}

	private boolean editEffect(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (raw.isBlank() || isNone(raw)) {
			manager.setOption(checkpoint, Checkpoint.EFFECT, null, player);
			player.sendMessage(Component.text("האפקט הוסר", NamedTextColor.YELLOW));
			return true;
		}
		String spec = raw.trim().replace(' ', ':');
		if (resolveEffect(spec.split(":")[0]) == null) {
			player.sendMessage(Component.text("אפקט לא מוכר: " + spec.split(":")[0], NamedTextColor.RED));
			player.sendMessage(Component.text(
					"דוגמאות: speed:2:10 | jump_boost:3:15 | night_vision:1:60", NamedTextColor.GRAY));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.EFFECT, spec, player);
		player.sendMessage(Component.text("האפקט נקבע: " + spec, NamedTextColor.GREEN));
		return true;
	}

	private boolean editLaunch(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (raw.isBlank()) {
			manager.setOption(checkpoint, Checkpoint.LAUNCH, null, player);
			player.sendMessage(Component.text("עוצמת השיגור חזרה לברירת המחדל", NamedTextColor.YELLOW));
			return true;
		}
		String spec = raw.trim().replace(' ', ',');
		manager.setOption(checkpoint, Checkpoint.LAUNCH, spec, player);
		player.sendMessage(Component.text("השיגור נקבע: " + spec, NamedTextColor.GREEN));
		player.sendMessage(Component.text(
				"הפורמט הוא קדימה,למעלה - למשל 1.2,0.8 לזינוק קדימה", NamedTextColor.GRAY));
		return true;
	}

	// ------------------------ access control ------------------------

	private boolean editPermission(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (raw.isBlank() || isNone(raw)) {
			manager.setOption(checkpoint, Checkpoint.PERMISSION, null, player);
			player.sendMessage(Component.text("הנקודה פתוחה לכולם", NamedTextColor.YELLOW));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.PERMISSION, raw.trim(), player);
		player.sendMessage(Component.text("רק מי שיש לו ", NamedTextColor.GREEN)
				.append(Component.text(raw.trim(), NamedTextColor.WHITE))
				.append(Component.text(" יוכל להשתמש", NamedTextColor.GREEN)));
		return true;
	}

	private boolean editCooldown(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		int seconds = raw.isBlank() ? 0 : parseInt(raw, -1);
		if (seconds < 0) {
			player.sendMessage(Component.text("צריך מספר שניות", NamedTextColor.RED));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.COOLDOWN, seconds == 0 ? null : String.valueOf(seconds), player);
		player.sendMessage(seconds == 0
				? Component.text("הקירור בוטל", NamedTextColor.YELLOW)
				: Component.text("קירור של " + seconds + " שניות", NamedTextColor.GREEN));
		return true;
	}

	private boolean editOnce(Player player, String raw) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		boolean on = raw.isBlank() ? !checkpoint.once() : raw.trim().equalsIgnoreCase("true")
				|| raw.trim().equalsIgnoreCase("on") || raw.trim().equals("1");
		manager.setOption(checkpoint, Checkpoint.ONCE, on ? "true" : null, player);
		if (!on) {
			manager.clearUsed(checkpoint.key());
		}
		player.sendMessage(on
				? Component.text("כל שחקן יוכל להשתמש פעם אחת בלבד", NamedTextColor.GREEN)
				: Component.text("אפשר להשתמש שוב ושוב (וההיסטוריה אופסה)", NamedTextColor.YELLOW));
		return true;
	}

	// ------------------------ courses ------------------------

	private boolean courseCommand(Player player, String[] args) {
		if (args.length >= 2 && args[1].equalsIgnoreCase("top")) {
			String name = args.length >= 3 ? join(args, 2) : courseOfTarget(player);
			if (name == null) {
				player.sendMessage(Component.text("איזה מסלול? /checkpoint course top <שם>", NamedTextColor.RED));
				return true;
			}
			courses.showLeaderboard(player, name);
			return true;
		}
		if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
			String name = join(args, 2);
			player.sendMessage(courses.clear(name)
					? Component.text("השיאים של " + name + " נמחקו", NamedTextColor.YELLOW)
					: Component.text("אין מסלול בשם " + name, NamedTextColor.RED));
			return true;
		}

		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		String name = join(args, 1);
		if (name.isBlank() || isNone(name)) {
			manager.setOption(checkpoint, Checkpoint.COURSE, null, player);
			player.sendMessage(Component.text("הנקודה הוסרה מהמסלול", NamedTextColor.YELLOW));
			return true;
		}
		manager.setOption(checkpoint, Checkpoint.COURSE, name, player);
		player.sendMessage(Component.text("הנקודה שייכת עכשיו למסלול ", NamedTextColor.GREEN)
				.append(Component.text(name, NamedTextColor.WHITE))
				.append(Component.text(" (תפקיד: " + checkpoint.role() + ")", NamedTextColor.GRAY)));
		player.sendMessage(Component.text(
				"תקבע תפקיד עם /checkpoint role START|CHECK|FINISH", NamedTextColor.GRAY));
		return true;
	}

	private String courseOfTarget(Player player) {
		Checkpoint checkpoint = targeted(player);
		return checkpoint == null ? null : checkpoint.course();
	}

	// ------------------------ build helpers ------------------------

	private boolean listCommand(Player player) {
		List<Checkpoint> all = new ArrayList<>();
		for (Checkpoint checkpoint : manager.all()) {
			all.add(checkpoint);
		}
		if (all.isEmpty()) {
			player.sendMessage(Component.text("אין עדיין אף נקודה", NamedTextColor.GRAY));
			return true;
		}
		all.sort(Comparator.comparing(Checkpoint::world).thenComparingInt(Checkpoint::x));

		player.sendMessage(Component.text("── " + all.size() + " נקודות ──", NamedTextColor.AQUA));
		int shown = 0;
		for (Checkpoint checkpoint : all) {
			if (shown++ >= 40) {
				player.sendMessage(Component.text("...ועוד " + (all.size() - 40), NamedTextColor.GRAY));
				break;
			}
			String where = checkpoint.x() + " " + checkpoint.y() + " " + checkpoint.z();
			Component line = Component.text(checkpoint.type().name() + " ", NamedTextColor.WHITE)
					.append(Component.text(where, NamedTextColor.GRAY));
			if (checkpoint.name() != null) {
				line = line.append(Component.text(" · ", NamedTextColor.DARK_GRAY))
						.append(TextFormat.parse(checkpoint.name(), player));
			}
			if (checkpoint.course() != null) {
				line = line.append(Component.text(" [" + checkpoint.course() + "/"
						+ checkpoint.role() + "]", NamedTextColor.DARK_AQUA));
			}
			player.sendMessage(line.clickEvent(ClickEvent.runCommand(
							"/tp " + checkpoint.x() + " " + (checkpoint.y() + 1) + " " + checkpoint.z()))
					.hoverEvent(HoverEvent.showText(Component.text("לחץ כדי לשגר לשם"))));
		}
		return true;
	}

	// the last copied option set, and the last change, per player
	private final Map<UUID, Map<String, String>> clipboards = new ConcurrentHashMap<>();
	private final Map<UUID, Checkpoint> undoStack = new ConcurrentHashMap<>();

	private boolean copyCommand(Player player) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		clipboards.put(player.getUniqueId(), new LinkedHashMap<>(checkpoint.options()));
		player.sendMessage(Component.text(
				"ההגדרות הועתקו (" + checkpoint.options().size() + " ערכים)", NamedTextColor.GREEN));
		return true;
	}

	private boolean pasteCommand(Player player) {
		Map<String, String> clipboard = clipboards.get(player.getUniqueId());
		if (clipboard == null) {
			player.sendMessage(Component.text("קודם /checkpoint copy", NamedTextColor.RED));
			return true;
		}
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}

		undoStack.put(player.getUniqueId(), checkpoint);
		Checkpoint current = checkpoint;
		for (String key : Checkpoint.KEYS) {
			manager.setOption(current, key, clipboard.get(key), player);
			current = manager.byKey(current.key());
		}
		player.sendMessage(Component.text("ההגדרות הודבקו", NamedTextColor.GREEN));
		player.sendMessage(Component.text("/checkpoint undo מבטל", NamedTextColor.GRAY));
		return true;
	}

	private boolean undoCommand(Player player) {
		Checkpoint previous = undoStack.remove(player.getUniqueId());
		if (previous == null) {
			player.sendMessage(Component.text("אין מה לבטל", NamedTextColor.GRAY));
			return true;
		}
		Checkpoint current = manager.byKey(previous.key());
		if (current == null) {
			player.sendMessage(Component.text("הנקודה כבר לא קיימת", NamedTextColor.RED));
			return true;
		}
		for (String key : Checkpoint.KEYS) {
			manager.setOption(current, key, previous.option(key), player);
			current = manager.byKey(current.key());
		}
		player.sendMessage(Component.text("השינוי בוטל", NamedTextColor.GREEN));
		return true;
	}

	private boolean infoCommand(Player player) {
		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		player.sendMessage(Component.text("── הנקודה הזו ──", NamedTextColor.AQUA));
		player.sendMessage(line("סוג", checkpoint.type().name()));
		player.sendMessage(line("בלתי נראה", checkpoint.invisible() ? "כן" : "לא"));
		player.sendMessage(Component.text("שם: ", NamedTextColor.GRAY)
				.append(checkpoint.name() == null
						? Component.text("(אין)", NamedTextColor.DARK_GRAY)
						: TextFormat.parse(checkpoint.name(), player)));
		player.sendMessage(line("אנימציית שם", checkpoint.animation().name()));
		player.sendMessage(line("חלקיקים", checkpoint.particle() + " (" + checkpoint.particleMode() + ")"));
		player.sendMessage(line("צליל", checkpoint.sound()));
		player.sendMessage(line("הודעה", checkpoint.message() == null
				? "ברירת מחדל (" + checkpoint.messageMode() + ")"
				: checkpoint.message() + " (" + checkpoint.messageMode() + ")"));
		player.sendMessage(line("ממלא לב ואוכל", checkpoint.heal() ? "כן" : "לא"));

		switch (checkpoint.type()) {
			case KILL -> player.sendMessage(line("סיבת מוות",
					checkpoint.deathMessage() == null ? "ברירת מחדל" : checkpoint.deathMessage()));
			case TELEPORT -> player.sendMessage(line("יעד",
					checkpoint.target() == null ? "(לא נקבע)" : checkpoint.target()));
			case COMMAND -> player.sendMessage(line("פקודה",
					checkpoint.command() == null ? "(לא נקבעה)"
							: checkpoint.command() + (checkpoint.commandAsConsole() ? " (קונסולה)" : " (שחקן)")));
			case EFFECT -> player.sendMessage(line("אפקט",
					checkpoint.effect() == null ? "(לא נקבע)" : checkpoint.effect()));
			case LAUNCH -> player.sendMessage(line("שיגור", checkpoint.launch()));
			default -> {
			}
		}

		if (checkpoint.permission() != null) {
			player.sendMessage(line("הרשאה נדרשת", checkpoint.permission()));
		}
		if (checkpoint.cooldown() > 0) {
			player.sendMessage(line("קירור", checkpoint.cooldown() + " שניות"));
		}
		if (checkpoint.once()) {
			player.sendMessage(line("שימוש חד־פעמי", "כן"));
		}
		if (checkpoint.course() != null) {
			player.sendMessage(line("מסלול", checkpoint.course() + " (" + checkpoint.role() + ")"));
		}
		return true;
	}

	private static Component line(String label, String value) {
		return Component.text(label + ": ", NamedTextColor.GRAY)
				.append(Component.text(value, NamedTextColor.WHITE));
	}

	// =========================== item tagging ===========================

	private void markItem(ItemStack item, boolean invisible, Checkpoint.Type type, Map<String, String> options) {
		item.editMeta(meta -> {
			PersistentDataContainer data = meta.getPersistentDataContainer();
			data.set(markerKey, PersistentDataType.BYTE, (byte) 1);
			data.set(invisibleKey, PersistentDataType.BYTE, (byte) (invisible ? 1 : 0));
			data.set(typeKey, PersistentDataType.STRING, type.name());
			for (Map.Entry<String, NamespacedKey> entry : optionKeys.entrySet()) {
				String value = options.get(entry.getKey());
				if (value == null || value.isBlank()) {
					data.remove(entry.getValue());
				} else {
					data.set(entry.getValue(), PersistentDataType.STRING, value);
				}
			}
		});
	}

	private Map<String, String> readOptions(ItemMeta meta) {
		Map<String, String> options = new LinkedHashMap<>();
		PersistentDataContainer data = meta.getPersistentDataContainer();
		for (Map.Entry<String, NamespacedKey> entry : optionKeys.entrySet()) {
			String value = data.get(entry.getValue(), PersistentDataType.STRING);
			if (value != null) {
				options.put(entry.getKey(), value);
			}
		}
		return options;
	}

	// =========================== events ===========================

	@EventHandler
	public void onPlace(BlockPlaceEvent event) {
		ItemStack item = event.getItemInHand();
		ItemMeta meta = item.getItemMeta();
		if (meta == null || !meta.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE)) {
			return;
		}
		Block block = event.getBlockPlaced();
		if (!CheckpointManager.isCheckpointBlock(block.getType())) {
			return;
		}

		PersistentDataContainer data = meta.getPersistentDataContainer();
		boolean invisible = data.getOrDefault(invisibleKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;

		Checkpoint.Type type;
		if (data.has(typeKey, PersistentDataType.STRING)) {
			type = Checkpoint.Type.parse(data.get(typeKey, PersistentDataType.STRING));
		} else {
			// Items handed out by older versions only carry the "reset" byte.
			type = data.getOrDefault(legacyResetKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1
					? Checkpoint.Type.RESET : Checkpoint.Type.CHECKPOINT;
		}

		Map<String, String> options = readOptions(meta);

		// A name typed in an anvil wins; the stock item names count as "no name".
		if (meta.hasDisplayName()) {
			String display = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
			if (!isDefaultName(display)) {
				options.put(Checkpoint.NAME, display);
			}
		}

		manager.register(block, invisible, type, options, event.getPlayer());
		event.getPlayer().sendMessage(Component.text(switch (type) {
			case RESET -> "נקודת האיפוס הונחה!";
			case KILL -> "לוחית ההרג הונחה!";
			case TELEPORT -> "לוחית הטלפורט הונחה! /checkpoint target";
			case COMMAND -> "לוחית הפקודה הונחה! /checkpoint command";
			case EFFECT -> "לוחית האפקט הונחה! /checkpoint effect";
			case LAUNCH -> "לוחית השיגור הונחה!";
			case PLAIN -> "הלוחית הבלתי נראית הונחה!";
			case CHECKPOINT -> "הצ'קפוינט הונח!";
		}, NamedTextColor.GREEN));
	}

	/**
	 * Copying a block in creative (pick-block) hands over an item carrying all
	 * of the point's settings, the way copying a container keeps its contents.
	 */
	@EventHandler(priority = EventPriority.HIGH)
	public void onCreativePick(InventoryCreativeEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)
				|| player.getGameMode() != GameMode.CREATIVE) {
			return;
		}
		ItemStack picked = event.getCursor();
		if (picked == null || !CheckpointManager.isCheckpointBlock(picked.getType())) {
			return;
		}

		Block target = player.getTargetBlockExact(6);
		if (target == null || target.getType() != picked.getType()) {
			return;
		}
		Checkpoint checkpoint = manager.get(target);
		if (checkpoint == null) {
			return;
		}

		ItemStack copy = picked.clone();
		markItem(copy, checkpoint.invisible(), checkpoint.type(), checkpoint.options());
		if (checkpoint.name() != null) {
			copy.editMeta(meta -> meta.displayName(
					TextFormat.parse(checkpoint.name(), player).colorIfAbsent(NamedTextColor.AQUA)));
		}
		event.setCursor(copy);
		player.sendActionBar(Component.text("הנקודה הועתקה עם כל ההגדרות", NamedTextColor.AQUA));
	}

	@EventHandler
	public void onBreak(BlockBreakEvent event) {
		Checkpoint checkpoint = manager.get(event.getBlock());
		if (checkpoint != null) {
			manager.unregister(checkpoint);
			event.getPlayer().sendMessage(Component.text(
					checkpoint.type() == Checkpoint.Type.PLAIN ? "הלוחית הבלתי נראית הוסרה" : "הצ'קפוינט הוסר",
					NamedTextColor.YELLOW));
			return;
		}
		scheduleNeighborCheck(event.getBlock());
	}

	@EventHandler
	public void onDestroy(BlockDestroyEvent event) {
		Checkpoint checkpoint = manager.get(event.getBlock());
		if (checkpoint != null) {
			manager.unregister(checkpoint);
			return;
		}
		scheduleNeighborCheck(event.getBlock());
	}

	/** A broken support block pops the plate off without its own break event. */
	private void scheduleNeighborCheck(Block block) {
		List<Checkpoint> nearby = new ArrayList<>();
		for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN,
				BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
			Checkpoint checkpoint = manager.get(block.getRelative(face));
			if (checkpoint != null) {
				nearby.add(checkpoint);
			}
		}
		if (nearby.isEmpty()) {
			return;
		}
		Bukkit.getScheduler().runTaskLater(this, () -> {
			for (Checkpoint checkpoint : nearby) {
				Block current = block.getWorld().getBlockAt(checkpoint.x(), checkpoint.y(), checkpoint.z());
				if (!CheckpointManager.isCheckpointBlock(current.getType())) {
					manager.unregister(checkpoint);
				}
			}
		}, 2L);
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Block block = event.getClickedBlock();
		if (block == null) {
			return;
		}

		// Right-clicking a kill block with a name tag sets its death reason.
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK
				&& event.getHand() == EquipmentSlot.HAND
				&& event.getPlayer().getInventory().getItemInMainHand().getType() == Material.NAME_TAG) {
			Checkpoint target = manager.get(block);
			if (target != null && target.type() == Checkpoint.Type.KILL) {
				event.setCancelled(true);
				applyNameTag(event.getPlayer(), target);
				return;
			}
		}

		boolean stepped = event.getAction() == Action.PHYSICAL
				&& Tag.PRESSURE_PLATES.isTagged(block.getType());
		boolean pressed = event.getAction() == Action.RIGHT_CLICK_BLOCK
				&& event.getHand() == EquipmentSlot.HAND
				&& Tag.BUTTONS.isTagged(block.getType());
		if (!stepped && !pressed) {
			return;
		}

		Checkpoint checkpoint = manager.get(block);
		if (checkpoint != null) {
			trigger(event.getPlayer(), block, checkpoint);
		}
	}

	/** Movement fallback: an invisible plate is client-side air. */
	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		if (!event.hasChangedBlock()) {
			return;
		}
		Block feet = event.getTo().getBlock();
		Checkpoint checkpoint = manager.get(feet);
		if (checkpoint != null && Tag.PRESSURE_PLATES.isTagged(feet.getType())) {
			trigger(event.getPlayer(), feet, checkpoint);
		}
	}

	private void applyNameTag(Player player, Checkpoint checkpoint) {
		ItemStack tag = player.getInventory().getItemInMainHand();
		ItemMeta meta = tag.getItemMeta();
		String reason = meta != null && meta.hasDisplayName()
				? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
				: null;

		manager.setOption(checkpoint, Checkpoint.DEATH_MESSAGE, reason, player);
		player.sendMessage(reason == null
				? Component.text("סיבת המוות אופסה (ה-name tag בלי שם)", NamedTextColor.YELLOW)
				: Component.text("סיבת המוות נקבעה: ", NamedTextColor.GREEN)
						.append(TextFormat.parse(reason, player).colorIfAbsent(NamedTextColor.RED)));
	}

	// =========================== activation ===========================

	private void trigger(Player player, Block block, Checkpoint checkpoint) {
		if (checkpoint.type() == Checkpoint.Type.PLAIN && checkpoint.course() == null) {
			return;
		}

		// A respawn point re-triggering while you stand on it would spam.
		boolean repeat = checkpoint.type().isRespawnPoint()
				&& checkpoint.key().equals(manager.getPlayerSpawn(player.getUniqueId()));
		if (repeat) {
			return;
		}
		if (!allowed(player, checkpoint)) {
			return;
		}

		switch (checkpoint.type()) {
			case KILL -> {
				// Dying already carries its own feedback (the death message and
				// screen) - skip the activation announcement, but the sound/
				// particle burst and cooldown/once bookkeeping still apply,
				// and only when the player was actually killed.
				if (kill(player, checkpoint)) {
					effects(player, block, checkpoint);
					spend(player, checkpoint);
				}
				return;
			}
			case RESET -> {
				player.setRespawnLocation(null);
				manager.clearPlayerSpawn(player.getUniqueId());
				announce(player, checkpoint, "הריספאון חזר לברירת המחדל", NamedTextColor.GOLD);
			}
			case TELEPORT -> {
				Location target = parseLocation(checkpoint.target());
				if (target == null) {
					player.sendActionBar(Component.text(
							"ללוחית הטלפורט אין יעד - /checkpoint target", NamedTextColor.RED));
					return;
				}
				player.teleport(target);
				announce(player, checkpoint, "שוגרת!", NamedTextColor.LIGHT_PURPLE);
			}
			case COMMAND -> {
				if (!runCommand(player, checkpoint)) {
					return;
				}
				announce(player, checkpoint, "", NamedTextColor.AQUA);
			}
			case EFFECT -> {
				if (!applyEffect(player, checkpoint)) {
					return;
				}
				announce(player, checkpoint, "", NamedTextColor.LIGHT_PURPLE);
			}
			case LAUNCH -> {
				launch(player, checkpoint);
				announce(player, checkpoint, "", NamedTextColor.WHITE);
			}
			case CHECKPOINT -> {
				manager.setPlayerSpawn(player.getUniqueId(), checkpoint.key());
				player.setRespawnLocation(manager.findRespawnSpot(
						block.getWorld(), checkpoint.x(), checkpoint.y(), checkpoint.z()), true);

				if (checkpoint.heal()) {
					AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
					player.setHealth(max != null ? max.getValue() : 20.0);
					player.setFoodLevel(20);
					player.setSaturation(20.0f);
					player.setFireTicks(0);
				}
				announce(player, checkpoint, checkpoint.name() != null
						? "נקודת ריספאון נשמרה: " + checkpoint.name()
						: "נקודת ריספאון נשמרה!", NamedTextColor.AQUA);
			}
			case PLAIN -> {
				// nothing of its own; it may still be part of a course
			}
		}

		course(player, checkpoint);
		effects(player, block, checkpoint);
		spend(player, checkpoint);
	}

	// ---- gates ----

	private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

	/** Permission, cooldown and one-time checks, with a word to the player. */
	private boolean allowed(Player player, Checkpoint checkpoint) {
		String permission = checkpoint.permission();
		if (permission != null && !player.hasPermission(permission)) {
			player.sendActionBar(Component.text("אין לך גישה לנקודה הזו", NamedTextColor.RED));
			return false;
		}

		if (checkpoint.once() && manager.hasUsed(checkpoint.key(), player.getUniqueId())) {
			player.sendActionBar(Component.text("כבר השתמשת בזה", NamedTextColor.GRAY));
			return false;
		}

		int cooldown = checkpoint.cooldown();
		if (cooldown > 0) {
			String key = checkpoint.key() + "|" + player.getUniqueId();
			long now = System.currentTimeMillis();
			Long until = cooldowns.get(key);
			if (until != null && until > now) {
				player.sendActionBar(Component.text(
						"עוד " + ((until - now) / 1000 + 1) + " שניות", NamedTextColor.GRAY));
				return false;
			}
		}
		return true;
	}

	/** Records the use, once the point has actually fired. */
	private void spend(Player player, Checkpoint checkpoint) {
		if (checkpoint.once()) {
			manager.markUsed(checkpoint.key(), player.getUniqueId());
		}
		if (checkpoint.cooldown() > 0) {
			cooldowns.put(checkpoint.key() + "|" + player.getUniqueId(),
					System.currentTimeMillis() + checkpoint.cooldown() * 1000L);
		}
	}

	// ---- the special types ----

	/** @return true if a command actually ran */
	private boolean runCommand(Player player, Checkpoint checkpoint) {
		String command = checkpoint.command();
		if (command == null || command.isBlank()) {
			player.sendActionBar(Component.text(
					"ללוחית הפקודה אין פקודה - /checkpoint command", NamedTextColor.RED));
			return false;
		}
		String resolved = command.replace("%player%", player.getName());
		if (resolved.startsWith("/")) {
			resolved = resolved.substring(1);
		}
		if (checkpoint.commandAsConsole()) {
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
		} else {
			player.performCommand(resolved);
		}
		return true;
	}

	private boolean applyEffect(Player player, Checkpoint checkpoint) {
		String spec = checkpoint.effect();
		if (spec == null) {
			player.sendActionBar(Component.text(
					"ללוחית האפקט אין אפקט - /checkpoint effect", NamedTextColor.RED));
			return false;
		}
		String[] parts = spec.split(":");
		PotionEffectType kind = resolveEffect(parts[0]);
		if (kind == null) {
			return false;
		}
		int amplifier = parts.length > 1 ? parseInt(parts[1], 1) - 1 : 0;
		int seconds = parts.length > 2 ? parseInt(parts[2], 10) : 10;
		player.addPotionEffect(new PotionEffect(kind, Math.max(1, seconds) * 20, Math.max(0, amplifier), true, true));
		return true;
	}

	private void launch(Player player, Checkpoint checkpoint) {
		String[] parts = checkpoint.launch().split(",");
		double forward = parts.length > 0 ? parseDouble(parts[0], 0) : 0;
		double up = parts.length > 1 ? parseDouble(parts[1], 1.0) : 1.0;

		Vector velocity = player.getLocation().getDirection().setY(0).normalize().multiply(forward);
		if (Double.isNaN(velocity.getX()) || Double.isNaN(velocity.getZ())) {
			velocity = new Vector(0, 0, 0); // looking straight up or down
		}
		velocity.setY(up);
		player.setVelocity(velocity);
	}

	private void course(Player player, Checkpoint checkpoint) {
		if (checkpoint.course() == null) {
			return;
		}
		switch (checkpoint.role()) {
			case START -> courses.start(player, checkpoint);
			case CHECK -> courses.split(player, checkpoint);
			case FINISH -> courses.finish(player, checkpoint);
		}
	}

	/** Sends the activation message the way this point is configured. */
	private void announce(Player player, Checkpoint checkpoint, String fallback, NamedTextColor color) {
		Checkpoint.MessageMode mode = checkpoint.messageMode();
		String custom = checkpoint.message();
		if (mode == Checkpoint.MessageMode.NONE || isNone(custom)) {
			return;
		}
		if (custom == null && fallback.isEmpty()) {
			return; // these types say nothing unless you give them words
		}

		Component message = custom != null
				? TextFormat.parse(custom, null).colorIfAbsent(color)
				: TextFormat.parse(fallback, null).colorIfAbsent(color);

		switch (mode) {
			case CHAT -> player.sendMessage(message);
			case ACTIONBAR -> player.sendActionBar(message);
			case TITLE -> player.showTitle(Title.title(message, Component.empty(),
					Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400))));
			case BOTH -> {
				player.sendMessage(message);
				player.sendActionBar(message);
			}
			default -> {
			}
		}
	}

	/** Sound, plus a particle burst for points set to TRIGGER mode. */
	private void effects(Player player, Block block, Checkpoint checkpoint) {
		Sound sound = resolveSound(checkpoint.sound());
		if (sound != null) {
			player.playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, 0.7f, 1.2f);
		}
		if (checkpoint.particleMode() == Checkpoint.ParticleMode.TRIGGER) {
			ParticleSpec spec = ParticleSpec.parse(checkpoint.particle());
			if (spec != null) {
				for (int i = 0; i < 4; i++) {
					spec.spawn(block.getWorld(), checkpoint.x() + 0.5,
							checkpoint.y() + 0.6 + i * 0.15, checkpoint.z() + 0.5);
				}
			}
		}
	}

	/** @return true if the player was actually killed */
	private boolean kill(Player player, Checkpoint checkpoint) {
		if (player.isDead() || player.getHealth() <= 0.0) {
			return false;
		}
		// Builders standing on their own kill plates would be annoying, and
		// should not burn a one-time use or the sound/particle burst either.
		if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
			return false;
		}
		if (checkpoint.deathMessage() != null) {
			pendingDeathMessages.put(player.getUniqueId(), checkpoint.deathMessage());
		}
		player.setHealth(0.0);
		return true;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(PlayerDeathEvent event) {
		String reason = pendingDeathMessages.remove(event.getPlayer().getUniqueId());
		if (reason == null) {
			return;
		}
		String text = reason.contains("%player%")
				? reason.replace("%player%", event.getPlayer().getName())
				: event.getPlayer().getName() + " " + reason;
		event.deathMessage(TextFormat.parse(text, event.getPlayer()));
	}

	/** The plugin decides where a player respawns, not the vanilla bed check. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onRespawn(PlayerRespawnEvent event) {
		Checkpoint checkpoint = manager.byKey(manager.getPlayerSpawn(event.getPlayer().getUniqueId()));
		if (checkpoint == null || checkpoint.type() != Checkpoint.Type.CHECKPOINT) {
			return;
		}
		World world = Bukkit.getWorld(checkpoint.world());
		if (world != null) {
			event.setRespawnLocation(
					manager.findRespawnSpot(world, checkpoint.x(), checkpoint.y(), checkpoint.z()));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		courses.quit(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (manager.consumePendingReset(player.getUniqueId())) {
			player.setRespawnLocation(null);
			player.sendMessage(Component.text(
					"הצ'קפוינט שלך נהרס - הריספאון חזר לברירת המחדל", NamedTextColor.YELLOW));
		}
	}

	@EventHandler
	public void onChunkLoad(PlayerChunkLoadEvent event) {
		int chunkX = event.getChunk().getX();
		int chunkZ = event.getChunk().getZ();
		for (Checkpoint checkpoint : manager.all()) {
			if (checkpoint.invisible()
					&& checkpoint.world().equals(event.getWorld().getName())
					&& (checkpoint.x() >> 4) == chunkX
					&& (checkpoint.z() >> 4) == chunkZ) {
				manager.sendPlateState(event.getPlayer(), checkpoint);
			}
		}
	}

	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTask(this, () -> manager.updateAllFor(player));
	}

	// =========================== particles ===========================

	private int animationFrame;

	/** Redraws every animated floating name that somebody could be looking at. */
	private void animateNames() {
		animationFrame++;
		for (Checkpoint checkpoint : manager.all()) {
			if (checkpoint.animation() == Checkpoint.Animation.NONE || checkpoint.name() == null) {
				continue;
			}
			World world = Bukkit.getWorld(checkpoint.world());
			if (world == null || !world.isChunkLoaded(checkpoint.x() >> 4, checkpoint.z() >> 4)) {
				continue;
			}
			Location at = new Location(world, checkpoint.x() + 0.5, checkpoint.y() + 0.5, checkpoint.z() + 0.5);
			boolean watched = false;
			for (Player player : world.getPlayers()) {
				if (player.getLocation().distanceSquared(at) <= 48 * 48) {
					watched = true;
					break;
				}
			}
			if (!watched) {
				continue;
			}

			TextDisplay display = manager.display(checkpoint);
			if (display == null) {
				continue;
			}

			Checkpoint.Animation animation = checkpoint.animation();
			Component text = NameAnimator.render(checkpoint.name(), animation, animationFrame);
			if (text != null) {
				display.text(text);
			}

			int opacity = NameAnimator.opacity(animation, animationFrame);
			if (opacity >= 0) {
				display.setTextOpacity((byte) opacity);
			}

			float scale = NameAnimator.scale(animation, animationFrame);
			if (scale > 0) {
				Transformation current = display.getTransformation();
				display.setTransformation(new Transformation(current.getTranslation(),
						current.getLeftRotation(), new Vector3f(scale, scale, scale),
						current.getRightRotation()));
			}
		}
	}

	/** Puts opacity and scale back to normal when an effect is turned off. */
	private void resetDisplayEffects(Checkpoint checkpoint) {
		TextDisplay display = manager.display(checkpoint);
		if (display == null) {
			return;
		}
		display.setTextOpacity((byte) -1);
		Transformation current = display.getTransformation();
		display.setTransformation(new Transformation(current.getTranslation(),
				current.getLeftRotation(), new Vector3f(1f, 1f, 1f), current.getRightRotation()));
	}

	private boolean editAnimation(Player player, String raw) {
		String value = raw.trim().toUpperCase(Locale.ROOT);
		if (value.isEmpty() || value.equals("LIST") || value.equals("?")) {
			return listAnimations(player);
		}

		Checkpoint checkpoint = requireTarget(player);
		if (checkpoint == null) {
			return true;
		}
		if (checkpoint.name() == null) {
			player.sendMessage(Component.text(
					"קודם תן לנקודה שם עם /checkpoint name", NamedTextColor.RED));
			return true;
		}
		if (isNone(raw)) {
			value = "NONE";
		}

		Checkpoint.Animation animation = Checkpoint.Animation.parse(value);
		if (!animation.name().equals(value)) {
			player.sendMessage(Component.text(
					"אנימציה לא מוכרת. /checkpoint anim list מציג את כולן", NamedTextColor.RED));
			return true;
		}
		if (animation.usesOwnColours()
				&& NameAnimator.render(checkpoint.name(), animation, 0) == null) {
			player.sendMessage(Component.text(
					"ל-" + animation.name() + " צריך שם עם כמה צבעים - למשל "
							+ "<gradient:#33ccff:#001f7d>טקסט</gradient>", NamedTextColor.RED));
			return true;
		}

		resetDisplayEffects(checkpoint);
		manager.setOption(checkpoint, Checkpoint.ANIMATION,
				animation == Checkpoint.Animation.NONE ? null : animation.name(), player);

		if (animation == Checkpoint.Animation.NONE) {
			// Put the written colours back exactly as they were.
			manager.setOption(manager.byKey(checkpoint.key()), Checkpoint.NAME, checkpoint.name(), player);
			player.sendMessage(Component.text("השם חזר להיות קבוע", NamedTextColor.YELLOW));
		} else {
			player.sendMessage(Component.text("השם זז עכשיו: ", NamedTextColor.GREEN)
					.append(Component.text(animation.name(), NamedTextColor.WHITE))
					.append(Component.text(" - " + animation.description(), NamedTextColor.GRAY)));
		}
		return true;
	}

	private boolean listAnimations(Player player) {
		player.sendMessage(Component.text("── אנימציות לשם ──", NamedTextColor.AQUA));
		for (Checkpoint.Animation animation : Checkpoint.Animation.values()) {
			if (animation == Checkpoint.Animation.NONE) {
				continue;
			}
			player.sendMessage(Component.text(animation.name(),
							animation.usesOwnColours() ? NamedTextColor.AQUA : NamedTextColor.WHITE)
					.append(Component.text(" - " + animation.description(), NamedTextColor.GRAY)));
		}
		player.sendMessage(Component.text(
				"תכלת = משתמש בצבעים שכתבת. שימוש: /checkpoint anim <שם>", NamedTextColor.DARK_GRAY));
		return true;
	}

	private void showParticles() {
		for (Checkpoint checkpoint : manager.all()) {
			Checkpoint.ParticleMode mode = checkpoint.particleMode();
			if (mode == Checkpoint.ParticleMode.NONE || mode == Checkpoint.ParticleMode.TRIGGER) {
				continue;
			}
			ParticleSpec spec = ParticleSpec.parse(checkpoint.particle());
			if (spec == null) {
				continue;
			}
			World world = Bukkit.getWorld(checkpoint.world());
			if (world == null || !world.isChunkLoaded(checkpoint.x() >> 4, checkpoint.z() >> 4)) {
				continue;
			}

			double range = mode == Checkpoint.ParticleMode.ALWAYS ? 48 : 16;
			double x = checkpoint.x() + 0.5;
			double y = checkpoint.y() + 0.6;
			double z = checkpoint.z() + 0.5;
			Location at = new Location(world, x, y, z);
			for (Player player : world.getPlayers()) {
				if (player.getLocation().distanceSquared(at) <= range * range) {
					spec.spawn(world, x, y, z);
					break; // one spawn is broadcast to everyone nearby
				}
			}
		}
	}

	// =========================== helpers ===========================

	private static boolean isDefaultName(String display) {
		return display.equals(DEFAULT_INVS_NAME) || display.equals(DEFAULT_RESET_NAME)
				|| display.equals(DEFAULT_PLAIN_NAME) || display.equals(DEFAULT_KILL_NAME)
				|| display.equals("invs teleport point") || display.equals("invs command point")
				|| display.equals("invs effect point") || display.equals("invs launch point");
	}

	private static boolean isNone(String raw) {
		return raw != null && (raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("off"));
	}

	private static int parseInt(String raw, int fallback) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double parseDouble(String raw, double fallback) {
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** "world;x;y;z;yaw;pitch" back into a location. */
	private static Location parseLocation(String raw) {
		if (raw == null) {
			return null;
		}
		String[] parts = raw.split(";");
		if (parts.length < 4) {
			return null;
		}
		World world = Bukkit.getWorld(parts[0]);
		if (world == null) {
			return null;
		}
		try {
			Location location = new Location(world,
					Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
			if (parts.length >= 6) {
				location.setYaw(Float.parseFloat(parts[4]));
				location.setPitch(Float.parseFloat(parts[5]));
			}
			return location;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String formatLocation(Location location) {
		return location.getWorld().getName() + ";" + round(location.getX()) + ";"
				+ round(location.getY()) + ";" + round(location.getZ()) + ";"
				+ round(location.getYaw()) + ";" + round(location.getPitch());
	}

	private static String round(double value) {
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private static PotionEffectType resolveEffect(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String text = raw.trim().toLowerCase(Locale.ROOT);
		NamespacedKey key = text.indexOf(':') >= 0
				? NamespacedKey.fromString(text)
				: NamespacedKey.minecraft(text);
		return key == null ? null : Registry.EFFECT.get(key);
	}

	/**
	 * Accepts both spellings of a sound: the constant style
	 * {@code ENTITY_EXPERIENCE_ORB_PICKUP} and the key style
	 * {@code entity.experience_orb.pickup}.
	 */
	private static Sound resolveSound(String raw) {
		if (raw == null || raw.isBlank() || isNone(raw)) {
			return null;
		}
		String text = raw.trim();

		if (text.indexOf('.') >= 0 || text.indexOf(':') >= 0) {
			NamespacedKey key = NamespacedKey.fromString(text.toLowerCase(Locale.ROOT));
			return key == null ? null : Registry.SOUNDS.get(key);
		}
		return soundsByConstantName().get(text.toUpperCase(Locale.ROOT));
	}

	private static Map<String, Sound> soundLookup;

	private static synchronized Map<String, Sound> soundsByConstantName() {
		if (soundLookup == null) {
			Map<String, Sound> lookup = new LinkedHashMap<>();
			for (Sound sound : Registry.SOUNDS) {
				NamespacedKey key = Registry.SOUNDS.getKey(sound);
				if (key != null) {
					lookup.put(key.getKey().toUpperCase(Locale.ROOT).replace('.', '_'), sound);
				}
			}
			soundLookup = lookup;
		}
		return soundLookup;
	}

	private static String join(String[] args, int from) {
		return from >= args.length ? "" : String.join(" ", List.of(args).subList(from, args.length));
	}

	private List<String> completeCheckpoint(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return List.of("new", "reset", "kill", "name", "anim", "message", "msgmode",
					"particle", "particlemode", "sound", "heal", "deathmsg", "info",
					"target", "command", "effect", "launch", "permission", "cooldown", "once",
					"course", "role", "list", "copy", "paste", "undo", "update");
		}

		String sub = args[0].toLowerCase(Locale.ROOT);
		if (args.length == 2 && (sub.equals("new") || sub.equals("create"))) {
			return List.of("check", "reset", "kill", "teleport", "command", "effect", "launch", "plain");
		}
		if (sub.equals("particle") && args.length == 2) {
			return particleNames();
		}
		if ((sub.equals("new") || sub.equals("create")) && args.length > 2) {
			String previous = args[args.length - 2];
			if (previous.equalsIgnoreCase("-p")) {
				return particleNames();
			}
			if (previous.equalsIgnoreCase("-d")) {
				return List.of("\"נפל לאבדון\"");
			}
			if (previous.equalsIgnoreCase("-a")) {
				List<String> names = new ArrayList<>();
				for (Checkpoint.Animation animation : Checkpoint.Animation.values()) {
					names.add(animation.name());
				}
				return names;
			}
			if (previous.equalsIgnoreCase("-r")) {
				return List.of("START", "CHECK", "FINISH");
			}
			if (previous.equalsIgnoreCase("-c")) {
				return List.of("\"שם המסלול\"");
			}
			if (previous.equalsIgnoreCase("-cd")) {
				return List.of("30");
			}
			if (args.length == 3) {
				return List.of("-i", "-p", "-s", "-a", "-d", "-c", "-r", "-perm", "-cd", "-once", "-heal");
			}
		}
		if (sub.equals("particlemode") || sub.equals("pmode")) {
			return List.of("ALWAYS", "NEAR", "TRIGGER", "NONE");
		}
		if (sub.equals("msgmode") || sub.equals("messagemode")) {
			return List.of("CHAT", "ACTIONBAR", "BOTH", "TITLE", "NONE");
		}
		if (sub.equals("heal")) {
			return List.of("true", "false");
		}
		if (sub.equals("anim") || sub.equals("animation")) {
			List<String> names = new ArrayList<>();
			names.add("list");
			for (Checkpoint.Animation animation : Checkpoint.Animation.values()) {
				names.add(animation.name());
			}
			return names;
		}
		if (sub.equals("sound") && args.length == 2) {
			return List.of("none", "default", "ENTITY_EXPERIENCE_ORB_PICKUP",
					"ENTITY_PLAYER_LEVELUP", "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_BLOCK_BELL",
					"ENTITY_ARROW_HIT_PLAYER", "BLOCK_BEACON_ACTIVATE", "ENTITY_ENDER_DRAGON_GROWL");
		}
		return List.of();
	}

	private static List<String> particleNames() {
		List<String> options = new ArrayList<>(List.of("none", "default", "DUST:#FF5555"));
		for (Particle particle : Particle.values()) {
			if (particle.getDataType() == Void.class) {
				options.add(particle.name());
			}
		}
		return options;
	}
}
