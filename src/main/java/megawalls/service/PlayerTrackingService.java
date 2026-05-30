package megawalls.service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import megawalls.MegaWallsMod;
import megawalls.api.PlayerStateView;
import megawalls.config.MegaWallsConfig;
import megawalls.domain.DiamondGear;
import megawalls.domain.MegaWallsClass;
import megawalls.util.ChatNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;

final class PlayerTrackingService {

    private static final long STRENGTH_DEBOUNCE_MS = 7000L;
    private static final long DREADLORD_STRENGTH_MS = 5000L;
    private static final int DREADLORD_STRENGTH_SECONDS = 5;
    private static final long HEROBRINE_STRENGTH_MS = 6000L;
    private static final int HEROBRINE_STRENGTH_SECONDS = 6;
    private static final double ZOMBIE_STRENGTH_SOUND_RADIUS_SQ = 9.0D;
    private static final String ZOMBIE_HURT_SOUND = "mob.zombie.hurt";
    private static final String ZOMBIE_REMEDY_SOUND = "mob.zombie.remedy";

    private final TrackedPlayerRegistry trackedPlayerRegistry;
    private final PhoenixResurrectionRegistry phoenixResurrectionRegistry;
    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final DeveloperDebugService debugService;
    private final PotionTrackingService potionTrackingService;
    private final Map<String, MegaWallsClass> killStrengthCandidates =
        new HashMap<String, MegaWallsClass>();

    PlayerTrackingService(
        TrackedPlayerRegistry trackedPlayerRegistry,
        PhoenixResurrectionRegistry phoenixResurrectionRegistry,
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService,
        DeveloperDebugService debugService
    ) {
        this.trackedPlayerRegistry = trackedPlayerRegistry;
        this.phoenixResurrectionRegistry = phoenixResurrectionRegistry;
        this.classResolver = classResolver;
        this.contextService = contextService;
        this.debugService = debugService;
        this.potionTrackingService = new PotionTrackingService(
            contextService,
            debugService
        );
    }

    PlayerStateView queryPlayerState(UUID playerId, String profileName) {
        return queryPlayerState(playerId, profileName, null);
    }

    PlayerStateView queryPlayerState(
        UUID playerId,
        String profileName,
        String renderedName
    ) {
        return queryPlayerState(playerId, profileName, renderedName, false);
    }

    PlayerStateView queryNametagPlayerState(
        UUID playerId,
        String profileName,
        String renderedName
    ) {
        return queryPlayerState(playerId, profileName, renderedName, true);
    }

    private PlayerStateView queryPlayerState(
        UUID playerId,
        String profileName,
        String renderedName,
        boolean nametagView
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        TrackedPlayerState trackedPlayerState =
            trackedPlayerRegistry.resolveTrackedState(
                playerId,
                profileName,
                true
            );
        if (trackedPlayerState == null || config == null) {
            return new PlayerStateView(
                playerId,
                profileName,
                false,
                false,
                Collections.<DiamondGear>emptyList(),
                -1,
                false
            );
        }

        seedTablistState(
            playerId,
            profileName,
            renderedName,
            trackedPlayerState
        );
        refreshPotionLiveState(playerId, profileName, trackedPlayerState);
        refreshPhoenixTablistState(playerId, profileName, trackedPlayerState);
        boolean phoenixTracked = trackedPlayerState.isPhoenixTracked();

        return new PlayerStateView(
            playerId,
            profileName,
            config.phoenixDetectorEnabled &&
                isPhoenixVisible(config, nametagView) &&
            phoenixTracked,
            !phoenixTracked ||
                phoenixResurrectionRegistry.isResurrectionAvailable(
                    trackedPlayerState.profileName
                ),
                config.diamondDetectorEnabled &&
                !nametagView &&
                canUseDiamond(config) &&
                config.isDiamondTablistDisplayEnabled()
                ? buildDiamondGear(trackedPlayerState)
                : Collections.<DiamondGear>emptyList(),
            config.potionDetectorEnabled &&
                canUsePotion(config) &&
                isPotionVisible(config, nametagView)
                ? trackedPlayerState.getDisplayedPotionCount()
                : -1,
            config.strengthDetectorEnabled &&
                canUseStrength(config) &&
            trackedPlayerState.isStrengthActive()
        );
    }

    private boolean isPhoenixVisible(MegaWallsConfig config, boolean nametagView) {
        return nametagView
            ? config.phoenixInNametags
            : config.phoenixInTablist && config.isPhoenixTablistDisplayEnabled();
    }

    private boolean isPotionVisible(MegaWallsConfig config, boolean nametagView) {
        return nametagView
            ? config.potionInNametags
            : config.potionInTablist && config.isPotionTablistDisplayEnabled();
    }

    void onClientTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }

        seedTablistStates(minecraft);

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null) {
            return;
        }

        for (EntityPlayer player : playerEntities) {
            updatePlayerState(player);
        }
    }

    void onChatReceived(ClientChatReceivedEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return;
        }

        String strippedText = classResolver.stripFormatting(
            classResolver.getRawText(event.message)
        );
        observeKillStrengthMessage(strippedText);
    }

    void onPlaySound(PlaySoundEvent event) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (
            config == null ||
            minecraft == null ||
            minecraft.theWorld == null ||
            !config.strengthDetectorEnabled ||
            !canUseStrength(config) ||
            !config.zombieStrength ||
            !contextService.isInMegaWalls()
        ) {
            return;
        }

        if (
            event == null ||
            event.name == null ||
            !isZombieStrengthSound(event.name) ||
            event.sound == null
        ) {
            return;
        }

        double soundX = event.sound.getXPosF();
        double soundY = event.sound.getYPosF();
        double soundZ = event.sound.getZPosF();

        EntityPlayer nearestZombie = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (EntityPlayer player : minecraft.theWorld.playerEntities) {
            if (player == null || player == minecraft.thePlayer) {
                continue;
            }

            MegaWallsClass megaWallsClass = classResolver.resolveMegaWallsClass(
                player
            );
            if (
                megaWallsClass != MegaWallsClass.ZOMBIE &&
                !classResolver.looksLikeZombieTag(player)
            ) {
                continue;
            }

            double distanceSq = player.getDistanceSq(soundX, soundY, soundZ);
            if (
                distanceSq > ZOMBIE_STRENGTH_SOUND_RADIUS_SQ ||
                distanceSq >= nearestDistanceSq
            ) {
                continue;
            }

            nearestZombie = player;
            nearestDistanceSq = distanceSq;
        }

        if (nearestZombie == null) {
            return;
        }

        TrackedPlayerState trackedPlayerState =
            trackedPlayerRegistry.resolveTrackedState(
                nearestZombie.getUniqueID(),
                nearestZombie.getName(),
                true
            );
        if (trackedPlayerState == null) {
            return;
        }

        markZombieStrength(
            trackedPlayerState,
            nearestZombie,
            config
        );
    }

    private boolean isZombieStrengthSound(String soundName) {
        return ZOMBIE_HURT_SOUND.equalsIgnoreCase(soundName) ||
            ZOMBIE_REMEDY_SOUND.equalsIgnoreCase(soundName);
    }

    private void markZombieStrength(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer zombie,
        MegaWallsConfig config
    ) {
        if (trackedPlayerState == null || zombie == null || config == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (
            trackedPlayerState.lastStrengthTriggerAt + STRENGTH_DEBOUNCE_MS >
            now
        ) {
            return;
        }

        trackedPlayerState.lastStrengthTriggerAt = now;
        trackedPlayerState.strengthExpiresAt = now + STRENGTH_DEBOUNCE_MS;

        if (config.autoTellStrength) {
            printStrengthWarning(
                config,
                aquaName(zombie.getName(), EnumChatFormatting.RED) +
                    " has Zombie strength!"
            );
        }
    }

    void observeTabProfile(
        UUID playerId,
        String profileName,
        String renderedName
    ) {
        TrackedPlayerState trackedPlayerState =
            trackedPlayerRegistry.resolveTrackedState(
                playerId,
                profileName,
                true
            );
        if (trackedPlayerState != null) {
            seedTablistState(
                playerId,
                profileName,
                renderedName,
                trackedPlayerState
            );
        }
    }

    void observeEntityMetadata(EntityPlayer player, float health) {
        TrackedPlayerState trackedPlayerState = resolveTrackedState(
            player,
            true
        );
        if (trackedPlayerState == null) {
            return;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(player)
        );
        observePhoenixRidingState(trackedPlayerState, player);
        potionTrackingService.updateLiveState(trackedPlayerState, player, health);
    }

    void observeEquipmentPacket(
        EntityPlayer player,
        int equipmentSlot,
        ItemStack itemStack
    ) {
        TrackedPlayerState trackedPlayerState = resolveTrackedState(
            player,
            true
        );
        if (trackedPlayerState == null) {
            return;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(player)
        );

        MegaWallsConfig config = MegaWallsMod.getConfig();

        if (
            config == null ||
            !config.diamondDetectorEnabled ||
            !canUseDiamond(config) ||
            itemStack == null
        ) {
            return;
        }

        if (equipmentSlot == 4) {
            observeDiamond(
                trackedPlayerState,
                player,
                ObservedDiamond.HELMET,
                itemStack,
                Items.diamond_helmet,
                config.autoTellDiamondArmor
            );
        } else if (equipmentSlot == 3) {
            observeDiamond(
                trackedPlayerState,
                player,
                ObservedDiamond.CHESTPLATE,
                itemStack,
                Items.diamond_chestplate,
                config.autoTellDiamondArmor
            );
        } else if (equipmentSlot == 2) {
            observeDiamond(
                trackedPlayerState,
                player,
                ObservedDiamond.LEGGINGS,
                itemStack,
                Items.diamond_leggings,
                config.autoTellDiamondArmor
            );
        } else if (equipmentSlot == 1) {
            observeDiamond(
                trackedPlayerState,
                player,
                ObservedDiamond.BOOTS,
                itemStack,
                Items.diamond_boots,
                config.autoTellDiamondArmor
            );
        } else if (equipmentSlot == 0) {
            observeDiamond(
                trackedPlayerState,
                player,
                ObservedDiamond.SWORD,
                itemStack,
                Items.diamond_sword,
                config.autoTellDiamondSword
            );
        }
    }

    void observeEntityEffect(
        EntityPlayer player,
        int effectId,
        int durationTicks
    ) {
        return;
    }

    void observeEntityEffectRemoved(EntityPlayer player, int effectId) {
        if (effectId != Potion.damageBoost.id) {
            return;
        }

        TrackedPlayerState trackedPlayerState = resolveTrackedState(
            player,
            false
        );
        if (trackedPlayerState != null) {
            trackedPlayerState.strengthExpiresAt = 0L;
        }
    }

    private void seedTablistStates(Minecraft minecraft) {
        if (minecraft.getNetHandler() == null) {
            return;
        }

        Scoreboard scoreboard =
            minecraft.theWorld == null
                ? null
                : minecraft.theWorld.getScoreboard();
        Collection<NetworkPlayerInfo> playerInfoMap = minecraft
            .getNetHandler()
            .getPlayerInfoMap();
        if (playerInfoMap == null || playerInfoMap.isEmpty()) {
            return;
        }

        for (NetworkPlayerInfo playerInfo : playerInfoMap) {
            if (playerInfo == null || playerInfo.getGameProfile() == null) {
                continue;
            }

            UUID playerId = playerInfo.getGameProfile().getId();
            String profileName = playerInfo.getGameProfile().getName();
            TrackedPlayerState trackedPlayerState =
                trackedPlayerRegistry.resolveTrackedState(
                    playerId,
                    profileName,
                    true
                );
            if (trackedPlayerState == null) {
                continue;
            }

            String renderedName =
                playerInfo.getDisplayName() == null
                    ? profileName
                    : playerInfo.getDisplayName().getFormattedText();
            EntityPlayer playerEntity = resolvePlayerEntity(playerId, profileName);
            Integer tablistHealth = potionTrackingService.lookupHealth(
                scoreboard,
                profileName,
                playerEntity
            );
            seedTablistState(
                playerId,
                profileName,
                renderedName,
                trackedPlayerState
            );
            potionTrackingService.observeScoreboardHealth(
                trackedPlayerState,
                tablistHealth,
                playerEntity != null
            );
            observePhoenixRidingState(trackedPlayerState, playerEntity);
        }
    }

    private void updatePlayerState(EntityPlayer player) {
        if (
            player == null ||
            player.getName() == null ||
            player.getName().isEmpty()
        ) {
            return;
        }

        TrackedPlayerState trackedPlayerState = resolveTrackedState(
            player,
            true
        );
        if (trackedPlayerState == null) {
            return;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(player)
        );
        observePhoenixRidingState(trackedPlayerState, player);
        recordObservedDiamonds(trackedPlayerState, player);
        potionTrackingService.updateLiveState(trackedPlayerState, player);
        updateStrengthState(trackedPlayerState, player);
    }

    void clear() {
        trackedPlayerRegistry.clear();
        phoenixResurrectionRegistry.clear();
        killStrengthCandidates.clear();
    }

    void resetSnapshots() {
        trackedPlayerRegistry.resetSnapshots();
        phoenixResurrectionRegistry.reset();
        killStrengthCandidates.clear();
    }

    private void seedTablistState(
        UUID playerId,
        String profileName,
        String renderedName,
        TrackedPlayerState trackedPlayerState
    ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || trackedPlayerState == null) {
            return;
        }

        Scoreboard scoreboard =
            minecraft.theWorld == null
                ? null
                : minecraft.theWorld.getScoreboard();
        String effectiveRenderedName = renderedName;
        if (
            (effectiveRenderedName == null ||
                effectiveRenderedName.isEmpty()) &&
            minecraft.getNetHandler() != null &&
            playerId != null
        ) {
            NetworkPlayerInfo playerInfo = minecraft
                .getNetHandler()
                .getPlayerInfo(playerId);
            if (playerInfo != null && playerInfo.getDisplayName() != null) {
                effectiveRenderedName = playerInfo
                    .getDisplayName()
                    .getFormattedText();
            }
        }

        if (
            (effectiveRenderedName == null ||
                effectiveRenderedName.isEmpty()) &&
            trackedPlayerState.lastRenderedName != null
        ) {
            effectiveRenderedName = trackedPlayerState.lastRenderedName;
        }

        trackedPlayerState.bindRenderedName(effectiveRenderedName);
        if (classResolver.hasPhoenixIndicator(effectiveRenderedName)) {
            trackedPlayerState.phoenixIndicatorSeen = true;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(
                effectiveRenderedName,
                profileName,
                scoreboard
            )
        );
    }

    private void applyResolvedClass(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass
    ) {
        if (trackedPlayerState == null || megaWallsClass == null) {
            return;
        }

        trackedPlayerState.megaWallsClass = megaWallsClass;
        updateKillStrengthCandidate(trackedPlayerState, megaWallsClass);
        trackedPlayerState.phoenixIndicatorSeen =
            megaWallsClass == MegaWallsClass.PHOENIX;
        if (trackedPlayerState.potionClass != megaWallsClass) {
            trackedPlayerState.resetPotions();
        }
    }

    private void updateKillStrengthCandidate(
        TrackedPlayerState trackedPlayerState,
        MegaWallsClass megaWallsClass
    ) {
        if (
            trackedPlayerState == null ||
            trackedPlayerState.normalizedProfileName == null
        ) {
            return;
        }

        if (isKillStrengthClass(megaWallsClass)) {
            killStrengthCandidates.put(
                trackedPlayerState.normalizedProfileName,
                megaWallsClass
            );
            return;
        }

        killStrengthCandidates.remove(trackedPlayerState.normalizedProfileName);
    }

    private void recordObservedDiamonds(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            config == null ||
            !config.diamondDetectorEnabled ||
            !canUseDiamond(config)
        ) {
            return;
        }

        observeDiamond(
            trackedPlayerState,
            player,
            ObservedDiamond.HELMET,
            player.getCurrentArmor(3),
            Items.diamond_helmet,
            config.autoTellDiamondArmor
        );
        observeDiamond(
            trackedPlayerState,
            player,
            ObservedDiamond.CHESTPLATE,
            player.getCurrentArmor(2),
            Items.diamond_chestplate,
            config.autoTellDiamondArmor
        );
        observeDiamond(
            trackedPlayerState,
            player,
            ObservedDiamond.LEGGINGS,
            player.getCurrentArmor(1),
            Items.diamond_leggings,
            config.autoTellDiamondArmor
        );
        observeDiamond(
            trackedPlayerState,
            player,
            ObservedDiamond.BOOTS,
            player.getCurrentArmor(0),
            Items.diamond_boots,
            config.autoTellDiamondArmor
        );
        observeDiamond(
            trackedPlayerState,
            player,
            ObservedDiamond.SWORD,
            player.getHeldItem(),
            Items.diamond_sword,
            config.autoTellDiamondSword
        );
    }

    private void observeDiamond(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player,
        ObservedDiamond observedDiamond,
        ItemStack itemStack,
        net.minecraft.item.Item expectedItem,
        boolean autoTell
    ) {
        if (trackedPlayerState == null || player == null) {
            return;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(player)
        );

        if (
            itemStack == null ||
            itemStack.getItem() != expectedItem ||
            trackedPlayerState.observedDiamonds.contains(observedDiamond)
        ) {
            return;
        }

        if (isKitDiamondGear(trackedPlayerState, observedDiamond)) {
            trackedPlayerState.observedDiamonds.add(observedDiamond);
            return;
        }

        trackedPlayerState.observedDiamonds.add(observedDiamond);
        if (autoTell) {
            ChatNotifier.info(
                aquaName(player.getName(), EnumChatFormatting.WHITE) +
                    " was seen with diamond " +
                    observedDiamond.name().toLowerCase(Locale.ROOT) +
                    '.'
            );
        }
    }

    private void updateStrengthState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player
    ) {
        long now = System.currentTimeMillis();
        if (
            trackedPlayerState.strengthExpiresAt > 0L &&
            trackedPlayerState.strengthExpiresAt <= now
        ) {
            trackedPlayerState.strengthExpiresAt = 0L;
        }

        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            config == null ||
            !config.strengthDetectorEnabled ||
            !canUseStrength(config) ||
            !config.zombieStrength
        ) {
            return;
        }

        if (
            trackedPlayerState.megaWallsClass == MegaWallsClass.ZOMBIE &&
            player.isPotionActive(Potion.damageBoost)
        ) {
            trackedPlayerState.strengthExpiresAt = Math.max(
                trackedPlayerState.strengthExpiresAt,
                now + STRENGTH_DEBOUNCE_MS
            );
        }
    }

    private void observeKillStrengthMessage(String strippedText) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            config == null ||
            !config.strengthDetectorEnabled ||
            !canUseStrength(config) ||
            strippedText == null ||
            strippedText.isEmpty() ||
            isLikelyPlayerChatMessage(strippedText)
        ) {
            return;
        }

        TrackedPlayerState strengthState = findKillStrengthKiller(strippedText);
        if (strengthState == null || strengthState.profileName == null) {
            return;
        }

        int durationSeconds = getKillStrengthSeconds(strengthState.megaWallsClass);
        long now = System.currentTimeMillis();
        strengthState.lastStrengthTriggerAt = now;
        strengthState.strengthExpiresAt =
            now + getKillStrengthDuration(strengthState.megaWallsClass);

        printStrengthWarning(
            config,
            aquaName(strengthState.profileName, EnumChatFormatting.RED) +
                " has strength for " +
                durationSeconds +
                " seconds."
        );
    }

    private void printStrengthWarning(MegaWallsConfig config, String message) {
        int printCount = config != null && config.strengthPrintOnce ? 1 : 3;
        for (int index = 0; index < printCount; index++) {
            ChatNotifier.warn(message);
        }
    }

    private TrackedPlayerState findKillStrengthKiller(String strippedText) {
        String killerName = extractSecondTrackedPlayerName(strippedText);
        if (killerName != null) {
            TrackedPlayerState trackedPlayerState =
                resolveKillStrengthCandidateByName(killerName);
            if (trackedPlayerState != null) {
                return trackedPlayerState;
            }
        }

        return null;
    }

    private TrackedPlayerState resolveKillStrengthCandidateByName(
        String playerName
    ) {
        MegaWallsClass candidateClass = getKillStrengthCandidateClass(playerName);
        if (candidateClass == null) {
            return resolveKillStrengthStateByName(playerName);
        }

        EntityPlayer player = resolvePlayerEntity(null, playerName);
        TrackedPlayerState trackedPlayerState = player == null
            ? trackedPlayerRegistry.resolveTrackedState(null, playerName, true)
            : resolveTrackedState(player, true);
        if (trackedPlayerState == null) {
            return null;
        }

        trackedPlayerState.megaWallsClass = candidateClass;
        return trackedPlayerState;
    }

    private MegaWallsClass getKillStrengthCandidateClass(String playerName) {
        String normalizedName = TrackedPlayerState.normalizeKey(playerName);
        return normalizedName == null
            ? null
            : killStrengthCandidates.get(normalizedName);
    }

    private TrackedPlayerState resolveKillStrengthStateByName(String playerName) {
        EntityPlayer player = resolvePlayerEntity(null, playerName);
        TrackedPlayerState trackedPlayerState = player == null
            ? trackedPlayerRegistry.resolveTrackedState(null, playerName, false)
            : resolveTrackedState(player, true);
        if (trackedPlayerState == null) {
            return null;
        }

        if (player != null) {
            applyResolvedClass(
                trackedPlayerState,
                classResolver.resolveMegaWallsClass(player)
            );
        }

        return isKillStrengthClass(trackedPlayerState.megaWallsClass)
            ? trackedPlayerState
            : null;
    }

    private boolean isKillStrengthClass(MegaWallsClass megaWallsClass) {
        return megaWallsClass == MegaWallsClass.DREADLORD ||
            megaWallsClass == MegaWallsClass.HEROBRINE;
    }

    private long getKillStrengthDuration(MegaWallsClass megaWallsClass) {
        return megaWallsClass == MegaWallsClass.HEROBRINE
            ? HEROBRINE_STRENGTH_MS
            : DREADLORD_STRENGTH_MS;
    }

    private int getKillStrengthSeconds(MegaWallsClass megaWallsClass) {
        return megaWallsClass == MegaWallsClass.HEROBRINE
            ? HEROBRINE_STRENGTH_SECONDS
            : DREADLORD_STRENGTH_SECONDS;
    }

    private String extractSecondTrackedPlayerName(String strippedText) {
        if (strippedText == null || strippedText.isEmpty()) {
            return null;
        }

        String firstPlayerName = null;
        int tokenStart = -1;
        for (int index = 0; index <= strippedText.length(); index++) {
            boolean atEnd = index == strippedText.length();
            char currentChar = atEnd ? '\0' : strippedText.charAt(index);
            if (!atEnd && isNameCharacter(currentChar)) {
                if (tokenStart < 0) {
                    tokenStart = index;
                }
                continue;
            }

            if (tokenStart >= 0) {
                String playerName = resolveTrackedPlayerNameToken(
                    strippedText.substring(tokenStart, index)
                );
                if (playerName != null) {
                    if (firstPlayerName == null) {
                        firstPlayerName = playerName;
                    } else if (!firstPlayerName.equalsIgnoreCase(playerName)) {
                        return playerName;
                    }
                }
                tokenStart = -1;
            }
        }

        return null;
    }

    private boolean isLikelyPlayerChatMessage(String strippedText) {
        int separatorIndex = strippedText.indexOf(": ");
        if (separatorIndex <= 0) {
            return false;
        }

        String senderText = strippedText.substring(0, separatorIndex);
        int tokenStart = -1;
        for (int index = 0; index <= senderText.length(); index++) {
            boolean atEnd = index == senderText.length();
            char currentChar = atEnd ? '\0' : senderText.charAt(index);
            if (!atEnd && isNameCharacter(currentChar)) {
                if (tokenStart < 0) {
                    tokenStart = index;
                }
                continue;
            }

            if (tokenStart >= 0) {
                String playerName = resolveTrackedPlayerNameToken(
                    senderText.substring(tokenStart, index)
                );
                if (playerName != null) {
                    return true;
                }
                tokenStart = -1;
            }
        }

        return false;
    }

    private String resolveTrackedPlayerNameToken(String token) {
        if (token == null || token.length() < 3 || token.length() > 16) {
            return null;
        }

        String normalizedToken = TrackedPlayerState.normalizeKey(token);
        for (TrackedPlayerState trackedPlayerState : trackedPlayerRegistry.snapshotTrackedStates()) {
            if (
                trackedPlayerState == null ||
                trackedPlayerState.normalizedProfileName == null ||
                trackedPlayerState.profileName == null
            ) {
                continue;
            }

            if (trackedPlayerState.normalizedProfileName.equals(normalizedToken)) {
                return trackedPlayerState.profileName;
            }
        }

        return null;
    }

    private boolean isNameCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private List<DiamondGear> buildDiamondGear(TrackedPlayerState trackedPlayerState) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (config == null) {
            return Collections.emptyList();
        }

        List<DiamondGear> diamondGear = new java.util.ArrayList<DiamondGear>();
        if (config.diamondArmorInTablist) {
            appendDiamond(diamondGear, trackedPlayerState, ObservedDiamond.HELMET);
            appendDiamond(
                diamondGear,
                trackedPlayerState,
                ObservedDiamond.CHESTPLATE
            );
            appendDiamond(diamondGear, trackedPlayerState, ObservedDiamond.LEGGINGS);
            appendDiamond(diamondGear, trackedPlayerState, ObservedDiamond.BOOTS);
        }
        if (config.diamondSwordInTablist) {
            appendDiamond(diamondGear, trackedPlayerState, ObservedDiamond.SWORD);
        }
        return diamondGear;
    }

    private void appendDiamond(
        List<DiamondGear> diamondGear,
        TrackedPlayerState trackedPlayerState,
        ObservedDiamond observedDiamond
    ) {
        if (trackedPlayerState.observedDiamonds.contains(observedDiamond)) {
            if (isKitDiamondGear(trackedPlayerState, observedDiamond)) {
                return;
            }
            diamondGear.add(observedDiamond.gear);
        }
    }

    private boolean isKitDiamondGear(
        TrackedPlayerState trackedPlayerState,
        ObservedDiamond observedDiamond
    ) {
        return (
            trackedPlayerState != null &&
            trackedPlayerState.megaWallsClass != null &&
            observedDiamond != null &&
            trackedPlayerState.megaWallsClass.hasKitDiamondGear(
                observedDiamond.gear
            )
        );
    }

    private boolean canUseDiamond(MegaWallsConfig config) {
        return (
            config != null &&
            config.canUseDiamond(contextService.isDeathmatchActive())
        );
    }

    private boolean canUsePotion(MegaWallsConfig config) {
        return (
            config != null &&
            config.canUsePotion(contextService.isDeathmatchActive())
        );
    }

    private boolean canUseStrength(MegaWallsConfig config) {
        return (
            config != null &&
            config.canUseStrength(contextService.isDeathmatchActive())
        );
    }

    private void refreshPotionLiveState(
        UUID playerId,
        String profileName,
        TrackedPlayerState trackedPlayerState
    ) {
        if (trackedPlayerState == null) {
            return;
        }

        EntityPlayer player = resolvePlayerEntity(playerId, profileName);
        if (player == null) {
            return;
        }

        applyResolvedClass(
            trackedPlayerState,
            classResolver.resolveMegaWallsClass(player)
        );
        potionTrackingService.updateLiveState(trackedPlayerState, player);
    }

    private void refreshPhoenixTablistState(
        UUID playerId,
        String profileName,
        TrackedPlayerState trackedPlayerState
    ) {
        if (trackedPlayerState == null) {
            return;
        }

        observePhoenixRidingState(
            trackedPlayerState,
            resolvePlayerEntity(playerId, profileName)
        );
    }

    private void observePhoenixRidingState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            config == null ||
            !config.phoenixDetectorEnabled ||
            trackedPlayerState == null ||
            !trackedPlayerState.isPhoenixTracked() ||
            player == null ||
            !player.isRiding()
        ) {
            return;
        }

        if (!phoenixResurrectionRegistry.markResurrectionUnavailable(
            trackedPlayerState.profileName
        )) {
            return;
        }

        if (config.phoenixAutoTalk) {
            String playerName = player.getName();
            if ((playerName == null || playerName.isEmpty()) &&
                trackedPlayerState.profileName != null) {
                playerName = trackedPlayerState.profileName;
            }

            if (playerName != null && !playerName.isEmpty()) {
                ChatNotifier.info(
                    aquaName(playerName, EnumChatFormatting.YELLOW) +
                        " lost Resurrection."
                );
            }
        }
    }

    private String aquaName(String playerName, EnumChatFormatting trailingColor) {
        return (
            EnumChatFormatting.AQUA.toString() +
            playerName +
            trailingColor.toString()
        );
    }

    private String redValue(int value, EnumChatFormatting trailingColor) {
        return (
            EnumChatFormatting.RED.toString() +
            value +
            trailingColor.toString()
        );
    }

    private TrackedPlayerState resolveTrackedState(
        EntityPlayer player,
        boolean create
    ) {
        if (player == null) {
            return null;
        }

        return trackedPlayerRegistry.resolveTrackedState(
            player.getUniqueID(),
            player.getName(),
            create
        );
    }

    private EntityPlayer resolvePlayerEntity(UUID playerId, String profileName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return null;
        }

        if (playerId != null) {
            EntityPlayer player = minecraft.theWorld.getPlayerEntityByUUID(playerId);
            if (player != null) {
                return player;
            }
        }

        if (profileName == null || profileName.isEmpty()) {
            return null;
        }

        List<EntityPlayer> playerEntities = minecraft.theWorld.playerEntities;
        if (playerEntities == null) {
            return null;
        }

        for (EntityPlayer player : playerEntities) {
            if (player != null && profileName.equalsIgnoreCase(player.getName())) {
                return player;
            }
        }

        return null;
    }
}
