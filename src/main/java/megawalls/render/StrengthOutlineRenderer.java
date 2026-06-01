package megawalls.render;

import megawalls.MegaWallsMod;
import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import megawalls.waypoint.WaypointTargetOutlineRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public final class StrengthOutlineRenderer {

    private static final int STRENGTH_OUTLINE_COLOR = 0xFF3300;
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();
    private static boolean renderedOutline;

    private StrengthOutlineRenderer() {}

    public static boolean renderStrengthOutlines(
        boolean original,
        Entity renderViewEntity,
        ICamera camera,
        float partialTicks,
        Framebuffer outlineFramebuffer,
        ShaderGroup outlineShader
    ) {
        if (!canRenderOutlines(outlineFramebuffer, outlineShader)) {
            renderedOutline = false;
            return original;
        }

        boolean renderedAny = false;
        for (Object rawPlayer : MINECRAFT.theWorld.playerEntities) {
            if (!(rawPlayer instanceof EntityPlayer)) {
                continue;
            }

            EntityPlayer player = (EntityPlayer) rawPlayer;
            if (!shouldOutlinePlayer(player)) {
                continue;
            }

            if (renderStrengthOutline(renderViewEntity, camera, partialTicks, outlineFramebuffer, outlineShader, player)) {
                renderedAny = true;
            }
        }

        renderedOutline = renderedAny;
        return false;
    }

    public static boolean shouldDrawOutlineFramebuffer(boolean original) {
        if (original || renderedOutline) {
            renderedOutline = false;
            return true;
        }

        return false;
    }

    public static int getOutlineColor(int originalColor, EntityLivingBase entity) {
        if (!(entity instanceof EntityPlayer)) {
            return originalColor;
        }

        EntityPlayer player = (EntityPlayer) entity;
        if (WaypointTargetOutlineRegistry.shouldOutline(player)) {
            return WaypointTargetOutlineRegistry.getOutlineColor(player);
        }

        return shouldOutlineStrengthPlayer(player) ? STRENGTH_OUTLINE_COLOR : originalColor;
    }

    private static boolean canRenderOutlines(Framebuffer outlineFramebuffer, ShaderGroup outlineShader) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            (canRenderStrengthOutlines(config) || canRenderWaypointTargetOutlines(config)) &&
            MINECRAFT != null &&
            MINECRAFT.theWorld != null &&
            MINECRAFT.thePlayer != null &&
            outlineFramebuffer != null &&
            outlineShader != null &&
            OpenGlHelper.isFramebufferEnabled();
    }

    private static boolean canRenderStrengthOutlines(MegaWallsConfig config) {
        return config.strengthOutline &&
            config.strengthDetectorEnabled &&
            config.canUseStrength(MegaWallsMod.isDeathmatchActive());
    }

    private static boolean canRenderWaypointTargetOutlines(MegaWallsConfig config) {
        return config.waypointRenderWorld && WaypointTargetOutlineRegistry.hasTargets();
    }

    private static boolean shouldOutlinePlayer(EntityPlayer player) {
        return shouldOutlineWaypointTarget(player) || shouldOutlineStrengthPlayer(player);
    }

    private static boolean shouldOutlineWaypointTarget(EntityPlayer player) {
        return isValidOutlineTarget(player) && WaypointTargetOutlineRegistry.shouldOutline(player);
    }

    private static boolean shouldOutlineStrengthPlayer(EntityPlayer player) {
        if (!isValidOutlineTarget(player)) {
            return false;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (config == null || !canRenderStrengthOutlines(config)) {
            return false;
        }

        PlayerStateView playerStateView = MegaWallsMod.queryNametagPlayerState(
            player.getUniqueID(),
            player.getName(),
            player.getDisplayName() == null ? player.getName() : player.getDisplayName().getFormattedText()
        );
        return playerStateView != null && playerStateView.hasStrength();
    }

    private static boolean isValidOutlineTarget(EntityPlayer player) {
        if (
            player == null ||
            MINECRAFT == null ||
            MINECRAFT.thePlayer == null ||
            player == MINECRAFT.thePlayer ||
            player.isDead ||
            player.isInvisible() ||
            player.isSpectator()
        ) {
            return false;
        }
        return true;
    }

    private static boolean renderStrengthOutline(
        Entity renderViewEntity,
        ICamera camera,
        float partialTicks,
        Framebuffer outlineFramebuffer,
        ShaderGroup outlineShader,
        EntityPlayer player
    ) {
        if (renderViewEntity == null || camera == null) {
            return false;
        }

        double cameraX = renderViewEntity.lastTickPosX +
            (renderViewEntity.posX - renderViewEntity.lastTickPosX) * partialTicks;
        double cameraY = renderViewEntity.lastTickPosY +
            (renderViewEntity.posY - renderViewEntity.lastTickPosY) * partialTicks;
        double cameraZ = renderViewEntity.lastTickPosZ +
            (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * partialTicks;

        boolean sleepingViewEntity = MINECRAFT.getRenderViewEntity() instanceof EntityLivingBase &&
            ((EntityLivingBase) MINECRAFT.getRenderViewEntity()).isPlayerSleeping();
        boolean inFrustum = player.isInRangeToRender3d(cameraX, cameraY, cameraZ) &&
            (player.ignoreFrustumCheck ||
                camera.isBoundingBoxInFrustum(player.getEntityBoundingBox()) ||
                player.ridingEntity == MINECRAFT.thePlayer);
        boolean thirdPersonOrSleeping = player != MINECRAFT.getRenderViewEntity() ||
            MINECRAFT.gameSettings.thirdPersonView != 0 ||
            sleepingViewEntity;

        if (!inFrustum || !thirdPersonOrSleeping) {
            return false;
        }

        GlStateManager.depthFunc(519);
        GlStateManager.disableFog();
        outlineFramebuffer.framebufferClear();
        outlineFramebuffer.bindFramebuffer(false);
        MINECRAFT.theWorld.theProfiler.endStartSection("strengthEntityOutlines");
        RenderHelper.disableStandardItemLighting();
        MINECRAFT.getRenderManager().setRenderOutlines(true);
        try {
            MINECRAFT.getRenderManager().renderEntitySimple(player, partialTicks);
        } finally {
            MINECRAFT.getRenderManager().setRenderOutlines(false);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.depthMask(false);
            outlineShader.loadShaderGroup(partialTicks);
            GlStateManager.enableLighting();
            GlStateManager.depthMask(true);
            MINECRAFT.getFramebuffer().bindFramebuffer(false);
            GlStateManager.enableFog();
            GlStateManager.enableBlend();
            GlStateManager.enableColorMaterial();
            GlStateManager.depthFunc(515);
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
        return true;
    }
}
