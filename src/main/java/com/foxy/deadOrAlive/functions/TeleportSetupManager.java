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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TeleportSetupManager implements Listener {

    private final DeadOrAlive plugin;
    private final NamespacedKey teleportKey;
    private final NamespacedKey cancelKey;
    private final File temporalFolder;
    private final Map<UUID, TeleportProgress> activeSessions = new HashMap<>();
    private final List<TeleportStep> steps;

    public TeleportSetupManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        this.teleportKey = new NamespacedKey(plugin, "setteleports_block");
        this.cancelKey = new NamespacedKey(plugin, "setteleports_cancel");
        this.temporalFolder = new File(plugin.getDataFolder(), "teleports_temp");
        this.steps = createSteps();

        if (!temporalFolder.exists() && !temporalFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create temporal folder for teleport inventories.");
        }
    }

    private List<TeleportStep> createSteps() {
        List<TeleportStep> list = new ArrayList<>();
        list.add(new TeleportStep("room_1", List.of(
                new TeleportTarget("teleport_to_room_2", "room_2", "Room 2"),
                new TeleportTarget("teleport_to_room_4", "room_4", "Room 4")
        )));
        list.add(new TeleportStep("room_2", List.of(
                new TeleportTarget("teleport_to_room_3", "room_3", "Room 3"),
                new TeleportTarget("teleport_to_room_5", "room_5", "Room 5")
        )));
        list.add(new TeleportStep("room_3", List.of(
                new TeleportTarget("teleport_to_room_6", "room_6", "Room 6"),
                new TeleportTarget("teleport_to_room_death", "room_death", "Room Death")
        )));
        list.add(new TeleportStep("room_6", List.of(
                new TeleportTarget("teleport_to_room_5", "room_5", "Room 5"),
                new TeleportTarget("teleport_to_room_7", "room_7", "Room 7")
        )));
        list.add(new TeleportStep("room_7", List.of(
                new TeleportTarget("teleport_to_room_death", "room_death", "Room Death"),
                new TeleportTarget("teleport_to_room_8", "room_8", "Room 8")
        )));
        list.add(new TeleportStep("room_8", List.of(
                new TeleportTarget("teleport_to_room_5", "room_5", "Room 5"),
                new TeleportTarget("teleport_to_room_9", "room_9", "Room 9")
        )));
        return list;
    }

    public void startSession(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeSessions.containsKey(uuid)) {
            player.sendMessage(plugin.getMessageManager().getMessage("setteleports-already-in-progress"));
            return;
        }

        if (!storeInventory(player)) {
            player.sendMessage(plugin.getMessageManager().getMessage("setteleports-storage-error"));
            return;
        }

        giveSetupItems(player);
        TeleportProgress progress = new TeleportProgress(0, 0);
        activeSessions.put(uuid, progress);
        sendTargetMessage(player, progress);
    }

    public boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    public boolean isInSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
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
            plugin.getLogger().severe("Failed to store teleport inventory for " + player.getName() + ": " + exception.getMessage());
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
        player.getInventory().setContents(toItemStackArray(configuration.getList("inventory")));
        player.getInventory().setArmorContents(toItemStackArray(configuration.getList("armor")));
        player.getInventory().setExtraContents(toItemStackArray(configuration.getList("extra")));
        player.updateInventory();

        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete teleport temporal inventory for " + player.getName());
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

        ItemStack teleportItem = createTeleportItem();
        ItemStack cancelItem = createCancelItem();

        player.getInventory().setItem(0, teleportItem);
        player.getInventory().setItem(8, cancelItem);
        player.updateInventory();
    }

    private ItemStack createTeleportItem() {
        ItemStack itemStack = new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 1);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "Set Teleport");
            meta.setLore(List.of(ChatColor.GRAY + "You will set the teleport from one room to another"));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(teleportKey, PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack createCancelItem() {
        ItemStack itemStack = new ItemStack(Material.BARRIER, 1);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Cancel");
            meta.setLore(List.of(ChatColor.GRAY + "Cancel the setup."));
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(cancelKey, PersistentDataType.BYTE, (byte) 1);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private boolean isTeleportItem(ItemStack itemStack) {
        return hasKey(itemStack, teleportKey);
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
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        TeleportProgress progress = activeSessions.get(uuid);
        if (progress == null) {
            return;
        }

        if (!isTeleportItem(event.getItemInHand())) {
            return;
        }

        event.setCancelled(false);

        Location location = event.getBlockPlaced().getLocation();
        TeleportStep step = steps.get(progress.stepIndex());
        TeleportTarget target = step.targets().get(progress.targetIndex());

        boolean saved = plugin.getTeleportManager().setTeleport(step.originRoom(), target.key(), location, target.destination());
        if (!saved) {
            player.sendMessage(plugin.getMessageManager().getMessage("setteleports-save-error"));
            event.getBlockPlaced().setType(Material.AIR);
            return;
        }

        player.sendMessage(plugin.getMessageManager().getMessage("setteleports-target-set")
                .replace("%room%", formatRoom(step.originRoom()))
                .replace("%destination%", target.displayName())
                .replace("%x%", String.valueOf(location.getBlockX()))
                .replace("%y%", String.valueOf(location.getBlockY()))
                .replace("%z%", String.valueOf(location.getBlockZ())));

        advanceProgress(player, progress);
    }

    private void advanceProgress(Player player, TeleportProgress progress) {
        TeleportStep currentStep = steps.get(progress.stepIndex());
        int nextTargetIndex = progress.targetIndex() + 1;
        if (nextTargetIndex < currentStep.targets().size()) {
            TeleportProgress updated = new TeleportProgress(progress.stepIndex(), nextTargetIndex);
            activeSessions.put(player.getUniqueId(), updated);
            sendTargetMessage(player, updated);
            return;
        }

        int nextStepIndex = progress.stepIndex() + 1;
        if (nextStepIndex >= steps.size()) {
            finishSession(player);
            return;
        }

        TeleportProgress updated = new TeleportProgress(nextStepIndex, 0);
        activeSessions.put(player.getUniqueId(), updated);
        player.sendMessage(plugin.getMessageManager().getMessage("setteleports-next-room")
                .replace("%room%", formatRoom(steps.get(nextStepIndex).originRoom())));
        sendTargetMessage(player, updated);
    }

    private void sendTargetMessage(Player player, TeleportProgress progress) {
        TeleportStep step = steps.get(progress.stepIndex());
        TeleportTarget target = step.targets().get(progress.targetIndex());
        player.sendMessage(plugin.getMessageManager().getMessage("setteleports-start")
                .replace("%room%", formatRoom(step.originRoom()))
                .replace("%destination%", target.displayName()));
    }

    private String formatRoom(String roomId) {
        String suffix = roomId.replace("room_", "");
        if (suffix.equalsIgnoreCase("death")) {
            return "Room Death";
        }
        return "Room " + suffix.toUpperCase(Locale.ROOT);
    }

    public void cancelSession(Player player, boolean restoreInventory, boolean notify) {
        UUID uuid = player.getUniqueId();
        activeSessions.remove(uuid);

        if (restoreInventory) {
            restoreInventory(player);
        }

        if (notify) {
            player.sendMessage(plugin.getMessageManager().getMessage("setteleports-cancelled"));
        }
    }

    private void finishSession(Player player) {
        activeSessions.remove(player.getUniqueId());
        restoreInventory(player);
        player.sendMessage(plugin.getMessageManager().getMessage("setteleports-finished"));
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
        if (isTeleportItem(itemStack) || isCancelItem(itemStack)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (isTeleportItem(current) || isTeleportItem(cursor) || isCancelItem(current) || isCancelItem(cursor)) {
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

    public void cancelAllSessions() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cancelSession(player, true, false);
            } else {
                activeSessions.remove(uuid);
                File file = getTemporalFile(uuid);
                if (file.exists() && !file.delete()) {
                    plugin.getLogger().warning("Could not delete teleport temporal inventory for session " + uuid);
                }
            }
        }
    }

    private File getTemporalFile(UUID uuid) {
        return new File(temporalFolder, uuid + ".yml");
    }


    private record TeleportProgress(int stepIndex, int targetIndex) {
    }

    private record TeleportStep(String originRoom, List<TeleportTarget> targets) {
    }

    private record TeleportTarget(String key, String destination, String displayName) {
    }
}