package p.psban.command.subcommand;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.command.SubCommand;
import p.psban.messages.LangUtil;
import p.psban.util.DurationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BanSubCommand extends SubCommand {

    public BanSubCommand(PsBanPlugin plugin) { super(plugin); }

    @Override
    public String getPermission() { return "psban.ban"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "ban.usage"); return; }

        String name = args[0];
        OfflinePlayer target = resolvePlayer(name);
        if (target == null) { send(sender, "invalid-player-name", "player", name); return; }

        Parsed p = parseArgs(sender, args);
        if (p.badDuration != null) { send(sender, "invalid-duration", "duration", p.badDuration); return; }

        String reason = p.reason;
        if (reason.isBlank() && plugin.getConfig().getBoolean("require-reason", false)) {
            send(sender, "reason-required"); return;
        }
        if (reason.isBlank()) reason = plugin.getConfig().getString("default-reason", "No especificada");

        PSRegion region = resolveRegion(sender, p.regionInput);
        String regionId;
        if (region != null) {
            regionId = region.getId();
        } else if (sender.hasPermission("psban.admin") && p.regionInput != null) {
            regionId = p.regionInput;
        } else {
            send(sender, "ban.no-region"); return;
        }
        if (!canManage(sender, region)) { send(sender, "ban.not-owner"); return; }

        UUID uuid = target.getUniqueId();
        if (bm.isBanned(uuid, regionId)) {
            send(sender, "ban.already-banned", "player", name, "region", regionId); return;
        }

        bm.ban(uuid, name, regionId, sender.getName(), reason, p.durationMs);
        String dur = p.durationMs <= 0 ? "permanente" : DurationUtil.format(p.durationMs);

        send(sender, "ban.success", "player", name, "region", regionId, "duration", dur, "reason", reason);

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(LangUtil.prefixed("ban.notified",
                    "protection", regionId, "duration", dur, "reason", reason));
            PSRegion at = PSRegion.fromLocation(online.getLocation());
            if (at != null && at.getId().equalsIgnoreCase(regionId))
                plugin.getBanManager().ejectPlayerToSpawn(online, regionId, "ban.ejected");
        }

        plugin.notifyStaff("ban.staff-notify",
                "banner", sender.getName(), "player", name,
                "region", regionId, "duration", dur, "reason", reason);
        plugin.getLogManager().log("BAN", sender.getName(), name, regionId,
                "duration=" + dur + " reason=" + reason);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return filter(names, args[0]);
        }
        return filter(List.of("-t", "-r", "30m", "1h", "1d", "perm"), args[args.length - 1]);
    }

    /* ── Parsing inteligente ─────────────────────────────────── */

    private Parsed parseArgs(CommandSender sender, String[] args) {
        Parsed out = new Parsed();
        List<String> reasonParts = new ArrayList<>();
        boolean reasonMode = false;

        for (int i = 1; i < args.length; i++) {
            String t = args[i];
            if (t.isBlank()) continue;

            if (t.equalsIgnoreCase("-r") || t.equalsIgnoreCase("--reason")) {
                reasonMode = true; continue;
            }
            if (t.equalsIgnoreCase("-t") || t.equalsIgnoreCase("--time")) {
                if (++i >= args.length) { out.badDuration = ""; return out; }
                long d = DurationUtil.parse(args[i]);
                if (d == -2L) { out.badDuration = args[i]; return out; }
                out.durationMs = d; continue;
            }
            if (reasonMode) { reasonParts.add(t); continue; }

            long d = DurationUtil.parse(t);
            if (d != -2L && !looksLikeRegion(sender, t)) { out.durationMs = d; continue; }
            if (out.regionInput == null && looksLikeRegion(sender, t)) { out.regionInput = t; continue; }
            reasonParts.add(t);
        }
        out.reason = String.join(" ", reasonParts).trim();
        return out;
    }

    private boolean looksLikeRegion(CommandSender sender, String token) {
        if (!(sender instanceof Player pl)) return false;
        List<PSRegion> m = PSRegion.fromName(pl.getWorld(), token);
        return m != null && !m.isEmpty();
    }

    private static class Parsed {
        String regionInput, reason = "", badDuration;
        long durationMs = 0;
    }
}

