package megawalls.service;

import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
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
    private static final double SPIDER_SOUND_MATCH_RADIUS = 16.0D;
    private static final double SPIDER_SOUND_MATCH_RADIUS_SQ = SPIDER_SOUND_MATCH_RADIUS * SPIDER_SOUND_MATCH_RADIUS;
    private static final long SPIDER_LEAP_ALERT_COOLDOWN_MS = 7000L;
    private static final long SPIDER_LEAP_SOUND_CONFIRM_MS = 700L;
    private static final long MOVEMENT_STATE_TTL_MS = 30000L;
    private static final double SPIDER_LEAP_VERTICAL_DELTA = 0.35D;
    private static final double SPIDER_LEAP_HORIZONTAL_SPEED = 0.45D;
    private static final double SPIDER_LEAP_HORIZONTAL_INCREASE = 1.8D;
    private static final double SPIDER_LEAP_HORIZONTAL_DELTA_INCREASE = 0.20D;
    private static final double SPIDER_LEAP_MIN_PREVIOUS_SPEED = 0.05D;
    private static final int SPIDER_LEAP_TRIGGER_SCORE = 10;
    private static final long DEBUG_SOUND_COOLDOWN_MS = 500L;
    private static final String CAVE_SPIDER_SOUND = "mob.spider.say";

    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final Map<UUID, Long> lastAlertByPlayerId = new HashMap<UUID, Long>();
    private final Map<UUID, Long> lastSpiderLeapAlertByPlayerId =
        new HashMap<UUID, Long>();
    private final Map<UUID, Long> recentSpiderSoundByPlayerId =
        new HashMap<UUID, Long>();
    private final Map<UUID, MovementState> movementStateByPlayerId =
        new HashMap<UUID, MovementState>();
    private final Map<String, Long> lastDebugSoundByName =
        new HashMap<String, Long>();
    private final List<MobilityAlertSnapshot> activeAlerts =
        new ArrayList<MobilityAlertSnapshot>();

    MobilityAlertService(
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService
    ) {
        this.classResolver = classResolver;
        this.contextService = contextService;
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

            if (hasLocalTeamColor(otherPlayer)) {
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
                detectSpiderLeap(config, otherPlayer, distanceSq, now);
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

        pruneMovementStates(now);
        notifyIfReady(config, minecraft.thePlayer, closestSpider, MegaWallsClass.SPIDER, closestSpiderDistanceSq, now);
        notifyIfReady(config, minecraft.thePlayer, closestEnderman, MegaWallsClass.ENDERMAN, closestEndermanDistanceSq, now);
    }

    void onPlaySound(PlaySoundEvent event) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        Minecraft minecraft = Minecraft.getMinecraft();
        maybePrintDebugSound(config, event);
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
            event.sound.getZPosF()
        );
        if (spider == null) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = spider.getUniqueID();
        recentSpiderSoundByPlayerId.put(playerId, now);
    }

    void reset() {
        lastAlertByPlayerId.clear();
        lastSpiderLeapAlertByPlayerId.clear();
        recentSpiderSoundByPlayerId.clear();
        movementStateByPlayerId.clear();
        lastDebugSoundByName.clear();
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

    private boolean isCaveSpiderSound(String soundName) {
        return CAVE_SPIDER_SOUND.equalsIgnoreCase(soundName);
    }

    private void maybePrintDebugSound(
        MegaWallsConfig config,
        PlaySoundEvent event
    ) {
        if (
            config == null ||
            !config.mobilityDebugSounds ||
            event == null ||
            event.name == null
        ) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastPrinted = lastDebugSoundByName.get(event.name);
        if (
            lastPrinted != null &&
            lastPrinted.longValue() + DEBUG_SOUND_COOLDOWN_MS > now
        ) {
            return;
        }

        lastDebugSoundByName.put(event.name, now);
        ChatNotifier.info("Sound packet: " + event.name);
    }

    private EntityPlayer findNearestSpiderForSound(
        Minecraft minecraft,
        double soundX,
        double soundY,
        double soundZ
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
        return nearestSpider == null || hasLocalTeamColor(nearestSpider)
            ? null
            : nearestSpider;
    }

    private void detectSpiderLeap(
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

        int score = 5;
        if (hasRecentSound) {
            score += 3;
        }
        if (hasVerticalBurst) {
            score += 2;
        }
        if (hasHorizontalBurst) {
            score += 2;
        }
        if (hasHorizontalIncrease || hasHorizontalDeltaIncrease) {
            score += 1;
        }
        if (isAirborne) {
            score += 1;
        }

        if (score < SPIDER_LEAP_TRIGGER_SCORE) {
            return;
        }

        triggerSpiderLeapAlert(config, spider, distanceSq, now);
    }

    private void triggerSpiderLeapAlert(
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

    private void pruneMovementStates(long now) {
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

    private boolean hasLocalTeamColor(EntityPlayer otherPlayer) {
        if (
            otherPlayer == null ||
            otherPlayer.worldObj == null
        ) {
            return false;
        }

        char localTeamColor = contextService.getLocalTeamColor();
        if (localTeamColor == '\0') {
            return false;
        }

        Scoreboard scoreboard = otherPlayer.worldObj.getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        ScorePlayerTeam otherTeam = scoreboard.getPlayersTeam(otherPlayer.getName());
        if (otherTeam == null) {
            return false;
        }

        char otherColor = getTeamColor(otherTeam);
        return otherColor != '\0' && otherColor == localTeamColor;
    }

    private char getTeamColor(ScorePlayerTeam team) {
        char prefixColor = getLastColorCode(team.getColorPrefix());
        return prefixColor != '\0'
            ? prefixColor
            : getLastColorCode(team.getColorSuffix());
    }

    private char getLastColorCode(String value) {
        if (value == null || value.isEmpty()) {
            return '\0';
        }

        for (int index = value.length() - 2; index >= 0; index--) {
            if (value.charAt(index) != '\u00a7') {
                continue;
            }

            char colorCode = Character.toLowerCase(value.charAt(index + 1));
            if (
                (colorCode >= '0' && colorCode <= '9') ||
                (colorCode >= 'a' && colorCode <= 'f')
            ) {
                return colorCode;
            }
        }
        return '\0';
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
