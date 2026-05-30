package megawalls.service;

import megawalls.domain.MegaWallsClass;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

final class TrackedPlayerRegistry {

    private final Map<UUID, TrackedPlayerState> trackedPlayersByUuid = new HashMap<UUID, TrackedPlayerState>();
    private final Map<String, TrackedPlayerState> trackedPlayersByName = new HashMap<String, TrackedPlayerState>();

    TrackedPlayerState resolveTrackedState(UUID playerId, String profileName, boolean create) {
        TrackedPlayerState uuidState = playerId == null ? null : trackedPlayersByUuid.get(playerId);
        String normalizedName = TrackedPlayerState.normalizeKey(profileName);
        TrackedPlayerState nameState = normalizedName == null ? null : trackedPlayersByName.get(normalizedName);

        TrackedPlayerState trackedPlayerState;
        if (uuidState != null && nameState != null && uuidState != nameState) {
            trackedPlayerState = mergeTrackedStates(uuidState, nameState);
        } else {
            trackedPlayerState = uuidState != null ? uuidState : nameState;
        }

        if (trackedPlayerState == null && !create) {
            return null;
        }

        if (trackedPlayerState == null) {
            trackedPlayerState = new TrackedPlayerState(playerId, profileName);
        }

        trackedPlayerState.bindIdentity(playerId, profileName);
        if (trackedPlayerState.sessionUuid != null) {
            trackedPlayersByUuid.put(trackedPlayerState.sessionUuid, trackedPlayerState);
        }
        if (trackedPlayerState.normalizedProfileName != null) {
            trackedPlayersByName.put(trackedPlayerState.normalizedProfileName, trackedPlayerState);
        }

        return trackedPlayerState;
    }

    void clear() {
        trackedPlayersByUuid.clear();
        trackedPlayersByName.clear();
    }

    void resetSnapshots() {
        for (TrackedPlayerState trackedPlayerState : snapshotStates()) {
            trackedPlayerState.observedDiamonds.clear();
            trackedPlayerState.resetPotions();
            trackedPlayerState.phoenixIndicatorSeen = false;
            trackedPlayerState.strengthExpiresAt = 0L;
            trackedPlayerState.lastStrengthTriggerAt = 0L;
            trackedPlayerState.lastPotionTablistHealth = Integer.MIN_VALUE;
            trackedPlayerState.maxPotionTablistHealth = Integer.MIN_VALUE;
            trackedPlayerState.potionEntityLoaded = false;
            trackedPlayerState.potionRespawnCandidate = false;
            trackedPlayerState.potionLowHealthSeen = false;
            trackedPlayerState.lastPotionUseAt = 0L;
        }
    }

    LinkedHashSet<TrackedPlayerState> snapshotTrackedStates() {
        return snapshotStates();
    }

    private TrackedPlayerState mergeTrackedStates(TrackedPlayerState primaryState, TrackedPlayerState secondaryState) {
        if (primaryState == null) {
            return secondaryState;
        }
        if (secondaryState == null || primaryState == secondaryState) {
            return primaryState;
        }

        primaryState.bindIdentity(secondaryState.sessionUuid, secondaryState.profileName);
        primaryState.bindRenderedName(secondaryState.lastRenderedName);
        if (primaryState.teamColor == '\0') {
            primaryState.teamColor = secondaryState.teamColor;
        }
        if (primaryState.megaWallsClass == null || secondaryState.megaWallsClass == MegaWallsClass.PHOENIX) {
            primaryState.megaWallsClass =
                    secondaryState.megaWallsClass == null ? primaryState.megaWallsClass : secondaryState.megaWallsClass;
        }
        primaryState.observedDiamonds.addAll(secondaryState.observedDiamonds);
        mergePotionState(primaryState, secondaryState);
        primaryState.lastPotionTablistHealth =
                secondaryState.lastPotionTablistHealth != Integer.MIN_VALUE
                        ? secondaryState.lastPotionTablistHealth
                        : primaryState.lastPotionTablistHealth;
        primaryState.maxPotionTablistHealth = Math.max(
                primaryState.maxPotionTablistHealth,
                secondaryState.maxPotionTablistHealth
        );
        primaryState.potionRespawnCandidate |= secondaryState.potionRespawnCandidate;
        primaryState.potionLowHealthSeen |= secondaryState.potionLowHealthSeen;
        primaryState.potionEntityLoaded |= secondaryState.potionEntityLoaded;
        primaryState.phoenixIndicatorSeen |= secondaryState.phoenixIndicatorSeen;
        primaryState.lastPotionUseAt = Math.max(primaryState.lastPotionUseAt, secondaryState.lastPotionUseAt);
        primaryState.strengthExpiresAt = Math.max(primaryState.strengthExpiresAt, secondaryState.strengthExpiresAt);
        primaryState.lastStrengthTriggerAt = Math.max(primaryState.lastStrengthTriggerAt, secondaryState.lastStrengthTriggerAt);

        if (secondaryState.sessionUuid != null) {
            trackedPlayersByUuid.put(secondaryState.sessionUuid, primaryState);
        }
        if (secondaryState.normalizedProfileName != null) {
            trackedPlayersByName.put(secondaryState.normalizedProfileName, primaryState);
        }

        return primaryState;
    }

    private void mergePotionState(TrackedPlayerState primaryState, TrackedPlayerState secondaryState) {
        if (primaryState == null || secondaryState == null || secondaryState.potionClass == null) {
            return;
        }

        if (primaryState.potionClass == null || primaryState.remainingPotions < 0) {
            copyPotionState(primaryState, secondaryState);
            return;
        }

        if (secondaryState.lastPotionUseAt > primaryState.lastPotionUseAt) {
            copyPotionState(primaryState, secondaryState);
            return;
        }

        if (
            secondaryState.potionClass == primaryState.potionClass &&
            secondaryState.remainingPotions >= 0 &&
            (primaryState.remainingPotions < 0 ||
                secondaryState.remainingPotions < primaryState.remainingPotions)
        ) {
            copyPotionState(primaryState, secondaryState);
            return;
        }

        if (!primaryState.hasPotionSample && secondaryState.hasPotionSample) {
            copyPotionState(primaryState, secondaryState);
        }
    }

    private void copyPotionState(TrackedPlayerState targetState, TrackedPlayerState sourceState) {
        if (targetState == null || sourceState == null) {
            return;
        }

        targetState.potionClass = sourceState.potionClass;
        targetState.remainingPotions = sourceState.remainingPotions;
        targetState.hasPotionSample = sourceState.hasPotionSample;
        targetState.lastHealth = sourceState.lastHealth;
        targetState.wasAlive = sourceState.wasAlive;
        targetState.maxPotionTablistHealth = sourceState.maxPotionTablistHealth;
        targetState.potionEntityLoaded = sourceState.potionEntityLoaded;
        targetState.potionRespawnCandidate = sourceState.potionRespawnCandidate;
        targetState.potionLowHealthSeen = sourceState.potionLowHealthSeen;
    }

    private LinkedHashSet<TrackedPlayerState> snapshotStates() {
        LinkedHashSet<TrackedPlayerState> states = new LinkedHashSet<TrackedPlayerState>();
        states.addAll(trackedPlayersByUuid.values());
        states.addAll(trackedPlayersByName.values());
        return states;
    }
}
