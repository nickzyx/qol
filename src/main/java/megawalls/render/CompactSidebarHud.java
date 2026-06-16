package megawalls.render;

import cc.polyfrost.oneconfig.config.annotations.Exclude;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.util.Collections;
import java.util.List;

public final class CompactSidebarHud extends BasicHud {

    @Exclude
    private static final int BACKGROUND_COLOR = 0x50000000;
    @Exclude
    private static final String PREVIEW_TITLE = "\u00a7eMEGA WALLS";
    @Exclude
    private static final List<String> PREVIEW_LINES = Collections.unmodifiableList(
        java.util.Arrays.asList(
            "\u00a706/16/26",
            "Map: \u00a7aAztec",
            "Players: \u00a7a9/100",
            "Starting in \u00a7a06:28",
            "Selected Class:",
            "\u00a7aSquid"
        )
    );

    @Exclude
    private transient String title = PREVIEW_TITLE;
    @Exclude
    private transient List<String> lines = PREVIEW_LINES;

    public CompactSidebarHud() {
        super(
            true,
            720.0F,
            120.0F,
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

    public void renderSidebar(String title, List<String> lines) {
        setSidebar(title, lines);
        drawAll(new UMatrixStack(), false);
    }

    public void setSidebar(String title, List<String> lines) {
        if (title != null && !title.isEmpty()) {
            this.title = title;
        }
        this.lines = lines == null || lines.isEmpty() ? PREVIEW_LINES : lines;
    }

    @Override
    protected void draw(
        UMatrixStack matrices,
        float x,
        float y,
        float scale,
        boolean example
    ) {
        FontRenderer fontRenderer = getFontRenderer();
        if (fontRenderer == null) {
            return;
        }

        String displayTitle = example ? PREVIEW_TITLE : title;
        List<String> displayLines = example ? PREVIEW_LINES : lines;
        int width = getSidebarWidth(fontRenderer, displayTitle, displayLines);
        int lineHeight = fontRenderer.FONT_HEIGHT;
        int left = Math.round(x);
        int top = Math.round(y);
        int right = left + Math.round(width * scale) + 4;

        drawScaledRow(fontRenderer, displayTitle, left, top, width, scale, true);
        for (int index = 0; index < displayLines.size(); index++) {
            drawScaledRow(
                fontRenderer,
                displayLines.get(index),
                left,
                top + Math.round((index + 1) * lineHeight * scale),
                width,
                scale,
                false
            );
        }
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        FontRenderer fontRenderer = getFontRenderer();
        if (fontRenderer == null) {
            return 0.0F;
        }

        return (getSidebarWidth(
            fontRenderer,
            example ? PREVIEW_TITLE : title,
            example ? PREVIEW_LINES : lines
        ) + 4) * scale;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        FontRenderer fontRenderer = getFontRenderer();
        if (fontRenderer == null) {
            return 0.0F;
        }

        int rows = (example ? PREVIEW_LINES : lines).size() + 1;
        return rows * fontRenderer.FONT_HEIGHT * scale;
    }

    @Override
    protected boolean shouldShow() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            config.compactSidebar &&
            super.shouldShow();
    }

    private void drawScaledRow(
        FontRenderer fontRenderer,
        String text,
        int left,
        int top,
        int width,
        float scale,
        boolean centered
    ) {
        int rowHeight = Math.round(fontRenderer.FONT_HEIGHT * scale);
        int right = left + Math.round((width + 4) * scale);
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (config == null || config.compactSidebarOriginalBackground) {
            Gui.drawRect(left, top, right, top + rowHeight, BACKGROUND_COLOR);
        }

        float textX = left + (2.0F * scale);
        if (centered) {
            textX = left + ((width + 4) * scale / 2.0F) -
                (fontRenderer.getStringWidth(text) * scale / 2.0F);
        }

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        try {
            net.minecraft.client.renderer.GlStateManager.translate(textX, top, 0.0F);
            net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0F);
            fontRenderer.drawString(text, 0.0F, 0.0F, 0xFFFFFF, false);
        } finally {
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    private int getSidebarWidth(
        FontRenderer fontRenderer,
        String title,
        List<String> lines
    ) {
        int width = fontRenderer.getStringWidth(title);
        for (String line : lines) {
            width = Math.max(width, fontRenderer.getStringWidth(line));
        }
        return width;
    }

    private FontRenderer getFontRenderer() {
        Minecraft minecraft = MinecraftClient.forHud();
        return minecraft == null ? null : minecraft.fontRendererObj;
    }
}
