package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** The quests NPC beside the daily chest. */
class QuestsNpcTest {

    private static final UUID ALICE = new UUID(0, 1);

    /** An open floor: every spot is free but the taken ones. */
    private static QuestsNpc.Ground floorBut(final int[]... taken) {
        final Set<String> blocked = new HashSet<>();
        for (int[] xz : taken) {
            blocked.add(xz[0] + "," + xz[1]);
        }
        return (x, y, z) -> !blocked.contains(x + "," + z);
    }

    @Test
    void itStandsTwoBlocksBesideTheChestAwayFromTheOtherFigures() {
        // The lobby of the network: the chest at (-2, 65, 11), the spawn far north, the Shop NPC east of the chest.
        List<double[]> shop = Collections.singletonList(new double[] {3.72, 11.42});
        assertArrayEquals(new int[] {-4, 11}, QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, shop, floorBut()),
                "west, away from the Shop NPC");
        // With the Shop NPC on the west side, it goes east.
        List<double[]> west = Collections.singletonList(new double[] {-7.5, 11.5});
        assertArrayEquals(new int[] {0, 11}, QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, west, floorBut()));
        // Nobody around: the right of someone at the spawn looking at the chest (looking south: the west).
        assertArrayEquals(new int[] {-4, 11}, QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, Collections.<double[]>emptyList(),
                floorBut()));
        // The spawn east of the chest: someone there looks west, their right is the north.
        assertArrayEquals(new int[] {0, -2}, QuestsNpc.beside(0, 65, 0, 30, 0, Collections.<double[]>emptyList(), floorBut()));
    }

    @Test
    void aTakenSpotSendsItToTheOtherSideThenFarther() {
        List<double[]> none = Collections.emptyList();
        assertArrayEquals(new int[] {0, 11}, QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, none, floorBut(new int[] {-4, 11})));
        assertArrayEquals(new int[] {-5, 11}, QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, none,
                floorBut(new int[] {-4, 11}, new int[] {0, 11})));
        assertNull(QuestsNpc.beside(-2, 65, 11, 0.1, -27.5, none, (x, y, z) -> false), "nowhere to stand: no NPC");
    }

    @Test
    void itStandsOnTheFloorInTheAir() {
        assertTrue(QuestsNpc.standable(Material.AIR, Material.AIR, Material.STONE));
        assertTrue(QuestsNpc.standable(Material.CARPET, Material.AIR, Material.STONE), "on a carpet");
        assertTrue(QuestsNpc.standable(Material.WOOD_PLATE, Material.AIR, Material.WOOD));
        assertFalse(QuestsNpc.standable(Material.AIR, Material.AIR, Material.AIR), "not floating");
        assertFalse(QuestsNpc.standable(Material.STONE, Material.AIR, Material.STONE), "not in a wall");
        assertFalse(QuestsNpc.standable(Material.AIR, Material.GLASS, Material.STONE), "not under a roof");
        assertFalse(QuestsNpc.standable(Material.RED_ROSE, Material.AIR, Material.GRASS), "not in a flower");
        assertFalse(QuestsNpc.standable(Material.AIR, Material.AIR, Material.WATER));
    }

    @Test
    void itsLinesCountTheQuestsDoneOfEachPlayer() {
        Quests quests = Quests.defaults();
        long now = ZonedDateTime.parse("2026-11-11T12:00:00Z").toInstant().toEpochMilli();
        String day = StatsPeriod.dayKey(now);
        String week = StatsPeriod.weekKey(now);
        List<String> none = QuestsNpc.lines(quests, null, now);
        assertEquals(QuestsNpc.LINES, none.size());
        assertEquals("QUESTS", ChatColor.stripColor(none.get(0)));
        assertEquals("Daily 0/4 · Weekly 0/4", ChatColor.stripColor(none.get(1)), "a player without stats yet");
        assertEquals("Click to see them", ChatColor.stripColor(none.get(2)));

        PlayerStats alice = new PlayerStats(ALICE, "Alice");
        quests.advance(alice, Quests.Goal.WINS, "sumo", 3, day, week);
        assertEquals(1, quests.done(alice, Quests.Period.DAILY, day, week), "three wins: the daily one");
        assertEquals(0, quests.done(alice, Quests.Period.WEEKLY, day, week));
        assertEquals("Daily 1/4 · Weekly 0/4", ChatColor.stripColor(QuestsNpc.lines(quests, alice, now).get(1)));
        long tomorrow = now + 24 * 3600_000L;
        assertEquals("Daily 0/4 · Weekly 0/4", ChatColor.stripColor(QuestsNpc.lines(quests, alice, tomorrow).get(1)),
                "a new day");

        Quests daily = new Quests(Arrays.asList(new Quests.Quest(Quests.Period.DAILY, "jouer", "Play a game",
                Quests.Goal.GAMES, 1, 100, null)));
        daily.advance(alice, Quests.Goal.GAMES, "sumo", 1, StatsPeriod.dayKey(tomorrow), StatsPeriod.weekKey(tomorrow));
        String line = QuestsNpc.lines(daily, alice, tomorrow).get(1);
        assertEquals("Daily 1/1", ChatColor.stripColor(line), "a period without quests is left out");
        assertTrue(line.contains(ChatColor.GREEN + "1/1"), "all done: green");
    }

    @Test
    void itsBodyHasANameOfItsOwnHiddenLikeTheOthers() {
        assertTrue(LobbyNpcs.questsName().startsWith(ChatColor.DARK_GRAY.toString()));
        assertFalse(LobbyNpcs.questsName().equals(LobbyNpcs.bodyName(0)) || LobbyNpcs.questsName().equals(LobbyNpcs.podiumName(0)));
        assertTrue(LobbyNpcs.questsName().length() <= 16);
        assertTrue(QuestsNpc.class.getResource("/skins/" + QuestsNpc.SKIN + ".png") != null, "its skin is in the jar");
    }
}
