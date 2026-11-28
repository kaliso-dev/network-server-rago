package gg.rago.tools.kbcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.tools.kbanalyzer.Recording;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplosionCheckTest {

    private static final long T0 = 1_794_052_800_000L;
    private static final int TNT = 300;
    private static final ExplosionCheck.Expected SUMMONED = new ExplosionCheck.Expected("tnt", 20.5, 4, 0.5, 4f);

    /** What the mod records when a TNT summoned at 20.5 4 0.5 explodes on grass. */
    private static List<String> recording(boolean destroy, boolean blocksBefore, boolean blockChange) {
        List<String> lines = new ArrayList<>();
        lines.add("{\"type\":\"session\",\"ms\":" + T0 + ",\"nano\":0,\"tick\":0,\"format\":1}");
        lines.add("{\"type\":\"tick\",\"ms\":" + T0 + ",\"nano\":0,\"tick\":1,\"eid\":7,\"x\":0.5,\"y\":4.0,\"z\":0.5,"
                + "\"ground\":true,\"players\":[]}");
        ByteArrayOutputStream spawn = new ByteArrayOutputStream();
        writeVarInt(spawn, TNT);
        spawn.write(50);
        byte[] rest = ByteBuffer.allocate(18).putInt(20 * 32 + 16).putInt(4 * 32).putInt(16).put((byte) 0).put((byte) 0)
                .putInt(0).array();
        spawn.write(rest, 0, rest.length);
        packet(lines, T0 + 100, 0x0E, spawn.toByteArray());
        byte[] explosion = ByteBuffer.allocate(20 + 6 + 12).putFloat(20.5f).putFloat(4.0625f).putFloat(0.5f).putFloat(4f)
                .putInt(2).put((byte) 0).put((byte) -1).put((byte) 0).put((byte) 1).put((byte) -1).put((byte) 0)
                .putFloat(0f).putFloat(0f).putFloat(0f).array();
        packet(lines, T0 + 1100, 0x27, explosion);
        if (blocksBefore) {
            lines.add("{\"type\":\"blocks\",\"ms\":" + (T0 + 1102) + ",\"nano\":0,\"tick\":2,\"x\":20.5,\"y\":4.0625,"
                    + "\"z\":0.5,\"strength\":4.0,\"reach\":8,\"count\":3,\"blocks\":[[20,3,0,2,0],[21,3,0,2,0],[20,2,0,3,0]]}");
        } else {
            lines.add("{\"type\":\"blocks\",\"ms\":" + (T0 + 1102) + ",\"nano\":0,\"tick\":2,\"x\":20.5,\"y\":4.0625,"
                    + "\"z\":0.5,\"strength\":4.0,\"reach\":8,\"count\":1,\"blocks\":[[20,2,0,3,0]]}");
        }
        if (blockChange) {
            ByteArrayOutputStream change = new ByteArrayOutputStream();
            byte[] chunk = ByteBuffer.allocate(8).putInt(1).putInt(0).array();
            change.write(chunk, 0, chunk.length);
            writeVarInt(change, 1);
            change.write(4 << 4);
            change.write(3);
            writeVarInt(change, 0);
            packet(lines, T0 + 1140, 0x22, change.toByteArray());
        }
        if (destroy) {
            packet(lines, T0 + 1150, 0x13, new byte[] {1, (byte) 0xAC, 0x02});
        }
        lines.add("{\"type\":\"end\",\"ms\":" + (T0 + 2000) + ",\"nano\":0,\"tick\":2,\"ticks\":2,\"dropped\":0,\"errors\":0}");
        return lines;
    }

    @Test
    void aCompleteRecordingPasses() {
        ExplosionCheck.Result result = ExplosionCheck.check(Recording.parse(recording(true, true, true)),
                Collections.singletonList(SUMMONED));
        assertTrue(result.passed(), result.toMarkdown());
        assertEquals("| tnt | TNT 1000 ms before | 4.0 | 2 | 3 | 1 | passed |", result.rows.get(0));
    }

    @Test
    void eachMissingPieceFails() {
        ExplosionCheck.Result noDestroy = ExplosionCheck.check(Recording.parse(recording(false, true, true)),
                Collections.singletonList(SUMMONED));
        assertFalse(noDestroy.passed());
        assertTrue(noDestroy.problems.get(0).contains("spawn or destroy packet missing"), noDestroy.problems.toString());

        ExplosionCheck.Result readAfter = ExplosionCheck.check(Recording.parse(recording(true, false, true)),
                Collections.singletonList(SUMMONED));
        assertTrue(readAfter.problems.get(0).contains("it was read after the explosion"), readAfter.problems.toString());

        ExplosionCheck.Result noChange = ExplosionCheck.check(Recording.parse(recording(true, true, false)),
                Collections.singletonList(SUMMONED));
        assertTrue(noChange.problems.get(0).contains("no block change"), noChange.problems.toString());

        ExplosionCheck.Result elsewhere = ExplosionCheck.check(Recording.parse(recording(true, true, true)),
                Collections.singletonList(new ExplosionCheck.Expected("tnt", 40.5, 4, 0.5, 4f)));
        assertTrue(elsewhere.problems.get(0).contains("no explosion packet near it"), elsewhere.problems.toString());

        ExplosionCheck.Result none = ExplosionCheck.check(Recording.parse(recording(true, true, true)),
                Collections.<ExplosionCheck.Expected>emptyList());
        assertTrue(none.problems.contains("the bot caused no explosion"));
    }

    private static void packet(List<String> lines, long ms, int id, byte[] data) {
        lines.add("{\"type\":\"packet\",\"ms\":" + ms + ",\"nano\":0,\"tick\":1,\"dir\":\"in\",\"id\":" + id
                + ",\"name\":\"x\",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}");
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }
}
