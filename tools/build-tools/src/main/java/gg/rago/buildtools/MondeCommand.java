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
 * Every subcommand except {@code tp} and {@code spawn} also works from the server console.
 */
final class MondeCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList(
            "liste", "creer", "importer", "tp", "spawn", "sauver"));
    private static final List<String> GENERATORS = Collections.unmodifiableList(Arrays.asList("vide", "plat"));
    private static final String PREFIX = ChatColor.RED + "[Rago] " + ChatColor.RESET;

    private final BuildWorlds worlds;
    private final Logger logger;

    MondeCommand(BuildWorlds worlds, Logger logger) {
        this.worlds = worlds;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "liste" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "liste":
                list(sender);
                return true;
            case "creer":
                if (args.length < 2 || args.length > 3) {
                    return usage(sender, "/" + label + " creer <nom> [vide|plat]");
                }
                create(sender, args[1], args.length == 3 ? args[2].toLowerCase(Locale.ROOT) : "vide");
                return true;
            case "importer":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " importer <dossier>");
                }
                importWorld(sender, args[1]);
                return true;
            case "tp":
                if (args.length != 2) {
                    return usage(sender, "/" + label + " tp <monde>");
                }
                teleport(sender, args[1]);
                return true;
            case "spawn":
                setSpawn(sender);
                return true;
            case "sauver":
                worlds.saveAll();
                sender.sendMessage(PREFIX + "Tous les mondes sont enregistrés sur le disque.");
                logger.info("Mondes enregistrés (" + sender.getName() + ")");
                return true;
            default:
                return usage(sender, "/" + label + " <liste|creer|importer|tp|spawn|sauver>");
        }
    }

    private void list(CommandSender sender) {
        sender.sendMessage(PREFIX + "Mondes du serveur :");
        for (World world : Bukkit.getWorlds()) {
            WorldSettings settings = worlds.settingsOf(world);
            String kind = settings == null ? "monde du serveur, survie" : BuildWorlds.describe(settings);
            sender.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + world.getName() + ChatColor.GRAY + " : "
                    + kind + ", " + world.getPlayers().size() + " joueur(s)");
        }
        List<String> importable = worlds.importable();
        if (!importable.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Maps à importer avec /monde importer : " + String.join(", ", importable));
        }
    }

    private void create(CommandSender sender, String name, String generatorName) {
        WorldSettings.Generator generator = WorldSettings.Generator.parse(generatorName);
        if (generator == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Type inconnu « " + generatorName + " » : vide ou plat.");
            return;
        }
        sender.sendMessage(PREFIX + "Création du monde " + name + "...");
        try {
            World world = worlds.create(name, generator);
            sender.sendMessage(PREFIX + "Monde " + name + " créé. Va le voir avec /monde tp " + name + ".");
            if (sender instanceof Player) {
                ((Player) sender).teleport(world.getSpawnLocation());
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Impossible : " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(sender, "la création du monde " + name, e);
        }
    }

    private void importWorld(CommandSender sender, String name) {
        try {
            World world = worlds.importWorld(name);
            sender.sendMessage(PREFIX + "Map " + name + " importée. Va la voir avec /monde tp " + name + ".");
            if (sender instanceof Player) {
                ((Player) sender).teleport(world.getSpawnLocation());
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Impossible : " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            fail(sender, "l'import de " + name, e);
        }
    }

    private void teleport(CommandSender sender, String name) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Seul un joueur peut se téléporter.");
            return;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Aucun monde « " + name + " ». Tape /monde pour la liste.");
            return;
        }
        ((Player) sender).teleport(world.getSpawnLocation());
    }

    private void setSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Seul un joueur peut placer le point d'apparition, là où il se trouve.");
            return;
        }
        Location at = ((Player) sender).getLocation();
        at.getWorld().setSpawnLocation(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        sender.sendMessage(PREFIX + "Point d'apparition de " + at.getWorld().getName() + " placé ici ("
                + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + ").");
    }

    private void fail(CommandSender sender, String what, Exception e) {
        logger.log(Level.SEVERE, "Échec de " + what, e);
        sender.sendMessage(PREFIX + ChatColor.RED + "Échec de " + what + " : " + e.getMessage()
                + ". Le détail est dans la console du serveur.");
    }

    private static boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(PREFIX + "Utilisation : " + usage);
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
        if (args.length == 2 && "importer".equalsIgnoreCase(args[0])) {
            return startingWith(worlds.importable(), args[1]);
        }
        if (args.length == 3 && "creer".equalsIgnoreCase(args[0])) {
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
