package megawalls.service;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.UUID;

final class PacketObservationService {

    private final PlayerTrackingService playerTrackingService;

    PacketObservationService(PlayerTrackingService playerTrackingService) {
        this.playerTrackingService = playerTrackingService;
    }

    void observeTabProfile(UUID playerId, String profileName) {
        observeTabProfile(playerId, profileName, null);
    }

    void observeTabProfile(UUID playerId, String profileName, String renderedName) {
        playerTrackingService.observeTabProfile(playerId, profileName, renderedName);
    }

    void observeEntityMetadata(int entityId, float health) {
        EntityPlayer player = resolvePlayerEntity(entityId);
        if (player != null) {
            playerTrackingService.observeEntityMetadata(player, health);
        }
    }

    void observeEquipmentPacket(int entityId, int equipmentSlot, ItemStack itemStack) {
        EntityPlayer player = resolvePlayerEntity(entityId);
        if (player != null) {
            playerTrackingService.observeEquipmentPacket(player, equipmentSlot, itemStack);
        }
    }

    void observeEntityEffect(int entityId, int effectId, int durationTicks) {
        EntityPlayer player = resolvePlayerEntity(entityId);
        if (player != null) {
            playerTrackingService.observeEntityEffect(player, effectId, durationTicks);
        }
    }

    void observeEntityEffectRemoved(int entityId, int effectId) {
        EntityPlayer player = resolvePlayerEntity(entityId);
        if (player != null) {
            playerTrackingService.observeEntityEffectRemoved(player, effectId);
        }
    }

    private EntityPlayer resolvePlayerEntity(int entityId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return null;
        }

        net.minecraft.entity.Entity entity = minecraft.theWorld.getEntityByID(entityId);
        return entity instanceof EntityPlayer ? (EntityPlayer) entity : null;
    }
}
