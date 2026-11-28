package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.BedDefense;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The Bed Break Simulator: its kits, where the attacker starts, the rounds and the ranking. */
class BedBreakGameTest {

    @Test
    void theAttackerChoosesOneOfTheOwnersThreeKits() {
        ShopCatalog catalog = ShopCatalog.standard();
        List<String> names = new ArrayList<>();
        for (RoundKit kit : RoundKit.BED_BREAK) {
            names.add(kit.name);
            assertEquals("Stone Sword", RoundKit.label(kit.entries(catalog).get(0)), kit.name + ": a stone sword first");
        }
        assertEquals(Arrays.asList("Classic", "Fireball", "Fighter"), names);
        assertEquals(Arrays.asList("Stone Sword", "Golden Pickaxe", "Iron Axe", "Permanent Shears", "Wool x64", "Wool x64"),
                labels(RoundKit.BED_BREAK.get(0)), "Classic: the tier III pickaxe and axe, shears, two stacks of blocks");
        assertEquals(Arrays.asList("Stone Sword", "Fireball", "Iron Pickaxe", "Stone Axe", "Permanent Shears", "Wool x64",
                "Wool x64"), labels(RoundKit.BED_BREAK.get(1)), "Fireball: a fireball, the tier II pickaxe and axe, shears, two stacks");
        assertEquals(Arrays.asList("Stone Sword", "Wooden Pickaxe", "Wooden Axe", "Permanent Shears", "Golden Apple", "Ladder x8",
                "Stick (Knockback I)"), labels(RoundKit.BED_BREAK.get(2)),
                "Fighter: the tier I pickaxe and axe, shears, a golden apple, ladders, the knockback stick");
        // the axe of each kit has the tier of its pickaxe
        int[] tiers = {2, 1, 0};
        for (int k = 0; k < tiers.length; k++) {
            RoundKit kit = RoundKit.BED_BREAK.get(k);
            ShopItem pickaxe = null;
            ShopItem axe = null;
            boolean shears = false;
            for (RoundKit.Entry entry : kit.entries(catalog)) {
                pickaxe = entry.item.kind() == ShopItem.Kind.PICKAXE ? entry.item : pickaxe;
                axe = entry.item.kind() == ShopItem.Kind.AXE ? entry.item : axe;
                shears |= entry.item.kind() == ShopItem.Kind.SHEARS;
            }
            assertEquals(catalog.pickaxes().get(tiers[k]), pickaxe, kit.name + ": the pickaxe of tier " + (tiers[k] + 1));
            assertEquals(catalog.axes().get(tiers[k]), axe, kit.name + ": the axe of the same tier");
            assertTrue(shears, kit.name + ": shears");
        }
    }

    /**
     * The attacker starts on the middle block of their bridge, standing on it, facing the next one:
     * along the first stretch, or the second after the turn.
     */
    @Test
    void theAttackerStartsInTheMiddleOfTheirBridgeFacingTheWayToTheBase() {
        List<int[]> north = new ArrayList<>();
        for (int z = 34; z >= -34; z--) {
            north.add(new int[] {-44, 93, z});
        }
        double[] start = BedBreakGame.attackerStart(north);
        assertEquals(-43.5, start[0], 1e-9);
        assertEquals(94, start[1], 1e-9, "standing on the middle block");
        assertEquals(0.5, start[2], 1e-9, "z 0: the middle of 34 to -34");
        assertEquals(180, Math.abs(start[3]), 1e-3, "facing the next block, to the north");

        List<int[]> turning = new ArrayList<>();
        for (int z = 10; z >= 0; z--) {
            turning.add(new int[] {0, 64, z});
        }
        for (int x = 1; x <= 20; x++) {
            turning.add(new int[] {x, 64, 0});
        }
        double[] afterTheTurn = BedBreakGame.attackerStart(turning);
        assertEquals(5.5, afterTheTurn[0], 1e-9, "the 16th of 31 blocks");
        assertEquals(-90, afterTheTurn[3], 1e-3, "facing the next block, to the east");

        assertEquals(-90, BedBreakGame.attackerStart(Arrays.asList(new int[] {0, 64, 0}, new int[] {1, 64, 0}))[3], 1e-3,
                "the last block: the way it came, to the east");
        assertNull(BedBreakGame.attackerStart(new ArrayList<>()), "no bridge: no place on it");
    }

    /**
     * On a map with its way up (the owner's stairway on Invasion and Archway, 21/11): the Top Bridge
     * platform at the top of the stairway, 17 wide, the half towards the defended bed in the
     * defenders' colour, the other in the attacker's, the middle row split in two around a white
     * block; the attacker stands on that block, facing the defenders' half.
     */
    @Test
    void theAttackerStartsInTheMiddleOfThePlatformAtTheTopFacingTheDefendersHalf() {
        // Invasion: the platform at y 114 (the top of the stairway), x -8 to 8, z -21 to 21; the red bed in the north-east
        BedwarsMap.BedBreakTop invasion = new BedwarsMap.BedBreakTop(
                Arrays.asList(new int[] {33, 93, -39}, new int[] {8, 114, -21}), 114, 8, 21, -8, -21);
        List<int[]> blocks = BedBreakGame.platform(invasion, 39.5, -38.5);
        assertEquals(17 * 43, blocks.size(), "17 wide, from z -21 to 21");
        int[] count = new int[3];
        for (int[] block : blocks) {
            assertEquals(114, block[1], "all at the height of the platform");
            count[block[3]]++;
            int expected = block[2] < 0 ? BedBreakGame.DEFENDERS : block[2] > 0 ? BedBreakGame.ATTACKERS
                    : block[0] == 0 ? BedBreakGame.WHITE : block[0] > 0 ? BedBreakGame.DEFENDERS : BedBreakGame.ATTACKERS;
            assertEquals(expected, block[3], block[0] + " " + block[2]);
        }
        assertEquals(1, count[BedBreakGame.WHITE], "one white block, in the middle");
        assertEquals(count[BedBreakGame.DEFENDERS], count[BedBreakGame.ATTACKERS], "two halves alike");
        double[] start = BedBreakGame.platformStart(invasion, 39.5, -38.5);
        assertEquals(0.5, start[0], 1e-9);
        assertEquals(115, start[1], 1e-9, "standing on the platform");
        assertEquals(0.5, start[2], 1e-9, "on the white block");
        assertEquals(180, Math.abs(start[3]), 1e-3, "facing the north: the red half and the stairway");

        // Archway: y 86 (the limit is 87), z -22 to 22; the red bed in the north
        BedwarsMap.BedBreakTop archway = new BedwarsMap.BedBreakTop(Arrays.asList(new int[] {-4, 69, -65}), 86, -8, -22, 8, 22);
        double[] onArchway = BedBreakGame.platformStart(archway, -14.5, -66.5);
        assertEquals(87, onArchway[1], 1e-9);
        assertEquals(180, Math.abs(onArchway[3]), 1e-3, "facing the red half");
        assertEquals(BedBreakGame.DEFENDERS, BedBreakGame.platform(archway, -14.5, -66.5).get(0)[3], "the north-west corner: red's");

        // along x, the defended bed in the east: facing the east, the east half the defenders'
        BedwarsMap.BedBreakTop eastWest = new BedwarsMap.BedBreakTop(Collections.<int[]>emptyList(), 100, -20, -3, 20, 3);
        double[] facingEast = BedBreakGame.platformStart(eastWest, 60.5, 0.5);
        assertEquals(-90, facingEast[3], 1e-3, "facing the east");
        for (int[] block : BedBreakGame.platform(eastWest, 60.5, 0.5)) {
            if (block[0] != 0) {
                assertEquals(block[0] > 0 ? BedBreakGame.DEFENDERS : BedBreakGame.ATTACKERS, block[3], block[0] + " " + block[2]);
            }
        }
    }

    @Test
    void eachPlayerAttacksInTurn() {
        assertEquals(2, BedBreakGame.laps(2), "a 1v1: two attacks each");
        assertEquals(2, BedBreakGame.laps(3));
        assertEquals(1, BedBreakGame.laps(4));
        assertEquals(1, BedBreakGame.laps(BedBreakGame.MAX_PLAYERS), "1 attacker against 4 defenders, once each");
        assertEquals(1, BedBreakGame.kitIndex(1, 3));
        assertEquals(0, BedBreakGame.kitIndex(5, 3), "a kit no longer in the config: the first");
        assertEquals(0, BedBreakGame.kitIndex(-1, 3));
        assertSame(BedDefense.RANKED, BedBreakGame.DEFENSE, "the Ranked defense on the bed (end stone, glass), not the duo one");
        assertEquals(2, BedBreakGame.ARMOUR_TIER, "iron armour");
        assertTrue(BedBreakGame.READY_COUNTDOWN < BedBreakGame.ROUND_COUNTDOWN);
    }

    @Test
    void theMostPointsWinThenTheQuickestBed() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        Map<UUID, Integer> points = new HashMap<>();
        Map<UUID, Integer> quickest = new HashMap<>();
        points.put(a, 1);
        points.put(b, 2);
        points.put(c, 1);
        quickest.put(a, 50);
        quickest.put(c, 31);
        assertEquals(Arrays.asList(b, c, a), BedBreakGame.ranking(Arrays.asList(a, b, c), points, quickest),
                "two points first; then of one point each, the quicker bed");
        quickest.remove(c);
        assertEquals(Arrays.asList(b, a, c), BedBreakGame.ranking(Arrays.asList(c, b, a), points, quickest),
                "a bed broken before a point of kills only");
    }

    private static List<String> labels(RoundKit kit) {
        List<String> labels = new ArrayList<>();
        for (RoundKit.Entry entry : kit.entries(ShopCatalog.standard())) {
            labels.add(RoundKit.label(entry));
        }
        return labels;
    }
}
