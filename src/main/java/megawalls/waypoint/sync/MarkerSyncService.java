package megawalls.waypoint.sync;

import megawalls.config.MegaWallsConfig;
import megawalls.service.DeveloperDebugService;
import megawalls.waypoint.Marker;
import megawalls.waypoint.MarkerManager;

public final class MarkerSyncService {

    private final ChatMarkerTransport chatTransport;

    public MarkerSyncService(MarkerManager markerManager) {
        this.chatTransport = new ChatMarkerTransport(markerManager);
    }

    public void publish(Marker marker, MegaWallsConfig config) {
        if (config == null || !config.waypointSharingEnabled) {
            return;
        }

        chatTransport.send(marker);
    }

    public void onClientTick() {
        chatTransport.onClientTick();
    }

    public boolean handleIncoming(
        String formattedMessage,
        String strippedMessage,
        MegaWallsConfig config,
        DeveloperDebugService debugService
    ) {
        ChatMarkerTransport.HandleResult result =
            chatTransport.handleIncoming(formattedMessage, strippedMessage, debugService);
        return result.isMatchedSyncMessage() &&
            (config == null || config.waypointHideSyncMessages);
    }
}
