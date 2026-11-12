package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplayDataTest {

    private static final UUID ANN = new UUID(1, 1);
    private static final UUID BOB = new UUID(2, 2);

    private static ReplayData.Info info(int ticks) {
        ReplayData.Info info = new ReplayData.Info();
        info.id = "ABCD2345";
        info.modeId = "bridge-duel";
        info.modeName = "Bridge Duel";
        info.mapName = "Wooden";
        info.startedAt = 1_700_000_000_000L;
        info.ticks = ticks;
        info.result = "Ann won (3 - 1)";
        info.stageKind = "blocks";
        info.actors.add(new ReplayData.Actor(ANN, "Ann", 'c', "skin", "signed"));
        info.actors.add(new ReplayData.Actor(BOB, "Bob", '9', null, null));
        return info;
    }

    private static byte[] file(ReplayData.Info info, int[] stage, byte[] body) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ReplayData.write(out, info, stage, body);
        return out.toByteArray();
    }

    /** Ann starts at (1.5, 1, -2.25) and walks; Bob comes in at tick 2; a block, a hit, an arrow, a line of chat. */
    private static ReplayData.BodyWriter game() {
        ReplayData.BodyWriter body = new ReplayData.BodyWriter();
        // tick 0
        body.position(0, 48, 32, -72, 64, 0);
        body.flags(0, 0);
        body.flags(1, ReplayData.HIDDEN);
        body.equip(0, ReplayData.HAND, new ReplayData.Item(267, 0, true, -1));
        body.block(3, 1, 0, 35, 14);
        body.tick();
        // tick 1
        body.move(0, 10, 0, -5, 70, 2);
        body.swing(0);
        body.tick();
        // tick 2
        body.position(1, -320, 32, 0, -128, 0);
        body.flags(1, ReplayData.SNEAK);
        body.health(1, 17);
        body.hurt(1);
        body.thingAdded(1, ReplayData.ARROW, 0, 64, 0, 0, 0, 0);
        body.chat("§cAnn §7hit Bob");
        body.tick();
        // tick 3
        body.thingMoved(1, 64, 64, -32, 0, 0);
        body.equip(0, ReplayData.HAND, ReplayData.Item.NONE);
        body.tick();
        // tick 4
        body.thingGone(1);
        body.explosion(32, 64, 32);
        body.tick();
        return body;
    }

    @Test
    void aGameComesBackTickByTickFromItsFile() throws IOException {
        ReplayData.BodyWriter body = game();
        assertEquals(5, body.ticks());
        int[] stage = {0, 0, 0, 1, 0, 1, 0, 0, 0, 0, -2, 3, 4, 159, 14};
        ReplayData data = ReplayData.read(new ByteArrayInputStream(file(info(5), stage, body.finish())));

        assertEquals("ABCD2345", data.info.id);
        assertEquals("Bridge Duel", data.info.modeName);
        assertEquals("Ann won (3 - 1)", data.info.result);
        assertEquals(2, data.actors());
        assertEquals('9', data.info.actors.get(1).colour);
        assertEquals("", data.info.actors.get(1).skinValue, "a player without a skin");
        assertArrayEquals(new int[] {0, 0, 0, 1, 0, -2, 3, 4, 159, 14}, data.stage, "the air of the stage is left out");

        // Ann's position, moved at tick 1, the same after
        assertEquals(48, data.x[0][0]);
        assertEquals(-72, data.z[0][0]);
        assertEquals(58, data.x[0][1]);
        assertEquals(-77, data.z[0][1]);
        assertEquals(58, data.x[0][4]);
        assertEquals(70, data.yaw[0][1]);
        // Bob is out of the game until tick 2
        assertTrue((data.flags[1][0] & ReplayData.HIDDEN) != 0);
        assertTrue((data.flags[1][1] & ReplayData.HIDDEN) != 0);
        assertEquals(ReplayData.SNEAK, data.flags[1][2]);
        assertEquals(-320, data.x[1][2]);
        assertEquals(20, data.health[1][1], "full health before any word of it");
        assertEquals(17, data.health[1][3]);

        // the sword in hand from tick 0 to 2, nothing from tick 3
        assertEquals(267, data.item(0, ReplayData.HAND, 0).id);
        assertTrue(data.item(0, ReplayData.HAND, 2).glow);
        assertEquals(ReplayData.Item.NONE, data.item(0, ReplayData.HAND, 3));
        assertEquals(ReplayData.Item.NONE, data.item(1, ReplayData.HAND, 4), "Bob held nothing");

        // the events of each tick
        assertEquals(ReplayData.BLOCK, data.events(0).get(0).type);
        assertEquals(35, data.events(0).get(0).d);
        assertEquals(14, data.events(0).get(0).e);
        assertEquals(ReplayData.SWING, data.events(1).get(0).type);
        List<ReplayData.Event> third = data.events(2);
        assertEquals(3, third.size());
        assertEquals(ReplayData.HURT, third.get(0).type);
        assertEquals(1, third.get(0).a);
        assertEquals("§cAnn §7hit Bob", third.get(2).text);
        assertEquals(ReplayData.EXPLODE, data.events(4).get(1).type);
        assertTrue(data.events(5).isEmpty());
        assertTrue(data.events(-1).isEmpty());

        Map<Integer, Integer> counts = ReplayData.countByType(data);
        assertEquals(1, (int) counts.get(ReplayData.ENT_ADD));
        assertEquals(1, (int) counts.get(ReplayData.ENT_GONE));
    }

    @Test
    void theProjectilesAreThereBetweenTheirStartAndTheirEnd() throws IOException {
        ReplayData data = ReplayData.read(new ByteArrayInputStream(file(info(5), new int[0], game().finish())));
        assertTrue(data.thingsAt(1).isEmpty());
        assertEquals(1, data.thingsAt(2).size());
        ReplayData.Thing arrow = data.thingsAt(3).get(1);
        assertEquals(ReplayData.ARROW, arrow.kind);
        assertEquals(64, arrow.x, "where it went at tick 3");
        assertEquals(-32, arrow.z);
        assertTrue(data.thingsAt(4).isEmpty(), "gone at tick 4");
        // two blocks along x and one back along z in a tick: its speed at the start
        double[] velocity = data.velocityOf(1);
        assertEquals(2.0, velocity[0], 1e-9);
        assertEquals(0.0, velocity[1], 1e-9);
        assertEquals(-1.0, velocity[2], 1e-9);
        assertArrayEquals(new double[] {0, -0.05, 0}, data.velocityOf(99), 1e-9);
    }

    @Test
    void theMenuReadsTheInfoAlone() throws IOException {
        ReplayData.Info read = ReplayData.readInfo(new ByteArrayInputStream(file(info(5), new int[] {1, 2, 3, 4, 5},
                game().finish())));
        assertEquals("Wooden", read.mapName);
        assertEquals(1_700_000_000_000L, read.startedAt);
        assertEquals(5, read.ticks);
        assertEquals(1, read.actorOf(BOB));
        assertEquals(-1, read.actorOf(new UUID(3, 3)));
    }

    @Test
    void aBodyCutShortKeepsItsLastStateUntilTheEnd() throws IOException {
        ReplayData data = ReplayData.read(new ByteArrayInputStream(file(info(40), new int[0], game().finish())));
        assertEquals(40, data.ticks);
        assertEquals(58, data.x[0][39]);
        assertEquals(ReplayData.SNEAK, data.flags[1][39]);
        assertTrue(data.events(39).isEmpty());
    }

    @Test
    void anotherFileIsNotReadAsAReplay() {
        assertThrows(IOException.class, () -> ReplayData.read(new ByteArrayInputStream("hello".getBytes("UTF-8"))));
        byte[] good;
        try {
            good = file(info(5), new int[0], game().finish());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        byte[] cut = Arrays.copyOf(good, good.length / 2);
        assertThrows(IOException.class, () -> ReplayData.read(new ByteArrayInputStream(cut)));
    }

    @Test
    void positionsAndAnglesAreThoseOfTheProtocol() {
        assertEquals(48, ReplayData.fixed(101.5, 100));
        assertEquals(-8, ReplayData.fixed(99.75, 100));
        assertEquals(64, ReplayData.angle(90f));
        assertEquals(-64, ReplayData.angle(-90f));
        assertEquals(-128, ReplayData.angle(180f));
        assertEquals(64, ReplayData.angle(450f), "a full turn more is the same angle");
        assertEquals(90f, ReplayData.degrees((byte) 64), 1e-6);
        assertEquals("0:00", ReplayData.clock(-5));
        assertEquals("1:05", ReplayData.clock(65 * 20 + 19));
    }

    @Test
    void theBlocksOfTheWorldAreKeptByTheirPlace() {
        int[][] places = {{0, 0, 0}, {-1, 255, -1}, {12_000_000, 64, -12_000_000}, {-30_000_000, 1, 29_999_999}};
        for (int[] place : places) {
            long key = ReplayRecorder.key(place[0], place[1], place[2]);
            assertEquals(place[0], ReplayRecorder.xOf(key));
            assertEquals(place[1], ReplayRecorder.yOf(key));
            assertEquals(place[2], ReplayRecorder.zOf(key));
        }
        assertFalse(ReplayRecorder.key(1, 2, 3) == ReplayRecorder.key(3, 2, 1));
    }

    @Test
    void theFlagsOfABodyAreThoseOfA18Entity() {
        assertEquals(0, ReplayViewer.metadataFlags(0));
        assertEquals(0x02 | 0x08, ReplayViewer.metadataFlags(ReplayData.SNEAK | ReplayData.SPRINT));
        assertEquals(0x01 | 0x10 | 0x20, ReplayViewer.metadataFlags(ReplayData.BURNING | ReplayData.USING | ReplayData.INVISIBLE));
    }

    @Test
    void theLineAfterAGameOffersItsReplay() {
        String json = NextGame.json("sumo", true, "Sumo", "Bob", "ABCD2345");
        assertTrue(json.contains("\"text\":\"[REPLAY]\""), json);
        assertTrue(json.contains("\"value\":\"/replay ABCD2345\""), json);
        assertTrue(json.indexOf("[REMATCH]") < json.indexOf("[REPLAY]"));
        String alone = NextGame.json("sumo", false, "Sumo", null, "ABCD2345");
        assertTrue(alone.startsWith("[\"\",{\"text\":\"[REPLAY]\""), "no space before the only button: " + alone);
        assertFalse(NextGame.json("sumo", true, "Sumo", null, null).contains("REPLAY"));
    }
}
