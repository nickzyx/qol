package megawalls.waypoint.sync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import megawalls.service.DeveloperDebugService;
import megawalls.waypoint.Marker;
import megawalls.waypoint.MarkerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StringUtils;

public final class ChatMarkerTransport {

    private static final long SEND_COOLDOWN_MILLIS = 650L;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final MarkerManager markerManager;
    private final MarkerWireCodec codec = new MarkerWireCodec();
    private final RemoteMarkerRateLimiter rateLimiter = new RemoteMarkerRateLimiter();
    private Marker queuedMarker;
    private long nextSendAt;

    public ChatMarkerTransport(MarkerManager markerManager) {
        this.markerManager = markerManager;
    }

    public void send(Marker marker) {
        queuedMarker = marker;
        flushQueued();
    }

    public void onClientTick() {
        flushQueued();
    }

    private void flushQueued() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (
            queuedMarker == null ||
            minecraft == null ||
            minecraft.thePlayer == null
        ) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSendAt) {
            return;
        }

        Marker markerToSend = queuedMarker;
        queuedMarker = null;
        String payload = codec.encode(markerToSend);
        if (payload.isEmpty()) {
            return;
        }

        minecraft.thePlayer.sendChatMessage("/pc " + payload);
        nextSendAt = now + SEND_COOLDOWN_MILLIS;
    }

    public HandleResult handleIncoming(
        String formattedMessage,
        String strippedMessage,
        DeveloperDebugService debugService
    ) {
        String plain = bestPlainMessage(formattedMessage, strippedMessage);
        if (!codec.hasMagic(plain)) {
            return HandleResult.NO_MATCH;
        }

        MarkerWireCodec.EncodedPayload payload = codec.findPayload(plain);
        if (payload == null) {
            log(debugService, "matched-magic malformed message=\"" + plain + "\"");
            return HandleResult.MATCHED_IGNORED;
        }

        String sender = extractSender(plain, payload.getStartIndex());
        Minecraft minecraft = Minecraft.getMinecraft();
        String localName = minecraft == null || minecraft.thePlayer == null
            ? ""
            : minecraft.thePlayer.getName();
        if (sender.equalsIgnoreCase(localName)) {
            log(debugService, "ignored-self sender=" + sender + " payload=" + payload.getValue());
            return HandleResult.MATCHED_IGNORED;
        }

        MarkerWireCodec.DecodedMarker decoded = codec.decode(payload.getValue(), sender);
        if (decoded == null) {
            log(debugService, "decode-rejected sender=" + sender + " payload=" + payload.getValue());
            return HandleResult.MATCHED_IGNORED;
        }

        if (!rateLimiter.allow(sender)) {
            log(debugService, "rate-limited sender=" + sender + " payload=" + payload.getValue());
            return HandleResult.MATCHED_IGNORED;
        }

        markerManager.addRemote(decoded.getMarker());
        log(
            debugService,
            "remote-added sender=" + sender +
                " kind=" + decoded.getMarker().getKind().name() +
                " pos=" + decoded.getMarker().getPosition().getX() +
                "," + decoded.getMarker().getPosition().getY() +
                "," + decoded.getMarker().getPosition().getZ()
        );
        return HandleResult.MATCHED_ACCEPTED;
    }

    private String bestPlainMessage(String formattedMessage, String strippedMessage) {
        String formatted = StringUtils.stripControlCodes(
            formattedMessage == null ? "" : formattedMessage
        ).trim();
        if (codec.hasMagic(formatted)) {
            return formatted;
        }

        return StringUtils.stripControlCodes(
            strippedMessage == null ? "" : strippedMessage
        ).trim();
    }

    private String extractSender(String message, int payloadStart) {
        String prefix = payloadStart <= 0 ? "" : message.substring(0, payloadStart);
        int colon = prefix.lastIndexOf(':');
        if (colon >= 0) {
            prefix = prefix.substring(0, colon);
        }

        Matcher matcher = USERNAME_PATTERN.matcher(prefix);
        String sender = "Unknown";
        while (matcher.find()) {
            sender = matcher.group();
        }
        return sender;
    }

    private void log(DeveloperDebugService debugService, String message) {
        if (debugService != null) {
            debugService.logWaypoint(message);
        }
    }

    public static final class HandleResult {
        static final HandleResult NO_MATCH = new HandleResult(false, false);
        static final HandleResult MATCHED_IGNORED = new HandleResult(true, false);
        static final HandleResult MATCHED_ACCEPTED = new HandleResult(true, true);

        private final boolean matchedSyncMessage;
        private final boolean accepted;

        private HandleResult(boolean matchedSyncMessage, boolean accepted) {
            this.matchedSyncMessage = matchedSyncMessage;
            this.accepted = accepted;
        }

        public boolean isMatchedSyncMessage() {
            return matchedSyncMessage;
        }

        public boolean isAccepted() {
            return accepted;
        }
    }
}
