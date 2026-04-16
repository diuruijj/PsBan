package p.psban.command.subcommand;

import org.bukkit.command.CommandSender;
import p.psban.PsBanPlugin;
import p.psban.command.SubCommand;
import p.psban.model.BanEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HistorySubCommand extends SubCommand {

    private static final int PAGE_SIZE = 8;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public HistorySubCommand(PsBanPlugin plugin) { super(plugin); }

    @Override
    public String getPermission() { return "psban.admin"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "history.usage"); return; }

        String region = args[0];
        int page = args.length >= 2 ? parsePage(args[1]) : 1;
        int total = bm.countHistory(region);

        if (total <= 0) { send(sender, "history.empty", "region", region); return; }

        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_SIZE));
        page = Math.min(Math.max(1, page), totalPages);
        int offset = (page - 1) * PAGE_SIZE;

        List<BanEntry> entries = bm.getHistory(region, PAGE_SIZE, offset);
        msg(sender, "history.header", "region", region,
                "page", String.valueOf(page), "total", String.valueOf(totalPages));

        for (BanEntry e : entries) {
            String expire = e.isPermanent() ? "permanente" : SDF.format(new Date(e.getExpireAt()));
            String status = (e.isActive() && !e.isExpired()) ? "activo" : "inactivo";
            msg(sender, "history.entry",
                    "player", e.getPlayerName(),
                    "reason", safeReason(e),
                    "banner", e.getBannedBy(),
                    "date", SDF.format(new Date(e.getBannedAt())),
                    "expire", expire,
                    "status", status);
        }
    }

    private int parsePage(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (NumberFormatException e) { return 1; }
    }
}

