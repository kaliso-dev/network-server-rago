package gg.rago.practice;

import gg.rago.game.ArenaGrid;
import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Plays one practice mode of the lobby: each match takes a free zone of the mode's row in the
 * arena world, built the first time it is used, and a new game of the mode in it. An open mode
 * (a free for all) has one game everyone joins and leaves when they like. Main thread only.
 */
final class PracticeHost implements MatchHost {

    /** Makes a game of the mode. */
    interface Factory {
        PracticeGame create(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams);
    }

    private final RagoPractice plugin;
    private final PracticeGames games;
    private final String modeId;
    private final Factory factory;
    private final ArenaGrid grid;
    private final int rowZ;
    private final boolean open;
    private final Set<Integer> built = new HashSet<>();
    private PracticeGame openGame;
    /** The maps copied into the zones of the mode, or null when the mode builds its own arena. */
    private MapZones zones;

    /**
     * @param rowZ the z of the mode's row of zones in the arena world
     * @param open true for one game everyone joins
     */
    PracticeHost(RagoPractice plugin, PracticeGames games, String modeId, Factory factory, ArenaGrid grid, int rowZ,
            boolean open) {
        this.plugin = plugin;
        this.games = games;
        this.modeId = modeId;
        this.factory = factory;
        this.grid = grid;
        this.rowZ = rowZ;
        this.open = open;
    }

    RagoPractice plugin() {
        return plugin;
    }

    String modeId() {
        return modeId;
    }

    /**
     * The games of this mode play on these maps, copied into its zones from now on, a few blocks
     * each tick; nothing happens without maps. Returns the zones, to stop at the end.
     */
    MapZones useMaps(List<MapZones.ZoneMap> maps) {
        if (maps.isEmpty()) {
            return null;
        }
        zones = new MapZones(games.world(), grid, RagoPractice.ARENA_Y, rowZ, maps);
        zones.start(plugin);
        return zones;
    }

    // --- MatchHost ------------------------------------------------------------------------------

    @Override
    public boolean start(MatchProposal match) {
        List<List<Player>> teams = new ArrayList<>();
        for (List<QueueEntry> team : match.teams()) {
            List<Player> players = new ArrayList<>();
            for (QueueEntry entry : team) {
                Player player = Bukkit.getPlayer(entry.player());
                if (player == null) {
                    return false;
                }
                players.add(player);
            }
            teams.add(players);
        }
        if (open && openGame != null && openGame.phase != PracticeGame.Phase.ENDED) {
            for (List<Player> team : teams) {
                for (Player player : team) {
                    openGame.join(player);
                }
            }
            return true;
        }
        int slot = zones != null ? zones.take() : grid.acquire();
        if (slot < 0) {
            return false;
        }
        // a zone of a map holds the map already: the mode builds nothing there
        Arena arena = new Arena(games.world(), slot, grid.centreX(slot), RagoPractice.ARENA_Y, rowZ, zones == null && built.add(slot));
        if (zones != null) {
            arena.onMap(zones.mapOf(slot));
        }
        PracticeGame game;
        try {
            game = factory.create(this, match.mode(), arena, teams);
        } catch (RuntimeException e) {
            grid.release(slot);
            built.remove(slot);
            throw e;
        }
        games.add(game);
        try {
            game.begin();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "A game of " + modeId + " could not start", e);
            game.cancelStart();
            finished(game);
            // built again next time, in case the build itself failed half way
            built.remove(slot);
            throw e;
        }
        if (open) {
            openGame = game;
        }
        return true;
    }

    @Override
    public void abandon(Player player) {
        PracticeGame game = games.gameOf(player.getUniqueId());
        if (game == null) {
            plugin.lobby().sendToLobby(player);
        } else {
            game.leave(player, true);
        }
    }

    // --- Games ----------------------------------------------------------------------------------

    PracticeGame gameOf(UUID player) {
        return games.gameOf(player);
    }

    /** The player no longer belongs to their game: the XP they earned there goes to the lobby. */
    void release(UUID player) {
        games.release(player);
        plugin.payXp(player);
    }

    /** The player joined this running game. */
    void joined(UUID player, PracticeGame game) {
        games.join(player, game);
    }

    /** The game is over and its players are gone: its zone is free again. */
    void finished(PracticeGame game) {
        if (games.finished(game) && grid.inUse(game.arena.slot())) {
            grid.release(game.arena.slot());
        }
        if (openGame == game) {
            openGame = null;
        }
    }

    /** This entity belongs to this game (a bot, a target): its events go to the game. */
    void own(org.bukkit.entity.Entity entity, PracticeGame game) {
        games.own(entity, game);
    }

    void disown(org.bukkit.entity.Entity entity) {
        games.disown(entity);
    }

    /** The TNT this player lit: its blast is theirs. */
    void litBy(org.bukkit.entity.Entity tnt, Player player) {
        games.litBy(tnt, player);
    }

    /** The records of the trainings. */
    Records records() {
        return plugin.records();
    }
}
