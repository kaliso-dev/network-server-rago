package gg.rago.tools.kbanalyzer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.VelocityPacket;
import gg.rago.tools.kbanalyzer.Explosions.Explosion;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A made-up fireball jump, written in the mod's line format: the player (entity 7) right-clicks a
 * fire charge, the fireball (entity 300) appears, moves, explodes under the player on wool next to
 * end stone, and the server pushes the player with a velocity packet instead of the explosion's own
 * push. A primed TNT (entity 301) that never explodes and a dropped item are around too.
 */
class ExplosionsTest {

    private static final int PLAYER = 7;
    private static final int FIREBALL = 300;
    private static final long T0 = 1_000_000L;
    private static final long CLICK = T0 + 500;
    private static final long SPAWN = CLICK + 40;
    private static final long EXPLOSION = SPAWN + 250;

    private final List<String> lines = new ArrayList<>();

    ExplosionsTest() {
        lines.add("{\"type\":\"session\",\"ms\":" + T0 + ",\"nano\":0,\"tick\":0,\"format\":1,\"mod\":\"0.2.0\","
                + "\"player\":\"Sofiene\",\"server\":\"mc.hypixel.net\"}");
        lines.add("{\"type\":\"context\",\"ms\":" + T0 + ",\"nano\":0,\"tick\":0,\"key\":\"server\",\"value\":\"mc.hypixel.net\"}");
        // The player stands at 10.5 65 10.5 until the explosion, flies, and lands 2 s later.
        int tick = 0;
        for (long t = T0; t <= EXPLOSION + 2000; t += 50) {
            tick++;
            double y = 65;
            boolean ground = true;
            double x = 10.5;
            if (t > EXPLOSION && t < EXPLOSION + 1500) {
                double s = (t - EXPLOSION) / 1500.0;
                y = 65 + 4 * s * (1 - s) * 3.0;
                x = 10.5 + 6 * s;
                ground = false;
            } else if (t >= EXPLOSION + 1500) {
                x = 16.5;
            }
            lines.add(String.format(Locale.ROOT, "{\"type\":\"tick\",\"ms\":%d,\"nano\":0,\"tick\":%d,\"eid\":%d,"
                    + "\"x\":%.4f,\"y\":%.4f,\"z\":10.5,\"ground\":%b,\"players\":[]}", t, tick, PLAYER, x, y, ground));
        }
        lines.add("{\"type\":\"mark\",\"ms\":" + (CLICK - 100) + ",\"nano\":0,\"tick\":1,\"text\":\"fireball jump on wool\"}");

        // A right click with a fire charge (385): position -1, face 255, then the item.
        ByteBuffer click = ByteBuffer.allocate(8 + 1 + 2 + 1 + 2 + 1 + 3);
        click.putLong(-1L).put((byte) 255).putShort((short) 385).put((byte) 1).putShort((short) 0).put((byte) 0)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        packet(CLICK, false, Packet.OUT_BLOCK_PLACEMENT, click.array());
        // The fireball appears at 10.5 66.5 10.5 thrown by the player, accelerating straight down.
        packet(SPAWN, true, Packet.IN_SPAWN_OBJECT, spawn(FIREBALL, Packet.OBJECT_FIREBALL, 10.5, 66.5, 10.5, PLAYER,
                new int[] {0, -800, 0}));
        packet(SPAWN + 5, true, Packet.IN_SPAWN_OBJECT, spawn(301, Packet.OBJECT_PRIMED_TNT, 30, 65, 30, 0, null));
        // 5 ticks later it is 1.5 blocks lower: 0.3 block per tick.
        packet(SPAWN + 250, true, Packet.IN_ENTITY_RELATIVE_MOVE, concat(varInt(FIREBALL), new byte[] {0, -48, 0, 0}));
        // It explodes at 10.5 65.0 10.5 with strength 3, breaking the wool under the player.
        packet(EXPLOSION, true, Packet.IN_EXPLOSION, explosion(10.5f, 65.0f, 10.5f, 3.0f,
                new int[][] {{0, -1, 0}, {1, -1, 0}}, new float[] {0, 0, 0}));
        packet(EXPLOSION + 30, true, Packet.IN_ENTITY_VELOCITY, concat(varInt(PLAYER), shorts(3200, 9600, 0)));
        packet(EXPLOSION + 50, true, Packet.IN_DESTROY_ENTITIES, concat(varInt(1), varInt(FIREBALL)));
        // The server confirms the wool is gone: one chunk, two records set to air.
        ByteArrayOutputStream changes = new ByteArrayOutputStream();
        changes.write(0);
        changes.write(0);
        changes.write(0);
        changes.write(0);
        changes.write(0);
        changes.write(0);
        changes.write(0);
        changes.write(0);
        writeVarInt(changes, 2);
        changes.write(10 << 4 | 10);
        changes.write(64);
        writeVarInt(changes, 0);
        changes.write(11 << 4 | 10);
        changes.write(64);
        writeVarInt(changes, 0);
        packet(EXPLOSION + 60, true, Packet.IN_MULTI_BLOCK_CHANGE, changes.toByteArray());
        // The blocks around it just before: wool under the player, end stone beside it.
        lines.add("{\"type\":\"blocks\",\"ms\":" + (EXPLOSION + 2) + ",\"nano\":0,\"tick\":10,\"x\":10.5,\"y\":65.0,"
                + "\"z\":10.5,\"strength\":3.0,\"reach\":7,\"count\":4,\"blocks\":[[10,64,10,35,14],[11,64,10,35,14],"
                + "[9,64,10,121,0],[10,64,16,121,0]]}");
        lines.add("{\"type\":\"end\",\"ms\":" + (EXPLOSION + 2100) + ",\"nano\":0,\"tick\":" + tick
                + ",\"ticks\":" + tick + ",\"dropped\":0,\"errors\":0}");
    }

    @Test
    void decodesTheNewPackets() {
        Packet spawn = new Packet(0, true, Packet.IN_SPAWN_OBJECT, spawn(FIREBALL, 63, 10.5, 66.5, -10.5, PLAYER,
                new int[] {1, -800, 2}));
        Packet.SpawnedObject object = spawn.spawnedObject();
        assertEquals(FIREBALL, object.entity);
        assertEquals(63, object.type);
        assertEquals(-10.5, object.z, 1e-9);
        assertArrayEquals(new int[] {1, -800, 2}, object.speed);
        assertNull(new Packet(0, true, Packet.IN_SPAWN_OBJECT, spawn(1, 50, 0, 0, 0, 0, null)).spawnedObject().speed);

        ByteBuffer change = ByteBuffer.allocate(8);
        change.putLong(((long) -5 & 0x3FFFFFF) << 38 | (64L & 0xFFF) << 26 | (long) -12 & 0x3FFFFFF);
        // Then the block state, 35 << 4 | 14 = 574, as a VarInt.
        byte[] bytes = concat(change.array(), varInt(35 << 4 | 14));
        assertArrayEquals(new int[] {-5, 64, -12, 35, 14},
                new Packet(0, true, Packet.IN_BLOCK_CHANGE, bytes).blockChanges().get(0));

        Packet.ExplosionPacket explosion = new Packet(0, true, Packet.IN_EXPLOSION,
                explosion(-3.5f, 70.2f, 4.9f, 4f, new int[][] {{-1, 0, 2}}, new float[] {0.1f, 0.2f, -0.3f})).explosion();
        // Offsets are added to the centre cast to int, as the client does: -3 for -3.5.
        assertArrayEquals(new int[] {-4, 70, 6}, explosion.blocks.get(0));
        assertEquals(-0.3, explosion.push[2], 1e-6);
        assertArrayEquals(new int[] {300, 301}, new Packet(0, true, Packet.IN_DESTROY_ENTITIES,
                concat(varInt(2), varInt(300), varInt(301))).destroyedEntities());
    }

    @Test
    void findsTheFireballJump() {
        Recording recording = Recording.parse(lines);
        assertTrue(recording.problems.isEmpty(), recording.problems.toString());
        List<Explosion> explosions = Explosions.find(recording);
        assertEquals(1, explosions.size());
        Explosion explosion = explosions.get(0);
        assertEquals("fireball jump on wool", explosion.mark);

        Explosions.Projectile source = explosion.source;
        assertNotNull(source);
        assertEquals("fireball", source.kind());
        assertEquals(Long.valueOf(CLICK), source.useMs);
        assertEquals(385, source.useItem);
        assertEquals(PLAYER, source.spawn.data);
        assertEquals(0.3, source.speedPerTick(), 1e-9);

        assertEquals(3.0f, explosion.packet.strength);
        assertNotNull(explosion.before);
        assertArrayEquals(new int[] {35, 14}, explosion.brokenBlock(explosion.packet.blocks.get(0)));
        assertEquals(2, explosion.changes.size());
        assertEquals(new VelocityPacket(3200, 9600, 0), explosion.velocity);
        assertEquals(30, explosion.velocityDelayMs);
        assertArrayEquals(new double[] {0.4, 1.2, 0}, explosion.push(), 1e-9);
        assertEquals(0.0, explosion.feetDistance(), 1e-9);
        assertEquals(1.62, explosion.eyeDistance(), 1e-9);
        assertTrue(Double.isNaN(explosion.pushAngle()), "the player stands on the centre");
        assertEquals(30, explosion.airTicks);
        assertEquals(3.0, explosion.rise, 0.05);
        assertEquals(6.0, explosion.landingDistance, 1e-9);
    }

    @Test
    void reportsTheExplosion() {
        String report = KbAnalyzer.report(Collections.<Fight>emptyList(),
                Collections.singletonMap("hypixel-fireballs", Recording.parse(lines)));
        assertTrue(report.contains("## Explosions: hypixel-fireballs"), report);
        assertTrue(report.contains("Player Sofiene on hypixel.net, kb-recorder 0.2.0: 1 explosions."), report);
        assertTrue(report.contains("| 1 | 0.79 s | fireball jump on wool | fireball | 40 ms (item 385) | 250 ms (5 ticks)"
                + " | 0.300 block/tick | 3.00 | 0.00 / 1.62 | 0.4000, 1.2000, 0.0000 | velocity packet after 30 ms"
                + " | 0.4000 / 1.2000 | - | 3.00 / 6.00 |"), report);
        assertTrue(report.contains("| 1 | wool ×2 up to 1.1 | 2 | end stone ×1 from 1.1 |"), report);
    }

    private void packet(long ms, boolean inbound, int id, byte[] data) {
        lines.add("{\"type\":\"packet\",\"ms\":" + ms + ",\"nano\":0,\"tick\":1,\"dir\":\"" + (inbound ? "in" : "out")
                + "\",\"id\":" + id + ",\"name\":\"x\",\"data\":\"" + Base64.getEncoder().encodeToString(data) + "\"}");
    }

    private static byte[] spawn(int entity, int type, double x, double y, double z, int data, int[] speed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, entity);
        out.write(type);
        ByteBuffer rest = ByteBuffer.allocate(12 + 2 + 4 + (speed != null ? 6 : 0));
        rest.putInt((int) Math.floor(x * 32)).putInt((int) Math.floor(y * 32)).putInt((int) Math.floor(z * 32))
                .put((byte) 0).put((byte) 0).putInt(data);
        if (speed != null) {
            rest.putShort((short) speed[0]).putShort((short) speed[1]).putShort((short) speed[2]);
        }
        return concat(out.toByteArray(), rest.array());
    }

    private static byte[] explosion(float x, float y, float z, float strength, int[][] offsets, float[] push) {
        ByteBuffer buffer = ByteBuffer.allocate(20 + 3 * offsets.length + 12);
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(strength).putInt(offsets.length);
        for (int[] offset : offsets) {
            buffer.put((byte) offset[0]).put((byte) offset[1]).put((byte) offset[2]);
        }
        buffer.putFloat(push[0]).putFloat(push[1]).putFloat(push[2]);
        return buffer.array();
    }

    private static byte[] shorts(int... values) {
        ByteBuffer buffer = ByteBuffer.allocate(2 * values.length);
        for (int value : values) {
            buffer.putShort((short) value);
        }
        return buffer.array();
    }

    private static byte[] varInt(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, value);
        return out.toByteArray();
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write(value & 0x7F | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }
}
