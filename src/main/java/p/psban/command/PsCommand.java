package p.psban.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import p.psban.PsBanPlugin;
import p.psban.command.subcommand.*;
import p.psban.messages.LangUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PsCommand extends Command {

    private final Map<String, SubCommand> subs = new LinkedHashMap<>();

    public PsCommand(PsBanPlugin plugin) {
        super("ps", "Comando principal de PsBan", "/ps <subcomando>", List.of("psban"));
        subs.put("ban", new BanSubCommand(plugin));
        subs.put("unban", new UnbanSubCommand(plugin));
        subs.put("bypass", new BypassSubCommand(plugin));
        subs.put("banlist", new BanlistSubCommand(plugin));
        subs.put("history", new HistorySubCommand(plugin));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }

        SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) { sendHelp(sender); return true; }
        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            sender.sendMessage(LangUtil.prefixed("no-permission"));
            return true;
        }

        sub.execute(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>();
            for (String key : subs.keySet())
                if (key.startsWith(prefix)) out.add(key);
            return out;
        }
        SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
        return sub != null ? sub.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length)) : List.of();
    }

    private void sendHelp(CommandSender sender) {
        for (String key : List.of("header", "ban", "unban", "bypass", "banlist", "history"))
            sender.sendMessage(LangUtil.get("help." + key));
    }
}
