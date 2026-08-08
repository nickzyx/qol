package megawalls.render;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
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
    private static volatile boolean connectedBorders;
    public static final PropertyBool DOWN = PropertyBool.create("down");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyBool WEST = PropertyBool.create("west");
    private static final IProperty[] CONNECTION_PROPERTIES = new IProperty[] {
        DOWN,
        EAST,
        NORTH,
        SOUTH,
        UP,
        WEST,
    };
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
        connectedBorders = config != null && config.barrierConnectedBorders;
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

    public static BlockState createBarrierBlockState(Block block) {
        return new BlockState(block, CONNECTION_PROPERTIES);
    }

    public static IBlockState getBarrierActualState(
        IBlockState state,
        IBlockAccess world,
        BlockPos pos
    ) {
        if (!enabled ||
            !connectedBorders ||
            state == null ||
            world == null ||
            pos == null ||
            !hasConnectionProperties(state)
        ) {
            return withDefaultConnections(state);
        }

        return state
            .withProperty(DOWN, isBarrier(world, pos.down()))
            .withProperty(EAST, isBarrier(world, pos.east()))
            .withProperty(NORTH, isBarrier(world, pos.north()))
            .withProperty(SOUTH, isBarrier(world, pos.south()))
            .withProperty(UP, isBarrier(world, pos.up()))
            .withProperty(WEST, isBarrier(world, pos.west()));
    }

    public static int getBarrierMetaFromState(IBlockState state) {
        return 0;
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
        boolean startupConnected = config != null && config.barrierConnectedBorders;
        String modelPath = getModelPath(startupStyle);
        String connectedModelPath = getConnectedModelPath(startupStyle);
        if (modelLocations == null || modelPath == null) {
            return;
        }

        for (IBlockState state : Blocks.barrier.getBlockState().getValidStates()) {
            ModelResourceLocation location = startupConnected &&
                connectedModelPath != null &&
                hasConnectionProperties(state)
                ? new ModelResourceLocation("minecraft:" + connectedModelPath, getConnectionVariant(state))
                : new ModelResourceLocation("minecraft:" + modelPath, "normal");
            modelLocations.put(state, location);
        }
        if (config != null && config.developerDebugEnabled) {
            modelApplications.incrementAndGet();
        }
    }

    private static IBlockState withDefaultConnections(IBlockState state) {
        if (state == null || !hasConnectionProperties(state)) {
            return state;
        }

        return state
            .withProperty(DOWN, false)
            .withProperty(EAST, false)
            .withProperty(NORTH, false)
            .withProperty(SOUTH, false)
            .withProperty(UP, false)
            .withProperty(WEST, false);
    }

    private static boolean isBarrier(IBlockAccess world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == Blocks.barrier;
    }

    private static String getStateModelPath(
        IBlockState state,
        String defaultModelPath,
        boolean connected
    ) {
        if (!connected || state == null || !hasConnectionProperties(state)) {
            return defaultModelPath;
        }

        String suffix = getConnectionSuffix(state);
        int connectionCount = suffix.length();
        if (connectionCount == 0) {
            return "barrier/barrier0/barrier";
        }
        if (connectionCount >= 5 || isCrossShape(suffix)) {
            return "barrier/barrier6/barrier-DENSUW";
        }
        return "barrier/barrier" + connectionCount + "/barrier-" + suffix;
    }

    private static boolean hasConnectionProperties(IBlockState state) {
        try {
            state.getValue(DOWN);
            state.getValue(EAST);
            state.getValue(NORTH);
            state.getValue(SOUTH);
            state.getValue(UP);
            state.getValue(WEST);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String getConnectionVariant(IBlockState state) {
        return "down=" + state.getValue(DOWN) +
            ",east=" + state.getValue(EAST) +
            ",north=" + state.getValue(NORTH) +
            ",south=" + state.getValue(SOUTH) +
            ",up=" + state.getValue(UP) +
            ",west=" + state.getValue(WEST);
    }

    private static String getConnectionSuffix(IBlockState state) {
        StringBuilder suffix = new StringBuilder(6);
        if (Boolean.TRUE.equals(state.getValue(DOWN))) {
            suffix.append('D');
        }
        if (Boolean.TRUE.equals(state.getValue(EAST))) {
            suffix.append('E');
        }
        if (Boolean.TRUE.equals(state.getValue(NORTH))) {
            suffix.append('N');
        }
        if (Boolean.TRUE.equals(state.getValue(SOUTH))) {
            suffix.append('S');
        }
        if (Boolean.TRUE.equals(state.getValue(UP))) {
            suffix.append('U');
        }
        if (Boolean.TRUE.equals(state.getValue(WEST))) {
            suffix.append('W');
        }
        return suffix.toString();
    }

    private static boolean isCrossShape(String suffix) {
        return "ENSW".equals(suffix) ||
            "DNSU".equals(suffix) ||
            "DEUW".equals(suffix) ||
            "ENSUW".equals(suffix) ||
            "DNSUW".equals(suffix) ||
            "DESUW".equals(suffix) ||
            "DENUW".equals(suffix) ||
            "DENSW".equals(suffix) ||
            "DENSU".equals(suffix);
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

    private static String getConnectedModelPath(int style) {
        switch (style) {
            case 0:
                return "qol_connected_barrier_white";
            case 1:
                return "qol_connected_barrier_red";
            case 2:
                return "qol_connected_barrier_green";
            case 3:
                return "qol_connected_barrier_blue";
            case 4:
                return "qol_connected_barrier_yellow";
            case 5:
                return "qol_connected_barrier_cyan";
            case 6:
                return "qol_connected_barrier_purple";
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
