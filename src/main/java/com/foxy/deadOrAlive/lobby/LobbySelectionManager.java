package com.foxy.deadOrAlive.lobby;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LobbySelectionManager implements Listener {

    private final DeadOrAlive plugin;
    private final LobbyManager lobbyManager;
    private final Set<UUID> activeSelections = new HashSet<>();
    private final Set<UUID> processingSelections = new HashSet<>();

    public LobbySelectionManager(DeadOrAlive plugin, LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
    }

    public void startSelection(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeSelections.contains(uuid)) {
            player.sendMessage(plugin.getMessageManager().getMessage("setlobby-already-in-progress"));
            return;
        }

        activeSelections.add(uuid);
        player.sendMessage(plugin.getMessageManager().getMessage("setlobby-start"));
    }

    public boolean isSelecting(UUID uuid) {
        return activeSelections.contains(uuid);
    }

    public boolean hasActiveSelections() {
        return !activeSelections.isEmpty();
    }

    public void cancelAllSelections() {
        for (UUID uuid : new HashSet<>(activeSelections)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                cancelSelection(player, true);
            } else {
                activeSelections.remove(uuid);
                processingSelections.remove(uuid);
            }
        }
    }

    private void cancelSelection(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        activeSelections.remove(uuid);
        processingSelections.remove(uuid);
        if (notify) {
            player.sendMessage(plugin.getMessageManager().getMessage("setlobby-cancelled"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeSelections.contains(uuid)) {
            cancelSelection(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!activeSelections.contains(uuid)) {
            return;
        }

        if (!processingSelections.add(uuid)) {
            event.setCancelled(true);
            return;
        }

        try {
            Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }

            event.setCancelled(true);

            Location location = block.getLocation();
            activeSelections.remove(uuid);

            if (!lobbyManager.updateLobbyLocation(location)) {
                activeSelections.add(uuid);
                player.sendMessage(plugin.getMessageManager().getMessage("setlobby-failed"));
                return;
            }

            player.sendMessage(plugin.getMessageManager().getMessage("setlobby-success")
                    .replace("%world%", block.getWorld() != null ? block.getWorld().getName() : "")
                    .replace("%x%", String.valueOf(block.getX()))
                    .replace("%y%", String.valueOf(block.getY()))
                    .replace("%z%", String.valueOf(block.getZ())));
        } finally {
            processingSelections.remove(uuid);
        }
    }
}
