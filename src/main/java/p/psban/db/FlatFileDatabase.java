package p.psban.db;

import org.bukkit.configuration.file.YamlConfiguration;
import p.psban.PsBanPlugin;
import p.psban.model.BanEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

class FlatFileDatabase implements Database {
    protected final PsBanPlugin plugin;
    private final String fileName;
    private File file;
    private YamlConfiguration yaml;

    FlatFileDatabase(PsBanPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public synchronized void init() throws Exception {
        file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (!file.exists()) file.createNewFile();
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    @Override
    public synchronized void addBan(BanEntry entry) throws Exception {
        ensureLoaded();
        String base = "bans." + System.currentTimeMillis() + "-" + Math.abs(entry.getPlayerUUID().hashCode());
        yaml.set(base + ".uuid", entry.getPlayerUUID().toString());
        yaml.set(base + ".name", entry.getPlayerName());
        yaml.set(base + ".region", entry.getRegionId());
        yaml.set(base + ".banned_by", entry.getBannedBy());
        yaml.set(base + ".reason", entry.getReason());
        yaml.set(base + ".banned_at", entry.getBannedAt());
        yaml.set(base + ".expire_at", entry.getExpireAt());
        yaml.set(base + ".active", true);
        save();
    }

    @Override
    public synchronized boolean removeBan(UUID uuid, String region) throws Exception {
        ensureLoaded();
        boolean changed = false;
        for (String key : yaml.getKeys(true)) {
            if (!key.startsWith("bans.")) continue;
            if (!key.endsWith(".uuid")) continue;
            String base = key.substring(0, key.length() - 5);
            String currentUuid = yaml.getString(base + ".uuid", "");
            String currentRegion = yaml.getString(base + ".region", "");
            boolean active = yaml.getBoolean(base + ".active", true);
            if (active && currentUuid.equalsIgnoreCase(uuid.toString()) && currentRegion.equalsIgnoreCase(region)) {
                yaml.set(base + ".active", false);
                changed = true;
            }
        }
        if (changed) save();
        return changed;
    }

    @Override
    public synchronized boolean isBanned(UUID uuid, String region) throws Exception {
        ensureLoaded();
        purgeExpired();
        for (BanEntry entry : getEntriesInternal()) {
            if (!entry.isActive()) continue;
            if (entry.getPlayerUUID().equals(uuid) && entry.getRegionId().equalsIgnoreCase(region) && !entry.isExpired()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized BanEntry getActiveBan(UUID uuid, String region) throws Exception {
        ensureLoaded();
        purgeExpired();
        for (BanEntry entry : getEntriesInternal()) {
            if (!entry.isActive()) continue;
            if (entry.getPlayerUUID().equals(uuid) && entry.getRegionId().equalsIgnoreCase(region) && !entry.isExpired()) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public synchronized List<BanEntry> getActiveBans(String region) throws Exception {
        ensureLoaded();
        purgeExpired();
        List<BanEntry> out = new ArrayList<>();
        for (BanEntry entry : getEntriesInternal()) {
            if (entry.isActive() && !entry.isExpired() && entry.getRegionId().equalsIgnoreCase(region)) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingLong(BanEntry::getBannedAt).reversed());
        return out;
    }

    @Override
    public synchronized List<BanEntry> getHistory(String region, int limit, int offset) throws Exception {
        ensureLoaded();
        List<BanEntry> out = new ArrayList<>();
        for (BanEntry entry : getEntriesInternal()) {
            if (entry.getRegionId().equalsIgnoreCase(region)) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingLong(BanEntry::getBannedAt).reversed());
        int from = Math.min(Math.max(offset, 0), out.size());
        int to = Math.min(from + Math.max(limit, 0), out.size());
        return new ArrayList<>(out.subList(from, to));
    }

    @Override
    public synchronized int countHistory(String region) throws Exception {
        ensureLoaded();
        int count = 0;
        for (BanEntry entry : getEntriesInternal()) {
            if (entry.getRegionId().equalsIgnoreCase(region)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized void purgeExpired() throws Exception {
        ensureLoaded();
        boolean changed = false;
        for (String key : yaml.getKeys(true)) {
            if (!key.startsWith("bans.")) continue;
            if (!key.endsWith(".expire_at")) continue;
            String base = key.substring(0, key.length() - 10);
            long expireAt = yaml.getLong(base + ".expire_at", -1L);
            boolean active = yaml.getBoolean(base + ".active", true);
            if (active && expireAt != -1L && System.currentTimeMillis() > expireAt) {
                yaml.set(base + ".active", false);
                changed = true;
            }
        }
        if (changed) save();
    }

    @Override
    public void close() {
        try {
            save();
        } catch (Exception ignored) {
        }
    }

    private List<BanEntry> getEntriesInternal() {
        List<BanEntry> out = new ArrayList<>();
        for (String key : yaml.getKeys(true)) {
            if (!key.startsWith("bans.")) continue;
            if (!key.endsWith(".uuid")) continue;
            String base = key.substring(0, key.length() - 5);
            String uuidRaw = yaml.getString(base + ".uuid", null);
            if (uuidRaw == null || uuidRaw.isBlank()) continue;
            try {
                UUID uuid = UUID.fromString(uuidRaw);
                String name = yaml.getString(base + ".name", "desconocido");
                String region = yaml.getString(base + ".region", "");
                String bannedBy = yaml.getString(base + ".banned_by", "CONSOLE");
                String reason = yaml.getString(base + ".reason", "No especificada");
                long bannedAt = yaml.getLong(base + ".banned_at", System.currentTimeMillis());
                long expireAt = yaml.getLong(base + ".expire_at", -1L);
                boolean active = yaml.getBoolean(base + ".active", true);
                out.add(new BanEntry(uuid, name, region, bannedBy, reason, bannedAt, expireAt, active));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    private void ensureLoaded() throws Exception {
        if (yaml == null) init();
    }

    private void save() throws IOException {
        if (yaml != null && file != null) yaml.save(file);
    }
}
