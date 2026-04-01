package p.psban.manager;

import p.psban.PsBanPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final PsBanPlugin plugin;
    private final File file;

    public LogManager(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "psban.log");
    }

    public void log(String action, String actor, String target, String region, String details) {
        if (!plugin.getConfig().getBoolean("log-to-file", true)) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write("[" + LocalDateTime.now().format(FMT) + "] [" + action + "] actor=" + actor + " target=" + target + " region=" + region + " details=" + details + System.lineSeparator());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo escribir psban.log: " + ex.getMessage());
        }
    }
}
