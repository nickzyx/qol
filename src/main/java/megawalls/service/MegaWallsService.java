package megawalls.service;

import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import megawalls.MegaWallsMod;
import megawalls.domain.DiamondGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
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
    private final PlayerTrackingService playerTrackingService =
            new PlayerTrackingService(
                    trackedPlayerRegistry,
                    phoenixResurrectionRegistry,
                    classResolver,
                    contextService
            );
    private final MobilityAlertService mobilityAlertService =
            new MobilityAlertService(classResolver, contextService);
    private final NametagIconService nametagIconService =
            new NametagIconService(classResolver, contextService);
    private final PacketObservationService packetObservationService = new PacketObservationService(playerTrackingService);
    private final EnergyReportService energyReportService = new EnergyReportService(classResolver);

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

    PlayerStateView queryNametagPlayerState(UUID playerId, String profileName, String renderedName) {
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

        if (!contextService.isInMegaWalls()) {
            playerTrackingService.resetSnapshots();
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
            return;
        }

        if (!contextService.isTrackingActive()) {
            playerTrackingService.resetSnapshots();
            mobilityAlertService.reset();
            nametagIconService.reset(minecraft);
            return;
        }

        playerTrackingService.onClientTick(minecraft);
        nametagIconService.handleClientTick(minecraft);
        MegaWallsConfig config = MegaWallsMod.getConfig();
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
        if (!contextService.isInMegaWalls()) {
            return;
        }

        contextService.observeChatMessage(
                event == null || event.message == null
                        ? ""
                        : event.message.getUnformattedTextForChat(),
                classResolver
        );
        playerTrackingService.onChatReceived(event);
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        playerTrackingService.onPlaySound(event);
    }

    public void observeTabProfile(UUID playerId, String profileName) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeTabProfile(playerId, profileName);
    }

    public void observeTabProfile(UUID playerId, String profileName, String renderedName) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeTabProfile(playerId, profileName, renderedName);
    }

    public void observeEntityMetadata(int entityId, float health) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityMetadata(entityId, health);
    }

    public void observeEquipmentPacket(int entityId, int equipmentSlot, ItemStack itemStack) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEquipmentPacket(entityId, equipmentSlot, itemStack);
    }

    public void observeEntityEffect(int entityId, int effectId, int durationTicks) {
        if (!contextService.isInMegaWalls() || !contextService.isTrackingActive()) {
            return;
        }

        packetObservationService.observeEntityEffect(entityId, effectId, durationTicks);
    }

    public void observeEntityEffectRemoved(int entityId, int effectId) {
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
