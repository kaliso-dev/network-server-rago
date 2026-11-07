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
 * /spawn, /file and /lobby.
 *
 * <ul>
 *   <li>{@code /spawn}: back to the lobby spawn (from a game, through the game, which counts it as
 *       a forfeit).
 *   <li>{@code /file <mode>} joins a queue, {@code /file quitter} leaves it.
 *   <li>{@code /lobby setspawn|build|modes}: for the staff ({@code rago.lobby.admin}).
 * </ul>
 */
final class LobbyCommands implements TabExecutor {

    private static final List<String> ADMIN = Arrays.asList("setspawn", "build", "modes");

    private final RagoLobby lobby;

    LobbyCommands(RagoLobby lobby) {
        this.lobby = lobby;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (name.equals("lobby") && args.length > 0 && args[0].equalsIgnoreCase("modes")) {
            listModes(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }
        Player player = (Player) sender;
        switch (name) {
            case "spawn":
                lobby.goToSpawn(player);
                return true;
            case "file":
                return queue(player, args);
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
        if (args[0].equalsIgnoreCase("quitter")) {
            if (lobby.queues().leave(player, true) == null) {
                player.sendMessage(ChatColor.GRAY + "Tu n'es dans aucune file.");
            }
            return true;
        }
        GameMode mode = lobby.mode(args[0].toLowerCase(java.util.Locale.ROOT));
        if (mode == null) {
            player.sendMessage(ChatColor.RED + "Mode inconnu. Les modes : " + modeIds());
            return true;
        }
        if (!lobby.state().isInLobby(player)) {
            player.sendMessage(ChatColor.RED + "Reviens au lobby (/spawn) pour entrer dans une file.");
            return true;
        }
        lobby.queues().join(player, mode);
        return true;
    }

    private boolean admin(Player player, String[] args) {
        if (!player.hasPermission("rago.lobby.admin")) {
            player.sendMessage(LobbyListener.noPermission());
            return true;
        }
        if (args.length != 1) {
            return false;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
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

    private void listModes(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Modes du lobby :");
        for (GameMode mode : lobby.settings().catalog().all()) {
            boolean playable = lobby.queues().isPlayable(mode.id());
            sender.sendMessage(ChatColor.GRAY + " " + mode.id() + " : " + mode.name() + ChatColor.GRAY + " (" + mode.format()
                    + (mode.ranked() ? ", classé" : "") + ") "
                    + (playable ? ChatColor.GREEN + "ouvert, " + lobby.queues().waiting(mode.id()) + " en file"
                            : ChatColor.DARK_GRAY + "bientôt"));
        }
    }

    private String modeIds() {
        List<String> ids = new ArrayList<>();
        for (GameMode mode : lobby.settings().catalog().all()) {
            ids.add(mode.id());
        }
        return String.join(", ", ids);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>();
        if (command.getName().equals("file")) {
            options.add("quitter");
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
