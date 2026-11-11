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
                logger.info("World " + name + " loaded (" + describe(world) + ")");
            } catch (IOException | RuntimeException e) {
                logger.log(Level.SEVERE, "The world " + name + " was not loaded: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a new build world.
     *
     * @throws IllegalArgumentException with a message for the player when the name cannot be used
     * @throws IOException if the settings file cannot be written
     */
    World create(String name, WorldSettings.Generator generator) throws IOException {
        checkNewName(name);
        File folder = new File(container, name);
        if (folder.exists()) {
            throw new IllegalArgumentException("a folder " + name + " already exists among the worlds: "
                    + "use /monde import " + name + " if it is a map.");
        }
        if (!folder.mkdirs()) {
            throw new IOException("cannot create the folder " + folder);
        }
        WorldSettings world = WorldSettings.forNewWorld(generator);
        World created;
        try {
            world.write(folder);
            created = load(name, world);
        } catch (IOException | RuntimeException e) {
            // Leave no half-made folder behind, or the name could never be used again.
            if (!WorldSettings.fileIn(folder).delete() || !folder.delete()) {
                logger.warning("The folder " + folder + " is left after the failure: delete it by hand.");
            }
            throw e;
        }
        created.setTime(NOON);
        if (generator == WorldSettings.Generator.VIDE) {
            created.getBlockAt(0, VoidGenerator.PLATFORM_Y, 0).setType(Material.BEDROCK);
            created.setSpawnLocation(0, VoidGenerator.PLATFORM_Y + 1, 0);
        }
        created.save();
        logger.info("World " + name + " created (" + describe(world) + ")");
        return created;
    }

    /**
     * Adopts a map copied into the world container: checks its version, writes its settings (empty
     * around the map, creative, protected) and loads it.
     *
     * @throws IllegalArgumentException with a message for the player when the folder cannot be imported
     * @throws IOException if level.dat cannot be read or the settings cannot be written
     */
    World importWorld(String name) throws IOException {
        checkNewName(name);
        File folder = new File(container, name);
        if (!new File(folder, "level.dat").isFile()) {
            throw new IllegalArgumentException("there is no folder " + name + " with a level.dat in "
                    + container.getAbsolutePath() + ". Copy the map folder there (the folder that "
                    + "holds level.dat and region), then try again.");
        }
        if (WorldSettings.fileIn(folder).exists()) {
            throw new IllegalArgumentException("the world " + name + " is already imported.");
        }
        int version = LevelVersion.read(folder);
        if (version > LevelVersion.LAST_OPENABLE) {
            throw new IllegalArgumentException("this map was saved with Minecraft 1.13 or newer "
                    + "(DataVersion " + version + "): a 1.8 server would empty its chunks. Use a version of the "
                    + "map made for 1.8 to 1.12, or a schematic to paste with WorldEdit.");
        }
        WorldSettings world = WorldSettings.forNewWorld(WorldSettings.Generator.VIDE);
        world.write(folder);
        World imported = load(name, world);
        imported.setTime(NOON);
        imported.save();
        logger.info("World " + name + " imported (" + describe(world) + ")");
        if (version != LevelVersion.BEFORE_1_9) {
            logger.warning("The map " + name + " comes from Minecraft 1.9 to 1.12 (DataVersion " + version
                    + "): the blocks added after 1.8 are gone.");
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

    /** "void, creative, protected": for /monde and the console. */
    static String describe(WorldSettings world) {
        return (world.generator() == WorldSettings.Generator.VIDE ? "void" : "flat") + ", "
                + (world.mode() == WorldSettings.Mode.CREATIF ? "creative" : "survival")
                + (world.isProtected() ? ", protected" : ", open to everyone");
    }

    private void checkNewName(String name) {
        String problem = WorldName.problem(name);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (Bukkit.getWorld(name) != null) {
            throw new IllegalArgumentException("the world " + name + " already exists.");
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
            throw new IllegalStateException("the server could not open the world " + name);
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
