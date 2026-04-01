package p.psban.manager;

import p.psban.PsBanPlugin;
import p.psban.db.Database;
import p.psban.db.MySQLDatabase;
import p.psban.db.SQLiteDatabase;
import p.psban.model.BanEntry;

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
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();
        this.db = type.equals("mysql") ? new MySQLDatabase(plugin) : new SQLiteDatabase(plugin);
        try {
            db.init();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo iniciar la base de datos de PsBan", ex);
        }
    }

    private String key(UUID uuid, String region) {
        return uuid + "::" + region.toLowerCase();
    }

    public void ban(UUID uuid, String playerName, String region, String bannedBy, String reason, long durationMs) {
        BanEntry entry = new BanEntry(uuid, playerName, region, bannedBy, reason, durationMs);
        try {
            db.addBan(entry);
            banCache.put(key(uuid, region), true);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo guardar el baneo: " + ex.getMessage());
        }
    }

    public boolean unban(UUID uuid, String region) {
        try {
            boolean changed = db.removeBan(uuid, region);
            if (changed) banCache.remove(key(uuid, region));
            return changed;
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo desbanear: " + ex.getMessage());
            return false;
        }
    }

    public boolean isBanned(UUID uuid, String region) {
        String key = key(uuid, region);
        Boolean cached = banCache.get(key);
        if (cached != null && !cached) return false;
        try {
            boolean banned = db.isBanned(uuid, region);
            banCache.put(key, banned);
            return banned;
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo comprobar el baneo: " + ex.getMessage());
            return false;
        }
    }

    public BanEntry getActiveBan(UUID uuid, String region) {
        try {
            return db.getActiveBan(uuid, region);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo leer el baneo activo: " + ex.getMessage());
            return null;
        }
    }

    public void purgeExpired() {
        try {
            db.purgeExpired();
            banCache.clear();
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudieron limpiar los baneos caducados: " + ex.getMessage());
        }
    }

    public List<BanEntry> getActiveBans(String region) {
        try {
            return db.getActiveBans(region);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudieron listar los baneos: " + ex.getMessage());
            return List.of();
        }
    }

    public List<BanEntry> getHistory(String region, int limit, int offset) {
        try {
            return db.getHistory(region, limit, offset);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo leer el historial: " + ex.getMessage());
            return List.of();
        }
    }

    public int countHistory(String region) {
        try {
            return db.countHistory(region);
        } catch (Exception ex) {
            plugin.getLogger().warning("No se pudo contar el historial: " + ex.getMessage());
            return 0;
        }
    }

    public void setBypass(UUID uuid, boolean enabled) {
        if (enabled) bypassSet.add(uuid);
        else bypassSet.remove(uuid);
    }

    public boolean hasBypass(UUID uuid) {
        return bypassSet.contains(uuid);
    }

    public boolean checkMsgCooldown(UUID uuid) {
        long cooldown = plugin.getConfig().getLong("message-cooldown-ms", 3000L);
        long now = System.currentTimeMillis();
        long last = msgCooldown.getOrDefault(uuid, 0L);
        if (now - last >= cooldown) {
            msgCooldown.put(uuid, now);
            return true;
        }
        return false;
    }

    public void close() {
        db.close();
    }

    public void saveAll() {
        db.close();
    }
}
