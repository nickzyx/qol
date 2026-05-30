package megawalls.service;

import java.util.HashMap;
import java.util.Map;

final class PhoenixResurrectionRegistry {

    private final Map<String, PhoenixResurrectionState> statesByName =
        new HashMap<String, PhoenixResurrectionState>();

    boolean isResurrectionAvailable(String profileName) {
        PhoenixResurrectionState state = resolveState(profileName, false);
        return state == null || state.resurrectionAvailable;
    }

    boolean markResurrectionUnavailable(String profileName) {
        PhoenixResurrectionState state = resolveState(profileName, true);
        if (state == null || !state.resurrectionAvailable) {
            return false;
        }

        state.resurrectionAvailable = false;
        return true;
    }

    private PhoenixResurrectionState resolveState(String profileName, boolean create) {
        String normalizedName = TrackedPlayerState.normalizeKey(profileName);
        if (normalizedName == null) {
            return null;
        }

        PhoenixResurrectionState state = statesByName.get(normalizedName);
        if (state == null && create) {
            state = new PhoenixResurrectionState();
            statesByName.put(normalizedName, state);
        }

        return state;
    }

    void clear() {
        statesByName.clear();
    }

    void reset() {
        for (PhoenixResurrectionState state : statesByName.values()) {
            if (state != null) {
                state.reset();
            }
        }
    }

    private static final class PhoenixResurrectionState {
        private boolean resurrectionAvailable = true;

        private void reset() {
            resurrectionAvailable = true;
        }
    }
}
