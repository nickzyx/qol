package megawalls.service;

import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import megawalls.MegaWallsMod;
import megawalls.domain.DiamondGear;
import megawalls.render.NametagIconService;
import megawalls.render.SnowmanTeamResolver;
import megawalls.render.TransparentSnowmanRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
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

    private MegaWallsService() {}

    public void reportEnergyNow() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
                !contextService.isInMegaWalls() ||
                !contextService.isTrackingActive() ||
                config == null
        ) {
            return;
        }

        energyReportService.reportEnergyNow();
    }

    public boolean isInMegaWalls() {
        return contextService.isInMegaWalls();
    }

    public PlayerStateView queryPlayerState(UUID playerId, String profileName) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryPlayerState(playerId, profileName);
    }

    public PlayerStateView queryPlayerState(UUID playerId, String profileName, String renderedName) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryPlayerState(playerId, profileName, renderedName);
    }

    public PlayerStateView queryNametagPlayerState(UUID playerId, String profileName, String renderedName) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return inactivePlayerState(playerId, profileName);
        }

        return playerTrackingService.queryNametagPlayerState(playerId, profileName, renderedName);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        WorldClient world = minecraft == null ? null : minecraft.theWorld;
        if (contextService.syncWorld(world)) {
            playerTrackingService.clear();
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
        }

        if (minecraft == null || minecraft.thePlayer == null || world == null) {
            return;
        }

        contextService.updateSidebarState(world, classResolver);
        debugService.onClientTick(minecraft, contextService, classResolver);

        MegaWallsConfig config = MegaWallsMod.getConfig();
        updateCheckerService.onClientTick(minecraft, config);

        if (!contextService.isInMegaWalls()) {
            if (!contextService.isDeathmatchActive()) {
                playerTrackingService.resetSnapshots();
            }
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
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
        debugService.logChat(formattedMessage, strippedMessage);

        if (!contextService.isInMegaWalls()) {
            return;
        }

        contextService.observeChatMessage(
                strippedMessage,
                classResolver
        );
        playerTrackingService.onChatReceived(event);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (
                event == null ||
                !contextService.isInMegaWalls() ||
                !contextService.isTrackingActive()
        ) {
            return;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
                config == null ||
                !config.mobilityAlertEnabled ||
                !config.canUseMobilityAlert(contextService.isDeathmatchActive())
        ) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
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
                !contextService.isInMegaWalls() ||
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

        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        playerTrackingService.onPlaySound(event);
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (
                event == null ||
                event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK ||
                !contextService.isInMegaWalls() ||
                !contextService.isTrackingActive()
        ) {
            return;
        }

        interactionGuardService.onPlayerInteract(event, MegaWallsMod.getConfig());
    }

    public void observeTabProfile(UUID playerId, String profileName, String renderedName) {
        debugService.logTabProfilePacket(playerId, profileName, renderedName);
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeTabProfile(playerId, profileName, renderedName);
    }

    public void observeEntityMetadata(int entityId, float health) {
        debugService.logEntityMetadataPacket(entityId, health);
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityMetadata(entityId, health);
    }

    public void observeEquipmentPacket(int entityId, int equipmentSlot, ItemStack itemStack) {
        debugService.logEquipmentPacket(entityId, equipmentSlot, itemStack);
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEquipmentPacket(entityId, equipmentSlot, itemStack);
    }

    public void observeEntityEffect(int entityId, int effectId, int durationTicks) {
        debugService.logEntityEffectPacket(entityId, effectId, durationTicks);
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityEffect(entityId, effectId, durationTicks);
    }

    public void observeEntityEffectRemoved(int entityId, int effectId) {
        debugService.logEntityEffectRemovedPacket(entityId, effectId);
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
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
