package megawalls.service;

import java.util.HashMap;
import java.util.Map;

final class PhoenixResurrectionRegistry {

    private final Map<String, PhoenixResurrectionState> statesByName =
        new HashMap<String, PhoenixResurrectionState>();

    PhoenixResurrectionState resolveState(String profileName, boolean create) {
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
}
