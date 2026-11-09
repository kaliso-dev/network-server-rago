package gg.rago.practice;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * The bots of the trainings: a zombie the size of a player, in leather of its colour with a
 * sword, that walks to the player and hits them. Its game owns it, so that it only hurts and is
 * only hurt as the game says, never burns, never despawns and drops nothing.
 */
final class Bots {

    private Bots() {
    }

    /** A bot at this place, this fast (0: a zombie's pace, 1: Speed I, 2: Speed II). */
    static Zombie spawn(PracticeGame game, Location at, String name, Color colour, int speed) {
        // a bot far from the player may stand in a chunk not loaded yet: no entity goes into one
        at.getChunk().load();
        Zombie zombie = at.getWorld().spawn(at, Zombie.class);
        zombie.setBaby(false);
        zombie.setVillager(false);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCustomName(ChatColor.RED + name);
        zombie.setCustomNameVisible(true);
        EntityEquipment equipment = zombie.getEquipment();
        equipment.setHelmet(PracticeGame.leatherPiece(Material.LEATHER_HELMET, colour));
        equipment.setChestplate(PracticeGame.leatherPiece(Material.LEATHER_CHESTPLATE, colour));
        equipment.setLeggings(PracticeGame.leatherPiece(Material.LEATHER_LEGGINGS, colour));
        equipment.setBoots(PracticeGame.leatherPiece(Material.LEATHER_BOOTS, colour));
        equipment.setItemInHand(new ItemStack(Material.STONE_SWORD));
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInHandDropChance(0f);
        if (speed > 0) {
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speed - 1, false, false), true);
        }
        game.host.own(zombie, game);
        return zombie;
    }

    /** The bot goes after this player. */
    static void chase(Zombie bot, Player player) {
        if (bot != null && bot.isValid() && player != null && !player.equals(bot.getTarget())) {
            bot.setTarget(player);
        }
    }

    /** The bot is gone. */
    static void remove(PracticeGame game, Zombie bot) {
        if (bot != null) {
            game.host.disown(bot);
            bot.remove();
        }
    }
}
