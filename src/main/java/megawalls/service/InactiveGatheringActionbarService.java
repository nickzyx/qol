package megawalls.service;

import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InactiveGatheringActionbarService {

    private static final Pattern GATHERING_SEGMENT_PATTERN =
        Pattern.compile("(^|\\s+)GATHERING\\s+\\S+(?=\\s|$)");

    public boolean filterActionbar(
        ClientChatReceivedEvent event,
        MegaWallsConfig config,
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService
    ) {
        if (
            event == null ||
            event.message == null ||
            config == null ||
            !config.hideActionBarGathering ||
            event.type != 2 ||
            classResolver == null ||
            contextService == null ||
            !contextService.isInMegaWallsGame()
        ) {
            return false;
        }

        MegaWallsClass localClass = classResolver.resolveLocalClass();
        if (!shouldHideGathering(localClass, contextService)) {
            return false;
        }

        String strippedMessage = StringUtils.stripControlCodes(
            event.message.getUnformattedTextForChat()
        );
        Matcher matcher = GATHERING_SEGMENT_PATTERN.matcher(strippedMessage);
        if (!matcher.find()) {
            return false;
        }

        String filteredMessage = removePlainRange(
            event.message.getFormattedText(),
            matcher.start(),
            matcher.end()
        ).trim();
        if (StringUtils.stripControlCodes(filteredMessage).trim().isEmpty()) {
            event.setCanceled(true);
            return true;
        }

        event.message = new ChatComponentText(filteredMessage);
        return true;
    }

    private boolean shouldHideGathering(
        MegaWallsClass localClass,
        MegaWallsContextService contextService
    ) {
        if (localClass == null) {
            return false;
        }

        switch (localClass) {
            case ENDERMAN:
            case SNOWMAN:
                return contextService.isDeathmatchActive();
            case PHOENIX:
            case PIGMAN:
            case SHARK:
            case SQUID:
                return contextService.isWallsFallenActive();
            default:
                return false;
        }
    }

    private String removePlainRange(String formattedMessage, int plainStart, int plainEnd) {
        int formattedStart = getFormattedIndexForPlainIndex(formattedMessage, plainStart);
        int formattedEnd = getFormattedIndexForPlainIndex(formattedMessage, plainEnd);
        if (formattedStart < 0 || formattedEnd < formattedStart) {
            return formattedMessage;
        }

        return formattedMessage.substring(0, formattedStart) +
            formattedMessage.substring(formattedEnd);
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
