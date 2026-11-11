package gg.rago.tools.kbcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.rago.combat.model.KnockbackCalculator;
import gg.rago.combat.model.KnockbackProfile;
import gg.rago.combat.model.Vec3;
import gg.rago.combat.model.VelocityPacket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the files a control run produces, in the exact format the engine and the bots write. */
class LogFormatTest {

    /**
     * A line written by the engine's HitRecord. HitRecordTest in rago-combat-engine checks that the
     * engine still writes exactly this line for the same hit, so the two modules cannot drift apart.
     */
    static final String ENGINE_LINE = "{\"type\":\"melee\",\"time\":1700000000123,\"tick\":42,\"mode\":\"override\","
            + "\"profile\":\"control-alt\",\"profileValues\":{\"friction\":1.7,\"horizontal\":0.36,\"vertical\":0.37,"
            + "\"verticalLimit\":0.39,\"extraHorizontal\":0.45,\"extraVertical\":0.085},\"world\":\"kb-control\","
            + "\"attacker\":\"RagoAttacker\",\"victim\":\"RagoVictim\",\"victimEntityId\":7,\"sprinting\":true,"
            + "\"knockbackEnchantment\":1,\"victimOnGround\":false,\"input\":{\"attackerX\":10.123456789012344,"
            + "\"attackerZ\":-4.5,\"attackerYaw\":37.25,\"knockbackLevel\":2,\"victimX\":11.0,\"victimZ\":-2.25,"
            + "\"victimMotion\":[0.03333333333333333,-0.0784000015258789,-1.0E-7]},"
            + "\"velocity\":[-0.3944655881428242,0.4088823520436006,1.0518537649986386],"
            + "\"packet\":[-3155,3271,8414],\"applied\":true}";

    /** A line written by bots/control-bots.mjs when the victim receives a velocity packet. */
    static final String BOT_LINE = "{\"type\":\"velocity\",\"time\":1700000000125,\"bot\":\"RagoVictim\","
            + "\"entityId\":7,\"packet\":[-3155,3271,8414]}";

    /** A line written by bots/control-bots.mjs for each attack. */
    static final String ATTACK_LINE = "{\"type\":\"attack\",\"time\":1700000000120,\"phase\":\"override-control-alt\","
            + "\"index\":5,\"sprint\":true,\"enchantment\":1,\"victim\":\"walk\",\"airTicks\":0,\"distance\":2.1234}";

    @TempDir
    Path directory;

    @Test
    void readsEveryFieldOfAnEngineLineExactly() throws IOException {
        Path hits = Files.createDirectory(directory.resolve("hits"));
        write(hits.resolve("hits-2026-11-07.jsonl"), ENGINE_LINE, "");
        write(hits.resolve("notes.txt"), "not a hit log");

        List<RecordedHit> read = ControlCheck.readHits(hits);

        assertEquals(1, read.size());
        RecordedHit hit = read.get(0);
        assertEquals(1700000000123L, hit.time);
        assertEquals("override", hit.mode);
        assertTrue(hit.applied);
        assertTrue(hit.sprinting);
        assertFalse(hit.victimOnGround);
        assertEquals(7, hit.victimEntityId);
        assertEquals(KnockbackProfile.of("control-alt", 1.7D, 0.36D, 0.37D, 0.39D, 0.45D, 0.085D), hit.profile);
        assertEquals(0.45F, hit.profile.extraHorizontal());
        assertEquals(10.123456789012345D, hit.hit.attackerX());
        assertEquals(37.25F, hit.hit.attackerYaw());
        assertEquals(2, hit.hit.knockbackLevel());
        assertEquals(new Vec3(0.1D / 3.0D, (0.0D - 0.08D) * 0.9800000190734863D, -1.0E-7D), hit.hit.victimMotion());
        assertEquals(new VelocityPacket(-3155, 3271, 8414), hit.packet);
        // The logged velocity is the model's, to the last bit.
        assertEquals(new KnockbackCalculator().compute(hit.profile, hit.hit), hit.velocity);
        assertEquals(VelocityPacket.encode(hit.velocity), hit.packet);
    }

    @Test
    void readsTheVelocityPacketsTheBotsReceived() throws IOException {
        Path received = directory.resolve("received.jsonl");
        write(received, "{\"type\":\"phase\",\"time\":1700000000000,\"name\":\"override-control-alt\"}", ATTACK_LINE,
                BOT_LINE);

        List<ReceivedVelocity> read = ControlCheck.readReceived(received);
        List<BotAttack> attacks = ControlCheck.readAttacks(received);

        assertEquals(1, attacks.size());
        assertEquals(1700000000120L, attacks.get(0).time);
        assertEquals("override-control-alt #5 (sprint, Knockback 1, victim walk)", attacks.get(0).description);
        assertEquals(1, read.size());
        assertEquals(1700000000125L, read.get(0).time);
        assertEquals(7, read.get(0).entityId);
        assertEquals(new VelocityPacket(-3155, 3271, 8414), read.get(0).packet);
    }

    @Test
    void floatsAreReadAsTheFloatsThatWereWritten() {
        // Reading "0.45" as a double and narrowing it would round twice; getAsFloat parses it once.
        for (float value : new float[] {0.45F, 0.425F, 37.25F, -179.99998F, 1.0E-7F, 0.1F, 3.4028235E38F}) {
            String json = "{\"value\":" + Float.toString(value) + "}";
            assertEquals(value, com.google.gson.JsonParser.parseString(json).getAsJsonObject().get("value").getAsFloat());
        }
    }

    private static void write(Path file, String... lines) throws IOException {
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
    }
}
