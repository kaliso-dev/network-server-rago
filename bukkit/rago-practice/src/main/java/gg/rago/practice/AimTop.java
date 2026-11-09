package gg.rago.practice;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /aimtop: the best scores of the aim trainer. Without a variant, the five best of each one; with
 * one (strafe, jump or circle), the ten best of it. The player's own place follows when they are
 * further down.
 */
final class AimTop implements CommandExecutor {

    private static final String MODE = "aim-trainer";
    private static final String[] LABELS = {"Strafing target", "Strafing and jumping", "Circling target"};

    private final RagoPractice plugin;

    AimTop(RagoPractice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int only = args.length == 0 ? -1 : variant(args[0]);
        if (args.length > 0 && only < 0) {
            sender.sendMessage(ChatColor.RED + "/" + label + " [strafe|jump|circle]");
            return true;
        }
        UUID viewer = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        for (int i = 0; i < AimTrainer.VARIANTS.length; i++) {
            if (only < 0 || only == i) {
                for (String line : AimTrainer.topLines(plugin.records().book(), MODE + "." + AimTrainer.VARIANTS[i], LABELS[i],
                        viewer, only < 0 ? 5 : 10)) {
                    sender.sendMessage(line);
                }
            }
        }
        return true;
    }

    /** The variant named (its id, or its first letters), or -1. */
    static int variant(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < AimTrainer.VARIANTS.length; i++) {
            if (!wanted.isEmpty() && AimTrainer.VARIANTS[i].startsWith(wanted)) {
                return i;
            }
        }
        return -1;
    }
}
