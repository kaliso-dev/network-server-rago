package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * The team upgrades and traps of a team, bought with diamonds at the upgrades NPC, as on Hypixel:
 * Sharpened Swords (Sharpness I on the swords and axes), Reinforced Armor (Protection I to IV),
 * Maniac Miner (Haste I and II), the Forge (iron and gold 50% faster, twice as fast, emeralds,
 * three times as fast), the Heal Pool (Regeneration around the base), and up to three traps
 * waiting their turn, each set off by the next enemy in the base. Prices as on Hypixel: those of
 * solo and doubles for teams of one or two players, those of 3v3v3v3 and 4v4v4v4 above. The base
 * is the island around the spawn, out to the bed, the forge and the NPCs. Main thread only.
 */
final class Upgrades {

    /** The upgrades, in the order of the menu. */
    enum Kind {
        SHARPENED_SWORDS("Sharpened Swords", Material.IRON_SWORD, new int[] {4}, new int[] {8},
                "Your team permanently gains", "Sharpness I on all swords and", "axes!"),
        REINFORCED_ARMOR("Reinforced Armor", Material.IRON_CHESTPLATE, new int[] {2, 4, 8, 16}, new int[] {5, 10, 20, 30},
                "Your team permanently gains", "Protection on all armor pieces!"),
        MANIAC_MINER("Maniac Miner", Material.GOLD_PICKAXE, new int[] {2, 4}, new int[] {4, 6},
                "All players on your team", "permanently gain Haste."),
        FORGE("Forge", Material.FURNACE, new int[] {2, 4, 6, 8}, new int[] {4, 8, 12, 16},
                "Upgrade resource spawning on", "your island."),
        HEAL_POOL("Heal Pool", Material.BEACON, new int[] {1}, new int[] {3},
                "Creates a Regeneration field", "around your base!");

        final String displayName;
        final Material icon;
        private final int[] small;
        private final int[] big;
        /** What it does, line by line, as the menu says it. */
        final String[] description;

        Kind(String displayName, Material icon, int[] small, int[] big, String... description) {
            this.displayName = displayName;
            this.icon = icon;
            this.small = small;
            this.big = big;
            this.description = description;
        }

        int tiers() {
            return small.length;
        }

        /** The price in diamonds of this tier (from 1), for teams of this many players. */
        int price(int tier, int teamSize) {
            int[] prices = teamSize >= 3 ? big : small;
            return prices[Math.max(0, Math.min(tier, prices.length) - 1)];
        }

        /** The name of a tier: "Reinforced Armor II", "Golden Forge". */
        String tierName(int tier) {
            if (this == FORGE) {
                return FORGES[Math.max(0, Math.min(tier, FORGES.length) - 1)];
            }
            return tiers() == 1 ? displayName : displayName + " " + Generator.roman(tier);
        }

        /** What a tier gives: "Protection II", "+100% Resources". */
        String tierEffect(int tier) {
            switch (this) {
                case REINFORCED_ARMOR:
                    return "Protection " + Generator.roman(tier);
                case MANIAC_MINER:
                    return "Haste " + Generator.roman(tier);
                case FORGE:
                    return FORGE_EFFECTS[Math.max(0, Math.min(tier, FORGE_EFFECTS.length) - 1)];
                default:
                    return displayName;
            }
        }
    }

    private static final String[] FORGES = {"Iron Forge", "Golden Forge", "Emerald Forge", "Molten Forge"};
    private static final String[] FORGE_EFFECTS = {"+50% Resources", "+100% Resources", "Spawn emeralds", "+200% Resources"};
    /** How much faster the forge goes at each tier, from none. */
    private static final double[] FORGE_SPEED = {1, 1.5, 2, 2, 3};

    /** The traps, set off one after the other by the enemies who enter the base. */
    enum Trap {
        ITS_A_TRAP("It's a Trap!", Material.TRIPWIRE_HOOK, "Inflicts Blindness and Slowness", "for 8 seconds."),
        COUNTER_OFFENSIVE("Counter-Offensive Trap", Material.FEATHER, "Grants Speed II and Jump Boost II",
                "for 15 seconds to allied players", "near your base."),
        ALARM("Alarm Trap", Material.REDSTONE_TORCH_ON, "Reveals invisible players as", "well as their name and team."),
        MINER_FATIGUE("Miner Fatigue Trap", Material.IRON_PICKAXE, "Inflict Mining Fatigue for 10", "seconds.");

        final String displayName;
        final Material icon;
        final String[] description;

        Trap(String displayName, Material icon, String... description) {
            this.displayName = displayName;
            this.icon = icon;
            this.description = description;
        }
    }

    /** At most this many traps wait their turn. */
    static final int MAX_TRAPS = 3;
    /** After a trap went off, the next one waits this long: the base has a short rest. */
    static final long TRAP_COOLDOWN_MS = 15_000L;
    /** The Emerald Forge: an emerald every minute on the island. */
    static final int EMERALD_FORGE_TICKS = 60 * 20;
    /** The base reaches this far around the spawn past its farthest point (bed, forge, NPCs), within these bounds. */
    static final double BASE_MARGIN = 6;
    static final double MIN_BASE = 12;
    static final double MAX_BASE = 22;
    /** And from this far under the spawn to this far above it. */
    static final double BASE_BELOW = 8;
    static final double BASE_ABOVE = 18;

    /** The price of the next trap, with this many waiting: 1, 2, then 4 diamonds. */
    static int trapPrice(int queued) {
        return queued <= 0 ? 1 : queued == 1 ? 2 : 4;
    }

    /** How much faster the island forge goes at this tier of the Forge. */
    static double forgeSpeed(int tier) {
        return FORGE_SPEED[Math.max(0, Math.min(tier, FORGE_SPEED.length - 1))];
    }

    /** This rate, so many times faster: as many items at most on the ground. */
    static BedwarsSettings.Rate faster(BedwarsSettings.Rate rate, double speed) {
        return new BedwarsSettings.Rate(Math.max(1, (int) Math.round(rate.ticks / speed)), rate.max, rate.batches);
    }

    /** How far the base of an island goes around its spawn. */
    static double baseRadius(BedwarsMap.Team points) {
        if (points == null || points.spawn == null) {
            return MIN_BASE;
        }
        double farthest = 0;
        for (MapPoint point : new MapPoint[] {points.bed, points.generator, points.shop, points.upgrades}) {
            if (point != null) {
                double dx = point.x() - points.spawn.x();
                double dz = point.z() - points.spawn.z();
                farthest = Math.max(farthest, Math.sqrt(dx * dx + dz * dz));
            }
        }
        return Math.max(MIN_BASE, Math.min(MAX_BASE, farthest + BASE_MARGIN));
    }

    /** True if a place this far from the spawn (dy: how much higher) is in a base of this radius. */
    static boolean inBase(double dx, double dy, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius && dy >= -BASE_BELOW && dy <= BASE_ABOVE;
    }

    private final int[] tiers = new int[Kind.values().length];
    /** The traps waiting their turn, the next one first. */
    final List<Trap> traps = new ArrayList<>();
    /** When the next trap can go off (a trap set off gives the base a short rest). */
    long trapReadyAt;
    /** The island forge, where it drops, and its speed before any upgrade; its emeralds from the Emerald Forge. */
    Generator iron;
    Generator gold;
    Generator emerald;
    Location forge;
    BedwarsSettings.Rate ironRate;
    BedwarsSettings.Rate goldRate;

    /** The tier bought of this upgrade, 0 for none. */
    int tier(Kind kind) {
        return tiers[kind.ordinal()];
    }

    void setTier(Kind kind, int tier) {
        tiers[kind.ordinal()] = tier;
    }

    boolean sharpness() {
        return tier(Kind.SHARPENED_SWORDS) > 0;
    }

    int protection() {
        return tier(Kind.REINFORCED_ARMOR);
    }

    int haste() {
        return tier(Kind.MANIAC_MINER);
    }

    boolean healPool() {
        return tier(Kind.HEAL_POOL) > 0;
    }
}
