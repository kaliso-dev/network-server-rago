package gg.rago.practice;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /speed: the Speed II of the boxing off, or back on. In a boxing game it counts at once; anywhere
 * else it is kept for the player's next boxing games (until a restart).
 */
final class SpeedCommand implements CommandExecutor {

    private final RagoPractice plugin;

    SpeedCommand(RagoPractice plugin) {
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
        if (game instanceof Boxing) {
            ((Boxing) game).speed(player);
            return true;
        }
        boolean on = Boxing.toggleSpeed(player.getUniqueId());
        player.sendMessage(line(on) + ChatColor.GRAY + " It counts from your next boxing game.");
        return true;
    }

    static String line(boolean on) {
        return ChatColor.YELLOW + "Boxing speed: " + (on ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF") + ChatColor.YELLOW + "."
                + ChatColor.GRAY + " /speed to " + (on ? "take it off." : "get it back.");
    }
}
