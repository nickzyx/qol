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

public final class HunterForceOfNatureHud extends BasicHud {

    @Exclude
    private static final String PREVIEW_TEXT = "\u00a7aF.O.N. \u00a7cSTRENGTH";

    @Exclude
    private transient String text = "";

    public HunterForceOfNatureHud() {
        super(
            true,
            450.0F,
            420.0F,
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

    public void renderActive(Minecraft minecraft, MegaWallsConfig config, String text) {
        if (
            minecraft == null ||
            config == null ||
            !config.hunterFonDraggableHud
        ) {
            return;
        }

        this.text = text == null ? "" : text;
        drawAll(new UMatrixStack(), false);
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
            0xFFFFFF,
            config != null && config.hunterFonTextShadow
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
            config.hunterFonDraggableHud &&
            super.shouldShow();
    }

    private String getDisplayText(boolean example) {
        return example ? PREVIEW_TEXT : text;
    }

    private float getTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        Minecraft minecraft = MinecraftClient.forHud();
        if (minecraft != null) {
            return minecraft.fontRendererObj.getStringWidth(text);
        }

        return text.length() * 6.0F;
    }
}
