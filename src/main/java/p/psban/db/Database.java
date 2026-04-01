package p.psban.db;

import p.psban.model.BanEntry;

import java.util.List;
import java.util.UUID;

public interface Database {
    void init() throws Exception;
    void addBan(BanEntry entry) throws Exception;
    boolean removeBan(UUID uuid, String region) throws Exception;
    boolean isBanned(UUID uuid, String region) throws Exception;
    BanEntry getActiveBan(UUID uuid, String region) throws Exception;
    List<BanEntry> getActiveBans(String region) throws Exception;
    List<BanEntry> getHistory(String region, int limit, int offset) throws Exception;
    int countHistory(String region) throws Exception;
    void purgeExpired() throws Exception;
    void close();
}
