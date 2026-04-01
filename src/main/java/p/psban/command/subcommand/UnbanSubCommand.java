package p.psban.command.subcommand;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.command.SubCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UnbanSubCommand extends SubCommand {

    public UnbanSubCommand(PsBanPlugin plugin) { super(plugin); }

    @Override
    public String getPermission() { return "psban.ban"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "unban.usage"); return; }

        String name = args[0];
        OfflinePlayer target = resolvePlayer(name);
        if (target == null) { send(sender, "invalid-player-name", "player", name); return; }

        String regionInput = args.length >= 2 ? args[1] : null;
        PSRegion region = resolveRegion(sender, regionInput);
        String regionId;
        if (region != null) {
            regionId = region.getId();
        } else if (sender.hasPermission("psban.admin") && regionInput != null) {
            regionId = regionInput;
        } else {
            send(sender, "unban.no-region"); return;
        }
        if (!canManage(sender, region)) { send(sender, "unban.not-owner"); return; }

        UUID uuid = target.getUniqueId();
        if (!bm.unban(uuid, regionId)) {
            send(sender, "unban.not-banned", "player", name, "region", regionId); return;
        }

        send(sender, "unban.success", "player", name, "region", regionId);
        plugin.getLogManager().log("UNBAN", sender.getName(), name, regionId, "manual=true");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[0]);
        }
        return List.of();
    }
}

