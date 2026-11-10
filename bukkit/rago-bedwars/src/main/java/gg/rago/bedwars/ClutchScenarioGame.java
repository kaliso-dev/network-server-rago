package gg.rago.bedwars;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Clutch Scenario: a real Ranked Bedwars game (any map: solo, doubles, 3s, 4s), the two teams on
 * opposite islands, where one team, drawn at random, has lost its bed already and has to clutch:
 * each of its players has one life. The other keeps its bed, under a ranked defense already built
 * (end stone, glass, wood, wool). Everyone starts on their island in iron armour with a stone
 * sword, no enchantment, no emerald, nothing else, the forges full of iron and gold. Then it is a
 * game as any other: the shop, the upgrades, the diamonds and emeralds, until one team is left.
 * Main thread only.
 */
final class ClutchScenarioGame extends BedwarsGame {

    /** The defense on the bed of the team that keeps it, from the bed out: a ranked one. */
    static final Material[] DEFENSE = {Material.ENDER_STONE, Material.STAINED_GLASS, Material.WOOD, Material.WOOL};
    /** Iron armour from the start (the tier of {@link Gamer#armor}). */
    static final int ARMOUR_TIER = 2;

    /** The team without its bed. */
    private final Team bedless;

    ClutchScenarioGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides);
        bedless = teams.get(ThreadLocalRandom.current().nextInt(teams.size()));
    }

    /** Iron armour from the start, and what they buy over it. */
    @Override
    Gamer newGamer(Player player, Team team) {
        Gamer gamer = super.newGamer(player, team);
        gamer.armor = ARMOUR_TIER;
        return gamer;
    }

    /** The bed of one team gone, a ranked defense on the others', the forges full. */
    @Override
    void setUp() {
        destroyBed(bedless);
        for (Team team : teams) {
            if (team != bedless && team.points.bed != null) {
                Defense.build(instance, team.points.bed.blockIn(instance.world()), DEFENSE, team.color.woolData());
            }
            if (team.upgrades.iron != null) {
                team.upgrades.iron.fill();
            }
            if (team.upgrades.gold != null) {
                team.upgrades.gold.fill();
            }
        }
    }

    /** A game's kit (the armour, the tools bought), with a stone sword for the wooden one. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        super.giveKit(player, gamer);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.WOOD_SWORD) {
                item.setType(Material.STONE_SWORD);
                inventory.setItem(slot, item);
            }
        }
        player.updateInventory();
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        String head = ChatColor.RED + "" + ChatColor.BOLD + modeName + ChatColor.RED + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.RED + ": you are in the " + gamer.team.color.coloured() + ChatColor.RED
                + " team. ";
        if (gamer.team == bedless) {
            player.sendMessage(head + "Your bed is gone already: one life, clutch! The " + other(gamer.team).color.coloured()
                    + ChatColor.RED + " bed is under a ranked defense.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "NO BED", ChatColor.WHITE + "One life: clutch!");
        } else {
            player.sendMessage(head + "You keep your bed, under a ranked defense; the " + bedless.color.coloured()
                    + ChatColor.RED + " team has lost theirs: one final kill each.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "BED ALIVE", ChatColor.WHITE + "They have one life each");
        }
        player.sendMessage(ChatColor.GRAY + "Iron armour and a stone sword, your forge full of iron and gold: then it is a "
                + "real game. /spawn takes you back to the lobby.");
    }

    /** The first other team than this one. */
    private Team other(Team team) {
        for (Team candidate : teams) {
            if (candidate != team) {
                return candidate;
            }
        }
        return team;
    }
}
