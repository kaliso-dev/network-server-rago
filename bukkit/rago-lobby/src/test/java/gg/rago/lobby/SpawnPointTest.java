package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnPointTest {

    @TempDir
    Path folder;

    @Test
    void roundTripsThroughSpawnYml() throws IOException {
        File file = folder.resolve(SpawnPoint.FILE_NAME).toFile();
        assertNull(SpawnPoint.read(file), "no file, no spawn");

        Files.write(file.toPath(), new SpawnPoint(10.5, 64, -3.25, 90f, -12.5f).toYaml().getBytes(StandardCharsets.UTF_8));
        Location read = SpawnPoint.read(file).in(null);
        assertEquals(10.5, read.getX());
        assertEquals(64.0, read.getY());
        assertEquals(-3.25, read.getZ());
        assertEquals(90f, read.getYaw());
        assertEquals(-12.5f, read.getPitch());
    }

    @Test
    void refusesAnIncompleteFile() throws IOException {
        File file = folder.resolve(SpawnPoint.FILE_NAME).toFile();
        Files.write(file.toPath(), "x: 1\ny: 2\n".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> SpawnPoint.read(file));
    }
}
