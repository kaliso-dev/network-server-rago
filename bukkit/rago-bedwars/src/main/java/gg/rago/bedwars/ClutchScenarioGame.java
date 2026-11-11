package gg.rago.bedwars;

import gg.rago.game.BedDefense;
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
 * each of its players has one life. The other keeps its bed, under the owner's duo defense already
 * built (end stone, then wool: {@link BedDefense#DUO}). Everyone starts on their island in iron armour with a stone
 * sword, no enchantment, no emerald, nothing else, the forges full of iron and gold (every forge
 * of the map, the empty islands' too: forges-partout in config.yml). The game is
 * half played already, as a real one at 18 minutes: the diamond generators at Tier III, the
 * emerald ones at Tier II, Emerald III next, then the end of the beds. Then it is a game as any
 * other: the shop, the upgrades, the diamonds and emeralds, until one team is left. Main thread
 * only.
 */
final class ClutchScenarioGame extends BedwarsGame {

    /** The defense on the bed of the team that keeps it: the owner's duo one, end stone then wool. */
    static final List<String[]> DEFENSE = BedDefense.DUO;
    /** Iron armour from the start (the tier of {@link Gamer#armor}). */
    static final int ARMOUR_TIER = 2;
    /** The tiers of the diamond and emerald generators at the start: a game half played. */
    static final int DIAMOND_TIER = 3;
    static final int EMERALD_TIER = 2;

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

    /**
     * The bed of one team gone, the duo defense on the others', the forges full; the clock where
     * the diamonds reach Tier III, with the emeralds at Tier II.
     */
    @Override
    void setUp() {
        seconds = settings.diamondIII();
        for (Generator generator : diamonds) {
            generator.upgrade(DIAMOND_TIER, settings.diamond().get(DIAMOND_TIER - 1));
        }
        for (Generator generator : emeralds) {
            generator.upgrade(EMERALD_TIER, settings.emerald().get(EMERALD_TIER - 1));
        }
        destroyBed(bedless);
        for (Team team : teams) {
            if (team != bedless && team.points.bed != null) {
                Defense.build(instance, team.points.bed.blockIn(instance.world()), DEFENSE, team.color.woolData());
            }
        }
        // every forge full: the teams' and, with forges-partout, those of the islands nobody plays
        for (Generator forge : forges) {
            forge.fill();
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
                    + ChatColor.RED + " bed is under a defense of end stone and wool.");
            player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "NO BED", ChatColor.WHITE + "One life: clutch!");
        } else {
            player.sendMessage(head + "You keep your bed, under a defense of end stone and wool; the " + bedless.color.coloured()
                    + ChatColor.RED + " team has lost theirs: one final kill each.");
            player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "BED ALIVE", ChatColor.WHITE + "They have one life each");
        }
        player.sendMessage(ChatColor.GRAY + "Iron armour and a stone sword, your forge full of iron and gold, the game "
                + "half played (Diamond III, Emerald II): then it is a real game. /spawn takes you back to the lobby.");
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
