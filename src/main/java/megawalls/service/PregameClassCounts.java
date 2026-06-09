package megawalls.service;

import megawalls.domain.MegaWallsClass;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class PregameClassCounts {

    private static final PregameClassCounts EMPTY =
        new PregameClassCounts(
            Collections.<MegaWallsClass, Integer>emptyMap(),
            0,
            0
        );

    private final Map<MegaWallsClass, Integer> classCounts;
    private final int knownPlayers;
    private final int unknownPlayers;

    public PregameClassCounts(
        Map<MegaWallsClass, Integer> classCounts,
        int knownPlayers,
        int unknownPlayers
    ) {
        this.classCounts = copyClassCounts(classCounts);
        this.knownPlayers = knownPlayers;
        this.unknownPlayers = unknownPlayers;
    }

    public Map<MegaWallsClass, Integer> getClassCounts() {
        return classCounts;
    }

    public int getKnownPlayers() {
        return knownPlayers;
    }

    public int getUnknownPlayers() {
        return unknownPlayers;
    }

    public int getTotalPlayers() {
        return knownPlayers + unknownPlayers;
    }

    public boolean isEmpty() {
        return getTotalPlayers() <= 0;
    }

    public static PregameClassCounts empty() {
        return EMPTY;
    }

    private static Map<MegaWallsClass, Integer> copyClassCounts(
        Map<MegaWallsClass, Integer> classCounts
    ) {
        if (classCounts == null || classCounts.isEmpty()) {
            return Collections.emptyMap();
        }

        EnumMap<MegaWallsClass, Integer> copiedCounts =
            new EnumMap<MegaWallsClass, Integer>(MegaWallsClass.class);
        copiedCounts.putAll(classCounts);
        return Collections.unmodifiableMap(copiedCounts);
    }
}
