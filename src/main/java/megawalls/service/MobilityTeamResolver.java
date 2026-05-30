package megawalls.service;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

final class MobilityTeamResolver {

    private final MegaWallsContextService contextService;

    MobilityTeamResolver(MegaWallsContextService contextService) {
        this.contextService = contextService;
    }

    boolean hasLocalTeamColor(EntityPlayer otherPlayer) {
        if (
            otherPlayer == null ||
            otherPlayer.worldObj == null
        ) {
            return false;
        }

        char localTeamColor = contextService.getLocalTeamColor();
        if (localTeamColor == '\0') {
            return false;
        }

        Scoreboard scoreboard = otherPlayer.worldObj.getScoreboard();
        if (scoreboard == null) {
            return false;
        }

        ScorePlayerTeam otherTeam = scoreboard.getPlayersTeam(otherPlayer.getName());
        if (otherTeam == null) {
            return false;
        }

        char otherColor = getTeamColor(otherTeam);
        return otherColor != '\0' && otherColor == localTeamColor;
    }

    private char getTeamColor(ScorePlayerTeam team) {
        char prefixColor = getLastColorCode(team.getColorPrefix());
        return prefixColor != '\0'
            ? prefixColor
            : getLastColorCode(team.getColorSuffix());
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
