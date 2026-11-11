package gg.rago.buildtools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/**
 * The build worlds: every folder of the server's world container that holds a level.dat and a
 * {@value WorldSettings#FILE_NAME}. They are loaded when the server starts, created with
 * {@code /monde creer} and adopted with {@code /monde importer}. Main thread only.
 */
final class BuildWorlds {

    /** Time of day set in a new or imported world: noon, the best light to build. */
    private static final long NOON = 6000L;

    private final File container;
    private final Logger logger;
    private final Map<String, WorldSettings> settings = new HashMap<>();

    BuildWorlds(File container, Logger logger) {
        this.container = container;
        this.logger = logger;
    }

    /** The settings of a build world, or null for the server's own worlds (the KB test world). */
    WorldSettings settingsOf(World world) {
        return settings.get(world.getName());
    }

    /** Loads every build world of the container. A world that fails is reported and skipped. */
    void loadAll() {
        File[] folders = container.listFiles(File::isDirectory);
        if (folders == null) {
            return;
        }
        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, folders);
        Collections.sort(sorted);
        for (File folder : sorted) {
            if (!WorldSettings.fileIn(folder).isFile() || !new File(folder, "level.dat").isFile()) {
                continue;
            }
            String name = folder.getName();
            try {
                WorldSettings world = WorldSettings.read(folder);
                if (Bukkit.getWorld(name) == null) {
                    load(name, world);
                } else {
                    settings.put(name, world);
                }
                logger.info("Monde " + name + " chargé (" + describe(world) + ")");
            } catch (IOException | RuntimeException e) {
                logger.log(Level.SEVERE, "Le monde " + name + " n'a pas été chargé : " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a new build world.
     *
     * @throws IllegalArgumentException with a French message when the name cannot be used
     * @throws IOException if the settings file cannot be written
     */
    World create(String name, WorldSettings.Generator generator) throws IOException {
        checkNewName(name);
        File folder = new File(container, name);
        if (folder.exists()) {
            throw new IllegalArgumentException("un dossier " + name + " existe déjà dans les mondes : "
                    + "utilise /monde importer " + name + " si c'est une map.");
        }
        if (!folder.mkdirs()) {
            throw new IOException("impossible de créer le dossier " + folder);
        }
        WorldSettings world = WorldSettings.forNewWorld(generator);
        World created;
        try {
            world.write(folder);
            created = load(name, world);
        } catch (IOException | RuntimeException e) {
            // Leave no half-made folder behind, or the name could never be used again.
            if (!WorldSettings.fileIn(folder).delete() || !folder.delete()) {
                logger.warning("Le dossier " + folder + " est resté après l'échec : supprime-le à la main.");
            }
            throw e;
        }
        created.setTime(NOON);
        if (generator == WorldSettings.Generator.VIDE) {
            created.getBlockAt(0, VoidGenerator.PLATFORM_Y, 0).setType(Material.BEDROCK);
            created.setSpawnLocation(0, VoidGenerator.PLATFORM_Y + 1, 0);
        }
        created.save();
        logger.info("Monde " + name + " créé (" + describe(world) + ")");
        return created;
    }

    /**
     * Adopts a map copied into the world container: checks its version, writes its settings (empty
     * around the map, creative, protected) and loads it.
     *
     * @throws IllegalArgumentException with a French message when the folder cannot be imported
     * @throws IOException if level.dat cannot be read or the settings cannot be written
     */
    World importWorld(String name) throws IOException {
        checkNewName(name);
        File folder = new File(container, name);
        if (!new File(folder, "level.dat").isFile()) {
            throw new IllegalArgumentException("il n'y a pas de dossier " + name + " avec un level.dat dans "
                    + container.getAbsolutePath() + ". Copie le dossier de la map à cet endroit (le dossier qui "
                    + "contient level.dat et region), puis recommence.");
        }
        if (WorldSettings.fileIn(folder).exists()) {
            throw new IllegalArgumentException("le monde " + name + " est déjà importé.");
        }
        int version = LevelVersion.read(folder);
        if (version > LevelVersion.LAST_OPENABLE) {
            throw new IllegalArgumentException("cette map a été enregistrée avec Minecraft 1.13 ou plus récent "
                    + "(DataVersion " + version + ") : un serveur 1.8 viderait ses chunks. Prends une version de la "
                    + "map faite pour 1.8 à 1.12, ou un schematic à coller avec WorldEdit.");
        }
        WorldSettings world = WorldSettings.forNewWorld(WorldSettings.Generator.VIDE);
        world.write(folder);
        World imported = load(name, world);
        imported.setTime(NOON);
        imported.save();
        logger.info("Monde " + name + " importé (" + describe(world) + ")");
        if (version != LevelVersion.BEFORE_1_9) {
            logger.warning("La map " + name + " vient de Minecraft 1.9 à 1.12 (DataVersion " + version
                    + ") : les blocs ajoutés après 1.8 ont disparu.");
        }
        return imported;
    }

    /** Folders of the container that hold a map not imported yet, for tab completion. */
    List<String> importable() {
        List<String> names = new ArrayList<>();
        File[] folders = container.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                if (new File(folder, "level.dat").isFile() && !WorldSettings.fileIn(folder).exists()
                        && Bukkit.getWorld(folder.getName()) == null) {
                    names.add(folder.getName());
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Writes every loaded world and the players to disk. Main thread only. */
    void saveAll() {
        for (World world : Bukkit.getWorlds()) {
            world.save();
        }
        Bukkit.savePlayers();
    }

    static String describe(WorldSettings world) {
        return world.generator().configName() + ", " + (world.mode() == WorldSettings.Mode.CREATIF ? "créatif" : "survie")
                + (world.isProtected() ? ", protégé" : ", ouvert à tous");
    }

    private void checkNewName(String name) {
        String problem = WorldName.problem(name);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (Bukkit.getWorld(name) != null) {
            throw new IllegalArgumentException("le monde " + name + " existe déjà.");
        }
    }

    private World load(String name, WorldSettings world) {
        WorldCreator creator = new WorldCreator(name).generateStructures(false);
        if (world.generator() == WorldSettings.Generator.VIDE) {
            creator.generator(new VoidGenerator());
        } else {
            creator.type(WorldType.FLAT);
        }
        World loaded = creator.createWorld();
        if (loaded == null) {
            throw new IllegalStateException("le serveur n'a pas pu ouvrir le monde " + name);
        }
        // Nothing should change a build but the builders.
        loaded.setGameRuleValue("doDaylightCycle", "false");
        loaded.setGameRuleValue("doMobSpawning", "false");
        loaded.setGameRuleValue("doFireTick", "false");
        loaded.setGameRuleValue("mobGriefing", "false");
        loaded.setSpawnFlags(false, false);
        loaded.setStorm(false);
        loaded.setThundering(false);
        settings.put(name, world);
        return loaded;
    }
}
