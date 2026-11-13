package gg.rago.bedwars;

import gg.rago.lobby.api.VoidGenerator;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * /bw: sets up the Bed Wars maps, in the map's own world. Go there with /bw edit &lt;map&gt; (or
 * /monde tp), stand where a point goes and type its command: /bw spawn red, /bw bed red (looking at
 * the bed), /bw generator red, /bw shop red, /bw upgrades red, then the same for the other teams,
 * /bw diamond and /bw emerald on each of those generators, /bw spectators, and /bw enable. Each
 * change is saved at once in the world's bedwars.yml; the games already running keep the map as it
 * was. /bw lobbyfight, on the floor of the map's waiting lobby, makes Lobby Fight play there. For
 * the staff (rago.bedwars.admin).
 */
final class MapCommands implements CommandExecutor, TabCompleter {

    static final String PERMISSION = "rago.bedwars.admin";
    private static final List<String> SUBCOMMANDS = Arrays.asList("maps", "info", "edit", "spawn", "bed", "generator",
            "shop", "upgrades", "diamond", "emerald", "spectators", "remove", "height", "floor", "void", "lobbyfight", "enable",
            "disable");
    private static final List<String> TEAM_COMMANDS = Arrays.asList("spawn", "bed", "generator", "shop", "upgrades");
    /** How far from the player /bw remove diamond and /bw remove emerald look for the generator. */
    private static final double REMOVE_RADIUS = 5.0;
    /** The most blocks /bw lobbyfight takes for the floor of a waiting lobby. */
    private static final int MAX_FLOOR_BLOCKS = 8000;

    private final RagoBedwars plugin;

    MapCommands(RagoBedwars plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "This command is for the staff.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "maps":
                maps(sender);
                return true;
            case "info":
                info(sender, args);
                return true;
            case "help":
                help(sender, label);
                return true;
            default:
                break;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "In game only: stand in the map's world.");
            return true;
        }
        Player player = (Player) sender;
        if (sub.equals("edit")) {
            edit(player, args);
            return true;
        }
        World world = player.getWorld();
        if (plugin.instances().isGameWorld(world)) {
            player.sendMessage(ChatColor.RED + "This is a copy where a game is played. Go to the map itself: /" + label
                    + " edit " + world.getName().replaceFirst("^" + Instances.PREFIX, "").replaceFirst("_\\d+$", ""));
            return true;
        }
        BedwarsMap known = plugin.maps().get(world.getName());
        BedwarsMap map = known != null ? known : new BedwarsMap(world.getName());
        String done = change(player, map, sub, args, label);
        if (done == null) {
            return true;
        }
        if (known == null) {
            plugin.maps().add(map);
        }
        plugin.maps().save(map);
        plugin.instances().discard(map.world());
        player.sendMessage(ChatColor.GREEN + done);
        List<String> missing = map.missing();
        if (!missing.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Still to place: " + ChatColor.WHITE + String.join(ChatColor.GRAY + "; "
                    + ChatColor.WHITE, missing));
        } else if (!map.enabled()) {
            player.sendMessage(ChatColor.YELLOW + "Everything is placed: /" + label + " enable to play it.");
        }
        return true;
    }

    /** Applies the change; its message, or null when nothing changed (the reason was given). */
    private String change(Player player, BedwarsMap map, String sub, String[] args, String label) {
        Location here = player.getLocation();
        if (TEAM_COMMANDS.contains(sub)) {
            TeamColor color = args.length < 2 ? null : TeamColor.byId(args[1]);
            if (color == null) {
                player.sendMessage(ChatColor.RED + "/" + label + " " + sub + " <team>: " + TeamColor.ids());
                return null;
            }
            BedwarsMap.Team team = map.team(color);
            switch (sub) {
                case "spawn":
                    team.spawn = MapPoint.standing(here);
                    return color.displayName() + " spawn: " + team.spawn + ", looking where you look.";
                case "bed":
                    Block bed = bedNear(player);
                    if (bed == null) {
                        player.sendMessage(ChatColor.RED + "Look at the bed (or stand on it), then type it again.");
                        return null;
                    }
                    team.bed = MapPoint.block(bed);
                    return color.displayName() + " bed: " + team.bed + ".";
                case "generator":
                    team.generator = MapPoint.centreOf(here);
                    return color.displayName() + " generator (iron and gold): " + team.generator + ".";
                case "shop":
                    team.shop = MapPoint.standing(here);
                    return color.displayName() + " item shop: " + team.shop + ", facing where you look.";
                default:
                    team.upgrades = MapPoint.standing(here);
                    return color.displayName() + " team upgrades: " + team.upgrades + ", facing where you look.";
            }
        }
        switch (sub) {
            case "diamond":
                map.diamonds().add(MapPoint.centreOf(here));
                return "Diamond generator " + map.diamonds().size() + ": " + MapPoint.centreOf(here) + ".";
            case "emerald":
                map.emeralds().add(MapPoint.centreOf(here));
                return "Emerald generator " + map.emeralds().size() + ": " + MapPoint.centreOf(here) + ".";
            case "spectators":
                map.setSpectators(new MapPoint(here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch()));
                return "The dead and the spectators look from here.";
            case "remove":
                return remove(player, map, args, label);
            case "height":
            case "void":
                return height(player, map, sub, args, label);
            case "floor":
                return floor(player, map, args, label);
            case "lobbyfight":
                return waitingLobby(player, map, args, label);
            case "enable":
                if (!map.missing().isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Not yet: " + String.join("; ", map.missing()));
                    return null;
                }
                map.setEnabled(true);
                return map.world() + " is enabled: games of " + map.sides()
                        + " teams or fewer can be played on it.";
            case "disable":
                map.setEnabled(false);
                return map.world() + " is disabled: no new game on it.";
            default:
                help(player, label);
                return null;
        }
    }

    private String remove(Player player, BedwarsMap map, String[] args, String label) {
        String what = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "diamonds":
                map.diamonds().clear();
                return "No more diamond generators.";
            case "emeralds":
                map.emeralds().clear();
                return "No more emerald generators.";
            case "diamond":
            case "emerald":
                List<MapPoint> points = what.equals("diamond") ? map.diamonds() : map.emeralds();
                Location here = player.getLocation();
                for (Iterator<MapPoint> it = points.iterator(); it.hasNext();) {
                    MapPoint point = it.next();
                    double dx = point.x() - here.getX();
                    double dy = point.y() - here.getY();
                    double dz = point.z() - here.getZ();
                    if (dx * dx + dy * dy + dz * dz <= REMOVE_RADIUS * REMOVE_RADIUS) {
                        it.remove();
                        return "The " + what + " generator at " + point + " is removed.";
                    }
                }
                player.sendMessage(ChatColor.RED + "No " + what + " generator within " + (int) REMOVE_RADIUS
                        + " blocks of you.");
                return null;
            default:
                TeamColor color = TeamColor.byId(what);
                if (color == null) {
                    player.sendMessage(ChatColor.RED + "/" + label + " remove <team>|diamond|emerald|diamonds|emeralds");
                    return null;
                }
                if (!map.removeTeam(color)) {
                    player.sendMessage(ChatColor.RED + "This map has no " + color.id() + " team.");
                    return null;
                }
                return "The " + color.id() + " team is removed from " + map.world() + ".";
        }
    }

    private String height(Player player, BedwarsMap map, String sub, String[] args, String label) {
        boolean build = sub.equals("height");
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/" + label + " " + sub + " <y>|auto (now " + (build ? map.buildLimit()
                    : map.voidY()) + ")");
            return null;
        }
        if (args[1].equalsIgnoreCase("auto")) {
            if (build) {
                map.setBuildLimit(0);
            } else {
                map.setVoidY(Integer.MIN_VALUE);
            }
        } else {
            int y;
            try {
                y = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                y = -1;
            }
            if (y < (build ? 1 : -64) || y > 255) {
                player.sendMessage(ChatColor.RED + "A height from " + (build ? 1 : -64) + " to 255, or auto.");
                return null;
            }
            if (build) {
                map.setBuildLimit(y);
            } else {
                map.setVoidY(y);
            }
        }
        return build ? "Players build up to y " + map.buildLimit() + "." : "Players die below y " + map.voidY() + ".";
    }

    /** /bw floor: the lowest block players may place (hauteur-min), or none. */
    private String floor(Player player, BedwarsMap map, String[] args, String label) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/" + label + " floor <y>|none (now " + (map.hasFloorY() ? "y " + map.floorY()
                    : "none") + ")");
            return null;
        }
        if (args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("auto")) {
            map.setFloorY(Integer.MIN_VALUE);
            return "Players place blocks down to the void.";
        }
        int y;
        try {
            y = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            y = -1;
        }
        if (y < 0 || y > 255) {
            player.sendMessage(ChatColor.RED + "A height from 0 to 255, or none.");
            return null;
        }
        map.setFloorY(y);
        return "Players place blocks down to y " + y + ".";
    }

    /**
     * /bw lobbyfight, standing on the floor of the map's waiting lobby: Lobby Fight is played there.
     * The floor is the block under the player's feet and every block of that height joined to it;
     * the lobby is the round around them (see {@link BedwarsMap.WaitingLobby#around}). /bw
     * lobbyfight remove: no more.
     */
    private String waitingLobby(Player player, BedwarsMap map, String[] args, String label) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            if (map.waitingLobby() == null) {
                player.sendMessage(ChatColor.RED + map.world() + " has no waiting lobby.");
                return null;
            }
            map.setWaitingLobby(null);
            return "No more waiting lobby: Lobby Fight is no longer played on " + map.world() + ".";
        }
        Location feet = player.getLocation();
        Block under = feet.getWorld().getBlockAt(feet.getBlockX(), (int) Math.floor(feet.getY() - 0.01), feet.getBlockZ());
        if (!under.getType().isSolid()) {
            player.sendMessage(ChatColor.RED + "Stand on the floor of the waiting lobby (not flying), then type /" + label
                    + " lobbyfight again.");
            return null;
        }
        List<int[]> floor = floorAround(under);
        if (floor == null) {
            player.sendMessage(ChatColor.RED + "This floor goes on too far (more than " + MAX_FLOOR_BLOCKS + " blocks, or "
                    + (int) BedwarsMap.WaitingLobby.MAX_RADIUS + " from you): stand on the floor of the waiting lobby.");
            return null;
        }
        BedwarsMap.WaitingLobby lobby = BedwarsMap.WaitingLobby.around(floor, under.getY());
        map.setWaitingLobby(lobby);
        return "Waiting lobby of " + map.world() + ": " + lobby + " (" + floor.size() + " blocks of floor). Lobby Fight is "
                + "played there, on the maps of its modes (config.yml).";
    }

    /**
     * The blocks of the floor (x, z) joined to this one at its height, side by side; null when
     * they go on too far for a waiting lobby.
     */
    private static List<int[]> floorAround(Block start) {
        World world = start.getWorld();
        int y = start.getY();
        List<int[]> floor = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> next = new java.util.ArrayDeque<>();
        next.add(new int[] {start.getX(), start.getZ()});
        seen.add(key(start.getX(), start.getZ()));
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!next.isEmpty()) {
            int[] block = next.poll();
            floor.add(block);
            if (floor.size() > MAX_FLOOR_BLOCKS) {
                return null;
            }
            for (int[] side : sides) {
                int x = block[0] + side[0];
                int z = block[1] + side[1];
                if (Math.abs(x - start.getX()) > BedwarsMap.WaitingLobby.MAX_RADIUS
                        || Math.abs(z - start.getZ()) > BedwarsMap.WaitingLobby.MAX_RADIUS) {
                    if (world.getBlockAt(x, y, z).getType().isSolid()) {
                        return null;
                    }
                    continue;
                }
                if (seen.add(key(x, z)) && world.getBlockAt(x, y, z).getType().isSolid()) {
                    next.add(new int[] {x, z});
                }
            }
        }
        return floor;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** The bed the player looks at, or stands on or in. */
    private static Block bedNear(Player player) {
        Block target = player.getTargetBlock((Set<Material>) null, 6);
        if (target != null && target.getType() == Material.BED_BLOCK) {
            return target;
        }
        Block feet = player.getLocation().getBlock();
        for (Block block : new Block[] {feet, feet.getRelative(BlockFace.DOWN)}) {
            if (block.getType() == Material.BED_BLOCK) {
                return block;
            }
        }
        return null;
    }

    private void edit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "/bw edit <map>: " + String.join(", ", mapFolders()));
            return;
        }
        String name = args[1];
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (!name.matches("[A-Za-z0-9_-]{1,32}") || !new File(folder, "level.dat").isFile() || Instances.isCopy(folder)) {
            player.sendMessage(ChatColor.RED + "No map " + name + " in the server's folder. Copy its world folder there "
                    + "(and import it with /monde import), or choose one of: " + String.join(", ", mapFolders()));
            return;
        }
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world == null) {
            player.sendMessage(ChatColor.RED + "The world " + name + " did not load: see the console.");
            return;
        }
        BedwarsMap map = plugin.maps().get(name);
        Location to = map != null && !map.teams().isEmpty() ? map.spectatorPoint().in(world) : world.getSpawnLocation();
        player.teleport(to);
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage(ChatColor.GREEN + "You are on " + name + ". " + ChatColor.GRAY + "/bw info for what it has, "
                + "/bw spawn <team> and the others to place its points.");
    }

    private void maps(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Bed Wars maps " + ChatColor.GRAY + "(" + plugin.host().running()
                + " games running, " + plugin.instances().busy() + "/" + plugin.instances().loaded() + " game worlds busy)");
        if (plugin.maps().all().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "None yet: /bw edit <map>, then /bw spawn <team>...");
        }
        for (BedwarsMap map : plugin.maps().all()) {
            String state = map.playable() ? ChatColor.GREEN + "playable" : !map.missing().isEmpty()
                    ? ChatColor.YELLOW + "incomplete" : ChatColor.RED + "disabled";
            sender.sendMessage(ChatColor.WHITE + " " + map.world() + ChatColor.GRAY + ": " + state + ChatColor.GRAY + ", "
                    + map.completeTeams().size() + " teams, " + map.diamonds().size() + " diamond and "
                    + map.emeralds().size() + " emerald generators");
        }
    }

    private void info(CommandSender sender, String[] args) {
        String name = args.length >= 2 ? args[1] : sender instanceof Player ? ((Player) sender).getWorld().getName() : null;
        BedwarsMap map = name == null ? null : plugin.maps().get(name);
        if (map == null) {
            sender.sendMessage(ChatColor.RED + "No Bed Wars map " + (name == null ? "" : name + " ") + "yet: /bw info <map>.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + map.world() + ChatColor.GRAY + " (" + (map.enabled() ? "enabled" : "disabled")
                + ", build limit y " + map.buildLimit() + (map.hasFloorY() ? ", blocks down to y " + map.floorY() : "")
                + ", void below y " + map.voidY() + ")");
        for (Map.Entry<TeamColor, BedwarsMap.Team> entry : map.teams().entrySet()) {
            BedwarsMap.Team team = entry.getValue();
            sender.sendMessage(" " + entry.getKey().coloured() + ChatColor.GRAY + ": spawn " + point(team.spawn) + ", bed "
                    + point(team.bed) + ", generator " + point(team.generator) + ", shop " + point(team.shop)
                    + ", upgrades " + point(team.upgrades));
        }
        sender.sendMessage(ChatColor.GRAY + " Diamonds: " + map.diamonds() + "; emeralds: " + map.emeralds()
                + "; spectators: " + (map.spectators() == null ? "automatic" : map.spectators().toString()));
        sender.sendMessage(ChatColor.GRAY + " Waiting lobby (Lobby Fight): " + (map.waitingLobby() == null
                ? "none (/bw lobbyfight on its floor)" : ChatColor.WHITE + map.waitingLobby().toString()));
        List<String> missing = map.missing();
        sender.sendMessage(missing.isEmpty() ? ChatColor.GREEN + " Nothing is missing."
                : ChatColor.YELLOW + " Missing: " + String.join("; ", missing));
    }

    private static String point(MapPoint point) {
        return point == null ? ChatColor.RED + "none" + ChatColor.GRAY : ChatColor.WHITE + point.toString() + ChatColor.GRAY;
    }

    private static void help(CommandSender sender, String label) {
        String l = "/" + label;
        sender.sendMessage(ChatColor.GOLD + "Bed Wars maps " + ChatColor.GRAY + "(in the map's world; teams: " + TeamColor.ids()
                + ")");
        sender.sendMessage(ChatColor.YELLOW + l + " maps" + ChatColor.GRAY + ": the maps and the games running");
        sender.sendMessage(ChatColor.YELLOW + l + " edit <map>" + ChatColor.GRAY + ": go to a map to set it up");
        sender.sendMessage(ChatColor.YELLOW + l + " info [map]" + ChatColor.GRAY + ": what it has, what it lacks");
        sender.sendMessage(ChatColor.YELLOW + l + " spawn|generator|shop|upgrades <team>" + ChatColor.GRAY
                + ": that point, where you stand");
        sender.sendMessage(ChatColor.YELLOW + l + " bed <team>" + ChatColor.GRAY + ": the bed you look at");
        sender.sendMessage(ChatColor.YELLOW + l + " diamond|emerald" + ChatColor.GRAY + ": one more generator, where you stand");
        sender.sendMessage(ChatColor.YELLOW + l + " spectators" + ChatColor.GRAY + ": where the dead look from");
        sender.sendMessage(ChatColor.YELLOW + l + " remove <team>|diamond|emerald|diamonds|emeralds");
        sender.sendMessage(ChatColor.YELLOW + l + " height <y>|auto" + ChatColor.GRAY + ", " + ChatColor.YELLOW
                + l + " void <y>|auto" + ChatColor.GRAY + ": the build limit, the void");
        sender.sendMessage(ChatColor.YELLOW + l + " floor <y>|none" + ChatColor.GRAY + ": the lowest block players place");
        sender.sendMessage(ChatColor.YELLOW + l + " lobbyfight [remove]" + ChatColor.GRAY
                + ": on the floor of the waiting lobby, Lobby Fight is played there");
        sender.sendMessage(ChatColor.YELLOW + l + " enable|disable" + ChatColor.GRAY + ": play it, or not");
    }

    /** The folders of the server that hold a world and are not game copies. */
    private static List<String> mapFolders() {
        List<String> names = new ArrayList<>();
        File[] folders = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                if (new File(folder, "level.dat").isFile() && !Instances.isCopy(folder)
                        && !folder.getName().startsWith(Instances.PREFIX)) {
                    names.add(folder.getName());
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (TEAM_COMMANDS.contains(sub) || sub.equals("remove")) {
                for (TeamColor color : TeamColor.values()) {
                    options.add(color.id());
                }
                if (sub.equals("remove")) {
                    options.addAll(Arrays.asList("diamond", "emerald", "diamonds", "emeralds"));
                }
            } else if (sub.equals("edit") || sub.equals("info")) {
                options.addAll(mapFolders());
            } else if (sub.equals("height") || sub.equals("void")) {
                options.add("auto");
            } else if (sub.equals("floor")) {
                options.add("none");
            } else if (sub.equals("lobbyfight")) {
                options.add("remove");
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matching = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matching.add(option);
            }
        }
        return matching;
    }
}
