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

public final class MegaWallsContextService {

    private WorldClient trackedWorld;
    private boolean inMegaWallsLobby;
    private boolean inPreGameQueue;
    private boolean inMegaWallsGame;
    private boolean trackingActive;
    private boolean wallsFallenActive;
    private boolean deathmatchActive;
    private char localTeamColor;
    private boolean redWitherDead;
    private boolean greenWitherDead;
    private boolean blueWitherDead;
    private boolean yellowWitherDead;

    boolean syncWorld(WorldClient world) {
        if (world == trackedWorld) {
            return false;
        }

        trackedWorld = world;
        clearContextState();
        return true;
    }

    void updateSidebarState(WorldClient world, MegaWallsClassResolver classResolver) {
        if (world == null) {
            clearContextState();
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
        boolean lobbySidebar = megaWallsSidebar && isMegaWallsLobbySidebar(normalized);
        boolean preGameQueueSidebar = megaWallsSidebar && isPreGameQueueSidebar(normalized);
        boolean gameSidebar = megaWallsSidebar && !lobbySidebar && !preGameQueueSidebar;
        boolean witherSidebar = sidebarText.contains("Wither");
        boolean preWallsSidebar = upperSidebarText.contains("WALLS FALL") ||
            normalized.contains("WALLSFALL");

        if (lobbySidebar) {
            clearContextState();
            inMegaWallsLobby = true;
            return;
        }

        if (preGameQueueSidebar) {
            clearContextState();
            inPreGameQueue = true;
            return;
        }

        inMegaWallsLobby = false;
        inPreGameQueue = false;
        inMegaWallsGame = gameSidebar || localClass != null;
        trackingActive = false;
        wallsFallenActive = false;
        deathmatchActive = false;
        localTeamColor = '\0';
        clearTeamWitherState();
        if (gameSidebar) {
            trackingActive = true;
            wallsFallenActive = !preWallsSidebar;
            localTeamColor = getLastColorCode(formattedSidebarTitle);
            deathmatchActive = !witherSidebar && !preWallsSidebar;
            updateTeamWitherState(sidebarLines, classResolver);
        }
    }

    public boolean isInMegaWallsLobby() {
        return inMegaWallsLobby;
    }

    public boolean isInPreGameQueue() {
        return inPreGameQueue;
    }

    public boolean isInMegaWallsGame() {
        return inMegaWallsGame;
    }

    public boolean isDeathmatchActive() {
        return deathmatchActive;
    }

    public boolean isWallsFallenActive() {
        return wallsFallenActive;
    }

    public boolean isTrackingActive() {
        return trackingActive;
    }

    char getLocalTeamColor() {
        return localTeamColor;
    }

    boolean isTeamWitherDead(char teamColor) {
        switch (Character.toLowerCase(teamColor)) {
            case 'c':
                return redWitherDead;
            case 'a':
                return greenWitherDead;
            case '9':
                return blueWitherDead;
            case 'e':
                return yellowWitherDead;
            default:
                return false;
        }
    }

    void observeChatMessage(String message, MegaWallsClassResolver classResolver) {
        // Deathmatch is inferred from the active sidebar instead.
    }

    private boolean isMegaWallsLobbySidebar(String normalizedSidebarText) {
        return normalizedSidebarText.contains("WINS");
    }

    private boolean isPreGameQueueSidebar(String normalizedSidebarText) {
        return normalizedSidebarText.contains("MAP");
    }

    private void clearContextState() {
        inMegaWallsLobby = false;
        inPreGameQueue = false;
        inMegaWallsGame = false;
        trackingActive = false;
        wallsFallenActive = false;
        deathmatchActive = false;
        localTeamColor = '\0';
        clearTeamWitherState();
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

    private void updateTeamWitherState(
        List<String> sidebarLines,
        MegaWallsClassResolver classResolver
    ) {
        clearTeamWitherState();
        for (String sidebarLine : sidebarLines) {
            String normalizedLine = classResolver
                .stripFormatting(sidebarLine)
                .replace("?", "")
                .toUpperCase(Locale.ROOT);
            observeTeamSidebarLine(normalizedLine, "[R]", 'c');
            observeTeamSidebarLine(normalizedLine, "[G]", 'a');
            observeTeamSidebarLine(normalizedLine, "[B]", '9');
            observeTeamSidebarLine(normalizedLine, "[Y]", 'e');
        }
    }

    private void observeTeamSidebarLine(
        String normalizedLine,
        String teamToken,
        char teamColor
    ) {
        if (normalizedLine == null || !normalizedLine.contains(teamToken)) {
            return;
        }

        if (normalizedLine.contains("WITHER")) {
            setTeamWitherDead(teamColor, false);
            return;
        }

        if (normalizedLine.contains("PLAYERS")) {
            setTeamWitherDead(teamColor, true);
        }
    }

    private void setTeamWitherDead(char teamColor, boolean witherDead) {
        switch (Character.toLowerCase(teamColor)) {
            case 'c':
                redWitherDead = witherDead;
                break;
            case 'a':
                greenWitherDead = witherDead;
                break;
            case '9':
                blueWitherDead = witherDead;
                break;
            case 'e':
                yellowWitherDead = witherDead;
                break;
            default:
                break;
        }
    }

    private void clearTeamWitherState() {
        redWitherDead = false;
        greenWitherDead = false;
        blueWitherDead = false;
        yellowWitherDead = false;
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
