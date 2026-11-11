package gg.rago.bedwars;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

class MapLeftoversTest {

    @Test
    void theItemsLyingOnTheForgesOfACapturedMapGo() {
        assertTrue(MapLeftovers.leftover(EntityType.DROPPED_ITEM, false, false, false));
        assertTrue(MapLeftovers.leftover(EntityType.EXPERIENCE_ORB, false, false, false));
        assertTrue(MapLeftovers.leftover(EntityType.ARROW, false, false, false));
    }

    @Test
    void theHologramsGoButTheArmourStandsOneCanSeeStay() {
        assertTrue(MapLeftovers.leftover(EntityType.ARMOR_STAND, true, false, true));
        assertFalse(MapLeftovers.leftover(EntityType.ARMOR_STAND, true, false, false));
    }

    @Test
    void theMobsGoThePlayersAndTheDecorationsStay() {
        assertTrue(MapLeftovers.leftover(EntityType.ZOMBIE, true, false, false));
        assertTrue(MapLeftovers.leftover(EntityType.VILLAGER, true, false, false));
        assertFalse(MapLeftovers.leftover(EntityType.PLAYER, true, true, false));
        assertFalse(MapLeftovers.leftover(EntityType.ITEM_FRAME, false, false, false));
        assertFalse(MapLeftovers.leftover(EntityType.PAINTING, false, false, false));
    }
}
