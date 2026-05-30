package megawalls;

import java.io.File;
import megawalls.config.MegaWallsConfig;
import megawalls.network.ClientboundPacketObserver;
import megawalls.service.MegaWallsService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = MegaWallsMod.MOD_ID,
    name = MegaWallsMod.MOD_NAME,
    version = MegaWallsMod.VERSION,
    acceptedMinecraftVersions = "[1.8.9]",
    clientSideOnly = true
)
public final class MegaWallsMod {

    public static final String MOD_ID = "qol";
    public static final String MOD_NAME = "qol";
    public static final String VERSION = "1.1.0";

    public static MegaWallsConfig config;
    private static File sourceFile;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        sourceFile = event.getSourceFile();
        config = new MegaWallsConfig();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(MegaWallsService.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ClientboundPacketObserver.INSTANCE);
    }

    public static void reportEnergyNow() {
        MegaWallsService.INSTANCE.reportEnergyNow();
    }

    public static boolean isInMegaWalls() {
        return MegaWallsService.INSTANCE.isInMegaWalls();
    }

    public static MegaWallsConfig getConfig() {
        return config;
    }

    public static File getSourceFile() {
        return sourceFile;
    }
}
