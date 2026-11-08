package gg.rago.buildtools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * {@code /monde}: list, create, import and visit the build worlds, set their spawn and save them.
 * Every subcommand except {@code tp} and {@code spawn} also works from the server console. The
 * subcommands are English (list, create, import, tp, spawn, save; void or flat); the French ones of
 * the first version (liste, creer, importer, sauver; vide, plat) still work.
 */
final class MondeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList(
            "list", "create", "import", "tp", "spawn", "save"));
    private static final List<String> GENERATORS = Collections.unmodifiableList(Arrays.asList("void", "flat"));
    private static final String PREFIX = ChatColor.RED + "[Rago] " + ChatColor.RESET;

    private final BuildWorlds worlds;
    private final Logger logger;

    MondeCommand(BuildWorlds worlds, Logger logger) {
        this.worlds = worlds;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : english(args[0].toLowerCase(Locale.ROOT));
        switch (sub) {
            case "list":
                list(sender);
                return true;
            case "create":
                if (args.length < 2 || args.length > 3) {
                    return usage(sender, "/" + label + " create <name> [void|flat]");
                }
                create(sender, args[1], args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "vide");
                return true;
            case "import":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " import <folder>");
                }
                importWorld(sender, args[1]);
                return true;
            case "tp":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " tp <world>");
                }
                teleport(sender, args[1]);
                return true;
            case "spawn":
                setSpawn(sender);
                return true;
            case "save":
                worlds.saveAll();
                sender.sendMessage(PREFIX + "Every world is saved to disk.");
                logger.info("Worlds saved (" + sender.getName() + ")");
                return true;
            default:
                return usage(sender, "/" + label + " <list|create|import|tp|spawn|save>");
        }
    }

    /** The English name of a subcommand typed in French. */
    private static String english(String sub) {
        switch (sub) {
            case "liste":
                return "list";
            case "creer":
                return "create";
            case "importer":
                return "import";
            case "sauver":
                return "save";
            default:
                return sub;
        }
    }

    private void list(CommandSender sender) {
        sender.sendMessage(PREFIX + "Server worlds:");
        for (World world : Bukkit.getWorlds()) {
            WorldSettings settings = worlds.settingsOf(world);
            String kind = settings == null ? "server world, survival" : BuildWorlds.describe(settings);
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + world.getName() + ChatColor.GRAY + ": "
                    + kind + ", " + world.getPlayers().size() + " player(s)");
        }
        List<String> importable = worlds.importable();
        if (!importable.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Maps to import with /monde import: " + String.join(", ", importable));
        }
    }

    private void create(CommandSender sender, String name, String generatorName) {
        WorldSettings.Generator generator = WorldSettings.Generator.parse(
                "void".equals(generatorName) ? "vide" : "flat".equals(generatorName) ? "plat" : generatorName);
        if (generator == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown type \"" + generatorName + "\": void or flat.");
            return;
        }
        sender.sendMessage(PREFIX + "Creating the world " + name + "...");
        try {
            World world = worlds.create(name, generator);
            sender.sendMessage(PREFIX + "World " + name + " created. Go and see it with /monde tp " + name + ".");
            if (sender instanceof Player) {
                ((Player) sender).teleport(world.getSpawnLocation());
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Cannot do it: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(sender, "creating the world " + name, e);
        }
    }

    private void importWorld(CommandSender sender, String name) {
        try {
            World world = worlds.importWorld(name);
            sender.sendMessage(PREFIX + "Map " + name + " imported. Go and see it with /monde tp " + name + ".");
            if (sender instanceof Player) {
                ((Player) sender).teleport(world.getSpawnLocation());
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Cannot do it: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(sender, "importing " + name, e);
        }
    }

    private void teleport(CommandSender sender, String name) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only a player can teleport.");
            return;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "No world \"" + name + "\". Type /monde for the list.");
            return;
        }
        ((Player) sender).teleport(world.getSpawnLocation());
    }

    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only a player can set the spawn point, where they stand.");
            return;
        }
        Location at = ((Player) sender).getLocation();
        at.getWorld().setSpawnLocation(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        sender.sendMessage(PREFIX + "Spawn point of " + at.getWorld().getName() + " set here ("
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + ").");
    }

    private void fail(CommandSender sender, String what, Exception e) {
        logger.log(Level.SEVERE, "Failed: " + what, e);
        sender.sendMessage(PREFIX + ChatColor.RED + "Failed: " + what + ": " + e.getMessage()
                + ". The details are in the server console.");
    }

    private static boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(PREFIX + "Usage: " + usage);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return startingWith(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "tp".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                names.add(world.getName());
            }
            return startingWith(names, args[1]);
        }
        if (args.length == 2 && "import".equals(english(args[0].toLowerCase(Locale.ROOT)))) {
            return startingWith(worlds.importable(), args[1]);
        }
        if (args.length == 3 && "create".equals(english(args[0].toLowerCase(Locale.ROOT)))) {
            return startingWith(GENERATORS, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> startingWith(List<String> options, String prefix) {
        List<String> matches = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
