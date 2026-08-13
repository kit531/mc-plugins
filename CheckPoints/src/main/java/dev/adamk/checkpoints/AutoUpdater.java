package dev.adamk.checkpoints;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Checks GitHub once per server start for a newer CheckPoints release that
 * declares support for this server's running Minecraft version, and stages
 * it in {@code plugins/update/CheckPoints.jar}.
 *
 * <p>Paper/Bukkit's own startup sequence (not this plugin) is what actually
 * swaps that staged file over the currently loaded one and deletes the old
 * jar - and only on the <em>next</em> boot, since a JVM cannot safely replace
 * the very jar file its own classes were just loaded from. Everything here
 * is read-only, unauthenticated and off the main thread: a failed check
 * never affects plugin startup or delays it.
 */
public final class AutoUpdater implements Listener {
	private static final String REPO = "kit531/mc-plugins";
	private static final String RELEASE_PREFIX = "checkpoints-v";
	private static final String JAR_ASSET_NAME = "CheckPoints.jar";
	private static final String COMPAT_ASSET_NAME = "compat.json";
	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final CheckPointsPlugin plugin;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

	// Set once an update has actually been staged this run, so newly joining
	// OPs get told about it even though console output already scrolled by.
	private final AtomicBoolean staged = new AtomicBoolean(false);
	private volatile String stagedVersion;

	public AutoUpdater(CheckPointsPlugin plugin) {
		this.plugin = plugin;
	}

	/** Runs the check on a background thread; safe to call from onEnable(). */
	public void checkOnStartup() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, (Runnable) this::runCheck);
	}

	/** Manual re-check, e.g. from /checkpoint update. Reports back to sender. */
	public void checkNow(java.util.function.Consumer<Component> report) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, (Runnable) (() -> runCheck(report)));
	}

	private void runCheck() {
		runCheck(message -> plugin.getLogger().info(plain(message)));
	}

	private void runCheck(java.util.function.Consumer<Component> report) {
		try {
			JsonObject release = findLatestCheckpointsRelease();
			if (release == null) {
				report.accept(Component.text(
						"No CheckPoints release found on GitHub yet.", NamedTextColor.GRAY));
				return;
			}

			String tag = release.get("tag_name").getAsString();
			String releaseVersion = tag.substring(RELEASE_PREFIX.length());
			String currentVersion = plugin.getPluginMeta().getVersion();

			if (compareVersions(releaseVersion, currentVersion) <= 0) {
				report.accept(Component.text(
						"Already on the latest version (" + currentVersion + ").", NamedTextColor.GRAY));
				return;
			}

			JsonObject compat = downloadJson(assetUrl(release, COMPAT_ASSET_NAME));
			if (compat == null) {
				report.accept(Component.text(
						"Release " + releaseVersion + " is missing its compat.json - skipping.",
						NamedTextColor.YELLOW));
				return;
			}

			String serverVersion = Bukkit.getMinecraftVersion();
			String min = compat.get("minMinecraft").getAsString();
			String max = compat.get("maxMinecraft").getAsString();
			boolean compatible = compareVersions(serverVersion, min) >= 0
					&& compareVersions(serverVersion, max) <= 0;

			if (!compatible) {
				report.accept(Component.text(
						"CheckPoints " + releaseVersion + " is available but only supports "
								+ min + "-" + max + " (this server runs " + serverVersion + ").",
						NamedTextColor.YELLOW));
				return;
			}

			String jarUrl = assetUrl(release, JAR_ASSET_NAME);
			if (jarUrl == null) {
				report.accept(Component.text(
						"Release " + releaseVersion + " has no jar attached - skipping.", NamedTextColor.YELLOW));
				return;
			}

			stageUpdate(jarUrl);
			staged.set(true);
			stagedVersion = releaseVersion;
			report.accept(Component.text(
					"CheckPoints " + releaseVersion + " staged - it will install automatically "
							+ "on the next server restart.", NamedTextColor.GREEN));
		} catch (Exception e) {
			plugin.getLogger().log(Level.WARNING, "Auto-update check failed: " + e.getMessage(), e);
			report.accept(Component.text("Update check failed: " + e.getMessage(), NamedTextColor.RED));
		}
	}

	/** The newest non-draft, non-prerelease release tagged "checkpoints-vX.Y.Z". */
	private JsonObject findLatestCheckpointsRelease() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.github.com/repos/" + REPO + "/releases?per_page=30"))
				.header("Accept", "application/vnd.github+json")
				.timeout(TIMEOUT)
				.GET()
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("GitHub API returned HTTP " + response.statusCode());
		}

		JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
		JsonObject best = null;
		String bestVersion = null;
		for (JsonElement element : releases) {
			JsonObject release = element.getAsJsonObject();
			if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) {
				continue;
			}
			String tag = release.get("tag_name").getAsString();
			if (!tag.startsWith(RELEASE_PREFIX)) {
				continue;
			}
			String version = tag.substring(RELEASE_PREFIX.length());
			if (best == null || compareVersions(version, bestVersion) > 0) {
				best = release;
				bestVersion = version;
			}
		}
		return best;
	}

	private static String assetUrl(JsonObject release, String name) {
		for (JsonElement element : release.getAsJsonArray("assets")) {
			JsonObject asset = element.getAsJsonObject();
			if (asset.get("name").getAsString().equals(name)) {
				return asset.get("browser_download_url").getAsString();
			}
		}
		return null;
	}

	private JsonObject downloadJson(String url) throws IOException, InterruptedException {
		if (url == null) {
			return null;
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			return null;
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private void stageUpdate(String jarUrl) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl)).timeout(TIMEOUT).GET().build();
		HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IOException("Downloading the jar returned HTTP " + response.statusCode());
		}

		Path updateDir = plugin.getServer().getUpdateFolderFile().toPath();
		Files.createDirectories(updateDir);
		Path target = updateDir.resolve(JAR_ASSET_NAME);
		Path temp = updateDir.resolve(JAR_ASSET_NAME + ".part");
		Files.write(temp, response.body());
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
	}

	/** Dotted version compare (1.21.11 vs 26.2 vs 1.0.1); missing segments count as 0. */
	static int compareVersions(String a, String b) {
		String[] pa = leadingNumeric(a).split("\\.");
		String[] pb = leadingNumeric(b).split("\\.");
		int length = Math.max(pa.length, pb.length);
		for (int i = 0; i < length; i++) {
			int va = i < pa.length ? parseIntSafe(pa[i]) : 0;
			int vb = i < pb.length ? parseIntSafe(pb[i]) : 0;
			if (va != vb) {
				return Integer.compare(va, vb);
			}
		}
		return 0;
	}

	private static String leadingNumeric(String raw) {
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[0-9.]+").matcher(raw.trim());
		return m.find() ? m.group() : "0";
	}

	private static int parseIntSafe(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String plain(Component component) {
		return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
				.serialize(component);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (!staged.get()) {
			return;
		}
		Player player = event.getPlayer();
		if (player.isOp()) {
			player.sendMessage(Component.text("CheckPoints " + stagedVersion
					+ " is staged - restart the server to install it.", NamedTextColor.GREEN));
		}
	}
}
