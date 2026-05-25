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
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
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
    private static final long POTION_CONFIRM_WINDOW_MS = 1500L;
    private static final long POTION_MIN_DRINK_MS = 1600L;
    private static final long POTION_MAX_DRINK_MS = 6000L;
    private static final long POTION_SWITCH_GRACE_MS = 500L;
    private static final long POTION_USE_DEBOUNCE_MS = 750L;
    private static final double POTION_HEALTH_THRESHOLD = 1.5D;
    private static final double ZOMBIE_STRENGTH_SOUND_RADIUS_SQ = 9.0D;
    private static final String ZOMBIE_HURT_SOUND = "mob.zombie.hurt";
    private static final String ZOMBIE_REMEDY_SOUND = "mob.zombie.remedy";

    private final TrackedPlayerRegistry trackedPlayerRegistry;
    private final PhoenixResurrectionRegistry phoenixResurrectionRegistry;
    private final MegaWallsClassResolver classResolver;
    private final MegaWallsContextService contextService;
    private final Map<String, MegaWallsClass> killStrengthCandidates =
        new HashMap<String, MegaWallsClass>();

    PlayerTrackingService(
        TrackedPlayerRegistry trackedPlayerRegistry,
        PhoenixResurrectionRegistry phoenixResurrectionRegistry,
        MegaWallsClassResolver classResolver,
        MegaWallsContextService contextService
    ) {
        this.trackedPlayerRegistry = trackedPlayerRegistry;
        this.phoenixResurrectionRegistry = phoenixResurrectionRegistry;
        this.classResolver = classResolver;
        this.contextService = contextService;
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
        PhoenixResurrectionState phoenixState =
            resolvePhoenixResurrectionState(trackedPlayerState, false);
        boolean phoenixTracked = trackedPlayerState.isPhoenixTracked();
        if (!phoenixTracked) {
            phoenixState = null;
        }

        return new PlayerStateView(
            playerId,
            profileName,
            config.phoenixDetectorEnabled &&
                isPhoenixVisible(config, nametagView) &&
                phoenixTracked,
            phoenixState == null || phoenixState.resurrectionAvailable,
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
        updatePotionState(trackedPlayerState, player, health);
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
        if (equipmentSlot == 0) {
            observeHeldPotionState(trackedPlayerState, itemStack, config);
        }

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
        Map<String, Integer> tablistHealthScores = getTablistHealthScores(
            scoreboard
        );
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
            Integer tablistHealth = lookupTablistHealth(
                tablistHealthScores,
                profileName
            );
            seedTablistState(
                playerId,
                profileName,
                renderedName,
                trackedPlayerState
            );
            observePotionTablistState(trackedPlayerState, tablistHealth);
            observePhoenixRidingState(
                trackedPlayerState,
                resolvePlayerEntity(playerId, profileName)
            );
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
        updatePotionState(trackedPlayerState, player);
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

    private void updatePotionState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player
    ) {
        updatePotionState(trackedPlayerState, player, player.getHealth());
    }

    private void updatePotionState(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player,
        float currentHealth
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        MegaWallsClass megaWallsClass = trackedPlayerState.megaWallsClass;
        if (
            config == null ||
            !config.potionDetectorEnabled ||
            !canUsePotion(config) ||
            megaWallsClass == null ||
            megaWallsClass.getPotionCount() < 0
        ) {
            trackedPlayerState.resetPotions();
            return;
        }

        boolean alive = !player.isDead && currentHealth > 0.0F;

        if (trackedPlayerState.potionClass != megaWallsClass) {
            trackedPlayerState.potionClass = megaWallsClass;
            trackedPlayerState.remainingPotions =
                megaWallsClass.getPotionCount();
            trackedPlayerState.hasPotionSample = true;
            trackedPlayerState.lastHealth = currentHealth;
            trackedPlayerState.wasAlive = alive;
            return;
        }

        if (!trackedPlayerState.hasPotionSample) {
            trackedPlayerState.lastHealth = currentHealth;
            trackedPlayerState.hasPotionSample = true;
            trackedPlayerState.wasAlive = alive;
            return;
        }

        if (!trackedPlayerState.wasAlive && alive) {
            trackedPlayerState.remainingPotions =
                megaWallsClass.getPotionCount();
            trackedPlayerState.lastHealth = currentHealth;
            trackedPlayerState.wasAlive = true;
            return;
        }

        if (
            alive &&
            trackedPlayerState.wasAlive &&
            trackedPlayerState.remainingPotions > 0
        ) {
            observePotionHealthIncrease(
                trackedPlayerState,
                player,
                megaWallsClass,
                currentHealth,
                config
            );
        }

        trackedPlayerState.lastHealth = currentHealth;
        trackedPlayerState.wasAlive = alive;
    }

    private void observePotionHealthIncrease(
        TrackedPlayerState trackedPlayerState,
        EntityPlayer player,
        MegaWallsClass megaWallsClass,
        float currentHealth,
        MegaWallsConfig config
    ) {
        double previousHealth = trackedPlayerState.lastHealth;
        double deltaHealth = (double) currentHealth - previousHealth;
        if (deltaHealth <= 0.0D) {
            return;
        }

        long now = System.currentTimeMillis();
        if (
            now - trackedPlayerState.lastPotionUseAt < POTION_USE_DEBOUNCE_MS ||
            !isHealingPotionHeldRecently(trackedPlayerState, now)
        ) {
            return;
        }

        double expectedHealing = getExpectedCappedPotionHealing(
            player,
            megaWallsClass,
            previousHealth
        );
        if (expectedHealing <= 0.0D) {
            return;
        }

        if (Math.abs(deltaHealth - expectedHealing) > POTION_HEALTH_THRESHOLD) {
            return;
        }

        trackedPlayerState.remainingPotions--;
        trackedPlayerState.lastPotionUseAt = now;
        trackedPlayerState.healingPotionHoldStartedAt = now;
        trackedPlayerState.healingPotionHoldGraceUntil = 0L;
        if (config.potionDebug) {
            ChatNotifier.info(
                aquaName(player.getName(), EnumChatFormatting.WHITE) +
                    " used a healing potion. Remaining: " +
                    redValue(
                        trackedPlayerState.remainingPotions,
                        EnumChatFormatting.WHITE
                    )
            );
        }
    }

    private double getExpectedCappedPotionHealing(
        EntityPlayer player,
        MegaWallsClass megaWallsClass,
        double previousHealth
    ) {
        double maxHealth = player == null ? 20.0D : player.getMaxHealth();
        double missingHealth = Math.max(0.0D, maxHealth - previousHealth);
        return Math.min(megaWallsClass.getPotionHealth(), missingHealth);
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
            strippedText.isEmpty()
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

    private Map<String, Integer> getTablistHealthScores(Scoreboard scoreboard) {
        Map<String, Integer> scoresByPlayer = new HashMap<String, Integer>();
        if (scoreboard == null) {
            return scoresByPlayer;
        }

        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
        if (objective == null || objective.getCriteria() == null) {
            return scoresByPlayer;
        }

        if (
            objective.getCriteria().getRenderType() !=
            IScoreObjectiveCriteria.EnumRenderType.HEARTS
        ) {
            return scoresByPlayer;
        }

        Collection<Score> sortedScores = scoreboard.getSortedScores(objective);
        if (sortedScores == null || sortedScores.isEmpty()) {
            return scoresByPlayer;
        }

        for (Score score : sortedScores) {
            if (
                score == null ||
                score.getPlayerName() == null ||
                score.getPlayerName().startsWith("#")
            ) {
                continue;
            }

            String normalizedName =
                TrackedPlayerState.normalizeKey(score.getPlayerName());
            if (normalizedName != null) {
                scoresByPlayer.put(
                    normalizedName,
                    Integer.valueOf(score.getScorePoints())
                );
            }
        }

        return scoresByPlayer;
    }

    private Integer lookupTablistHealth(
        Map<String, Integer> tablistHealthScores,
        String profileName
    ) {
        if (
            tablistHealthScores == null ||
            tablistHealthScores.isEmpty() ||
            profileName == null ||
            profileName.isEmpty()
        ) {
            return null;
        }

        return tablistHealthScores.get(
            TrackedPlayerState.normalizeKey(profileName)
        );
    }

    private void observePotionTablistState(
        TrackedPlayerState trackedPlayerState,
        Integer tablistHealth
    ) {
        MegaWallsConfig config = MegaWallsMod.getConfig();
        if (
            trackedPlayerState == null ||
            tablistHealth == null ||
            config == null ||
            !canUsePotion(config)
        ) {
            return;
        }

        int currentTablistHealth = tablistHealth.intValue();
        if (
            trackedPlayerState.lastPotionTablistHealth != Integer.MIN_VALUE &&
            currentTablistHealth > trackedPlayerState.lastPotionTablistHealth
        ) {
            trackedPlayerState.recentPotionTablistIncreaseUntil =
                System.currentTimeMillis() + POTION_CONFIRM_WINDOW_MS;
        }

        trackedPlayerState.lastPotionTablistHealth = currentTablistHealth;
    }

    private boolean isHealingPotionHeldRecently(
        TrackedPlayerState trackedPlayerState,
        long now
    ) {
        if (trackedPlayerState == null) {
            return false;
        }

        long holdStartedAt = trackedPlayerState.healingPotionHoldStartedAt;
        if (holdStartedAt <= 0L) {
            return false;
        }

        long heldFor = now - holdStartedAt;
        return heldFor >= POTION_MIN_DRINK_MS &&
            heldFor <= POTION_MAX_DRINK_MS &&
            (
                trackedPlayerState.healingPotionHoldGraceUntil == 0L ||
                trackedPlayerState.healingPotionHoldGraceUntil >= now
            );
    }

    private void observeHeldPotionState(
        TrackedPlayerState trackedPlayerState,
        ItemStack itemStack,
        MegaWallsConfig config
    ) {
        if (trackedPlayerState == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (
            config != null &&
            canUsePotion(config) &&
            itemStack != null &&
            isInstantHealingPotion(itemStack)
        ) {
            if (
                trackedPlayerState.healingPotionHoldStartedAt <= 0L ||
                trackedPlayerState.healingPotionHoldGraceUntil > 0L ||
                now - trackedPlayerState.healingPotionHoldStartedAt >
                    POTION_MAX_DRINK_MS
            ) {
                trackedPlayerState.healingPotionHoldStartedAt = now;
            }
            trackedPlayerState.healingPotionHoldGraceUntil = 0L;
            return;
        }

        if (trackedPlayerState.healingPotionHoldStartedAt <= 0L) {
            trackedPlayerState.healingPotionHoldGraceUntil = 0L;
            return;
        }

        long heldFor = now - trackedPlayerState.healingPotionHoldStartedAt;
        if (heldFor >= POTION_MIN_DRINK_MS) {
            trackedPlayerState.healingPotionHoldGraceUntil =
                now + POTION_SWITCH_GRACE_MS;
            return;
        }

        trackedPlayerState.healingPotionHoldStartedAt = 0L;
        trackedPlayerState.healingPotionHoldGraceUntil = 0L;
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

    private boolean isInstantHealingPotion(ItemStack itemStack) {
        if (itemStack == null || !(itemStack.getItem() instanceof ItemPotion)) {
            return false;
        }

        List<PotionEffect> potionEffects =
            ((ItemPotion) itemStack.getItem()).getEffects(itemStack);
        if (potionEffects == null || potionEffects.isEmpty()) {
            return false;
        }

        for (PotionEffect potionEffect : potionEffects) {
            if (
                potionEffect != null &&
                potionEffect.getPotionID() == Potion.heal.id
            ) {
                return true;
            }
        }
        return false;
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
        updatePotionState(trackedPlayerState, player);
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

        PhoenixResurrectionState phoenixState =
            resolvePhoenixResurrectionState(trackedPlayerState, true);
        if (
            phoenixState == null ||
            !phoenixState.resurrectionAvailable
        ) {
            return;
        }

        phoenixState.resurrectionAvailable = false;

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

    private PhoenixResurrectionState resolvePhoenixResurrectionState(
        TrackedPlayerState trackedPlayerState,
        boolean create
    ) {
        if (trackedPlayerState == null) {
            return null;
        }

        PhoenixResurrectionState phoenixState =
            phoenixResurrectionRegistry.resolveState(
                trackedPlayerState.profileName,
                false
            );
        if (phoenixState != null || !create || trackedPlayerState.isPhoenixTracked()) {
            return phoenixState != null
                ? phoenixState
                : phoenixResurrectionRegistry.resolveState(
                    trackedPlayerState.profileName,
                    true
                );
        }

        return null;
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
