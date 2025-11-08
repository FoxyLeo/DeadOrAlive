package com.foxy.deadOrAlive.command;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DoaCommand implements CommandExecutor, TabCompleter {

    private final DeadOrAlive plugin;

    public DoaCommand(DeadOrAlive plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("deadoralive.use")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessageManager().getMessage("usage"));
            return true;
        }

        if (args.length == 1) {
            String subCommand = args[0];

            if (subCommand.equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("deadoralive.reload")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }

                plugin.reloadConfig();
                plugin.getMessageManager().reload();
                plugin.getLobbyManager().reload();
                plugin.getRoomManager().reload();
                plugin.getTeleportManager().reload();
                if (plugin.getEventManager() != null) {
                    plugin.getEventManager().reloadSettings();
                }
                sender.sendMessage(plugin.getMessageManager().getMessage("reload-success"));
                return true;
            }

            if (subCommand.equalsIgnoreCase("setlobby")) {
                if (!sender.hasPermission("deadoralive.setlobby")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("not-player"));
                    return true;
                }

                if (plugin.getRoomSetupManager().hasActiveSessions()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setlobby-blocked-by-rooms"));
                    return true;
                }

                if (plugin.getTeleportSetupManager().hasActiveSessions()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setlobby-blocked-by-teleports"));
                    return true;
                }

                plugin.getLobbySelectionManager().startSelection(player);
                return true;
            }

            if (subCommand.equalsIgnoreCase("setrooms")) {
                if (!sender.hasPermission("deadoralive.setrooms")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("not-player"));
                    return true;
                }

                if (plugin.getLobbySelectionManager().isSelecting(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setrooms-blocked-by-lobby"));
                    return true;
                }

                if (plugin.getTeleportSetupManager().hasActiveSessions()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setrooms-blocked-by-teleports"));
                    return true;
                }

                plugin.getRoomSetupManager().startSession(player);
                return true;
            }

            if (subCommand.equalsIgnoreCase("setteleports")) {
                if (!sender.hasPermission("deadoralive.setteleports")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("not-player"));
                    return true;
                }

                if (plugin.getLobbySelectionManager().isSelecting(player.getUniqueId())) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setteleports-blocked-by-lobby"));
                    return true;
                }

                if (plugin.getRoomSetupManager().hasActiveSessions()) {
                    player.sendMessage(plugin.getMessageManager().getMessage("setteleports-blocked-by-rooms"));
                    return true;
                }

                plugin.getTeleportSetupManager().startSession(player);
                return true;
            }
            if (subCommand.equalsIgnoreCase("start")) {
                if (!sender.hasPermission("deadoralive.start")) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("no-permission"));
                    return true;
                }

                if (plugin.getEventManager() == null) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("event-start-unavailable"));
                    return true;
                }

                if (!plugin.getRoomManager().areAllRoomsConfigured()) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("event-start-rooms-not-configured"));
                    return true;
                }

                if (!plugin.getTeleportManager().hasConfiguredTeleports()) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("event-start-teleports-not-configured"));
                    return true;
                }

                plugin.getEventManager().startEvent(sender);
                return true;
            }
            return true;
        }

        sender.sendMessage(plugin.getMessageManager().getMessage("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("deadoralive.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("deadoralive.setlobby")) {
                subCommands.add("setlobby");
            }
            if (sender.hasPermission("deadoralive.setrooms")) {
                subCommands.add("setrooms");
            }
            if (sender.hasPermission("deadoralive.setteleports")) {
                subCommands.add("setteleports");
            }
            if (sender.hasPermission("deadoralive.start")) {
                subCommands.add("start");
            }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
            Collections.sort(completions);
            return completions;
        }
        return Collections.emptyList();
    }
}