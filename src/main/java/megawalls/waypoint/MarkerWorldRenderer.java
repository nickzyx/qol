package megawalls.waypoint;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

public final class MarkerWorldRenderer {

    private static final float MIN_LABEL_SCALE = 0.026F;
    private static final float MAX_LABEL_SCALE = 0.22F;
    private static final float LABEL_SCALE_PER_BLOCK = 0.00145F;

    public void render(Minecraft minecraft, List<Marker> markers, float partialTicks, int renderRange) {
        if (
            minecraft == null ||
            minecraft.thePlayer == null ||
            minecraft.theWorld == null ||
            markers == null ||
            markers.isEmpty()
        ) {
            return;
        }

        EntityPlayerSP player = minecraft.thePlayer;
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        int maxRangeSq = renderRange * renderRange;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                1,
                0
            );
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GL11.glLineWidth(2.0F);

            for (Marker marker : markers) {
                if (marker.hasTargetPlayer()) {
                    continue;
                }

                BlockPos pos = marker.getPosition();
                double dx = pos.getX() + 0.5D - playerX;
                double dy = pos.getY() - playerY;
                double dz = pos.getZ() + 0.5D - playerZ;
                if (dx * dx + dy * dy + dz * dz > maxRangeSq) {
                    continue;
                }
                renderMarker(minecraft, marker, dx, dy, dz, playerX, playerY, playerZ);
            }
        } finally {
            GL11.glLineWidth(1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.resetColor();
            GlStateManager.popMatrix();
        }
    }

    private void renderMarker(
        Minecraft minecraft,
        Marker marker,
        double dx,
        double dy,
        double dz,
        double playerX,
        double playerY,
        double playerZ
    ) {
        int rgb = marker.getKind().getRgb();
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.color(red, green, blue, 0.9F);
            RenderGlobal.drawSelectionBoundingBox(
                new AxisAlignedBB(-0.5D, 0.0D, -0.5D, 0.5D, 1.0D, 0.5D)
            );
            renderStaticColumn();
            renderLabel(minecraft, marker, playerX, playerY, playerZ);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void renderStaticColumn() {
        GL11.glBegin(GL11.GL_LINES);
        edge(0.0D, 0.0D, 0.0D, 0.0D, 48.0D, 0.0D);
        edge(-0.25D, 48.0D, 0.0D, 0.25D, 48.0D, 0.0D);
        edge(0.0D, 48.0D, -0.25D, 0.0D, 48.0D, 0.25D);
        GL11.glEnd();
    }

    private void edge(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private void renderLabel(
        Minecraft minecraft,
        Marker marker,
        double playerX,
        double playerY,
        double playerZ
    ) {
        if (
            minecraft == null ||
            minecraft.fontRendererObj == null ||
            minecraft.getRenderManager() == null
        ) {
            return;
        }

        FontRenderer fontRenderer = minecraft.fontRendererObj;
        String text = marker.getKind().getLabel() +
            " " +
            marker.getOwner() +
            " " +
            marker.distance3D(playerX, playerY, playerZ) +
            "m";
        float labelScale = getLabelScale(marker, playerX, playerY, playerZ);
        int width = fontRenderer.getStringWidth(text);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0D, 1.65D, 0.0D);
            GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(minecraft.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-labelScale, -labelScale, labelScale);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                1,
                0
            );
            GlStateManager.depthMask(false);
            fontRenderer.drawStringWithShadow(text, -width / 2.0F, 0.0F, 0xFFFFFF);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GlStateManager.disableTexture2D();
            GlStateManager.depthMask(false);
            GlStateManager.popMatrix();
        }
    }

    private float getLabelScale(Marker marker, double playerX, double playerY, double playerZ) {
        if (marker == null || marker.getPosition() == null) {
            return MIN_LABEL_SCALE;
        }

        BlockPos position = marker.getPosition();
        double dx = position.getX() + 0.5D - playerX;
        double dy = position.getY() + 1.65D - playerY;
        double dz = position.getZ() + 0.5D - playerZ;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return clamp(MIN_LABEL_SCALE + distance * LABEL_SCALE_PER_BLOCK, MIN_LABEL_SCALE, MAX_LABEL_SCALE);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
