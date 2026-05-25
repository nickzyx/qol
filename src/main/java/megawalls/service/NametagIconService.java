package megawalls.service;

import megawalls.MegaWallsMod;
import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import cc.polyfrost.oneconfig.config.core.OneColor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class NametagIconService {

    private static final String PHOENIX_FULL_HEART = "\u2764 ";
    private static final String LEGACY_POTION_PREFIX = "QOLP:";
    private static final LegacyTextColor[] POTION_TEXT_COLORS = new LegacyTextColor[] {
        new LegacyTextColor(EnumChatFormatting.DARK_BLUE, 0, 0, 170),
        new LegacyTextColor(EnumChatFormatting.DARK_GREEN, 0, 170, 0),
        new LegacyTextColor(EnumChatFormatting.DARK_AQUA, 0, 170, 170),
        new LegacyTextColor(EnumChatFormatting.DARK_RED, 170, 0, 0),
        new LegacyTextColor(EnumChatFormatting.DARK_PURPLE, 170, 0, 170),
        new LegacyTextColor(EnumChatFormatting.GOLD, 255, 170, 0),
        new LegacyTextColor(EnumChatFormatting.GRAY, 170, 170, 170),
        new LegacyTextColor(EnumChatFormatting.DARK_GRAY, 85, 85, 85),
        new LegacyTextColor(EnumChatFormatting.BLUE, 85, 85, 255),
        new LegacyTextColor(EnumChatFormatting.GREEN, 85, 255, 85),
        new LegacyTextColor(EnumChatFormatting.AQUA, 85, 255, 255),
        new LegacyTextColor(EnumChatFormatting.RED, 255, 85, 85),
        new LegacyTextColor(EnumChatFormatting.LIGHT_PURPLE, 255, 85, 255),
        new LegacyTextColor(EnumChatFormatting.YELLOW, 255, 255, 85),
        new LegacyTextColor(EnumChatFormatting.WHITE, 255, 255, 255)
    };

    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;

    NametagIconService(
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService
    ) {
        this.classResolver = classResolver;
        this.contextService = contextService;
    }

    void handleClientTick(Minecraft minecraft) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            minecraft == null ||
            minecraft.theWorld == null ||
            minecraft.thePlayer == null ||
            config == null ||
            !contextService.isInMegaWalls() ||
            !contextService.isTrackingActive()
        ) {
            return;
        }

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null || playerEntities.isEmpty()) {
            return;
        }

        for (EntityPlayer player : playerEntities) {
            updatePlayerNametag(minecraft, config, player);
        }
    }

    void reset(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null || playerEntities.isEmpty()) {
            return;
        }

        for (EntityPlayer player : playerEntities) {
            clearNametagIcons(player);
        }
    }

    private void updatePlayerNametag(
        Minecraft minecraft,
        MegaWallsConfig config,
        EntityPlayer player
    ) {
        if (player == null || player == minecraft.thePlayer) {
            return;
        }

        String renderedName = classResolver.getRenderedName(player);
        PlayerStateView playerStateView = MegaWallsService.INSTANCE.queryNametagPlayerState(
            player.getUniqueID(),
            player.getName(),
            renderedName
        );
        if (playerStateView == null) {
            clearNametagIcons(player);
            return;
        }

        boolean showPhoenix =
            config.phoenixDetectorEnabled &&
            config.phoenixInNametags &&
            playerStateView.isPhoenixClass();
        boolean showPotion =
            config.potionDetectorEnabled &&
            config.canUsePotion(contextService.isDeathmatchActive()) &&
            config.potionInNametags &&
            playerStateView.getPotionCount() >= 0;

        IChatComponent phoenixPrefix = showPhoenix
            ? createPhoenixPrefix(playerStateView.isPhoenixResurrectionAvailable())
            : null;
        IChatComponent potionSuffix = showPotion
            ? createPotionSuffix(playerStateView.getPotionCount(), config.potionNametagColor)
            : null;

        applyNametagIcons(player, phoenixPrefix, potionSuffix);
    }

    private IChatComponent createPhoenixPrefix(boolean resurrectionAvailable) {
        String icon = PHOENIX_FULL_HEART;
        EnumChatFormatting color = resurrectionAvailable
            ? EnumChatFormatting.GREEN
            : EnumChatFormatting.RED;
        return new ChatComponentText(color.toString() + icon + EnumChatFormatting.RESET);
    }

    private IChatComponent createPotionSuffix(int potionCount, OneColor color) {
        return new ChatComponentText(
            " " +
                getClosestPotionFormatting(color) +
                "[" +
                potionCount +
                "]" +
                EnumChatFormatting.RESET
        );
    }

    private void applyNametagIcons(
        EntityPlayer player,
        IChatComponent phoenixPrefix,
        IChatComponent potionSuffix
    ) {
        if (player == null) {
            return;
        }

        boolean changed = removeExistingNametagIcons(player);
        if (phoenixPrefix != null) {
            player.addPrefix(phoenixPrefix);
            changed = true;
        }
        if (potionSuffix != null) {
            player.addSuffix(potionSuffix);
            changed = true;
        }
        if (changed) {
            player.refreshDisplayName();
        }
    }

    private void clearNametagIcons(EntityPlayer player) {
        if (player != null && removeExistingNametagIcons(player)) {
            player.refreshDisplayName();
        }
    }

    private boolean removeExistingNametagIcons(EntityPlayer player) {
        boolean changed = false;
        changed |= removeMatching(player.getPrefixes());
        changed |= removeMatching(player.getSuffixes());
        return changed;
    }

    private boolean removeMatching(Collection<IChatComponent> components) {
        if (components == null || components.isEmpty()) {
            return false;
        }

        boolean changed = false;
        List<IChatComponent> snapshot = new ArrayList<IChatComponent>(components);
        for (IChatComponent component : snapshot) {
            if (isQolNametagIcon(component)) {
                components.remove(component);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isQolNametagIcon(IChatComponent component) {
        if (component == null) {
            return false;
        }

        String text = component.getUnformattedText();
        if (text == null) {
            return false;
        }

        return text.indexOf(PHOENIX_FULL_HEART.trim()) >= 0 ||
            text.indexOf(LEGACY_POTION_PREFIX) >= 0 ||
            isPotionCountSuffix(component, text);
    }

    private boolean isPotionCountSuffix(IChatComponent component, String text) {
        String trimmed = EnumChatFormatting.getTextWithoutFormattingCodes(text).trim();
        if (!trimmed.matches("\\d+") && !trimmed.matches("\\[\\d+\\]")) {
            return false;
        }

        String formattedText = component.getFormattedText();
        return hasLegacyColor(text) ||
            (formattedText != null && hasLegacyColor(formattedText));
    }

    private EnumChatFormatting getClosestPotionFormatting(OneColor color) {
        if (color == null) {
            return EnumChatFormatting.RED;
        }

        LegacyTextColor closestColor = POTION_TEXT_COLORS[0];
        int closestDistance = Integer.MAX_VALUE;
        for (LegacyTextColor textColor : POTION_TEXT_COLORS) {
            int redDistance = color.getRed() - textColor.red;
            int greenDistance = color.getGreen() - textColor.green;
            int blueDistance = color.getBlue() - textColor.blue;
            int distance =
                redDistance * redDistance +
                greenDistance * greenDistance +
                blueDistance * blueDistance;
            if (distance < closestDistance) {
                closestDistance = distance;
                closestColor = textColor;
            }
        }

        return closestColor.formatting;
    }

    private boolean hasLegacyColor(String text) {
        if (text == null) {
            return false;
        }

        for (LegacyTextColor textColor : POTION_TEXT_COLORS) {
            if (text.indexOf(textColor.formatting.toString()) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static final class LegacyTextColor {
        private final EnumChatFormatting formatting;
        private final int red;
        private final int green;
        private final int blue;

        private LegacyTextColor(
            EnumChatFormatting formatting,
            int red,
            int green,
            int blue
        ) {
            this.formatting = formatting;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}
