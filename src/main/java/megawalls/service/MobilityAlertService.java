package megawalls.service;

import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MobilityAlertService {

    private static final int SPIDER_LEAP_RANGE = 30;
    private static final int ENDERMAN_TELEPORT_RANGE = 25;
    private static final double MAX_ALERT_DISTANCE = 45.0D;
    private static final double MAX_ALERT_DISTANCE_SQ =
        MAX_ALERT_DISTANCE * MAX_ALERT_DISTANCE;

    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final MobilityTeamResolver teamResolver;
    private final SpiderLeapAlertService spiderLeapAlertService;
    private final Map<UUID, Long> lastAlertByPlayerId = new HashMap<UUID, Long>();
    private final List<MobilityAlertSnapshot> activeAlerts =
        new ArrayList<MobilityAlertSnapshot>();

    MobilityAlertService(
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService,
        DeveloperDebugService debugService
    ) {
        this.classResolver = classResolver;
        this.contextService = contextService;
        this.teamResolver = new MobilityTeamResolver(contextService);
        this.spiderLeapAlertService =
            new SpiderLeapAlertService(
                classResolver,
                contextService,
                teamResolver,
                debugService
            );
    }

    void handleClientTick(Minecraft minecraft) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            config == null ||
            !config.mobilityAlertEnabled ||
            !contextService.isInMegaWalls() ||
            !contextService.isTrackingActive() ||
            minecraft == null ||
            minecraft.theWorld == null ||
            minecraft.thePlayer == null
        ) {
            activeAlerts.clear();
            return;
        }

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null || playerEntities.isEmpty()) {
            activeAlerts.clear();
            return;
        }

        if (!config.canUseMobilityAlert(contextService.isDeathmatchActive())) {
            activeAlerts.clear();
            return;
        }

        List<MobilityAlertSnapshot> alerts =
            new ArrayList<MobilityAlertSnapshot>();
        EntityPlayer closestSpider = null;
        double closestSpiderDistanceSq = Double.MAX_VALUE;
        EntityPlayer closestEnderman = null;
        double closestEndermanDistanceSq = Double.MAX_VALUE;
        long now = System.currentTimeMillis();

        for (EntityPlayer otherPlayer : playerEntities) {
            if (otherPlayer == null || otherPlayer == minecraft.thePlayer) {
                continue;
            }

            if (teamResolver.hasLocalTeamColor(otherPlayer)) {
                continue;
            }

            MegaWallsClass megaWallsClass =
                classResolver.resolveMegaWallsClass(otherPlayer);
            if (!isMobilityClassEnabled(config, megaWallsClass)) {
                continue;
            }

            double distanceSq = minecraft.thePlayer.getDistanceSqToEntity(
                (Entity) otherPlayer
            );
            if (distanceSq > MAX_ALERT_DISTANCE_SQ) {
                continue;
            }

            if (megaWallsClass == MegaWallsClass.SPIDER) {
                spiderLeapAlertService.detectLeap(config, otherPlayer, distanceSq, now);
            }

            if (!isInAbilityRange(megaWallsClass, distanceSq)) {
                continue;
            }

            alerts.add(createSnapshot(minecraft.thePlayer, otherPlayer, megaWallsClass, distanceSq));

            if (
                megaWallsClass == MegaWallsClass.SPIDER &&
                distanceSq < closestSpiderDistanceSq
            ) {
                closestSpider = otherPlayer;
                closestSpiderDistanceSq = distanceSq;
            } else if (
                megaWallsClass == MegaWallsClass.ENDERMAN &&
                distanceSq < closestEndermanDistanceSq
            ) {
                closestEnderman = otherPlayer;
                closestEndermanDistanceSq = distanceSq;
            }
        }

        Collections.sort(alerts, new Comparator<MobilityAlertSnapshot>() {
            @Override
            public int compare(MobilityAlertSnapshot first, MobilityAlertSnapshot second) {
                return first.distance - second.distance;
            }
        });
        activeAlerts.clear();
        activeAlerts.addAll(alerts);

        spiderLeapAlertService.prune(now);
        notifyIfReady(config, minecraft.thePlayer, closestSpider, MegaWallsClass.SPIDER, closestSpiderDistanceSq, now);
        notifyIfReady(config, minecraft.thePlayer, closestEnderman, MegaWallsClass.ENDERMAN, closestEndermanDistanceSq, now);
    }

    void onPlaySound(PlaySoundEvent event) {
        spiderLeapAlertService.onPlaySound(event);
    }

    void reset() {
        lastAlertByPlayerId.clear();
        spiderLeapAlertService.reset();
        activeAlerts.clear();
    }

    List<MobilityAlertSnapshot> getActiveAlerts() {
        return Collections.unmodifiableList(activeAlerts);
    }

    private boolean isMobilityClassEnabled(
        MegaWallsConfig config,
        MegaWallsClass megaWallsClass
    ) {
        return (megaWallsClass == MegaWallsClass.SPIDER &&
                config.mobilityAlertSpider) ||
            (megaWallsClass == MegaWallsClass.ENDERMAN &&
                config.mobilityAlertEnderman);
    }

    private boolean isInAbilityRange(
        MegaWallsClass megaWallsClass,
        double distanceSq
    ) {
        if (megaWallsClass == MegaWallsClass.SPIDER) {
            return distanceSq <= SPIDER_LEAP_RANGE * SPIDER_LEAP_RANGE;
        }
        if (megaWallsClass == MegaWallsClass.ENDERMAN) {
            return distanceSq <=
                ENDERMAN_TELEPORT_RANGE * ENDERMAN_TELEPORT_RANGE;
        }
        return false;
    }

    private MobilityAlertSnapshot createSnapshot(
        EntityPlayer self,
        EntityPlayer nearbyPlayer,
        MegaWallsClass megaWallsClass,
        double distanceSq
    ) {
        int distance = (int) Math.round(Math.sqrt(distanceSq));
        int yDifference = (int) Math.round(nearbyPlayer.posY - self.posY);
        return new MobilityAlertSnapshot(
            nearbyPlayer,
            megaWallsClass,
            distance,
            yDifference,
            getRelativeYaw(self, nearbyPlayer)
        );
    }

    private float getRelativeYaw(EntityPlayer self, EntityPlayer nearbyPlayer) {
        double deltaX = nearbyPlayer.posX - self.posX;
        double deltaZ = nearbyPlayer.posZ - self.posZ;
        double yawToPlayer = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D;
        return wrapDegrees((float) (yawToPlayer - self.rotationYaw));
    }

    private float wrapDegrees(float degrees) {
        while (degrees <= -180.0F) {
            degrees += 360.0F;
        }
        while (degrees > 180.0F) {
            degrees -= 360.0F;
        }
        return degrees;
    }

    private void notifyIfReady(
        MegaWallsConfig config,
        EntityPlayer self,
        EntityPlayer nearbyPlayer,
        MegaWallsClass megaWallsClass,
        double distanceSq,
        long now
    ) {
        if (self == null || nearbyPlayer == null || megaWallsClass == null) {
            return;
        }
        if (config == null || !config.mobilityChatNotification) {
            return;
        }

        UUID playerId = nearbyPlayer.getUniqueID();
        Long lastAlert = lastAlertByPlayerId.get(playerId);
        if (lastAlert != null && lastAlert.longValue() + getAlertCooldownMs(config) > now) {
            return;
        }

        lastAlertByPlayerId.put(playerId, now);
        int distance = (int) Math.round(Math.sqrt(distanceSq));
        int yDifference = (int) Math.round(nearbyPlayer.posY - self.posY);
        ChatNotifier.warn(
            "Mobility Alert: " +
                megaWallsClass.getDisplayName() +
                " " +
                EnumChatFormatting.AQUA +
                nearbyPlayer.getName() +
                EnumChatFormatting.RED +
                " is " +
                getThreatLabel(megaWallsClass) +
                " (" +
                distance +
                "m, Y " +
                formatYDifference(yDifference) +
                ")."
        );
    }

    private long getAlertCooldownMs(MegaWallsConfig config) {
        int intervalSeconds = config == null ? 5 : config.mobilityAlertIntervalSeconds;
        intervalSeconds = Math.max(1, Math.min(10, intervalSeconds));
        return intervalSeconds * 1000L;
    }

    private String getThreatLabel(MegaWallsClass megaWallsClass) {
        if (megaWallsClass == MegaWallsClass.SPIDER) {
            return "inside Leap range";
        }
        if (megaWallsClass == MegaWallsClass.ENDERMAN) {
            return "inside Teleport range";
        }
        return "nearby";
    }

    private String formatYDifference(int yDifference) {
        if (yDifference > 0) {
            return "+" + yDifference;
        }
        return Integer.toString(yDifference);
    }

}
