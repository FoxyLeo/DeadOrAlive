package com.foxy.deadOrAlive.event;

import com.foxy.deadOrAlive.DeadOrAlive;
import com.foxy.deadOrAlive.lobby.LobbyManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventManager implements Listener {

    private final DeadOrAlive plugin;
    private final LobbyManager lobbyManager;
    private final File playersFile;
    private final Map<UUID, String> playerRooms = new HashMap<>();
    private final Map<String, Set<UUID>> roomPlayers = new HashMap<>();
    private final Map<UUID, Double> heartsLost = new HashMap<>();
    private final Map<UUID, Long> teleportCooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingDeaths = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingDisconnects = new HashMap<>();
    private final Map<UUID, Location> disconnectLocations = new HashMap<>();
    private final Set<UUID> preEventNotified = ConcurrentHashMap.newKeySet();

    private BossBar bossBar;
    private BukkitTask countdownTask;
    private BukkitTask damageTask;
    private BukkitTask pendingMessageTask;
    private BukkitTask pendingCountdownStart;

    private boolean active;
    private String currentStageRoom;
    private int stageIndex;
    private int stageDurationSeconds;
    private int remainingSeconds;

    private int initialTimeSeconds;
    private int timeDecrementSeconds;
    private int damageIntervalSeconds;
    private int damageHearts;
    private int startDelaySeconds;
    private int deathDelaySeconds;

    public EventManager(DeadOrAlive plugin, LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.lobbyManager = lobbyManager;
        File temporalFolder = new File(plugin.getDataFolder(), "temporal");
        if (!temporalFolder.exists() && !temporalFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create temporal folder for event files.");
        }
        this.playersFile = new File(temporalFolder, "players.yml");
        reloadSettings();
    }

    public void reloadSettings() {
        this.initialTimeSeconds = Math.max(1, plugin.getConfig().getInt("event.initial-time", 120));
        this.timeDecrementSeconds = Math.max(0, plugin.getConfig().getInt("event.time-decrement", 10));
        this.damageIntervalSeconds = Math.max(1, plugin.getConfig().getInt("event.damage-interval", 1));
        this.damageHearts = Math.max(1, plugin.getConfig().getInt("event.damage-hearts", 1));
        this.startDelaySeconds = Math.max(0, plugin.getConfig().getInt("event.start-delay", 3));
        this.deathDelaySeconds = Math.max(1, plugin.getConfig().getInt("event.death-delay", 2));
    }

    public boolean startEvent(org.bukkit.command.CommandSender sender) {
        if (active) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("event-start-already-running"));
            }
            return false;
        }

        if (!plugin.getRoomManager().areAllRoomsConfigured()) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("event-start-rooms-not-configured"));
            }
            return false;
        }

        if (!plugin.getTeleportManager().hasConfiguredTeleports()) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("event-start-teleports-not-configured"));
            }
            return false;
        }

        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
        if (players.isEmpty()) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("event-start-no-players"));
            }
            return false;
        }

        Vector roomVector = plugin.getRoomManager().getRoom("room_1");
        if (roomVector == null) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().getMessage("event-start-missing-room"));
            }
            return false;
        }

        active = true;
        stageIndex = 0;
        currentStageRoom = "room_1";
        stageDurationSeconds = initialTimeSeconds;
        remainingSeconds = stageDurationSeconds;
        playerRooms.clear();
        roomPlayers.clear();
        heartsLost.clear();
        teleportCooldown.clear();
        preEventNotified.clear();
        disconnectLocations.clear();
        cancelAllPendingDisconnects();
        cancelCountdown();
        cancelDamageTask();
        cancelPendingAnnouncements();
        initializeRooms();

        Set<World> affectedWorlds = new HashSet<>();
        for (Player player : players) {
            Vector vector = roomVector;
            Location location = new Location(player.getWorld(), vector.getX(), vector.getY(), vector.getZ());
            player.teleport(location);
            player.setGameMode(GameMode.ADVENTURE);
            affectedWorlds.add(player.getWorld());
            addParticipant(player, currentStageRoom);
            heartsLost.put(player.getUniqueId(), 0.0D);
        }

        for (World world : affectedWorlds) {
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        }

        savePlayersFile();

        String title = plugin.getMessageManager().getMessage("event-start-title");
        String subtitle = plugin.getMessageManager().getMessage("event-start-subtitle");
        for (UUID uuid : playerRooms.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.sendTitle(title, subtitle, 10, 100, 10);
            }
        }

        pendingMessageTask = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastToParticipants(plugin.getMessageManager().getMessage("event-start-chat"));
                scheduleCountdownStart();
            }
        }.runTaskLater(plugin, 100L);

        if (sender != null) {
            sender.sendMessage(plugin.getMessageManager().getMessage("event-start-success"));
        }
        return true;
    }

    public boolean isEventActive() {
        return active;
    }

    public boolean isParticipant(UUID uuid) {
        return playerRooms.containsKey(uuid);
    }

    public boolean canUseTeleport(Player player, String originRoom) {
        if (player == null) {
            return false;
        }

        if (!active) {
            UUID uuid = player.getUniqueId();
            if (preEventNotified.add(uuid)) {
                String message = plugin.getMessageManager().getMessage("event-teleport-blocked");
                if (message != null && !message.isEmpty()) {
                    player.sendMessage(message);
                }
            }
            return false;
        }

        UUID uuid = player.getUniqueId();
        preEventNotified.remove(uuid);

        if (!isParticipant(player.getUniqueId())) {
            if (player.hasPermission("deadoralive.teleport.bypass")) {
                return true;
            }
            sendTeleportWarning(player, plugin.getMessageManager().getMessage("event-teleport-not-participant"));
            return false;
        }

        if (player.hasPermission("deadoralive.teleport.bypass")) {
            return true;
        }

        if (originRoom != null && currentStageRoom != null && !originRoom.equalsIgnoreCase(currentStageRoom)) {
            sendTeleportWarning(player, plugin.getMessageManager().getMessage("event-teleport-locked"));
            return false;
        }

        return true;
    }

    public void handleRoomTeleport(Player player, String destinationRoom) {
        if (!active || player == null || destinationRoom == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!isParticipant(uuid)) {
            return;
        }

        String normalizedDestination = destinationRoom.toLowerCase(Locale.ROOT);
        updatePlayerRoom(uuid, normalizedDestination);
        savePlayersFile();

        String roomType = plugin.getRoomManager().getRoomType(normalizedDestination);
        if (isDeathRoomType(roomType)) {
            scheduleDeath(player);
        } else {
            cancelPendingDeath(uuid);
        }

        checkForAdvanceOrFinish();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onParticipantDamage(EntityDamageEvent event) {
        if (!active) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!isParticipant(uuid)) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) {
            return;
        }

        double lostHearts = heartsLost.getOrDefault(uuid, 0.0D) + (finalDamage / 2.0D);
        heartsLost.put(uuid, lostHearts);
        applySlowness(player, lostHearts);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (!isParticipant(uuid)) {
            return;
        }

        event.setDeathMessage(null);
        String message = plugin.getMessageManager().getMessage("event-player-eliminated")
                .replace("%player%", player.getName());
        plugin.getServer().broadcastMessage(message);

        Location deathLocation = player.getLocation();
        World world = deathLocation.getWorld();
        if (world != null) {
            world.createExplosion(deathLocation, 0F, false, false);
        }

        removeParticipant(uuid);
        savePlayersFile();
        checkForAdvanceOrFinish();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getPlayer();
        if (isParticipant(player.getUniqueId())) {
            return;
        }

        Location respawnLocation = lobbyManager.getLobbyLocation();
        if (respawnLocation == null) {
            List<World> worlds = plugin.getServer().getWorlds();
            if (worlds.isEmpty()) {
                return;
            }
            World world = worlds.get(0);
            respawnLocation = resolveWorldSpawn(world);
        }
        if (respawnLocation != null) {
            event.setRespawnLocation(respawnLocation.clone());
        }

        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!isParticipant(uuid)) {
            return;
        }

        cancelPendingDisconnect(uuid);

        String roomId = playerRooms.get(uuid);
        if (roomId != null) {
            String roomType = plugin.getRoomManager().getRoomType(roomId);
            if (isDeathRoomType(roomType)) {
                scheduleDeath(player);
            }
        }

        player.setGameMode(GameMode.ADVENTURE);
        Location previousLocation = disconnectLocations.remove(uuid);
        if (previousLocation != null && previousLocation.getWorld() != null) {
            player.teleport(previousLocation);
        } else {
            teleportPlayerToSpawn(player);
        }
        updateBossBarPlayers();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!active) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!isParticipant(uuid)) {
            return;
        }

        cancelPendingDeath(uuid);

        int timeLeft = Math.max(0, remainingSeconds);
        if (countdownTask == null) {
            if (pendingCountdownStart != null) {
                timeLeft = Math.max(timeLeft, stageDurationSeconds);
            } else if (damageTask != null) {
                timeLeft = 0;
            } else if (timeLeft <= 0) {
                timeLeft = stageDurationSeconds;
            }
        }

        if (timeLeft <= 0) {
            eliminateDisconnectedPlayer(uuid, player.getName());
            return;
        }

        Location quitLocation = player.getLocation();
        if (quitLocation != null) {
            disconnectLocations.put(uuid, quitLocation.clone());
        }

        String message = plugin.getMessageManager().getMessage("event-player-disconnected")
                .replace("%player%", player.getName())
                .replace("%time%", formatTime(timeLeft));
        plugin.getServer().broadcastMessage(message);

        scheduleDisconnectElimination(uuid, player.getName(), timeLeft);
    }

    public void shutdown() {
        endEvent(false, Collections.emptyList(), false);
    }

    private void initializeRooms() {
        roomPlayers.clear();
        for (String roomId : plugin.getRoomManager().getRoomIds()) {
            roomPlayers.put(roomId.toLowerCase(Locale.ROOT), ConcurrentHashMap.newKeySet());
        }
    }

    private void addParticipant(Player player, String roomId) {
        UUID uuid = player.getUniqueId();
        playerRooms.put(uuid, roomId);
        roomPlayers.computeIfAbsent(roomId, key -> ConcurrentHashMap.newKeySet()).add(uuid);
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    private void updatePlayerRoom(UUID uuid, String destinationRoom) {
        String previousRoom = playerRooms.put(uuid, destinationRoom);
        if (previousRoom != null) {
            Set<UUID> previousSet = roomPlayers.get(previousRoom);
            if (previousSet != null) {
                previousSet.remove(uuid);
            }
        }
        roomPlayers.computeIfAbsent(destinationRoom, key -> ConcurrentHashMap.newKeySet()).add(uuid);
        updateBossBarPlayers();
    }

    private void removeParticipant(UUID uuid) {
        String roomId = playerRooms.remove(uuid);
        if (roomId != null) {
            Set<UUID> set = roomPlayers.get(roomId);
            if (set != null) {
                set.remove(uuid);
            }
        }
        heartsLost.remove(uuid);
        disconnectLocations.remove(uuid);
        cancelPendingDeath(uuid);
        cancelPendingDisconnect(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && bossBar != null) {
            bossBar.removePlayer(player);
        }
        updateBossBarPlayers();
    }

    private void scheduleCountdownStart() {
        cancelPendingCountdownStart();
        pendingCountdownStart = new BukkitRunnable() {
            @Override
            public void run() {
                startCountdown();
            }
        }.runTaskLater(plugin, startDelaySeconds * 20L);
    }

    private void startCountdown() {
        cancelCountdown();
        cancelDamageTask();

        if (!active) {
            return;
        }

        Set<UUID> participants = roomPlayers.getOrDefault(currentStageRoom, Collections.emptySet());
        if (participants.isEmpty()) {
            advanceStage();
            return;
        }

        stageDurationSeconds = Math.max(1, initialTimeSeconds - (stageIndex * timeDecrementSeconds));
        remainingSeconds = stageDurationSeconds;
        ensureBossBar();
        updateBossBarPlayers();
        updateBossBarTitle();
        bossBar.setProgress(1.0);

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }

                remainingSeconds--;
                if (remainingSeconds <= 0) {
                    bossBar.setProgress(0.0);
                    updateBossBarTitle();
                    cancel();
                    startDamagePhase();
                    return;
                }

                double progress = remainingSeconds / (double) stageDurationSeconds;
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                updateBossBarTitle();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startDamagePhase() {
        cancelDamageTask();
        if (!active) {
            return;
        }

        damageTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) {
                    cancel();
                    return;
                }

                Set<UUID> participants = new HashSet<>(roomPlayers.getOrDefault(currentStageRoom, Collections.emptySet()));
                if (participants.isEmpty()) {
                    cancel();
                    advanceStage();
                    return;
                }

                for (UUID uuid : participants) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null || player.isDead()) {
                        continue;
                    }

                    double damage = damageHearts * 2.0D;
                    player.damage(damage);
                }
            }
        }.runTaskTimer(plugin, damageIntervalSeconds * 20L, damageIntervalSeconds * 20L);
    }

    private void applySlowness(Player player, double heartsLostCount) {
        int amplifier = Math.max(0, (int) Math.floor(heartsLostCount) - 1);
        PotionEffect effect = new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, amplifier, false, false, false);
        player.addPotionEffect(effect);
    }

    private void advanceStage() {
        cancelCountdown();
        cancelDamageTask();

        String nextRoom = findNextStageRoom();
        if (nextRoom == null) {
            if (!pendingDeaths.isEmpty()) {
                return;
            }
            endEvent(false, Collections.emptyList());
            return;
        }

        if (!nextRoom.equalsIgnoreCase(currentStageRoom)) {
            String type = plugin.getRoomManager().getRoomType(nextRoom);
            if (!"finish".equalsIgnoreCase(type)) {
                stageIndex++;
            }
            currentStageRoom = nextRoom;
        }

        if ("finish".equalsIgnoreCase(plugin.getRoomManager().getRoomType(currentStageRoom))) {
            List<String> winners = collectParticipantsByRoom(currentStageRoom);
            endEvent(true, winners);
            return;
        }

        startCountdown();
    }

    private String findNextStageRoom() {
        List<String> orderedRooms = new ArrayList<>(roomPlayers.keySet());
        orderedRooms.sort(String.CASE_INSENSITIVE_ORDER);

        for (String roomId : orderedRooms) {
            Set<UUID> occupants = roomPlayers.get(roomId);
            if (occupants == null || occupants.isEmpty()) {
                continue;
            }
            String type = plugin.getRoomManager().getRoomType(roomId);
            if ("safe".equalsIgnoreCase(type) || "finish".equalsIgnoreCase(type)) {
                return roomId;
            }
        }
        return null;
    }

    private void checkForAdvanceOrFinish() {
        if (!active) {
            return;
        }

        if (playerRooms.isEmpty()) {
            endEvent(false, Collections.emptyList());
            return;
        }

        boolean allFinished = true;
        List<String> winners = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : playerRooms.entrySet()) {
            String roomId = entry.getValue();
            String type = plugin.getRoomManager().getRoomType(roomId);
            if (!"finish".equalsIgnoreCase(type)) {
                allFinished = false;
                break;
            }
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                winners.add(player.getName());
            } else {
                String name = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
                winners.add(name == null ? entry.getKey().toString() : name);
            }
        }

        if (allFinished) {
            endEvent(true, winners);
            return;
        }

        if (roomPlayers.getOrDefault(currentStageRoom, Collections.emptySet()).isEmpty()) {
            advanceStage();
        }
    }

    private void ensureBossBar() {
        if (bossBar != null) {
            return;
        }

        BarColor color = parseColor(plugin.getMessageManager().getRaw("event-bossbar-color"));
        BarStyle style = parseStyle(plugin.getMessageManager().getRaw("event-bossbar-style"));
        String title = plugin.getMessageManager().getMessage("event-bossbar-title");
        bossBar = Bukkit.createBossBar(title, color, style);
        bossBar.setVisible(true);
    }

    private void updateBossBarPlayers() {
        if (bossBar == null) {
            return;
        }

        bossBar.removeAll();
        for (UUID uuid : playerRooms.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
    }

    private void updateBossBarTitle() {
        if (bossBar == null) {
            return;
        }

        String baseTitle = plugin.getMessageManager().getMessage("event-bossbar-title");
        bossBar.setTitle(baseTitle.replace("%time%", formatTime(Math.max(0, remainingSeconds))));
    }

    private void broadcastToParticipants(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        for (UUID uuid : playerRooms.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void sendTeleportWarning(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = teleportCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < next) {
            return;
        }
        teleportCooldown.put(player.getUniqueId(), now + 1000L);
        player.sendMessage(message);
    }

    private void scheduleDeath(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        cancelPendingDeath(uuid);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                pendingDeaths.remove(uuid);
                Player target = plugin.getServer().getPlayer(uuid);
                if (target != null && target.isOnline() && !target.isDead()) {
                    target.setHealth(0.0);
                }
            }
        }.runTaskLater(plugin, deathDelaySeconds * 20L);
        pendingDeaths.put(uuid, task);
    }

    private void cancelPendingDeath(UUID uuid) {
        BukkitTask task = pendingDeaths.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void cancelDamageTask() {
        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }
    }

    private void cancelPendingAnnouncements() {
        if (pendingMessageTask != null) {
            pendingMessageTask.cancel();
            pendingMessageTask = null;
        }
        cancelPendingCountdownStart();
    }

    private void cancelPendingCountdownStart() {
        if (pendingCountdownStart != null) {
            pendingCountdownStart.cancel();
            pendingCountdownStart = null;
        }
    }

    private void endEvent(boolean success, List<String> winners) {
        endEvent(success, winners, true);
    }

    private void endEvent(boolean success, List<String> winners, boolean notify) {
        if (!active) {
            return;
        }
        active = false;

        Set<UUID> participants = new HashSet<>(playerRooms.keySet());

        cancelCountdown();
        cancelDamageTask();
        cancelPendingAnnouncements();
        cancelAllPendingDisconnects();

        for (BukkitTask task : pendingDeaths.values()) {
            task.cancel();
        }
        pendingDeaths.clear();

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }

        if (notify) {
            if (success) {
                String names = winners.isEmpty() ? "" : String.join(", ", winners);
                plugin.getServer().broadcastMessage(plugin.getMessageManager().getMessage("event-finish-success")
                        .replace("%players%", names));
            } else {
                plugin.getServer().broadcastMessage(plugin.getMessageManager().getMessage("event-finish-failure"));
            }
        }

        for (UUID uuid : heartsLost.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        heartsLost.clear();

        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                teleportPlayerToSpawn(player);
            }
        }

        if (playersFile.exists() && !playersFile.delete()) {
            plugin.getLogger().warning("Could not delete players.yml after the event.");
        }

        playerRooms.clear();
        roomPlayers.clear();
        teleportCooldown.clear();
        preEventNotified.clear();
        pendingDisconnects.clear();
        disconnectLocations.clear();
    }

    private List<String> collectParticipantsByRoom(String roomId) {
        Set<UUID> participants = roomPlayers.getOrDefault(roomId, Collections.emptySet());
        List<String> names = new ArrayList<>();
        for (UUID uuid : participants) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                names.add(player.getName());
            } else {
                String name = plugin.getServer().getOfflinePlayer(uuid).getName();
                names.add(name == null ? uuid.toString() : name);
            }
        }
        return names;
    }

    private void savePlayersFile() {
        YamlConfiguration configuration = new YamlConfiguration();
        for (String roomId : roomPlayers.keySet()) {
            Set<UUID> participants = roomPlayers.get(roomId);
            List<String> names;
            if (participants == null || participants.isEmpty()) {
                names = Collections.emptyList();
            } else {
                names = participants.stream()
                        .map(uuid -> {
                            Player player = plugin.getServer().getPlayer(uuid);
                            if (player != null) {
                                return player.getName();
                            }
                            String name = plugin.getServer().getOfflinePlayer(uuid).getName();
                            return name == null ? uuid.toString() : name;
                        })
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
            configuration.set(roomId, names);
        }

        try {
            configuration.save(playersFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save players.yml: " + exception.getMessage());
        }
    }

    private void scheduleDisconnectElimination(UUID uuid, String playerName, int timeLeftSeconds) {
        if (timeLeftSeconds <= 0) {
            eliminateDisconnectedPlayer(uuid, playerName);
            return;
        }
        cancelPendingDisconnect(uuid);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                eliminateDisconnectedPlayer(uuid, playerName);
            }
        }.runTaskLater(plugin, timeLeftSeconds * 20L);
        pendingDisconnects.put(uuid, task);
    }

    private void eliminateDisconnectedPlayer(UUID uuid, String fallbackName) {
        if (!active) {
            cancelPendingDisconnect(uuid);
            return;
        }

        pendingDisconnects.remove(uuid);

        String playerName = fallbackName;
        if (playerName == null || playerName.isEmpty()) {
            String offlineName = plugin.getServer().getOfflinePlayer(uuid).getName();
            playerName = offlineName == null ? uuid.toString() : offlineName;
        }

        removeParticipant(uuid);
        savePlayersFile();

        String message = plugin.getMessageManager().getMessage("event-player-disconnect-eliminated")
                .replace("%player%", playerName);
        plugin.getServer().broadcastMessage(message);

        checkForAdvanceOrFinish();
    }

    private void cancelPendingDisconnect(UUID uuid) {
        BukkitTask task = pendingDisconnects.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void cancelAllPendingDisconnects() {
        for (BukkitTask task : pendingDisconnects.values()) {
            task.cancel();
        }
        pendingDisconnects.clear();
    }

    private void teleportPlayerToSpawn(Player player) {
        if (player == null) {
            return;
        }

        Location target = lobbyManager.getLobbyLocation();
        if (target == null) {
            List<World> worlds = plugin.getServer().getWorlds();
            if (worlds.isEmpty()) {
                return;
            }
            World world = worlds.get(0);
            target = resolveWorldSpawn(world);
        }

        if (target != null) {
            player.teleport(target);
        }
    }

    private Location resolveWorldSpawn(World world) {
        if (world == null) {
            return null;
        }

        Location spawn = world.getSpawnLocation();
        if (spawn == null) {
            return null;
        }

        return ensureSafeSpawnLocation(spawn);
    }

    private Location ensureSafeSpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return location;
        }

        Location safeLocation = location.clone();
        int blockX = safeLocation.getBlockX();
        int blockZ = safeLocation.getBlockZ();

        safeLocation.setX(blockX + 0.5);
        safeLocation.setZ(blockZ + 0.5);
        return safeLocation;
    }


    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + "m " + seconds + "s";
    }

    private boolean isDeathRoomType(String roomType) {
        if (roomType == null) {
            return false;
        }
        return "dead".equalsIgnoreCase(roomType) || "death".equalsIgnoreCase(roomType);
    }

    private BarColor parseColor(String value) {
        if (value == null || value.isEmpty()) {
            return BarColor.RED;
        }
        try {
            return BarColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown boss bar color '" + value + "'. Using RED.");
            return BarColor.RED;
        }
    }

    private BarStyle parseStyle(String value) {
        if (value == null || value.isEmpty()) {
            return BarStyle.SOLID;
        }
        try {
            return BarStyle.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown boss bar style '" + value + "'. Using SOLID.");
            return BarStyle.SOLID;
        }
    }
}