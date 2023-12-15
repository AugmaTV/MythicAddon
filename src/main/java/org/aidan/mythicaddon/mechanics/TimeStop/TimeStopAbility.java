package org.aidan.mythicaddon.mechanics.TimeStop;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimeStopAbility {
    private static TimeStopAbility instance;
    private final JavaPlugin plugin;
    private Map<UUID, Location> frozenProjectiles = new HashMap<>(); // Stores frozen projectiles
    private Map<UUID, Vector> projectileVelocities = new HashMap<>(); // Stores velocities of projectiles
    private BukkitTask freezeTask; // Task for freezing projectiles
    public boolean isTimeStopped = false; // Flag to indicate if time is stopped

    public TimeStopAbility(JavaPlugin plugin) {
        this.plugin = plugin;
        setupPacketInterception(); // Sets up packet interception for handling projectile teleports
    }

    public static synchronized TimeStopAbility getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new TimeStopAbility(plugin);
        }
        return instance;
    }

    // Starts the task for freezing projectiles
    public void startFreezingProjectiles() {
        isTimeStopped = true; // Sets the flag to true
        freezeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::freezeProjectiles, 0L, 1L); // Schedules the freezing task
    }

    // Stores the velocities of projectiles when time is stopped
    private void storeProjectileVelocity(Projectile projectile) {
        UUID projectileId = projectile.getUniqueId();
        if (!projectileVelocities.containsKey(projectileId)) {
            projectileVelocities.put(projectileId, projectile.getVelocity());
        }
    }

    // Freezes all projectiles in the world
    private void freezeProjectiles() {
        Bukkit.getWorlds().forEach(world -> {
            world.getEntitiesByClass(Projectile.class).forEach(projectile -> {
                UUID projectileId = projectile.getUniqueId();
                if (!frozenProjectiles.containsKey(projectileId)) {
                    frozenProjectiles.put(projectileId, projectile.getLocation());
                    storeProjectileVelocity(projectile); // Store velocity when first freezing
                }
                Location storedLocation = frozenProjectiles.get(projectileId);
                if (storedLocation != null) {
                    projectile.teleport(storedLocation);
                    projectile.setGravity(false);
                }
            });
        });
    }

    // Clears stored projectile velocities when time resumes
    public void clearProjectileVelocities() {
        projectileVelocities.clear();
    }

    // Restores velocities of projectiles when time resumes
    public void restoreProjectileVelocities() {
        boolean restoreVelocities = plugin.getConfig().getBoolean("TimeStop.RestoreVelocities", true); // Check the config option

        for (UUID projectileUuid : frozenProjectiles.keySet()) {
            Entity entity = Bukkit.getEntity(projectileUuid);
            if (entity instanceof Projectile) {
                Projectile projectile = (Projectile) entity;
                UUID projectileId = projectile.getUniqueId();
                if (restoreVelocities) {
                    Vector originalVelocity = projectileVelocities.get(projectileId);
                    if (originalVelocity != null) {
                        projectile.setVelocity(originalVelocity);
                    }
                } else {
                    // Set the projectile velocity to (0, 0, 0) if RestoreVelocities is false
                    projectile.setVelocity(new Vector(0, 0, 0));
                }
            }
        }
    }


    // Resumes all frozen projectiles and restores their velocities
    public void resumeProjectiles() {
        isTimeStopped = false; // Sets the flag to false
        if (freezeTask != null) {
            freezeTask.cancel(); // Cancels the freezing task
        }

        // Restore velocities of frozen projectiles
        restoreProjectileVelocities();

        // Set gravity for projectiles
        for (UUID projectileUuid : frozenProjectiles.keySet()) {
            Entity entity = Bukkit.getEntity(projectileUuid);
            if (entity instanceof Projectile) {
                Projectile projectile = (Projectile) entity;
                projectile.setGravity(true); // Enables gravity for the projectile
            }
        }

        frozenProjectiles.clear(); // Clears the list of frozen projectiles
        clearProjectileVelocities();
    }

    // Sets up packet interception to handle teleporting of entities (for freezing projectiles)
    public void setupPacketInterception() {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_TELEPORT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
                    int entityId = event.getPacket().getIntegers().read(0);
                    Entity entity = event.getPlayer().getWorld().getEntities().stream()
                            .filter(e -> e.getEntityId() == entityId)
                            .findFirst()
                            .orElse(null);
                    if (entity instanceof Projectile) {
                        UUID entityUuid = entity.getUniqueId();
                        if (frozenProjectiles.containsKey(entityUuid)) {
                            event.setCancelled(true); // Cancels the teleport packet if the projectile is frozen
                        }
                    }
                }
            }
        });
    }
}