package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The block under an NPC of the lobby (pnj: ... bloc:, plan 5.3). */
class PedestalTest {

    @Test
    void aBlockByItsNameOrItsIdWithItsData() {
        Pedestal quartz = Pedestal.parse("QUARTZ_BLOCK");
        assertEquals(Material.QUARTZ_BLOCK, quartz.material());
        assertEquals(0, quartz.data());
        Pedestal andesite = Pedestal.parse("STONE:6");
        assertEquals(Material.STONE, andesite.material());
        assertEquals(6, andesite.data());
        Pedestal clay = Pedestal.parse("159:14");
        assertEquals(Material.STAINED_CLAY, clay.material(), "the red stained clay of the plan, by its id");
        assertEquals(14, clay.data());
        assertEquals(Material.EMERALD_BLOCK, Pedestal.parse("emerald_block").material());
    }

    @Test
    void notABlockIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("DIAMOND"), "an item");
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("AIR"));
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("TORCH"), "not solid");
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("STONE:16"));
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("STONE:x"));
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse("NOT_A_BLOCK"));
        assertThrows(IllegalArgumentException.class, () -> Pedestal.parse(""));
    }

    @Test
    void aBlockChosenAndAlreadyUnderThePointIsKept() {
        Pedestal emerald = Pedestal.parse("EMERALD_BLOCK");
        assertTrue(emerald.builtUnder(Material.EMERALD_BLOCK, (byte) 0, 0), "the emerald the builder put there, alone");
        assertFalse(emerald.builtUnder(Material.STONE, (byte) 0, 0));
        assertFalse(Pedestal.parse("STONE:6").builtUnder(Material.STONE, (byte) 0, 0), "another stone");
        assertFalse(Pedestal.ANDESITE.builtUnder(Material.STONE, (byte) 6, 0),
                "without bloc:, a floor of andesite still gets its pedestal, as before");
        assertFalse(Pedestal.parse("QUARTZ_BLOCK").builtUnder(Material.QUARTZ_BLOCK, (byte) 0, 4),
                "a floor of quartz (the platform of a new lobby): the NPC still stands a block higher");
        assertFalse(emerald.builtUnder(Material.EMERALD_BLOCK, (byte) 0, 1), "an emerald beside it: a floor");
    }

    @Test
    void theBundledNpcsStandOnTheBlocksOfThePlan() throws Exception {
        LobbySettings settings = LobbySettings.load(LobbySettingsTest.bundledConfig());
        for (String play : new String[] {"practice", "rbw", "duels", "arenas", "prive"}) {
            assertEquals(Material.QUARTZ_BLOCK, settings.pedestal(play).material(), play);
        }
        assertEquals(Material.IRON_BLOCK, settings.pedestal("profil").material());
        assertEquals(Material.IRON_BLOCK, settings.pedestal("profil2").material());
        assertEquals(Material.EMERALD_BLOCK, settings.pedestal("shop").material());
        assertEquals(Pedestal.ANDESITE, settings.pedestal("newleaderboardselector"), "no bloc: polished andesite");
        assertEquals("daily", settings.dailyPoint());
        assertEquals("quests", settings.questsPoint());
    }

    @Test
    void theChestAndTheQuestsNpcOnOtherPoints() throws Exception {
        YamlConfiguration yaml = LobbySettingsTest.bundledConfig();
        yaml.set("point-coffre-du-jour", "newdailychest");
        yaml.set("point-pnj-quetes", "newquest");
        LobbySettings settings = LobbySettings.load(yaml);
        assertEquals("newdailychest", settings.dailyPoint());
        assertEquals("newquest", settings.questsPoint());
        yaml.set("point-pnj-quetes", "not a point");
        assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(yaml));
        YamlConfiguration wrong = LobbySettingsTest.bundledConfig();
        wrong.set("pnj.shop.bloc", "DIAMOND");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> LobbySettings.load(wrong));
        assertTrue(error.getMessage().startsWith("pnj.shop.bloc"), error.getMessage());
    }
}
