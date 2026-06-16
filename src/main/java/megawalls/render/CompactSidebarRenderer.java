package megawalls.render;

import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.service.MegaWallsService;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CompactSidebarRenderer {

    private static final int MAX_LINES = 15;
    private static final long DEBUG_INTERVAL_MS = 1000L;
    private static long nextDebugLogAt;

    private CompactSidebarRenderer() {}

    public static boolean renderCompactSidebar(
        ScoreObjective objective,
        ScaledResolution resolution
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (!shouldRender(objective, resolution, config)) {
            return false;
        }

        Minecraft minecraft = MinecraftClient.forHud();
        if (minecraft == null || minecraft.fontRendererObj == null) {
            return false;
        }

        List<String> lines = getSidebarLines(objective, config.compactSidebar);
        if (lines.isEmpty()) {
            return true;
        }

        if (minecraft.currentScreen != null) {
            if (config.compactSidebarHud != null) {
                config.compactSidebarHud.setSidebar(objective.getDisplayName(), lines);
            }
            return true;
        }

        GlStateManager.pushMatrix();
        try {
            resetOverlayState();
            if (config.compactSidebarHud != null) {
                config.compactSidebarHud.renderSidebar(objective.getDisplayName(), lines);
            } else {
                drawSidebar(minecraft.fontRendererObj, objective.getDisplayName(), lines, resolution);
            }
        } finally {
            restoreOverlayState();
            GlStateManager.popMatrix();
        }
        return true;
    }

    private static boolean shouldRender(
        ScoreObjective objective,
        ScaledResolution resolution,
        MegaWallsConfig config
    ) {
        return config != null &&
            config.compactSidebar &&
            objective != null &&
            resolution != null;
    }

    private static List<String> getSidebarLines(
        ScoreObjective objective,
        boolean compact
    ) {
        Scoreboard scoreboard = objective.getScoreboard();
        Collection<Score> sortedScores = scoreboard.getSortedScores(objective);
        List<Score> visibleScores = new ArrayList<Score>();
        for (Score score : sortedScores) {
            if (
                score != null &&
                score.getPlayerName() != null &&
                !score.getPlayerName().startsWith("#")
            ) {
                visibleScores.add(score);
            }
        }

        if (visibleScores.size() > MAX_LINES) {
            visibleScores = visibleScores.subList(
                visibleScores.size() - MAX_LINES,
                visibleScores.size()
            );
        }

        Collections.reverse(visibleScores);
        List<String> lines = new ArrayList<String>();
        boolean debugThisPass = shouldLogDebug();
        for (Score score : visibleScores) {
            ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
            String formattedLine = ScorePlayerTeam.formatPlayerName(
                team,
                score.getPlayerName()
            );
            boolean hidden = compact && shouldHideLine(formattedLine);
            String rewrittenLine = compact ? rewriteLine(formattedLine) : formattedLine;
            if (debugThisPass) {
                MegaWallsService.INSTANCE.logCompactSidebar(
                    formattedLine,
                    StringUtils.stripControlCodes(formattedLine),
                    hidden,
                    rewrittenLine
                );
            }
            if (!hidden) {
                lines.add(rewrittenLine);
            }
        }

        return lines;
    }

    private static boolean shouldLogDebug() {
        long now = System.currentTimeMillis();
        if (now < nextDebugLogAt) {
            return false;
        }

        nextDebugLogAt = now + DEBUG_INTERVAL_MS;
        return true;
    }

    private static boolean shouldHideLine(String formattedLine) {
        String line = StringUtils.stripControlCodes(formattedLine);
        if (line == null) {
            return true;
        }

        String normalized = normalize(line);
        if (normalized.isEmpty()) {
            return true;
        }

        return alphanumericOnly(normalized).contains("WWWHYPIXELNET");
    }

    private static String rewriteLine(String formattedLine) {
        String line = StringUtils.stripControlCodes(formattedLine);
        if (line == null) {
            return formattedLine;
        }

        String rewrittenLine = formattedLine
            .replace("Enrage Off", "Enrage")
            .replace("ENRAGE OFF", "Enrage")
            .replace("F. Kills", "FK")
            .replace("F. KILLS", "FK")
            .replace("F. Assists", "FA")
            .replace("F. ASSISTS", "FA")
            .replace("Kills", "K")
            .replace("KILLS", "K")
            .replace("Assists", "A")
            .replace("ASSISTS", "A");

        return removePlainWord(rewrittenLine, "Wither");
    }

    private static String removePlainWord(String formattedLine, String word) {
        if (formattedLine == null || word == null || word.isEmpty()) {
            return formattedLine;
        }

        String strippedLine = StringUtils.stripControlCodes(formattedLine);
        int plainStart = strippedLine == null
            ? -1
            : strippedLine.toLowerCase(Locale.ROOT).indexOf(word.toLowerCase(Locale.ROOT));
        if (plainStart < 0) {
            return formattedLine;
        }

        int plainEnd = plainStart + word.length();
        while (plainEnd < strippedLine.length()) {
            char character = strippedLine.charAt(plainEnd);
            if (Character.isLetterOrDigit(character)) {
                break;
            }
            plainEnd++;
        }

        int formattedStart = getFormattedIndexForPlainIndex(formattedLine, plainStart);
        int formattedEnd = getFormattedIndexForPlainIndex(formattedLine, plainEnd);
        if (formattedStart < 0 || formattedEnd < formattedStart) {
            return formattedLine;
        }

        return formattedLine.substring(0, formattedStart) +
            formattedLine.substring(formattedEnd);
    }

    private static int getFormattedIndexForPlainIndex(String formatted, int plainIndex) {
        if (formatted == null) {
            return -1;
        }

        int plain = 0;
        for (int index = 0; index < formatted.length(); index++) {
            char character = formatted.charAt(index);
            if (character == '\u00a7' && index + 1 < formatted.length()) {
                index++;
                continue;
            }

            if (plain == plainIndex) {
                return index;
            }
            plain++;
        }

        return plain == plainIndex ? formatted.length() : -1;
    }

    private static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String upper = value.toUpperCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(upper.length());
        for (int index = 0; index < upper.length(); index++) {
            char character = upper.charAt(index);
            if (!Character.isWhitespace(character)) {
                normalized.append(character);
            }
        }

        return normalized.toString();
    }

    private static String alphanumericOnly(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder cleaned = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                cleaned.append(character);
            }
        }

        return cleaned.toString();
    }

    private static void resetOverlayState() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            770,
            771,
            1,
            0
        );
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void restoreOverlayState() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
    }

    private static void drawSidebar(
        FontRenderer fontRenderer,
        String title,
        List<String> lines,
        ScaledResolution resolution
    ) {
        int maxWidth = fontRenderer.getStringWidth(title);
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, fontRenderer.getStringWidth(line));
        }

        int lineHeight = fontRenderer.FONT_HEIGHT;
        int lineCount = lines.size();
        int left = resolution.getScaledWidth() - maxWidth - 3;
        int right = resolution.getScaledWidth() - 3;
        int bottom = resolution.getScaledHeight() / 2 + lineCount * lineHeight / 3;

        for (int index = 0; index < lineCount; index++) {
            String line = lines.get(index);
            int y = bottom - (lineCount - 1 - index) * lineHeight;
            int top = y - lineHeight;
            Gui.drawRect(left - 2, top, right, y, 0x50000000);
            fontRenderer.drawString(line, left, top, 0xFFFFFF);
        }

        int titleTop = bottom - lineCount * lineHeight;
        Gui.drawRect(left - 2, titleTop - lineHeight, right, titleTop, 0x50000000);
        fontRenderer.drawString(
            title,
            left + maxWidth / 2 - fontRenderer.getStringWidth(title) / 2,
            titleTop - lineHeight,
            0xFFFFFF
        );
    }
}
