package gg.rago.buildtools;

import java.util.Collections;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * {@code /vitesse} (flying speed) and {@code /nv} (night vision), the two comforts of building.
 * Only the flying speed changes: the walking speed stays Minecraft's, so the KB tests are not
 * affected.
 */
final class BuilderCommands implements TabExecutor {

    static final int MIN_SPEED = 1;
    static final int MAX_SPEED = 10;
    private static final String PREFIX = ChatColor.RED + "[Rago] " + ChatColor.RESET;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        if ("vitesse".equals(command.getName())) {
            speed(player, label, args);
        } else {
            nightVision(player);
        }
        return true;
    }

    private static void speed(Player player, String label, String[] args) {
        Integer speed = args.length == 1 ? parseSpeed(args[0]) : null;
        if (speed == null) {
            player.sendMessage(PREFIX + "Usage: /" + label + " <" + MIN_SPEED + "-" + MAX_SPEED
                    + "> (1 is the normal flying speed)");
            return;
        }
        player.setFlySpeed(flySpeed(speed));
        player.sendMessage(PREFIX + "Flying speed: " + speed + ".");
    }

    /** The value Minecraft expects for a speed from 1 (its normal flying speed, 0.1) to 10 (1.0). */
    static float flySpeed(int speed) {
        return speed / 10f;
    }

    /** The speed typed by the player, or null when it is not a whole number from 1 to 10. */
    static Integer parseSpeed(String text) {
        try {
            int speed = Integer.parseInt(text.trim());
            return speed >= MIN_SPEED && speed <= MAX_SPEED ? speed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void nightVision(Player player) {
        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendMessage(PREFIX + "Night vision off.");
        } else {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, true, false));
            player.sendMessage(PREFIX + "Night vision on (type /nv to remove it).");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
