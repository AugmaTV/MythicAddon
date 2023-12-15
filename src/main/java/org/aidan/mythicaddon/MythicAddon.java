package org.aidan.mythicaddon;

import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.aidan.mythicaddon.mechanics.TimeStop.TimeStopAbility;
import org.aidan.mythicaddon.mechanics.TimeStop.TimeStopSkill;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class MythicAddon extends JavaPlugin implements Listener {
    private Logger log;
    private TimeStopAbility timeStopAbility; // Reference to TimeStopAbility

    // Called when the plugin is enabled
    @Override
    public void onEnable() {
        saveDefaultConfig(); // Saves the default configuration
        updateConfig();
        log = this.getLogger(); // Retrieves the plugin's logger
        log.info("MythicAddon Plugin Enabled!"); // Logs plugin enabled message
        String version = getServerVersion(); // Retrieves the server version
        log.info("Server is running Minecraft version: " + version); // Logs server version

        // Registers the plugin as an event listener
        Bukkit.getPluginManager().registerEvents(this, this);
        timeStopAbility = TimeStopAbility.getInstance(this);
        log.info("[MythicAbilities] Event Listener registered."); // Logs event listener registration
        this.getCommand("mythicaddon").setExecutor(new MythicAddonCommandExecutor(this));
    }

    // Called when the plugin is disabled
    @Override
    public void onDisable() {
        timeStopAbility.clearFrozenProjectiles();
        log.info("MythicAddon Plugin Disabled!"); // Logs plugin disabled message
    }

    // Event handler for MythicMechanicLoadEvent
    @EventHandler
    public void onMythicMechanicLoad(MythicMechanicLoadEvent event) {
        log.info("MythicMechanicLoadEvent called for mechanic " + event.getMechanicName()); // Logs event call

        // Checks if the mechanic name is "timestop"
        if (event.getMechanicName().equalsIgnoreCase("timestop")) {
            event.register(new TimeStopSkill(this, event.getConfig())); // Registers TimeStopSkill
            log.info("-- Registered TimeStop mechanic!"); // Logs mechanic registration
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (timeStopAbility.isTimeStopped) {
            if (timeStopAbility.spawnProjectile) {
                freezeNewProjectile(event.getEntity());
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (timeStopAbility.isTimeStopped) {
            if (timeStopAbility.spawnProjectile) {
                Entity entity = event.getProjectile();
                if (entity instanceof Projectile) {
                    freezeNewProjectile((Projectile) entity);
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void freezeNewProjectile(Projectile projectile) {
        UUID projectileId = projectile.getUniqueId();
        if (!timeStopAbility.frozenProjectiles.containsKey(projectileId)) {
            timeStopAbility.frozenProjectiles.put(projectileId, projectile.getLocation());
            timeStopAbility.storeProjectileVelocity(projectile);
        }
    }


    // Retrieves the server version
    private String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    private void updateConfig() {
        // Define the current config version for your plugin
        final double currentConfigVersion = 1.2;

        // Check if the config version is outdated
        if (this.getConfig().getDouble("config-version", 0.0) < currentConfigVersion) {
            // Update the config
            updateConfiguration();
            // Set the new version
            this.getConfig().set("config-version", currentConfigVersion);
            // Save the config
            this.saveConfig();
        }
    }
    private void updateConfiguration() {
        FileConfiguration config = this.getConfig();
        double currentConfigVersion = 1.2; // Update this to the latest version

        Set<String> keys = config.getKeys(false);
        for (String key : keys) {
            if (!key.equals("config-version")) {
                config.set(key, null);
            }
        }

        config.set("config-version", currentConfigVersion);

        this.saveConfig();
    }
}
