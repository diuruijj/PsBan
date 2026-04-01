package p.psban.command;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.manager.BanManager;
import p.psban.model.BanEntry;
import p.psban.util.DurationUtil;
import p.psban.util.Messages;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PsCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 8;
    private final PsBanPlugin plugin;
    private final BanManager bm;

    public PsCommand(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.bm = plugin.getBanManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "ban" -> handleBan(sender, args);
            case "unban" -> handleUnban(sender, args);
            case "bypass" -> handleBypass(sender, args);
            case "banlist" -> handleBanlist(sender, args);
            case "history" -> handleHistory(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleBan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("psban.ban")) {
            noPerms(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefix() + Messages.get("ban.usage"));
            return;
        }

        String requestedName = args[1];
        OfflinePlayer target = resolveOfflinePlayer(requestedName);
        if (target == null) {
            sender.sendMessage(Messages.prefix() + Messages.get("invalid-player-name", "player", requestedName));
            return;
        }

        ParsedBanArguments parsed = parseBanArgs(sender, Arrays.copyOfRange(args, 2, args.length));
        if (parsed.invalidDurationInput != null) {
            sender.sendMessage(Messages.prefix() + Messages.get("invalid-duration", "duration", parsed.invalidDurationInput));
            return;
        }

        String reason = parsed.reason;
        if ((reason == null || reason.isBlank()) && plugin.getConfig().getBoolean("require-reason", false)) {
            sender.sendMessage(Messages.prefix() + Messages.get("reason-required"));
            return;
        }
        if (reason == null || reason.isBlank()) {
            reason = plugin.getConfig().getString("default-reason", "No especificada");
        }

        RegionResolution regionResolution = resolveRegion(sender, parsed.regionInput, true);
        if (regionResolution == null || regionResolution.regionId == null || regionResolution.regionId.isBlank()) {
            sender.sendMessage(Messages.prefix() + Messages.get("ban.no-region"));
            return;
        }
        if (!canManageRegion(sender, regionResolution.regionObject)) {
            sender.sendMessage(Messages.prefix() + Messages.get("ban.not-owner"));
            return;
        }

        UUID uuid = target.getUniqueId();
        if (uuid == null) {
            sender.sendMessage(Messages.prefix() + Messages.get("player-not-found", "player", requestedName));
            return;
        }
        if (bm.isBanned(uuid, regionResolution.regionId)) {
            sender.sendMessage(Messages.prefix() + Messages.get("ban.already-banned", "player", requestedName, "region", regionResolution.regionId));
            return;
        }

        bm.ban(uuid, requestedName, regionResolution.regionId, sender.getName(), reason, parsed.durationMs);

        String durationText = parsed.durationMs <= 0 ? "permanente" : DurationUtil.format(parsed.durationMs);
        sender.sendMessage(Messages.prefix() + Messages.get("ban.success",
                "player", requestedName,
                "region", regionResolution.regionId,
                "duration", durationText,
                "reason", reason));

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            online.sendMessage(Messages.prefix() + Messages.get("ban.notified",
                    "region", regionResolution.regionId,
                    "protection", "esta protección",
                    "duration", durationText,
                    "reason", reason));
            String currentRegion = getRegionIdAtPlayer(online);
            if (currentRegion != null && currentRegion.equalsIgnoreCase(regionResolution.regionId)) {
                plugin.ejectPlayerToSpawn(online, regionResolution.regionId, "ban.ejected");
            }
        }

        notifyStaff(Messages.get("ban.staff-notify",
                "banner", sender.getName(),
                "player", requestedName,
                "region", regionResolution.regionId,
                "duration", durationText,
                "reason", reason));
        plugin.getLogManager().log("BAN", sender.getName(), requestedName, regionResolution.regionId,
                "duration=" + durationText + " reason=" + reason);
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("psban.ban")) {
            noPerms(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefix() + Messages.get("unban.usage"));
            return;
        }

        String requestedName = args[1];
        OfflinePlayer target = resolveOfflinePlayer(requestedName);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(Messages.prefix() + Messages.get("invalid-player-name", "player", requestedName));
            return;
        }

        String regionInput = args.length >= 3 ? args[2] : null;
        RegionResolution regionResolution = resolveRegion(sender, regionInput, true);
        if (regionResolution == null || regionResolution.regionId == null || regionResolution.regionId.isBlank()) {
            sender.sendMessage(Messages.prefix() + Messages.get("unban.no-region"));
            return;
        }
        if (!canManageRegion(sender, regionResolution.regionObject)) {
            sender.sendMessage(Messages.prefix() + Messages.get("unban.not-owner"));
            return;
        }

        if (!bm.unban(target.getUniqueId(), regionResolution.regionId)) {
            sender.sendMessage(Messages.prefix() + Messages.get("unban.not-banned", "player", requestedName, "region", regionResolution.regionId));
            return;
        }

        sender.sendMessage(Messages.prefix() + Messages.get("unban.success", "player", requestedName, "region", regionResolution.regionId));
        plugin.getLogManager().log("UNBAN", sender.getName(), requestedName, regionResolution.regionId, "manual=true");
    }

    private void handleBypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.prefix() + Messages.get("only-player"));
            return;
        }
        if (!player.hasPermission("psban.bypass")) {
            noPerms(player);
            return;
        }

        boolean enable;
        if (args.length >= 2) {
            enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        } else {
            enable = !bm.hasBypass(player.getUniqueId());
        }

        bm.setBypass(player.getUniqueId(), enable);
        player.sendMessage(Messages.prefix() + Messages.get(enable ? "bypass.on" : "bypass.off"));
    }

    private void handleBanlist(CommandSender sender, String[] args) {
        if (!sender.hasPermission("psban.ban")) {
            noPerms(sender);
            return;
        }

        String regionInput = args.length >= 2 ? args[1] : null;
        int page = args.length >= 3 ? parsePage(args[2]) : 1;
        RegionResolution regionResolution = resolveRegion(sender, regionInput, false);
        if (regionResolution == null || regionResolution.regionId == null || regionResolution.regionId.isBlank()) {
            sender.sendMessage(Messages.prefix() + Messages.get("banlist.no-region"));
            return;
        }

        List<BanEntry> entries = bm.getActiveBans(regionResolution.regionId);
        if (entries.isEmpty()) {
            sender.sendMessage(Messages.prefix() + Messages.get("banlist.empty", "region", regionResolution.regionId));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.min(Math.max(1, page), totalPages);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        sender.sendMessage(Messages.get("banlist.header", "region", regionResolution.regionId, "page", String.valueOf(page), "total", String.valueOf(totalPages)));
        for (BanEntry entry : entries.subList(from, to)) {
            String duration = entry.isPermanent() ? "permanente" : DurationUtil.format(entry.remainingMs());
            sender.sendMessage(Messages.get("banlist.entry",
                    "player", entry.getPlayerName(),
                    "duration", duration,
                    "reason", entry.getReason(),
                    "banner", entry.getBannedBy()));
        }
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("psban.admin")) {
            noPerms(sender);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.prefix() + Messages.get("history.usage"));
            return;
        }

        String region = args[1];
        int page = args.length >= 3 ? parsePage(args[2]) : 1;
        int total = bm.countHistory(region);
        if (total <= 0) {
            sender.sendMessage(Messages.prefix() + Messages.get("history.empty", "region", region));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        page = Math.min(Math.max(1, page), totalPages);
        int offset = (page - 1) * PAGE_SIZE;

        List<BanEntry> entries = bm.getHistory(region, PAGE_SIZE, offset);
        sender.sendMessage(Messages.get("history.header", "region", region, "page", String.valueOf(page), "total", String.valueOf(totalPages)));
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        for (BanEntry entry : entries) {
            String expire = entry.isPermanent() ? "permanente" : sdf.format(new Date(entry.getExpireAt()));
            sender.sendMessage(Messages.get("history.entry",
                    "player", entry.getPlayerName(),
                    "reason", entry.getReason(),
                    "banner", entry.getBannedBy(),
                    "date", sdf.format(new Date(entry.getBannedAt())),
                    "expire", expire,
                    "status", entry.isActive() && !entry.isExpired() ? "activo" : "inactivo"));
        }
    }

    private ParsedBanArguments parseBanArgs(CommandSender sender, String[] extra) {
        ParsedBanArguments out = new ParsedBanArguments();
        if (extra.length == 0) {
            return out;
        }

        List<String> reasonParts = new ArrayList<>();
        boolean explicitReasonMode = false;

        for (int i = 0; i < extra.length; i++) {
            String token = extra[i];
            if (token == null || token.isBlank()) {
                continue;
            }

            if (token.equalsIgnoreCase("-r") || token.equalsIgnoreCase("--reason")) {
                explicitReasonMode = true;
                continue;
            }

            if (token.equalsIgnoreCase("-t") || token.equalsIgnoreCase("--time")) {
                if (i + 1 >= extra.length) {
                    out.invalidDurationInput = "";
                    return out;
                }
                String raw = extra[++i];
                long parsed = DurationUtil.parse(raw);
                if (parsed == -2L) {
                    out.invalidDurationInput = raw;
                    return out;
                }
                out.durationMs = parsed;
                continue;
            }

            if (explicitReasonMode) {
                reasonParts.add(token);
                continue;
            }

            long parsedDuration = DurationUtil.parse(token);
            if (parsedDuration != -2L && !looksLikeRegionToken(sender, token)) {
                out.durationMs = parsedDuration;
                continue;
            }

            if (out.regionInput == null && looksLikeRegionToken(sender, token)) {
                out.regionInput = token;
                continue;
            }

            reasonParts.add(token);
        }

        out.reason = String.join(" ", reasonParts).trim();
        return out;
    }

    private boolean looksLikeRegionToken(CommandSender sender, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }
        List<PSRegion> matches = PSRegion.fromName(player.getWorld(), token);
        return matches != null && !matches.isEmpty();
    }

    private OfflinePlayer resolveOfflinePlayer(String name) {
        if (name == null) return null;
        String clean = name.trim();
        if (!clean.matches("^[A-Za-z0-9_]{3,16}$")) return null;
        Player online = Bukkit.getPlayerExact(clean);
        if (online != null) return online;
        return Bukkit.getOfflinePlayer(clean);
    }

    private RegionResolution resolveRegion(CommandSender sender, String regionInput, boolean requireRealRegionForPlayer) {
        RegionResolution out = new RegionResolution();
        if (regionInput == null || regionInput.isBlank()) {
            if (sender instanceof Player player) {
                PSRegion region = PSRegion.fromLocationGroup(player.getLocation());
                if (region == null) region = PSRegion.fromLocation(player.getLocation());
                if (region != null) {
                    out.regionObject = region;
                    out.regionId = region.getId();
                    return out;
                }
            }
            return null;
        }

        if (sender instanceof Player player) {
            World world = player.getWorld();
            List<PSRegion> matches = PSRegion.fromName(world, regionInput);
            if (matches != null && !matches.isEmpty()) {
                out.regionObject = matches.get(0);
                out.regionId = matches.get(0).getId();
                return out;
            }
            if (requireRealRegionForPlayer) {
                return null;
            }
        }

        out.regionId = regionInput;
        return out;
    }

    private boolean canManageRegion(CommandSender sender, PSRegion region) {
        if (sender.hasPermission("psban.admin")) return true;
        if (!(sender instanceof Player player)) return false;
        return region != null && region.isOwner(player.getUniqueId());
    }

    private String getRegionIdAtPlayer(Player player) {
        PSRegion region = PSRegion.fromLocation(player.getLocation());
        return region == null ? null : region.getId();
    }

    private void notifyStaff(String msg) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("psban.notify")) {
                online.sendMessage(Messages.prefix() + msg);
            }
        }
    }

    private int parsePage(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help.header"));
        sender.sendMessage(Messages.get("help.ban"));
        sender.sendMessage(Messages.get("help.unban"));
        sender.sendMessage(Messages.get("help.bypass"));
        sender.sendMessage(Messages.get("help.banlist"));
        sender.sendMessage(Messages.get("help.history"));
    }

    private void noPerms(CommandSender sender) {
        sender.sendMessage(Messages.prefix() + Messages.get("no-permission"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("ban", "unban", "bypass", "banlist", "history"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filterPrefix(names, args[1]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("bypass")) {
            return filterPrefix(List.of("on", "off"), args[args.length - 1]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("ban")) {
            return filterPrefix(List.of("-t", "-r", "30m", "1h", "1d", "perm"), args[args.length - 1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
        return out;
    }

    private static final class ParsedBanArguments {
        private String regionInput;
        private long durationMs = 0L;
        private String reason;
        private String invalidDurationInput;
    }

    private static final class RegionResolution {
        private String regionId;
        private PSRegion regionObject;
    }
}
