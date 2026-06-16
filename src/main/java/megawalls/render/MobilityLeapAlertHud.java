package megawalls.render;

import cc.polyfrost.oneconfig.config.annotations.Exclude;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import cc.polyfrost.oneconfig.renderer.TextRenderer;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class MobilityLeapAlertHud extends BasicHud {

    @Exclude
    private static final long DISPLAY_MS = 1750L;
    @Exclude
    private static final String PREVIEW_TEXT =
        "Spider Example activated Leap (15m).";

    @Exclude
    private transient String message = "";
    @Exclude
    private transient long visibleUntilMs = 0L;

    public MobilityLeapAlertHud() {
        super(
            true,
            780.0F,
            500.0F,
            1.0F,
            true,
            false,
            2.0F,
            5.0F,
            5.0F,
            new OneColor(0, 0, 0, 120),
            false,
            2.0F,
            new OneColor(0, 0, 0)
        );
        this.positionAlignment = 2;
    }

    public void showLeapAlert(String playerName, int distance) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }

        this.message =
            "Spider " + playerName + " activated Leap (" + distance + "m).";
        this.visibleUntilMs = System.currentTimeMillis() + DISPLAY_MS;
    }

    public void renderActive(Minecraft minecraft, MegaWallsConfig config) {
        if (
            minecraft == null ||
            config == null ||
            !config.mobilityLeapGuiAlert
        ) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawAll(new UMatrixStack(), false);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
        }
    }

    @Override
    protected void draw(
        UMatrixStack matrices,
        float x,
        float y,
        float scale,
        boolean example
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        TextRenderer.drawScaledString(
            getDisplayText(example),
            x,
            y,
            0xFF5555,
            config != null && config.mobilityLeapAlertTextShadow
                ? TextRenderer.TextType.SHADOW
                : TextRenderer.TextType.NONE,
            scale
        );
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        return getTextWidth(getDisplayText(example)) * scale;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        return 8.0F * scale;
    }

    @Override
    protected boolean shouldShow() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            config.mobilityLeapGuiAlert &&
            isAlertVisible() &&
            super.shouldShow();
    }

    private boolean isAlertVisible() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            System.currentTimeMillis() <= visibleUntilMs &&
            message != null &&
            !message.isEmpty();
    }

    private String getDisplayText(boolean example) {
        return example ? PREVIEW_TEXT : message;
    }

    private float getTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        Minecraft minecraft = MinecraftClient.forHud();
        if (minecraft != null) {
            return TextRenderer.getStringWidth(text);
        }

        return text.length() * 6.0F;
    }
}
