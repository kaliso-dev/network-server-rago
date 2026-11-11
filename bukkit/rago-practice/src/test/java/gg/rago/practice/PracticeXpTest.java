package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PracticeXpTest {

    private static final UUID ME = UUID.randomUUID();

    @Test
    void aSuccessGivesMoreThanAnAttemptAndTheMinuteCapsIt() {
        PracticeXp xp = new PracticeXp(new PracticeXp.Rules(1, 1, 3, 8));
        assertEquals(3, xp.success(ME, "clutch", 0));
        assertEquals(1, xp.attempt(ME, "clutch", 1_000));
        assertEquals(1, xp.minute(ME, "clutch", 2_000));
        // 5 so far: one success more fits, the second only in part, then nothing until the minute ends
        assertEquals(3, xp.success(ME, "clutch", 3_000));
        assertEquals(0, xp.success(ME, "clutch", 4_000));
        assertEquals(0, xp.attempt(ME, "clutch", 59_999));
        assertEquals(3, xp.success(ME, "pearl-practice", 60_000));
        assertEquals("pearl-practice", xp.modeOf(ME));
        assertEquals(11, xp.take(ME));
        assertEquals(0, xp.take(ME));
        assertTrue(xp.waiting().isEmpty());
    }

    @Test
    void leavingAndComingBackDoesNotResetTheCap() {
        PracticeXp xp = new PracticeXp(new PracticeXp.Rules(1, 1, 3, 4));
        xp.success(ME, "clutch", 0);
        assertEquals(3, xp.take(ME));
        xp.prune(30_000);
        assertEquals(1, xp.success(ME, "clutch", 30_000), "the minute still runs");
        xp.take(ME);
        xp.prune(90_000);
        assertNull(xp.modeOf(ME), "forgotten once the minute is over and nothing waits");
    }

    @Test
    void theSettingsGiveTheRules() {
        YamlConfiguration config = new YamlConfiguration();
        PracticeXp.Rules rules = RagoPractice.xpRules(config);
        assertEquals(1, rules.perMinute);
        assertEquals(1, rules.perAttempt);
        assertEquals(3, rules.perSuccess);
        assertEquals(8, rules.maxPerMinute);
        config.set("xp-entrainement.max-par-minute", 500);
        assertEquals("xp-entrainement.max-par-minute: from 0 to 100", RagoPractice.checkModes(config));
    }
}
