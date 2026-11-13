package gg.rago.practice;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /center: back to the centre of Arena Free (see {@link BlockArena#centre}). */
final class CenterCommand implements CommandExecutor {

    private final RagoPractice plugin;

    CenterCommand(RagoPractice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can do that.");
            return true;
        }
        Player player = (Player) sender;
        PracticeGames games = plugin.games();
        PracticeGame game = games == null ? null : games.gameOf(player.getUniqueId());
        if (game instanceof BlockArena && ((BlockArena) game).free()) {
            ((BlockArena) game).centre(player);
            return true;
        }
        player.sendMessage(ChatColor.RED + "/" + label + " only works in Arena Free: it takes you back to its centre.");
        return true;
    }
}
