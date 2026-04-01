package p.psban.manager;

import org.bukkit.entity.Player;
import p.psban.PsBanPlugin;
import p.psban.storage.Database;
import p.psban.storage.SQLiteDatabase;
import p.psban.messages.LangUtil;
import p.psban.model.BanEntry;
import p.psban.util.DurationUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BanManager {

    private final PsBanPlugin plugin;
    private final Database db;
    private final Map<String, Boolean> banCache = new ConcurrentHashMap<>();
    private final Set<UUID> bypassSet = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, Long> msgCooldown = new ConcurrentHashMap<>();

    public BanManager(PsBanPlugin plugin) {
        this.plugin = plugin;
        this.db = new SQLiteDatabase(plugin);
        try {
            db.init();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo iniciar la base de datos de PsBan", ex);
        }
    }

    private String key(UUID uuid, String region) {
        return uuid + "::" + region.toLowerCase();
    }

    public void ban(UUID uuid, String name, String region, String bannedBy, String reason, long durationMs) {
        try {
            db.addBan(new BanEntry(uuid, name, region, bannedBy, reason, durationMs));
            banCache.put(key(uuid, region), true);
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo guardar el baneo: " + ex.getMessage());
            }
        }
    }

    public boolean unban(UUID uuid, String region) {
        try {
            boolean ok = db.removeBan(uuid, region);
            if (ok) banCache.remove(key(uuid, region));
            return ok;
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo desbanear: " + ex.getMessage());
            }
            return false;
        }
    }

    public boolean isBanned(UUID uuid, String region) {
        String k = key(uuid, region);
        Boolean cached = banCache.get(k);
        if (cached != null && !cached) return false;
        try {
            boolean banned = db.isBanned(uuid, region);
            banCache.put(k, banned);
            return banned;
        } catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo comprobar el baneo: " + ex.getMessage());
            }
            return false;
        }
    }

    public BanEntry getActiveBan(UUID uuid, String region) {
        try { return db.getActiveBan(uuid, region); }
        catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo leer el baneo activo: " + ex.getMessage());
            }
            return null;
        }
    }

    public List<BanEntry> getActiveBans(String region) {
        try { return db.getActiveBans(region); }
        catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudieron listar los baneos: " + ex.getMessage());
            }
            return List.of();
        }
    }

    public List<BanEntry> getHistory(String region, int limit, int offset) {
        try { return db.getHistory(region, limit, offset); }
        catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo leer el historial: " + ex.getMessage());
            }
            return List.of();
        }
    }

    public int countHistory(String region) {
        try { return db.countHistory(region); }
        catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudo contar el historial: " + ex.getMessage());
            }
            return 0;
        }
    }

    public void purgeExpired() {
        try { db.purgeExpired(); banCache.clear(); }
        catch (Exception ex) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("No se pudieron limpiar los baneos caducados: " + ex.getMessage());
            }
        }
    }

    public void setBypass(UUID uuid, boolean enabled) {
        if (enabled) bypassSet.add(uuid); else bypassSet.remove(uuid);
    }

    public boolean hasBypass(UUID uuid) { return bypassSet.contains(uuid); }

    public boolean checkMsgCooldown(UUID uuid) {
        long cooldown = plugin.getConfig().getLong("message-cooldown-ms", 3000L);
        long now = System.currentTimeMillis();
        if (now - msgCooldown.getOrDefault(uuid, 0L) >= cooldown) {
            msgCooldown.put(uuid, now);
            return true;
        }
        return false;
    }

    public void ejectPlayerToSpawn(Player player, String regionId, String messagePath) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                player.teleport(player.getWorld().getSpawnLocation());
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo teleportar al spawn de " + player.getName() + ": " + ex.getMessage());
                return;
            }

            if (messagePath == null || messagePath.isBlank()) return;

            BanEntry entry = getActiveBan(player.getUniqueId(), regionId);
            String duration = "permanente";
            String reason = "No especificada";

            if (entry != null) {
                if (!entry.isPermanent()) duration = DurationUtil.format(entry.remainingMs());
                if (entry.getReason() != null && !entry.getReason().isBlank()) reason = entry.getReason();
            }

            player.sendMessage(LangUtil.prefixed(messagePath,
                    "region", regionId, "protection", regionId,
                    "duration", duration, "reason", reason));
        });
    }

    public void close() { db.close(); }
}
