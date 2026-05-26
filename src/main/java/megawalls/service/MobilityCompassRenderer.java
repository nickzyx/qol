package megawalls.service;

import java.util.List;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class MobilityCompassRenderer {

    private static final int MAX_ALERTS = 6;
    private static final int HEAD_SIZE = 8;
    private static final int MARKER_WIDTH = 42;
    private static final int MARKER_HEIGHT = 24;

    void render(
        Minecraft minecraft,
        MegaWallsConfig config,
        List<MobilityAlertSnapshot> alerts
    ) {
        if (
            minecraft == null ||
            minecraft.thePlayer == null ||
            minecraft.theWorld == null ||
            minecraft.fontRendererObj == null ||
            config == null ||
            alerts == null ||
            alerts.isEmpty()
        ) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        int alertCount = Math.min(MAX_ALERTS, alerts.size());
        int centerX = getCenterX(config, resolution);
        int centerY = getCenterY(config, resolution);
        int radius = getRadius(config, resolution, centerX, centerY);

        for (int index = 0; index < alertCount; index++) {
            renderMarker(
                minecraft,
                minecraft.fontRendererObj,
                alerts.get(index),
                centerX,
                centerY,
                radius
            );
        }
    }

    private int getCenterX(MegaWallsConfig config, ScaledResolution resolution) {
        int minX = MARKER_WIDTH / 2;
        int maxX = Math.max(minX, resolution.getScaledWidth() - MARKER_WIDTH / 2);
        int percent = clampPercent(config.mobilityCompassX);
        return minX + (int) Math.round((maxX - minX) * (percent / 100.0D));
    }

    private int getCenterY(MegaWallsConfig config, ScaledResolution resolution) {
        int minY = MARKER_HEIGHT / 2;
        int maxY = Math.max(minY, resolution.getScaledHeight() - MARKER_HEIGHT / 2);
        int percent = clampPercent(config.mobilityCompassY);
        return minY + (int) Math.round((maxY - minY) * (percent / 100.0D));
    }

    private int getRadius(
        MegaWallsConfig config,
        ScaledResolution resolution,
        int centerX,
        int centerY
    ) {
        int horizontalLimit = Math.min(
            centerX - MARKER_WIDTH / 2,
            resolution.getScaledWidth() - centerX - MARKER_WIDTH / 2
        );
        int verticalLimit = Math.min(
            centerY - MARKER_HEIGHT / 2,
            resolution.getScaledHeight() - centerY - MARKER_HEIGHT / 2
        );
        int maxRadius = Math.max(10, Math.min(horizontalLimit, verticalLimit));
        return Math.max(10, Math.min(maxRadius, config.mobilityCompassRadius));
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private void renderMarker(
        Minecraft minecraft,
        FontRenderer fontRenderer,
        MobilityAlertSnapshot alert,
        int centerX,
        int centerY,
        int radius
    ) {
        double radians = Math.toRadians(alert.relativeYaw);
        int markerX = centerX + (int) Math.round(Math.sin(radians) * radius);
        int markerY = centerY - (int) Math.round(Math.cos(radians) * radius);
        int x = markerX - HEAD_SIZE / 2;
        int y = markerY - HEAD_SIZE / 2;

        renderHead(minecraft, alert.player, x, y);

        drawArrow(
            markerX,
            markerY + 14,
            alert.relativeYaw,
            getClassRgb(alert.megaWallsClass)
        );

        String distanceText = getClassColor(alert.megaWallsClass) + Integer.toString(alert.distance);
        GlStateManager.enableTexture2D();
        fontRenderer.drawStringWithShadow(distanceText, x + HEAD_SIZE + 2, y - 1, 0xFFFFFF);

        String yText = formatYDifference(alert.yDifference);
        GlStateManager.enableTexture2D();
        fontRenderer.drawStringWithShadow(
            EnumChatFormatting.GRAY + yText,
            x + HEAD_SIZE + 2,
            y + 8,
            0xFFFFFF
        );
    }

    private void renderHead(Minecraft minecraft, EntityPlayer player, int x, int y) {
        if (player instanceof AbstractClientPlayer) {
            ResourceLocation skin = ((AbstractClientPlayer) player).getLocationSkin();
            minecraft.getTextureManager().bindTexture(skin);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, HEAD_SIZE, HEAD_SIZE, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, HEAD_SIZE, HEAD_SIZE, 64.0F, 64.0F);
            return;
        }

        Gui.drawRect(x, y, x + HEAD_SIZE, y + HEAD_SIZE, 0xAA333333);
    }

    private void drawArrow(int x, int y, float relativeYaw, int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.translate((float) x, (float) y, 0.0F);
        GlStateManager.rotate(relativeYaw, 0.0F, 0.0F, 1.0F);
        GL11.glColor4f(red, green, blue, 1.0F);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(0.0F, -5.0F);
        GL11.glVertex2f(4.0F, 4.0F);
        GL11.glVertex2f(-4.0F, 4.0F);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private String getClassColor(MegaWallsClass megaWallsClass) {
        if (megaWallsClass == MegaWallsClass.SPIDER) {
            return EnumChatFormatting.YELLOW.toString();
        }
        if (megaWallsClass == MegaWallsClass.ENDERMAN) {
            return EnumChatFormatting.AQUA.toString();
        }
        return EnumChatFormatting.WHITE.toString();
    }

    private int getClassRgb(MegaWallsClass megaWallsClass) {
        if (megaWallsClass == MegaWallsClass.SPIDER) {
            return 0x55FF55;
        }
        if (megaWallsClass == MegaWallsClass.ENDERMAN) {
            return 0x55FFFF;
        }
        return 0xFFFFFF;
    }

    private String formatYDifference(int yDifference) {
        if (yDifference > 0) {
            return "+" + yDifference;
        }
        return Integer.toString(yDifference);
    }
}
