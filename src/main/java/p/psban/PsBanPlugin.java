package p.psban;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import p.psban.command.PsCommand;
import p.psban.listener.ProtectionListener;
import p.psban.manager.BanManager;
import p.psban.manager.LogManager;
import p.psban.util.Messages;

public class PsBanPlugin extends JavaPlugin {
    private BanManager banManager;
    private LogManager logManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        Messages.init(this);
        this.logManager = new LogManager(this);
        this.banManager = new BanManager(this);

        PsCommand cmd = new PsCommand(this);
        if (getCommand("ps") != null) {
            getCommand("ps").setExecutor(cmd);
            getCommand("ps").setTabCompleter(cmd);
        }

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> banManager.purgeExpired(), 1200L, 1200L);
        getLogger().info("PsBan activado correctamente.");
    }

    @Override
    public void onDisable() {
        if (banManager != null) {
            banManager.saveAll();
            banManager.close();
        }
        getLogger().info("PsBan desactivado.");
    }

    public BanManager getBanManager() {
        return banManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public void ejectPlayerToSpawn(Player player, String regionId, String reasonMessagePath) {
        getServer().getScheduler().runTask(this, () -> {
            boolean sent = false;
            try {
                if (getConfig().getBoolean("run-spawn-command-on-ban", true)) {
                    String command = getConfig().getString("spawn-command", "spawn").trim();
                    if (command.startsWith("/")) command = command.substring(1);
                    String mode = getConfig().getString("spawn-command-mode", "PLAYER").trim().toUpperCase();
                    if (mode.equals("CONSOLE")) {
                        CommandSender console = getServer().getConsoleSender();
                        sent = Bukkit.dispatchCommand(console, command.replace("{player}", player.getName()));
                    } else {
                        sent = Bukkit.dispatchCommand(player, command.replace("{player}", player.getName()));
                    }
                }
            } catch (Exception ex) {
                getLogger().warning("No se pudo ejecutar el comando de spawn: " + ex.getMessage());
            }
            if (!sent && getConfig().getBoolean("fallback-teleport-to-world-spawn", true)) {
                try {
                    Location spawn = player.getWorld().getSpawnLocation();
                    player.teleport(spawn);
                    sent = true;
                } catch (Exception ex) {
                    getLogger().warning("No se pudo teleportar al spawn: " + ex.getMessage());
                }
            }
            if (sent && reasonMessagePath != null && !reasonMessagePath.isBlank()) {
                String duration = "permanente";
                String reason = "No especificada";
                if (banManager != null) {
                    p.psban.model.BanEntry entry = banManager.getActiveBan(player.getUniqueId(), regionId);
                    if (entry != null) {
                        duration = entry.isPermanent() ? "permanente" : p.psban.util.DurationUtil.format(entry.remainingMs());
                        if (entry.getReason() != null && !entry.getReason().isBlank()) {
                            reason = entry.getReason();
                        }
                    }
                }
                player.sendMessage(Messages.prefix() + Messages.get(reasonMessagePath,
                        "region", regionId,
                        "protection", "esta protección",
                        "duration", duration,
                        "reason", reason));
            }
        });
    }
}
