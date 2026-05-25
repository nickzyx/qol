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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MobilityAlertService {

    private static final int SPIDER_LEAP_RANGE = 30;
    private static final int ENDERMAN_TELEPORT_RANGE = 25;
    private static final double MAX_ALERT_DISTANCE =
        Math.max(SPIDER_LEAP_RANGE, ENDERMAN_TELEPORT_RANGE);
    private static final double MAX_ALERT_DISTANCE_SQ =
        MAX_ALERT_DISTANCE * MAX_ALERT_DISTANCE;

    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final Map<UUID, Long> lastAlertByPlayerId = new HashMap<UUID, Long>();

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
            !config.canUseMobilityAlert(contextService.isDeathmatchActive()) ||
            minecraft == null ||
            minecraft.theWorld == null ||
            minecraft.thePlayer == null ||
            !contextService.isInMegaWalls()
        ) {
            return;
        }

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null || playerEntities.isEmpty()) {
            return;
        }

        EntityPlayer closestSpider = null;
        double closestSpiderDistanceSq = Double.MAX_VALUE;
        EntityPlayer closestEnderman = null;
        double closestEndermanDistanceSq = Double.MAX_VALUE;

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

            if (!isInAbilityRange(megaWallsClass, distanceSq)) {
                continue;
            }

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

        long now = System.currentTimeMillis();
        notifyIfReady(config, minecraft.thePlayer, closestSpider, MegaWallsClass.SPIDER, closestSpiderDistanceSq, now);
        notifyIfReady(config, minecraft.thePlayer, closestEnderman, MegaWallsClass.ENDERMAN, closestEndermanDistanceSq, now);
    }

    void reset() {
        lastAlertByPlayerId.clear();
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
        int intervalSeconds = config == null ? 3 : config.mobilityAlertIntervalSeconds;
        intervalSeconds = Math.max(1, Math.min(5, intervalSeconds));
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
}
