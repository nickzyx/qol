package megawalls.service;

import megawalls.domain.MegaWallsClass;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class MegaWallsContextService {

    private static final String DEATHMATCH_START_MESSAGE =
            "All withers are dead! 1 second until deathmatch!";

    private WorldClient trackedWorld;
    private boolean inMegaWalls;
    private boolean trackingActive;
    private boolean deathmatchActive;
    private char localTeamColor;

    boolean syncWorld(WorldClient world) {
        if (world == trackedWorld) {
            return false;
        }

        trackedWorld = world;
        inMegaWalls = false;
        trackingActive = false;
        deathmatchActive = false;
        localTeamColor = '\0';
        return true;
    }

    void updateSidebarState(WorldClient world, MegaWallsClassResolver classResolver) {
        if (world == null) {
            inMegaWalls = false;
            trackingActive = false;
            deathmatchActive = false;
            localTeamColor = '\0';
            return;
        }

        Scoreboard scoreboard = world.getScoreboard();
        List<String> sidebarLines = getSidebarLines(scoreboard);
        ScoreObjective sidebarObjective = scoreboard == null ? null : scoreboard.getObjectiveInDisplaySlot(1);
        String formattedSidebarTitle = sidebarObjective == null ? "" : sidebarObjective.getDisplayName();
        String sidebarTitle = classResolver.stripFormatting(formattedSidebarTitle);

        StringBuilder haystack = new StringBuilder(sidebarTitle);
        for (String sidebarLine : sidebarLines) {
            haystack.append(' ').append(classResolver.stripFormatting(sidebarLine));
        }

        String sidebarText = haystack.toString();
        String upperSidebarText = sidebarText.toUpperCase(Locale.ROOT);
        String normalized = classResolver.normalize(sidebarText);
        MegaWallsClass localClass = classResolver.resolveLocalClass();
        boolean megaWallsSidebar = upperSidebarText.contains("MEGA WALLS");
        boolean gameSidebar = megaWallsSidebar;
        boolean lobbySidebar = normalized.contains("WINS");
        if (lobbySidebar) {
            inMegaWalls = false;
            trackingActive = false;
            deathmatchActive = false;
            localTeamColor = '\0';
            return;
        }

        inMegaWalls = gameSidebar || localClass != null;
        if (gameSidebar) {
            trackingActive = true;
            localTeamColor = getLastColorCode(formattedSidebarTitle);
        }
    }

    boolean isInMegaWalls() {
        return inMegaWalls;
    }

    boolean isDeathmatchActive() {
        return deathmatchActive;
    }

    boolean isTrackingActive() {
        return trackingActive;
    }

    char getLocalTeamColor() {
        return localTeamColor;
    }

    void observeChatMessage(String message, MegaWallsClassResolver classResolver) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String strippedMessage = classResolver == null
                ? message
                : classResolver.stripFormatting(message);
        if (DEATHMATCH_START_MESSAGE.equals(strippedMessage)) {
            deathmatchActive = true;
        }
    }

    private List<String> getSidebarLines(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return Collections.emptyList();
        }

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return Collections.emptyList();
        }

        Collection<Score> sortedScores = scoreboard.getSortedScores(objective);
        List<Score> filteredScores = new ArrayList<Score>();
        for (Score score : sortedScores) {
            if (score != null && score.getPlayerName() != null && !score.getPlayerName().startsWith("#")) {
                filteredScores.add(score);
            }
        }

        if (filteredScores.size() > 15) {
            filteredScores = filteredScores.subList(filteredScores.size() - 15, filteredScores.size());
        }

        Collections.reverse(filteredScores);
        List<String> lines = new ArrayList<String>();
        for (Score score : filteredScores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            lines.add(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName()));
        }
        return lines;
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
