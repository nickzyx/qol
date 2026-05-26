package megawalls.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelSnowMan;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderSnowMan;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.entity.layers.LayerSnowmanHead;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class TransparentSnowmanRenderer {

    private static Field layerRenderersField;
    private boolean renderingTransparentSnowman;

    public boolean isRenderingTransparentSnowman() {
        return renderingTransparentSnowman;
    }

    public void render(RenderLivingEvent.Pre event, int opacityPercent) {
        if (renderingTransparentSnowman || event == null || !(event.entity instanceof EntitySnowman)) {
            return;
        }

        float alpha = getAlpha(opacityPercent);
        event.setCanceled(true);
        renderingTransparentSnowman = true;
        GlStateManager.pushMatrix();
        List<LayerRenderer> removedSnowmanLayers = removeSnowmanHeadLayers(event.renderer);
        try {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
            event.renderer.doRender(
                    (Entity) event.entity,
                    event.x,
                    event.y,
                    event.z,
                    event.entity.rotationYaw,
                    0.0F
            );
            renderTransparentSnowmanHead(event, alpha);
        } finally {
            restoreSnowmanHeadLayers(event.renderer, removedSnowmanLayers);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            renderingTransparentSnowman = false;
        }
    }

    private float getAlpha(int opacityPercent) {
        int clampedOpacity = Math.max(10, Math.min(90, opacityPercent));
        return clampedOpacity / 100.0F;
    }

    @SuppressWarnings("unchecked")
    private List<LayerRenderer> removeSnowmanHeadLayers(RendererLivingEntity renderer) {
        if (renderer == null) {
            return Collections.emptyList();
        }

        try {
            Field field = getLayerRenderersField();
            List<LayerRenderer> layers = (List<LayerRenderer>) field.get(renderer);
            if (layers == null || layers.isEmpty()) {
                return Collections.emptyList();
            }

            List<LayerRenderer> removedLayers = new ArrayList<LayerRenderer>();
            Iterator<LayerRenderer> iterator = layers.iterator();
            while (iterator.hasNext()) {
                LayerRenderer layer = iterator.next();
                if (layer instanceof LayerSnowmanHead) {
                    removedLayers.add(layer);
                    iterator.remove();
                }
            }
            return removedLayers;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private void restoreSnowmanHeadLayers(
            RendererLivingEntity renderer,
            List<LayerRenderer> removedLayers
    ) {
        if (renderer == null || removedLayers == null || removedLayers.isEmpty()) {
            return;
        }

        try {
            Field field = getLayerRenderersField();
            @SuppressWarnings("unchecked")
            List<LayerRenderer> layers = (List<LayerRenderer>) field.get(renderer);
            if (layers != null) {
                layers.addAll(removedLayers);
            }
        } catch (Exception ignored) {
        }
    }

    private Field getLayerRenderersField() {
        if (layerRenderersField == null) {
            layerRenderersField = ReflectionHelper.findField(
                    RendererLivingEntity.class,
                    "layerRenderers",
                    "field_177097_h"
            );
            layerRenderersField.setAccessible(true);
        }
        return layerRenderersField;
    }

    private void renderTransparentSnowmanHead(RenderLivingEvent.Pre event, float alpha) {
        if (!(event.entity instanceof EntitySnowman) || !(event.renderer instanceof RenderSnowMan)) {
            return;
        }

        EntitySnowman snowman = (EntitySnowman) event.entity;
        if (snowman.isInvisible()) {
            return;
        }

        RenderSnowMan renderer = (RenderSnowMan) event.renderer;
        ModelSnowMan model = renderer.getMainModel();
        if (model == null || model.head == null) {
            return;
        }

        float partialTicks = 0.0F;
        float bodyYaw = interpolateRotation(
                snowman.prevRenderYawOffset,
                snowman.renderYawOffset,
                partialTicks
        );
        float headYaw = interpolateRotation(
                snowman.prevRotationYawHead,
                snowman.rotationYawHead,
                partialTicks
        );
        float netHeadYaw = headYaw - bodyYaw;
        float headPitch = snowman.prevRotationPitch +
                (snowman.rotationPitch - snowman.prevRotationPitch) * partialTicks;
        float ageInTicks = snowman.ticksExisted + partialTicks;
        float limbSwingAmount = snowman.prevLimbSwingAmount +
                (snowman.limbSwingAmount - snowman.prevLimbSwingAmount) * partialTicks;
        float limbSwing = snowman.limbSwing -
                snowman.limbSwingAmount * (1.0F - partialTicks);
        if (limbSwingAmount > 1.0F) {
            limbSwingAmount = 1.0F;
        }

        model.setLivingAnimations(snowman, limbSwing, limbSwingAmount, partialTicks);
        model.setRotationAngles(
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch,
                0.0625F,
                snowman
        );

        GlStateManager.pushMatrix();
        GlStateManager.translate(event.x, event.y, event.z);
        GlStateManager.rotate(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
        GlStateManager.translate(0.0F, -1.5078125F, 0.0F);
        model.head.postRender(0.0625F);
        float scale = 0.625F;
        GlStateManager.translate(0.0F, -0.34375F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(scale, -scale, -scale);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        renderTransparentPumpkinCube(Minecraft.getMinecraft(), alpha);
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }

    private float interpolateRotation(float previous, float current, float partialTicks) {
        float delta = current - previous;
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        while (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + partialTicks * delta;
    }

    private void renderTransparentPumpkinCube(Minecraft minecraft, float alpha) {
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return;
        }

        TextureAtlasSprite side = getBlockSprite(minecraft, "minecraft:blocks/pumpkin_side");
        TextureAtlasSprite top = getBlockSprite(minecraft, "minecraft:blocks/pumpkin_top");
        TextureAtlasSprite face = getBlockSprite(minecraft, "minecraft:blocks/pumpkin_face_off");
        if (side == null || top == null || face == null) {
            return;
        }

        minecraft.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        addFace(worldRenderer, face, -0.5D, -0.5D, -0.5D, 0.5D, 0.5D, -0.5D, FaceDirection.NORTH, alpha);
        addFace(worldRenderer, side, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D, 0.5D, FaceDirection.SOUTH, alpha);
        addFace(worldRenderer, side, -0.5D, -0.5D, -0.5D, -0.5D, 0.5D, 0.5D, FaceDirection.WEST, alpha);
        addFace(worldRenderer, side, 0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D, FaceDirection.EAST, alpha);
        addFace(worldRenderer, top, -0.5D, 0.5D, -0.5D, 0.5D, 0.5D, 0.5D, FaceDirection.UP, alpha);
        addFace(worldRenderer, top, -0.5D, -0.5D, -0.5D, 0.5D, -0.5D, 0.5D, FaceDirection.DOWN, alpha);

        tessellator.draw();
        GlStateManager.enableCull();
    }

    private TextureAtlasSprite getBlockSprite(Minecraft minecraft, String spriteName) {
        return minecraft.getTextureMapBlocks() == null
                ? null
                : minecraft.getTextureMapBlocks().getAtlasSprite(spriteName);
    }

    private enum FaceDirection {
        NORTH,
        SOUTH,
        WEST,
        EAST,
        UP,
        DOWN
    }

    private void addFace(
            WorldRenderer worldRenderer,
            TextureAtlasSprite sprite,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            FaceDirection direction,
            float alpha
    ) {
        double minU = sprite.getMinU();
        double maxU = sprite.getMaxU();
        double minV = sprite.getMinV();
        double maxV = sprite.getMaxV();

        switch (direction) {
            case NORTH:
                addVertex(worldRenderer, minX, maxY, minZ, maxU, minV, alpha);
                addVertex(worldRenderer, maxX, maxY, minZ, minU, minV, alpha);
                addVertex(worldRenderer, maxX, minY, minZ, minU, maxV, alpha);
                addVertex(worldRenderer, minX, minY, minZ, maxU, maxV, alpha);
                break;
            case SOUTH:
                addVertex(worldRenderer, maxX, maxY, maxZ, maxU, minV, alpha);
                addVertex(worldRenderer, minX, maxY, maxZ, minU, minV, alpha);
                addVertex(worldRenderer, minX, minY, maxZ, minU, maxV, alpha);
                addVertex(worldRenderer, maxX, minY, maxZ, maxU, maxV, alpha);
                break;
            case WEST:
                addVertex(worldRenderer, minX, maxY, maxZ, maxU, minV, alpha);
                addVertex(worldRenderer, minX, maxY, minZ, minU, minV, alpha);
                addVertex(worldRenderer, minX, minY, minZ, minU, maxV, alpha);
                addVertex(worldRenderer, minX, minY, maxZ, maxU, maxV, alpha);
                break;
            case EAST:
                addVertex(worldRenderer, maxX, maxY, minZ, maxU, minV, alpha);
                addVertex(worldRenderer, maxX, maxY, maxZ, minU, minV, alpha);
                addVertex(worldRenderer, maxX, minY, maxZ, minU, maxV, alpha);
                addVertex(worldRenderer, maxX, minY, minZ, maxU, maxV, alpha);
                break;
            case UP:
                addVertex(worldRenderer, minX, maxY, maxZ, minU, maxV, alpha);
                addVertex(worldRenderer, maxX, maxY, maxZ, maxU, maxV, alpha);
                addVertex(worldRenderer, maxX, maxY, minZ, maxU, minV, alpha);
                addVertex(worldRenderer, minX, maxY, minZ, minU, minV, alpha);
                break;
            case DOWN:
                addVertex(worldRenderer, minX, minY, minZ, minU, minV, alpha);
                addVertex(worldRenderer, maxX, minY, minZ, maxU, minV, alpha);
                addVertex(worldRenderer, maxX, minY, maxZ, maxU, maxV, alpha);
                addVertex(worldRenderer, minX, minY, maxZ, minU, maxV, alpha);
                break;
            default:
                break;
        }
    }

    private void addVertex(
            WorldRenderer worldRenderer,
            double x,
            double y,
            double z,
            double u,
            double v,
            float alpha
    ) {
        worldRenderer.pos(x, y, z).tex(u, v).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
    }
}
