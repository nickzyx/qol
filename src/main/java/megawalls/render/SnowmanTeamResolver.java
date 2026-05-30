package megawalls.render;

import megawalls.domain.MegaWallsClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.IChatComponent;

import java.util.Locale;

public final class SnowmanTeamResolver {

    public boolean isLocalTeamSnowman(EntitySnowman snowman, char localTeamColor) {
        if (snowman == null || localTeamColor == '\0') {
            return false;
        }

        char snowmanTeamColor = getSnowmanTeamColor(snowman);
        return snowmanTeamColor != '\0' && snowmanTeamColor == localTeamColor;
    }

    private char getSnowmanTeamColor(EntitySnowman snowman) {
        Team team = snowman.getTeam();
        if (team instanceof ScorePlayerTeam) {
            ScorePlayerTeam scorePlayerTeam = (ScorePlayerTeam) team;
            char prefixColor = getLastColorCode(scorePlayerTeam.getColorPrefix());
            if (prefixColor != '\0') {
                return prefixColor;
            }

            char suffixColor = getLastColorCode(scorePlayerTeam.getColorSuffix());
            if (suffixColor != '\0') {
                return suffixColor;
            }
        }

        char nearbyNameplateColor = getNearbySnowmanNameplateColor(snowman);
        if (nearbyNameplateColor != '\0') {
            return nearbyNameplateColor;
        }

        IChatComponent displayName = snowman.getDisplayName();
        char displayColor = displayName == null
                ? '\0'
                : getFirstColorCode(displayName.getFormattedText());
        if (displayColor != '\0') {
            return displayColor;
        }

        char customNameColor = getFirstColorCode(snowman.getCustomNameTag());
        return customNameColor != '\0'
                ? customNameColor
                : getLastColorCode(snowman.getCustomNameTag());
    }

    private char getNearbySnowmanNameplateColor(EntitySnowman snowman) {
        if (snowman == null || snowman.worldObj == null) {
            return '\0';
        }

        EntityArmorStand nearestNameplate = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Object rawEntity : snowman.worldObj.loadedEntityList) {
            if (!(rawEntity instanceof EntityArmorStand)) {
                continue;
            }

            EntityArmorStand armorStand = (EntityArmorStand) rawEntity;
            double yOffset = armorStand.posY - snowman.posY;
            if (yOffset < 0.25D || yOffset > 4.0D) {
                continue;
            }

            double xOffset = armorStand.posX - snowman.posX;
            double zOffset = armorStand.posZ - snowman.posZ;
            double horizontalDistance = xOffset * xOffset + zOffset * zOffset;
            if (horizontalDistance > 2.25D || horizontalDistance >= nearestDistance) {
                continue;
            }

            String strippedName = MegaWallsClass.stripFormatting(getEntityFormattedName(armorStand));
            if (!strippedName.toUpperCase(Locale.ROOT).contains("SNOWMEN")) {
                continue;
            }

            nearestDistance = horizontalDistance;
            nearestNameplate = armorStand;
        }

        if (nearestNameplate == null) {
            return '\0';
        }

        ScorePlayerTeam team = nearestNameplate.getTeam() instanceof ScorePlayerTeam
                ? (ScorePlayerTeam) nearestNameplate.getTeam()
                : null;
        if (team != null) {
            char teamColor = getLastColorCode(team.getColorPrefix());
            if (teamColor != '\0') {
                return teamColor;
            }
        }

        String formattedName = getEntityFormattedName(nearestNameplate);
        char firstColor = getFirstColorCode(formattedName);
        return firstColor != '\0'
                ? firstColor
                : getLastColorCode(formattedName);
    }

    private String getEntityFormattedName(Entity entity) {
        if (entity == null || entity.getDisplayName() == null) {
            return "";
        }

        return entity.getDisplayName().getFormattedText();
    }

    private char getFirstColorCode(String value) {
        if (value == null || value.length() < 2) {
            return '\0';
        }

        for (int index = 0; index < value.length() - 1; index++) {
            if (value.charAt(index) != '\u00a7') {
                continue;
            }

            char colorCode = Character.toLowerCase(value.charAt(index + 1));
            if (isColorCode(colorCode)) {
                return colorCode;
            }
        }

        return '\0';
    }

    private char getLastColorCode(String value) {
        if (value == null || value.length() < 2) {
            return '\0';
        }

        char lastColor = '\0';
        for (int index = 0; index < value.length() - 1; index++) {
            if (value.charAt(index) != '\u00a7') {
                continue;
            }

            char colorCode = Character.toLowerCase(value.charAt(index + 1));
            if (isColorCode(colorCode)) {
                lastColor = colorCode;
            }
        }

        return lastColor;
    }

    private boolean isColorCode(char colorCode) {
        return (colorCode >= '0' && colorCode <= '9') ||
                (colorCode >= 'a' && colorCode <= 'f');
    }
}
