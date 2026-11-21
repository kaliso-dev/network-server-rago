package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/** The Fireball Fight of FUN PRACTICE: its stronger push, its wind gust and its settings. */
class FireballKnockbackTest {

    @Test
    void theOthersArePushedHarderAcrossAndUp() {
        double[] push = Fireballs.push(0, 0, 0, 2, 0, 0);
        double[] harder = FireballKnockback.scaled(push, 1.5, 1.2);
        assertEquals(push[0] * 1.5, harder[0], 1e-9);
        assertEquals(push[1] * 1.2, harder[1], 1e-9);
        assertEquals(push[2] * 1.5, harder[2], 1e-9);
        assertTrue(harder[0] > push[0] && push[0] > 0, "still away from the blast, farther");
        assertArrayEquals(push, FireballKnockback.scaled(push, 1, 1), 1e-9, "times 1: the push of Bed Wars");
    }

    @Test
    void theGustIsTheLeapOfTheLobbyFeather() {
        // looking straight ahead: 1.6 forward, 0.7 up at least
        double[] ahead = FireballKnockback.leap(new Vector(0, 0, 1));
        assertArrayEquals(new double[] {0, 0.7, 1.6}, ahead, 1e-9);
        // looking up, more up; the same 1.6 across, whatever the pitch
        Vector up = new Vector(1, 1, 0).normalize();
        double[] higher = FireballKnockback.leap(up);
        assertEquals(1.6, higher[0], 1e-9);
        assertEquals(up.getY() * 1.4 + 0.5, higher[1], 1e-9);
        assertEquals(0, higher[2], 1e-9);
        // straight up: no way across
        double[] vertical = FireballKnockback.leap(new Vector(0, 1, 0));
        assertEquals(0, vertical[0], 1e-9);
        assertEquals(1.9, vertical[1], 1e-9);
        assertEquals(0, vertical[2], 1e-9);
    }

    @Test
    void theBarFillsUpUntilTheGustIsReady() {
        assertEquals(1f, FireballKnockback.gustBar(0, 3), 1e-6, "ready: a full bar");
        assertEquals(1f, FireballKnockback.gustBar(-500, 3), 1e-6);
        assertEquals(0f, FireballKnockback.gustBar(3000, 3), 1e-6, "just used: empty");
        assertEquals(0.5f, FireballKnockback.gustBar(1500, 3), 1e-6);
        assertEquals(1f, FireballKnockback.gustBar(1500, 0), 1e-6, "no wait between two gusts");
    }

    @Test
    void withoutALimitAFullStackOfFireballs() {
        assertEquals(64, FireballKnockback.fireballsOf(0));
        assertEquals(64, FireballKnockback.fireballsOf(-1));
        assertEquals(8, FireballKnockback.fireballsOf(8));
    }

    @Test
    void theSidesStartApartOnThePlatform() {
        assertEquals(5, FireballKnockback.startDistance(10), 1e-9);
        assertEquals(3, FireballKnockback.startDistance(5), 1e-9, "never closer than 3 blocks from the middle");
        assertTrue(FireballKnockback.startDistance(30) < 30, "on the platform");
    }

    @Test
    void checksItsSettings() {
        assertNull(FireballKnockback.Settings.check(10, 1.5, 1.2, 0, 3));
        assertTrue(FireballKnockback.Settings.check(4, 1.5, 1.2, 0, 3).startsWith("rayon"));
        assertTrue(FireballKnockback.Settings.check(31, 1.5, 1.2, 0, 3).startsWith("rayon"));
        assertTrue(FireballKnockback.Settings.check(10, 0.4, 1.2, 0, 3).startsWith("recul"));
        assertTrue(FireballKnockback.Settings.check(10, 1.5, 5.5, 0, 3).startsWith("recul"));
        assertTrue(FireballKnockback.Settings.check(10, 1.5, 1.2, 65, 3).startsWith("fireballs"));
        assertTrue(FireballKnockback.Settings.check(10, 1.5, 1.2, -1, 3).startsWith("fireballs"));
        assertTrue(FireballKnockback.Settings.check(10, 1.5, 1.2, 0, 61).startsWith("souffle"));
    }

    @Test
    void theBundledSettingsAreAccepted() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertNotNull(config.getConfigurationSection("fireball-knockback"));
        assertNull(FireballKnockback.Settings.check(config.getInt("fireball-knockback.rayon"),
                config.getDouble("fireball-knockback.recul-horizontal"), config.getDouble("fireball-knockback.recul-vertical"),
                config.getInt("fireball-knockback.fireballs"), config.getInt("fireball-knockback.souffle-secondes")));
        int points = config.getInt("fireball-knockback.points-pour-gagner");
        assertTrue(points >= 1 && points <= 10);
        assertTrue(config.getList("fireball-knockback.cartes").isEmpty(), "no map yet: the plugin's platform");
    }
}
