package p.psban.command.subcommand;

import org.bukkit.command.CommandSender;
import p.psban.PsBanPlugin;
import p.psban.command.SubCommand;
import p.psban.model.BanEntry;

import java.util.List;

public class BanlistSubCommand extends SubCommand {

    private static final int PAGE_SIZE = 8;

    public BanlistSubCommand(PsBanPlugin plugin) { super(plugin); }

    @Override
    public String getPermission() { return "psban.ban"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String regionInput = args.length >= 1 ? args[0] : null;
        int page = args.length >= 2 ? parsePage(args[1]) : 1;

        String regionId = resolveRegionId(sender, regionInput);
        if (regionId == null) { send(sender, "banlist.no-region"); return; }

        List<BanEntry> entries = bm.getActiveBans(regionId);
        if (entries.isEmpty()) { send(sender, "banlist.empty", "region", regionId); return; }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) PAGE_SIZE));
        page = Math.min(Math.max(1, page), totalPages);
        int from = (page - 1) * PAGE_SIZE;
        int to = Math.min(entries.size(), from + PAGE_SIZE);

        msg(sender, "banlist.header", "region", regionId,
                "page", String.valueOf(page), "total", String.valueOf(totalPages));
        for (BanEntry e : entries.subList(from, to)) {
            msg(sender, "banlist.entry",
                    "player", e.getPlayerName(),
                    "duration", formatDuration(e),
                    "reason", safeReason(e),
                    "banner", e.getBannedBy());
        }
    }

    private int parsePage(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (NumberFormatException e) { return 1; }
    }
}

