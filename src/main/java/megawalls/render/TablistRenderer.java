package megawalls.render;

import megawalls.api.PlayerStateView;
import megawalls.api.TablistBridge;
import megawalls.domain.DiamondGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class TablistRenderer {

    private static final int ICON_WIDTH = 9;
    private static final int PHOENIX_ICON_WIDTH = 9;
    private static final int HEART_TEXTURE_Y = 0;
    private static final int HEART_FULL_TEXTURE_X = 52;
    private static final int HEART_EMPTY_TEXTURE_X = 16;
    private static final int POTION_ICON_WIDTH = 16;
    private static final int POTION_DAMAGE = 8229;
    private static final int SPACE_WIDTH = 4;

    private TablistRenderer() {}

    public static String decorateName(String renderedName, NetworkPlayerInfo playerInfo) {
        PlayerStateView playerStateView = queryPlayerState(playerInfo, renderedName);
        if (renderedName == null || playerStateView == null) {
            return renderedName;
        }

        List<DiamondGear> diamondGear = playerStateView.getDiamondGear();
        StringBuilder decoratedName = new StringBuilder(renderedName);

        appendPhoenixMarker(decoratedName, playerStateView);
        appendIconSpace(decoratedName, diamondGear);
        appendPotionIconSpace(decoratedName, playerStateView);

        return decoratedName.toString();
    }

    private static void appendPhoenixMarker(StringBuilder decoratedName, PlayerStateView playerStateView) {
        if (!playerStateView.isPhoenixClass()) {
            return;
        }

        int spacesNeeded = (PHOENIX_ICON_WIDTH + SPACE_WIDTH - 1) / SPACE_WIDTH;
        decoratedName.append(' ');
        for (int index = 0; index < spacesNeeded; index++) {
            decoratedName.append(' ');
        }
    }

    private static void appendIconSpace(StringBuilder decoratedName, List<DiamondGear> diamondGear) {
        if (diamondGear == null || diamondGear.isEmpty()) {
            return;
        }

        int spacesNeeded = ((diamondGear.size() * ICON_WIDTH) + SPACE_WIDTH - 1) / SPACE_WIDTH;
        decoratedName.append(' ');
        for (int index = 0; index < spacesNeeded; index++) {
            decoratedName.append(' ');
        }
    }

    private static void appendPotionIconSpace(StringBuilder decoratedName, PlayerStateView playerStateView) {
        if (playerStateView.getPotionCount() < 0) {
            return;
        }

        int spacesNeeded = (POTION_ICON_WIDTH + SPACE_WIDTH - 1) / SPACE_WIDTH;
        decoratedName.append(' ');
        for (int index = 0; index < spacesNeeded; index++) {
            decoratedName.append(' ');
        }
    }

    private static String stripFormatting(CharSequence value) {
        StringBuilder stripped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (currentChar == '\u00a7' && index + 1 < value.length()) {
                index++;
                continue;
            }
            stripped.append(currentChar);
        }
        return stripped.toString();
    }

    public static void renderDiamondIcons(NetworkPlayerInfo playerInfo, String renderedName, int x, int y) {
        PlayerStateView playerStateView = queryPlayerState(playerInfo, renderedName);
        if (renderedName == null || playerStateView == null) {
            return;
        }

        List<DiamondGear> diamondGear = playerStateView.getDiamondGear();
        int potionCount = playerStateView.getPotionCount();
        if (
            !playerStateView.isPhoenixClass() &&
            (diamondGear == null || diamondGear.isEmpty()) &&
            potionCount < 0
        ) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.fontRendererObj == null || minecraft.getRenderItem() == null) {
            return;
        }

        int iconX = x + minecraft.fontRendererObj.getStringWidth(getIconPrefix(renderedName, playerStateView)) + 2;
        int iconY = y;
        RenderItem renderItem = minecraft.getRenderItem();

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        RenderHelper.enableGUIStandardItemLighting();

        int drawnIcons = 0;
        if (playerStateView.isPhoenixClass()) {
            drawPhoenixHeart(
                    minecraft,
                    iconX,
                    iconY,
                    playerStateView.isPhoenixResurrectionAvailable()
            );
            drawnIcons++;
        }

        if (diamondGear != null && !diamondGear.isEmpty()) {
            for (DiamondGear gear : diamondGear) {
                Item item = getIconItem(gear);
                if (item == null) {
                    continue;
                }

                GlStateManager.pushMatrix();
                GlStateManager.translate(iconX + (drawnIcons * ICON_WIDTH), iconY, 0.0F);
                GlStateManager.scale(0.5F, 0.5F, 1.0F);
                renderItem.renderItemAndEffectIntoGUI(new ItemStack(item), 0, 0);
                GlStateManager.popMatrix();
                drawnIcons++;
            }
        }

        if (potionCount >= 0) {
            int potionX = iconX + (drawnIcons * ICON_WIDTH);
            GlStateManager.pushMatrix();
            GlStateManager.translate(potionX, iconY, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 1.0F);
            renderItem.renderItemAndEffectIntoGUI(new ItemStack(Items.potionitem, 1, POTION_DAMAGE), 0, 0);
            GlStateManager.popMatrix();

            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.pushMatrix();
            GlStateManager.scale(0.75F, 0.75F, 1.0F);
            minecraft.fontRendererObj.drawStringWithShadow(
                    Integer.toString(potionCount),
                    (potionX + 9) / 0.75F,
                    (iconY + 2) / 0.75F,
                    0xFF5555
            );
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static String getIconPrefix(String renderedName, PlayerStateView playerStateView) {
        return stripIconReservation(renderedName);
    }

    private static String stripIconReservation(String renderedName) {
        if (renderedName == null || renderedName.isEmpty()) {
            return "";
        }

        int endIndex = renderedName.length();
        while (endIndex > 0 && renderedName.charAt(endIndex - 1) == ' ') {
            endIndex--;
        }
        return renderedName.substring(0, endIndex);
    }

    private static void drawPhoenixHeart(
        Minecraft minecraft,
        int x,
        int y,
        boolean resurrectionAvailable
    ) {
        TextureManager textureManager = minecraft.getTextureManager();
        if (textureManager == null) {
            return;
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        textureManager.bindTexture(Gui.icons);
        new Gui().drawTexturedModalRect(
                x,
                y,
                resurrectionAvailable ? HEART_FULL_TEXTURE_X : HEART_EMPTY_TEXTURE_X,
                HEART_TEXTURE_Y,
                PHOENIX_ICON_WIDTH,
                PHOENIX_ICON_WIDTH
        );
        RenderHelper.enableGUIStandardItemLighting();
    }

    private static PlayerStateView queryPlayerState(NetworkPlayerInfo playerInfo, String renderedName) {
        if (playerInfo == null || playerInfo.getGameProfile() == null) {
            return null;
        }

        try {
            UUID playerId = playerInfo.getGameProfile().getId();
            String profileName = playerInfo.getGameProfile().getName();
            return TablistBridge.queryPlayerState(playerId, profileName, renderedName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Item getIconItem(DiamondGear gear) {
        if (gear == null) {
            return null;
        }

        switch (gear) {
            case HELMET:
                return Items.diamond_helmet;
            case CHESTPLATE:
                return Items.diamond_chestplate;
            case LEGGINGS:
                return Items.diamond_leggings;
            case BOOTS:
                return Items.diamond_boots;
            case SWORD:
                return Items.diamond_sword;
            default:
                return null;
        }
    }
}
