package jc.ivanremoenunlago;

import jc.ivanremoenunlago.listeners.JobJoinListener;
import jc.ivanremoenunlago.listeners.JobLeaveListener;
import jc.ivanremoenunlago.utils.YAMLUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class JobController extends JavaPlugin {

    private static JobController instance;
    private LuckPerms luckPerms;
    private YAMLUtils yamlUtils;

    @Override
    public void onEnable() {
        instance = this;

        // Save and load the jobs_settings.yml file
        saveDefaultConfig();
        yamlUtils = new YAMLUtils(this, "jobs_settings.yml");
        yamlUtils.load();

        // Get LuckPerms instance
        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            getLogger().severe("LuckPerms is not loaded! The plugin will not function properly.");
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new JobJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new JobLeaveListener(this), this);


        getLogger().info("JobController has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        getLogger().info("JobController has been disabled.");
    }

    public static JobController getInstance() {
        return instance;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public YAMLUtils getYamlUtils() {
        return yamlUtils;
    }
}
