package gg.rago.bedwars;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Lobby Fight 1v1: the rounds of Top Bridge in the waiting lobby of the map (salle-attente: in its
 * bedwars.yml; Archway FFA has one, its glass floor high over the middle), with the kit of Lobby
 * Fight (lobby-fight-ffa: in the lobby's config.yml, that this mode plays with kit-de:, as each
 * player arranged it in the lobby's kit editor: a stone sword, 5 golden apples, 3 fireballs, 12 end
 * stone and a wooden pickaxe), Haste to mine the end stone, diamond leggings and boots with a
 * leather helmet and chestplate of the team's colour, Protection IV. The two players start face to
 * face across the middle of the lobby; one life a round, a kill or a fall off the lobby (a few
 * blocks under its floor) gives the other a point, the first to {@value #ROUNDS_TO_WIN} wins, each
 * round after a countdown of {@value #ROUND_COUNTDOWN} seconds. The blocks go in the lobby only,
 * go after {@value FfaGame#DECAY_SECONDS} seconds as in Lobby Fight FFA, and all go at each round
 * with the rollback of the map; only the blocks placed can be broken, never the lobby's. Main
 * thread only.
 */
final class LobbyFightGame extends TopBridgeGame {

    /** The countdown before each round after the first, both players frozen on their start. */
    static final int ROUND_COUNTDOWN = 3;
    /** The rounds a player must win to win the game: the first to 3. */
    static final int ROUNDS_TO_WIN = 3;

    private final LobbyFight lobby;
    /** The blocks placed go after the FFA's seconds, as in Lobby Fight FFA. */
    private final BlockDecay decay = new BlockDecay(FfaGame.DECAY_SECONDS);

    LobbyFightGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides, false);
        this.lobby = new LobbyFight(instance.map().waitingLobby(), instance.world());
    }

    @Override
    boolean playsInWaitingLobby() {
        return true;
    }

    /** No platform: the fight is in the map's waiting lobby. */
    @Override
    boolean hasTopBridge() {
        return false;
    }

    /** Nothing to build for a round: the rollback has taken the blocks placed away, and nothing is left to decay. */
    @Override
    void buildFight() {
        decay.clear();
    }

    @Override
    int pointsToWin() {
        return ROUNDS_TO_WIN;
    }

    @Override
    void placed(Player player, Block block) {
        super.placed(player, block);
        decay.placed(block, seconds);
    }

    @Override
    void everySecond() {
        super.everySecond();
        if (phase != Phase.ENDED) {
            decay.tick(instance, instance.world(), seconds);
        }
    }

    @Override
    int roundCountdown() {
        return ROUND_COUNTDOWN;
    }

    /** The beds stay on the map, far below the lobby, and nobody comes back with them. */
    @Override
    void removeUnusedBeds() {
        for (Team team : teams) {
            team.bed = false;
        }
    }

    @Override
    double fallLine() {
        return lobby.fallLine();
    }

    @Override
    Location watchPoint() {
        return lobby.watchPoint();
    }

    /** Face to face across the middle of the lobby, teammates side by side. */
    @Override
    Location spawnOf(Gamer gamer) {
        int team = teams.indexOf(gamer.team);
        int index = Math.max(0, gamer.team.members.indexOf(gamer.id));
        return lobby.start(team == 1 ? 1 : 0, index, Math.max(1, gamer.team.members.size()));
    }

    @Override
    String cannotBuildAt(Block block) {
        return lobby.cannotBuildAt(block);
    }

    @Override
    String roundsRule() {
        return "Rounds of one life in the waiting lobby of the map: kill the other or knock them out of it. The first to "
                + pointsToWin() + " points wins.";
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + modeName + ChatColor.YELLOW + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.YELLOW + ": you are " + gamer.team.color.coloured() + ChatColor.YELLOW
                + ". " + roundsRule());
        player.sendMessage(ChatColor.GRAY + "The kit of Lobby Fight, as you arranged it in the lobby's kit editor, with Haste."
                + " Your blocks go after " + decay.seconds() + " seconds. /spawn takes you back to the lobby.");
    }

    /** The kit of Lobby Fight, Haste and the fight armour in the team's colour, at each round. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            for (ItemStack item : LobbyFight.fallbackKit()) {
                inventory.addItem(kitItem(item, gamer));
            }
        }
        fightArmour(player, gamer.team.color.leather());
        LobbyFight.haste(player);
        player.updateInventory();
    }
}
