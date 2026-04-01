package p.psban.util;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import p.psban.PsBanPlugin;

import java.io.File;

public final class Messages {
    private static YamlConfiguration cfg;

    private Messages() {}

    public static void init(PsBanPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public static String get(String path) {
        String raw = cfg == null ? path : cfg.getString(path, "!" + path + "!");
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "!" + path + "!" : raw);
    }

    public static String get(String path, String... replacements) {
        String text = get(path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return text;
    }

    public static String prefix() {
        return get("prefix");
    }
}
