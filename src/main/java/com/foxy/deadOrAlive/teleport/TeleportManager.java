package com.foxy.deadOrAlive.teleport;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TeleportManager implements Listener {

    private static final String LOCATION_SUFFIX = "_location";
    private static final Random RANDOM = new Random();

    private final DeadOrAlive plugin;
    private final File teleportFile;
    private FileConfiguration configuration;
    private final Map<String, Map<String, TeleportPoint>> teleports = new HashMap<>();
    private final Map<String, TeleportPoint> locationIndex = new HashMap<>();

    public TeleportManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        this.teleportFile = new File(plugin.getDataFolder(), "teleports.yml");
        saveDefaultTeleports();
        reload();
    }

    private void saveDefaultTeleports() {
        if (!teleportFile.exists()) {
            if (!teleportFile.getParentFile().exists()) {
                teleportFile.getParentFile().mkdirs();
            }
            plugin.saveResource("teleports.yml", false);
        }
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(teleportFile);
        loadTeleports();
    }

    public boolean save() {
        if (configuration == null) {
            return false;
        }

        try {
            configuration.save(teleportFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save teleports.yml: " + exception.getMessage());
            return false;
        }
    }

    private void loadTeleports() {
        teleports.clear();
        locationIndex.clear();

        if (configuration == null) {
            return;
        }

        for (String origin : configuration.getKeys(false)) {
            ConfigurationSection roomSection = configuration.getConfigurationSection(origin);
            if (roomSection == null) {
                continue;
            }

            Map<String, TeleportPoint> roomTeleports = new HashMap<>();
            for (String key : roomSection.getKeys(false)) {
                if (key.endsWith(LOCATION_SUFFIX)) {
                    continue;
                }
                String destinationRoom = roomSection.getString(key);
                if (destinationRoom == null || destinationRoom.trim().isEmpty()) {
                    continue;
                }

                ConfigurationSection locationSection = roomSection.getConfigurationSection(key + LOCATION_SUFFIX);
                if (locationSection == null) {
                    continue;
                }

                String worldName = locationSection.getString("world");
                if (worldName == null || worldName.trim().isEmpty()) {
                    continue;
                }

                int x = locationSection.getInt("x");
                int y = locationSection.getInt("y");
                int z = locationSection.getInt("z");

                TeleportPoint point = new TeleportPoint(origin, key, destinationRoom, worldName, x, y, z);
                roomTeleports.put(key.toLowerCase(), point);
                locationIndex.put(point.getLocationKey(), point);
            }

            if (!roomTeleports.isEmpty()) {
                teleports.put(origin.toLowerCase(), roomTeleports);
            }
        }
    }

    public Map<String, Map<String, TeleportPoint>> getTeleports() {
        return Collections.unmodifiableMap(teleports);
    }

    public boolean hasConfiguredTeleports() {
        if (teleports.isEmpty()) {
            return false;
        }

        for (Map<String, TeleportPoint> roomTeleports : teleports.values()) {
            if (roomTeleports != null && !roomTeleports.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public boolean setTeleport(String origin, String teleportKey, Location location, String destinationRoom) {
        if (configuration == null || origin == null || teleportKey == null || location == null || destinationRoom == null) {
            return false;
        }

        String normalizedOrigin = origin.toLowerCase();
        String normalizedKey = teleportKey.toLowerCase();

        ConfigurationSection roomSection = configuration.getConfigurationSection(normalizedOrigin);
        if (roomSection == null) {
            roomSection = configuration.createSection(normalizedOrigin);
        }

        roomSection.set(normalizedKey, destinationRoom.toLowerCase());

        ConfigurationSection locationSection = roomSection.getConfigurationSection(normalizedKey + LOCATION_SUFFIX);
        if (locationSection == null) {
            locationSection = roomSection.createSection(normalizedKey + LOCATION_SUFFIX);
        }

        if (location.getWorld() == null) {
            return false;
        }

        locationSection.set("world", location.getWorld().getName());
        locationSection.set("x", location.getBlockX());
        locationSection.set("y", location.getBlockY());
        locationSection.set("z", location.getBlockZ());

        boolean saved = save();
        if (saved) {
            loadTeleports();
        }
        return saved;
    }

    public boolean resetTeleport(String origin, String teleportKey) {
        if (configuration == null || origin == null || teleportKey == null) {
            return false;
        }

        String normalizedOrigin = origin.toLowerCase();
        String normalizedKey = teleportKey.toLowerCase();

        ConfigurationSection roomSection = configuration.getConfigurationSection(normalizedOrigin);
        if (roomSection == null) {
            return false;
        }

        roomSection.set(normalizedKey, null);
        roomSection.set(normalizedKey + LOCATION_SUFFIX, null);

        boolean saved = save();
        if (saved) {
            loadTeleports();
        }
        return saved;
    }

    public TeleportPoint getTeleportPointAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        String key = toLocationKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return locationIndex.get(key);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        TeleportPoint point = getTeleportPointAt(location);
        if (point == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setteleports-protected"));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location to = event.getTo();
        TeleportPoint point = getTeleportPointAt(to);
        if (point == null) {
            return;
        }

        if (plugin.getTeleportSetupManager() != null &&
                plugin.getTeleportSetupManager().isInSession(event.getPlayer().getUniqueId())) {
            return;
        }

        if (plugin.getRoomSetupManager() != null &&
                plugin.getRoomSetupManager().isInSession(event.getPlayer().getUniqueId())) {
            return;
        }

        if (plugin.getEventManager() != null &&
                !plugin.getEventManager().canUseTeleport(event.getPlayer(), point.getOriginRoom())) {
            return;
        }

        TeleportResult result = resolveDestination(point, event.getPlayer().getWorld());
        if (result == null) {
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setteleports-missing-destination"));
            return;
        }

        event.getPlayer().teleport(result.getLocation());
        event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setteleports-teleported")
                .replace("%destination%", result.getDisplayName()));

        if (plugin.getEventManager() != null) {
            plugin.getEventManager().handleRoomTeleport(event.getPlayer(), result.getResolvedRoom());
        }
    }

    private TeleportResult resolveDestination(TeleportPoint point, World fallbackWorld) {
        String destinationRoom = point.getDestinationRoom();
        String resolvedRoom = destinationRoom;
        if (destinationRoom.equalsIgnoreCase("room_death")) {
            resolvedRoom = RANDOM.nextBoolean() ? "room_4" : "room_5";
        }

        Vector vector = plugin.getRoomManager().getRoom(resolvedRoom);
        if (vector == null) {
            return null;
        }

        World world = plugin.getServer().getWorld(point.getWorldName());
        if (world == null) {
            world = fallbackWorld;
        }

        Location location = new Location(world, vector.getX(), vector.getY(), vector.getZ());
        String displayName = formatDestinationName(resolvedRoom);
        return new TeleportResult(location, resolvedRoom, displayName);
    }

    private String formatDestinationName(String destinationRoom) {
        if (destinationRoom.equalsIgnoreCase("room_death")) {
            return "Room Death";
        }
        return destinationRoom.replace('_', ' ').replace("room", "Room").trim();
    }

    private String toLocationKey(String world, int x, int y, int z) {
        return world.toLowerCase() + ":" + x + ":" + y + ":" + z;
    }

    public String createLocationKey(String world, int x, int y, int z) {
        return toLocationKey(world, x, y, z);
    }

    public static class TeleportPoint {
        private final String originRoom;
        private final String teleportKey;
        private final String destinationRoom;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;

        public TeleportPoint(String originRoom, String teleportKey, String destinationRoom, String worldName, int x, int y, int z) {
            this.originRoom = originRoom.toLowerCase();
            this.teleportKey = teleportKey.toLowerCase();
            this.destinationRoom = destinationRoom.toLowerCase();
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getOriginRoom() {
            return originRoom;
        }

        public String getTeleportKey() {
            return teleportKey;
        }

        public String getDestinationRoom() {
            return destinationRoom;
        }

        public String getWorldName() {
            return worldName;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public BlockVector getBlockVector() {
            return new BlockVector(x, y, z);
        }

        public String getLocationKey() {
            return worldName.toLowerCase() + ":" + x + ":" + y + ":" + z;
        }
    }

    private static class TeleportResult {
        private final Location location;
        private final String resolvedRoom;
        private final String displayName;

        private TeleportResult(Location location, String resolvedRoom, String displayName) {
            this.location = location;
            this.resolvedRoom = resolvedRoom;
            this.displayName = displayName;
        }

        public Location getLocation() {
            return location;
        }

        public String getResolvedRoom() {
            return resolvedRoom;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}