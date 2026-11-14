package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.game.BedDefense;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertEquals(Arrays.asList("Stone Sword", "Golden Pickaxe", "Wool x64", "Wool x64"), labels(RoundKit.BED_BREAK.get(0)),
                "Classic: the tier III pickaxe and two stacks of blocks");
        assertEquals(Arrays.asList("Stone Sword", "Fireball", "Iron Pickaxe", "Wool x64", "Wool x64"),
                labels(RoundKit.BED_BREAK.get(1)), "Fireball: a fireball, the tier II pickaxe and two stacks");
        assertEquals(Arrays.asList("Stone Sword", "Wooden Pickaxe", "Golden Apple", "Ladder x8", "Stick (Knockback I)"),
                labels(RoundKit.BED_BREAK.get(2)), "Fighter: the tier I pickaxe, a golden apple, ladders, the knockback stick");
        assertEquals(catalog.pickaxes().get(2), RoundKit.BED_BREAK.get(0).entries(catalog).get(1).item, "tier III");
        assertEquals(catalog.pickaxes().get(1), RoundKit.BED_BREAK.get(1).entries(catalog).get(2).item, "tier II");
        assertEquals(catalog.pickaxes().get(0), RoundKit.BED_BREAK.get(2).entries(catalog).get(1).item, "tier I");
    }

    @Test
    void theAttackerStartsInTheMiddleOfTheTopBridgeFacingTheBase() {
        MapPoint defended = new MapPoint(0, 83, 79, 0f, 0f);
        MapPoint other = new MapPoint(0, 83, -79, 0f, 0f);
        TopBridge bridge = TopBridge.plan(TeamColor.RED, defended, TeamColor.BLUE, other,
                (x, y, z) -> Math.abs(z) >= 74 && Math.abs(z) <= 99 && Math.abs(x) <= 11 && y >= 76 && y <= 82);
        assertNotNull(bridge);
        double[] start = bridge.centre(true);
        assertEquals(0.5, start[0], 1e-9);
        assertEquals(100, start[1], 1e-9, "on the platform");
        assertEquals(0.5, start[2], 1e-9, "on its white middle line");
        assertEquals(0, start[3], 1e-3, "facing the red base, to the south");
        assertEquals(180, Math.abs(bridge.centre(false)[3]), 1e-3, "or the other one");
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
        assertSame(BedDefense.DUO, BedBreakGame.DEFENSE, "the owner's duo defense on the bed");
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
