package gg.rago.buildtools;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * The settings of a build world, kept in {@value #FILE_NAME} inside the world's own folder. Keeping
 * them there, rather than in the plugin's folder, means a world carries everything it needs: copy
 * the folder (or restore a backup) and the world comes back exactly as it was.
 *
 * <p>A world folder without this file is not loaded by the plugin: {@code /monde importer} adds it.
 */
final class WorldSettings {

    static final String FILE_NAME = "rago-monde.yml";

    /** What the server generates where nothing has been built yet. */
    enum Generator {
        /** Nothing at all: the world is empty air, except a single block under the spawn. */
        VIDE,
        /** Minecraft's default superflat: bedrock, two dirt, grass. */
        PLAT;

        String configName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Generator parse(String value) {
            for (Generator generator : values()) {
                if (generator.configName().equals(value)) {
                    return generator;
                }
            }
            return null;
        }
    }

    /** The game mode players get when they enter the world. */
    enum Mode {
        CREATIF,
        SURVIE;

        String configName() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.configName().equals(value)) {
                    return mode;
                }
            }
            return null;
        }
    }

    private final Generator generator;
    private final Mode mode;
    private final boolean protectedWorld;

    WorldSettings(Generator generator, Mode mode, boolean protectedWorld) {
        if (generator == null || mode == null) {
            throw new IllegalArgumentException("generator and mode are required");
        }
        this.generator = generator;
        this.mode = mode;
        this.protectedWorld = protectedWorld;
    }

    /** A new build world: creative, and only builders may change it. */
    static WorldSettings forNewWorld(Generator generator) {
        return new WorldSettings(generator, Mode.CREATIF, true);
    }

    Generator generator() {
        return generator;
    }

    Mode mode() {
        return mode;
    }

    /** True when only players with {@code rago.build.construire} may place or break blocks. */
    boolean isProtected() {
        return protectedWorld;
    }

    static File fileIn(File worldFolder) {
        return new File(worldFolder, FILE_NAME);
    }

    /**
     * Reads the settings of a world folder.
     *
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if a value is missing or not one of the allowed ones; the
     *     message is in French, for the server console
     */
    static WorldSettings read(File worldFolder) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(fileIn(worldFolder));
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException(FILE_NAME + " n'est pas un YAML valide : " + e.getMessage(), e);
        }
        String generatorValue = yaml.getString("generateur", "");
        Generator generator = Generator.parse(generatorValue);
        if (generator == null) {
            throw new IllegalArgumentException("generateur doit valoir vide ou plat, pas « " + generatorValue + " »");
        }
        String modeValue = yaml.getString("mode", "");
        Mode mode = Mode.parse(modeValue);
        if (mode == null) {
            throw new IllegalArgumentException("mode doit valoir creatif ou survie, pas « " + modeValue + " »");
        }
        if (!yaml.isBoolean("protege")) {
            throw new IllegalArgumentException("protege doit valoir true ou false");
        }
        return new WorldSettings(generator, mode, yaml.getBoolean("protege"));
    }

    /** Writes the settings into the world folder, which must exist. */
    void write(File worldFolder) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Réglages de ce monde pour RagoBuildTools (serveur de test Rago).\n"
                + "generateur : ce que le serveur crée là où rien n'est construit (vide ou plat).\n"
                + "mode : le mode de jeu des joueurs qui entrent dans le monde (creatif ou survie).\n"
                + "protege : si true, seuls les constructeurs (les opérateurs) peuvent poser ou casser des blocs.");
        yaml.set("generateur", generator.configName());
        yaml.set("mode", mode.configName());
        yaml.set("protege", protectedWorld);
        yaml.save(fileIn(worldFolder));
    }
}
