package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnerRulesTest {

    private static final UUID QRAGO = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OTHER = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

    @Test
    void theOwnerIsTheirAccountWhereTheServerVerifiesTheNames() {
        assertTrue(OwnerRules.isOwner("qRago", null, "qRago", QRAGO, true));
        assertTrue(OwnerRules.isOwner("qRago", null, "qrago", QRAGO, true), "a Minecraft name has one owner, whatever its case");
        assertFalse(OwnerRules.isOwner("qRago", null, "Notch", OTHER, true));
        assertFalse(OwnerRules.isOwner("qRago", null, null, OTHER, true));
        assertFalse(OwnerRules.isOwner("", null, "qRago", QRAGO, true), "no account: no tool for anyone");
        assertFalse(OwnerRules.isOwner(null, null, "qRago", QRAGO, true));
    }

    @Test
    void whereNamesAreNotVerifiedTheIdDecides() {
        assertFalse(OwnerRules.isOwner("qRago", null, "qRago", QRAGO, false), "anyone can take a name in offline mode");
        assertTrue(OwnerRules.isOwner("qRago", QRAGO, "qRago", QRAGO, false));
        assertFalse(OwnerRules.isOwner("qRago", QRAGO, "qRago", OTHER, false), "the name without its id");
        assertFalse(OwnerRules.isOwner("qRago", QRAGO, "qRago", OTHER, true), "a configured id holds in online mode too");
        assertFalse(OwnerRules.isOwner("qRago", QRAGO, "Notch", QRAGO, false), "the id without its name");
    }

    @Test
    void theReachIsFromAFairOneToWhatTheServerTakes() {
        assertEquals(4.5, OwnerRules.reach("4.5"), 0);
        assertEquals(4.5, OwnerRules.reach("4,5"), 0);
        assertEquals(5.0, OwnerRules.reach(" 5 "), 0);
        assertEquals(3.0, OwnerRules.reach("3"), 0);
        assertEquals(6.0, OwnerRules.reach("6"), 0);
        for (String wrong : Arrays.asList("2.9", "6.1", "0", "-4", "four", "", "NaN", "Infinity")) {
            assertTrue(Double.isNaN(OwnerRules.reach(wrong)), wrong);
        }
        assertEquals("4", OwnerRules.blocks(4.0));
        assertEquals("4.5", OwnerRules.blocks(4.5));
        assertEquals("3", OwnerRules.blocks(OwnerRules.FAIR_REACH));
        assertEquals("6", OwnerRules.blocks(OwnerRules.MAX_REACH));
    }

    @Test
    void aHitLandsWhereTheAimEntersThePlayersBox() {
        // eyes at 1.62 above feet at the origin, looking along +x
        assertEquals(4.1, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, 4.5, 0, 0), 1e-9, "the box is 0.4 from the middle of the player");
        assertEquals(-1, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, 4.5, 0, 2), 0, "beside the aim");
        assertEquals(-1, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, -4.5, 0, 0), 0, "behind");
        assertEquals(-1, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, 4.5, 2, 0), 0, "over the aim: the box is 1.9 high, from 0.1 under the feet");
        assertEquals(4.1, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, 4.5, -0.25, 0), 1e-9, "lower, the head in the aim");
        assertEquals(0, OwnerRules.hit(0, 1.62, 0, 1, 0, 0, 0.2, 0, 0), 0, "from inside the box");
        double way = Math.sqrt(0.5);
        assertEquals(Math.sqrt(2) * 3.6, OwnerRules.hit(0, 1, 0, way, 0, way, 4, 0, 4), 1e-9, "on a diagonal");
        assertEquals(2.5, OwnerRules.block(0.5, 0.5, 0.5, 1, 0, 0, 3, 0, 0), 1e-9);
        assertEquals(-1, OwnerRules.block(0.5, 0.5, 0.5, 1, 0, 0, 3, 1, 0), 0);
    }

    /** Solid blocks at these places, "x y z". */
    private static OwnerRules.Solid solid(String... places) {
        final Set<String> at = new HashSet<>(Arrays.asList(places));
        return (x, y, z) -> at.contains(x + " " + y + " " + z);
    }

    @Test
    void theScaffoldPutsTheBlockUnderTheFeetAgainstOneBesideIt() {
        // walking off the east edge of a floor at y 64: feet at y 65, over the empty block 1 64 0
        assertArrayEquals(new int[] {1, 64, 0, 0, 64, 0}, OwnerRules.scaffold(1.2, 65, 0.5, solid("0 64 0")));
        assertArrayEquals(new int[] {1, 64, 0, 2, 64, 0}, OwnerRules.scaffold(1.2, 65, 0.5, solid("2 64 0")));
        assertArrayEquals(new int[] {1, 64, 0, 1, 64, 1}, OwnerRules.scaffold(1.2, 65, 0.5, solid("1 64 1")));
        assertArrayEquals(new int[] {1, 64, 0, 1, 64, -1}, OwnerRules.scaffold(1.2, 65, 0.5, solid("1 64 -1")));
        assertArrayEquals(new int[] {1, 64, 0, 1, 63, 0}, OwnerRules.scaffold(1.2, 65, 0.5, solid("1 63 0")), "on the block under it");
        assertArrayEquals(new int[] {-1, 64, -1, 0, 64, -1}, OwnerRules.scaffold(-0.5, 65, -0.5, solid("0 64 -1")), "negative places");
    }

    @Test
    void theScaffoldPutsNothingWhereABlockIsOrNothingHoldsIt() {
        assertNull(OwnerRules.scaffold(0.5, 65, 0.5, solid("0 64 0")), "a floor already under the feet");
        assertNull(OwnerRules.scaffold(1.2, 65, 0.5, solid()), "in the air, against nothing");
        assertNull(OwnerRules.scaffold(1.2, 65, 0.5, solid("0 65 0", "1 66 0")), "only blocks beside the feet or over the head");
        // falling at y 64.9: the block wholly under the feet is 63, not 64
        assertArrayEquals(new int[] {1, 63, 0, 0, 63, 0}, OwnerRules.scaffold(1.2, 64.9, 0.5, solid("0 63 0")));
    }
}
