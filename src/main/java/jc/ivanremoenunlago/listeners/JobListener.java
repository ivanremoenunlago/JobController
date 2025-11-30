package jc.ivanremoenunlago.listeners;

import jc.ivanremoenunlago.JobController;
import jc.ivanremoenunlago.utils.YAMLUtils;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import su.nightexpress.excellentjobs.api.event.JobJoinEvent;
import su.nightexpress.excellentjobs.api.event.JobLeaveEvent;
import su.nightexpress.excellentjobs.api.user.JobUser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;

import java.util.List;

public class JobListener implements Listener {

    private final JobController plugin;
    private final YAMLUtils yamlUtils;

    public JobListener(JobController plugin) {
        this.plugin = plugin;
        this.yamlUtils = plugin.getYamlUtils();
    }

    @EventHandler
    public void onJobJoin(JobJoinEvent event) {
        String jobName = event.getJob().getId();
        JobUser user = event.getUser();

        // Check incompatible jobs
        List<String> incompatibleJobs = yamlUtils.getConfig()
                .getStringList("jobs." + jobName + ".incompatible-jobs");

        for (String incompatibleJob : incompatibleJobs) {
            if (user.getJobs().containsKey(incompatibleJob)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou cannot join this job because you have an incompatible job: " + incompatibleJob);
                return;
            }
        }

        // Give permissions based on level
        int level = user.getLevel(jobName);
        List<String> perms = yamlUtils.getConfig()
                .getStringList("jobs." + jobName + ".on-join.give-permissions-per-level." + level);

        if (perms != null && !perms.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                User lpUser = plugin.getLuckPerms().getUserManager().getUser(user.getPlayer().getUniqueId());
                if (lpUser != null) {
                    for (String perm : perms) {
                        if (perm.startsWith("group.")) {
                            String group = perm.substring(6);
                            lpUser.data().add(InheritanceNode.builder(group).build());
                        } else {
                            lpUser.data().add(net.luckperms.api.node.types.PermissionNode.builder(perm).build());
                        }
                    }
                    plugin.getLuckPerms().getUserManager().saveUser(lpUser);
                }
            });
        }
    }

    @EventHandler
    public void onJobLeave(JobLeaveEvent event) {
        String jobName = event.getJob().getId();
        JobUser user = event.getUser();

        // Decrease level if configured
        int decrease = yamlUtils.getConfig()
                .getInt("jobs." + jobName + ".on-leave.decrease-level", 0);
        if (decrease > 0) {
            int currentLevel = user.getLevel(jobName);
            user.setLevel(jobName, Math.max(0, currentLevel - decrease));
        }

        // Remove permissions
        List<String> permsToRemove = yamlUtils.getConfig()
                .getStringList("jobs." + jobName + ".on-leave.remove-permissions");

        if (permsToRemove != null && !permsToRemove.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                User lpUser = plugin.getLuckPerms().getUserManager().getUser(user.getPlayer().getUniqueId());
                if (lpUser != null) {
                    for (String perm : permsToRemove) {
                        if (perm.startsWith("group.")) {
                            String group = perm.substring(6);
                            lpUser.data().remove(InheritanceNode.builder(group).build());
                        } else {
                            lpUser.data().remove(net.luckperms.api.node.types.PermissionNode.builder(perm).build());
                        }
                    }
                    plugin.getLuckPerms().getUserManager().saveUser(lpUser);
                }
            });
        }
    }
}
