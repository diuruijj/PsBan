package p.psban.command.subcommand;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.command.SubCommand;

import java.util.List;

public class BypassSubCommand extends SubCommand {

    public BypassSubCommand(PsBanPlugin plugin) { super(plugin); }

    @Override
    public String getPermission() { return "psban.bypass"; }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { send(sender, "only-player"); return; }

        boolean enable = args.length >= 1
                ? args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true")
                : !bm.hasBypass(player.getUniqueId());

        bm.setBypass(player.getUniqueId(), enable);
        send(player, enable ? "bypass.on" : "bypass.off");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("on", "off"), args[0]);
        return List.of();
    }
}

