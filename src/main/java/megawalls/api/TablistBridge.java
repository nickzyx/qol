package megawalls.api;

import megawalls.service.MegaWallsService;

import java.util.UUID;

public final class TablistBridge {

    private TablistBridge() {}

    public static PlayerStateView queryPlayerState(UUID playerId, String profileName) {
        return MegaWallsService.INSTANCE.queryPlayerState(playerId, profileName);
    }

    public static PlayerStateView queryPlayerState(UUID playerId, String profileName, String renderedName) {
        return MegaWallsService.INSTANCE.queryPlayerState(playerId, profileName, renderedName);
    }
}
