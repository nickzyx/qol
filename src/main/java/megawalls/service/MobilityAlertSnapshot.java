package megawalls.service;

import java.util.UUID;
import megawalls.domain.MegaWallsClass;
import net.minecraft.entity.player.EntityPlayer;

final class MobilityAlertSnapshot {

    final UUID playerId;
    final String playerName;
    final MegaWallsClass megaWallsClass;
    final int distance;
    final int yDifference;
    final float relativeYaw;
    final EntityPlayer player;

    MobilityAlertSnapshot(
        EntityPlayer player,
        MegaWallsClass megaWallsClass,
        int distance,
        int yDifference,
        float relativeYaw
    ) {
        this.player = player;
        this.playerId = player.getUniqueID();
        this.playerName = player.getName();
        this.megaWallsClass = megaWallsClass;
        this.distance = distance;
        this.yDifference = yDifference;
        this.relativeYaw = relativeYaw;
    }
}
