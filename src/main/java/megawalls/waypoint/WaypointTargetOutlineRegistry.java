package megawalls.waypoint;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;

public final class WaypointTargetOutlineRegistry {

    private static final Map<String, Integer> OUTLINE_COLORS = new HashMap<String, Integer>();

    private WaypointTargetOutlineRegistry() {}

    public static synchronized void updateFromMarkers(List<Marker> markers) {
        OUTLINE_COLORS.clear();
        if (markers == null || markers.isEmpty()) {
            return;
        }

        for (Marker marker : markers) {
            if (marker == null || !marker.hasTargetPlayer()) {
                continue;
            }

            OUTLINE_COLORS.put(normalize(marker.getTargetPlayerName()), Integer.valueOf(marker.getKind().getRgb()));
        }
    }

    public static synchronized boolean hasTargets() {
        return !OUTLINE_COLORS.isEmpty();
    }

    public static synchronized boolean shouldOutline(EntityPlayer player) {
        return player != null && OUTLINE_COLORS.containsKey(normalize(player.getName()));
    }

    public static synchronized int getOutlineColor(EntityPlayer player) {
        Integer color = player == null ? null : OUTLINE_COLORS.get(normalize(player.getName()));
        return color == null ? MarkerKind.HERE.getRgb() : color.intValue();
    }

    public static synchronized void clear() {
        OUTLINE_COLORS.clear();
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
