package com.foxy.deadOrAlive;

import com.foxy.deadOrAlive.command.DoaCommand;
import com.foxy.deadOrAlive.event.EventManager;
import com.foxy.deadOrAlive.lobby.LobbyManager;
import com.foxy.deadOrAlive.lobby.LobbySelectionManager;
import com.foxy.deadOrAlive.message.MessageManager;
import com.foxy.deadOrAlive.room.RoomManager;
import com.foxy.deadOrAlive.room.setup.RoomSetupManager;
import com.foxy.deadOrAlive.teleport.TeleportManager;
import com.foxy.deadOrAlive.teleport.setup.TeleportSetupManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DeadOrAlive extends JavaPlugin {

    private MessageManager messageManager;
    private LobbyManager lobbyManager;
    private LobbySelectionManager lobbySelectionManager;
    private RoomManager roomManager;
    private RoomSetupManager roomSetupManager;
    private TeleportManager teleportManager;
    private TeleportSetupManager teleportSetupManager;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messageManager = new MessageManager(this);
        lobbyManager = new LobbyManager(this);
        roomManager = new RoomManager(this);
        roomSetupManager = new RoomSetupManager(this);
        teleportSetupManager = new TeleportSetupManager(this);
        teleportManager = new TeleportManager(this);
        lobbySelectionManager = new LobbySelectionManager(this, lobbyManager);
        eventManager = new EventManager(this, lobbyManager);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(roomSetupManager, this);
        pluginManager.registerEvents(teleportSetupManager, this);
        pluginManager.registerEvents(teleportManager, this);
        pluginManager.registerEvents(eventManager, this);
        pluginManager.registerEvents(lobbySelectionManager, this);

        DoaCommand doaCommand = new DoaCommand(this);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("doa"), "Command 'doa' not defined in plugin.yml");
        pluginCommand.setExecutor(doaCommand);
        pluginCommand.setTabCompleter(doaCommand);

        logStartupMessage();
    }

    @Override
    public void onDisable() {
        if (roomSetupManager != null) {
            roomSetupManager.cancelAllSessions();
        }
        if (teleportSetupManager != null) {
            teleportSetupManager.cancelAllSessions();
        }
        if (lobbySelectionManager != null) {
            lobbySelectionManager.cancelAllSelections();
        }
        if (eventManager != null) {
            eventManager.shutdown();
        }
        logShutdownMessage();
    }

    private void logStartupMessage() {
        String pluginName = getDescription().getName();
        String version = getDescription().getVersion();
        String author = String.join(", ", getDescription().getAuthors());

        String line = "----------------------------------------------";
        getServer().getConsoleSender().sendMessage(line);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Version: " + version);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Author: " + author);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Enjoy the plugin! :)");
        getServer().getConsoleSender().sendMessage(line);
    }

    private void logShutdownMessage() {
        String pluginName = getDescription().getName();
        String line = "----------------------------------------------";
        getServer().getConsoleSender().sendMessage(line);
        getServer().getConsoleSender().sendMessage("[" + pluginName + "] Plugin disabled.");
        getServer().getConsoleSender().sendMessage(line);
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public LobbySelectionManager getLobbySelectionManager() {
        return lobbySelectionManager;
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }

    public RoomSetupManager getRoomSetupManager() {
        return roomSetupManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public TeleportSetupManager getTeleportSetupManager() {
        return teleportSetupManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }
}