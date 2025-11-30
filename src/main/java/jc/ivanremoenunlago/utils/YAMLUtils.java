package jc.ivanremoenunlago.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class YAMLUtils {

    private final JavaPlugin plugin;
    private final String fileName;
    private File file;
    private FileConfiguration config;

    public YAMLUtils(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    /**
     * Load or reload the YAML file
     */
    public void load() {
        file = new File(plugin.getDataFolder(), fileName);

        // Save default if not exist
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        config = YamlConfiguration.loadConfiguration(file);
        plugin.getLogger().info(fileName + " has been loaded.");
    }

    /**
     * Save the YAML file
     */
    public void save() {
        if (config == null || file == null) return;

        try {
            config.save(file);
            plugin.getLogger().info(fileName + " has been saved.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + fileName + ": " + e.getMessage());
        }
    }

    /**
     * Get the configuration object
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
