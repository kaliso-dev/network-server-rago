package gg.rago.buildtools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldSettingsTest {

    @TempDir
    Path folder;

    @Test
    void newWorldsAreCreativeAndProtected() {
        WorldSettings settings = WorldSettings.forNewWorld(WorldSettings.Generator.PLAT);
        assertEquals(WorldSettings.Generator.PLAT, settings.generator());
        assertEquals(WorldSettings.Mode.CREATIF, settings.mode());
        assertTrue(settings.isProtected());
    }

    @Test
    void roundTripsThroughTheWorldFolder() throws IOException {
        new WorldSettings(WorldSettings.Generator.VIDE, WorldSettings.Mode.SURVIE, false).write(folder.toFile());

        File file = new File(folder.toFile(), WorldSettings.FILE_NAME);
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("generateur: vide"), text);
        assertTrue(text.contains("mode: survie"), text);
        assertTrue(text.contains("protege: false"), text);

        WorldSettings read = WorldSettings.read(folder.toFile());
        assertEquals(WorldSettings.Generator.VIDE, read.generator());
        assertEquals(WorldSettings.Mode.SURVIE, read.mode());
        assertFalse(read.isProtected());
    }

    @Test
    void readsAFileWrittenByHand() throws IOException {
        write("generateur: plat\nmode: creatif\nprotege: true\n");
        WorldSettings read = WorldSettings.read(folder.toFile());
        assertEquals(WorldSettings.Generator.PLAT, read.generator());
        assertEquals(WorldSettings.Mode.CREATIF, read.mode());
        assertTrue(read.isProtected());
    }

    @Test
    void refusesUnknownOrMissingValues() throws IOException {
        write("generateur: normal\nmode: creatif\nprotege: true\n");
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.read(folder.toFile()));
        write("generateur: vide\nmode: aventure\nprotege: true\n");
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.read(folder.toFile()));
        write("generateur: vide\nmode: creatif\n");
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.read(folder.toFile()));
        write("generateur: [vide\n");
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.read(folder.toFile()));
    }

    private void write(String yaml) throws IOException {
        Files.write(folder.resolve(WorldSettings.FILE_NAME), yaml.getBytes(StandardCharsets.UTF_8));
    }
}
