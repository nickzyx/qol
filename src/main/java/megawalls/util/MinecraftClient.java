package megawalls.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.NetworkManager;

import java.util.Collection;
import java.util.UUID;

public final class MinecraftClient {

    private MinecraftClient() {}

    public static Minecraft get() {
        return Minecraft.getMinecraft();
    }

    public static Minecraft withPlayer() {
        Minecraft minecraft = get();
        return hasPlayer(minecraft) ? minecraft : null;
    }

    public static Minecraft withWorld() {
        Minecraft minecraft = withPlayer();
        return hasWorld(minecraft) ? minecraft : null;
    }

    public static Minecraft withLoadedWorld() {
        Minecraft minecraft = get();
        return hasLoadedWorld(minecraft) ? minecraft : null;
    }

    public static NetHandlerPlayClient netHandler() {
        Minecraft minecraft = get();
        return minecraft == null ? null : minecraft.getNetHandler();
    }

    public static NetworkManager networkManager() {
        NetHandlerPlayClient netHandler = netHandler();
        return netHandler == null ? null : netHandler.getNetworkManager();
    }

    public static NetworkPlayerInfo playerInfo(UUID playerId) {
        NetHandlerPlayClient netHandler = netHandler();
        return netHandler == null || playerId == null ? null : netHandler.getPlayerInfo(playerId);
    }

    public static Collection<NetworkPlayerInfo> playerInfoMap() {
        NetHandlerPlayClient netHandler = netHandler();
        return netHandler == null ? null : netHandler.getPlayerInfoMap();
    }

    public static Minecraft forHud() {
        Minecraft minecraft = withPlayer();
        return hasHud(minecraft) ? minecraft : null;
    }

    public static Minecraft forWorldRender() {
        return withWorld();
    }

    public static boolean hasPlayer(Minecraft minecraft) {
        return minecraft != null && minecraft.thePlayer != null;
    }

    public static boolean hasWorld(Minecraft minecraft) {
        return hasPlayer(minecraft) && minecraft.theWorld != null;
    }

    public static boolean hasLoadedWorld(Minecraft minecraft) {
        return minecraft != null && minecraft.theWorld != null;
    }

    public static boolean hasHud(Minecraft minecraft) {
        return hasPlayer(minecraft) && minecraft.fontRendererObj != null;
    }

    public static boolean hasWorldHud(Minecraft minecraft) {
        return hasWorld(minecraft) && minecraft.fontRendererObj != null;
    }

    public static boolean hasRenderGlobal(Minecraft minecraft) {
        return hasWorld(minecraft) && minecraft.renderGlobal != null;
    }
}
