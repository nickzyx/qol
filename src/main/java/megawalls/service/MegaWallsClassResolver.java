package megawalls.service;

import megawalls.domain.MegaWallsClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;

import java.util.Locale;

final class MegaWallsClassResolver {

    MegaWallsClass resolveLocalClass() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.thePlayer == null ? null : resolveMegaWallsClass(minecraft.thePlayer);
    }

    MegaWallsClass resolveMegaWallsClass(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        Scoreboard scoreboard = player.worldObj == null ? null : player.worldObj.getScoreboard();
        return resolveMegaWallsClass(getRenderedName(player), player.getName(), scoreboard);
    }

    MegaWallsClass resolveMegaWallsClass(String renderedName, String playerName, Scoreboard scoreboard) {
        MegaWallsClass scoreboardClass = MegaWallsClass.fromScoreboard(scoreboard, playerName);
        if (scoreboardClass != null) {
            return scoreboardClass;
        }

        return MegaWallsClass.fromRenderedName(renderedName);
    }

    String getRenderedName(EntityPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getNetHandler() == null || player == null) {
            return player == null ? "" : player.getName();
        }

        NetworkPlayerInfo playerInfo = minecraft.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (playerInfo != null && playerInfo.getDisplayName() != null) {
            return playerInfo.getDisplayName().getFormattedText();
        }

        IChatComponent displayName = player.getDisplayName();
        return displayName == null ? player.getName() : displayName.getFormattedText();
    }

    String getRawText(IChatComponent chatComponent) {
        return chatComponent == null ? "" : chatComponent.getUnformattedTextForChat();
    }

    String stripFormatting(String value) {
        return MegaWallsClass.stripFormatting(value);
    }

    String normalize(String value) {
        return MegaWallsClass.normalize(stripFormatting(value));
    }

    boolean looksLikeZombieTag(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        String renderedName = getRenderedName(player).toUpperCase(Locale.ROOT);
        return renderedName.contains("[ZOM]") || renderedName.contains("[僵尸]");
    }

    boolean hasPhoenixIndicator(String renderedName) {
        return MegaWallsClass.fromRenderedName(renderedName) == MegaWallsClass.PHOENIX;
    }
}
