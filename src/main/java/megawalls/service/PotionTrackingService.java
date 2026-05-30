package megawalls.service;

import java.util.Map;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;

final class PotionTrackingService {

    private static final int RESPAWN_LOW_HEALTH_THRESHOLD = 2;
    private static final long HEALTH_BASELINE_GRACE_MS = 5000L;
    private static final long HEALTH_MISSING_LOG_INTERVAL_MS = 5000L;
    private static final int SIX_HEART_PARTIAL_MIN_GAIN = 10;
    private static final int TEN_HEART_ONE_POT_MIN_GAIN = 12;
    private static final int MIN_BURST_HEAL_GAIN = 3;
    private static final long HEAL_BURST_WINDOW_MS = 900L;
    private static final int ABSORPTION_HEALTH_BONUS = 8;

    private final MegaWallsContextService contextService;
    private final DeveloperDebugService debugService;

    PotionTrackingService(
        MegaWallsContextService contextService,
        DeveloperDebugService debugService
    ) {
        this.contextService = contextService;
        this.debugService = debugService;
    }

    void updateLiveState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player
    ) {
        if (player == null) {
            return;
        }

        updateLiveState(trackedPlayerState, player, player.getHealth());
    }

    void updateLiveState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player,
        float currentHealth
    ) {
        if (trackedPlayerState == null || player == null) {
            return;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        MegaWallsClass megaWallsClass = trackedPlayerState.megaWallsClass;
        if (
            config == null ||
            !config.potionDetectorEnabled ||
            !canUsePotion(config) ||
            megaWallsClass == null ||
            megaWallsClass.getPotionCount() < 0
        ) {
            trackedPlayerState.resetPotions();
            return;
        }

        boolean alive = !player.isDead && currentHealth > 0.0F;

        if (!ensureTrackingState(trackedPlayerState, megaWallsClass)) {
            return;
        }

        if (!trackedPlayerState.hasPotionSample) {
            trackedPlayerState.lastHealth = currentHealth;
            trackedPlayerState.hasPotionSample = true;
            trackedPlayerState.wasAlive = alive;
            return;
        }

        if (!trackedPlayerState.wasAlive && alive) {
            trackedPlayerState.lastHealth = currentHealth;
            trackedPlayerState.wasAlive = alive;
            return;
        }

        trackedPlayerState.lastHealth = currentHealth;
        trackedPlayerState.wasAlive = alive;
    }

    Integer lookupHealth(
        Scoreboard scoreboard,
        String profileName,
        EntityPlayer playerEntity
    ) {
        if (
            scoreboard == null || profileName == null || profileName.isEmpty()
        ) {
            return null;
        }

        Integer tablistHealth = lookupObjectiveScore(
            scoreboard,
            profileName,
            0
        );
        if (tablistHealth != null || contextService.isDeathmatchActive()) {
            return tablistHealth;
        }

        // Before deathmatch, Hypixel can hide tablist health while still showing
        // the same health value below nearby player nametags.
        return playerEntity == null
            ? null
            : lookupObjectiveScore(scoreboard, profileName, 2);
    }

    void observeScoreboardHealth(
        TrackedPlayerState trackedPlayerState,
        Integer scoreboardHealth,
        boolean playerEntityLoaded
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (trackedPlayerState == null) {
            return;
        }

        if (scoreboardHealth == null) {
            long now = System.currentTimeMillis();
            boolean keepRecentBaseline =
                trackedPlayerState.lastPotionTablistHealth != Integer.MIN_VALUE &&
                    trackedPlayerState.lastPotionScoreboardHealthAt > 0L &&
                    trackedPlayerState.lastPotionScoreboardHealthAt +
                        HEALTH_BASELINE_GRACE_MS >= now;
            if (!keepRecentBaseline) {
                trackedPlayerState.lastPotionTablistHealth = Integer.MIN_VALUE;
            }
            observeEntityPresence(trackedPlayerState, playerEntityLoaded);
            if (
                shouldLogMissingHealth(
                    trackedPlayerState,
                    playerEntityLoaded,
                    now
                )
            ) {
                debugService.logPotionHealthMissing(
                    trackedPlayerState,
                    playerEntityLoaded,
                    keepRecentBaseline
                );
            }
            return;
        }

        if (
            config == null ||
            !config.potionDetectorEnabled ||
            !canUsePotion(config)
        ) {
            return;
        }

        int rawCurrentHealth = scoreboardHealth.intValue();
        int currentHealth = normalizeScoreboardHealth(rawCurrentHealth);
        // Entity presence is used as a respawn hint because players often vanish
        // briefly before reappearing at full health.
        observeEntityPresence(trackedPlayerState, playerEntityLoaded);
        MegaWallsClass megaWallsClass = trackedPlayerState.megaWallsClass;
        if (!ensureTrackingState(trackedPlayerState, megaWallsClass)) {
            trackedPlayerState.lastPotionTablistHealth = currentHealth;
            trackedPlayerState.lastPotionScoreboardHealthAt =
                System.currentTimeMillis();
            observeRespawnCandidate(trackedPlayerState, currentHealth);
            updateMaxScoreboardHealth(trackedPlayerState, currentHealth);
            debugService.logPotionObservation(
                trackedPlayerState,
                megaWallsClass,
                null,
                currentHealth,
                0,
                "tracking-state-not-ready",
                false
            );
            return;
        }

        // Respawn jumps also look like large heals, so handle them before potion
        // decrement logic.
        if (isRespawnReset(trackedPlayerState, currentHealth)) {
            debugService.logPotionObservation(
                trackedPlayerState,
                megaWallsClass,
                Integer.valueOf(trackedPlayerState.lastPotionTablistHealth),
                currentHealth,
                currentHealth - trackedPlayerState.lastPotionTablistHealth,
                "respawn-reset",
                false
            );
            resetToClassDefault(trackedPlayerState, megaWallsClass);
            trackedPlayerState.lastPotionTablistHealth = currentHealth;
            trackedPlayerState.lastPotionScoreboardHealthAt =
                System.currentTimeMillis();
            updateMaxScoreboardHealth(trackedPlayerState, currentHealth);
            return;
        }

        clearStaleRespawnCandidate(trackedPlayerState, currentHealth);

        if (trackedPlayerState.lastPotionTablistHealth == Integer.MIN_VALUE) {
            debugService.logPotionObservation(
                trackedPlayerState,
                megaWallsClass,
                null,
                currentHealth,
                0,
                "missing-previous-health-sample",
                false
            );
        } else if (trackedPlayerState.lastPotionTablistHealth <= 0) {
            int healthGain =
                currentHealth - trackedPlayerState.lastPotionTablistHealth;
            boolean potionHealthGain =
                trackedPlayerState.remainingPotions > 0 &&
                    isPotionHealthGain(
                        trackedPlayerState,
                        megaWallsClass,
                        healthGain,
                        currentHealth
                    );
            String potionReason = potionHealthGain
                ? buildPotionReason(
                    trackedPlayerState,
                    megaWallsClass,
                    healthGain,
                    currentHealth,
                    true
                )
                : "previous-health-not-positive";
            debugService.logPotionObservation(
                trackedPlayerState,
                megaWallsClass,
                Integer.valueOf(trackedPlayerState.lastPotionTablistHealth),
                currentHealth,
                healthGain,
                potionReason,
                potionHealthGain
            );
            if (potionHealthGain) {
                recordPotionUse(trackedPlayerState, config);
                resetHealBurst(trackedPlayerState);
            }
        } else if (trackedPlayerState.remainingPotions <= 0) {
            int healthGain =
                currentHealth - trackedPlayerState.lastPotionTablistHealth;
            if (healthGain > 0) {
                debugService.logPotionObservation(
                    trackedPlayerState,
                    megaWallsClass,
                    Integer.valueOf(trackedPlayerState.lastPotionTablistHealth),
                    currentHealth,
                    healthGain,
                    "no-remaining-potions",
                    false
                );
            }
        } else {
            int healthGain =
                currentHealth - trackedPlayerState.lastPotionTablistHealth;
            boolean potionHealthGain = isPotionHealthGain(
                trackedPlayerState,
                megaWallsClass,
                healthGain,
                currentHealth
            );
            String potionReason = buildPotionReason(
                trackedPlayerState,
                megaWallsClass,
                healthGain,
                currentHealth,
                potionHealthGain
            );
            if (!potionHealthGain) {
                potionHealthGain = observeHealBurst(
                    trackedPlayerState,
                    megaWallsClass,
                    healthGain,
                    currentHealth
                );
                if (potionHealthGain) {
                    potionReason = buildPotionReason(
                        trackedPlayerState,
                        megaWallsClass,
                        healthGain,
                        currentHealth,
                        true
                    ) + " burstGain=" + trackedPlayerState.potionHealBurstGain;
                }
            }
            if (healthGain != 0) {
                debugService.logPotionObservation(
                    trackedPlayerState,
                    megaWallsClass,
                    Integer.valueOf(trackedPlayerState.lastPotionTablistHealth),
                    currentHealth,
                    healthGain,
                    potionReason,
                    potionHealthGain
                );
            }
            if (potionHealthGain) {
                recordPotionUse(trackedPlayerState, config);
                resetHealBurst(trackedPlayerState);
            }
        }

        trackedPlayerState.lastPotionTablistHealth = currentHealth;
        trackedPlayerState.lastPotionScoreboardHealthAt =
            System.currentTimeMillis();
        observeRespawnCandidate(trackedPlayerState, currentHealth);
        updateMaxScoreboardHealth(trackedPlayerState, currentHealth);
    }

    private boolean shouldLogMissingHealth(
        TrackedPlayerState trackedPlayerState,
        boolean playerEntityLoaded,
        long now
    ) {
        if (trackedPlayerState == null) {
            return false;
        }

        boolean entityLoadedChanged =
            trackedPlayerState.lastPotionHealthMissingLogAt == 0L ||
                trackedPlayerState.lastPotionHealthMissingEntityLoaded !=
                    playerEntityLoaded;
        boolean intervalElapsed =
            trackedPlayerState.lastPotionHealthMissingLogAt +
                HEALTH_MISSING_LOG_INTERVAL_MS <= now;
        if (!entityLoadedChanged && !intervalElapsed) {
            return false;
        }

        trackedPlayerState.lastPotionHealthMissingLogAt = now;
        trackedPlayerState.lastPotionHealthMissingEntityLoaded =
            playerEntityLoaded;
        return true;
    }

    private Integer lookupObjectiveScore(
        Scoreboard scoreboard,
        String profileName,
        int displaySlot
    ) {
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(
            displaySlot
        );
        if (objective == null) {
            return null;
        }

        Map<ScoreObjective, Score> playerScores =
            scoreboard.getObjectivesForEntity(profileName);
        if (playerScores == null || !playerScores.containsKey(objective)) {
            return null;
        }

        Score score = playerScores.get(objective);
        return score == null ? null : Integer.valueOf(score.getScorePoints());
    }

    private boolean ensureTrackingState(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass
    ) {
        if (
            trackedPlayerState == null ||
            megaWallsClass == null ||
            megaWallsClass.getPotionCount() < 0
        ) {
            return false;
        }

        if (trackedPlayerState.potionClass != megaWallsClass) {
            trackedPlayerState.potionClass = megaWallsClass;
            trackedPlayerState.remainingPotions =
                megaWallsClass.getPotionCount();
        }
        if (trackedPlayerState.remainingPotions < 0) {
            trackedPlayerState.remainingPotions =
                megaWallsClass.getPotionCount();
        }

        return true;
    }

    private void observeEntityPresence(
        TrackedPlayerState trackedPlayerState,
        boolean playerEntityLoaded
    ) {
        if (trackedPlayerState == null) {
            return;
        }

        if (trackedPlayerState.potionEntityLoaded && !playerEntityLoaded) {
            trackedPlayerState.potionRespawnCandidate = true;
        }
        trackedPlayerState.potionEntityLoaded = playerEntityLoaded;
    }

    private void observeRespawnCandidate(
        TrackedPlayerState trackedPlayerState,
        int currentHealth
    ) {
        if (
            trackedPlayerState != null &&
            currentHealth > 0 &&
            currentHealth <= RESPAWN_LOW_HEALTH_THRESHOLD
        ) {
            trackedPlayerState.potionLowHealthSeen = true;
        }
    }

    private boolean isRespawnReset(
        TrackedPlayerState trackedPlayerState,
        int currentHealth
    ) {
        return (
            trackedPlayerState != null &&
            canPlayerRespawn(trackedPlayerState) &&
            trackedPlayerState.potionRespawnCandidate &&
            trackedPlayerState.potionLowHealthSeen &&
            isFullMegaWallsHealth(currentHealth)
        );
    }

    private boolean canPlayerRespawn(TrackedPlayerState trackedPlayerState) {
        if (contextService.isDeathmatchActive()) {
            return false;
        }

        return (
            trackedPlayerState == null ||
            trackedPlayerState.teamColor == '\0' ||
            !contextService.isTeamWitherDead(trackedPlayerState.teamColor)
        );
    }

    private void resetToClassDefault(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass
    ) {
        if (
            trackedPlayerState == null ||
            megaWallsClass == null ||
            megaWallsClass.getPotionCount() < 0
        ) {
            return;
        }

        trackedPlayerState.potionClass = megaWallsClass;
        trackedPlayerState.remainingPotions = megaWallsClass.getPotionCount();
        trackedPlayerState.potionRespawnCandidate = false;
        trackedPlayerState.potionLowHealthSeen = false;
        resetHealBurst(trackedPlayerState);
    }

    private void clearStaleRespawnCandidate(
        TrackedPlayerState trackedPlayerState,
        int currentHealth
    ) {
        if (
            trackedPlayerState == null ||
            !trackedPlayerState.potionRespawnCandidate ||
            isFullMegaWallsHealth(currentHealth)
        ) {
            return;
        }

        trackedPlayerState.potionRespawnCandidate = false;
        trackedPlayerState.potionLowHealthSeen = false;
    }

    private boolean isPotionHealthGain(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass,
        int healthGain,
        int currentHealth
    ) {
        if (megaWallsClass == null || healthGain <= 0) {
            return false;
        }

        int[] expectedPotionHealthValues =
            getExpectedPotionHealthValues(megaWallsClass);
        if (expectedPotionHealthValues.length == 0) {
            return false;
        }

        for (int expectedPotionHealth : expectedPotionHealthValues) {
            if (Math.abs(healthGain - expectedPotionHealth) <= 1) {
                return true;
            }
        }

        if (isLooseOnePotTenHeartPotionGain(megaWallsClass, healthGain)) {
            return true;
        }

        // 6-heart potions overlap with Phoenix bond/heal values. Reject the
        // most ambiguous +6/+7 gains, but still count larger partial gains so
        // Squid/Automaton potions can track when they do not cap at full HP.
        if (
            megaWallsClass == MegaWallsClass.SQUID ||
            megaWallsClass == MegaWallsClass.AUTOMATON
        ) {
            return isSixHeartPartialPotionHealthGain(healthGain);
        }

        // A potion used near full health may cap at 40/44 HP, so the visible
        // gain can be much smaller than the potion's real heal amount.
        for (int expectedPotionHealth : expectedPotionHealthValues) {
            if (
                isCappedPotionHealthGain(
                    trackedPlayerState,
                    expectedPotionHealth,
                    healthGain,
                    currentHealth
                )
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean observeHealBurst(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass,
        int healthGain,
        int currentHealth
    ) {
        if (
            trackedPlayerState == null ||
            megaWallsClass == null ||
            healthGain < MIN_BURST_HEAL_GAIN
        ) {
            resetHealBurst(trackedPlayerState);
            return false;
        }

        long now = System.currentTimeMillis();
        if (
            trackedPlayerState.potionHealBurstLastAt == 0L ||
            trackedPlayerState.potionHealBurstLastAt + HEAL_BURST_WINDOW_MS < now
        ) {
            trackedPlayerState.potionHealBurstGain = 0;
            trackedPlayerState.potionHealBurstStartedAt = now;
        }

        trackedPlayerState.potionHealBurstGain += healthGain;
        trackedPlayerState.potionHealBurstLastAt = now;

        return isPotionHealthGain(
            trackedPlayerState,
            megaWallsClass,
            trackedPlayerState.potionHealBurstGain,
            currentHealth
        );
    }

    private void resetHealBurst(TrackedPlayerState trackedPlayerState) {
        if (trackedPlayerState == null) {
            return;
        }

        trackedPlayerState.potionHealBurstGain = 0;
        trackedPlayerState.potionHealBurstStartedAt = 0L;
        trackedPlayerState.potionHealBurstLastAt = 0L;
    }

    private boolean isSixHeartPartialPotionHealthGain(int healthGain) {
        return healthGain >= SIX_HEART_PARTIAL_MIN_GAIN && healthGain < 12;
    }

    private boolean isLooseOnePotTenHeartPotionGain(
        MegaWallsClass megaWallsClass,
        int healthGain
    ) {
        return (
            megaWallsClass != null &&
            megaWallsClass.getPotionCount() == 1 &&
            Math.round(megaWallsClass.getPotionHealth()) == 20L &&
            healthGain >= TEN_HEART_ONE_POT_MIN_GAIN &&
            healthGain < 20
        );
    }

    private int[] getExpectedPotionHealthValues(MegaWallsClass megaWallsClass) {
        if (megaWallsClass == null) {
            return new int[0];
        }

        int expectedPotionHealth = (int) Math.round(
            megaWallsClass.getPotionHealth()
        );
        if (expectedPotionHealth <= 0) {
            return new int[0];
        }

        return new int[] {expectedPotionHealth};
    }

    private boolean isCappedPotionHealthGain(
        TrackedPlayerState trackedPlayerState,
        int expectedPotionHealth,
        int healthGain,
        int currentHealth
    ) {
        if (trackedPlayerState == null || healthGain >= expectedPotionHealth) {
            return false;
        }

        if (!isFullMegaWallsHealth(currentHealth)) {
            return false;
        }

        int minimumCappedGain = Math.max(
            8,
            (expectedPotionHealth * 3 + 3) / 4
        );
        return healthGain >= minimumCappedGain;
    }

    private String buildPotionReason(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass,
        int healthGain,
        int currentHealth,
        boolean accepted
    ) {
        int[] expectedPotionHealthValues =
            getExpectedPotionHealthValues(megaWallsClass);
        StringBuilder reason = new StringBuilder();
        reason.append("expected=");
        for (int index = 0; index < expectedPotionHealthValues.length; index++) {
            if (index > 0) {
                reason.append(',');
            }
            reason.append(expectedPotionHealthValues[index]);
        }
        reason.append(" fullHealth=").append(isFullMegaWallsHealth(currentHealth));
        reason.append(" accepted=").append(accepted);
        if (
            megaWallsClass == MegaWallsClass.SQUID ||
            megaWallsClass == MegaWallsClass.AUTOMATON
        ) {
            reason.append(" sixHeartPartial=");
            reason.append(isSixHeartPartialPotionHealthGain(healthGain));
        }
        if (
            expectedPotionHealthValues.length == 1 &&
            expectedPotionHealthValues[0] == 20 &&
            megaWallsClass != null &&
            megaWallsClass.getPotionCount() == 1
        ) {
            reason.append(" tenHeartPartial=");
            reason.append(isLooseOnePotTenHeartPotionGain(megaWallsClass, healthGain));
        }
        if (healthGain <= 0) {
            reason.append(" nonPositiveGain=true");
        }
        return reason.toString();
    }

    private boolean isFullMegaWallsHealth(int health) {
        return health == 40 || health == 44;
    }

    private int normalizeScoreboardHealth(int health) {
        return health > 44 ? health - ABSORPTION_HEALTH_BONUS : health;
    }

    private void updateMaxScoreboardHealth(
        TrackedPlayerState trackedPlayerState,
        int currentHealth
    ) {
        if (trackedPlayerState == null || currentHealth <= 0) {
            return;
        }

        trackedPlayerState.maxPotionTablistHealth = Math.max(
            trackedPlayerState.maxPotionTablistHealth,
            currentHealth
        );
    }

    private void recordPotionUse(
        TrackedPlayerState trackedPlayerState,
        MegaWallsConfig config
    ) {
        if (
            trackedPlayerState == null ||
            trackedPlayerState.remainingPotions <= 0
        ) {
            return;
        }

        trackedPlayerState.remainingPotions--;
        trackedPlayerState.lastPotionUseAt = System.currentTimeMillis();
        if (config.potionDebug) {
            ChatNotifier.info(
                aquaName(
                    trackedPlayerState.profileName,
                    EnumChatFormatting.WHITE
                ) +
                    " used a healing potion. Remaining: " +
                    redValue(
                        trackedPlayerState.remainingPotions,
                        EnumChatFormatting.WHITE
                    )
            );
        }
    }

    private boolean canUsePotion(MegaWallsConfig config) {
        return (
            config != null &&
            config.canUsePotion(contextService.isDeathmatchActive())
        );
    }

    private String aquaName(
        String playerName,
        EnumChatFormatting trailingColor
    ) {
        return (
            EnumChatFormatting.AQUA.toString() +
            playerName +
            trailingColor.toString()
        );
    }

    private String redValue(int value, EnumChatFormatting trailingColor) {
        return (
            EnumChatFormatting.RED.toString() + value + trailingColor.toString()
        );
    }
}
