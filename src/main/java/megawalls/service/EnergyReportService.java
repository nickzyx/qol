package megawalls.service;

import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

final class EnergyReportService {

    private final MegaWallsClassResolver classResolver;

    EnergyReportService(MegaWallsClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    void reportEnergyNow() {
        Minecraft minecraft = Minecraft.getMinecraft();
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (minecraft == null || minecraft.thePlayer == null || config == null) {
            return;
        }

        MegaWallsClass megaWallsClass = classResolver.resolveMegaWallsClass(minecraft.thePlayer);
        if (megaWallsClass == null) {
            ChatNotifier.warn("Unable to resolve your Mega Walls class.");
            return;
        }

        if (minecraft.thePlayer.capabilities.isFlying || minecraft.thePlayer.isInvisible()) {
            return;
        }

        int energy = minecraft.thePlayer.experienceLevel;
        String label = getReportLabel(config, megaWallsClass, energy);
        EnumChatFormatting energyColor = getEnergyColor(energy);

        if (megaWallsClass.isAbilityReady(energy)) {
            ChatNotifier.success(label + " ready at " + energyColor + energy + EnumChatFormatting.GREEN + " energy.");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(label)
                .append(" energy: ")
                .append(energyColor)
                .append(energy)
                .append(EnumChatFormatting.GRAY)
                .append("/100");

        if (config.showHitsNeeded) {
            int preferredGain = Math.max(1, megaWallsClass.getPreferredEnergyGain());
            int hitsNeeded = (int) Math.ceil((100.0D - energy) / (double) preferredGain);
            messageBuilder.append(EnumChatFormatting.GRAY)
                    .append(" (")
                    .append(hitsNeeded)
                    .append(' ')
                    .append(megaWallsClass.getPreferredEnergyMode())
                    .append(hitsNeeded == 1 ? " hit" : " hits")
                    .append(')');
        }

        ChatNotifier.info(messageBuilder.toString());
    }

    private EnumChatFormatting getEnergyColor(int energy) {
        if (energy >= 80) {
            return EnumChatFormatting.GREEN;
        }
        if (energy >= 50) {
            return EnumChatFormatting.YELLOW;
        }
        return EnumChatFormatting.RED;
    }

    private String getReportLabel(MegaWallsConfig config, MegaWallsClass megaWallsClass, int energy) {
        return config.showAbilityName
                ? megaWallsClass.getAbilityName(energy)
                : megaWallsClass.getDisplayName();
    }
}
