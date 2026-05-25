package megawalls.config;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import cc.polyfrost.oneconfig.config.annotations.Slider;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import megawalls.MegaWallsMod;
import megawalls.util.ChatNotifier;

public final class MegaWallsConfig extends Config {

    @KeyBind(name = "KeyBind", category = "Mega Walls", subcategory = "Energy Tracker")
    public OneKeyBind energyReporterKeybind = new OneKeyBind();

    @Switch(name = "Show Hits Needed", category = "Mega Walls", subcategory = "Energy Tracker")
    public boolean showHitsNeeded = false;

    @Switch(name = "Show Ability Name", category = "Mega Walls", subcategory = "Energy Tracker")
    public boolean showAbilityName = false;

    @KeyBind(name = "Toggle Tablist Display", category = "Mega Walls", subcategory = "Phoenix Resurrection Tracker")
    public OneKeyBind phoenixTablistKeybind = new OneKeyBind();

    @Switch(name = "Enabled", category = "Mega Walls", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixDetectorEnabled = false;

    @Switch(name = "Show Resurrection in tablist", category = "Mega Walls", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixInTablist = false;

    @Switch(name = "Show Resurrection in nametags", category = "Mega Walls", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixInNametags = false;

    @Switch(name = "Chat Notification", category = "Mega Walls", subcategory = "Phoenix Resurrection Tracker")
    public boolean phoenixAutoTalk = false;

    @KeyBind(name = "Toggle Tablist Display", category = "Mega Walls", subcategory = "Diamond Tracker")
    public OneKeyBind diamondTablistKeybind = new OneKeyBind();

    @Switch(name = "Enabled", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean diamondDetectorEnabled = false;

    @Switch(name = "Show Diamond Armor in tablist", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean diamondArmorInTablist = true;

    @Switch(name = "Show Diamond Sword in tablist", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean diamondSwordInTablist = true;

    @Switch(name = "Chat Notification: Diamond Armor", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean autoTellDiamondArmor = false;

    @Switch(name = "Chat Notification: Diamond Sword", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean autoTellDiamondSword = false;

    @Switch(name = "Only in Deathmatch", category = "Mega Walls", subcategory = "Diamond Tracker")
    public boolean diamondDeathmatchOnly = false;

    @KeyBind(name = "Toggle Tablist Display", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public OneKeyBind potionTablistKeybind = new OneKeyBind();

    @Switch(name = "Enabled", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public boolean potionDetectorEnabled = false;

    @Switch(name = "Show Potion in tablist", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public boolean potionInTablist = true;

    @Switch(name = "Show Potion in nametags", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public boolean potionInNametags = false;

    @Color(name = "Nametag Color", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public OneColor potionNametagColor = new OneColor(255, 85, 85);

    @Switch(name = "Chat Notification", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public boolean potionDebug = true;

    @Switch(name = "Only in Deathmatch", category = "Mega Walls", subcategory = "Potion Tracker (Experimental)")
    public boolean potionDeathmatchOnly = false;

    @Switch(name = "Enabled", category = "Mega Walls", subcategory = "Strength Tracker")
    public boolean strengthDetectorEnabled = false;

    @Switch(name = "Zombie Strength", category = "Mega Walls", subcategory = "Strength Tracker")
    public boolean zombieStrength = false;

    @Switch(name = "Chat Notification", category = "Mega Walls", subcategory = "Strength Tracker")
    public boolean autoTellStrength = false;

    @Switch(name = "Only Show One Alert Message", category = "Mega Walls", subcategory = "Strength Tracker")
    public boolean strengthPrintOnce = false;

    @Switch(name = "Only in Deathmatch", category = "Mega Walls", subcategory = "Strength Tracker")
    public boolean strengthDeathmatchOnly = false;

    @KeyBind(name = "Toggle KeyBind", category = "Mega Walls", subcategory = "Mobility Alert")
    public OneKeyBind mobilityAlertKeybind = new OneKeyBind();

    @Switch(name = "Enabled", category = "Mega Walls", subcategory = "Mobility Alert")
    public boolean mobilityAlertEnabled = false;

    @Switch(name = "Spider", category = "Mega Walls", subcategory = "Mobility Alert")
    public boolean mobilityAlertSpider = true;

    @Switch(name = "Enderman", category = "Mega Walls", subcategory = "Mobility Alert")
    public boolean mobilityAlertEnderman = true;

    @Slider(name = "Chat Print Interval", min = 1.0F, max = 5.0F, step = 1, category = "Mega Walls", subcategory = "Mobility Alert")
    public int mobilityAlertIntervalSeconds = 3;

    @Switch(name = "Only in Deathmatch", category = "Mega Walls", subcategory = "Mobility Alert")
    public boolean mobilityAlertDeathmatchOnly = false;

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
}
