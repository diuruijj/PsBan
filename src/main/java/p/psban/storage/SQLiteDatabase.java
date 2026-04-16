package p.psban.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import p.psban.PsBanPlugin;
import p.psban.model.BanEntry;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SQLiteDatabase implements Database {

    private final PsBanPlugin plugin;
    private HikariDataSource ds;

    public SQLiteDatabase(PsBanPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        File dir = plugin.getDataFolder();
        if (!dir.exists()) dir.mkdirs();

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + new File(dir, "psban.db").getAbsolutePath());
        cfg.setMaximumPoolSize(1);
        cfg.setPoolName("PsBan-Pool");
        cfg.setConnectionTestQuery("SELECT 1");
        ds = new HikariDataSource(cfg);

        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("""
                CREATE TABLE IF NOT EXISTS bans (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid      TEXT    NOT NULL,
                    name      TEXT    NOT NULL,
                    region    TEXT    NOT NULL COLLATE NOCASE,
                    banned_by TEXT    NOT NULL,
                    reason    TEXT    DEFAULT 'No especificada',
                    banned_at BIGINT  NOT NULL,
                    expire_at BIGINT  DEFAULT -1,
                    active    INTEGER DEFAULT 1
                )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_lookup ON bans(uuid, region, active)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_region ON bans(region, active)");
        }
    }

    @Override
    public void addBan(BanEntry e) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO bans(uuid,name,region,banned_by,reason,banned_at,expire_at,active) VALUES(?,?,?,?,?,?,?,1)")) {
            ps.setString(1, e.getPlayerUUID().toString());
            ps.setString(2, e.getPlayerName());
            ps.setString(3, e.getRegionId());
            ps.setString(4, e.getBannedBy());
            ps.setString(5, e.getReason());
            ps.setLong(6, e.getBannedAt());
            ps.setLong(7, e.getExpireAt());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean removeBan(UUID uuid, String region) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE bans SET active=0 WHERE uuid=? AND region=? COLLATE NOCASE AND active=1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, region);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean isBanned(UUID uuid, String region) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT 1 FROM bans WHERE uuid=? AND region=? COLLATE NOCASE AND active=1 AND (expire_at=-1 OR expire_at>?) LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, region);
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public BanEntry getActiveBan(UUID uuid, String region) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM bans WHERE uuid=? AND region=? COLLATE NOCASE AND active=1 AND (expire_at=-1 OR expire_at>?) ORDER BY banned_at DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, region);
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        }
    }

    @Override
    public List<BanEntry> getActiveBans(String region) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM bans WHERE region=? COLLATE NOCASE AND active=1 AND (expire_at=-1 OR expire_at>?) ORDER BY banned_at DESC")) {
            ps.setString(1, region);
            ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                return readList(rs);
            }
        }
    }

    @Override
    public List<BanEntry> getHistory(String region, int limit, int offset) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM bans WHERE region=? COLLATE NOCASE ORDER BY banned_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, region);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                return readList(rs);
            }
        }
    }

    @Override
    public int countHistory(String region) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM bans WHERE region=? COLLATE NOCASE")) {
            ps.setString(1, region);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public void purgeExpired() throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE bans SET active=0 WHERE active=1 AND expire_at!=-1 AND expire_at<?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    private BanEntry fromRow(ResultSet rs) throws SQLException {
        return new BanEntry(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("name"),
            rs.getString("region"),
            rs.getString("banned_by"),
            rs.getString("reason"),
            rs.getLong("banned_at"),
            rs.getLong("expire_at"),
            rs.getInt("active") == 1
        );
    }

    private List<BanEntry> readList(ResultSet rs) throws SQLException {
        List<BanEntry> list = new ArrayList<>();
        while (rs.next()) list.add(fromRow(rs));
        return list;
    }
}
