package megawalls.render;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;

public final class BarrierBlockReplacement {

    private static volatile boolean enabled;
    private static volatile boolean performanceLogging;
    private static volatile int renderStyle;
    private static final AtomicLong renderTypeCalls = new AtomicLong();
    private static final AtomicLong blockLayerCalls = new AtomicLong();
    private static final AtomicLong sideCalls = new AtomicLong();
    private static final AtomicLong sideCulled = new AtomicLong();
    private static final AtomicLong stateMapperCalls = new AtomicLong();
    private static final AtomicLong stateMapperBarrierIncluded = new AtomicLong();
    private static final AtomicLong modelApplications = new AtomicLong();

    private BarrierBlockReplacement() {}

    public static void updateFromConfig(MegaWallsConfig config) {
        enabled = config != null && config.visibleBarriers;
        performanceLogging = config != null && config.developerDebugEnabled;
        renderStyle = config == null ? 0 : config.barrierRenderStyle;
    }

    public static int getBarrierRenderType(int originalRenderType) {
        if (performanceLogging) {
            renderTypeCalls.incrementAndGet();
        }

        if (!enabled) {
            return originalRenderType;
        }

        return 3;
    }

    public static EnumWorldBlockLayer getBarrierBlockLayer() {
        if (performanceLogging) {
            blockLayerCalls.incrementAndGet();
        }

        if (!enabled) {
            return EnumWorldBlockLayer.SOLID;
        }

        return EnumWorldBlockLayer.CUTOUT;
    }

    public static boolean shouldRenderBarrierSide(
        boolean original,
        IBlockAccess world,
        BlockPos pos,
        EnumFacing side,
        Block block
    ) {
        if (performanceLogging) {
            sideCalls.incrementAndGet();
        }

        if (!enabled || world == null || pos == null || block == null) {
            return original;
        }

        boolean render = world.getBlockState(pos).getBlock() != block && original;
        if (performanceLogging && !render) {
            sideCulled.incrementAndGet();
        }
        return render;
    }

    public static boolean shouldTreatAsBuiltInBlock(Set set, Object block) {
        if (performanceLogging) {
            stateMapperCalls.incrementAndGet();
        }

        if (block == Blocks.barrier) {
            if (performanceLogging) {
                stateMapperBarrierIncluded.incrementAndGet();
            }
            return false;
        }

        return set != null && set.contains(block);
    }

    public static void applyBarrierModelLocations(Map<IBlockState, ModelResourceLocation> modelLocations) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        int startupStyle = config == null ? 0 : config.barrierRenderStyle;
        String modelPath = getModelPath(startupStyle);
        if (modelLocations == null || modelPath == null) {
            return;
        }

        modelLocations.put(
            Blocks.barrier.getDefaultState(),
            new ModelResourceLocation("minecraft:" + modelPath, "normal")
        );
        if (config != null && config.developerDebugEnabled) {
            modelApplications.incrementAndGet();
        }
    }

    private static String getModelPath(int style) {
        switch (style) {
            case 0:
                return "qol_barrier_white";
            case 1:
                return "qol_barrier_red";
            case 2:
                return "qol_barrier_green";
            case 3:
                return "qol_barrier_blue";
            case 4:
                return "qol_barrier_yellow";
            case 5:
                return "qol_barrier_cyan";
            case 6:
                return "qol_barrier_purple";
            default:
                return null;
        }
    }

    public static BarrierPerformanceSnapshot drainPerformanceSnapshot() {
        return new BarrierPerformanceSnapshot(
            enabled,
            renderStyle,
            renderTypeCalls.getAndSet(0L),
            blockLayerCalls.getAndSet(0L),
            sideCalls.getAndSet(0L),
            sideCulled.getAndSet(0L),
            stateMapperCalls.getAndSet(0L),
            stateMapperBarrierIncluded.getAndSet(0L),
            modelApplications.getAndSet(0L)
        );
    }

    public static final class BarrierPerformanceSnapshot {

        public final boolean enabled;
        public final int renderStyle;
        public final long renderTypeCalls;
        public final long blockLayerCalls;
        public final long sideCalls;
        public final long sideCulled;
        public final long stateMapperCalls;
        public final long stateMapperBarrierIncluded;
        public final long modelApplications;

        private BarrierPerformanceSnapshot(
            boolean enabled,
            int renderStyle,
            long renderTypeCalls,
            long blockLayerCalls,
            long sideCalls,
            long sideCulled,
            long stateMapperCalls,
            long stateMapperBarrierIncluded,
            long modelApplications
        ) {
            this.enabled = enabled;
            this.renderStyle = renderStyle;
            this.renderTypeCalls = renderTypeCalls;
            this.blockLayerCalls = blockLayerCalls;
            this.sideCalls = sideCalls;
            this.sideCulled = sideCulled;
            this.stateMapperCalls = stateMapperCalls;
            this.stateMapperBarrierIncluded = stateMapperBarrierIncluded;
            this.modelApplications = modelApplications;
        }

        public boolean isEmpty() {
            return renderTypeCalls == 0L &&
                blockLayerCalls == 0L &&
                sideCalls == 0L &&
                sideCulled == 0L &&
                stateMapperCalls == 0L &&
                stateMapperBarrierIncluded == 0L &&
                modelApplications == 0L;
        }
    }
}
