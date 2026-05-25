package megawalls.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

@IFMLLoadingPlugin.Name("QOL Core")
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.TransformerExclusions({
        "megawalls.core"
})
public class CorePlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("QOL");
    private static boolean obfuscatedEnvironment;

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{"megawalls.core.Transformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        obfuscatedEnvironment = Boolean.TRUE.equals(data.get("runtimeDeobfuscationEnabled"));
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    public static boolean isObfuscatedEnvironment() {
        return obfuscatedEnvironment;
    }
}
