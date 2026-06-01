package megawalls.waypoint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.BlockPos;

public final class MarkerManager {

    private static final int MAX_TOTAL_MARKERS = 24;
    private static final double REACH_RADIUS = 4.0D;

    private final LinkedHashMap<UUID, Marker> markers = new LinkedHashMap<UUID, Marker>();

    public synchronized Marker addLocal(
        MarkerKind kind,
        String owner,
        BlockPos position,
        long ttlMillis,
        boolean dismissOnReach
    ) {
        return addLocal(kind, owner, position, "", ttlMillis, dismissOnReach);
    }

    public synchronized Marker addLocal(
        MarkerKind kind,
        String owner,
        BlockPos position,
        String targetPlayerName,
        long ttlMillis,
        boolean dismissOnReach
    ) {
        long now = System.currentTimeMillis();
        Marker marker = new Marker(
            UUID.randomUUID(),
            kind,
            false,
            owner,
            position,
            targetPlayerName,
            now,
            now + Math.max(1000L, ttlMillis),
            dismissOnReach
        );
        put(marker);
        return marker;
    }

    public synchronized boolean addRemote(Marker marker) {
        if (marker == null || !marker.isRemote()) {
            return false;
        }

        if (markers.containsKey(marker.getId())) {
            return false;
        }

        removeRenderedRemoteMarkerForSender(marker.getOwner());
        put(marker);
        return true;
    }

    public synchronized void clear() {
        markers.clear();
    }

    public synchronized void removeExpiredAndReached(double playerX, double playerZ) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (marker.isExpired(now) || isReached(marker, playerX, playerZ)) {
                iterator.remove();
            }
        }
    }

    public synchronized List<Marker> snapshot() {
        LinkedHashMap<String, Marker> latestByOwner = new LinkedHashMap<String, Marker>();
        for (Marker marker : markers.values()) {
            String owner = normalizeOwner(marker.getOwner());
            if (latestByOwner.containsKey(owner)) {
                latestByOwner.remove(owner);
            }
            latestByOwner.put(owner, marker);
        }
        return new ArrayList<Marker>(latestByOwner.values());
    }

    private void put(Marker marker) {
        markers.put(marker.getId(), marker);
        while (markers.size() > MAX_TOTAL_MARKERS) {
            Iterator<UUID> iterator = markers.keySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private void removeRenderedRemoteMarkerForSender(String owner) {
        String key = normalizeOwner(owner);
        Iterator<Map.Entry<UUID, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (
                marker.isRemote() &&
                normalizeOwner(marker.getOwner()).equals(key)
            ) {
                iterator.remove();
            }
        }
    }

    private boolean isReached(Marker marker, double playerX, double playerZ) {
        if (!marker.isDismissOnReach()) {
            return false;
        }

        double dx = marker.getPosition().getX() + 0.5D - playerX;
        double dz = marker.getPosition().getZ() + 0.5D - playerZ;
        return dx * dx + dz * dz <= REACH_RADIUS * REACH_RADIUS;
    }

    private String normalizeOwner(String owner) {
        return owner == null ? "" : owner.trim().toLowerCase(Locale.ROOT);
    }
}
