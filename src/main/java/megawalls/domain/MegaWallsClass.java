package megawalls.domain;

import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum MegaWallsClass {

    ANGEL("ANG", "Angel", "Divine Intervention", 2, 16.0D, 12, 12, DiamondGear.LEGGINGS),
    ARCANIST("ARC", "Arcanist", "Arcane Beam", 2, 16.0D, 34, 34, DiamondGear.SWORD, DiamondGear.LEGGINGS),
    ASSASSIN("ASN", "Assassin", "Shadow Cloak", -1, -2.0D, 10, 10, DiamondGear.SWORD),
    AUTOMATON("ATN", "Automaton", "A-23 Protocol", 3, 13.0D, 4, 4, DiamondGear.LEGGINGS, DiamondGear.BOOTS),
    BLAZE("BLA", "Blaze", "Immolating Burst", 2, 16.0D, 8, 4, DiamondGear.SWORD),
    COW("COW", "Cow", "Soothing Moo", 1, 20.0D, 25, 20, DiamondGear.CHESTPLATE),
    CREEPER("CRE", "Creeper", "Detonate", 2, 16.0D, 30, 20, DiamondGear.LEGGINGS),
    DRAGON("DRG", "Dragon", "Scorching Breath", 2, 16.0D, 12, 8, DiamondGear.HELMET),
    DREADLORD("DRE", "Dreadlord", "Shadow Burst", 2, 16.0D, 10, 10, DiamondGear.SWORD, DiamondGear.HELMET),
    ENDERMAN("END", "Enderman", "Teleport", 2, 16.0D, 20, 20, DiamondGear.BOOTS),
    GOLEM("GOL", "Golem", "Iron Punch", -1, -2.0D, 10, 10, DiamondGear.CHESTPLATE, DiamondGear.BOOTS),
    HEROBRINE("HBR", "Herobrine", "Wrath", 2, 14.0D, 25, 25, DiamondGear.SWORD),
    HUNTER("HUN", "Hunter", "Eagles Eye", -1, -2.0D, 4, 8, DiamondGear.HELMET),
    MOLEMAN("MOL", "Moleman", "Dig", 2, 18.0D, 10, 10, DiamondGear.SHOVEL, DiamondGear.LEGGINGS),
    PHOENIX("PHX", "Phoenix", "Sun Ray", 2, 16.0D, 8, 14),
    PIGMAN("PIG", "Pigman", "Burning Soul", 1, 20.0D, 10, 10, DiamondGear.SWORD, DiamondGear.CHESTPLATE),
    PIRATE("PIR", "Pirate", "Cannon Fire", -1, -2.0D, 12, 12, DiamondGear.HELMET, DiamondGear.BOOTS),
    RENEGADE("REN", "Renegade", "Rend", 2, 16.0D, 13, 17, DiamondGear.BOOTS),
    SHAMAN("SHA", "Shaman", "Tornado", 2, 16.0D, 10, 10, DiamondGear.SWORD, DiamondGear.BOOTS),
    SHARK("SRK", "Shark", "From the Depths", 2, 16.0D, 18, 18, DiamondGear.SWORD, DiamondGear.BOOTS),
    SHEEP("SHP", "Sheep", "Wool War", 2, 16.0D, 10, 5, DiamondGear.LEGGINGS),
    SKELETON("SKE", "Skeleton", "Explosive Arrow", 2, 16.0D, 0, 20, DiamondGear.HELMET),
    SNOWMAN("SNO", "Snowman", "Ice Bolt", 2, 16.0D, 8, 8, DiamondGear.SWORD, DiamondGear.SHOVEL, DiamondGear.LEGGINGS),
    SPIDER("SPI", "Spider", "Leap", 2, 16.0D, 8, 8, DiamondGear.SWORD, DiamondGear.BOOTS),
    SQUID("SQU", "Squid", "Squid Splash", 3, 12.0D, 10, 10, DiamondGear.BOOTS),
    WEREWOLF("WER", "Werewolf", "Lycanthropy", 1, 20.0D, 10, 10, DiamondGear.SWORD, DiamondGear.CHESTPLATE),
    ZOMBIE("ZOM", "Zombie", "Circle of Healing", 1, 20.0D, 12, 12, DiamondGear.CHESTPLATE);

    private static final Map<String, MegaWallsClass> BY_TAG = new HashMap<String, MegaWallsClass>();

    static {
        for (MegaWallsClass megaWallsClass : values()) {
            BY_TAG.put(megaWallsClass.tag, megaWallsClass);
        }
    }

    private final String tag;
    private final String displayName;
    private final String abilityName;
    private final Set<DiamondGear> kitDiamondGear;
    private final int potionCount;
    private final double potionHealth;
    private final int meleeEnergyGain;
    private final int bowEnergyGain;

    MegaWallsClass(
            String tag,
            String displayName,
            String abilityName,
            int potionCount,
            double potionHealth,
            int meleeEnergyGain,
            int bowEnergyGain,
            DiamondGear... kitDiamondGear
    ) {
        this.tag = tag;
        this.displayName = displayName;
        this.abilityName = abilityName;
        this.potionCount = potionCount;
        this.potionHealth = potionHealth;
        this.meleeEnergyGain = meleeEnergyGain;
        this.bowEnergyGain = bowEnergyGain;
        this.kitDiamondGear = createKitDiamondGear(kitDiamondGear);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbilityName() {
        return abilityName;
    }

    public String getAbilityName(int energy) {
        if (this == PHOENIX && energy == 100) {
            return "Bond";
        }

        return abilityName;
    }

    public boolean isAbilityReady(int energy) {
        if (this == PHOENIX) {
            return energy >= 25;
        }

        return energy >= 100;
    }

    public boolean isPrimaryAbilityReady(int energy) {
        return this == PHOENIX && energy >= 25;
    }

    public boolean isSecondaryAbilityReady(int energy) {
        return this == PHOENIX && energy == 100;
    }

    public int getPotionCount() {
        return potionCount;
    }

    public double getPotionHealth() {
        return potionHealth;
    }

    public int getMeleeEnergyGain() {
        return meleeEnergyGain;
    }

    public int getBowEnergyGain() {
        return bowEnergyGain;
    }

    public int getPreferredEnergyGain() {
        return Math.max(meleeEnergyGain, bowEnergyGain);
    }

    public String getPreferredEnergyMode() {
        return meleeEnergyGain >= bowEnergyGain ? "melee" : "bow";
    }

    public boolean hasKitDiamondGear(DiamondGear diamondGear) {
        return diamondGear != null && kitDiamondGear.contains(diamondGear);
    }

    public Set<DiamondGear> getKitDiamondGear() {
        return kitDiamondGear;
    }

    public static MegaWallsClass fromScoreboard(Scoreboard scoreboard, String playerName) {
        if (scoreboard == null || playerName == null || playerName.isEmpty()) {
            return null;
        }

        ScorePlayerTeam playerTeam = scoreboard.getPlayersTeam(playerName);
        if (playerTeam == null) {
            return null;
        }

        MegaWallsClass prefixClass = fromTeamTag(playerTeam.getColorPrefix());
        if (prefixClass != null) {
            return prefixClass;
        }

        return fromTeamTag(playerTeam.getColorSuffix());
    }

    public static MegaWallsClass fromTeamPrefix(String teamPrefix) {
        return fromTeamTag(teamPrefix);
    }

    public static MegaWallsClass fromTeamTag(String teamTag) {
        String normalizedTag = normalize(stripFormatting(teamTag));
        if (normalizedTag.isEmpty()) {
            return null;
        }

        MegaWallsClass exactMatch = BY_TAG.get(normalizedTag);
        if (exactMatch != null) {
            return exactMatch;
        }

        for (MegaWallsClass megaWallsClass : values()) {
            if (normalizedTag.contains(megaWallsClass.tag)) {
                return megaWallsClass;
            }
        }

        return null;
    }

    public static MegaWallsClass fromRenderedName(String renderedName) {
        if (renderedName == null || renderedName.isEmpty()) {
            return null;
        }

        String strippedName = stripFormatting(renderedName).toUpperCase(Locale.ROOT);
        for (MegaWallsClass megaWallsClass : values()) {
            if (strippedName.contains("[" + megaWallsClass.tag + "]")) {
                return megaWallsClass;
            }
        }

        return null;
    }

    public static String stripFormatting(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder strippedValue = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (currentChar == '\u00A7' && index + 1 < value.length()) {
                index++;
                continue;
            }

            strippedValue.append(currentChar);
        }

        return strippedValue.toString();
    }

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder normalizedValue = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (Character.isLetter(currentChar)) {
                normalizedValue.append(Character.toUpperCase(currentChar));
            }
        }

        return normalizedValue.toString();
    }

    private static Set<DiamondGear> createKitDiamondGear(DiamondGear[] diamondGear) {
        if (diamondGear == null || diamondGear.length == 0) {
            return Collections.emptySet();
        }

        EnumSet<DiamondGear> gearSet = EnumSet.noneOf(DiamondGear.class);
        Collections.addAll(gearSet, diamondGear);
        return Collections.unmodifiableSet(gearSet);
    }
}
