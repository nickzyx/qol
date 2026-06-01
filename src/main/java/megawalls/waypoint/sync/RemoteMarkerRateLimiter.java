package megawalls.waypoint.sync;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class RemoteMarkerRateLimiter {

    private static final long WINDOW_MILLIS = 100L;
    private final Map<String, Long> lastAcceptedBySender = new HashMap<String, Long>();

    boolean allow(String sender) {
        long now = System.currentTimeMillis();
        String key = sender == null ? "" : sender.trim().toLowerCase(Locale.ROOT);
        Long lastAccepted = lastAcceptedBySender.get(key);
        if (lastAccepted != null && now - lastAccepted.longValue() < WINDOW_MILLIS) {
            return false;
        }

        lastAcceptedBySender.put(key, Long.valueOf(now));
        return true;
    }
}
