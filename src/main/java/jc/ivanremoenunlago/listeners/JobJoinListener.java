package jc.ivanremoenunlago.listeners;

import jc.ivanremoenunlago.JobController;
import jc.ivanremoenunlago.utils.YAMLUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import su.nightexpress.excellentjobs.JobsAPI;
import su.nightexpress.excellentjobs.data.impl.JobData;
import su.nightexpress.excellentjobs.user.JobUser;
import su.nightexpress.excellentjobs.api.event.JobJoinEvent;

import java.util.*;
import java.util.stream.Collectors;

import static org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS;

/**

 * Listener for handling when a player joins a job.
 * Grants the nearest-level permissions and/or groups based on the player's current job level.
 * Cancels the join if the player already has an incompatible job active.
 *
 * Listener para manejar cuando un jugador se une a un trabajo.
 * Asigna los permisos y/o grupos más cercanos según el nivel actual del jugador.
 * Cancela la unión si el jugador ya tiene un trabajo incompatible activo.
 */
public class JobJoinListener implements Listener {

    private final JobController plugin;
    private final YAMLUtils yaml;

    public JobJoinListener(JobController plugin) {
        this.plugin = plugin;
        this.yaml = plugin.getYamlUtils();
    }

    @EventHandler
    public void onJobJoin(JobJoinEvent event) {
        Player player = event.getPlayer();
        String jobId = event.getJob().getId();

  
        // Check incompatible jobs / Comprobar trabajos incompatibles
        if (!checkIncompatibleJobs(player, jobId, event)) {
            return;
        }

        // Assign nearest-level permissions and groups using LuckPerms
        // Asignar permisos y grupos según el nivel más cercano usando LuckPerms
        assignNearestLevelPermissions(player, jobId);
  

    }

    /**

     * Check if the player has any incompatible jobs active.
     * Cancels the join if an incompatible job is active.
     *
     * Comprobar si el jugador tiene trabajos incompatibles activos.
     * Cancela la unión si hay algún trabajo incompatible activo.
     */
    private boolean checkIncompatibleJobs(Player player, String jobId, JobJoinEvent event) {
        JobUser user = JobsAPI.getUserData(player);

        // Get all active jobs of the player / Obtener todos los trabajos activos del jugador
        Map<String, JobData> activeJobs = user.getDataMap().entrySet().stream()
                .filter(entry -> entry.getValue().isActive())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Load incompatible jobs list from YAML / Lista de trabajos incompatibles desde YAML
        List<String> incompatibleJobs = yaml.getConfig().getStringList("jobs." + jobId + ".incompatible-jobs");

        // Message from YAML / Mensaje desde YAML
        String msg = yaml.getConfig().getString("messages.incompatible-job",
                "§cYou cannot join this job because you already have an incompatible job active: ");

        // Optional sound from YAML / Sonido opcional desde YAML
        String soundName = yaml.getConfig().getString("messages.incompatible-job-sound", null);

        for (String incompatible : incompatibleJobs) {
            if (activeJobs.containsKey(incompatible)) {
                // Send message / Enviar mensaje
                player.sendMessage(msg + Objects.requireNonNull(JobsAPI.getJobById(incompatible)).getName());

    
                // Play sound only if defined / Reproducir sonido solo si está definido
                if (soundName != null && !soundName.isEmpty()) {
                    try {
                        NamespacedKey soundKey = NamespacedKey.minecraft(soundName.toLowerCase());
                        Sound sound = Registry.SOUNDS.get(soundKey);
                        assert sound != null;
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("The sound '" + soundName + "' is not valid.");
                    }
                }

                // Cancel the join / Cancelar la unión
                event.setCancelled(true);
                return false;
            }
    

        }

        return true;
    }



    /**

     * Assigns permissions and/or groups to the player based on their job level.
     * Uses LuckPerms to manage permissions and groups.
     *
     * Asigna permisos y/o grupos al jugador según su nivel en el trabajo.
     * Usa LuckPerms para gestionar permisos y grupos.
     */
    private void assignNearestLevelPermissions(Player player, String jobId) {
        // Get player's JobUser and JobData / Obtener JobUser y JobData del jugador
        JobUser user = JobsAPI.getUserData(player);
        JobData jobData = user.getData(Objects.requireNonNull(JobsAPI.getJobById(jobId)));

        int playerLevel = jobData.getLevel();

        // Load defined levels from YAML / Cargar niveles definidos en YAML
        if (yaml.getConfig().getConfigurationSection("jobs." + jobId + ".on-join.give-permissions-per-level") == null) {
            return; // No levels defined / No hay niveles definidos
        }

        Map<Integer, List<String>> levelPermissions = yaml.getConfig()
                .getConfigurationSection("jobs." + jobId + ".on-join.give-permissions-per-level")
                .getKeys(false)
                .stream()
                .map(Integer::parseInt)
                .collect(Collectors.toMap(
                        lvl -> lvl,
                        lvl -> yaml.getConfig().getStringList("jobs." + jobId + ".on-join.give-permissions-per-level." + lvl)
                ));

        if (levelPermissions.isEmpty()) {
            return; // Empty list / Lista vacía
        }

        // Find the nearest level <= player's level / Buscar el nivel más cercano menor o igual al nivel del jugador
        Optional<Integer> nearestLevel = levelPermissions.keySet().stream()
                .filter(lvl -> lvl <= playerLevel)
                .max(Integer::compareTo);

        if (nearestLevel.isEmpty()) {
            return; // No suitable level found / Ningún nivel definido es menor o igual al del jugador
        }

        List<String> permissionsToGive = levelPermissions.get(nearestLevel.get());

        // Load LuckPerms / Cargar LuckPerms
        LuckPerms lp = plugin.getServer().getServicesManager().load(LuckPerms.class);
        if (lp == null) {
            plugin.getLogger().severe("LuckPerms not loaded, cannot assign permissions to " + player.getName());
            return;
        }

        User lpUser = lp.getUserManager().getUser(player.getUniqueId());
        if (lpUser == null) {
            plugin.getLogger().warning("LuckPerms user not found for " + player.getName());
            return;
        }

        // Assign permissions and groups / Asignar permisos y grupos
        for (String entry : permissionsToGive) {
            if (entry.startsWith("group.")) {
                // Handle group assignment / Manejar asignación de grupo
                String groupName = entry.substring(6);
                boolean alreadyInGroup = lpUser.getInheritedGroups(lpUser.getQueryOptions()).stream()
                        .anyMatch(g -> g.getName().equalsIgnoreCase(groupName));
                if (!alreadyInGroup) {
                    lpUser.data().add(InheritanceNode.builder(groupName).build());
                    plugin.getLogger().info("Group '" + groupName + "' added to " + player.getName());
                }
            } else {
                // Handle permission assignment / Manejar asignación de permiso
                boolean alreadyHas = lpUser.getNodes().stream()
                        .filter(n -> n instanceof PermissionNode)
                        .map(n -> ((PermissionNode) n).getPermission())
                        .anyMatch(p -> p.equalsIgnoreCase(entry));
                if (!alreadyHas) {
                    lpUser.data().add(PermissionNode.builder(entry).build());
                    plugin.getLogger().info("Permission '" + entry + "' added to " + player.getName());
                }
            }
        }

        // Save changes in LuckPerms / Guardar cambios en LuckPerms
        lp.getUserManager().saveUser(lpUser);
        plugin.getLogger().info("Permissions and groups assigned to " + player.getName() + " for level " + nearestLevel.get());
    }
}
