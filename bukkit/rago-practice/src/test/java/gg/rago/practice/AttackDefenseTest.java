package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The attack and defense as a solo rush: the island, the bridge with its gap, the starts. */
class AttackDefenseTest {

    @Test
    void thePlainBridgeLeavesThreeBlocksOfVoid() {
        AttackDefense.Base base = AttackDefense.Base.plain();
        // the plain island runs from x = -2: the bridge stops at x = -6, at the height of its top
        assertEquals(-6, Math.max(base.bridge[0], base.bridge[3]));
        assertEquals(3, -2 - Math.max(base.bridge[0], base.bridge[3]) - 1);
        assertEquals(0, base.bridge[1]);
        assertEquals(1, base.defender[1], 0.0, "the defender stands on the island");
    }

    @Test
    void theAttackerStartsOnTheFarEndFacingTheIsland() {
        AttackDefense.Base plain = AttackDefense.Base.plain();
        // along +x: yaw -90
        assertArrayEquals(new double[] {-15.5, 1, 0.5, -90}, plain.attackerStart(0), 1e-9);
        assertArrayEquals(new double[] {-16.5, 1, 0.5, -90}, plain.attackerStart(1), 1e-9, "the second one behind");
        // the Lotus bridge, along -z: yaw 180
        AttackDefense.Base lotus = new AttackDefense.Base(null, null, new double[] {0.5, 0, -5.5, 0}, new int[] {0, 0, 18, 0, 0, 8});
        double[] start = lotus.attackerStart(0);
        assertEquals(0.5, start[0], 1e-9);
        assertEquals(18.5, start[2], 1e-9);
        assertEquals(180, Math.abs(start[3]), 1e-9);
    }

    @Test
    void theSectionIsChecked() {
        int spacing = 200;
        assertNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84, -70), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, -82, 0), Arrays.asList(-34, 65, -58, -34, 65, -68), spacing));
        assertNotNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84, -70), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, -82, 0), Arrays.asList(-34, 65, -58, -30, 65, -68), spacing), "a bridge on a slant");
        assertNotNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84, -70), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, -82, 0), Arrays.asList(-34, 65, -58, -34, 66, -68), spacing), "a bridge on two heights");
        assertNotNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84, -70), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, 82, 0), Arrays.asList(-34, 65, -58, -34, 65, -68), spacing), "a defender out of the zone");
        assertNotNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84, -70), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, -82, 0), Arrays.asList(-34, 65, 58, -34, 65, -68), 100), "a bridge too long for the zone");
        assertNotNull(RagoPractice.checkAttackDefense(Arrays.asList(-55, 58, -102, -16, 84), Arrays.asList(-34, 66, -76),
                Arrays.asList(-34, 65, -82, 0), Arrays.asList(-34, 65, -58, -34, 65, -68), spacing));
    }

    @Test
    void theBundledIslandIsLotusAndIsChecked() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                RagoPractice.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        assertEquals("Lotus", config.getString("attaque-defense.monde"));
        assertNull(RagoPractice.checkAttackDefense(config.getList("attaque-defense.zone"), config.getList("attaque-defense.lit"),
                config.getList("attaque-defense.defenseur"), config.getList("attaque-defense.pont"), config.getInt("espacement")));
        assertEquals(0, config.getInt("attaque-defense.manches") % 2);
    }
}
