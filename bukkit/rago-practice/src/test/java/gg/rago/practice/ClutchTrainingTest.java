package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The block clutch: its knockback settings, its top fight platform, its islands side by side. */
class ClutchTrainingTest {

    @Test
    void theBundledIslandIsTheOwnersClutchIsland() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        ConfigurationSection section = config.getConfigurationSection("clutch");
        assertNotNull(section);
        assertEquals("BlockClutchNew", section.getString("monde"));
        assertNull(RagoPractice.checkClutch(section));
        // the player stands on the island, one block above its centre, facing north (towards its bed)
        List<Integer> centre = section.getIntegerList("centre");
        List<Double> player = section.getDoubleList("joueur");
        assertEquals(1, player.get(1) - centre.get(1), 1e-9);
        assertEquals(180, player.get(3), 1e-9);
        section.set("joueur", Arrays.asList(870607.5, 92, 870620.5, 180));
        assertTrue(RagoPractice.checkClutch(section).startsWith("joueur"));
        section.set("zone", Arrays.asList(870560, 80, 870601, 870616, 102, 870614));
        assertTrue(RagoPractice.checkClutch(section).startsWith("zone"), "wider than the room between two islands");
    }

    @Test
    void theHitThrowsBackwardsFromWhereThePlayerLooks() {
        // facing north (yaw 180): thrown south, as on the plugin's own island and the owner's
        assertArrayEquals(new double[] {0, 1}, ClutchTraining.thrownWay(180, 0), 1e-9);
        // facing south (yaw 0): thrown north
        assertArrayEquals(new double[] {0, -1}, ClutchTraining.thrownWay(0, 0), 1e-9);
        // facing west (yaw 90): thrown east
        assertArrayEquals(new double[] {1, 0}, ClutchTraining.thrownWay(90, 0), 1e-9);
        assertEquals(180, ClutchTraining.plainIsland().player[3], 1e-9);
    }

    @Test
    void aClutchIsAsBigAsTheHitThrewThePlayerInTenthsOfABlock() {
        assertEquals(43, ClutchTraining.tenths(3.5, 1.5, 3.5, 5.8));
        assertEquals(50, ClutchTraining.tenths(0, 0, 3, -4));
        assertEquals(0, ClutchTraining.tenths(1, 1, 1, 1));
        assertEquals("4.3 blocks", ClutchTraining.blocks(43));
        assertEquals("1.0 block", ClutchTraining.blocks(10));
        assertEquals("0.7 blocks", ClutchTraining.blocks(7));
        assertEquals("12.0 blocks", ClutchTraining.blocks(120));
    }

    @Test
    void theBoardOfTheBiggestClutchesShowsTheFirstTenTheViewerInGreen() {
        java.util.UUID me = java.util.UUID.randomUUID();
        List<String> empty = ClutchTraining.sizeLines(java.util.Collections.emptyList(), me, false);
        assertEquals(3, empty.size());
        assertTrue(empty.get(0).contains("BIGGEST CLUTCHES"));
        assertTrue(empty.get(2).contains("Nobody yet"));
        RecordBook book = new RecordBook();
        book.define("clutch.island-blocks", false);
        for (int i = 0; i < 12; i++) {
            book.submit("clutch.island-blocks", i == 4 ? me : java.util.UUID.randomUUID(), "P" + i, 10 + i);
        }
        List<String> lines = ClutchTraining.sizeLines(book.ranked("clutch.island-blocks"), me, true);
        assertEquals(2 + KillBoard.ROWS, lines.size());
        assertTrue(lines.get(1).contains("Top fight"));
        assertTrue(lines.get(2).contains("P11") && lines.get(2).contains("2.1 blocks"), lines.get(2));
        String mine = lines.get(2 + 7);
        assertTrue(mine.contains(org.bukkit.ChatColor.GREEN + "P4"), mine);
        // the islands' boards stand in front of the player, over the island, under the build limit
        assertTrue(ClutchTraining.BOARD_AHEAD < ClutchTraining.SPACING / 2.0);
    }

    @Test
    void moreKnockbackThrowsFartherAndTheStickFarthest() {
        for (int kb = 1; kb < 5; kb++) {
            assertTrue(ClutchTraining.throwOf(kb + 1, false)[0] > ClutchTraining.throwOf(kb, false)[0]);
        }
        assertEquals(0.42, ClutchTraining.throwOf(3, false)[0], 1e-9, "3: a hit of a fight");
        assertTrue(ClutchTraining.throwOf(1, true)[0] > ClutchTraining.throwOf(5, false)[0], "the stick beats any hit");
        assertTrue(ClutchTraining.throwOf(3, true)[1] > ClutchTraining.throwOf(3, false)[1]);
    }

    @Test
    void theTopFightPlatformTapersAtBothEnds() {
        int blocks = 0;
        for (int x = -12; x <= 12; x++) {
            assertEquals(ClutchTraining.halfWidth(x), ClutchTraining.halfWidth(-x), "the same at both ends");
            if (x > 0) {
                assertTrue(ClutchTraining.halfWidth(x) <= ClutchTraining.halfWidth(x - 1), "narrower towards the tips");
            }
            blocks += Math.max(0, 2 * ClutchTraining.halfWidth(x) + 1);
        }
        assertEquals(7, 2 * ClutchTraining.halfWidth(0) + 1, "seven wide in the middle");
        assertEquals(0, ClutchTraining.halfWidth(10), "one block at the tips");
        assertEquals(-1, ClutchTraining.halfWidth(11));
        assertEquals(11 * 7 + 4 * 5 + 4 * 3 + 2, blocks);
    }

    @Test
    void eachPlayerBuildsOnlyAroundTheirOwnZone() {
        assertTrue(ClutchTraining.PLACE_ALONG >= 10, "the whole platform");
        assertTrue(2 * ClutchTraining.PLACE_ALONG < ClutchTraining.SPACING, "never on the next zone");
        assertTrue(ClutchTraining.SPACING <= 32, "close enough to see the others");
        assertTrue(ClutchTraining.SPACING >= gg.rago.game.ArenaGrid.MIN_SPACING, "a grid of zones takes it");
    }
}
