package com.foxy.deadOrAlive.functions;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RoomManager {

    private final DeadOrAlive plugin;
    private final File roomFile;
    private FileConfiguration configuration;
    private final Map<String, RoomData> rooms = new HashMap<>();

    public RoomManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        this.roomFile = new File(plugin.getDataFolder(), "rooms.yml");
        saveDefaultRooms();
        reload();
    }

    private void saveDefaultRooms() {
        if (!roomFile.exists()) {
            if (!roomFile.getParentFile().exists()) {
                roomFile.getParentFile().mkdirs();
            }
            plugin.saveResource("rooms.yml", false);
        }
    }

    public void reload() {
        configuration = YamlConfiguration.loadConfiguration(roomFile);
        loadRooms();
    }

    private void loadRooms() {
        rooms.clear();
        if (configuration == null) {
            return;
        }

        for (String key : configuration.getKeys(false)) {
            ConfigurationSection section = configuration.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String type = section.getString("type", "safe");
            boolean hasCoordinates = section.contains("x") && section.contains("y") && section.contains("z");
            Vector center = null;
            if (hasCoordinates) {
                double x = section.getDouble("x");
                double y = section.getDouble("y");
                double z = section.getDouble("z");
                if (x != 0 || y != 0 || z != 0) {
                    center = new Vector(x, y, z);
                }
            }

            rooms.put(key.toLowerCase(), new RoomData(type, center));
        }
    }

    public boolean save() {
        if (configuration == null) {
            return false;
        }

        try {
            configuration.save(roomFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save rooms.yml: " + exception.getMessage());
            return false;
        }
    }

    public Map<String, Vector> getRooms() {
        Map<String, Vector> map = new HashMap<>();
        for (Map.Entry<String, RoomData> entry : rooms.entrySet()) {
            Vector center = entry.getValue().getCenter();
            if (center != null) {
                map.put(entry.getKey(), center);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public Vector getRoom(String roomId) {
        if (roomId == null) {
            return null;
        }
        RoomData data = rooms.get(roomId.toLowerCase());
        return data == null ? null : data.getCenter();
    }

    public boolean areAllRoomsConfigured() {
        if (rooms.isEmpty()) {
            return false;
        }

        for (RoomData data : rooms.values()) {
            if (data == null || !data.isConfigured()) {
                return false;
            }
        }

        return true;
    }

    public String getRoomType(String roomId) {
        if (roomId == null) {
            return "";
        }
        RoomData data = rooms.get(roomId.toLowerCase());
        return data == null ? "" : data.getType();
    }

    public Iterable<String> getRoomIds() {
        return Collections.unmodifiableSet(rooms.keySet());
    }

    public boolean setRoomCoordinates(String roomId, Location location) {
        if (configuration == null || roomId == null || location == null) {
            return false;
        }

        String key = roomId.toLowerCase();
        ConfigurationSection section = configuration.getConfigurationSection(key);
        if (section == null) {
            section = configuration.createSection(key);
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        section.set("x", x);
        section.set("y", y);
        section.set("z", z);

        String type = section.getString("type", "safe");
        Vector center = (x == 0 && y == 0 && z == 0) ? null : new Vector(x, y, z);
        rooms.put(key, new RoomData(type, center));
        return save();
    }

    public static class RoomData {
        private final String type;
        private final Vector center;

        public RoomData(String type, Vector center) {
            this.type = type == null ? "" : type;
            this.center = center;
        }

        public boolean isConfigured() {
            return center != null;
        }

        public String getType() {
            return type;
        }

        public Vector getCenter() {
            return center;
        }
    }
}