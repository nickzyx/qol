package megawalls.api;

import megawalls.domain.DiamondGear;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlayerStateView {

    private final UUID playerId;
    private final String profileName;
    private final boolean phoenixClass;
    private final boolean phoenixResurrectionAvailable;
    private final List<DiamondGear> diamondGear;
    private final int potionCount;
    private final boolean strength;

    public PlayerStateView(
            UUID playerId,
            String profileName,
            boolean phoenixClass,
            boolean phoenixResurrectionAvailable,
            List<DiamondGear> diamondGear,
            int potionCount,
            boolean strength
    ) {
        this.playerId = playerId;
        this.profileName = profileName;
        this.phoenixClass = phoenixClass;
        this.phoenixResurrectionAvailable = phoenixResurrectionAvailable;
        this.diamondGear = diamondGear == null
                ? Collections.<DiamondGear>emptyList()
                : Collections.unmodifiableList(diamondGear);
        this.potionCount = potionCount;
        this.strength = strength;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getProfileName() {
        return profileName;
    }

    public boolean isPhoenixClass() {
        return phoenixClass;
    }

    public boolean isPhoenixResurrectionAvailable() {
        return phoenixResurrectionAvailable;
    }

    public List<DiamondGear> getDiamondGear() {
        return diamondGear;
    }

    public int getPotionCount() {
        return potionCount;
    }

    public boolean hasStrength() {
        return strength;
    }
}
