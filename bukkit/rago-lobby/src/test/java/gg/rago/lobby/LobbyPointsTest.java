package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LobbyPointsTest {

    @TempDir
    Path folder;

    @Test
    void roundTripsThroughPointsYml() throws IOException {
        File file = folder.resolve(LobbyPoints.FILE_NAME).toFile();
        assertTrue(LobbyPoints.read(file).all().isEmpty(), "no file, no points");

        LobbyPoints points = new LobbyPoints();
        assertTrue(points.set("pnj-sumo", new SpawnPoint(4.5, 65, -2, 180f, 0f)));
        assertTrue(points.set("classement-1v1", new SpawnPoint(-10, 70.5, 8, 0f, 10f)));
        Files.write(file.toPath(), points.toYaml().getBytes(StandardCharsets.UTF_8));

        LobbyPoints read = LobbyPoints.read(file);
        assertEquals(2, read.all().size());
        SpawnPoint sumo = read.get("pnj-sumo");
        assertEquals(4.5, sumo.x());
        assertEquals(65.0, sumo.y());
        assertEquals(-2.0, sumo.z());
        assertEquals(180f, sumo.yaw());
        assertEquals(10f, read.get("classement-1v1").pitch());
    }

    @Test
    void checksNamesAndRemoves() {
        LobbyPoints points = new LobbyPoints();
        assertFalse(points.set("Avec Espace", new SpawnPoint(0, 0, 0, 0f, 0f)));
        assertFalse(points.set("", new SpawnPoint(0, 0, 0, 0f, 0f)));
        assertTrue(points.set("pnj", new SpawnPoint(0, 0, 0, 0f, 0f)));
        assertTrue(points.remove("pnj"));
        assertFalse(points.remove("pnj"));
        assertNull(points.get("pnj"));
    }

    @Test
    void refusesABrokenFile() throws IOException {
        File file = folder.resolve(LobbyPoints.FILE_NAME).toFile();
        Files.write(file.toPath(), "pnj:\n  x: 1\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> LobbyPoints.read(file));
    }
}
