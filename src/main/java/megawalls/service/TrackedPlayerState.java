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
    MegaWallsClass megaWallsClass;
    final EnumSet<ObservedDiamond> observedDiamonds = EnumSet.noneOf(ObservedDiamond.class);
    MegaWallsClass potionClass;
    int remainingPotions = -1;
    boolean hasPotionSample;
    float lastHealth;
    boolean wasAlive;
    int lastPotionTablistHealth = Integer.MIN_VALUE;
    long recentPotionTablistIncreaseUntil;
    boolean phoenixIndicatorSeen;
    long healingPotionHoldStartedAt;
    long healingPotionHoldGraceUntil;
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
        }
    }

    void resetPotions() {
        potionClass = null;
        remainingPotions = -1;
        hasPotionSample = false;
        lastHealth = 0.0F;
        wasAlive = false;
        lastPotionTablistHealth = Integer.MIN_VALUE;
        recentPotionTablistIncreaseUntil = 0L;
        healingPotionHoldStartedAt = 0L;
        healingPotionHoldGraceUntil = 0L;
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
}
