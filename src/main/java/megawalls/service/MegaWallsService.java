package megawalls.service;

import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import megawalls.MegaWallsMod;
import megawalls.domain.DiamondGear;
import megawalls.render.NametagIconService;
import megawalls.render.BarrierBlockReplacement;
import megawalls.render.SnowmanTeamResolver;
import megawalls.render.TransparentSnowmanRenderer;
import megawalls.util.MinecraftClient;
import megawalls.waypoint.MarkerService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.UUID;

public final class MegaWallsService {

    public static final MegaWallsService INSTANCE = new MegaWallsService();

    private final MegaWallsClassResolver classResolver = new MegaWallsClassResolver();
    private final TrackedPlayerRegistry trackedPlayerRegistry = new TrackedPlayerRegistry();
    private final PhoenixResurrectionRegistry phoenixResurrectionRegistry =
            new PhoenixResurrectionRegistry();
    private final MegaWallsContextService contextService = new MegaWallsContextService();
    private final DeveloperDebugService debugService = new DeveloperDebugService();
    private final PlayerTrackingService playerTrackingService =
            new PlayerTrackingService(
                    trackedPlayerRegistry,
                    phoenixResurrectionRegistry,
                    classResolver,
                    contextService,
                    debugService
            );
    private final MobilityAlertService mobilityAlertService =
            new MobilityAlertService(classResolver, contextService, debugService);
    private final MobilityCompassRenderer mobilityCompassRenderer =
            new MobilityCompassRenderer();
    private final NametagIconService nametagIconService =
            new NametagIconService(classResolver, contextService);
    private final PacketObservationService packetObservationService =
            new PacketObservationService(playerTrackingService, debugService);
    private final EnergyReportService energyReportService = new EnergyReportService(classResolver);
    private final InteractionGuardService interactionGuardService = new InteractionGuardService();
    private final SnowmanTeamResolver snowmanTeamResolver = new SnowmanTeamResolver();
    private final TransparentSnowmanRenderer transparentSnowmanRenderer =
            new TransparentSnowmanRenderer();
    private final UpdateCheckerService updateCheckerService = new UpdateCheckerService();
    private final MarkerService markerService = new MarkerService();
    private final HunterForceOfNatureService hunterForceOfNatureService =
            new HunterForceOfNatureService();
    private final InactiveGatheringActionbarService inactiveGatheringActionbarService =
            new InactiveGatheringActionbarService();
    private final PregameClassTrackerService pregameClassTrackerService =
            new PregameClassTrackerService();
    private boolean lastVisibleBarriers;

    private MegaWallsService() {}

    public void reportEnergyNow() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
                !contextService.isInMegaWallsGame() ||
                !contextService.isTrackingActive() ||
                config == null
        ) {
            return;
        }

        energyReportService.reportEnergyNow();
    }

    public void pingWaypointNow() {
        markerService.pingLookedAt(MegaWallsMod.getConfig());
    }

    public void logCompactSidebar(
            String formattedLine,
            String strippedLine,
            boolean hidden,
            String rewrittenLine
    ) {
        debugService.logCompactSidebar(
                formattedLine,
                strippedLine,
                hidden,
                rewrittenLine
        );
    }

    public boolean isInMegaWallsGame() {
        return contextService.isInMegaWallsGame();
    }

    public boolean isInPreGameQueue() {
        return contextService.isInPreGameQueue();
    }

    public boolean isDeathmatchActive() {
        return contextService.isDeathmatchActive();
    }

    public PlayerStateView queryPlayerState(UUID playerId, String profileName) {
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryPlayerState(playerId, profileName);
    }

    public PlayerStateView queryPlayerState(UUID playerId, String profileName, String renderedName) {
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryPlayerState(playerId, profileName, renderedName);
    }

    public PlayerStateView queryNametagPlayerState(UUID playerId, String profileName, String renderedName) {
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryNametagPlayerState(playerId, profileName, renderedName);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = MinecraftClient.get();
        WorldClient world = minecraft == null ? null : minecraft.theWorld;
        if (contextService.syncWorld(world)) {
            playerTrackingService.clear();
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
        }

        if (!MinecraftClient.hasPlayer(minecraft) || world == null) {
            return;
        }

        contextService.updateSidebarState(world, classResolver);
        debugService.onClientTick(minecraft, contextService, classResolver);

        MegaWallsConfig config = MegaWallsMod.getConfig();
        BarrierBlockReplacement.updateFromConfig(config);
        debugService.logBarrierPerformance(BarrierBlockReplacement.drainPerformanceSnapshot());
        updateCheckerService.onClientTick(minecraft, config);
        markerService.onClientTick(minecraft);
        pregameClassTrackerService.onClientTick(
                minecraft,
                config,
                contextService
        );
        if (config != null && config.pregameClassTrackerHud != null) {
            config.pregameClassTrackerHud.setQueueActive(
                    contextService.isInPreGameQueue()
            );
        }
        refreshBarrierChunksIfNeeded(minecraft, config);

        if (!contextService.isInMegaWallsGame()) {
            if (!contextService.isDeathmatchActive()) {
                playerTrackingService.resetSnapshots();
            }
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
            if (!contextService.isInPreGameQueue()) {
                pregameClassTrackerService.reset();
            }
            return;
        }

        if (!contextService.isTrackingActive()) {
            if (!contextService.isDeathmatchActive()) {
                playerTrackingService.resetSnapshots();
            }
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
            return;
        }

        playerTrackingService.onClientTick(minecraft);
        nametagIconService.handleClientTick(minecraft);
        if (
                config == null ||
                config.canUseMobilityAlert(contextService.isDeathmatchActive())
        ) {
            mobilityAlertService.handleClientTick(minecraft);
        } else {
            mobilityAlertService.reset();
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String formattedMessage = event == null || event.message == null
                ? ""
                : event.message.getFormattedText();
        String strippedMessage = event == null || event.message == null
                ? ""
                : event.message.getUnformattedTextForChat();
        MegaWallsConfig config = MegaWallsMod.getConfig();

        inactiveGatheringActionbarService.filterActionbar(
                event,
                config,
                classResolver,
                contextService
        );
        if (event != null && event.isCanceled()) {
            return;
        }

        if (hunterForceOfNatureService.onChatReceived(event, config)) {
            event.setCanceled(true);
            return;
        }

        if (markerService.onChat(formattedMessage, strippedMessage, config, debugService)) {
            event.setCanceled(true);
            return;
        }

        debugService.logChat(formattedMessage, strippedMessage);

        if (!contextService.isInMegaWallsGame()) {
            return;
        }

        contextService.observeChatMessage(
                strippedMessage,
                classResolver
        );
        playerTrackingService.onChatReceived(event);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        markerService.onRenderWorld(event, MegaWallsMod.getConfig());
    }

    private void refreshBarrierChunksIfNeeded(Minecraft minecraft, MegaWallsConfig config) {
        if (
                !MinecraftClient.hasRenderGlobal(minecraft) ||
                config == null
        ) {
            return;
        }

        if (
                config.visibleBarriers == lastVisibleBarriers
        ) {
            return;
        }

        lastVisibleBarriers = config.visibleBarriers;

        int radius = Math.max(2, minecraft.gameSettings.renderDistanceChunks) * 16 + 16;
        int playerX = (int) Math.floor(minecraft.thePlayer.posX);
        int playerZ = (int) Math.floor(minecraft.thePlayer.posZ);
        long startedAt = System.nanoTime();
        minecraft.renderGlobal.markBlockRangeForRenderUpdate(
                playerX - radius,
                0,
                playerZ - radius,
                playerX + radius,
                255,
                playerZ + radius
        );
        debugService.logBarrierChunkRefresh(
                config.visibleBarriers,
                radius,
                playerX,
                playerZ,
                System.nanoTime() - startedAt
        );
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (event == null) {
            return;
        }

        Minecraft minecraft = MinecraftClient.withWorld();
        if (minecraft == null) {
            return;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        hunterForceOfNatureService.onRenderOverlay(event, config);
        if (config != null && config.pregameClassTrackerHud != null) {
            config.pregameClassTrackerHud.setQueueActive(
                    contextService.isInPreGameQueue()
            );
        }

        if (
                config == null ||
                !config.mobilityAlertEnabled ||
                !config.canUseMobilityAlert(contextService.isDeathmatchActive())
        ) {
            return;
        }

        if (config.mobilityCompassHud) {
            mobilityCompassRenderer.render(
                    minecraft,
                    config,
                    mobilityAlertService.getActiveAlerts()
            );
        }

        if (config.mobilityLeapAlertHud != null) {
            config.mobilityLeapAlertHud.renderActive(minecraft, config);
        }
    }

    @SubscribeEvent
    public void onRenderOverlayPost(RenderGameOverlayEvent.Post event) {
        if (
                event == null ||
                event.type != RenderGameOverlayEvent.ElementType.ALL
        ) {
            return;
        }

        Minecraft minecraft = MinecraftClient.withWorld();
        if (minecraft == null) {
            return;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (config == null || config.pregameClassTrackerHud == null) {
            return;
        }

        config.pregameClassTrackerHud.setQueueActive(
                contextService.isInPreGameQueue()
        );
        if (!contextService.isInPreGameQueue()) {
            return;
        }

        config.pregameClassTrackerHud.renderActive(
                minecraft,
                config,
                pregameClassTrackerService.getLatestCounts()
        );
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        if (event == null) {
            return;
        }

        if (!(event.entity instanceof EntitySnowman)) {
            return;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
                config == null ||
                !config.transparentSnowmen ||
                !contextService.isInMegaWallsGame() ||
                !contextService.isTrackingActive() ||
                (
                        !config.transparentSnowmenAllTeams &&
                        !snowmanTeamResolver.isLocalTeamSnowman(
                                (EntitySnowman) event.entity,
                                contextService.getLocalTeamColor()
                        )
                )
        ) {
            return;
        }

        transparentSnowmanRenderer.beginRender(event, config.transparentSnowmenOpacity);
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post event) {
        transparentSnowmanRenderer.finishRender(event);
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        debugService.logSound(event);
        mobilityAlertService.onPlaySound(event);

        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        playerTrackingService.onPlaySound(event);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (
                event == null ||
                event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK ||
                !contextService.isInMegaWallsGame() ||
                !contextService.isTrackingActive()
        ) {
            return;
        }

        interactionGuardService.onPlayerInteract(event, MegaWallsMod.getConfig());
    }

    public void observeTabProfile(UUID playerId, String profileName, String renderedName) {
        debugService.logTabProfilePacket(playerId, profileName, renderedName);
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeTabProfile(playerId, profileName, renderedName);
    }

    public void observeEntityMetadata(int entityId, float health) {
        debugService.logEntityMetadataPacket(entityId, health);
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityMetadata(entityId, health);
    }

    public void observeEquipmentPacket(int entityId, int equipmentSlot, ItemStack itemStack) {
        debugService.logEquipmentPacket(entityId, equipmentSlot, itemStack);
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEquipmentPacket(entityId, equipmentSlot, itemStack);
    }

    public void observeEntityEffect(int entityId, int effectId, int durationTicks) {
        debugService.logEntityEffectPacket(entityId, effectId, durationTicks);
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityEffect(entityId, effectId, durationTicks);
    }

    public void observeEntityEffectRemoved(int entityId, int effectId) {
        debugService.logEntityEffectRemovedPacket(entityId, effectId);
        if (!contextService.isInMegaWallsGame() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityEffectRemoved(entityId, effectId);
    }

    private PlayerStateView inactivePlayerState(UUID playerId, String profileName) {
        return new PlayerStateView(
                playerId,
                profileName,
                false,
                false,
                Collections.<DiamondGear>emptyList(),
                -1,
                false
        );
    }

}
