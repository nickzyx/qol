package megawalls.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import megawalls.MegaWallsMod;
import megawalls.render.CompactSidebarHud;
import megawalls.render.HunterForceOfNatureHud;
import megawalls.render.MobilityLeapAlertHud;
import megawalls.render.PregameClassTrackerHud;
import megawalls.util.ChatNotifier;

public final class MegaWallsConfig extends Config {

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Announce Energy",
        description = "Press this key to send your current ability energy as a real chat message.",
        category = "General",
        subcategory = "Energy Announcer"
    )
    public OneKeyBind energyReporterKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Prevent accidental crafting table, chest, furnace, and hopper interactions.",
        category = "General",
        subcategory = "Interaction Guard"
    )
    public boolean swordInteractionGuard = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only Empty Hand",
        description = "Only allow guarded block interactions when your hand is empty. When disabled, guarded interactions are blocked while holding a sword.",
        category = "General",
        subcategory = "Interaction Guard"
    )
    public boolean interactionGuardEmptyHandOnly = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Hide Action Bar Gathering",
        description = "Hide Gathering text from action bar when inactive.",
        category = "General",
        subcategory = "Action Bar"
    )
    public boolean hideActionBarGathering = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Compact Sidebar",
        description = "Hide less useful Mega Walls sidebar lines without changing the real scoreboard.",
        category = "General",
        subcategory = "Sidebar"
    )
    public boolean compactSidebar = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Original Background",
        description = "Draw the vanilla-style row background behind compact sidebar text.",
        category = "General",
        subcategory = "Sidebar"
    )
    public boolean compactSidebarOriginalBackground = false;

    @HUD(
        name = "Compact Sidebar HUD",
        category = "General",
        subcategory = "Sidebar"
    )
    public CompactSidebarHud compactSidebarHud = new CompactSidebarHud();

    @Switch(
        size = OptionSize.DUAL,
        name = "Visible Barriers",
        description = "Render barrier blocks as glass.",
        category = "Render",
        subcategory = "Barriers"
    )
    public boolean visibleBarriers = false;

    @Dropdown(
        size = OptionSize.DUAL,
        name = "Barrier Style (Restart Required)",
        description = "Choose the glass color to render barriers. Restart Minecraft after changing this.",
        options = {
            "White Glass",
            "Red Glass",
            "Green Glass",
            "Blue Glass",
            "Yellow Glass",
            "Cyan Glass",
            "Purple Glass",
        },
        category = "Render",
        subcategory = "Barriers"
    )
    public int barrierRenderStyle = 0;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle Tablist Display",
        description = "Press this key to show or hide Phoenix resurrection icons in the tablist.",
        category = "Render",
        subcategory = "Phoenix Resurrection Tracker"
    )
    public OneKeyBind phoenixTablistKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Track Phoenix resurrection state for PHX players.",
        category = "General",
        subcategory = "Phoenix Resurrection Tracker"
    )
    public boolean phoenixDetectorEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Resurrection in tablist",
        description = "Show a heart icon next to Phoenix players in the tablist.",
        category = "Render",
        subcategory = "Phoenix Resurrection Tracker"
    )
    public boolean phoenixInTablist = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Resurrection in nametags",
        description = "Show a colored heart icon in Phoenix player nametags.",
        category = "Render",
        subcategory = "Phoenix Resurrection Tracker"
    )
    public boolean phoenixInNametags = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification",
        description = "Print a chat notification when a tracked Phoenix loses resurrection.",
        category = "General",
        subcategory = "Phoenix Resurrection Tracker"
    )
    public boolean phoenixAutoTalk = false;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle Tablist Display",
        description = "Press this key in Mega Walls to show or hide diamond gear icons in the tablist.",
        category = "Render",
        subcategory = "Diamond Tracker"
    )
    public OneKeyBind diamondTablistKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Track non-kit diamond armor and swords held by Mega Walls players.",
        category = "General",
        subcategory = "Diamond Tracker"
    )
    public boolean diamondDetectorEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Diamond Armor in tablist",
        description = "Show icons for diamond armor pieces that are not part of a player's class kit.",
        category = "Render",
        subcategory = "Diamond Tracker"
    )
    public boolean diamondArmorInTablist = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Diamond Sword in tablist",
        description = "Show a sword icon when a player is seen with a diamond sword that is not from their class kit.",
        category = "Render",
        subcategory = "Diamond Tracker"
    )
    public boolean diamondSwordInTablist = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification: Diamond Armor",
        description = "Print a chat notification when a player is seen with non-kit diamond armor.",
        category = "General",
        subcategory = "Diamond Tracker"
    )
    public boolean autoTellDiamondArmor = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification: Diamond Sword",
        description = "Print a chat notification when a player is seen with a non-kit diamond sword.",
        category = "General",
        subcategory = "Diamond Tracker"
    )
    public boolean autoTellDiamondSword = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only in Deathmatch",
        description = "Only run the Diamond Tracker after deathmatch starts.",
        category = "General",
        subcategory = "Diamond Tracker"
    )
    public boolean diamondDeathmatchOnly = false;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle Tablist Display",
        description = "Press this key in Mega Walls to show or hide potion counts in the tablist.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public OneKeyBind potionTablistKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Track healing potion counts using tablist health increases.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public boolean potionDetectorEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Potion in tablist",
        description = "Show tracked remaining healing potions next to players in the tablist.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public boolean potionInTablist = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Potion in nametags",
        description = "Show tracked remaining healing potions in player nametags.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public boolean potionInNametags = false;

    @Color(
        size = OptionSize.DUAL,
        name = "Nametag Color",
        description = "Choose the color used for potion counts shown in nametags.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public OneColor potionNametagColor = new OneColor(255, 85, 85);

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification",
        description = "Print a chat notification when a potion use is detected.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public boolean potionDebug = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only in Deathmatch",
        description = "Only run the Potion Tracker after deathmatch starts.",
        category = "Experimental",
        subcategory = "Potion Tracker"
    )
    public boolean potionDeathmatchOnly = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Track strength activations for supported Mega Walls classes.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean strengthDetectorEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Zombie Strength",
        description = "Detect Zombie strength using the Zombie class tag and hurt sound.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean zombieStrength = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification",
        description = "Print chat alerts when strength is detected.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean autoTellStrength = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only Show One Alert Message",
        description = "Print one strength alert instead of the default repeated alerts.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean strengthPrintOnce = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Outline Strength Players",
        description = "Render a warning outline around players with detected strength.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean strengthOutline = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only in Deathmatch",
        description = "Only run the Strength Tracker after deathmatch starts.",
        category = "General",
        subcategory = "Strength Tracker"
    )
    public boolean strengthDeathmatchOnly = false;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle KeyBind",
        description = "Press this key to enable or disable.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public OneKeyBind hunterFonKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enable",
        description = "Replace Hunter Force of Nature actionbar text with a slot-style animation.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public boolean hunterFonSlotHud = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Strength Sound",
        description = "Play a sound when Force of Nature rolls Strength.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public boolean hunterFonStrengthSound = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Roll Sound",
        description = "Play a sound while the Force of Nature slot animation rolls.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public boolean hunterFonRollSound = true;

    @Dropdown(
        size = OptionSize.DUAL,
        name = "Roll Sound Type",
        description = "Choose the sound used while the Force of Nature slot animation rolls.",
        options = { "Default", "Mystery Box" },
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public int hunterFonRollSoundType = 0;

    @Switch(
        size = OptionSize.DUAL,
        name = "Use HUD",
        description = "Display the Force of Nature roll on a movable HUD instead of in the actionbar.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public boolean hunterFonDraggableHud = false;

    @HUD(
        name = "F.O.N. HUD",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public HunterForceOfNatureHud hunterFonHud = new HunterForceOfNatureHud();

    @Switch(
        size = OptionSize.DUAL,
        name = "Text Shadow",
        description = "Draw the custom Force of Nature actionbar text with a shadow.",
        category = "Casino",
        subcategory = "Hunter F.O.N."
    )
    public boolean hunterFonTextShadow = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Show class counts for players in the Mega Walls pregame queue.",
        category = "General",
        subcategory = "Class Tracker"
    )
    public boolean pregameClassTrackerEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Unknown",
        description = "Show players whose queue skin is not mapped to a class.",
        category = "General",
        subcategory = "Class Tracker"
    )
    public boolean pregameClassTrackerShowUnknown = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Compact Icons",
        description = "Replace class names with skin face icons.",
        category = "General",
        subcategory = "Class Tracker"
    )
    public boolean pregameClassTrackerClassIcons = false;

    @HUD(
        name = "Class Tracker HUD",
        category = "General",
        subcategory = "Class Tracker"
    )
    public PregameClassTrackerHud pregameClassTrackerHud =
        new PregameClassTrackerHud();

    @Switch(
        size = OptionSize.DUAL,
        name = "Text Shadow",
        description = "Draw the class tracker HUD text with a shadow.",
        category = "General",
        subcategory = "Class Tracker"
    )
    public boolean pregameClassTrackerTextShadow = true;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle KeyBind",
        description = "Press this key in Mega Walls to enable or disable Mobility Alert.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public OneKeyBind mobilityAlertKeybind = new OneKeyBind();

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Ping Location",
        description = "Press this key to place a marker at the block or player you are looking at.",
        category = "General",
        subcategory = "Waypoints"
    )
    public OneKeyBind waypointPingKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Render waypoint boxes and beams in the world.",
        category = "General",
        subcategory = "Waypoints"
    )
    public boolean waypointRenderWorld = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Shared Waypoints",
        description = "Share pings with party members. Party members without qol may see these messages.",
        category = "General",
        subcategory = "Waypoints"
    )
    public boolean waypointSharingEnabled = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Hide Sync Messages",
        description = "Hide qol waypoint sync messages from chat when receiving shared pings.",
        category = "General",
        subcategory = "Waypoints"
    )
    public boolean waypointHideSyncMessages = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Hide Nearby Waypoints",
        description = "Hide waypoints when you are within 10 meters of the marker.",
        category = "General",
        subcategory = "Waypoints"
    )
    public boolean waypointHideNearbyTitles = false;

    @Dropdown(
        size = OptionSize.DUAL,
        name = "Message Channel",
        description = "Choose where player target ping messages are sent.",
        options = { "Public Chat", "Party Chat" },
        category = "General",
        subcategory = "Waypoints"
    )
    public int waypointPlayerTargetMessageChannel = 0;

    @Switch(
        size = OptionSize.DUAL,
        name = "Enabled",
        description = "Track nearby enemy Spider and Enderman mobility threats.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityAlertEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Spider",
        description = "Detect enemy Spiders in Leap range and Spider Leap activation movement.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityAlertSpider = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Enderman",
        description = "Detect enemy Endermen in Teleport range.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityAlertEnderman = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Chat Notification",
        description = "Print chat alerts for Spider Leap and Enderman Teleport range warnings.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityChatNotification = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Leap GUI Alert",
        description = "Show a movable on-screen alert when an enemy Spider activates Leap.",
        category = "Experimental",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityLeapGuiAlert = false;

    @HUD(
        name = "Leap Alert HUD",
        category = "Experimental",
        subcategory = "Mobility Alert"
    )
    public MobilityLeapAlertHud mobilityLeapAlertHud =
        new MobilityLeapAlertHud();

    @Switch(
        size = OptionSize.DUAL,
        name = "Text Shadow",
        description = "Draw the Leap Alert HUD text with a shadow.",
        category = "Experimental",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityLeapAlertTextShadow = true;

    @Switch(
        size = OptionSize.DUAL,
        name = "Show Compass HUD",
        description = "Show nearby active mobility alerts on the compass HUD.",
        category = "Experimental",
        subcategory = "Mobility HUD"
    )
    public boolean mobilityCompassHud = false;

    @Slider(
        name = "Compass X",
        description = "Move the Mobility Alert compass horizontally across the screen.",
        min = 0.0F,
        max = 100.0F,
        step = 1,
        category = "Experimental",
        subcategory = "Mobility HUD"
    )
    public int mobilityCompassX = 50;

    @Slider(
        name = "Compass Y",
        description = "Move the Mobility Alert compass vertically across the screen.",
        min = 0.0F,
        max = 100.0F,
        step = 1,
        category = "Experimental",
        subcategory = "Mobility HUD"
    )
    public int mobilityCompassY = 50;

    @Slider(
        name = "Compass Radius",
        description = "Adjust how far compass markers sit from the HUD center.",
        min = 10.0F,
        max = 160.0F,
        step = 1,
        category = "Experimental",
        subcategory = "Mobility HUD"
    )
    public int mobilityCompassRadius = 55;

    @Slider(
        name = "Chat Print Interval",
        description = "Set the cooldown between repeated Mobility Alert chat messages.",
        min = 1.0F,
        max = 10.0F,
        step = 1,
        category = "General",
        subcategory = "Mobility Alert"
    )
    public int mobilityAlertIntervalSeconds = 5;

    @Switch(
        size = OptionSize.DUAL,
        name = "Only in Deathmatch",
        description = "Only run Mobility Alert after deathmatch starts.",
        category = "General",
        subcategory = "Mobility Alert"
    )
    public boolean mobilityAlertDeathmatchOnly = false;

    @KeyBind(
        size = OptionSize.DUAL,
        name = "Toggle Transparent Snowmen",
        description = "Press this key in Mega Walls to enable or disable transparent Snowman rendering.",
        category = "Render",
        subcategory = "Snowmen"
    )
    public OneKeyBind transparentSnowmenKeybind = new OneKeyBind();

    @Switch(
        size = OptionSize.DUAL,
        name = "Transparent Snowmen",
        description = "Render Snowman mobs translucent while in Mega Walls.",
        category = "Render",
        subcategory = "Snowmen"
    )
    public boolean transparentSnowmen = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Apply to All Snowmen",
        description = "Render enemy Snowman mobs translucent too. When disabled, only ally Snowman mobs are affected.",
        category = "Render",
        subcategory = "Snowmen"
    )
    public boolean transparentSnowmenAllTeams = false;

    @Slider(
        name = "Snowman Opacity",
        description = "Adjust how visible transparent Snowman mobs are.",
        min = 10.0F,
        max = 90.0F,
        step = 5,
        category = "Render",
        subcategory = "Snowmen"
    )
    public int transparentSnowmenOpacity = 35;

    @Switch(
        size = OptionSize.DUAL,
        name = "Developer Debug Logging",
        description = "Write detailed chat, sound, packet, scoreboard, tablist, and player snapshots to .minecraft/qol-debug.",
        category = "Experimental",
        subcategory = "Developer Debug"
    )
    public boolean developerDebugEnabled = false;

    @Switch(
        size = OptionSize.DUAL,
        name = "Check for Updates",
        description = "Check GitHub releases for newer versions after Minecraft starts.",
        category = "General",
        subcategory = "Auto Update"
    )
    public boolean updateCheckerEnabled = true;

    private transient boolean phoenixTablistDisplayEnabled = true;
    private transient boolean diamondTablistDisplayEnabled = true;
    private transient boolean potionTablistDisplayEnabled = true;

    public MegaWallsConfig() {
        super(
            new Mod(
                MegaWallsMod.MOD_NAME,
                ModType.UTIL_QOL,
                "/assets/qol/qol-logo-dark.png",
                76,
                76
            ),
            "qol.json"
        );
        initialize();
        registerKeyBind(energyReporterKeybind, MegaWallsMod::reportEnergyNow);
        registerKeyBind(
            phoenixTablistKeybind,
            this::togglePhoenixTablistDisplay
        );
        registerKeyBind(
            diamondTablistKeybind,
            this::toggleDiamondTablistDisplay
        );
        registerKeyBind(potionTablistKeybind, this::togglePotionTablistDisplay);
        registerKeyBind(mobilityAlertKeybind, this::toggleMobilityAlert);
        registerKeyBind(waypointPingKeybind, MegaWallsMod::pingWaypointNow);
        registerKeyBind(hunterFonKeybind, this::toggleHunterFon);
        registerKeyBind(
            transparentSnowmenKeybind,
            this::toggleTransparentSnowmen
        );
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
        if (!MegaWallsMod.isInMegaWallsGame()) {
            return;
        }

        phoenixTablistDisplayEnabled = !phoenixTablistDisplayEnabled;
        ChatNotifier.toggle(
            "Phoenix tablist display",
            phoenixTablistDisplayEnabled
        );
    }

    private void toggleDiamondTablistDisplay() {
        if (!MegaWallsMod.isInMegaWallsGame()) {
            return;
        }

        diamondTablistDisplayEnabled = !diamondTablistDisplayEnabled;
        ChatNotifier.toggle(
            "Diamond tablist display",
            diamondTablistDisplayEnabled
        );
    }

    private void togglePotionTablistDisplay() {
        if (!MegaWallsMod.isInMegaWallsGame()) {
            return;
        }

        potionTablistDisplayEnabled = !potionTablistDisplayEnabled;
        ChatNotifier.toggle(
            "Potion tablist display",
            potionTablistDisplayEnabled
        );
    }

    private void toggleMobilityAlert() {
        if (!MegaWallsMod.isInMegaWallsGame()) {
            return;
        }

        mobilityAlertEnabled = !mobilityAlertEnabled;
        ChatNotifier.toggle("Mobility Alert", mobilityAlertEnabled);
        save();
    }

    private void toggleHunterFon() {
        hunterFonSlotHud = !hunterFonSlotHud;
        ChatNotifier.toggle("Hunter F.O.N.", hunterFonSlotHud);
        save();
    }

    private void toggleTransparentSnowmen() {
        if (!MegaWallsMod.isInMegaWallsGame()) {
            return;
        }

        transparentSnowmen = !transparentSnowmen;
        ChatNotifier.toggle("Transparent Snowmen", transparentSnowmen);
        save();
    }
}
