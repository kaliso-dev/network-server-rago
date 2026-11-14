package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The billboards of config.yml, and where their maps go. */
class BillboardTest {

    private static List<Billboard> read(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return Billboard.read(yaml.getConfigurationSection("panneaux"));
    }

    @Test
    void readsTheBillboards() throws Exception {
        List<Billboard> boards = read("panneaux:\n"
                + "  rago-client: {image: rago-client.png, largeur: 8, hauteur: 8, lieu: \"18 67 47\"}\n"
                + "  discord: {image: Discord Banner.jpg, largeur: 16, hauteur: 9, lieu: \"-40 66 10 Est\"}\n"
                + "  youtube: {image: yt.png, largeur: 4, hauteur: 2}\n");
        assertEquals(3, boards.size());
        Billboard client = boards.get(0);
        assertEquals("rago-client", client.name);
        assertEquals("rago-client.png", client.image);
        assertEquals(64, client.maps());
        assertArrayEquals(new int[] {18, 67, 47}, client.block);
        assertEquals(Billboard.TOWARDS_SPAWN, client.face, "no face given: towards the spawn");
        assertEquals("panneau-rago-client", client.point());
        Billboard discord = boards.get(1);
        assertEquals("Discord Banner.jpg", discord.image);
        assertEquals(144, discord.maps());
        assertEquals(Billboard.EAST, discord.face);
        assertNull(boards.get(2).block, "no lieu: only on its point");
        assertTrue(read("panneaux: {}\n").isEmpty());
        assertTrue(read("titre: x\n").isEmpty(), "no section: none");
    }

    /** The bundled config.yml has none (the images are the server's), its example reads. */
    @Test
    void theBundledConfigHasNoneAndItsExampleReads() throws Exception {
        YamlConfiguration bundled = LobbySettingsTest.bundledConfig();
        assertTrue(LobbySettings.load(bundled).billboards().isEmpty());
        bundled.set("panneaux", null);
        bundled.createSection("panneaux");
        bundled.getConfigurationSection("panneaux").createSection("discord");
        bundled.set("panneaux.discord.image", "discord.png");
        bundled.set("panneaux.discord.largeur", 8);
        bundled.set("panneaux.discord.hauteur", 8);
        bundled.set("panneaux.discord.lieu", "0 70 40");
        List<Billboard> boards = LobbySettings.load(bundled).billboards();
        assertEquals(1, boards.size());
        assertArrayEquals(new int[] {0, 70, 40}, boards.get(0).block);
    }

    @Test
    void refusesWhatCannotShow() {
        assertInvalid("panneaux:\n  Pub: {image: a.png, largeur: 2, hauteur: 2}\n", "panneaux.Pub: the name");
        assertInvalid("panneaux:\n  pub: {image: ../config.yml, largeur: 2, hauteur: 2}\n", "panneaux.pub.image");
        assertInvalid("panneaux:\n  pub: {image: pub, largeur: 2, hauteur: 2}\n", "panneaux.pub.image");
        assertInvalid("panneaux:\n  pub: {image: a.png, largeur: 17, hauteur: 2}\n", "panneaux.pub: largeur and hauteur");
        assertInvalid("panneaux:\n  pub: {image: a.png, hauteur: 2}\n", "panneaux.pub: largeur and hauteur");
        assertInvalid("panneaux:\n  pub: {image: a.png, largeur: 2, hauteur: 2, lieu: \"1 2\"}\n", "panneaux.pub.lieu");
        assertInvalid("panneaux:\n  pub: {image: a.png, largeur: 2, hauteur: 2, lieu: \"1 2 x\"}\n", "panneaux.pub.lieu");
        assertInvalid("panneaux:\n  pub: {image: a.png, largeur: 2, hauteur: 2, lieu: \"1 2 3 haut\"}\n",
                "panneaux.pub.lieu: it faces");
        assertInvalid("panneaux:\n  pub: a.png\n", "panneaux.pub: image");
        StringBuilder many = new StringBuilder("panneaux:\n");
        for (int i = 0; i <= Billboard.MAX_BOARDS; i++) {
            many.append("  p").append(i).append(": {image: a.png, largeur: 1, hauteur: 1}\n");
        }
        assertInvalid(many.toString(), "panneaux: " + Billboard.MAX_BOARDS);
    }

    private static void assertInvalid(String text, String start) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(text), text);
        assertTrue(e.getMessage().startsWith(start), e.getMessage());
    }

    /** The way a player looks, rounded: what a board placed on their point faces. */
    @Test
    void facesTheWayThePlayerLooked() {
        assertEquals(Billboard.SOUTH, Billboard.face(0f));
        assertEquals(Billboard.SOUTH, Billboard.face(359.7f));
        assertEquals(Billboard.SOUTH, Billboard.face(-44f));
        assertEquals(Billboard.WEST, Billboard.face(91.5f));
        assertEquals(Billboard.NORTH, Billboard.face(180f));
        assertEquals(Billboard.NORTH, Billboard.face(-170f));
        assertEquals(Billboard.EAST, Billboard.face(-90f));
        assertEquals(Billboard.EAST, Billboard.face(269.5f));
    }

    /** On the back edge of the lobby, the spawn far ahead and a little aside: north, towards it. */
    @Test
    void facesTheSpawn() {
        assertEquals(Billboard.NORTH, Billboard.faceTowards(18.5, 47.5, 0.1, -27.5));
        assertEquals(Billboard.SOUTH, Billboard.faceTowards(0.5, -40.5, 3, 10));
        assertEquals(Billboard.EAST, Billboard.faceTowards(-48.5, 10.5, 0.1, -27.5));
        assertEquals(Billboard.WEST, Billboard.faceTowards(48.5, 10.5, 0.1, -27.5));
    }

    /**
     * Seen from in front, the first map top left, the bottom row on the block given, its middle
     * there: facing north, whoever looks at it looks south and has the west on their right.
     */
    @Test
    void theMapsFillTheWallFromTheTopLeftAsSeenFromInFront() {
        int[][] north = Billboard.blocks(18, 67, 47, Billboard.NORTH, 8, 8);
        assertEquals(64, north.length);
        assertArrayEquals(new int[] {22, 74, 47}, north[0], "top left: east, as seen from the north");
        assertArrayEquals(new int[] {15, 74, 47}, north[7], "top right");
        assertArrayEquals(new int[] {18, 67, 47}, north[60], "bottom row, just right of the middle");
        assertArrayEquals(new int[] {15, 67, 47}, north[63], "bottom right");

        int[][] south = Billboard.blocks(0, 64, 0, Billboard.SOUTH, 3, 2);
        assertArrayEquals(new int[] {-1, 65, 0}, south[0], "seen from the south, the west on the left");
        assertArrayEquals(new int[] {1, 65, 0}, south[2]);
        assertArrayEquals(new int[] {0, 64, 0}, south[4], "the middle of the bottom row");

        int[][] east = Billboard.blocks(0, 64, 0, Billboard.EAST, 2, 1);
        assertArrayEquals(new int[] {0, 64, 1}, east[0], "seen from the east, the south on the left");
        assertArrayEquals(new int[] {0, 64, 0}, east[1]);

        int[][] west = Billboard.blocks(0, 64, 0, Billboard.WEST, 2, 1);
        assertArrayEquals(new int[] {0, 64, -1}, west[0], "seen from the west, the north on the left");
        assertArrayEquals(new int[] {0, 64, 0}, west[1]);
    }
}
