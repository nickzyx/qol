package megawalls.render;

import cc.polyfrost.oneconfig.config.annotations.Exclude;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import cc.polyfrost.oneconfig.renderer.TextRenderer;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.service.PregameClassCounts;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class PregameClassTrackerHud extends BasicHud {

    @Exclude
    private static final String[] PREVIEW_LINES = new String[] {
        "Queue Classes 24/100",
        "Cow 4",
        "Hunter 3",
        "Zombie 3",
        "Unknown 8",
    };

    @Exclude
    private static final int LINE_HEIGHT = 10;

    @Exclude
    private transient String[] lines = new String[0];
    @Exclude
    private transient boolean queueActive;

    public PregameClassTrackerHud() {
        super(
            false,
            12.0F,
            160.0F,
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
    }

    public void renderActive(
        Minecraft minecraft,
        MegaWallsConfig config,
        PregameClassCounts counts
    ) {
        if (
            minecraft == null ||
            config == null ||
            minecraft.currentScreen != null ||
            !config.pregameClassTrackerEnabled ||
            !queueActive
        ) {
            return;
        }

        this.lines = buildLines(config, counts);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawAll(new UMatrixStack(), false);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
        String[] displayLines = getDisplayLines(example);
        TextRenderer.TextType textType =
            config != null && config.pregameClassTrackerTextShadow
                ? TextRenderer.TextType.SHADOW
                : TextRenderer.TextType.NONE;

        for (int index = 0; index < displayLines.length; index++) {
            TextRenderer.drawScaledString(
                displayLines[index],
                x,
                y + (index * LINE_HEIGHT * scale),
                index == 0 ? 0x55FFFF : 0xFFFFFF,
                textType,
                scale
            );
        }
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        float width = 0.0F;
        for (String line : getDisplayLines(example)) {
            width = Math.max(width, getTextWidth(line));
        }

        return width * scale;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        return getDisplayLines(example).length * LINE_HEIGHT * scale;
    }

    @Override
    protected boolean shouldShow() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            config.pregameClassTrackerEnabled &&
            queueActive &&
            super.shouldShow();
    }

    public void setQueueActive(boolean queueActive) {
        this.queueActive = queueActive;
        if (!queueActive) {
            this.lines = new String[0];
        }
    }

    private String[] buildLines(
        MegaWallsConfig config,
        PregameClassCounts counts
    ) {
        if (counts == null || counts.isEmpty()) {
            return new String[] { "Queue Classes 0/100" };
        }

        List<String> builtLines = new ArrayList<String>();
        builtLines.add("Queue Classes " + counts.getTotalPlayers() + "/100");

        for (ClassCount classCount : sortedClassCounts(counts.getClassCounts())) {
            builtLines.add(
                classCount.megaWallsClass.getDisplayName() +
                    " \u00a7a" +
                    classCount.count
            );
        }

        if (config.pregameClassTrackerShowUnknown && counts.getUnknownPlayers() > 0) {
            builtLines.add("Unknown \u00a7a" + counts.getUnknownPlayers());
        }

        return builtLines.toArray(new String[builtLines.size()]);
    }

    private List<ClassCount> sortedClassCounts(
        Map<MegaWallsClass, Integer> classCounts
    ) {
        if (classCounts == null || classCounts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClassCount> sortedCounts = new ArrayList<ClassCount>();
        for (Map.Entry<MegaWallsClass, Integer> entry : classCounts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                sortedCounts.add(new ClassCount(entry.getKey(), entry.getValue()));
            }
        }

        Collections.sort(
            sortedCounts,
            new Comparator<ClassCount>() {
                @Override
                public int compare(ClassCount left, ClassCount right) {
                    int countCompare = right.count - left.count;
                    if (countCompare != 0) {
                        return countCompare;
                    }

                    return left.megaWallsClass
                        .getDisplayName()
                        .compareTo(right.megaWallsClass.getDisplayName());
                }
            }
        );
        return sortedCounts;
    }

    private String[] getDisplayLines(boolean example) {
        return example ? PREVIEW_LINES : lines;
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

    private static final class ClassCount {

        private final MegaWallsClass megaWallsClass;
        private final int count;

        private ClassCount(MegaWallsClass megaWallsClass, int count) {
            this.megaWallsClass = megaWallsClass;
            this.count = count;
        }
    }
}
