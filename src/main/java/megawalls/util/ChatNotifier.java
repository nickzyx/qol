package megawalls.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public final class ChatNotifier {

    private ChatNotifier() {}

    public static void info(String message) {
        send(message, EnumChatFormatting.WHITE);
    }

    public static void success(String message) {
        send(message, EnumChatFormatting.GREEN);
    }

    public static void warn(String message) {
        send(message, EnumChatFormatting.RED);
    }

    public static void toggle(String label, boolean enabled) {
        IChatComponent component = createPrefixedMessage();
        component.appendSibling(styled(label + " ", EnumChatFormatting.WHITE, false));
        component.appendSibling(
            styled(
                enabled ? "enabled." : "disabled.",
                enabled ? EnumChatFormatting.GREEN : EnumChatFormatting.RED,
                false
            )
        );
        send(component);
    }

    private static void send(String message, EnumChatFormatting messageColor) {
        send(createMessage(message, messageColor));
    }

    private static void send(IChatComponent message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (
            message == null || minecraft == null || minecraft.ingameGUI == null
        ) {
            return;
        }

        if (minecraft.isCallingFromMinecraftThread()) {
            minecraft.ingameGUI.getChatGUI().printChatMessage(message);
        } else {
            minecraft.addScheduledTask(
                new Runnable() {
                    @Override
                    public void run() {
                        if (minecraft.ingameGUI != null) {
                            minecraft.ingameGUI
                                .getChatGUI()
                                .printChatMessage(message);
                        }
                    }
                }
            );
        }
    }

    private static IChatComponent createMessage(
        String message,
        EnumChatFormatting messageColor
    ) {
        IChatComponent component = createPrefixedMessage();
        component.appendSibling(styled(message, messageColor, false));
        return component;
    }

    private static IChatComponent createPrefixedMessage() {
        IChatComponent component = new ChatComponentText("");
        component.appendSibling(styled("[", EnumChatFormatting.AQUA, false));
        component.appendSibling(
            styled("qol", EnumChatFormatting.DARK_PURPLE, true)
        );
        component.appendSibling(styled("] ", EnumChatFormatting.AQUA, false));
        return component;
    }

    private static IChatComponent styled(
        String text,
        EnumChatFormatting color,
        boolean bold
    ) {
        ChatComponentText component = new ChatComponentText(text);
        ChatStyle style = component.getChatStyle();
        style.setColor(color);
        style.setBold(bold);
        return component;
    }
}
