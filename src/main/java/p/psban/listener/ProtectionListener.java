package p.psban.listener;

import dev.espi.protectionstones.PSRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import p.psban.PsBanPlugin;
import p.psban.manager.BanManager;
import p.psban.messages.LangUtil;
import p.psban.model.BanEntry;
import p.psban.util.DurationUtil;

public class ProtectionListener implements Listener {

    private final PsBanPlugin plugin;
    private final BanManager bm;

    public ProtectionListener(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.bm = plugin.getBanManager();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;

        Player player = event.getPlayer();
        if (bm.hasBypass(player.getUniqueId())) return;

        PSRegion region = PSRegion.fromLocation(to);
        if (region == null) return;
        BanEntry entry = bm.getActiveBan(player.getUniqueId(), region.getId());
        if (entry == null) return;

        event.setCancelled(true);
        pushBack(player, from, to, region.getId(), entry, true);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || bm.hasBypass(player.getUniqueId())) return;

        PSRegion region = PSRegion.fromLocation(to);
        if (region == null) return;
        BanEntry entry = bm.getActiveBan(player.getUniqueId(), region.getId());
        if (entry == null) return;

        event.setCancelled(true);
        pushBack(player, player.getLocation(), to, region.getId(), entry, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (bm.hasBypass(player.getUniqueId())) return;

        PSRegion region = PSRegion.fromLocation(player.getLocation());
        if (region == null) return;
        BanEntry entry = bm.getActiveBan(player.getUniqueId(), region.getId());
        if (entry == null) return;

        player.sendMessage(LangUtil.prefixed("banned.join",
                "protection", region.getId(),
                "duration", formatDuration(entry),
                "reason", safeReason(entry)));
        plugin.getBanManager().ejectPlayerToSpawn(player, region.getId(), null);
        plugin.notifyStaff("banned.staff-join", "player", player.getName(), "region", region.getId());
    }

    private void pushBack(Player player, Location from, Location to, String regionId, BanEntry entry, boolean move) {
        Vector dir = from.toVector().subtract(to.toVector());
        dir.setY(0).normalize().multiply(plugin.getConfig().getDouble("push-force", 1.2D));
        dir.setY(plugin.getConfig().getDouble("push-upward-force", 0.25D));
        player.setVelocity(dir);

        if (bm.checkMsgCooldown(player.getUniqueId())) {
            String path = move ? "banned.enter" : "banned.tp";
            String staffPath = move ? "banned.staff-enter" : "banned.staff-tp";
            player.sendMessage(LangUtil.prefixed(path,
                    "protection", regionId,
                    "duration", formatDuration(entry),
                    "reason", safeReason(entry)));
            plugin.notifyStaff(staffPath, "player", player.getName(), "region", regionId);
        }
    }

    private String formatDuration(BanEntry e) {
        return e.isPermanent() ? "permanente" : DurationUtil.format(e.remainingMs());
    }

    private String safeReason(BanEntry e) {
        String r = e.getReason();
        return (r == null || r.isBlank()) ? "No especificada" : r;
    }
}
