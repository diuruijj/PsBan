package p.psban;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import p.psban.command.PsCommand;
import p.psban.listener.ProtectionListener;
import p.psban.manager.BanManager;
import p.psban.manager.LogManager;
import p.psban.messages.LangUtil;

public class PsBanPlugin extends JavaPlugin {

    private BanManager banManager;
    private LogManager logManager;

    @Override
    public void onEnable() {
        initConfig();
        initManagers();
        initCommands();
        initListeners();
        initTasks();
        getLogger().info("PsBan activado correctamente.");
    }

    @Override
    public void onDisable() {
        if (banManager != null) banManager.close();
        getLogger().info("PsBan desactivado.");
    }

    private void initConfig() {
        saveDefaultConfig();
        saveResource("lang.yml", false);
        LangUtil.init(this);
    }

    private void initManagers() {
        this.logManager = new LogManager(this);
        this.banManager = new BanManager(this);
    }

    private void initCommands() {
        Bukkit.getCommandMap().register("psban", new PsCommand(this));
    }

    private void initListeners() {
        new ProtectionListener(this);
    }

    private void initTasks() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> banManager.purgeExpired(), 1200L, 1200L);
    }

    public BanManager getBanManager() { return banManager; }

    public LogManager getLogManager() { return logManager; }

    public void notifyStaff(String path, String... replacements) {
        Component msg = LangUtil.prefixed(path, replacements);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("psban.notify")) p.sendMessage(msg);
        }
    }
}
