package dev.adamk.checkpoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.permissions.Permissible;

/**
 * Stores the points (data.yml), spawns and removes the floating name displays
 * and hides invisible blocks from non-creative players.
 */
public final class CheckpointManager {
	private final CheckPointsPlugin plugin;
	private final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();

	// Which checkpoint set each player's current respawn.
	private final Map<UUID, String> playerSpawns = new ConcurrentHashMap<>();
	// Players whose checkpoint was destroyed while they were offline -
	// their respawn is cleared the next time they join.
	private final Set<UUID> pendingReset = ConcurrentHashMap.newKeySet();
	// point key -> players who have already used a one-time point
	private final Map<String, Set<UUID>> usedOnce = new ConcurrentHashMap<>();

	public CheckpointManager(CheckPointsPlugin plugin) {
		this.plugin = plugin;
	}

	public static boolean isCheckpointBlock(Material type) {
		return Tag.PRESSURE_PLATES.isTagged(type) || Tag.BUTTONS.isTagged(type);
	}

	public Checkpoint get(Block block) {
		return checkpoints.get(Checkpoint.key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
	}

	public Checkpoint byKey(String key) {
		return key == null ? null : checkpoints.get(key);
	}

	public Iterable<Checkpoint> all() {
		return checkpoints.values();
	}

	public void register(Block block, boolean invisible, Checkpoint.Type type,
			Map<String, String> options, Permissible author) {
		Map<String, String> clean = new LinkedHashMap<>();
		options.forEach((key, value) -> {
			if (value != null && !value.isBlank()) {
				clean.put(key, value);
			}
		});

		Checkpoint checkpoint = new Checkpoint(block.getWorld().getName(),
				block.getX(), block.getY(), block.getZ(), invisible, type, null, clean);
		checkpoint = checkpoint.withDisplay(spawnDisplay(block, checkpoint, author));

		checkpoints.put(checkpoint.key(), checkpoint);
		save();

		if (invisible) {
			// Hide after the server has sent the real block to nearby players.
			Checkpoint registered = checkpoint;
			Bukkit.getScheduler().runTaskLater(plugin, () -> hideFromAll(registered), 2L);
		}
	}

	/** Sets one option, redrawing the floating name when that is what changed. */
	public void setOption(Checkpoint checkpoint, String option, String value, Permissible author) {
		Checkpoint updated = checkpoint.with(option, value);

		if (Checkpoint.NAME.equals(option)) {
			removeDisplay(checkpoint);
			World world = Bukkit.getWorld(updated.world());
			if (world != null) {
				Block block = world.getBlockAt(updated.x(), updated.y(), updated.z());
				updated = updated.withDisplay(spawnDisplay(block, updated, author));
			}
		}

		checkpoints.put(updated.key(), updated);
		save();
	}

	/** Spawns the floating name entity, or returns null when there is no name. */
	private UUID spawnDisplay(Block block, Checkpoint checkpoint, Permissible author) {
		String name = checkpoint.name();
		if (name == null || name.isBlank()) {
			return null;
		}
		NamedTextColor fallback = switch (checkpoint.type()) {
			case RESET -> NamedTextColor.GOLD;
			case KILL -> NamedTextColor.RED;
			case TELEPORT -> NamedTextColor.LIGHT_PURPLE;
			case COMMAND -> NamedTextColor.YELLOW;
			case EFFECT -> NamedTextColor.LIGHT_PURPLE;
			case LAUNCH -> NamedTextColor.WHITE;
			case PLAIN -> NamedTextColor.WHITE;
			case CHECKPOINT -> NamedTextColor.AQUA;
		};
		// The author's own colours win; the type colour only fills the gap.
		Component text = TextFormat.parse(name, author).colorIfAbsent(fallback);
		TextDisplay display = block.getWorld().spawn(displayLocation(block), TextDisplay.class, td -> {
			td.text(text);
			td.setBillboard(Display.Billboard.CENTER);
			td.setPersistent(true);
		});
		return display.getUniqueId();
	}

	/** The floating name entity of a point, if it exists and is loaded. */
	public TextDisplay display(Checkpoint checkpoint) {
		if (checkpoint.display() == null) {
			return null;
		}
		return Bukkit.getEntity(checkpoint.display()) instanceof TextDisplay display ? display : null;
	}

	private void removeDisplay(Checkpoint checkpoint) {
		if (checkpoint.display() == null) {
			return;
		}
		Entity entity = Bukkit.getEntity(checkpoint.display());
		if (entity != null) {
			entity.remove();
		}
	}

	/** Above the block; for a wall button - beside it, pushed out from the wall. */
	private static Location displayLocation(Block block) {
		BlockData data = block.getBlockData();
		if (Tag.BUTTONS.isTagged(block.getType())
				&& data instanceof FaceAttachable attachable
				&& attachable.getAttachedFace() == FaceAttachable.AttachedFace.WALL
				&& data instanceof Directional directional) {
			BlockFace facing = directional.getFacing();
			return block.getLocation().add(
					0.5 + facing.getModX() * 0.45, 0.5, 0.5 + facing.getModZ() * 0.45);
		}
		return block.getLocation().add(0.5, 0.9, 0.5);
	}

	public void unregister(Checkpoint checkpoint) {
		checkpoints.remove(checkpoint.key());

		// Like a broken bed: everyone whose respawn was this checkpoint loses it.
		if (checkpoint.type() == Checkpoint.Type.CHECKPOINT) {
			for (Map.Entry<UUID, String> entry : playerSpawns.entrySet()) {
				if (!entry.getValue().equals(checkpoint.key())) {
					continue;
				}
				playerSpawns.remove(entry.getKey());
				Player online = Bukkit.getPlayer(entry.getKey());
				if (online != null) {
					online.setRespawnLocation(null);
					online.sendMessage(Component.text(
							"הצ'קפוינט שלך נהרס - הריספאון חזר לברירת המחדל", NamedTextColor.YELLOW));
				} else {
					pendingReset.add(entry.getKey());
				}
			}
		} else {
			playerSpawns.values().removeIf(key -> key.equals(checkpoint.key()));
		}

		removeDisplay(checkpoint);

		// Safety net: remove any stray name display left around this block.
		World world = Bukkit.getWorld(checkpoint.world());
		if (world != null && world.isChunkLoaded(checkpoint.x() >> 4, checkpoint.z() >> 4)) {
			Location center = new Location(world,
					checkpoint.x() + 0.5, checkpoint.y() + 0.6, checkpoint.z() + 0.5);
			for (Entity entity : world.getNearbyEntities(center, 1.0, 1.2, 1.0)) {
				if (entity instanceof TextDisplay) {
					entity.remove();
				}
			}
		}
		save();
	}

	/** The nearest free block next to the point, like a bed spawn. */
	public Location findRespawnSpot(World world, int x, int y, int z) {
		int[][] offsets = {
				{1, 0}, {-1, 0}, {0, 1}, {0, -1},
				{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
		};
		for (int[] offset : offsets) {
			for (int dy = 0; dy >= -1; dy--) {
				Block candidate = world.getBlockAt(x + offset[0], y + dy, z + offset[1]);
				if (candidate.isPassable()
						&& candidate.getRelative(0, 1, 0).isPassable()
						&& candidate.getRelative(0, -1, 0).getType().isSolid()) {
					return candidate.getLocation().add(0.5, 0.0, 0.5);
				}
			}
		}
		return new Location(world, x + 0.5, y, z + 0.5);
	}

	// ---- invisible block handling ----

	public void hideFromAll(Checkpoint checkpoint) {
		World world = Bukkit.getWorld(checkpoint.world());
		if (world == null) {
			return;
		}
		for (Player player : world.getPlayers()) {
			sendPlateState(player, checkpoint);
		}
	}

	/** Sends the block as air to non-creative players, the real block to builders. */
	public void sendPlateState(Player player, Checkpoint checkpoint) {
		if (!checkpoint.invisible() || !player.getWorld().getName().equals(checkpoint.world())) {
			return;
		}
		Location loc = new Location(player.getWorld(), checkpoint.x(), checkpoint.y(), checkpoint.z());
		if (player.getGameMode() == GameMode.CREATIVE) {
			player.sendBlockChange(loc, loc.getBlock().getBlockData());
		} else {
			player.sendBlockChange(loc, Material.AIR.createBlockData());
		}
	}

	public void updateAllFor(Player player) {
		for (Checkpoint checkpoint : checkpoints.values()) {
			sendPlateState(player, checkpoint);
		}
	}

	// ---- player spawn tracking ----

	public String getPlayerSpawn(UUID player) {
		return playerSpawns.get(player);
	}

	public void setPlayerSpawn(UUID player, String checkpointKey) {
		playerSpawns.put(player, checkpointKey);
		save();
	}

	public void clearPlayerSpawn(UUID player) {
		playerSpawns.remove(player);
		save();
	}

	// ---- one-time points ----

	public boolean hasUsed(String checkpointKey, UUID player) {
		Set<UUID> used = usedOnce.get(checkpointKey);
		return used != null && used.contains(player);
	}

	public void markUsed(String checkpointKey, UUID player) {
		usedOnce.computeIfAbsent(checkpointKey, key -> ConcurrentHashMap.newKeySet()).add(player);
		save();
	}

	public void clearUsed(String checkpointKey) {
		if (usedOnce.remove(checkpointKey) != null) {
			save();
		}
	}

	/** True once, if this player's checkpoint was destroyed while they were offline. */
	public boolean consumePendingReset(UUID player) {
		boolean pending = pendingReset.remove(player);
		if (pending) {
			save();
		}
		return pending;
	}

	// ---- persistence ----

	public void load() {
		load(new File(plugin.getDataFolder(), "data.yml"));
	}

	/** Reads a data file, upgrading anything written by an older version. */
	void load(File file) {
		checkpoints.clear();
		playerSpawns.clear();
		pendingReset.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

		ConfigurationSection players = yaml.getConfigurationSection("players");
		if (players != null) {
			for (String uuid : players.getKeys(false)) {
				playerSpawns.put(UUID.fromString(uuid), players.getString(uuid));
			}
		}
		for (String uuid : yaml.getStringList("pending-reset")) {
			pendingReset.add(UUID.fromString(uuid));
		}

		usedOnce.clear();
		for (String entry : yaml.getStringList("used-once")) {
			// "world;x;y;z=uuid,uuid"
			int split = entry.lastIndexOf('=');
			if (split <= 0) {
				continue;
			}
			Set<UUID> ids = ConcurrentHashMap.newKeySet();
			for (String id : entry.substring(split + 1).split(",")) {
				try {
					ids.add(UUID.fromString(id.trim()));
				} catch (IllegalArgumentException ignored) {
					// skip anything unreadable
				}
			}
			if (!ids.isEmpty()) {
				usedOnce.put(entry.substring(0, split), ids);
			}
		}

		ConfigurationSection section = yaml.getConfigurationSection("checkpoints");
		if (section == null) {
			return;
		}
		for (String key : section.getKeys(false)) {
			String[] parts = key.split(";");
			if (parts.length != 4) {
				continue;
			}
			ConfigurationSection entry = section.getConfigurationSection(key);
			if (entry == null) {
				continue;
			}

			Map<String, String> options = new LinkedHashMap<>();
			ConfigurationSection stored = entry.getConfigurationSection("options");
			if (stored != null) {
				for (String option : stored.getKeys(false)) {
					options.put(option, stored.getString(option));
				}
			} else {
				// Files written before options existed kept a few flat keys.
				for (String legacy : new String[]{Checkpoint.NAME, Checkpoint.PARTICLE, Checkpoint.DEATH_MESSAGE}) {
					String value = entry.getString(legacy);
					if (value != null) {
						options.put(legacy, value);
					}
				}
			}

			// Older files had a "reset" boolean instead of a type.
			Checkpoint.Type type = entry.contains("type")
					? Checkpoint.Type.parse(entry.getString("type"))
					: (entry.getBoolean("reset") ? Checkpoint.Type.RESET : Checkpoint.Type.CHECKPOINT);
			String displayId = entry.getString("display");

			Checkpoint checkpoint = new Checkpoint(parts[0],
					Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
					entry.getBoolean("invisible"), type,
					displayId == null ? null : UUID.fromString(displayId), options);
			checkpoints.put(checkpoint.key(), checkpoint);
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		for (Checkpoint checkpoint : checkpoints.values()) {
			String path = "checkpoints." + checkpoint.key();
			yaml.set(path + ".invisible", checkpoint.invisible());
			yaml.set(path + ".type", checkpoint.type().name());
			if (checkpoint.display() != null) {
				yaml.set(path + ".display", checkpoint.display().toString());
			}
			checkpoint.options().forEach((key, value) -> yaml.set(path + ".options." + key, value));
		}
		for (Map.Entry<UUID, String> entry : playerSpawns.entrySet()) {
			yaml.set("players." + entry.getKey(), entry.getValue());
		}
		List<String> pending = new ArrayList<>();
		for (UUID uuid : pendingReset) {
			pending.add(uuid.toString());
		}
		yaml.set("pending-reset", pending);

		List<String> used = new ArrayList<>();
		usedOnce.forEach((key, players) -> {
			List<String> ids = new ArrayList<>();
			for (UUID uuid : players) {
				ids.add(uuid.toString());
			}
			used.add(key + "=" + String.join(",", ids));
		});
		yaml.set("used-once", used);

		try {
			File file = new File(plugin.getDataFolder(), "data.yml");
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save checkpoints: " + e.getMessage());
		}
	}
}
