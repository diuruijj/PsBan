package p.psban.model;

import java.util.UUID;

public class BanEntry {
    private final UUID playerUUID;
    private final String playerName;
    private final String regionId;
    private final String bannedBy;
    private final String reason;
    private final long bannedAt;
    private final long expireAt;
    private boolean active;

    public BanEntry(UUID playerUUID, String playerName, String regionId, String bannedBy, String reason, long durationMs) {
        this(playerUUID, playerName, regionId, bannedBy, reason, System.currentTimeMillis(),
                durationMs <= 0 ? -1L : System.currentTimeMillis() + durationMs, true);
    }

    public BanEntry(UUID playerUUID, String playerName, String regionId, String bannedBy, String reason, long bannedAt, long expireAt) {
        this(playerUUID, playerName, regionId, bannedBy, reason, bannedAt, expireAt, true);
    }

    public BanEntry(UUID playerUUID, String playerName, String regionId, String bannedBy, String reason, long bannedAt, long expireAt, boolean active) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.regionId = regionId;
        this.bannedBy = bannedBy;
        this.reason = reason;
        this.bannedAt = bannedAt;
        this.expireAt = expireAt;
        this.active = active;
    }

    public boolean isExpired() {
        return expireAt != -1L && System.currentTimeMillis() > expireAt;
    }

    public boolean isPermanent() {
        return expireAt == -1L;
    }

    public long remainingMs() {
        if (isPermanent()) return Long.MAX_VALUE;
        return Math.max(0L, expireAt - System.currentTimeMillis());
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getBannedBy() {
        return bannedBy;
    }

    public String getReason() {
        return reason;
    }

    public long getBannedAt() {
        return bannedAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
