package megawalls.waypoint;

import java.util.List;
import megawalls.config.MegaWallsConfig;
import megawalls.service.DeveloperDebugService;
import megawalls.util.MinecraftClient;
import megawalls.waypoint.sync.MarkerSyncService;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public final class MarkerService {

    private static final long PING_TTL_MILLIS = 15000L;
    private static final long WAYPOINT_TTL_MILLIS = 60000L;
    private static final long PLAYER_TARGET_BROADCAST_COOLDOWN_MILLIS = 1500L;
    private static final int WAYPOINT_RANGE = 512;

    private final MarkerManager markerManager = new MarkerManager();
    private final MarkerRaycastService raycastService = new MarkerRaycastService();
    private final MarkerSyncService syncService = new MarkerSyncService(markerManager);
    private final MarkerWorldRenderer worldRenderer = new MarkerWorldRenderer();
    private long lastPlayerTargetBroadcastAt;
    private String pendingPlayerTargetBroadcast;
    private long pendingPlayerTargetBroadcastAt;

    public void pingLookedAt(MegaWallsConfig config) {
        Minecraft minecraft = MinecraftClient.withPlayer();
        if (minecraft == null) {
            return;
        }

        MarkerRaycastService.LookTarget target = raycastService.resolveLookTarget(
            minecraft,
            WAYPOINT_RANGE
        );
        if (target == null) {
            return;
        }

        BlockPos position = target.getPosition();
        if (position == null) {
            return;
        }

        Marker marker = markerManager.addLocal(
            MarkerKind.HERE,
            minecraft.thePlayer.getName(),
            position,
            target.isPlayer() ? target.getPlayer().getName() : "",
            PING_TTL_MILLIS,
            false
        );
        syncService.publish(marker, config);
        if (target.isPlayer()) {
            broadcastPlayerTarget(minecraft, target.getPlayer(), config);
        }
    }

    public void addWaypoint(int x, int y, int z) {
        Minecraft minecraft = MinecraftClient.withPlayer();
        if (minecraft == null) {
            return;
        }

        BlockPos position = new BlockPos(x, y, z);
        markerManager.addLocal(
            MarkerKind.WAYPOINT,
            minecraft.thePlayer.getName(),
            position,
            WAYPOINT_TTL_MILLIS,
            true
        );
    }

    public void clear() {
        markerManager.clear();
        WaypointTargetOutlineRegistry.clear();
    }

    public void onClientTick(Minecraft minecraft) {
        if (!MinecraftClient.hasPlayer(minecraft)) {
            return;
        }

        markerManager.removeExpiredAndReached(minecraft.thePlayer.posX, minecraft.thePlayer.posZ);
        WaypointTargetOutlineRegistry.updateFromMarkers(markerManager.snapshot());
        syncService.onClientTick();
        flushPendingPlayerTargetBroadcast(minecraft);
    }

    public boolean onChat(
        String formattedMessage,
        String strippedMessage,
        MegaWallsConfig config,
        DeveloperDebugService debugService
    ) {
        return syncService.handleIncoming(formattedMessage, strippedMessage, config, debugService);
    }

    public void onRenderWorld(RenderWorldLastEvent event, MegaWallsConfig config) {
        if (event == null || config == null || !config.waypointRenderWorld) {
            return;
        }

        List<Marker> markers = markerManager.snapshot();
        worldRenderer.render(
            MinecraftClient.forWorldRender(),
            markers,
            event.partialTicks,
            WAYPOINT_RANGE,
            config.waypointHideNearbyTitles
        );
    }

    private void broadcastPlayerTarget(
        Minecraft minecraft,
        EntityPlayer player,
        MegaWallsConfig config
    ) {
        if (!MinecraftClient.hasPlayer(minecraft) || player == null) {
            return;
        }

        String commandPrefix = config != null && config.waypointPlayerTargetMessageChannel == 1
            ? "/pc "
            : "/ac ";
        long earliestSendAt = System.currentTimeMillis();
        if (config != null && config.waypointSharingEnabled) {
            earliestSendAt += PLAYER_TARGET_BROADCAST_COOLDOWN_MILLIS;
        }
        queuePlayerTargetBroadcast(
            minecraft,
            commandPrefix +
            "Target " +
            formatTargetDisplayName(player) +
            " " +
            formatHealth(player.getHealth()) +
            " HP",
            earliestSendAt
        );
    }

    private void queuePlayerTargetBroadcast(Minecraft minecraft, String command, long earliestSendAt) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (
            now >= earliestSendAt &&
            now - lastPlayerTargetBroadcastAt >= PLAYER_TARGET_BROADCAST_COOLDOWN_MILLIS
        ) {
            sendPlayerTargetBroadcast(minecraft, command, now);
            return;
        }

        pendingPlayerTargetBroadcast = command;
        pendingPlayerTargetBroadcastAt = Math.max(
            earliestSendAt,
            lastPlayerTargetBroadcastAt + PLAYER_TARGET_BROADCAST_COOLDOWN_MILLIS
        );
    }

    private void flushPendingPlayerTargetBroadcast(Minecraft minecraft) {
        if (
            pendingPlayerTargetBroadcast == null ||
            !MinecraftClient.hasPlayer(minecraft)
        ) {
            return;
        }

        long now = System.currentTimeMillis();
        if (
            now < pendingPlayerTargetBroadcastAt ||
            now - lastPlayerTargetBroadcastAt < PLAYER_TARGET_BROADCAST_COOLDOWN_MILLIS
        ) {
            return;
        }

        String command = pendingPlayerTargetBroadcast;
        pendingPlayerTargetBroadcast = null;
        pendingPlayerTargetBroadcastAt = 0L;
        sendPlayerTargetBroadcast(minecraft, command, now);
    }

    private void sendPlayerTargetBroadcast(Minecraft minecraft, String command, long now) {
        if (!MinecraftClient.hasPlayer(minecraft)) {
            return;
        }

        minecraft.thePlayer.sendChatMessage(command);
        lastPlayerTargetBroadcastAt = now;
    }

    private String formatTargetDisplayName(EntityPlayer player) {
        if (player.getDisplayName() == null) {
            return player.getName();
        }

        String displayName = player.getDisplayName().getUnformattedTextForChat();
        return displayName == null || displayName.trim().isEmpty()
            ? player.getName()
            : displayName.trim();
    }

    private String formatHealth(float health) {
        if (Math.abs(health - Math.round(health)) < 0.05F) {
            return Integer.toString(Math.round(health));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", health);
    }
}
