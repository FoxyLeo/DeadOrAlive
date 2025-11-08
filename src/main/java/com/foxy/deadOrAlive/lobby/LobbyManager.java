package com.foxy.deadOrAlive.lobby;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Objects;

public class LobbyManager {

    private final DeadOrAlive plugin;
    private Location cachedLobbyLocation;

    public LobbyManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cachedLobbyLocation = loadFromConfig();
    }

    public Location getLobbyLocation() {
        return cachedLobbyLocation == null ? null : cachedLobbyLocation.clone();
    }

    public boolean hasLobbyLocation() {
        return cachedLobbyLocation != null;
    }

    public boolean updateLobbyLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = Objects.requireNonNull(location.getWorld(), "Location world cannot be null");
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        FileConfiguration configuration = plugin.getConfig();
        configuration.set("coords_lobby.world", world.getName());
        configuration.set("coords_lobby.x", x);
        configuration.set("coords_lobby.y", y);
        configuration.set("coords_lobby.z", z);

        plugin.saveConfig();
        reload();
        return true;
    }

    private Location loadFromConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("coords_lobby");
        if (section == null) {
            return null;
        }

        if (!section.contains("x") || !section.contains("y") || !section.contains("z")) {
            return null;
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        String worldName = section.getString("world", "");

        World world = null;
        if (worldName != null && !worldName.trim().isEmpty()) {
            world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("coords_lobby world '" + worldName + "' is not loaded. Falling back to the default world.");
            }
        }

        if (world == null) {
            List<World> worlds = plugin.getServer().getWorlds();
            if (worlds.isEmpty()) {
                return null;
            }
            world = worlds.get(0);
        }

        return centerOnBlock(world, x, y, z);
    }

    private Location centerOnBlock(World world, double x, double y, double z) {
        Location location = new Location(world, x, y, z);
        location.setX(location.getBlockX() + 0.5);
        location.setZ(location.getBlockZ() + 0.5);
        return location;
    }
}
