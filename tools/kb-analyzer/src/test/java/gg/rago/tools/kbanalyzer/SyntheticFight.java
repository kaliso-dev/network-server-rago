package gg.rago.tools.kbanalyzer;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Two recordings of the same made-up fight, written in the mod's line format: Bob hits Alice six
 * times, 9 ticks apart, and attacks three more times after each hit, too early to count.
 *
 * <ul>
 *   <li>Alice (entity 10) receives each hit 20 ms after the server sends it; her clock is the
 *       true time.</li>
 *   <li>Bob (entity 20) sends with 50 ms of delay and receives with 50 ms; his clock is 5000 ms
 *       behind.</li>
 *   <li>Bob starts sprinting before hits 0, 2 and 4. Each sprint hit ends his sprint on the
 *       server, so hits 1, 3 and 5 are without sprint.</li>
 *   <li>Packets are those of a server with a sprint step of 560 units (Hypixel's): 2886/3446 up,
 *       3200/7200 along z, with Bob 2.5 blocks north of Alice and looking south.</li>
 * </ul>
 */
final class SyntheticFight {

    static final int ALICE = 10;
    static final int BOB = 20;
    static final long BOB_CLOCK = -5000L;
    static final long ALICE_DOWN = 20L;
    static final long BOB_UP = 50L;
    static final long BOB_DOWN = 50L;
    static final int HITS = 6;
    static final long FIRST_HIT = 1000L;
    static final long HIT_INTERVAL = 450L;

    static long serverHit(int k) {
        return FIRST_HIT + HIT_INTERVAL * k;
    }

    static boolean sprintHit(int k) {
        return k % 2 == 0;
    }

    static List<String> alice() {
        Lines lines = new Lines("Alice", 0L);
        for (long t = 0; t <= 4000; t += 50) {
            lines.tick(t, ALICE, 0.5, 3.0, true, BOB, "Bob", 0.5, 0.5);
        }
        for (int k = 0; k < HITS; k++) {
            long arrival = serverHit(k) + ALICE_DOWN;
            lines.status(arrival, ALICE);
            lines.velocity(arrival + 1, ALICE, 0, sprintHit(k) ? 3446 : 2886, sprintHit(k) ? 7200 : 3200);
        }
        return lines.finish();
    }

    static List<String> bob() {
        Lines lines = new Lines("Bob", BOB_CLOCK);
        for (long t = 0; t <= 4000; t += 50) {
            lines.tick(t, BOB, 0.5, 0.5, true, ALICE, "Alice", 0.5, 3.0);
        }
        lines.look(500L, 0.0F);
        for (int k = 0; k < HITS; k++) {
            long attack = serverHit(k) - BOB_UP;
            if (sprintHit(k)) {
                lines.sprint(attack - 50, true);
            }
            lines.attack(attack, ALICE);
            for (int extra = 1; extra <= 3; extra++) {
                lines.attack(attack + 110L * extra, ALICE);
            }
            lines.status(serverHit(k) + BOB_DOWN, ALICE);
        }
        return lines.finish();
    }

    /** Builds the lines of one recording; times are given in true time and written in the player's clock. */
    static final class Lines {
        private final List<String> lines = new ArrayList<>();
        private final long clock;
        private int tick;

        Lines(String player, long clock) {
            this.clock = clock;
            lines.add("{\"type\":\"session\",\"ms\":" + (clock + 0) + ",\"nano\":0,\"tick\":0,\"format\":1,"
                    + "\"label\":\"test\",\"player\":\"" + player + "\",\"server\":\"test.local\"}");
        }

        void tick(long t, int entity, double x, double z, boolean ground, int other, String otherName, double ox,
                double oz) {
            tick++;
            lines.add(String.format(Locale.ROOT, "{\"type\":\"tick\",\"ms\":%d,\"nano\":0,\"tick\":%d,\"eid\":%d,"
                    + "\"x\":%s,\"y\":4.0,\"z\":%s,\"mx\":0.0,\"my\":-0.0784000015258789,\"mz\":0.0,\"yaw\":0.0,"
                    + "\"ground\":%s,\"sprint\":false,\"players\":[{\"eid\":%d,\"name\":\"%s\",\"x\":%s,\"y\":4.0,"
                    + "\"z\":%s,\"yaw\":0.0,\"ground\":true,\"sprint\":false,\"swing\":false,\"hurt\":0}]}",
                    clock + t, tick, entity, x, z, ground, other, otherName, ox, oz));
        }

        void status(long t, int entity) {
            packet(t, "in", 0x1A, "entity_status", ByteBuffer.allocate(5).putInt(entity).put((byte) 2).array());
        }

        void velocity(long t, int entity, int x, int y, int z) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            varInt(out, entity);
            out.write(ByteBuffer.allocate(6).putShort((short) x).putShort((short) y).putShort((short) z).array(), 0, 6);
            packet(t, "in", 0x12, "entity_velocity", out.toByteArray());
        }

        void attack(long t, int target) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            varInt(out, target);
            varInt(out, 1);
            packet(t, "out", 0x02, "use_entity", out.toByteArray());
        }

        void sprint(long t, boolean start) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            varInt(out, BOB);
            varInt(out, start ? 3 : 4);
            varInt(out, 0);
            packet(t, "out", 0x0B, "entity_action", out.toByteArray());
        }

        void look(long t, float yaw) {
            packet(t, "out", 0x05, "player_look", ByteBuffer.allocate(9).putFloat(yaw).putFloat(0.0F).put((byte) 1).array());
        }

        private void packet(long t, String dir, int id, String name, byte[] data) {
            lines.add("{\"type\":\"packet\",\"ms\":" + (clock + t) + ",\"nano\":0,\"tick\":" + tick + ",\"dir\":\"" + dir
                    + "\",\"id\":" + id + ",\"name\":\"" + name + "\",\"data\":\""
                    + Base64.getEncoder().encodeToString(data) + "\"}");
        }

        List<String> finish() {
            lines.add("{\"type\":\"end\",\"ms\":" + (clock + 4100) + ",\"nano\":0,\"tick\":" + tick + ",\"ticks\":" + tick
                    + ",\"dropped\":0,\"errors\":0}");
            return lines;
        }

        private static void varInt(ByteArrayOutputStream out, int value) {
            while ((value & ~0x7F) != 0) {
                out.write((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            out.write(value);
        }
    }
}
