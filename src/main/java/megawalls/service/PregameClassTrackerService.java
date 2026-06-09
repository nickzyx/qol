package megawalls.service;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.ClassSkinRegistry;
import megawalls.domain.MegaWallsClass;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PregameClassTrackerService {

    private static final long REFRESH_INTERVAL_MS = 500L;
    private static final Pattern SKIN_HASH_PATTERN =
        Pattern.compile("textures\\.minecraft\\.net/texture/([A-Fa-f0-9]+)");

    private PregameClassCounts latestCounts = PregameClassCounts.empty();
    private long nextRefreshAtMs;

    public void onClientTick(
        Minecraft minecraft,
        MegaWallsConfig config,
        MegaWallsContextService contextService
    ) {
        if (
            minecraft == null ||
            config == null ||
            contextService == null ||
            !config.pregameClassTrackerEnabled ||
            !contextService.isInPreGameQueue()
        ) {
            reset();
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs < nextRefreshAtMs) {
            return;
        }

        nextRefreshAtMs = nowMs + REFRESH_INTERVAL_MS;
        latestCounts = scanTablist();
    }

    public PregameClassCounts getLatestCounts() {
        return latestCounts;
    }

    public void reset() {
        latestCounts = PregameClassCounts.empty();
        nextRefreshAtMs = 0L;
    }

    private PregameClassCounts scanTablist() {
        Collection<NetworkPlayerInfo> players = MinecraftClient.playerInfoMap();
        if (players == null || players.isEmpty()) {
            return PregameClassCounts.empty();
        }

        EnumMap<MegaWallsClass, Integer> classCounts =
            new EnumMap<MegaWallsClass, Integer>(MegaWallsClass.class);
        int knownPlayers = 0;
        int unknownPlayers = 0;

        for (NetworkPlayerInfo playerInfo : players) {
            MegaWallsClass megaWallsClass = resolveClass(playerInfo);
            if (megaWallsClass == null) {
                unknownPlayers++;
                continue;
            }

            Integer currentCount = classCounts.get(megaWallsClass);
            classCounts.put(
                megaWallsClass,
                Integer.valueOf(currentCount == null ? 1 : currentCount + 1)
            );
            knownPlayers++;
        }

        return new PregameClassCounts(classCounts, knownPlayers, unknownPlayers);
    }

    private MegaWallsClass resolveClass(NetworkPlayerInfo playerInfo) {
        if (playerInfo == null) {
            return null;
        }

        String skinHash = extractSkinHash(playerInfo.getGameProfile());
        return ClassSkinRegistry.getClassForSkinHash(skinHash);
    }

    private String extractSkinHash(GameProfile profile) {
        if (profile == null || profile.getProperties() == null) {
            return "";
        }

        Collection<Property> textures = profile.getProperties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return "";
        }

        for (Property texture : textures) {
            String skinHash = extractSkinHash(texture);
            if (!skinHash.isEmpty()) {
                return skinHash;
            }
        }

        return "";
    }

    private String extractSkinHash(Property texture) {
        if (texture == null || texture.getValue() == null) {
            return "";
        }

        try {
            String decodedTexture = new String(
                DatatypeConverter.parseBase64Binary(texture.getValue()),
                StandardCharsets.UTF_8
            );
            Matcher matcher = SKIN_HASH_PATTERN.matcher(decodedTexture);
            return matcher.find()
                ? matcher.group(1).toLowerCase(Locale.ROOT)
                : "";
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }
}
