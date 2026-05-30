package megawalls.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import megawalls.MegaWallsMod;
import megawalls.render.MobilityLeapAlertHud;
import megawalls.util.ChatNotifier;

public final class MegaWallsConfig extends Config {

    @KeyBind(size = OptionSize.DUAL, name = "Announce Energy", description = "Press this key to send your current ability energy as a real chat message.", category = "General", subcategory = "Energy Announcer")
    public OneKeyBind energyReporterKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Prevent accidental crafting table, chest, furnace, and hopper interactions.", category = "General", subcategory = "Interaction Guard")
    public boolean swordInteractionGuard = false;

    @Switch(size = OptionSize.DUAL, name = "Only Empty Hand", description = "Only allow guarded block interactions when your hand is empty. When disabled, guarded interactions are blocked while holding a sword.", category = "General", subcategory = "Interaction Guard")
    public boolean interactionGuardEmptyHandOnly = false;

    @KeyBind(size = OptionSize.DUAL, name = "Toggle Tablist Display", description = "Press this key in Mega Walls to show or hide Phoenix resurrection icons in the tablist.", category = "Render", subcategory = "Phoenix Resurrection Tracker")
    public OneKeyBind phoenixTablistKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Track Phoenix resurrection state for PHX players.", category = "General", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixDetectorEnabled = false;

    @Switch(size = OptionSize.DUAL, name = "Show Resurrection in tablist", description = "Show a heart icon next to Phoenix players in the tablist.", category = "Render", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixInTablist = false;

    @Switch(size = OptionSize.DUAL, name = "Show Resurrection in nametags", description = "Show a colored heart icon in Phoenix player nametags.", category = "Render", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixInNametags = false;

    @Switch(size = OptionSize.DUAL, name = "Chat Notification", description = "Print a chat notification when a tracked Phoenix loses resurrection.", category = "General", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixAutoTalk = false;

    @KeyBind(size = OptionSize.DUAL, name = "Toggle Tablist Display", description = "Press this key in Mega Walls to show or hide diamond gear icons in the tablist.", category = "Render", subcategory = "Diamond Tracker")
    public OneKeyBind diamondTablistKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Track non-kit diamond armor and swords held by Mega Walls players.", category = "General", subcategory = "Diamond Tracker")
    public boolean diamondDetectorEnabled = false;

    @Switch(size = OptionSize.DUAL, name = "Show Diamond Armor in tablist", description = "Show icons for diamond armor pieces that are not part of a player's class kit.", category = "Render", subcategory = "Diamond Tracker")
    public boolean diamondArmorInTablist = true;

    @Switch(size = OptionSize.DUAL, name = "Show Diamond Sword in tablist", description = "Show a sword icon when a player is seen with a diamond sword that is not from their class kit.", category = "Render", subcategory = "Diamond Tracker")
    public boolean diamondSwordInTablist = true;

    @Switch(size = OptionSize.DUAL, name = "Chat Notification: Diamond Armor", description = "Print a chat notification when a player is seen with non-kit diamond armor.", category = "General", subcategory = "Diamond Tracker")
    public boolean autoTellDiamondArmor = false;

    @Switch(size = OptionSize.DUAL, name = "Chat Notification: Diamond Sword", description = "Print a chat notification when a player is seen with a non-kit diamond sword.", category = "General", subcategory = "Diamond Tracker")
    public boolean autoTellDiamondSword = false;

    @Switch(size = OptionSize.DUAL, name = "Only in Deathmatch", description = "Only run the Diamond Tracker after deathmatch starts.", category = "General", subcategory = "Diamond Tracker")
    public boolean diamondDeathmatchOnly = false;

    @KeyBind(size = OptionSize.DUAL, name = "Toggle Tablist Display", description = "Press this key in Mega Walls to show or hide potion counts in the tablist.", category = "Experimental", subcategory = "Potion Tracker")
    public OneKeyBind potionTablistKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Track healing potion counts using tablist health increases.", category = "Experimental", subcategory = "Potion Tracker")
    public boolean potionDetectorEnabled = false;

    @Switch(size = OptionSize.DUAL, name = "Show Potion in tablist", description = "Show tracked remaining healing potions next to players in the tablist.", category = "Experimental", subcategory = "Potion Tracker")
    public boolean potionInTablist = true;

    @Switch(size = OptionSize.DUAL, name = "Show Potion in nametags", description = "Show tracked remaining healing potions in player nametags.", category = "Experimental", subcategory = "Potion Tracker")
    public boolean potionInNametags = false;

    @Color(size = OptionSize.DUAL, name = "Nametag Color", description = "Choose the color used for potion counts shown in nametags.", category = "Experimental", subcategory = "Potion Tracker")
    public OneColor potionNametagColor = new OneColor(255, 85, 85);

    @Switch(size = OptionSize.DUAL, name = "Chat Notification", description = "Print a chat notification when a potion use is detected.", category = "Experimental", subcategory = "Potion Tracker")
    public boolean potionDebug = true;

    @Switch(size = OptionSize.DUAL, name = "Only in Deathmatch", description = "Only run the Potion Tracker after deathmatch starts.", category = "Experimental", subcategory = "Potion Tracker")
    public boolean potionDeathmatchOnly = false;

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Track strength activations for supported Mega Walls classes.", category = "General", subcategory = "Strength Tracker")
    public boolean strengthDetectorEnabled = false;

    @Switch(size = OptionSize.DUAL, name = "Zombie Strength", description = "Detect Zombie strength using the Zombie class tag and hurt sound.", category = "General", subcategory = "Strength Tracker")
    public boolean zombieStrength = false;

    @Switch(size = OptionSize.DUAL, name = "Chat Notification", description = "Print chat alerts when strength is detected.", category = "General", subcategory = "Strength Tracker")
    public boolean autoTellStrength = false;

    @Switch(size = OptionSize.DUAL, name = "Only Show One Alert Message", description = "Print one strength alert instead of the default repeated alerts.", category = "General", subcategory = "Strength Tracker")
    public boolean strengthPrintOnce = false;

    @Switch(size = OptionSize.DUAL, name = "Only in Deathmatch", description = "Only run the Strength Tracker after deathmatch starts.", category = "General", subcategory = "Strength Tracker")
    public boolean strengthDeathmatchOnly = false;

    @KeyBind(size = OptionSize.DUAL, name = "Toggle KeyBind", description = "Press this key in Mega Walls to enable or disable Mobility Alert.", category = "General", subcategory = "Mobility Alert")
    public OneKeyBind mobilityAlertKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Enabled", description = "Track nearby enemy Spider and Enderman mobility threats.", category = "General", subcategory = "Mobility Alert")
    public boolean mobilityAlertEnabled = false;

    @Switch(size = OptionSize.DUAL, name = "Spider", description = "Detect enemy Spiders in Leap range and Spider Leap activation movement.", category = "General", subcategory = "Mobility Alert")
    public boolean mobilityAlertSpider = true;

    @Switch(size = OptionSize.DUAL, name = "Enderman", description = "Detect enemy Endermen in Teleport range.", category = "General", subcategory = "Mobility Alert")
    public boolean mobilityAlertEnderman = true;

    @Switch(size = OptionSize.DUAL, name = "Chat Notification", description = "Print chat alerts for Spider Leap and Enderman Teleport range warnings.", category = "General", subcategory = "Mobility Alert")
    public boolean mobilityChatNotification = true;

    @Switch(size = OptionSize.DUAL, name = "Show Leap GUI Alert", description = "Show a movable on-screen alert when an enemy Spider activates Leap.", category = "Experimental", subcategory = "Mobility Alert")
    public boolean mobilityLeapGuiAlert = true;

    @HUD(name = "Leap Alert HUD", category = "Experimental", subcategory = "Mobility Alert")
    public MobilityLeapAlertHud mobilityLeapAlertHud = new MobilityLeapAlertHud();

    @Switch(size = OptionSize.DUAL, name = "Show Compass HUD", description = "Show nearby active mobility alerts on the compass HUD.", category = "Experimental", subcategory = "Mobility HUD")
    public boolean mobilityCompassHud = true;

    @Slider(name = "Compass X", description = "Move the Mobility Alert compass horizontally across the screen.", min = 0.0F, max = 100.0F, step = 1, category = "Experimental", subcategory = "Mobility HUD")
    public int mobilityCompassX = 50;

    @Slider(name = "Compass Y", description = "Move the Mobility Alert compass vertically across the screen.", min = 0.0F, max = 100.0F, step = 1, category = "Experimental", subcategory = "Mobility HUD")
    public int mobilityCompassY = 50;

    @Slider(name = "Compass Radius", description = "Adjust how far compass markers sit from the HUD center.", min = 10.0F, max = 160.0F, step = 1, category = "Experimental", subcategory = "Mobility HUD")
    public int mobilityCompassRadius = 55;

    @Slider(name = "Chat Print Interval", description = "Set the cooldown between repeated Mobility Alert chat messages.", min = 1.0F, max = 10.0F, step = 1, category = "General", subcategory = "Mobility Alert")
    public int mobilityAlertIntervalSeconds = 5;

    @Switch(size = OptionSize.DUAL, name = "Only in Deathmatch", description = "Only run Mobility Alert after deathmatch starts.", category = "General", subcategory = "Mobility Alert")
    public boolean mobilityAlertDeathmatchOnly = false;

    @KeyBind(size = OptionSize.DUAL, name = "Toggle Transparent Snowmen", description = "Press this key in Mega Walls to enable or disable transparent Snowman rendering.", category = "Render", subcategory = "Visuals")
    public OneKeyBind transparentSnowmenKeybind = new OneKeyBind();

    @Switch(size = OptionSize.DUAL, name = "Transparent Snowmen", description = "Render Snowman mobs translucent while in Mega Walls.", category = "Render", subcategory = "Visuals")
    public boolean transparentSnowmen = false;

    @Switch(size = OptionSize.DUAL, name = "Apply to All Snowmen", description = "Render enemy Snowman mobs translucent too. When disabled, only ally Snowman mobs are affected.", category = "Render", subcategory = "Visuals")
    public boolean transparentSnowmenAllTeams = false;

    @Slider(name = "Snowman Opacity", description = "Adjust how visible transparent Snowman mobs are.", min = 10.0F, max = 90.0F, step = 5, category = "Render", subcategory = "Visuals")
    public int transparentSnowmenOpacity = 35;

    @Switch(size = OptionSize.DUAL, name = "Developer Debug Logging", description = "Write detailed chat, sound, packet, scoreboard, tablist, and player snapshots to .minecraft/qol-debug.", category = "Experimental", subcategory = "Developer Debug")
    public boolean developerDebugEnabled = false;

    private transient boolean phoenixTablistDisplayEnabled = true;
    private transient boolean diamondTablistDisplayEnabled = true;
    private transient boolean potionTablistDisplayEnabled = true;

    public MegaWallsConfig() {
        super(new Mod(MegaWallsMod.MOD_NAME, ModType.UTIL_QOL, "/assets/qol/qol-logo-dark.png", 76, 76), "qol.json");
        initialize();
        registerKeyBind(energyReporterKeybind, MegaWallsMod::reportEnergyNow);
        registerKeyBind(phoenixTablistKeybind, this::togglePhoenixTablistDisplay);
        registerKeyBind(diamondTablistKeybind, this::toggleDiamondTablistDisplay);
        registerKeyBind(potionTablistKeybind, this::togglePotionTablistDisplay);
        registerKeyBind(mobilityAlertKeybind, this::toggleMobilityAlert);
        registerKeyBind(transparentSnowmenKeybind, this::toggleTransparentSnowmen);
    }

    public boolean isPhoenixTablistDisplayEnabled() {
        return phoenixTablistDisplayEnabled;
    }

    public boolean isDiamondTablistDisplayEnabled() {
        return diamondTablistDisplayEnabled;
    }

    public boolean isPotionTablistDisplayEnabled() {
        return potionTablistDisplayEnabled;
    }

    public boolean canUseDiamond(boolean deathmatchActive) {
        return !diamondDeathmatchOnly || deathmatchActive;
    }

    public boolean canUsePotion(boolean deathmatchActive) {
        return !potionDeathmatchOnly || deathmatchActive;
    }

    public boolean canUseStrength(boolean deathmatchActive) {
        return !strengthDeathmatchOnly || deathmatchActive;
    }

    public boolean canUseMobilityAlert(boolean deathmatchActive) {
        return !mobilityAlertDeathmatchOnly || deathmatchActive;
    }

    private void togglePhoenixTablistDisplay() {
        if (!MegaWallsMod.isInMegaWalls()) {
            return;
        }

        phoenixTablistDisplayEnabled = !phoenixTablistDisplayEnabled;
        ChatNotifier.toggle("Phoenix tablist display", phoenixTablistDisplayEnabled);
    }

    private void toggleDiamondTablistDisplay() {
        if (!MegaWallsMod.isInMegaWalls()) {
            return;
        }

        diamondTablistDisplayEnabled = !diamondTablistDisplayEnabled;
        ChatNotifier.toggle("Diamond tablist display", diamondTablistDisplayEnabled);
    }

    private void togglePotionTablistDisplay() {
        if (!MegaWallsMod.isInMegaWalls()) {
            return;
        }

        potionTablistDisplayEnabled = !potionTablistDisplayEnabled;
        ChatNotifier.toggle("Potion tablist display", potionTablistDisplayEnabled);
    }

    private void toggleMobilityAlert() {
        if (!MegaWallsMod.isInMegaWalls()) {
            return;
        }

        mobilityAlertEnabled = !mobilityAlertEnabled;
        ChatNotifier.toggle("Mobility Alert", mobilityAlertEnabled);
        save();
    }

    private void toggleTransparentSnowmen() {
        if (!MegaWallsMod.isInMegaWalls()) {
            return;
        }

        transparentSnowmen = !transparentSnowmen;
        ChatNotifier.toggle("Transparent Snowmen", transparentSnowmen);
        save();
    }
}
