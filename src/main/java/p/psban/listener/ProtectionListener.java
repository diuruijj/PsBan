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
import p.psban.model.BanEntry;
import p.psban.util.Messages;

public class ProtectionListener implements Listener {
    private final PsBanPlugin plugin;
    private final BanManager bm;

    public ProtectionListener(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.bm = plugin.getBanManager();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

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

        player.sendMessage(Messages.prefix() + Messages.get("banned.join",
                "region", region.getId(),
                "protection", "esta protección",
                "duration", formatDuration(entry),
                "reason", safeReason(entry)));
        plugin.ejectPlayerToSpawn(player, region.getId(), null);
        notifyStaff(Messages.get("banned.staff-join", "player", player.getName(), "region", region.getId()));
    }

    private void pushBack(Player player, Location from, Location to, String regionId, BanEntry entry, boolean moveAttempt) {
        Location safeFrom = from != null ? from : player.getLocation();
        Vector direction = safeFrom.toVector().subtract(to.toVector());
        direction.setY(0).normalize().multiply(plugin.getConfig().getDouble("push-force", 1.35D));
        direction.setY(plugin.getConfig().getDouble("push-upward-force", 0.25D));
        player.setVelocity(direction);

        if (bm.checkMsgCooldown(player.getUniqueId())) {
            String path = moveAttempt ? "banned.enter" : "banned.tp";
            String staffPath = moveAttempt ? "banned.staff-enter" : "banned.staff-tp";
            player.sendMessage(Messages.prefix() + Messages.get(path,
                    "region", regionId,
                    "protection", "esta protección",
                    "duration", formatDuration(entry),
                    "reason", safeReason(entry)));
            notifyStaff(Messages.get(staffPath, "player", player.getName(), "region", regionId));
        }
    }

    private String formatDuration(BanEntry entry) {
        return entry.isPermanent() ? "permanente" : p.psban.util.DurationUtil.format(entry.remainingMs());
    }

    private String safeReason(BanEntry entry) {
        String reason = entry.getReason();
        return reason == null || reason.isBlank() ? "No especificada" : reason;
    }

    private void notifyStaff(String msg) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("psban.notify")) {
                online.sendMessage(Messages.prefix() + msg);
            }
        }
    }
}
