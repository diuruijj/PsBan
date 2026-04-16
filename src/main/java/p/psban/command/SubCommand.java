package p.psban.command;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.manager.BanManager;
import p.psban.messages.LangUtil;
import p.psban.model.BanEntry;
import p.psban.util.DurationUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public abstract class SubCommand {

    protected final PsBanPlugin plugin;
    protected final BanManager bm;

    protected SubCommand(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.bm = plugin.getBanManager();
    }

    public abstract void execute(CommandSender sender, String[] args);

    public abstract String getPermission();

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    protected void send(CommandSender s, String path, String... r) {
        s.sendMessage(LangUtil.prefixed(path, r));
    }

    protected void msg(CommandSender s, String path, String... r) {
        s.sendMessage(LangUtil.get(path, r));
    }

    protected OfflinePlayer resolvePlayer(String name) {
        if (name == null || !name.matches("^[A-Za-z0-9_]{3,16}$")) return null;
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayer(name);
    }

    protected PSRegion resolveRegion(CommandSender sender, String input) {
        if (input != null && !input.isBlank() && sender instanceof Player p) {
            List<PSRegion> m = PSRegion.fromName(p.getWorld(), input);
            if (m != null && !m.isEmpty()) return m.get(0);
            return null;
        }
        if (sender instanceof Player p) {
            PSRegion r = PSRegion.fromLocationGroup(p.getLocation());
            return r != null ? r : PSRegion.fromLocation(p.getLocation());
        }
        return null;
    }

    protected String resolveRegionId(CommandSender sender, String input) {
        PSRegion r = resolveRegion(sender, input);
        if (r != null) return r.getId();
        return (input != null && !input.isBlank()) ? input : null;
    }

    protected boolean canManage(CommandSender sender, PSRegion region) {
        if (sender.hasPermission("psban.admin")) return true;
        return sender instanceof Player p && region != null && region.isOwner(p.getUniqueId());
    }

    protected String formatDuration(BanEntry entry) {
        return entry.isPermanent() ? "permanente" : DurationUtil.format(entry.remainingMs());
    }

    protected String safeReason(BanEntry entry) {
        String r = entry.getReason();
        return (r == null || r.isBlank()) ? "No especificada" : r;
    }

    protected List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String v : values) if (v.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(v);
        return out;
    }
}

