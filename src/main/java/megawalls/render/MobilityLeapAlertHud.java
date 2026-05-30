package megawalls.render;

import cc.polyfrost.oneconfig.config.annotations.Exclude;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;

public final class MobilityLeapAlertHud extends SingleTextHud {

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
        super("", true, 780, 500);
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
            minecraft.fontRendererObj == null ||
            config == null ||
            !config.mobilityLeapGuiAlert ||
            !isAlertVisible()
        ) {
            return;
        }

        FontRenderer fontRenderer = minecraft.fontRendererObj;
        float scale = getScale();
        if (scale <= 0.0F) {
            scale = 1.0F;
        }

        float x = position == null ? 0.0F : position.getX();
        float y = position == null ? 0.0F : position.getY();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        fontRenderer.drawStringWithShadow(message, 0.0F, 0.0F, 0xFF5555);
        GlStateManager.popMatrix();
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return PREVIEW_TEXT;
        }
        return message;
    }

    @Override
    protected String getTextFrequent(boolean example) {
        return getText(example);
    }

    @Override
    protected boolean shouldShow() {
        return false;
    }

    private boolean isAlertVisible() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            System.currentTimeMillis() <= visibleUntilMs &&
            message != null &&
            !message.isEmpty();
    }
}
