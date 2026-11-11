package gg.rago.practice;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

/**
 * /speed: the Speed II of the boxing off, or back on. In a boxing game it counts at once; anywhere
 * else it is kept for the player's next boxing games (until a restart). With a number, it is the
 * flying speed of the builders: /speed was an alias of RagoBuildTools' /vitesse, which this
 * command takes over, so it hands it on.
 */
final class SpeedCommand implements CommandExecutor {

    /** The command of RagoBuildTools that /speed with a number stands for. */
    static final String FLYING_SPEED = "vitesse";

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
        if (args.length > 0) {
            PluginCommand flying = plugin.getServer().getPluginCommand(FLYING_SPEED);
            if (flying != null && flying.getPlugin() != plugin) {
                return flying.execute(sender, label, args);
            }
            sender.sendMessage(ChatColor.RED + "/" + label + " takes nothing: it takes the boxing speed off, or gives it back.");
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
