package jc.ivanremoenunlago.listeners;

import jc.ivanremoenunlago.JobController;
import jc.ivanremoenunlago.utils.YAMLUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.nightexpress.excellentjobs.JobsAPI;
import su.nightexpress.excellentjobs.data.impl.JobData;
import su.nightexpress.excellentjobs.user.JobUser;
import su.nightexpress.excellentjobs.api.event.JobLeaveEvent;

import java.util.List;
import java.util.Objects;

/**
 * Listener for handling when a player leaves a job.
 * It safely decreases the player's job level and removes specific permissions and/or groups.
 */
public class JobLeaveListener implements Listener {

    private final JobController plugin;
    private final YAMLUtils yaml;

    public JobLeaveListener(JobController plugin) {
        this.plugin = plugin;
        this.yaml = plugin.getYamlUtils();
    }

    @EventHandler
    public void onJobLeave(JobLeaveEvent event) {
        Player player = event.getPlayer();
        String jobId = event.getJob().getId();

        // Decrease the player's job level safely
        reduceLevel(player, jobId);

        // Remove specific permissions and/or groups
        removePermissionsAndGroups(player, jobId);
    }

    /**
     * Safely reduces the player's level for the given job.
     * If the decrease exceeds current level, sets the level to 1.
     */
    private void reduceLevel(Player player, String jobId) {
        JobUser user = JobsAPI.getUserData(player);
        JobData data = user.getData(Objects.requireNonNull(JobsAPI.getJobById(jobId)));

        int decrease = yaml.getConfig().getInt("jobs." + jobId + ".on-leave.decrease-level", 0);
        int newLevel = Math.max(1, data.getLevel() - decrease);

        data.setLevel(newLevel);
        data.setXP(0); // Optional: reset XP
        data.update();

        plugin.getLogger().info("JobLeaveListener: Decreased level of " + player.getName() +
                " for job '" + jobId + "' by " + decrease + ". New level: " + newLevel);
    }

    /**
     * Removes permissions and groups defined in YAML from the player.
     * Groups are identified by the prefix "group.".
     * If a permission/group is not present, it is simply ignored.
     */
    private void removePermissionsAndGroups(Player player, String jobId) {
        List<String> list = yaml.getConfig().getStringList("jobs." + jobId + ".on-leave.remove-permissions");
        if (list.isEmpty()) return;

        // Load LuckPerms API
        LuckPerms lp = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (lp == null) {
            plugin.getLogger().severe("LuckPerms is not loaded. Cannot modify permissions for " + player.getName());
            return;
        }

        // Get the LuckPerms user object (player must be online)
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            plugin.getLogger().warning("LuckPerms user not found for online player " + player.getName());
            return;
        }

        // Iterate over the list and remove each permission or group if the user actually has it
        for (String entry : list) {
            if (entry.startsWith("group.")) {
                String groupName = entry.substring(6);
                boolean hasGroup = user.getInheritedGroups(user.getQueryOptions()).stream()
                        .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
                if (hasGroup) {
                    user.data().remove(InheritanceNode.builder(groupName).build());
                    plugin.getLogger().info("Removed group '" + groupName + "' from player " + player.getName());
                }
            } else {
                boolean hasPerm = user.getNodes().stream()
                        .filter(n -> n instanceof PermissionNode)
                        .map(n -> ((PermissionNode) n).getPermission())
                        .anyMatch(p -> p.equalsIgnoreCase(entry));
                if (hasPerm) {
                    user.data().remove(PermissionNode.builder(entry).build());
                    plugin.getLogger().info("Removed permission '" + entry + "' from player " + player.getName());
                }
            }
        }

        // Save changes to LuckPerms
        lp.getUserManager().saveUser(user);
        plugin.getLogger().info("JobLeaveListener: Permissions and groups updated for player " + player.getName());

    }

}
