package com.foxy.deadOrAlive.message;

import com.foxy.deadOrAlive.DeadOrAlive;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class MessageManager {

    private static final String PREFIX_PATH = "prefix";

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String[] SUPPORTED_LANGUAGES = {"en", "es"};

    private final DeadOrAlive plugin;
    private final File messagesFolder;
    private File messageFile;
    private FileConfiguration configuration;
    private String currentLanguage = DEFAULT_LANGUAGE;

    public MessageManager(DeadOrAlive plugin) {
        this.plugin = plugin;
        this.messagesFolder = new File(plugin.getDataFolder(), "messages");
        saveDefaultMessages();
        reload();
    }

    private void saveDefaultMessages() {
        if (!messagesFolder.exists() && !messagesFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create messages folder.");
            return;
        }

        for (String language : SUPPORTED_LANGUAGES) {
            File languageFile = new File(messagesFolder, language + ".yml");
            if (!languageFile.exists()) {
                plugin.saveResource("messages/" + language + ".yml", false);
            }
        }
    }

    public void reload() {
        messageFile = resolveMessageFile();
        configuration = YamlConfiguration.loadConfiguration(messageFile);
    }

    public boolean save() {
        if (configuration == null) {
            return false;
        }

        try {
            configuration.save(messageFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save " + messageFile.getName() + ": " + exception.getMessage());
            return false;
        }
    }

    public String getMessage(String path) {
        if (configuration == null) {
            return ChatColor.translateAlternateColorCodes('&', "&cMessages file not loaded.");
        }

        String message = configuration.getString(path, "&cMessage not found: " + path);
        String prefix = configuration.getString(PREFIX_PATH, "");

        if (message == null) {
            message = "";
        }

        if (prefix == null) {
            prefix = "";
        }

        message = message.replace("%prefix%", prefix);
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getPrefix() {
        if (configuration == null) {
            return ChatColor.translateAlternateColorCodes('&', "");
        }

        String prefix = configuration.getString(PREFIX_PATH, "");
        return ChatColor.translateAlternateColorCodes('&', prefix == null ? "" : prefix);
    }

    public String getRaw(String path) {
        if (configuration == null) {
            return "";
        }

        String value = configuration.getString(path);
        return value == null ? "" : value;
    }

    private File resolveMessageFile() {
        String configuredLanguage = plugin.getConfig().getString("lang", DEFAULT_LANGUAGE);
        if (configuredLanguage == null) {
            configuredLanguage = DEFAULT_LANGUAGE;
        }

        String normalized = configuredLanguage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            normalized = DEFAULT_LANGUAGE;
        }

        File languageFile = new File(messagesFolder, normalized + ".yml");
        if (!languageFile.exists()) {
            if (!DEFAULT_LANGUAGE.equals(normalized)) {
                plugin.getLogger().warning("Messages file for language '" + normalized + "' not found. Falling back to '" + DEFAULT_LANGUAGE + "'.");
            }
            normalized = DEFAULT_LANGUAGE;
            languageFile = new File(messagesFolder, normalized + ".yml");
            if (!languageFile.exists()) {
                plugin.saveResource("messages/" + normalized + ".yml", false);
            }
        }

        currentLanguage = normalized;
        return languageFile;
    }
}