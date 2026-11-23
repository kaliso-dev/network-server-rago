package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A YAML file of the plugin read as UTF-8, whatever the charset of the system. Bukkit 1.8 reads
 * config.yml in the system's charset: Windows-1252 on a French Windows, where the € of the prices,
 * the ✦ of the badges and the » of the messages came out broken (the owner's report of 23/11). A
 * file that is not UTF-8 (saved as "ANSI" by an editor) is still read in the system's charset, as
 * before.
 */
final class Utf8Config {

    private Utf8Config() {
    }

    /** The file (empty if it is not there or cannot be read, as Bukkit does), with the bundled one as its defaults. */
    static YamlConfiguration read(File file, InputStream bundled, Logger logger) {
        YamlConfiguration config = file(file, logger);
        if (bundled != null) {
            try (Reader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not read the bundled " + file.getName(), e);
            }
        }
        return config;
    }

    /** The file, empty if it is not there or cannot be read. */
    static YamlConfiguration file(File file, Logger logger) {
        YamlConfiguration config = new YamlConfiguration();
        if (!file.isFile()) {
            return config;
        }
        try {
            config.loadFromString(text(Files.readAllBytes(file.toPath()), Charset.defaultCharset()));
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Cannot load " + file, e);
        }
        return config;
    }

    /** The bytes as UTF-8 (without a byte order mark), or in the fallback charset if they are not UTF-8. */
    static String text(byte[] bytes, Charset fallback) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, fallback);
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
