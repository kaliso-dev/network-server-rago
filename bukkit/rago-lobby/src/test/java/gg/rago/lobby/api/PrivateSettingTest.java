package gg.rago.lobby.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class PrivateSettingTest {

    @Test
    void oneChoiceOfItsChoices() {
        PrivateSetting speed = new PrivateSetting("speed", "Speed", Material.SUGAR,
                Arrays.asList("None", "Speed I", "Speed II", "Speed III"), Collections.singletonList("Speed for every player."));
        assertFalse(speed.toggles);
        assertTrue(speed.icons.isEmpty());
        assertTrue(speed.valid(0));
        assertTrue(speed.valid(3));
        assertFalse(speed.valid(4));
        assertFalse(speed.valid(-1));
        assertEquals("Speed II", speed.describe(2));
        assertEquals("None", speed.describe(9), "a choice out of the setting is that of a normal game");
    }

    @Test
    void itemsToTickOneBitEach() {
        PrivateSetting weapons = PrivateSetting.toggles("one-hit-one-kill", "One Hit One Kill", Material.DIAMOND_SWORD,
                Arrays.asList("Wooden Sword", "Bow", "TNT"), Arrays.asList(Material.WOOD_SWORD, Material.BOW, Material.TNT),
                Collections.singletonList("Select which items should instantly kill enemies."));
        assertTrue(weapons.toggles);
        assertEquals(Arrays.asList(Material.WOOD_SWORD, Material.BOW, Material.TNT), weapons.icons);
        assertTrue(weapons.valid(0), "none ticked: a normal game");
        assertTrue(weapons.valid(7), "all three");
        assertFalse(weapons.valid(8));
        assertEquals("None", weapons.describe(0));
        assertEquals("Wooden Sword, TNT", weapons.describe(1 | 4));
        assertEquals("Wooden Sword, Bow, TNT", weapons.describe(7));
        assertThrows(IllegalArgumentException.class, () -> PrivateSetting.toggles("x", "X", Material.STONE,
                Arrays.asList("A", "B"), Collections.singletonList(Material.STONE), Collections.<String>emptyList()),
                "an icon for each item");
    }
}
