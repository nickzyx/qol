package megawalls.waypoint;

import com.google.common.base.Predicate;
import java.util.List;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public final class MarkerRaycastService {

    public BlockPos resolveLookedAtPosition(Minecraft minecraft, int range) {
        LookTarget target = resolveLookTarget(minecraft, range);
        return target == null ? null : target.getPosition();
    }

    public LookTarget resolveLookTarget(Minecraft minecraft, int range) {
        if (!MinecraftClient.hasWorld(minecraft)) {
            return null;
        }

        int clampedRange = Math.max(4, Math.min(512, range));
        Vec3 eyes = minecraft.thePlayer.getPositionEyes(1.0F);
        Vec3 look = minecraft.thePlayer.getLook(1.0F);
        Vec3 reach = eyes.addVector(
            look.xCoord * clampedRange,
            look.yCoord * clampedRange,
            look.zCoord * clampedRange
        );

        MovingObjectPosition blockHit = minecraft.theWorld.rayTraceBlocks(
            eyes,
            reach,
            false,
            false,
            true
        );

        double maxEntityDistance = clampedRange;
        if (blockHit != null && blockHit.hitVec != null) {
            maxEntityDistance = eyes.distanceTo(blockHit.hitVec);
        }

        EntityPlayer player = resolveLookedAtPlayer(
            minecraft,
            eyes,
            reach,
            look,
            clampedRange,
            maxEntityDistance
        );
        if (player != null) {
            return LookTarget.player(player);
        }

        if (blockHit != null && blockHit.getBlockPos() != null) {
            return LookTarget.block(blockHit.getBlockPos());
        }

        return null;
    }

    private EntityPlayer resolveLookedAtPlayer(
        Minecraft minecraft,
        Vec3 eyes,
        Vec3 reach,
        Vec3 look,
        int range,
        double maxDistance
    ) {
        if (!MinecraftClient.hasWorld(minecraft)) {
            return null;
        }

        AxisAlignedBB searchBox = minecraft.thePlayer
            .getEntityBoundingBox()
            .addCoord(look.xCoord * range, look.yCoord * range, look.zCoord * range)
            .expand(1.0D, 1.0D, 1.0D);
        List<Entity> entities = minecraft.theWorld.getEntitiesInAABBexcluding(
            minecraft.thePlayer,
            searchBox,
            new Predicate<Entity>() {
                @Override
                public boolean apply(Entity entity) {
                    return entity != null && entity.canBeCollidedWith();
                }
            }
        );

        EntityPlayer closestPlayer = null;
        double closestDistance = maxDistance;
        for (Entity entity : entities) {
            if (!(entity instanceof EntityPlayer) || entity == minecraft.thePlayer) {
                continue;
            }

            EntityPlayer player = (EntityPlayer) entity;
            if (player.isDead || player.isInvisible()) {
                continue;
            }

            float border = player.getCollisionBorderSize();
            AxisAlignedBB hitBox = player
                .getEntityBoundingBox()
                .expand(border, border, border);
            MovingObjectPosition hit = hitBox.calculateIntercept(eyes, reach);
            if (hit == null || hit.hitVec == null) {
                continue;
            }

            double distance = eyes.distanceTo(hit.hitVec);
            if (distance < closestDistance) {
                closestPlayer = player;
                closestDistance = distance;
            }
        }

        return closestPlayer;
    }

    public static final class LookTarget {
        private final BlockPos position;
        private final EntityPlayer player;

        private LookTarget(BlockPos position, EntityPlayer player) {
            this.position = position;
            this.player = player;
        }

        static LookTarget block(BlockPos position) {
            return new LookTarget(position, null);
        }

        static LookTarget player(EntityPlayer player) {
            return new LookTarget(player.getPosition(), player);
        }

        public BlockPos getPosition() {
            return position;
        }

        public EntityPlayer getPlayer() {
            return player;
        }

        public boolean isPlayer() {
            return player != null;
        }
    }
}
