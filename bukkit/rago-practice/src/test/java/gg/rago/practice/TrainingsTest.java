package gg.rago.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TrainingsTest {

    @Test
    void aDefenseIsEndStoneInsideAndWoolOutside() {
        assertEquals(Material.WOOL, FastDefenseTraining.typeOf(0, 1));
        assertEquals(Material.ENDER_STONE, FastDefenseTraining.typeOf(0, 2));
        assertEquals(Material.WOOL, FastDefenseTraining.typeOf(1, 2));
        assertEquals(Material.ENDER_STONE, FastDefenseTraining.typeOf(0, 3));
        assertEquals(Material.WOOD, FastDefenseTraining.typeOf(1, 3));
        assertEquals(Material.WOOL, FastDefenseTraining.typeOf(2, 3));
    }

    @Test
    void theDefensesTakeTheBlocksTheirDescriptionsSay() {
        // "8 blocks", "26 blocks", "58 blocks"
        assertEquals(8, count(Shapes.defense(0, 1, 0, 1, 0, 1)));
        assertEquals(26, count(Shapes.defense(0, 1, 0, 1, 0, 2)));
        assertEquals(58, count(Shapes.defense(0, 1, 0, 1, 0, 3)));
    }

    private static int count(List<List<int[]>> layers) {
        int count = 0;
        for (List<int[]> layer : layers) {
            count += layer.size();
        }
        return count;
    }
}
