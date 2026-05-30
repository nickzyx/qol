package megawalls.service;

import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SpiderLeapAlertService {

    private static final double SPIDER_SOUND_MATCH_RADIUS = 16.0D;
    private static final double SPIDER_SOUND_MATCH_RADIUS_SQ =
        SPIDER_SOUND_MATCH_RADIUS * SPIDER_SOUND_MATCH_RADIUS;
    private static final long SPIDER_LEAP_ALERT_COOLDOWN_MS = 7000L;
    private static final long SPIDER_LEAP_SOUND_CONFIRM_MS = 700L;
    private static final long SPIDER_LEAP_MAX_SAMPLE_GAP_MS = 350L;
    private static final long MOVEMENT_STATE_TTL_MS = 30000L;
    private static final double SPIDER_LEAP_VERTICAL_DELTA = 0.35D;
    private static final double SPIDER_LEAP_HORIZONTAL_SPEED = 0.45D;
    private static final double SPIDER_LEAP_HORIZONTAL_INCREASE = 1.8D;
    private static final double SPIDER_LEAP_HORIZONTAL_DELTA_INCREASE = 0.20D;
    private static final double SPIDER_LEAP_MIN_PREVIOUS_SPEED = 0.05D;
    private static final int SPIDER_LEAP_TRIGGER_SCORE = 10;
    private static final String CAVE_SPIDER_SOUND = "mob.spider.say";

    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final MobilityTeamResolver teamResolver;
    private final DeveloperDebugService debugService;
    private final Map<UUID, Long> lastSpiderLeapAlertByPlayerId =
        new HashMap<UUID, Long>();
    private final Map<UUID, Long> recentSpiderSoundByPlayerId =
        new HashMap<UUID, Long>();
    private final Map<UUID, MovementState> movementStateByPlayerId =
        new HashMap<UUID, MovementState>();

    SpiderLeapAlertService(
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService,
        MobilityTeamResolver teamResolver,
        DeveloperDebugService debugService
    ) {
        this.classResolver = classResolver;
        this.contextService = contextService;
        this.teamResolver = teamResolver;
        this.debugService = debugService;
    }

    void onPlaySound(PlaySoundEvent event) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (
            config == null ||
            !config.mobilityAlertEnabled ||
            !config.mobilityAlertSpider ||
            !contextService.isInMegaWalls() ||
            !contextService.isTrackingActive() ||
            minecraft == null ||
            minecraft.theWorld == null ||
            minecraft.thePlayer == null ||
            event == null ||
            event.name == null ||
            event.sound == null ||
            !isCaveSpiderSound(event.name)
        ) {
            return;
        }

        EntityPlayer spider = findNearestSpiderForSound(
            minecraft,
            event.sound.getXPosF(),
            event.sound.getYPosF(),
            event.sound.getZPosF(),
            event.name
        );
        if (spider == null) {
            return;
        }

        recentSpiderSoundByPlayerId.put(spider.getUniqueID(), System.currentTimeMillis());
    }

    void detectLeap(
        MegaWallsConfig config,
        EntityPlayer spider,
        double distanceSq,
        long now
    ) {
        if (
            config == null ||
            spider == null
        ) {
            rememberMovementState(spider, now);
            return;
        }

        UUID playerId = spider.getUniqueID();
        MovementState previousState = movementStateByPlayerId.get(playerId);
        MovementState currentState = rememberMovementState(spider, now);
        if (previousState == null || currentState == null) {
            return;
        }
        if (currentState.lastSeenMs - previousState.lastSeenMs > SPIDER_LEAP_MAX_SAMPLE_GAP_MS) {
            return;
        }

        double deltaY = currentState.y - previousState.y;
        double deltaX = currentState.x - previousState.x;
        double deltaZ = currentState.z - previousState.z;
        double horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        boolean hasVerticalBurst = deltaY > SPIDER_LEAP_VERTICAL_DELTA;
        boolean hasHorizontalBurst = horizontalSpeed > SPIDER_LEAP_HORIZONTAL_SPEED;
        boolean hasHorizontalIncrease =
            previousState.horizontalSpeed > SPIDER_LEAP_MIN_PREVIOUS_SPEED &&
                horizontalSpeed > previousState.horizontalSpeed * SPIDER_LEAP_HORIZONTAL_INCREASE;
        boolean hasHorizontalDeltaIncrease =
            horizontalSpeed - previousState.horizontalSpeed > SPIDER_LEAP_HORIZONTAL_DELTA_INCREASE;
        boolean isAirborne = !spider.onGround;
        boolean hasRecentSound = hasRecentSpiderSound(playerId, now);

        int score = 0;
        if (hasRecentSound) {
            score += 4;
        }
        if (hasVerticalBurst) {
            score += 4;
        }
        if (hasHorizontalBurst) {
            score += 3;
        }
        if (hasHorizontalIncrease || hasHorizontalDeltaIncrease) {
            score += 2;
        }
        if (isAirborne) {
            score += 1;
        }

        if (
            score < SPIDER_LEAP_TRIGGER_SCORE ||
            !hasVerticalBurst ||
            !(hasHorizontalBurst || hasHorizontalIncrease || hasHorizontalDeltaIncrease)
        ) {
            if (hasRecentSound || hasVerticalBurst || score >= 6) {
                debugService.logSpiderLeapDecision(
                    spider,
                    distanceSq,
                    deltaY,
                    horizontalSpeed,
                    previousState.horizontalSpeed,
                    score,
                    hasRecentSound,
                    hasVerticalBurst,
                    hasHorizontalBurst,
                    hasHorizontalIncrease,
                    hasHorizontalDeltaIncrease,
                    isAirborne,
                    "threshold-not-met",
                    false
                );
            }
            return;
        }

        debugService.logSpiderLeapDecision(
            spider,
            distanceSq,
            deltaY,
            horizontalSpeed,
            previousState.horizontalSpeed,
            score,
            hasRecentSound,
            hasVerticalBurst,
            hasHorizontalBurst,
            hasHorizontalIncrease,
            hasHorizontalDeltaIncrease,
            isAirborne,
            "accepted",
            true
        );
        triggerLeapAlert(config, spider, distanceSq, now);
    }

    void reset() {
        lastSpiderLeapAlertByPlayerId.clear();
        recentSpiderSoundByPlayerId.clear();
        movementStateByPlayerId.clear();
    }

    void prune(long now) {
        pruneStaleTimes(recentSpiderSoundByPlayerId, now, MOVEMENT_STATE_TTL_MS);
        pruneStaleTimes(lastSpiderLeapAlertByPlayerId, now, MOVEMENT_STATE_TTL_MS);
        List<UUID> staleMovementStateIds = new ArrayList<UUID>();
        for (Map.Entry<UUID, MovementState> entry : movementStateByPlayerId.entrySet()) {
            MovementState state = entry.getValue();
            if (state == null || state.lastSeenMs + MOVEMENT_STATE_TTL_MS < now) {
                staleMovementStateIds.add(entry.getKey());
            }
        }
        for (UUID playerId : staleMovementStateIds) {
            movementStateByPlayerId.remove(playerId);
        }
    }

    private boolean isCaveSpiderSound(String soundName) {
        return CAVE_SPIDER_SOUND.equalsIgnoreCase(soundName);
    }

    private EntityPlayer findNearestSpiderForSound(
        Minecraft minecraft,
        double soundX,
        double soundY,
        double soundZ,
        String soundName
    ) {
        EntityPlayer nearestSpider = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (EntityPlayer player : minecraft.theWorld.playerEntities) {
            if (player == null || player == minecraft.thePlayer) {
                continue;
            }

            MegaWallsClass megaWallsClass =
                classResolver.resolveMegaWallsClass(player);
            if (megaWallsClass != MegaWallsClass.SPIDER) {
                continue;
            }

            double distanceSq = player.getDistanceSq(soundX, soundY, soundZ);
            if (
                distanceSq > SPIDER_SOUND_MATCH_RADIUS_SQ ||
                distanceSq >= nearestDistanceSq
            ) {
                continue;
            }

            nearestSpider = player;
            nearestDistanceSq = distanceSq;
        }
        if (nearestSpider == null || teamResolver.hasLocalTeamColor(nearestSpider)) {
            debugService.logSpiderSoundMatch(soundName, null, 0.0D);
            return null;
        }

        debugService.logSpiderSoundMatch(soundName, nearestSpider, nearestDistanceSq);
        return nearestSpider;
    }

    private void triggerLeapAlert(
        MegaWallsConfig config,
        EntityPlayer spider,
        double distanceSq,
        long now
    ) {
        if (config == null || spider == null) {
            return;
        }

        UUID playerId = spider.getUniqueID();
        Long lastAlert = lastSpiderLeapAlertByPlayerId.get(playerId);
        if (
            lastAlert != null &&
            lastAlert.longValue() + SPIDER_LEAP_ALERT_COOLDOWN_MS > now
        ) {
            return;
        }

        lastSpiderLeapAlertByPlayerId.put(playerId, now);
        int distance = (int) Math.round(Math.sqrt(distanceSq));
        if (config.mobilityLeapGuiAlert && config.mobilityLeapAlertHud != null) {
            config.mobilityLeapAlertHud.showLeapAlert(spider.getName(), distance);
        }
    }

    private MovementState rememberMovementState(EntityPlayer player, long now) {
        if (player == null) {
            return null;
        }

        UUID playerId = player.getUniqueID();
        MovementState previousState = movementStateByPlayerId.get(playerId);
        double horizontalSpeed = 0.0D;
        if (previousState != null) {
            double deltaX = player.posX - previousState.x;
            double deltaZ = player.posZ - previousState.z;
            horizontalSpeed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        }

        MovementState currentState = new MovementState(
            player.posX,
            player.posY,
            player.posZ,
            horizontalSpeed,
            player.onGround,
            now
        );
        movementStateByPlayerId.put(playerId, currentState);
        return currentState;
    }

    private boolean hasRecentSpiderSound(UUID playerId, long now) {
        Long soundTime = recentSpiderSoundByPlayerId.get(playerId);
        return soundTime != null &&
            soundTime.longValue() + SPIDER_LEAP_SOUND_CONFIRM_MS > now;
    }

    private void pruneStaleTimes(
        Map<UUID, Long> timesByPlayerId,
        long now,
        long ttlMs
    ) {
        List<UUID> staleIds = new ArrayList<UUID>();
        for (Map.Entry<UUID, Long> entry : timesByPlayerId.entrySet()) {
            Long time = entry.getValue();
            if (time == null || time.longValue() + ttlMs < now) {
                staleIds.add(entry.getKey());
            }
        }
        for (UUID playerId : staleIds) {
            timesByPlayerId.remove(playerId);
        }
    }

    private static final class MovementState {
        private final double x;
        private final double y;
        private final double z;
        private final double horizontalSpeed;
        private final boolean onGround;
        private final long lastSeenMs;

        private MovementState(
            double x,
            double y,
            double z,
            double horizontalSpeed,
            boolean onGround,
            long lastSeenMs
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.horizontalSpeed = horizontalSpeed;
            this.onGround = onGround;
            this.lastSeenMs = lastSeenMs;
        }
    }
}
