package megawalls.service;

import megawalls.config.MegaWallsConfig;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

final class InteractionGuardService {

    void onPlayerInteract(PlayerInteractEvent event, MegaWallsConfig config) {
        if (
                event == null ||
                event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK ||
                config == null ||
                !config.swordInteractionGuard ||
                event.entityPlayer == null ||
                event.world == null ||
                event.pos == null
        ) {
            return;
        }

        ItemStack heldItem = event.entityPlayer.getHeldItem();
        if (!shouldBlockGuardedInteraction(config, heldItem)) {
            return;
        }

        Block block = event.world.getBlockState(event.pos).getBlock();
        if (!isGuardedInteractionBlock(block)) {
            return;
        }

        event.useBlock = Event.Result.DENY;
        event.useItem = Event.Result.DENY;
        event.setCanceled(true);
    }

    private boolean isGuardedInteractionBlock(Block block) {
        return block == Blocks.crafting_table ||
                block == Blocks.chest ||
                block == Blocks.trapped_chest ||
                block == Blocks.furnace ||
                block == Blocks.lit_furnace ||
                block == Blocks.hopper;
    }

    private boolean shouldBlockGuardedInteraction(
            MegaWallsConfig config,
            ItemStack heldItem
    ) {
        if (config.interactionGuardEmptyHandOnly) {
            return heldItem != null;
        }

        return heldItem != null && heldItem.getItem() instanceof ItemSword;
    }
}
