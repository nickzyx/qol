package megawalls.render;

import cc.polyfrost.oneconfig.config.annotations.Exclude;
import cc.polyfrost.oneconfig.hud.SingleTextHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import net.minecraft.client.Minecraft;

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
            config == null ||
            !config.mobilityLeapGuiAlert
        ) {
            return;
        }

        drawAll(new UMatrixStack(), false);
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
}
