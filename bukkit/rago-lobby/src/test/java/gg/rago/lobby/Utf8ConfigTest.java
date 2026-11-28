package gg.rago.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** config.yml read as UTF-8: the € of the prices, the ✦ of the badges (the owner's report of 23/11). */
class Utf8ConfigTest {

    private static final Charset WINDOWS = Charset.forName("windows-1252");
    private static final Logger LOG = Logger.getLogger("Utf8ConfigTest");

    @Test
    void itReadsUtf8OnASystemOfAnotherCharset() {
        String text = "prix: \"4.99€\"\nbadge: \"&d&l✦\"\nligne: \"&c&lRAGO &8» &fBed Wars · Duels\"\n";
        assertEquals(text, Utf8Config.text(text.getBytes(StandardCharsets.UTF_8), WINDOWS),
                "what Bukkit read as 4.99â‚¬ on a French Windows");
    }

    @Test
    void aFileSavedInTheCharsetOfTheSystemIsStillRead() {
        assertEquals("prix: \"4.99€ é\"", Utf8Config.text("prix: \"4.99€ é\"".getBytes(WINDOWS), WINDOWS),
                "a file an editor saved as ANSI: read as Bukkit did");
    }

    @Test
    void theByteOrderMarkIsLeftOut() {
        assertEquals("a: 1", Utf8Config.text("﻿a: 1".getBytes(StandardCharsets.UTF_8), WINDOWS));
    }

    @Test
    void theFileWithTheBundledOneBehindIt(@TempDir Path folder) throws Exception {
        File file = folder.resolve("config.yml").toFile();
        Files.write(file.toPath(), "devise: \"€\"\n".getBytes(StandardCharsets.UTF_8));
        YamlConfiguration config = Utf8Config.read(file,
                new ByteArrayInputStream("devise: \"$\"\nbadge: \"✦\"\n".getBytes(StandardCharsets.UTF_8)), LOG);
        assertEquals("€", config.getString("devise"), "the server's own");
        assertEquals("✦", config.getString("badge"), "the bundled one, for what the file leaves out");
        assertTrue(Utf8Config.file(folder.resolve("none.yml").toFile(), LOG).getKeys(false).isEmpty(), "no file: empty");
    }
}
