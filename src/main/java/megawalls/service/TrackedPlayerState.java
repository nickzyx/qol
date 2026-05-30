package megawalls.service;

import megawalls.domain.MegaWallsClass;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

final class TrackedPlayerState {

    UUID sessionUuid;
    String profileName;
    String normalizedProfileName;
    String lastRenderedName;
    char teamColor;
    MegaWallsClass megaWallsClass;
    final EnumSet<ObservedDiamond> observedDiamonds = EnumSet.noneOf(ObservedDiamond.class);
    MegaWallsClass potionClass;
    int remainingPotions = -1;
    boolean hasPotionSample;
    float lastHealth;
    boolean wasAlive;
    int lastPotionTablistHealth = Integer.MIN_VALUE;
    int maxPotionTablistHealth = Integer.MIN_VALUE;
    long lastPotionScoreboardHealthAt;
    long lastPotionHealthMissingLogAt;
    boolean lastPotionHealthMissingEntityLoaded;
    boolean potionEntityLoaded;
    boolean potionRespawnCandidate;
    boolean potionLowHealthSeen;
    int potionHealBurstGain;
    long potionHealBurstStartedAt;
    long potionHealBurstLastAt;
    boolean phoenixIndicatorSeen;
    long lastPotionUseAt;
    long strengthExpiresAt;
    long lastStrengthTriggerAt;

    TrackedPlayerState(UUID sessionUuid, String profileName) {
        bindIdentity(sessionUuid, profileName);
    }

    void bindIdentity(UUID playerId, String profileName) {
        if (playerId != null) {
            sessionUuid = playerId;
        }
        if (profileName != null && !profileName.isEmpty()) {
            this.profileName = profileName;
            normalizedProfileName = normalizeKey(profileName);
        }
    }

    void bindRenderedName(String renderedName) {
        if (renderedName != null && !renderedName.isEmpty()) {
            lastRenderedName = renderedName;
            char renderedTeamColor = getRenderedTeamColor(renderedName);
            if (renderedTeamColor != '\0') {
                teamColor = renderedTeamColor;
            }
        }
    }

    void resetPotions() {
        potionClass = null;
        remainingPotions = -1;
        hasPotionSample = false;
        lastHealth = 0.0F;
        wasAlive = false;
        lastPotionTablistHealth = Integer.MIN_VALUE;
        maxPotionTablistHealth = Integer.MIN_VALUE;
        lastPotionScoreboardHealthAt = 0L;
        lastPotionHealthMissingLogAt = 0L;
        lastPotionHealthMissingEntityLoaded = false;
        potionEntityLoaded = false;
        potionRespawnCandidate = false;
        potionLowHealthSeen = false;
        potionHealBurstGain = 0;
        potionHealBurstStartedAt = 0L;
        potionHealBurstLastAt = 0L;
        lastPotionUseAt = 0L;
    }

    int getDisplayedPotionCount() {
        if (remainingPotions >= 0) {
            return remainingPotions;
        }
        return megaWallsClass == null ? -1 : megaWallsClass.getPotionCount();
    }

    boolean isPhoenixTracked() {
        return phoenixIndicatorSeen || megaWallsClass == MegaWallsClass.PHOENIX;
    }

    boolean isStrengthActive() {
        return strengthExpiresAt > System.currentTimeMillis();
    }

    static String normalizeKey(String value) {
        return value == null || value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

    private char getRenderedTeamColor(String value) {
        if (value == null || value.length() < 2) {
            return '\0';
        }

        int profileStart = getProfileNameStart(value);
        if (profileStart >= 0) {
            return getLastTeamColor(value, profileStart);
        }

        return getFirstTeamColor(value);
    }

    private int getProfileNameStart(String value) {
        if (profileName == null || profileName.isEmpty()) {
            return -1;
        }

        return value.toLowerCase(Locale.ROOT).indexOf(
            profileName.toLowerCase(Locale.ROOT)
        );
    }

    private char getLastTeamColor(String value, int endExclusive) {
        for (int index = Math.min(endExclusive - 2, value.length() - 2); index >= 0; index--) {
            if (value.charAt(index) != '\u00a7') {
                continue;
            }

            char colorCode = Character.toLowerCase(value.charAt(index + 1));
            if (isTeamColor(colorCode)) {
                return colorCode;
            }
        }
        return '\0';
    }

    private char getFirstTeamColor(String value) {
        for (int index = 0; index < value.length() - 1; index++) {
            if (value.charAt(index) != '\u00a7') {
                continue;
            }

            char colorCode = Character.toLowerCase(value.charAt(index + 1));
            if (isTeamColor(colorCode)) {
                return colorCode;
            }
        }
        return '\0';
    }

    private boolean isTeamColor(char colorCode) {
        return colorCode == 'c' || colorCode == 'a' || colorCode == '9' || colorCode == 'e';
    }
}
