package com.foxy.deadOrAlive;

import com.foxy.deadOrAlive.functions.DoaCommand;
import com.foxy.deadOrAlive.functions.EventManager;
import com.foxy.deadOrAlive.functions.MessageManager;
import com.foxy.deadOrAlive.functions.RoomManager;
import com.foxy.deadOrAlive.functions.RoomSetupManager;
import com.foxy.deadOrAlive.functions.TeleportManager;
import com.foxy.deadOrAlive.functions.TeleportSetupManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class DeadOrAlive extends JavaPlugin {

    private MessageManager messageManager;
    private RoomManager roomManager;
    private RoomSetupManager roomSetupManager;
    private TeleportManager teleportManager;
    private TeleportSetupManager teleportSetupManager;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messageManager = new MessageManager(this);
        roomManager = new RoomManager(this);
        roomSetupManager = new RoomSetupManager(this);
        teleportSetupManager = new TeleportSetupManager(this);
        teleportManager = new TeleportManager(this);
        eventManager = new EventManager(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(roomSetupManager, this);
        pluginManager.registerEvents(teleportSetupManager, this);
        pluginManager.registerEvents(teleportManager, this);
        pluginManager.registerEvents(eventManager, this);

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