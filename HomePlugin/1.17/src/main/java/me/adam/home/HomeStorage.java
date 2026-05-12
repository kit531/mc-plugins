package me.adam.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HomeStorage {
    private static final Pattern VALID_HOME_NAME = Pattern.compile("^[a-z0-9_-]{1,32}$");
    private final Plugin plugin;
    private final File file;
    private final FileConfiguration data;

    public HomeStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized Set<String> listHomes(UUID uuid) {
        String path = "homes." + uuid;
        if (!data.isConfigurationSection(path)) {
            return Collections.emptySet();
        }
        return new HashSet<>(data.getConfigurationSection(path).getKeys(false));
    }

    public synchronized int countHomes(UUID uuid) {
        return listHomes(uuid).size();
    }

    public synchronized Location getHome(UUID uuid, String name) {
        String base = base(uuid, name);
        String worldName = data.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble(base + ".x");
        double y = data.getDouble(base + ".y");
        double z = data.getDouble(base + ".z");
        float yaw = (float) data.getDouble(base + ".yaw");
        float pitch = (float) data.getDouble(base + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public synchronized void setHome(UUID uuid, String name, Location location) {
        String base = base(uuid, name);
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getX());
        data.set(base + ".y", location.getY());
        data.set(base + ".z", location.getZ());
        data.set(base + ".yaw", location.getYaw());
        data.set(base + ".pitch", location.getPitch());
        saveNow();
    }

    public synchronized boolean deleteHome(UUID uuid, String name) {
        String base = base(uuid, name);
        if (!data.isConfigurationSection(base) && !data.contains(base + ".world")) {
            return false;
        }
        data.set(base, null);
        saveNow();
        return true;
    }

    private String base(UUID uuid, String name) {
        return "homes." + uuid + "." + normalizeName(name);
    }

    public static String normalizeName(String name) {
        return name.trim().toLowerCase();
    }

    public static boolean isValidHomeName(String name) {
        return VALID_HOME_NAME.matcher(normalizeName(name)).matches();
    }

    private void saveNow() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
        }
    }
}
