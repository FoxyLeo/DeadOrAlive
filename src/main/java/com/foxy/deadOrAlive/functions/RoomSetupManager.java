package com.foxy.deadOrAlive.functions;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RoomSetupManager implements Listener {

    private static final int TOTAL_ROOMS = 9;

    private final DeadOrAlive plugin;
    private final NamespacedKey centerKey;
    private final NamespacedKey cancelKey;
    private final File temporalFolder;
    private final Map<UUID, Integer> activeSessions = new HashMap<>();
    private final DecimalFormat coordinateFormat = new DecimalFormat("#.##");

    public RoomSetupManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        this.centerKey = new NamespacedKey(plugin, "setrooms_center");
        this.cancelKey = new NamespacedKey(plugin, "setrooms_cancel");
        this.temporalFolder = new File(plugin.getDataFolder(), "temporal");
        this.coordinateFormat.setDecimalFormatSymbols(new DecimalFormatSymbols(Locale.US));

        if (!temporalFolder.exists() && !temporalFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create temporal folder for inventories.");
        }
    }

    public void startSession(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeSessions.containsKey(uuid)) {
            player.sendMessage(plugin.getMessageManager().getMessage("setrooms-already-in-progress"));
            return;
        }

        if (!storeInventory(player)) {
            player.sendMessage(plugin.getMessageManager().getMessage("setrooms-storage-error"));
            return;
        }

        giveSetupItems(player);
        activeSessions.put(uuid, 1);
        sendStartMessage(player, 1);
    }

    public boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    public boolean isInSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    private void sendStartMessage(Player player, int roomNumber) {
        player.sendMessage(formatRoomMessage("setrooms-start", roomNumber, null));
    }

    private boolean storeInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack[] extra = player.getInventory().getExtraContents();

        File file = getTemporalFile(player.getUniqueId());
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("inventory", contents);
        configuration.set("armor", armor);
        configuration.set("extra", extra);

        try {
            configuration.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to store inventory for " + player.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        File file = getTemporalFile(uuid);
        if (!file.exists()) {
            player.getInventory().clear();
            player.updateInventory();
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ItemStack[] contents = toItemStackArray(configuration.getList("inventory"));
        ItemStack[] armor = toItemStackArray(configuration.getList("armor"));
        ItemStack[] extra = toItemStackArray(configuration.getList("extra"));

        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(armor);
        player.getInventory().setExtraContents(extra);
        player.updateInventory();

        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete temporal inventory file for " + player.getName());
        }
    }

    private ItemStack[] toItemStackArray(List<?> list) {
        if (list == null) {
            return new ItemStack[0];
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        for (Object object : list) {
            if (object instanceof ItemStack itemStack) {
                itemStacks.add(itemStack);
            } else {
                itemStacks.add(null);
            }
        }
        return itemStacks.toArray(new ItemStack[0]);
    }

    private void giveSetupItems(Player player) {
        player.getInventory().clear();

        ItemStack centerItem = createSetupItem(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "Center",
                List.of(ChatColor.GRAY + "Set the center of each room."), centerKey);

        ItemStack cancelItem = createSetupItem(Material.BARRIER, "Cancel",
                List.of(ChatColor.GRAY + "Cancel the setup."), cancelKey);

        player.getInventory().setItem(0, centerItem);
        player.getInventory().setItem(8, cancelItem);
        player.updateInventory();
    }

    private ItemStack createSetupItem(Material material, String name, List<String> lore, NamespacedKey key) {
        ItemStack itemStack = new ItemStack(material, 1);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            itemMeta.setDisplayName(ChatColor.YELLOW + name);
            itemMeta.setLore(lore);
            PersistentDataContainer container = itemMeta.getPersistentDataContainer();
            container.set(key, PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }

    private boolean isCenterItem(ItemStack itemStack) {
        return hasKey(itemStack, centerKey);
    }

    private boolean isCancelItem(ItemStack itemStack) {
        return hasKey(itemStack, cancelKey);
    }

    private boolean hasKey(ItemStack itemStack, NamespacedKey key) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Integer currentRoom = activeSessions.get(uuid);
        if (currentRoom == null) {
            return;
        }

        if (!isCenterItem(event.getItemInHand())) {
            return;
        }

        event.setCancelled(true);

        Location blockLocation = event.getBlock().getLocation().add(0.5, 0, 0.5);
        String roomId = "room_" + currentRoom;
        boolean saved = plugin.getRoomManager().setRoomCoordinates(roomId, blockLocation);

        if (!saved) {
            player.sendMessage(plugin.getMessageManager().getMessage("setrooms-save-error"));
            return;
        }

        plugin.getRoomManager().reload();
        Vector vector = plugin.getRoomManager().getRoom(roomId);
        player.sendMessage(formatRoomMessage("setrooms-room-set", currentRoom, vector));

        int nextRoom = currentRoom + 1;
        if (nextRoom > TOTAL_ROOMS) {
            finishSession(player);
        } else {
            activeSessions.put(uuid, nextRoom);
            player.sendMessage(formatRoomMessage("setrooms-next-room", nextRoom, null));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!isCancelItem(item)) {
            return;
        }

        Player player = event.getPlayer();
        if (!activeSessions.containsKey(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        cancelSession(player, true, true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack itemStack = event.getItemDrop().getItemStack();
        if (isCenterItem(itemStack) || isCancelItem(itemStack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isCenterItem(currentItem) || isCancelItem(currentItem) || isCenterItem(cursor) || isCancelItem(cursor)) {
            if (event.getWhoClicked() instanceof Player player && activeSessions.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activeSessions.containsKey(player.getUniqueId())) {
            cancelSession(player, true, false);
        }
    }

    public void cancelSession(Player player, boolean restoreInventory, boolean notify) {
        UUID uuid = player.getUniqueId();
        activeSessions.remove(uuid);

        if (restoreInventory) {
            restoreInventory(player);
        }

        if (notify) {
            player.sendMessage(plugin.getMessageManager().getMessage("setrooms-cancelled"));
        }
    }

    private void finishSession(Player player) {
        activeSessions.remove(player.getUniqueId());
        restoreInventory(player);
        player.sendMessage(plugin.getMessageManager().getMessage("setrooms-finished"));
    }

    public void cancelAllSessions() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cancelSession(player, true, false);
            } else {
                activeSessions.remove(uuid);
                File file = getTemporalFile(uuid);
                if (file.exists() && !file.delete()) {
                    plugin.getLogger().warning("Could not delete temporal inventory file for session " + uuid);
                }
            }
        }
    }

    private String formatRoomMessage(String path, int roomNumber, Vector vector) {
        String message = plugin.getMessageManager().getMessage(path);
        message = message.replace("%room%", String.valueOf(roomNumber));

        if (vector != null) {
            message = message.replace("%x%", formatCoordinate(vector.getX()));
            message = message.replace("%y%", formatCoordinate(vector.getY()));
            message = message.replace("%z%", formatCoordinate(vector.getZ()));
        }

        return message;
    }

    private String formatCoordinate(double value) {
        return coordinateFormat.format(value);
    }

    private File getTemporalFile(UUID uuid) {
        return new File(temporalFolder, uuid + ".yml");
    }
}