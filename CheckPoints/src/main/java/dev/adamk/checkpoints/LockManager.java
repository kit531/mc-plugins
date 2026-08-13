package dev.adamk.checkpoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * /lock and /unlock for doors, trapdoors, fence gates and armour stands.
 *
 * <p>Locked means locked for people: only a player in creative mode can open,
 * move or break it, and wind charges, explosions, pistons and mobs are all
 * refused. Redstone is the deliberate exception - a circuit still works a
 * locked door. Holding the matching item and running /lock tags the item, so
 * whatever you place from it starts out locked.
 */
public final class LockManager implements CommandExecutor, Listener {
	public static final String PERMISSION = "checkpoints.lock";

	private final CheckPointsPlugin plugin;
	private final NamespacedKey itemKey;

	// key -> the open/closed state the block must keep.
	private final Map<String, Boolean> blocks = new ConcurrentHashMap<>();
	private final Set<UUID> entities = ConcurrentHashMap.newKeySet();

	public LockManager(CheckPointsPlugin plugin) {
		this.plugin = plugin;
		this.itemKey = new NamespacedKey(plugin, "locked");
	}

	public void start() {
		load();
		// Catch-all: anything that changed a locked block despite the event
		// handlers below gets put straight back.
		Bukkit.getScheduler().runTaskTimer(plugin, this::enforceStates, 2L, 2L);
	}

	private static String key(Block block) {
		return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
	}

	public static boolean lockableBlock(Material material) {
		return Tag.DOORS.isTagged(material)
				|| Tag.TRAPDOORS.isTagged(material)
				|| Tag.FENCE_GATES.isTagged(material);
	}

	private static boolean lockableItem(Material material) {
		return lockableBlock(material) || material == Material.ARMOR_STAND;
	}

	/** Doors are two blocks; both halves share one lock. */
	private static Block otherHalf(Block block) {
		BlockData data = block.getBlockData();
		if (Tag.DOORS.isTagged(block.getType()) && data instanceof Bisected bisected) {
			return bisected.getHalf() == Bisected.Half.TOP
					? block.getRelative(0, -1, 0)
					: block.getRelative(0, 1, 0);
		}
		return null;
	}

	public boolean isLocked(Block block) {
		if (blocks.containsKey(key(block))) {
			return true;
		}
		Block other = otherHalf(block);
		return other != null && blocks.containsKey(key(other));
	}

	public boolean isLocked(Entity entity) {
		return entities.contains(entity.getUniqueId());
	}

	/** The only way past a lock. */
	private static boolean mayBypass(Player player) {
		return player != null && player.getGameMode() == GameMode.CREATIVE;
	}

	// =========================== commands ===========================

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("Players only."));
			return true;
		}
		boolean locking = command.getName().equalsIgnoreCase("lock");

		// 1. An item in hand becomes a "place it already locked" item.
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!held.getType().isAir() && lockableItem(held.getType())) {
			held.editMeta(meta -> {
				if (locking) {
					meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
				} else {
					meta.getPersistentDataContainer().remove(itemKey);
				}
			});
			player.getInventory().setItemInMainHand(held);
			player.sendMessage(locking
					? Component.text("הפריט שביד ננעל - כל מה שתניח ממנו יהיה נעול", NamedTextColor.GREEN)
					: Component.text("הפריט שביד כבר לא ננעל בהנחה", NamedTextColor.YELLOW));
			return true;
		}

		// 2. An armour stand you are looking at.
		Entity entity = player.getTargetEntity(5);
		if (entity instanceof ArmorStand) {
			setEntityLocked(entity, locking);
			player.sendMessage(locking
					? Component.text("הארמור סטנד ננעל", NamedTextColor.GREEN)
					: Component.text("הארמור סטנד נפתח", NamedTextColor.YELLOW));
			return true;
		}

		// 3. The block you are looking at.
		Block block = player.getTargetBlockExact(6);
		if (block == null || !lockableBlock(block.getType())) {
			player.sendMessage(Component.text(
					"תסתכל על דלת, טרפדור, שער או ארמור סטנד - או תחזיק אחד מהם ביד",
					NamedTextColor.RED));
			return true;
		}

		setBlockLocked(block, locking);
		player.sendMessage(locking
				? Component.text("ננעל", NamedTextColor.GREEN)
				: Component.text("נפתח", NamedTextColor.YELLOW));
		return true;
	}

	private void setBlockLocked(Block block, boolean locked) {
		Block other = otherHalf(block);
		if (locked) {
			blocks.put(key(block), isOpen(block));
			if (other != null) {
				blocks.put(key(other), isOpen(other));
			}
		} else {
			blocks.remove(key(block));
			if (other != null) {
				blocks.remove(key(other));
			}
		}
		save();
	}

	private void setEntityLocked(Entity entity, boolean locked) {
		if (locked) {
			entities.add(entity.getUniqueId());
			entity.setInvulnerable(true);
		} else {
			entities.remove(entity.getUniqueId());
			entity.setInvulnerable(false);
		}
		save();
	}

	private static boolean isOpen(Block block) {
		return block.getBlockData() instanceof Openable openable && openable.isOpen();
	}

	/** Records however the block stands right now as the state to keep. */
	private void rememberState(Block block) {
		if (!isLocked(block) || !lockableBlock(block.getType())) {
			return;
		}
		blocks.put(key(block), isOpen(block));
		Block other = otherHalf(block);
		if (other != null) {
			blocks.put(key(other), isOpen(other));
		}
		save();
	}

	/** Power on either half of a door counts, since both halves swing together. */
	private static boolean powered(Block block) {
		if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
			return true;
		}
		Block other = otherHalf(block);
		return other != null && (other.isBlockPowered() || other.isBlockIndirectlyPowered());
	}

	// =========================== placing locked items ===========================

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlaceBlock(BlockPlaceEvent event) {
		ItemStack item = event.getItemInHand();
		if (item.getItemMeta() == null
				|| !item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) {
			return;
		}
		if (!lockableBlock(event.getBlockPlaced().getType())) {
			return;
		}
		setBlockLocked(event.getBlockPlaced(), true);
		event.getPlayer().sendActionBar(Component.text("הונח נעול", NamedTextColor.GREEN));
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlaceEntity(EntityPlaceEvent event) {
		if (!(event.getEntity() instanceof ArmorStand stand) || event.getPlayer() == null) {
			return;
		}
		ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
		if (item.getItemMeta() == null
				|| !item.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) {
			return;
		}
		setEntityLocked(stand, true);
		event.getPlayer().sendActionBar(Component.text("הונח נעול", NamedTextColor.GREEN));
	}

	// =========================== enforcement ===========================

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
			return;
		}
		Block block = event.getClickedBlock();
		if (!isLocked(block)) {
			return;
		}
		if (mayBypass(event.getPlayer())) {
			// A builder may work the door - and whatever they leave it as
			// becomes the state the lock keeps from now on.
			Bukkit.getScheduler().runTask(plugin, () -> rememberState(block));
			return;
		}
		event.setCancelled(true);
		event.getPlayer().sendActionBar(Component.text("נעול", NamedTextColor.RED));
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (!isLocked(block)) {
			return;
		}
		if (mayBypass(event.getPlayer())) {
			setBlockLocked(block, false); // it is gone; drop the lock with it
			return;
		}
		event.setCancelled(true);
		event.getPlayer().sendActionBar(Component.text("נעול", NamedTextColor.RED));
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onArmorStand(PlayerArmorStandManipulateEvent event) {
		if (!isLocked(event.getRightClicked()) || mayBypass(event.getPlayer())) {
			return;
		}
		event.setCancelled(true);
		event.getPlayer().sendActionBar(Component.text("נעול", NamedTextColor.RED));
	}

	/** Fire, lava, arrows, wind bursts, mobs - a locked stand takes none of it. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!isLocked(event.getEntity())) {
			return;
		}
		// A builder in creative is still allowed to remove it.
		if (event instanceof EntityDamageByEntityEvent byEntity
				&& byEntity.getDamager() instanceof Player player && mayBypass(player)) {
			entities.remove(event.getEntity().getUniqueId());
			event.getEntity().setInvulnerable(false);
			save();
			return;
		}
		event.setCancelled(true);
	}

	/** Mobs and other entities opening or changing the block. */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (isLocked(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityInteract(EntityInteractEvent event) {
		if (isLocked(event.getBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().removeIf(this::isLocked);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().removeIf(this::isLocked);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		if (event.getBlocks().stream().anyMatch(this::isLocked)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		if (event.getBlocks().stream().anyMatch(this::isLocked)) {
			event.setCancelled(true);
		}
	}

	/**
	 * Whatever slipped through - a wind charge burst, another plugin, a quirk of
	 * a future version - is undone here, so a locked door cannot stay open.
	 *
	 * <p>Redstone is the one thing allowed to work a locked door, so a block
	 * that is currently powered is left exactly as the circuit wants it.
	 */
	private void enforceStates() {
		for (Map.Entry<String, Boolean> entry : blocks.entrySet()) {
			String[] parts = entry.getKey().split(";");
			if (parts.length != 4) {
				continue;
			}
			World world = Bukkit.getWorld(parts[0]);
			if (world == null) {
				continue;
			}
			int x = Integer.parseInt(parts[1]);
			int y = Integer.parseInt(parts[2]);
			int z = Integer.parseInt(parts[3]);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}

			Block block = world.getBlockAt(x, y, z);
			if (!lockableBlock(block.getType())) {
				continue; // it is gone; the lock is harmless
			}
			if (powered(block)) {
				continue; // a circuit is driving it - let it
			}
			BlockData data = block.getBlockData();
			if (data instanceof Openable openable && openable.isOpen() != entry.getValue()) {
				openable.setOpen(entry.getValue());
				block.setBlockData(data, false);
			}
		}
	}

	// =========================== storage ===========================

	private File file() {
		return new File(plugin.getDataFolder(), "locks.yml");
	}

	public void load() {
		load(file());
	}

	void load(File file) {
		blocks.clear();
		entities.clear();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

		List<String> entries = yaml.getStringList("blocks");
		if (!entries.isEmpty()) {
			for (String entry : entries) {
				decodeInto(entry, blocks);
			}
		} else {
			// An interim version stored a section keyed by position.
			ConfigurationSection section = yaml.getConfigurationSection("blocks");
			if (section != null) {
				for (String key : section.getKeys(false)) {
					blocks.put(key.replace('|', ';'), section.getBoolean(key));
				}
			}
		}

		for (String id : yaml.getStringList("entities")) {
			try {
				entities.add(UUID.fromString(id));
			} catch (IllegalArgumentException ignored) {
				// A malformed id simply drops out of the list.
			}
		}
	}

	/**
	 * One lock as a single line: {@code world;x;y;z;open}. Keeping it in a list
	 * rather than a keyed section means a world name containing a dot cannot be
	 * mistaken for a config path.
	 */
	static String encode(String key, boolean open) {
		return key + ";" + open;
	}

	static void decodeInto(String entry, Map<String, Boolean> target) {
		if (entry == null || entry.isBlank()) {
			return;
		}
		int cut = entry.lastIndexOf(';');
		if (cut > 0) {
			String tail = entry.substring(cut + 1);
			if (tail.equals("true") || tail.equals("false")) {
				target.put(entry.substring(0, cut), Boolean.parseBoolean(tail));
				return;
			}
		}
		// The first version stored positions only, with no open/closed state.
		target.put(entry, false);
	}

	public void save() {
		save(file());
	}

	void save(File file) {
		YamlConfiguration yaml = new YamlConfiguration();
		List<String> entries = new ArrayList<>();
		blocks.forEach((key, open) -> entries.add(encode(key, open)));
		yaml.set("blocks", entries);

		List<String> ids = new ArrayList<>();
		for (UUID id : entities) {
			ids.add(id.toString());
		}
		yaml.set("entities", ids);

		try {
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save locks: " + e.getMessage());
		}
	}
}
