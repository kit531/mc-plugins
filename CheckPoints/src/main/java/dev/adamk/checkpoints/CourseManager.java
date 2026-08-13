package dev.adamk.checkpoints;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Parkour courses: a start point begins the clock, checkpoints record splits
 * and a finish point stops it. Best times per player are kept in courses.yml.
 */
public final class CourseManager {
	/** One player's attempt at one course. */
	private record Run(String course, long startedAt, int splits) {
	}

	public record Record(UUID player, String name, long millis) {
	}

	private final CheckPointsPlugin plugin;

	// live attempts, cleared on quit - nothing to persist
	private final Map<UUID, Run> running = new ConcurrentHashMap<>();
	// course -> the best time each player ever set
	private final Map<String, Map<UUID, Record>> records = new ConcurrentHashMap<>();

	public CourseManager(CheckPointsPlugin plugin) {
		this.plugin = plugin;
	}

	// =========================== running ===========================

	public void start(Player player, Checkpoint point) {
		String course = point.course();
		if (course == null) {
			return;
		}
		running.put(player.getUniqueId(), new Run(course, System.currentTimeMillis(), 0));
		player.sendMessage(Component.text("יצאת לדרך: ", NamedTextColor.AQUA)
				.append(Component.text(course, NamedTextColor.WHITE)));
	}

	/** A split along the way; silent when the player is not on this course. */
	public void split(Player player, Checkpoint point) {
		Run run = running.get(player.getUniqueId());
		if (run == null || !run.course().equals(point.course())) {
			return;
		}
		running.put(player.getUniqueId(), new Run(run.course(), run.startedAt(), run.splits() + 1));
		player.sendActionBar(Component.text("נקודה " + (run.splits() + 1) + " · "
				+ format(System.currentTimeMillis() - run.startedAt()), NamedTextColor.AQUA));
	}

	public void finish(Player player, Checkpoint point) {
		Run run = running.remove(player.getUniqueId());
		if (run == null || !run.course().equals(point.course())) {
			return;
		}

		long millis = System.currentTimeMillis() - run.startedAt();
		Map<UUID, Record> table = records.computeIfAbsent(run.course(), key -> new ConcurrentHashMap<>());
		Record previous = table.get(player.getUniqueId());

		player.sendMessage(Component.text("סיימת את ", NamedTextColor.GREEN)
				.append(Component.text(run.course(), NamedTextColor.WHITE))
				.append(Component.text(" ב-" + format(millis), NamedTextColor.AQUA)));

		if (previous == null || millis < previous.millis()) {
			table.put(player.getUniqueId(), new Record(player.getUniqueId(), player.getName(), millis));
			save();
			player.sendMessage(previous == null
					? Component.text("הזמן הראשון שלך במסלול הזה!", NamedTextColor.GOLD)
					: Component.text("שיא אישי חדש! שיפרת ב-"
							+ format(previous.millis() - millis), NamedTextColor.GOLD));

			List<Record> top = top(run.course(), 1);
			if (!top.isEmpty() && top.get(0).player().equals(player.getUniqueId())
					&& (previous == null || !top.get(0).equals(previous))) {
				Bukkit.broadcast(Component.text(player.getName() + " הוביל את ", NamedTextColor.GOLD)
						.append(Component.text(run.course(), NamedTextColor.WHITE))
						.append(Component.text(" עם " + format(millis), NamedTextColor.GOLD)));
			}
		} else {
			player.sendMessage(Component.text("השיא שלך: " + format(previous.millis()),
					NamedTextColor.GRAY));
		}
	}

	public void quit(UUID player) {
		running.remove(player);
	}

	public boolean isRunning(Player player, String course) {
		Run run = running.get(player.getUniqueId());
		return run != null && run.course().equals(course);
	}

	// =========================== records ===========================

	public List<Record> top(String course, int limit) {
		Map<UUID, Record> table = records.get(course);
		if (table == null) {
			return List.of();
		}
		List<Record> sorted = new ArrayList<>(table.values());
		sorted.sort(Comparator.comparingLong(Record::millis));
		return sorted.subList(0, Math.min(limit, sorted.size()));
	}

	public void showLeaderboard(Player player, String course) {
		List<Record> top = top(course, 10);
		player.sendMessage(Component.text("── " + course + " ──", NamedTextColor.AQUA));
		if (top.isEmpty()) {
			player.sendMessage(Component.text("אף אחד לא סיים עדיין", NamedTextColor.GRAY));
			return;
		}
		int place = 1;
		for (Record record : top) {
			NamedTextColor colour = switch (place) {
				case 1 -> NamedTextColor.GOLD;
				case 2 -> NamedTextColor.WHITE;
				case 3 -> NamedTextColor.YELLOW;
				default -> NamedTextColor.GRAY;
			};
			player.sendMessage(Component.text(place + ". ", colour)
					.append(Component.text(record.name(), NamedTextColor.WHITE))
					.append(Component.text(" - " + format(record.millis()), colour)));
			place++;
		}
	}

	public boolean clear(String course) {
		return records.remove(course) != null;
	}

	public List<String> courses() {
		return new ArrayList<>(records.keySet());
	}

	/** m:ss.mmm, or s.mmm for quick runs. */
	public static String format(long millis) {
		long minutes = millis / 60000;
		long seconds = (millis % 60000) / 1000;
		long rest = millis % 1000;
		return minutes > 0
				? String.format("%d:%02d.%03d", minutes, seconds, rest)
				: String.format("%d.%03d", seconds, rest);
	}

	// =========================== storage ===========================

	private File file() {
		return new File(plugin.getDataFolder(), "courses.yml");
	}

	public void load() {
		records.clear();
		File file = file();
		if (!file.exists()) {
			return;
		}
		YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
		for (String course : yaml.getKeys(false)) {
			ConfigurationSection section = yaml.getConfigurationSection(course);
			if (section == null) {
				continue;
			}
			Map<UUID, Record> table = new ConcurrentHashMap<>();
			for (String id : section.getKeys(false)) {
				try {
					UUID uuid = UUID.fromString(id);
					table.put(uuid, new Record(uuid,
							section.getString(id + ".name", "?"),
							section.getLong(id + ".millis")));
				} catch (IllegalArgumentException ignored) {
					// a malformed entry simply drops out
				}
			}
			records.put(course, table);
		}
	}

	public void save() {
		YamlConfiguration yaml = new YamlConfiguration();
		Map<String, Map<UUID, Record>> snapshot = new LinkedHashMap<>(records);
		snapshot.forEach((course, table) -> table.forEach((uuid, record) -> {
			yaml.set(course + "." + uuid + ".name", record.name());
			yaml.set(course + "." + uuid + ".millis", record.millis());
		}));
		try {
			File file = file();
			file.getParentFile().mkdirs();
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("Failed to save courses: " + e.getMessage());
		}
	}
}
