package p.psban.messages;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import p.psban.PsBanPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class LangUtil {

    private static PsBanPlugin plugin;
    private static YamlConfiguration config;

    private LangUtil() {}

    public static void init(PsBanPlugin instance) {
        plugin = instance;
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) plugin.saveResource("lang.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource("lang.yml");
        if (def != null) config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def)));
    }

    public static void reload() {
        if (plugin != null) init(plugin);
    }

    public static String raw(String path) {
        return config != null ? config.getString(path, "!" + path + "!") : "!" + path + "!";
    }

    public static Component get(String path, String... replacements) {
        return ColorTranslator.translate(replace(raw(path), replacements));
    }

    public static Component prefixed(String path, String... replacements) {
        return ColorTranslator.translate(raw("prefix") + replace(raw(path), replacements));
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("%%" + replacements[i] + "%%", replacements[i + 1]);
        }
        return text;
    }
}
