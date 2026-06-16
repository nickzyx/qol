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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PregameClassTrackerHud extends BasicHud {

    @Exclude
    private static final String[] PREVIEW_LINES = new String[] {
        "24/100",
        "Cow 4",
        "Hunter 3",
        "Zombie 3",
        "Unknown 8",
    };

    @Exclude
    private static final int LINE_HEIGHT = 10;
    @Exclude
    private static final int ICON_SIZE = 8;
    @Exclude
    private static final int ICON_GAP = 3;
    @Exclude
    private static final Map<MegaWallsClass, ResourceLocation> CLASS_ICONS =
        createClassIcons();
    @Exclude
    private static final ResourceLocation UNKNOWN_ICON =
        new ResourceLocation("qol", "textures/class_icons/unknown.png");

    @Exclude
    private transient String[] lines = new String[0];
    @Exclude
    private transient PregameClassCounts latestCounts = PregameClassCounts.empty();
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
        this.latestCounts = counts == null ? PregameClassCounts.empty() : counts;
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

        if (shouldDrawClassIcons(config, example)) {
            drawIconRows(matrices, x, y, scale, textType, example);
            return;
        }

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
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (shouldDrawClassIcons(config, example)) {
            return getIconRowsWidth(example) * scale;
        }

        float width = 0.0F;
        for (String line : getDisplayLines(example)) {
            width = Math.max(width, getTextWidth(line));
        }

        return width * scale;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (shouldDrawClassIcons(config, example)) {
            return getIconRowsCount(example) * LINE_HEIGHT * scale;
        }

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
            this.latestCounts = PregameClassCounts.empty();
        }
    }

    private void drawIconRows(
        UMatrixStack matrices,
        float x,
        float y,
        float scale,
        TextRenderer.TextType textType,
        boolean example
    ) {
        TextRenderer.drawScaledString(
            getHeaderLine(example),
            x,
            y,
            0x55FFFF,
            textType,
            scale
        );

        List<ClassCount> classCounts = getIconClassCounts(example);
        for (int index = 0; index < classCounts.size(); index++) {
            ClassCount classCount = classCounts.get(index);
            float rowY = y + ((index + 1) * LINE_HEIGHT * scale);
            drawClassIcon(classCount.megaWallsClass, x, rowY, scale);
            TextRenderer.drawScaledString(
                "\u00a7a" + classCount.count,
                x + ((ICON_SIZE + ICON_GAP) * scale),
                rowY,
                0x55FF55,
                textType,
                scale
            );
        }

        if (shouldShowUnknown(example)) {
            int rowIndex = classCounts.size() + 1;
            float rowY = y + (rowIndex * LINE_HEIGHT * scale);
            drawIcon(UNKNOWN_ICON, x, rowY, scale);
            TextRenderer.drawScaledString(
                "\u00a7a" + getUnknownCount(example),
                x + ((ICON_SIZE + ICON_GAP) * scale),
                rowY,
                0x55FF55,
                textType,
                scale
            );
        }
    }

    private void drawClassIcon(MegaWallsClass megaWallsClass, float x, float y, float scale) {
        Minecraft minecraft = MinecraftClient.forHud();
        ResourceLocation icon = CLASS_ICONS.get(megaWallsClass);
        drawIcon(icon, x, y, scale);
    }

    private void drawIcon(ResourceLocation icon, float x, float y, float scale) {
        Minecraft minecraft = MinecraftClient.forHud();
        if (minecraft == null || icon == null) {
            return;
        }

        minecraft.getTextureManager().bindTexture(icon);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            Gui.drawModalRectWithCustomSizedTexture(
                0,
                0,
                0.0F,
                0.0F,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE,
                ICON_SIZE
            );
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private String[] buildLines(
        MegaWallsConfig config,
        PregameClassCounts counts
    ) {
        if (counts == null || counts.isEmpty()) {
            return new String[] { "0/100" };
        }

        List<String> builtLines = new ArrayList<String>();
        builtLines.add(counts.getTotalPlayers() + "/100");

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

    private String getHeaderLine(boolean example) {
        if (example) {
            return PREVIEW_LINES[0];
        }

        if (latestCounts == null || latestCounts.isEmpty()) {
            return "0/100";
        }

        return latestCounts.getTotalPlayers() + "/100";
    }

    private boolean shouldDrawClassIcons(MegaWallsConfig config, boolean example) {
        return config != null && config.pregameClassTrackerClassIcons;
    }

    private List<ClassCount> getIconClassCounts(boolean example) {
        if (example) {
            List<ClassCount> exampleCounts = new ArrayList<ClassCount>();
            exampleCounts.add(new ClassCount(MegaWallsClass.COW, 4));
            exampleCounts.add(new ClassCount(MegaWallsClass.HUNTER, 3));
            exampleCounts.add(new ClassCount(MegaWallsClass.ZOMBIE, 3));
            return exampleCounts;
        }

        if (latestCounts == null) {
            return Collections.emptyList();
        }

        return sortedClassCounts(latestCounts.getClassCounts());
    }

    private boolean shouldShowUnknown(boolean example) {
        if (example) {
            return true;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null &&
            config.pregameClassTrackerShowUnknown &&
            latestCounts != null &&
            latestCounts.getUnknownPlayers() > 0;
    }

    private int getUnknownCount(boolean example) {
        if (example) {
            return 8;
        }

        return latestCounts == null ? 0 : latestCounts.getUnknownPlayers();
    }

    private float getIconRowsWidth(boolean example) {
        float width = getTextWidth(getHeaderLine(example));
        for (ClassCount classCount : getIconClassCounts(example)) {
            width = Math.max(
                width,
                ICON_SIZE + ICON_GAP + getTextWidth("\u00a7a" + classCount.count)
            );
        }

        if (shouldShowUnknown(example)) {
            width = Math.max(width, getTextWidth("Unknown \u00a7a" + getUnknownCount(example)));
        }

        return width;
    }

    private int getIconRowsCount(boolean example) {
        int rows = 1 + getIconClassCounts(example).size();
        if (shouldShowUnknown(example)) {
            rows++;
        }

        return rows;
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

    private static Map<MegaWallsClass, ResourceLocation> createClassIcons() {
        EnumMap<MegaWallsClass, ResourceLocation> icons =
            new EnumMap<MegaWallsClass, ResourceLocation>(MegaWallsClass.class);
        for (MegaWallsClass megaWallsClass : MegaWallsClass.values()) {
            icons.put(
                megaWallsClass,
                new ResourceLocation(
                    "qol",
                    "textures/class_icons/" +
                        megaWallsClass.getDisplayName().toLowerCase() +
                        ".png"
                )
            );
        }

        return Collections.unmodifiableMap(icons);
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
