package megawalls.service;

import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;

final class EnergyReportService {

    private final MegaWallsClassResolver classResolver;

    EnergyReportService(MegaWallsClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    void reportEnergyNow() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
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
        minecraft.thePlayer.sendChatMessage(
            megaWallsClass.getAbilityName(energy) + " is at " + energy + "%"
        );
    }
}
