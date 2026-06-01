package megawalls.waypoint;

import java.util.UUID;
import net.minecraft.util.BlockPos;

public final class Marker {

    private final UUID id;
    private final MarkerKind kind;
    private final boolean remote;
    private final String owner;
    private final BlockPos position;
    private final String targetPlayerName;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final boolean dismissOnReach;

    public Marker(
        UUID id,
        MarkerKind kind,
        boolean remote,
        String owner,
        BlockPos position,
        String targetPlayerName,
        long createdAtMillis,
        long expiresAtMillis,
        boolean dismissOnReach
    ) {
        this.id = id;
        this.kind = kind;
        this.remote = remote;
        this.owner = owner == null || owner.trim().isEmpty() ? "Unknown" : owner.trim();
        this.position = position;
        this.targetPlayerName = sanitizeTargetPlayerName(targetPlayerName);
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
        this.dismissOnReach = dismissOnReach;
    }

    public UUID getId() {
        return id;
    }

    public MarkerKind getKind() {
        return kind;
    }

    public boolean isRemote() {
        return remote;
    }

    public String getOwner() {
        return owner;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public boolean hasTargetPlayer() {
        return targetPlayerName != null && !targetPlayerName.isEmpty();
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isDismissOnReach() {
        return dismissOnReach;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public float getAlpha(long nowMillis) {
        long lifetime = Math.max(1L, expiresAtMillis - createdAtMillis);
        long remaining = Math.max(0L, expiresAtMillis - nowMillis);
        return Math.max(0.15F, Math.min(1.0F, remaining / (float) lifetime));
    }

    public int distanceHorizontal(double x, double z) {
        double dx = position.getX() + 0.5D - x;
        double dz = position.getZ() + 0.5D - z;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    public int distance3D(double x, double y, double z) {
        double dx = position.getX() + 0.5D - x;
        double dy = position.getY() + 0.5D - y;
        double dz = position.getZ() + 0.5D - z;
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private String sanitizeTargetPlayerName(String name) {
        if (name == null) {
            return "";
        }

        String trimmed = name.trim();
        return trimmed.matches("[A-Za-z0-9_]{3,16}") ? trimmed : "";
    }
}
