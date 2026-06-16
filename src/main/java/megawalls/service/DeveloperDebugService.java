package megawalls.service;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import megawalls.MegaWallsMod;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.MegaWallsClass;
import megawalls.render.BarrierBlockReplacement.BarrierPerformanceSnapshot;
import megawalls.util.MinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

public final class DeveloperDebugService {

    private static final long SNAPSHOT_INTERVAL_MS = 1000L;
    private static final Pattern SKIN_URL_PATTERN =
        Pattern.compile("\"url\"\\s*:\\s*\"([^\"]*textures\\.minecraft\\.net/texture/[^\"]+)\"");
    private static final Pattern PROFILE_NAME_PATTERN =
        Pattern.compile("\"profileName\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEXTURE_HASH_PATTERN =
        Pattern.compile("/texture/([A-Za-z0-9]+)");
    private static final Pattern SKIN_PREVIEW_CHAT_PATTERN =
        Pattern.compile("You are previewing the (.+?) Skin!");
    private static final int LOBBY_SKIN_PREVIEW_ENTITY_ID = 4;
    private static final SimpleDateFormat FILE_DATE_FORMAT =
        new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final SimpleDateFormat LINE_DATE_FORMAT =
        new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    private PrintWriter writer;
    private File logFile;
    private long nextSnapshotAt;
    private long nextBarrierPerformanceAt;
    private String lastLobbySkinPreviewKey = "";
    private String pendingLobbySkinPreviewName = "";
    private String lastLobbySkinPreviewMapKey = "";
    private boolean wasEnabled;

    void onClientTick(
        Minecraft minecraft,
        MegaWallsContextService contextService,
        MegaWallsClassResolver classResolver
    ) {
        if (!isEnabled()) {
            closeIfDisabled();
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextSnapshotAt) {
            return;
        }

        nextSnapshotAt = now + SNAPSHOT_INTERVAL_MS;
        log(
            "context",
            "megaWallsLobby=" + safeBoolean(contextService.isInMegaWallsLobby()) +
                " preGameQueue=" + safeBoolean(contextService.isInPreGameQueue()) +
                " megaWallsGame=" + safeBoolean(contextService.isInMegaWallsGame()) +
                " trackingActive=" + safeBoolean(contextService.isTrackingActive()) +
                " wallsFallen=" + safeBoolean(contextService.isWallsFallenActive()) +
                " deathmatch=" + safeBoolean(contextService.isDeathmatchActive()) +
                " localTeamColor=" + printableColor(contextService.getLocalTeamColor())
        );
        logScoreboardSnapshot(minecraft, classResolver);
        logTablistSnapshot(minecraft);
        logLobbySkinPreviewSnapshot(minecraft, contextService);
        logPlayerSnapshot(minecraft, classResolver);
    }

    void logChat(String formattedMessage, String strippedMessage) {
        if (!isEnabled()) {
            return;
        }

        rememberLobbySkinPreviewName(strippedMessage);
        log(
            "chat",
            "formatted=\"" + sanitize(formattedMessage) + "\" stripped=\"" +
                sanitize(strippedMessage) + "\""
        );
    }

    public void logWaypoint(String message) {
        if (!isEnabled()) {
            return;
        }

        log("waypoint", message);
    }

    public void logCompactSidebar(
        String formattedLine,
        String strippedLine,
        boolean hidden,
        String rewrittenLine
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            "compact-sidebar",
            "hidden=" + hidden +
                " formatted=\"" + sanitize(formattedLine) + "\"" +
                " stripped=\"" + sanitize(strippedLine) + "\"" +
                " rewritten=\"" + sanitize(rewrittenLine) + "\""
        );
    }

    void logBarrierPerformance(BarrierPerformanceSnapshot snapshot) {
        if (!isEnabled() || snapshot == null || snapshot.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextBarrierPerformanceAt) {
            return;
        }

        nextBarrierPerformanceAt = now + SNAPSHOT_INTERVAL_MS;
        log(
            "barrier.perf",
            "enabled=" + snapshot.enabled +
                " style=" + snapshot.renderStyle +
                " renderTypeCalls=" + snapshot.renderTypeCalls +
                " blockLayerCalls=" + snapshot.blockLayerCalls +
                " sideCalls=" + snapshot.sideCalls +
                " sideCulled=" + snapshot.sideCulled +
                " stateMapperCalls=" + snapshot.stateMapperCalls +
                " stateMapperBarrierIncluded=" + snapshot.stateMapperBarrierIncluded +
                " modelApplications=" + snapshot.modelApplications
        );
    }

    void logBarrierChunkRefresh(
        boolean visibleBarriers,
        int radius,
        int playerX,
        int playerZ,
        long elapsedNanos
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            "barrier.refresh",
            "visibleBarriers=" + visibleBarriers +
                " radius=" + radius +
                " center=" + playerX + "," + playerZ +
                " elapsedMs=" + round(elapsedNanos / 1000000.0D)
        );
    }

    void logSound(PlaySoundEvent event) {
        if (!isEnabled() || event == null) {
            return;
        }

        String position = event.sound == null
            ? "sound=null"
            : "x=" + event.sound.getXPosF() +
                " y=" + event.sound.getYPosF() +
                " z=" + event.sound.getZPosF() +
                " volume=" + event.sound.getVolume() +
                " pitch=" + event.sound.getPitch();
        log("sound", "name=" + sanitize(event.name) + " " + position);
    }

    void logTabProfilePacket(UUID playerId, String profileName, String renderedName) {
        logPacket(
            "tab-profile",
            "uuid=" + safe(playerId) +
                " profile=" + sanitize(profileName) +
                " rendered=\"" + sanitize(renderedName) + "\""
        );
    }

    void logEntityMetadataPacket(int entityId, float health) {
        logPacket("entity-metadata", "entityId=" + entityId + " health=" + health);
    }

    void logEquipmentPacket(int entityId, int equipmentSlot, ItemStack itemStack) {
        logPacket(
            "equipment",
            "entityId=" + entityId +
                " slot=" + equipmentSlot +
                " item=" + describeItem(itemStack)
        );
    }

    void logEntityEffectPacket(int entityId, int effectId, int durationTicks) {
        logPacket(
            "entity-effect",
            "entityId=" + entityId +
                " effectId=" + effectId +
                " durationTicks=" + durationTicks
        );
    }

    void logEntityEffectRemovedPacket(int entityId, int effectId) {
        logPacket(
            "entity-effect-removed",
            "entityId=" + entityId + " effectId=" + effectId
        );
    }

    void logPacketResolution(String packetType, int entityId, EntityPlayer player) {
        logPacket(
            packetType + "-resolution",
            "entityId=" + entityId +
                " player=" + (player == null ? "null" : sanitize(player.getName())) +
                " uuid=" + (player == null ? "null" : safe(player.getUniqueID()))
        );
    }

    void logPotionObservation(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass,
        Integer previousHealth,
        int currentHealth,
        int healthGain,
        String reason,
        boolean accepted
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            accepted ? "potion-accepted" : "potion-rejected",
            "player=" + sanitize(
                trackedPlayerState == null ? null : trackedPlayerState.profileName
            ) +
                " class=" + (megaWallsClass == null ? "null" : megaWallsClass.name()) +
                " previous=" + (previousHealth == null ? "null" : previousHealth.toString()) +
                " current=" + currentHealth +
                " gain=" + healthGain +
                " remaining=" + (
                    trackedPlayerState == null
                        ? "null"
                        : Integer.toString(trackedPlayerState.remainingPotions)
                ) +
                " potionClass=" + (
                    trackedPlayerState == null || trackedPlayerState.potionClass == null
                        ? "null"
                        : trackedPlayerState.potionClass.name()
                ) +
                " respawnCandidate=" + (
                    trackedPlayerState != null && trackedPlayerState.potionRespawnCandidate
                ) +
                " lowHealthSeen=" + (
                    trackedPlayerState != null && trackedPlayerState.potionLowHealthSeen
                ) +
                " reason=\"" + sanitize(reason) + "\""
        );
    }

    void logPotionHealthMissing(
        TrackedPlayerState trackedPlayerState,
        boolean playerEntityLoaded,
        boolean baselineKept
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            "potion-health-missing",
            "player=" + sanitize(
                trackedPlayerState == null ? null : trackedPlayerState.profileName
            ) +
                " class=" + (
                    trackedPlayerState == null || trackedPlayerState.megaWallsClass == null
                        ? "null"
                        : trackedPlayerState.megaWallsClass.name()
                ) +
                " playerEntityLoaded=" + playerEntityLoaded +
                " baselineKept=" + baselineKept +
                " lastHealth=" + (
                    trackedPlayerState == null ||
                        trackedPlayerState.lastPotionTablistHealth == Integer.MIN_VALUE
                        ? "null"
                        : Integer.toString(trackedPlayerState.lastPotionTablistHealth)
                )
        );
    }

    void logSpiderSoundMatch(
        String soundName,
        EntityPlayer spider,
        double distanceSq
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            "spider-sound-match",
            "sound=" + sanitize(soundName) +
                " player=" + (spider == null ? "null" : sanitize(spider.getName())) +
                " distance=" + (
                    spider == null
                        ? "null"
                        : round(Math.sqrt(distanceSq))
                )
        );
    }

    void logSpiderLeapDecision(
        EntityPlayer spider,
        double alertDistanceSq,
        double deltaY,
        double horizontalSpeed,
        double previousHorizontalSpeed,
        int score,
        boolean hasRecentSound,
        boolean hasVerticalBurst,
        boolean hasHorizontalBurst,
        boolean hasHorizontalIncrease,
        boolean hasHorizontalDeltaIncrease,
        boolean isAirborne,
        String reason,
        boolean accepted
    ) {
        if (!isEnabled()) {
            return;
        }

        log(
            accepted ? "spider-leap-accepted" : "spider-leap-rejected",
            "player=" + (spider == null ? "null" : sanitize(spider.getName())) +
                " distance=" + round(Math.sqrt(alertDistanceSq)) +
                " deltaY=" + round(deltaY) +
                " horizontalSpeed=" + round(horizontalSpeed) +
                " previousHorizontalSpeed=" + round(previousHorizontalSpeed) +
                " score=" + score +
                " sound=" + hasRecentSound +
                " vertical=" + hasVerticalBurst +
                " horizontalBurst=" + hasHorizontalBurst +
                " horizontalIncrease=" + hasHorizontalIncrease +
                " horizontalDeltaIncrease=" + hasHorizontalDeltaIncrease +
                " airborne=" + isAirborne +
                " onGround=" + (spider != null && spider.onGround) +
                " reason=\"" + sanitize(reason) + "\""
        );
    }

    File getLogFile() {
        return logFile;
    }

    private void logPacket(String packetType, String message) {
        if (!isEnabled()) {
            return;
        }

        log("packet." + packetType, message);
    }

    private void logScoreboardSnapshot(
        Minecraft minecraft,
        MegaWallsClassResolver classResolver
    ) {
        if (!MinecraftClient.hasLoadedWorld(minecraft)) {
            log("scoreboard", "world=null");
            return;
        }

        Scoreboard scoreboard = minecraft.theWorld.getScoreboard();
        if (scoreboard == null) {
            log("scoreboard", "scoreboard=null");
            return;
        }

        for (int slot = 0; slot <= 2; slot++) {
            ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(slot);
            if (objective == null) {
                log("scoreboard.slot", "slot=" + slot + " objective=null");
                continue;
            }

            log(
                "scoreboard.slot",
                "slot=" + slot +
                    " name=" + sanitize(objective.getName()) +
                    " display=\"" + sanitize(objective.getDisplayName()) + "\""
            );
            Collection<Score> scores = scoreboard.getSortedScores(objective);
            if (scores == null || scores.isEmpty()) {
                continue;
            }

            int logged = 0;
            for (Score score : scores) {
                if (
                    score == null ||
                    score.getPlayerName() == null ||
                    score.getPlayerName().startsWith("#")
                ) {
                    continue;
                }

                ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
                String formattedName = ScorePlayerTeam.formatPlayerName(
                    team,
                    score.getPlayerName()
                );
                log(
                    "scoreboard.score",
                    "slot=" + slot +
                        " player=\"" + sanitize(score.getPlayerName()) + "\"" +
                        " formatted=\"" + sanitize(formattedName) + "\"" +
                        " stripped=\"" + sanitize(classResolver.stripFormatting(formattedName)) + "\"" +
                        " score=" + score.getScorePoints() +
                        " team=" + describeTeam(team)
                );
                logged++;
                if (logged >= 80) {
                    log("scoreboard.score", "slot=" + slot + " truncated=true");
                    break;
                }
            }
        }
    }

    private void logTablistSnapshot(Minecraft minecraft) {
        Collection<NetworkPlayerInfo> players = MinecraftClient.playerInfoMap();
        if (players == null) {
            log("tablist", "netHandler=null");
            return;
        }

        if (players.isEmpty()) {
            log("tablist", "empty=true");
            return;
        }

        int logged = 0;
        for (NetworkPlayerInfo playerInfo : players) {
            if (playerInfo == null || playerInfo.getGameProfile() == null) {
                continue;
            }

            log(
                "tablist.entry",
                "uuid=" + safe(playerInfo.getGameProfile().getId()) +
                    " profile=" + sanitize(playerInfo.getGameProfile().getName()) +
                    " display=\"" + sanitize(
                        playerInfo.getDisplayName() == null
                            ? null
                            : playerInfo.getDisplayName().getFormattedText()
                    ) +
                    "\" responseTime=" + playerInfo.getResponseTime() +
                    " team=" + describeTeam(playerInfo.getPlayerTeam())
            );
            logged++;
            if (logged >= 100) {
                log("tablist.entry", "truncated=true");
                break;
            }
        }
    }

    private void logLobbySkinPreviewSnapshot(
        Minecraft minecraft,
        MegaWallsContextService contextService
    ) {
        if (
            contextService == null ||
                !contextService.isInMegaWallsLobby() ||
                !MinecraftClient.hasLoadedWorld(minecraft)
        ) {
            return;
        }

        List<EntityPlayer> players = minecraft.theWorld.playerEntities;
        if (players == null || players.isEmpty()) {
            return;
        }

        for (EntityPlayer player : players) {
            if (
                player == null ||
                    player.getEntityId() != LOBBY_SKIN_PREVIEW_ENTITY_ID ||
                    player.getGameProfile() == null
            ) {
                continue;
            }

            GameProfile profile = player.getGameProfile();
            TextureDebugInfo textureDebugInfo = extractTextureDebugInfo(profile);
            String previewKey = textureDebugInfo.skinHash + ":" + textureDebugInfo.profileName;
            if (previewKey.equals(lastLobbySkinPreviewKey)) {
                return;
            }

            lastLobbySkinPreviewKey = previewKey;
            log(
                "skin-preview-hash",
                "entityId=" + player.getEntityId() +
                    " hash=" + sanitize(textureDebugInfo.skinHash) +
                    " decodedProfileName=" + sanitize(textureDebugInfo.profileName) +
                    " profile=" + sanitize(profile.getName()) +
                    " pos=" + round(player.posX) + "," + round(player.posY) + "," + round(player.posZ) +
                    " uuid=" + safe(profile.getId())
            );
            logLobbySkinPreviewMap(player, profile, textureDebugInfo);
            return;
        }

        lastLobbySkinPreviewKey = "";
    }

    private void rememberLobbySkinPreviewName(String strippedMessage) {
        if (strippedMessage == null || strippedMessage.isEmpty()) {
            return;
        }

        Matcher matcher = SKIN_PREVIEW_CHAT_PATTERN.matcher(stripFormatting(strippedMessage));
        if (!matcher.find()) {
            return;
        }

        pendingLobbySkinPreviewName = matcher.group(1).trim();
        log(
            "skin-preview-pending",
            "skin=\"" + sanitize(pendingLobbySkinPreviewName) + "\""
        );
    }

    private void logLobbySkinPreviewMap(
        EntityPlayer player,
        GameProfile profile,
        TextureDebugInfo textureDebugInfo
    ) {
        if (
            pendingLobbySkinPreviewName.isEmpty() ||
                textureDebugInfo.skinHash.isEmpty()
        ) {
            return;
        }

        String mapKey = pendingLobbySkinPreviewName + ":" +
            textureDebugInfo.skinHash + ":" +
            textureDebugInfo.profileName;
        if (mapKey.equals(lastLobbySkinPreviewMapKey)) {
            pendingLobbySkinPreviewName = "";
            return;
        }

        lastLobbySkinPreviewMapKey = mapKey;
        log(
            "skin-preview-map",
            "skin=\"" + sanitize(pendingLobbySkinPreviewName) + "\"" +
                " hash=" + sanitize(textureDebugInfo.skinHash) +
                " decodedProfileName=" + sanitize(textureDebugInfo.profileName) +
                " profile=" + sanitize(profile.getName()) +
                " uuid=" + safe(profile.getId()) +
                " entityId=" + player.getEntityId() +
                " pos=" + round(player.posX) + "," + round(player.posY) + "," + round(player.posZ) +
                " csv=\"" + sanitize(pendingLobbySkinPreviewName) + "," +
                sanitize(textureDebugInfo.skinHash) + "," +
                sanitize(textureDebugInfo.profileName) + "\""
        );
        pendingLobbySkinPreviewName = "";
    }

    private TextureDebugInfo extractTextureDebugInfo(GameProfile profile) {
        TextureDebugInfo debugInfo = new TextureDebugInfo();
        if (profile == null || profile.getProperties() == null) {
            return debugInfo;
        }

        Collection<Property> textures = profile.getProperties().get("textures");
        if (textures == null || textures.isEmpty()) {
            return debugInfo;
        }

        Property textureProperty = textures.iterator().next();
        if (textureProperty == null || textureProperty.getValue() == null) {
            return debugInfo;
        }

        String decoded = decodeBase64(textureProperty.getValue());
        debugInfo.profileName = extractProfileName(decoded);
        debugInfo.skinUrl = extractSkinUrl(decoded);
        debugInfo.skinHash = extractTextureHash(debugInfo.skinUrl);
        if (debugInfo.skinHash.isEmpty()) {
            debugInfo.skinHash = extractTextureHash(decoded);
        }
        return debugInfo;
    }

    private String decodeBase64(String value) {
        try {
            return new String(
                Base64.getDecoder().decode(value),
                StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String extractSkinUrl(String decodedTextures) {
        if (decodedTextures == null || decodedTextures.isEmpty()) {
            return "";
        }

        Matcher matcher = SKIN_URL_PATTERN.matcher(decodedTextures);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractProfileName(String decodedTextures) {
        if (decodedTextures == null || decodedTextures.isEmpty()) {
            return "";
        }

        Matcher matcher = PROFILE_NAME_PATTERN.matcher(decodedTextures);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractTextureHash(String skinUrl) {
        if (skinUrl == null || skinUrl.isEmpty()) {
            return "";
        }

        Matcher matcher = TEXTURE_HASH_PATTERN.matcher(skinUrl);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void logPlayerSnapshot(
        Minecraft minecraft,
        MegaWallsClassResolver classResolver
    ) {
        if (!MinecraftClient.hasLoadedWorld(minecraft)) {
            log("players", "world=null");
            return;
        }

        List<EntityPlayer> players = minecraft.theWorld.playerEntities;
        if (players == null || players.isEmpty()) {
            log("players", "empty=true");
            return;
        }

        for (EntityPlayer player : players) {
            if (player == null) {
                continue;
            }

            MegaWallsClass megaWallsClass = classResolver.resolveMegaWallsClass(player);
            log(
                "player",
                "uuid=" + safe(player.getUniqueID()) +
                    " name=" + sanitize(player.getName()) +
                    " class=" + (megaWallsClass == null ? "null" : megaWallsClass.name()) +
                    " health=" + player.getHealth() +
                    " maxHealth=" + player.getMaxHealth() +
                    " dead=" + player.isDead +
                    " riding=" + player.isRiding() +
                    " onGround=" + player.onGround +
                    " pos=" + round(player.posX) + "," + round(player.posY) + "," + round(player.posZ) +
                    " rendered=\"" + sanitize(classResolver.getRenderedName(player)) + "\"" +
                    " team=" + describeTeam(player.getTeam())
            );
        }
    }

    private boolean isEnabled() {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        return config != null && config.developerDebugEnabled;
    }

    private void ensureOpen(Minecraft minecraft) {
        if (writer != null) {
            return;
        }

        File root = minecraft == null || minecraft.mcDataDir == null
            ? new File(".")
            : minecraft.mcDataDir;
        File directory = new File(root, "qol-debug");
        if (!directory.exists() && !directory.mkdirs()) {
            return;
        }

        logFile = new File(
            directory,
            "qol-debug-" + FILE_DATE_FORMAT.format(new Date()) + ".log"
        );
        try {
            writer = new PrintWriter(new FileWriter(logFile, true));
            wasEnabled = true;
            writeLine("debug", "opened file=" + logFile.getAbsolutePath());
        } catch (IOException ignored) {
            writer = null;
        }
    }

    private void closeIfDisabled() {
        if (!wasEnabled || writer == null) {
            wasEnabled = false;
            return;
        }

        log("debug", "closed");
        writer.flush();
        writer.close();
        writer = null;
        wasEnabled = false;
    }

    private synchronized void log(String category, String message) {
        ensureOpen(MinecraftClient.get());
        writeLine(category, message);
    }

    private void writeLine(String category, String message) {
        if (writer == null) {
            return;
        }

        writer.println(
            LINE_DATE_FORMAT.format(new Date()) +
                "\t" +
                category +
                "\t" +
                message
        );
        writer.flush();
    }

    private String describeItem(ItemStack itemStack) {
        if (itemStack == null) {
            return "null";
        }

        return sanitize(itemStack.getDisplayName()) +
            " item=" + sanitize(String.valueOf(itemStack.getItem())) +
            " meta=" + itemStack.getMetadata() +
            " stackSize=" + itemStack.stackSize;
    }

    private String describeTeam(net.minecraft.scoreboard.Team team) {
        if (team == null) {
            return "null";
        }

        if (team instanceof ScorePlayerTeam) {
            ScorePlayerTeam scorePlayerTeam = (ScorePlayerTeam) team;
            return "name=" + sanitize(scorePlayerTeam.getRegisteredName()) +
                " prefix=\"" + sanitize(scorePlayerTeam.getColorPrefix()) + "\"" +
                " suffix=\"" + sanitize(scorePlayerTeam.getColorSuffix()) + "\"";
        }

        return sanitize(team.getRegisteredName());
    }

    private String printableColor(char colorCode) {
        return colorCode == '\0'
            ? "none"
            : EnumChatFormatting.getValueByName(Character.toString(colorCode)) + "(" + colorCode + ")";
    }

    private String safe(Object value) {
        return value == null ? "null" : sanitize(String.valueOf(value));
    }

    private String safeBoolean(boolean value) {
        return value ? "true" : "false";
    }

    private String sanitize(String value) {
        if (value == null) {
            return "null";
        }

        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ');
    }

    private String stripFormatting(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        return value
            .replaceAll("(?i)§.", "")
            .replaceAll("�.", "");
    }

    private String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class TextureDebugInfo {

        private String profileName = "";
        private String skinUrl = "";
        private String skinHash = "";
    }
}
