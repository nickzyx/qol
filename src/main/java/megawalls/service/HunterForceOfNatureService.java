package megawalls.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import megawalls.config.MegaWallsConfig;
import megawalls.util.MinecraftClient;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public final class HunterForceOfNatureService {

    private static final Pattern ACTIONBAR_PATTERN = Pattern.compile("F\\.O\\.N\\.(?:\\s+\\([^)]*\\)\\s+\\d+|\\s+\\d+)?");
    private static final Pattern UPCOMING_ACTIONBAR_PATTERN = Pattern.compile(
        "F\\.O\\.N\\.\\s+\\(([^)]*)\\)\\s+(\\d+)"
    );
    private static final Pattern COOLDOWN_ACTIONBAR_PATTERN = Pattern.compile("F\\.O\\.N\\.\\s+(\\d+)");
    private static final long ROLL_ANIMATION_MILLIS = 4000L;
    private static final long ROLL_FRAME_MILLIS = 160L;
    private static final long ACTIONBAR_DISPLAY_MILLIS = 1500L;
    private static final long RESULT_DISPLAY_MILLIS = 3000L;
    private static final String GREEN = "\u00a7a";
    private static final String BLUE = "\u00a79";
    private static final String RED = "\u00a7c";
    private static final String GOLD = "\u00a76";
    private static final String GRAY = "\u00a77";
    private static final String YELLOW = "\u00a7e";
    private static final String PINK = "\u00a7d";
    private static final String WHITE = "\u00a7f";
    private static final String BOLD = "\u00a7l";
    private static final String RESET = "\u00a7r";
    private static final String DEFAULT_ROLL_SOUND = "gui.button.press";
    private static final String CUSTOM_ROLL_SOUND = "qol:hunter_fon_roll";
    private static final String[] SLOT_VALUES = new String[] {
        "SPEED&REGEN",
        "RESISTANCE",
        "HASTE",
        "STRENGTH",
        "REGENERATION",
        "ABSORPTION",
        "SPEED",
    };

    private String upcomingBuffDisplay = "";
    private long upcomingExpiresAt;
    private long resultDisplayExpiresAt;
    private long cooldownExpiresAt;
    private long lastActionbarAt;
    private long lastResultSoundRollAt;
    private long lastRollSoundFrame = Long.MIN_VALUE;
    private long lastRollSoundEndsAt;
    private long lastCustomRollSoundEndsAt;
    private String actionbarPrefix = "";
    private String actionbarSuffix = "";

    public boolean onChatReceived(ClientChatReceivedEvent event, MegaWallsConfig config) {
        if (event == null || event.message == null || config == null || !config.hunterFonSlotHud) {
            return false;
        }

        String strippedMessage = StringUtils.stripControlCodes(
            event.message.getUnformattedTextForChat()
        ).trim();

        if (event.type == 2) {
            return handleActionbar(strippedMessage, event.message.getFormattedText(), config);
        }

        return false;
    }

    public void onRenderOverlay(RenderGameOverlayEvent.Text event, MegaWallsConfig config) {
        if (
            event == null ||
            config == null ||
            !config.hunterFonSlotHud ||
            !shouldRender(System.currentTimeMillis())
        ) {
            return;
        }

        Minecraft minecraft = MinecraftClient.forHud();
        if (minecraft == null) {
            return;
        }

        long now = System.currentTimeMillis();
        String fonText = getFonDisplayText(now, config);
        if (config.hunterFonDraggableHud && config.hunterFonHud != null) {
            config.hunterFonHud.renderActive(minecraft, config, fonText);
        }

        String text = actionbarPrefix + actionbarSuffix;
        if (StringUtils.stripControlCodes(text).trim().isEmpty()) {
            if (config.hunterFonDraggableHud) {
                return;
            }
        }

        FontRenderer fontRenderer = minecraft.fontRendererObj;
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int y = getActionbarY(resolution);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (config.hunterFonDraggableHud) {
                int x = (resolution.getScaledWidth() - fontRenderer.getStringWidth(text)) / 2;
                drawText(fontRenderer, text, x, y, config.hunterFonTextShadow);
            } else {
                drawStackedActionbar(
                    fontRenderer,
                    resolution,
                    y,
                    fonText,
                    config.hunterFonTextShadow
                );
            }
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    private void drawStackedActionbar(
        FontRenderer fontRenderer,
        ScaledResolution resolution,
        int y,
        String fonText,
        boolean textShadow
    ) {
        String otherActionbarText = actionbarPrefix + actionbarSuffix;
        int fonX = (resolution.getScaledWidth() - fontRenderer.getStringWidth(fonText)) / 2;
        drawText(fontRenderer, fonText, fonX, y - 10, textShadow);

        if (!StringUtils.stripControlCodes(otherActionbarText).trim().isEmpty()) {
            int otherX = (resolution.getScaledWidth() - fontRenderer.getStringWidth(otherActionbarText)) / 2;
            drawText(fontRenderer, otherActionbarText, otherX, y, textShadow);
        }
    }

    private int getActionbarY(ScaledResolution resolution) {
        int bottomOffset = Math.max(68, GuiIngameForge.left_height);
        return resolution.getScaledHeight() - bottomOffset;
    }

    private void drawText(
        FontRenderer fontRenderer,
        String text,
        int x,
        int y,
        boolean textShadow
    ) {
        if (text == null || text.isEmpty()) {
            return;
        }

        if (textShadow) {
            fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFF);
        } else {
            fontRenderer.drawString(text, x, y, 0xFFFFFF);
        }
    }

    private boolean handleActionbar(String strippedMessage, String formattedMessage, MegaWallsConfig config) {
        Matcher matcher = ACTIONBAR_PATTERN.matcher(strippedMessage);
        if (!matcher.find()) {
            return false;
        }
        long now = System.currentTimeMillis();

        int formattedStart = getFormattedIndexForPlainIndex(formattedMessage, matcher.start());
        int formattedEnd = getFormattedIndexForPlainIndex(formattedMessage, matcher.end());
        if (formattedStart >= 0 && formattedEnd >= formattedStart) {
            actionbarPrefix = formattedMessage.substring(0, formattedStart);
            actionbarSuffix = formattedMessage.substring(formattedEnd);
        } else {
            actionbarPrefix = strippedMessage.substring(0, matcher.start());
            actionbarSuffix = strippedMessage.substring(matcher.end());
        }

        Matcher upcomingMatcher = UPCOMING_ACTIONBAR_PATTERN.matcher(strippedMessage);
        if (upcomingMatcher.find()) {
            String upcomingBuff = normalizeBuffDisplay(upcomingMatcher.group(1));
            int seconds = parseDuration(upcomingMatcher.group(2));
            if (!upcomingBuff.isEmpty() && seconds > 0) {
                String buffDisplay = toKnownBuffDisplay(upcomingBuff);
                if (isNewUpcomingCycle(buffDisplay, now)) {
                    upcomingExpiresAt = now + seconds * 1000L;
                    resultDisplayExpiresAt = upcomingExpiresAt + RESULT_DISPLAY_MILLIS;
                    lastRollSoundFrame = Long.MIN_VALUE;
                    lastRollSoundEndsAt = 0L;
                    lastCustomRollSoundEndsAt = 0L;
                }
                upcomingBuffDisplay = buffDisplay;
                cooldownExpiresAt = 0L;
            }
        } else {
            Matcher cooldownMatcher = COOLDOWN_ACTIONBAR_PATTERN.matcher(strippedMessage);
            if (cooldownMatcher.find()) {
                int seconds = parseDuration(cooldownMatcher.group(1));
                if (seconds > 0) {
                    cooldownExpiresAt = now + seconds * 1000L;
                    if (hasPendingResult()) {
                        upcomingExpiresAt = Math.min(now, upcomingExpiresAt);
                        resultDisplayExpiresAt = now + RESULT_DISPLAY_MILLIS;
                    } else if (!hasResolvedBuff(now)) {
                        upcomingBuffDisplay = "";
                        upcomingExpiresAt = 0L;
                        resultDisplayExpiresAt = 0L;
                    }
                }
            }
        }

        lastActionbarAt = now;
        return true;
    }

    private String getFonDisplayText(long now, MegaWallsConfig config) {
        if (hasUpcomingBuff(now)) {
            long rollStartsAt = upcomingExpiresAt - ROLL_ANIMATION_MILLIS;
            if (now < rollStartsAt) {
                return fonPrefix() + RED + BOLD + secondsRemaining(upcomingExpiresAt, now) + RESET;
            }
            if (now < upcomingExpiresAt) {
                return fonPrefix() + colorizeBuffText(getSlotValue(now, upcomingExpiresAt, config)) + RESET;
            }
        }

        if (hasResolvedBuff(now)) {
            playResultSoundIfNeeded(config);
            return fonPrefix() + colorizeBuffText(upcomingBuffDisplay) + RESET;
        }

        if (cooldownExpiresAt > now) {
            return fonPrefix() + RED + BOLD + secondsRemaining(cooldownExpiresAt, now) + RESET;
        }

        return fonPrefix() + colorizeBuffText(getSlotValue(now, now + ROLL_ANIMATION_MILLIS)) + RESET;
    }

    private String fonPrefix() {
        return GREEN + BOLD + "F.O.N. ";
    }

    private boolean isNewUpcomingCycle(String buffDisplay, long now) {
        return !buffDisplay.equals(upcomingBuffDisplay) ||
            upcomingExpiresAt <= 0L ||
            now > resultDisplayExpiresAt;
    }

    private String getSlotValue(long now, long rollEndsAt, MegaWallsConfig config) {
        long frame = Math.max(0L, (now - (rollEndsAt - ROLL_ANIMATION_MILLIS)) / ROLL_FRAME_MILLIS);
        playRollTickIfNeeded(frame, rollEndsAt, config);
        return SLOT_VALUES[(int) (frame % SLOT_VALUES.length)];
    }

    private String getSlotValue(long now, long rollEndsAt) {
        long frame = Math.max(0L, (now - (rollEndsAt - ROLL_ANIMATION_MILLIS)) / ROLL_FRAME_MILLIS);
        return SLOT_VALUES[(int) (frame % SLOT_VALUES.length)];
    }

    private String colorizeBuffText(String value) {
        if ("SPEED&REGEN".equals(value)) {
            return BLUE + BOLD + "SPEED" + WHITE + BOLD + "&" + PINK + BOLD + "REGEN";
        }
        if ("SPEED".equals(value)) {
            return BLUE + BOLD + value;
        }
        if ("REGENERATION".equals(value)) {
            return PINK + BOLD + value;
        }
        if ("ABSORPTION".equals(value)) {
            return GOLD + BOLD + value;
        }
        if ("RESISTANCE".equals(value)) {
            return GRAY + BOLD + value;
        }
        if ("HASTE".equals(value)) {
            return YELLOW + BOLD + value;
        }
        if ("STRENGTH".equals(value)) {
            return RED + BOLD + value;
        }
        return GOLD + BOLD + (value == null ? "" : value);
    }

    private void playRollTickIfNeeded(long frame, long rollEndsAt, MegaWallsConfig config) {
        if (frame == lastRollSoundFrame && rollEndsAt == lastRollSoundEndsAt) {
            return;
        }

        if (config == null || !config.hunterFonRollSound) {
            lastRollSoundFrame = frame;
            lastRollSoundEndsAt = rollEndsAt;
            return;
        }

        Minecraft minecraft = MinecraftClient.withPlayer();
        if (minecraft == null) {
            return;
        }

        if (isCustomRollSound(config)) {
            if (lastCustomRollSoundEndsAt != rollEndsAt) {
                playClientSound(minecraft, CUSTOM_ROLL_SOUND, 1.0F);
                lastCustomRollSoundEndsAt = rollEndsAt;
            }
            lastRollSoundFrame = frame;
            lastRollSoundEndsAt = rollEndsAt;
            return;
        }

        playClientSound(
            minecraft,
            DEFAULT_ROLL_SOUND,
            1.6F
        );
        lastRollSoundFrame = frame;
        lastRollSoundEndsAt = rollEndsAt;
    }

    private boolean isCustomRollSound(MegaWallsConfig config) {
        return config != null && config.hunterFonRollSoundType == 1;
    }

    private String normalizeBuffDisplay(String value) {
        String normalized = value == null
            ? ""
            : value.trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    private boolean shouldRender(long now) {
        return lastActionbarAt > 0L && now <= lastActionbarAt + ACTIONBAR_DISPLAY_MILLIS;
    }

    private boolean hasUpcomingBuff(long now) {
        return upcomingExpiresAt > now && upcomingBuffDisplay != null && !upcomingBuffDisplay.isEmpty();
    }

    private String toKnownBuffDisplay(String actionbarBuff) {
        switch (actionbarBuff) {
            case "Speed&Reg":
            case "Speed&Regen":
            case "Speed&Regeneration":
                return "SPEED&REGEN";
            case "Regeneration":
                return "REGENERATION";
            case "Speed":
                return "SPEED";
            case "Resistance":
                return "RESISTANCE";
            case "Haste":
                return "HASTE";
            case "Strength":
                return "STRENGTH";
            case "Absorption":
                return "ABSORPTION";
            default:
                return actionbarBuff;
        }
    }

    private boolean hasResolvedBuff(long now) {
        return upcomingExpiresAt > 0L &&
            now >= upcomingExpiresAt &&
            now <= resultDisplayExpiresAt &&
            upcomingBuffDisplay != null &&
            !upcomingBuffDisplay.isEmpty();
    }

    private boolean hasPendingResult() {
        return upcomingExpiresAt > 0L &&
            upcomingBuffDisplay != null &&
            !upcomingBuffDisplay.isEmpty();
    }

    private void playResultSoundIfNeeded(MegaWallsConfig config) {
        if (
            config == null ||
            !config.hunterFonStrengthSound ||
            upcomingExpiresAt == lastResultSoundRollAt
        ) {
            return;
        }

        Minecraft minecraft = MinecraftClient.withPlayer();
        if (minecraft != null) {
            if ("STRENGTH".equals(upcomingBuffDisplay)) {
                playClientSound(minecraft, "mob.wither.spawn", 1.0F);
            } else {
                playClientSound(minecraft, "random.levelup", getResultSoundPitch(upcomingBuffDisplay));
            }
            lastResultSoundRollAt = upcomingExpiresAt;
        }
    }

    private void playClientSound(Minecraft minecraft, String soundName, float pitch) {
        if (minecraft == null || minecraft.getSoundHandler() == null || soundName == null) {
            return;
        }

        minecraft.getSoundHandler().playSound(
            PositionedSoundRecord.create(new ResourceLocation(soundName), pitch)
        );
    }

    private float getResultSoundPitch(String buffDisplay) {
        if ("SPEED&REGEN".equals(buffDisplay)) {
            return 0.7F;
        }
        if ("RESISTANCE".equals(buffDisplay)) {
            return 0.85F;
        }
        if ("HASTE".equals(buffDisplay)) {
            return 1.0F;
        }
        if ("REGENERATION".equals(buffDisplay)) {
            return 1.15F;
        }
        if ("ABSORPTION".equals(buffDisplay)) {
            return 1.3F;
        }
        if ("SPEED".equals(buffDisplay)) {
            return 1.5F;
        }
        return 1.0F;
    }

    private int secondsRemaining(long deadline, long now) {
        return Math.max(0, (int) Math.ceil((deadline - now) / 1000.0D));
    }

    private int parseDuration(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int getFormattedIndexForPlainIndex(String formatted, int plainIndex) {
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
}
