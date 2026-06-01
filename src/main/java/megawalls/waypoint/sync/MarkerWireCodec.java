package megawalls.waypoint.sync;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import megawalls.waypoint.Marker;
import megawalls.waypoint.MarkerKind;
import net.minecraft.util.BlockPos;

public final class MarkerWireCodec {

    private static final Pattern WIRE_PATTERN = Pattern.compile(
        "^qol\\|([hw])\\|[a-f0-9]{8}\\|(-?\\d{1,5})\\|(-?\\d{1,3})\\|(-?\\d{1,5})\\|(\\d{1,3})(?:\\|([A-Za-z0-9_]{3,16}))?\\|([a-z0-9]{2})$"
    );
    private static final Pattern PAYLOAD_PATTERN = Pattern.compile(
        "qol\\|[hw]\\|[a-f0-9]{8}\\|-?\\d{1,5}\\|-?\\d{1,3}\\|-?\\d{1,5}\\|\\d{1,3}(?:\\|[A-Za-z0-9_]{3,16})?\\|[a-z0-9]{2}"
    );
    private static final int MAX_TTL_SECONDS = 60;

    public String encode(Marker marker) {
        if (marker == null) {
            return "";
        }

        String id = marker.getId().toString().replace("-", "").substring(0, 8);
        int ttlSeconds = Math.max(
            1,
            Math.min(
                MAX_TTL_SECONDS,
                (int) ((marker.getExpiresAtMillis() - marker.getCreatedAtMillis()) / 1000L)
            )
        );
        BlockPos pos = marker.getPosition();
        String unsigned =
            "qol|" +
            marker.getKind().getWireId() +
            "|" +
            id +
            "|" +
            pos.getX() +
            "|" +
            pos.getY() +
            "|" +
            pos.getZ() +
            "|" +
            ttlSeconds;
        if (marker.hasTargetPlayer()) {
            unsigned += "|" + marker.getTargetPlayerName();
        }
        return unsigned + "|" + checksum(unsigned);
    }

    public DecodedMarker decode(String payload, String owner) {
        if (payload == null) {
            return null;
        }

        String trimmed = payload.trim();
        Matcher matcher = WIRE_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }

        String unsigned = trimmed.substring(0, trimmed.lastIndexOf('|'));
        String expectedChecksum = matcher.group(7);
        if (!checksum(unsigned).equals(expectedChecksum)) {
            return null;
        }

        MarkerKind kind = MarkerKind.fromWireId(matcher.group(1));
        if (kind == null) {
            return null;
        }

        int x = parseInt(matcher.group(2));
        int y = parseInt(matcher.group(3));
        int z = parseInt(matcher.group(4));
        int ttlSeconds = parseInt(matcher.group(5));
        if (!isValidPosition(x, y, z) || ttlSeconds <= 0 || ttlSeconds > MAX_TTL_SECONDS) {
            return null;
        }

        String targetPlayerName = matcher.group(6) == null ? "" : matcher.group(6);
        long now = System.currentTimeMillis();
        UUID id = UUID.nameUUIDFromBytes(trimmed.getBytes());
        Marker marker = new Marker(
            id,
            kind,
            true,
            owner,
            new BlockPos(x, y, z),
            targetPlayerName,
            now,
            now + ttlSeconds * 1000L,
            false
        );
        return new DecodedMarker(marker);
    }

    public EncodedPayload findPayload(String message) {
        if (message == null) {
            return null;
        }

        Matcher matcher = PAYLOAD_PATTERN.matcher(message);
        return matcher.find() ? new EncodedPayload(matcher.group(), matcher.start()) : null;
    }

    public boolean hasMagic(String message) {
        return message != null && message.contains("qol|");
    }

    private boolean isValidPosition(int x, int y, int z) {
        return x >= -2000 && x <= 2000 && z >= -2000 && z <= 2000 && y >= 0 && y <= 255;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String checksum(String unsigned) {
        int hash = 17;
        for (int index = 0; index < unsigned.length(); index++) {
            hash = hash * 31 + unsigned.charAt(index);
        }
        String value = Integer.toString(Math.abs(hash) % 1296, 36).toLowerCase(Locale.ROOT);
        return value.length() == 1 ? "0" + value : value;
    }

    public static final class DecodedMarker {
        private final Marker marker;

        private DecodedMarker(Marker marker) {
            this.marker = marker;
        }

        public Marker getMarker() {
            return marker;
        }
    }

    public static final class EncodedPayload {
        private final String value;
        private final int startIndex;

        private EncodedPayload(String value, int startIndex) {
            this.value = value;
            this.startIndex = startIndex;
        }

        public String getValue() {
            return value;
        }

        public int getStartIndex() {
            return startIndex;
        }
    }
}
