package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * /spawn, /queue (alias /file), /duel, /hypixel and /lobby. The French words of the first version
 * (quitter, accepter, refuser, retirer) still work.
 *
 * <ul>
 *   <li>{@code /spawn}: back to the lobby spawn (from a game, through the game, which counts it as
 *       a forfeit).
 *   <li>{@code /queue <mode>} joins a queue, {@code /queue leave} leaves it.
 *   <li>{@code /hypixel import [name]} copies the Bed Wars shop and hotbar manager of a Hypixel
 *       account (the player's own without a name), {@code /hypixel shop} shows it.
 *   <li>{@code /lobby setspawn|build|modes|points}, {@code /lobby point <name>|remove <name>|tp <name>}:
 *       for the staff ({@code rago.lobby.admin}).
 * </ul>
 */
final class LobbyCommands implements TabExecutor {

    private static final List<String> ADMIN = Arrays.asList("setspawn", "build", "modes", "point", "points");

    private final RagoLobby lobby;

    LobbyCommands(RagoLobby lobby) {
        this.lobby = lobby;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (name.equals("hypixel") && args.length == 0) {
            return false;
        }
        if (name.equals("lobby") && args.length > 0 && args[0].equalsIgnoreCase("modes")) {
            listModes(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        switch (name) {
            case "spawn":
                lobby.goToSpawn(player);
                return true;
            case "queue":
                return queue(player, args);
            case "duel":
                return duel(player, args);
            case "hypixel":
                return hypixel(player, args);
            case "lobby":
                return admin(player, args);
            default:
                return false;
        }
    }

    private boolean queue(Player player, String[] args) {
        if (args.length != 1) {
            return false;
        }
        if (args[0].equalsIgnoreCase("leave") || args[0].equalsIgnoreCase("quitter")) {
            if (lobby.queues().leave(player, true) == null) {
                player.sendMessage(ChatColor.GRAY + "You are not in a queue.");
            }
            return true;
        }
        GameMode mode = lobby.mode(args[0].toLowerCase(java.util.Locale.ROOT));
        if (mode == null) {
            player.sendMessage(ChatColor.RED + "Unknown mode. The modes: " + modeIds());
            return true;
        }
        if (!lobby.state().isInLobby(player)) {
            player.sendMessage(ChatColor.RED + "Go back to the lobby (/spawn) to join a queue.");
            return true;
        }
        lobby.queues().join(player, mode);
        return true;
    }

    private boolean duel(Player player, String[] args) {
        if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("accepter"))) {
            lobby.duels().accept(player, args[1]);
            return true;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("refuser"))) {
            lobby.duels().refuse(player, args[1]);
            return true;
        }
        if (args.length == 0 || args.length > 2) {
            return false;
        }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (!lobby.duels().canChallenge(player, target)) {
            return true;
        }
        if (args.length == 1) {
            lobby.menu().openDuel(player, target);
            return true;
        }
        GameMode mode = lobby.mode(args[1].toLowerCase(java.util.Locale.ROOT));
        if (mode == null) {
            player.sendMessage(ChatColor.RED + "Unknown mode.");
            return true;
        }
        lobby.duels().challenge(player, target, mode);
        return true;
    }

    /** /hypixel import [name]: copies a Bed Wars shop from Hypixel. /hypixel shop: shows it. */
    private boolean hypixel(Player player, String[] args) {
        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("import") && args.length <= 2) {
            lobby.hypixel().importFor(player, args.length == 2 ? args[1] : null);
            return true;
        }
        if (sub.equals("shop") && args.length == 1) {
            HypixelProfile profile = lobby.hypixel().get(player.getUniqueId());
            if (profile == null) {
                player.sendMessage(ChatColor.GRAY + "You have not imported a Hypixel shop yet: /hypixel import, or"
                        + " /hypixel import <name> for another account.");
            } else {
                HypixelMenu.open(player, profile);
            }
            return true;
        }
        return false;
    }

    private boolean admin(Player player, String[] args) {
        if (!player.hasPermission("rago.lobby.admin")) {
            player.sendMessage(LobbyListener.noPermission());
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("point")) {
            return point(player, args);
        }
        if (args.length != 1) {
            return false;
        }
        switch (sub) {
            case "points":
                listPoints(player);
                return true;
            case "setspawn":
                lobby.setSpawn(player);
                return true;
            case "build":
                lobby.toggleBuild(player);
                return true;
            default:
                return false;
        }
    }

    private boolean point(Player player, String[] args) {
        if (args.length == 2) {
            lobby.setPoint(player, args[1].toLowerCase(java.util.Locale.ROOT));
            return true;
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("retirer"))) {
            lobby.removePoint(player, args[2].toLowerCase(java.util.Locale.ROOT));
            return true;
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("tp")) {
            lobby.goToPoint(player, args[2].toLowerCase(java.util.Locale.ROOT));
            return true;
        }
        player.sendMessage(ChatColor.RED + "/lobby point <name>, /lobby point remove <name>, /lobby point tp <name>");
        return true;
    }

    private void listPoints(Player player) {
        java.util.Map<String, SpawnPoint> all = lobby.points().all();
        if (all.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No point yet. /lobby point <name> places one where you stand.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Lobby points:");
        for (java.util.Map.Entry<String, SpawnPoint> entry : all.entrySet()) {
            SpawnPoint p = entry.getValue();
            LobbyNpc npc = lobby.settings().npc(entry.getKey());
            player.sendMessage(ChatColor.GRAY + " " + entry.getKey() + ": " + (int) Math.floor(p.x()) + " "
                    + (int) Math.floor(p.y()) + " " + (int) Math.floor(p.z())
                    + (npc == null ? ChatColor.DARK_GRAY + " (no NPC)" : ChatColor.GREEN + " → " + npc.name()));
        }
        List<String> missing = new ArrayList<>();
        for (LobbyNpc npc : lobby.settings().npcs()) {
            if (!all.containsKey(npc.point())) {
                missing.add(npc.point());
            }
        }
        if (!missing.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "NPCs without a point (/lobby point <name>): " + String.join(", ", missing));
        }
    }

    private void listModes(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Lobby modes:");
        for (GameMode mode : lobby.settings().catalog().all()) {
            boolean playable = lobby.queues().isPlayable(mode.id());
            sender.sendMessage(ChatColor.GRAY + " " + mode.id() + ": " + mode.name() + ChatColor.GRAY + " (" + mode.format()
                    + (mode.ranked() ? ", ranked" : "") + ") "
                    + (playable ? ChatColor.GREEN + "open, " + lobby.queues().waiting(mode.id()) + " in queue"
                            : ChatColor.DARK_GRAY + "coming soon"));
        }
    }

    private String modeIds() {
        List<String> ids = new ArrayList<>();
        for (GameMode mode : lobby.settings().catalog().all()) {
            ids.add(mode.id());
        }
        return String.join(", ", ids);
    }

    private static boolean isAnswer(String word) {
        return Arrays.asList("accept", "deny", "accepter", "refuser").contains(word.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equals("duel")) {
            if (args.length == 1) {
                options.add("accept");
                options.add("deny");
                for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    options.add(online.getName());
                }
            } else if (args.length == 2 && !isAnswer(args[0])) {
                for (ModeDisplay display : lobby.duels().duelModes()) {
                    options.add(display.mode().id());
                }
            }
            List<String> matching = new ArrayList<>();
            String typed = args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
            for (String option : options) {
                if (option.toLowerCase(java.util.Locale.ROOT).startsWith(typed)) {
                    matching.add(option);
                }
            }
            return matching;
        }
        if (args.length != 1) {
            return Collections.emptyList();
        }
        if (command.getName().equals("hypixel")) {
            options.add("import");
            options.add("shop");
        } else if (command.getName().equals("queue")) {
            options.add("leave");
            for (GameMode mode : lobby.settings().catalog().all()) {
                options.add(mode.id());
            }
        } else if (command.getName().equals("lobby") && sender.hasPermission("rago.lobby.admin")) {
            options.addAll(ADMIN);
        }
        List<String> matching = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
                matching.add(option);
            }
        }
        return matching;
    }
}
