package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.Matchmaker;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The local queues: the lobby's matchmaker and the game plugins that play each mode. Once a
 * second, the matches found are handed to their {@link MatchHost}. Main thread only.
 *
 * <p>A party queues as on Hypixel: only its leader joins, for everyone. A party that fills a whole
 * match (two in a 1v1, four in a 2v2) plays a private game against itself at once, which counts in
 * no stats; a party that fits in a team waits for the others, and plays in one team.
 *
 * <p>This is the queue of the local test server, where the lobby and the games share one server.
 * On the network the same {@link Matchmaker} runs in the proxy (rago-proxy, jalon Ranked).
 */
final class QueueController {

    private final LobbySettings settings;
    private final LobbyState state;
    private final RagoLobby lobby;
    private final Logger logger;
    private final Matchmaker matchmaker;
    private final Map<String, MatchHost> hosts = new HashMap<>();
    private final Map<String, Plugin> hostPlugins = new HashMap<>();
    /** Players already told that no arena is free, so that they are told once, not every second. */
    private final Set<UUID> toldNoArena = new HashSet<>();
    private final Runnable onChange;
    private final PartyBook parties;

    QueueController(RagoLobby lobby, LobbySettings settings, LobbyState state, PartyBook parties, Runnable onChange) {
        this.lobby = lobby;
        this.settings = settings;
        this.state = state;
        this.parties = parties;
        this.logger = lobby.getLogger();
        this.matchmaker = new Matchmaker(settings.catalog().all(), settings.window());
        this.onChange = onChange;
    }

    // --- Hosts ----------------------------------------------------------------------------------

    boolean register(Plugin plugin, String modeId, MatchHost host) {
        if (settings.catalog().get(modeId) == null) {
            return false;
        }
        hosts.put(modeId, host);
        hostPlugins.put(modeId, plugin);
        logger.info("Mode " + modeId + " hosted by " + plugin.getName());
        onChange.run();
        return true;
    }

    void unregister(Plugin plugin) {
        List<String> closed = new ArrayList<>();
        for (Map.Entry<String, Plugin> entry : hostPlugins.entrySet()) {
            if (entry.getValue().equals(plugin)) {
                closed.add(entry.getKey());
            }
        }
        for (String modeId : closed) {
            hosts.remove(modeId);
            hostPlugins.remove(modeId);
            closeQueue(modeId);
        }
        if (!closed.isEmpty()) {
            onChange.run();
        }
    }

    /**
     * True if a game plugin plays this mode: its queue is open. A private game whose size follows
     * the party is open when one of the modes it plays is.
     */
    boolean isPlayable(String modeId) {
        if (hosts.containsKey(modeId)) {
            return true;
        }
        List<GameMode> targets = settings.privateRoutes().get(modeId);
        if (targets != null) {
            for (GameMode target : targets) {
                if (hosts.containsKey(target.id())) {
                    return true;
                }
            }
        }
        return false;
    }

    MatchHost hostOf(String modeId) {
        return hosts.get(modeId);
    }

    // --- Players --------------------------------------------------------------------------------

    /** The player clicked a mode in the selector, or typed /queue: any map. */
    void join(Player player, GameMode mode) {
        join(player, mode, null);
    }

    /** Joins the mode's queue for this map (null: any map, the host chooses). */
    void join(Player player, GameMode mode, String map) {
        if (state.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "You are already in a game.");
            return;
        }
        if (settings.privateRoutes().containsKey(mode.id())) {
            joinPrivate(player, mode, map);
            return;
        }
        if (!isPlayable(mode.id())) {
            player.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " is coming soon.");
            return;
        }
        PartyBook.Party party = parties.together(player.getUniqueId());
        if (party != null) {
            joinTogether(player, party, mode, map);
            return;
        }
        Matchmaker.JoinResult result = matchmaker.join(player.getUniqueId(), mode.id(), settings.defaultRating(),
                System.currentTimeMillis(), map);
        switch (result) {
            case JOINED:
            case SWITCHED:
                toldNoArena.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "You joined the queue for " + mode.name() + ChatColor.GREEN
                        + (map == null ? "" : " on " + ChatColor.WHITE + map + ChatColor.GREEN) + " ("
                        + mode.format() + ", " + matchmaker.waiting(mode.id()) + " waiting).");
                onChange.run();
                break;
            case ALREADY_WAITING:
                player.sendMessage(ChatColor.GRAY + "You are already in this queue.");
                break;
            default:
                player.sendMessage(ChatColor.RED + "This mode does not exist.");
                break;
        }
    }

    /**
     * A private game whose size follows the party: the party plays the mode of the list made for
     * its size (two players: a 1v1, four: a 2v2...), against itself, in no stats.
     */
    private void joinPrivate(Player player, GameMode mode, String map) {
        PartyBook.Party party = parties.together(player.getUniqueId());
        if (party == null) {
            Parties.boxed(player, ChatColor.LIGHT_PURPLE + mode.name() + ChatColor.LIGHT_PURPLE
                    + " is played with your party: invite your friends with " + ChatColor.WHITE + "/party invite <name>"
                    + ChatColor.LIGHT_PURPLE + ", then click again. The size of the game follows the party.");
            return;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Parties.boxed(player, ChatColor.RED + "Only the party leader can start a private game.");
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member == null || !state.isInLobby(member) || state.isInGame(member)) {
                Parties.boxed(player, ChatColor.RED + (member == null ? "A player of your party" : member.getName())
                        + " is not in the lobby: your party cannot start a game.");
                return;
            }
            players.add(member);
        }
        GameMode chosen = null;
        List<String> sizes = new ArrayList<>();
        for (GameMode target : settings.privateRoutes().get(mode.id())) {
            if (hosts.containsKey(target.id())) {
                sizes.add(String.valueOf(target.playersPerMatch()));
                if (chosen == null && target.playersPerMatch() == players.size()) {
                    chosen = target;
                }
            }
        }
        if (chosen == null) {
            Parties.boxed(player, ChatColor.RED + (sizes.isEmpty() ? mode.name() + ChatColor.RED + " is coming soon."
                    : "Your party has " + players.size() + " players: " + mode.name() + ChatColor.RED + " is played with "
                    + String.join(", ", sizes) + " players, two teams of the same size."));
            return;
        }
        startPrivate(party, players, chosen, map);
    }

    /** The leader queues the party: a private game if it fills the match, else the queue, in one team. */
    private void joinTogether(Player leader, PartyBook.Party party, GameMode mode, String map) {
        if (!party.isLeader(leader.getUniqueId())) {
            Parties.boxed(leader, ChatColor.RED + "Only the party leader can join a queue.");
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member == null || !state.isInLobby(member) || state.isInGame(member)) {
                Parties.boxed(leader, ChatColor.RED + (member == null ? "A player of your party" : member.getName())
                        + " is not in the lobby: your party cannot join a queue.");
                return;
            }
            players.add(member);
        }
        if (mode.teams() >= 2 && players.size() == mode.playersPerMatch()) {
            startPrivate(party, players, mode, map);
            return;
        }
        if (players.size() > mode.playersPerTeam()) {
            Parties.boxed(leader, ChatColor.RED + "Your party has " + players.size() + " players: too many for "
                    + mode.name() + ChatColor.RED + " (" + mode.format() + ").");
            return;
        }
        Map<UUID, Integer> ratings = new LinkedHashMap<>();
        for (Player member : players) {
            ratings.put(member.getUniqueId(), settings.defaultRating());
        }
        Matchmaker.JoinResult result = matchmaker.joinParty(party.id(), ratings, mode.id(), System.currentTimeMillis(), map);
        switch (result) {
            case JOINED:
            case SWITCHED:
                for (Player member : players) {
                    toldNoArena.remove(member.getUniqueId());
                    member.sendMessage(ChatColor.GREEN + "Your party joined the queue for " + mode.name() + ChatColor.GREEN
                            + (map == null ? "" : " on " + ChatColor.WHITE + map + ChatColor.GREEN) + " ("
                            + mode.format() + ", " + matchmaker.waiting(mode.id()) + " waiting). You will play in the same team.");
                }
                onChange.run();
                break;
            case ALREADY_WAITING:
                leader.sendMessage(ChatColor.GRAY + "Your party is already in this queue.");
                break;
            case PARTY_TOO_BIG:
                leader.sendMessage(ChatColor.RED + "Your party is too big for this mode.");
                break;
            default:
                leader.sendMessage(ChatColor.RED + "This mode does not exist.");
                break;
        }
    }

    /** The party fills the match: a private game between its players, teams dealt in turn, counted nowhere. */
    private void startPrivate(PartyBook.Party party, List<Player> players, GameMode mode, String map) {
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = new ArrayList<>();
        List<List<String>> names = new ArrayList<>();
        for (int t = 0; t < mode.teams(); t++) {
            teams.add(new ArrayList<>());
            names.add(new ArrayList<>());
        }
        for (int i = 0; i < players.size(); i++) {
            Player member = players.get(i);
            teams.get(i % mode.teams()).add(new QueueEntry(member.getUniqueId(), settings.defaultRating(), now, map, party.id()));
            names.get(i % mode.teams()).add(member.getName());
        }
        List<String> sides = new ArrayList<>();
        for (List<String> side : names) {
            sides.add(String.join(" and ", side));
        }
        for (Player member : players) {
            member.sendMessage(ChatColor.LIGHT_PURPLE + "Private game of your party in " + mode.name() + ChatColor.LIGHT_PURPLE
                    + ": " + ChatColor.WHITE + String.join(ChatColor.LIGHT_PURPLE + " vs " + ChatColor.WHITE, sides)
                    + ChatColor.LIGHT_PURPLE + ". It counts in no stats.");
        }
        startDirect(new MatchProposal(mode, teams), true);
    }

    /**
     * The players of a party changed (one joined, left, was kicked, the party was disbanded or
     * transferred): those who waited, with their party or alone, leave the queue.
     */
    void partyChanged(Collection<UUID> players) {
        for (UUID id : players) {
            GameMode mode = matchmaker.queueOf(id);
            if (mode == null) {
                continue;
            }
            List<UUID> together = matchmaker.partyOf(id);
            matchmaker.leave(id);
            for (UUID member : together) {
                toldNoArena.remove(member);
                Player online = Bukkit.getPlayer(member);
                if (online != null) {
                    online.sendMessage(ChatColor.YELLOW + "The party changed: "
                            + (together.size() > 1 ? "your party left" : "you left") + " the queue for " + mode.name()
                            + ChatColor.YELLOW + ".");
                }
            }
            onChange.run();
        }
    }

    /**
     * The maps a player may choose from for this mode, as its host offers them: empty when the mode
     * has no host, or offers no choice.
     */
    List<String> mapsOf(GameMode mode) {
        MatchHost host = hosts.get(mode.id());
        if (host == null) {
            return new ArrayList<>();
        }
        try {
            List<String> maps = host.maps(mode);
            return maps == null ? new ArrayList<>() : new ArrayList<>(maps);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + mode.id() + " could not list its maps", e);
            return new ArrayList<>();
        }
    }

    /** The map the player waits for (null: any map, or not waiting). */
    String mapOf(Player player) {
        QueueEntry entry = matchmaker.entryOf(player.getUniqueId());
        return entry == null ? null : entry.map();
    }

    /** How many players wait in this mode's queue for this very map. */
    int waitingFor(String modeId, String map) {
        return matchmaker.waitingFor(modeId, map);
    }

    /** Returns the mode left, or null if the player was not waiting. With a party, the whole party leaves. */
    GameMode leave(Player player, boolean tell) {
        List<UUID> together = matchmaker.partyOf(player.getUniqueId());
        GameMode left = matchmaker.leave(player.getUniqueId());
        toldNoArena.remove(player.getUniqueId());
        if (left != null) {
            if (tell && together.size() < 2) {
                player.sendMessage(ChatColor.YELLOW + "You left the queue for " + left.name() + ChatColor.YELLOW + ".");
            }
            if (together.size() >= 2) {
                for (UUID member : together) {
                    toldNoArena.remove(member);
                    Player online = Bukkit.getPlayer(member);
                    if (online != null && (tell || !online.equals(player))) {
                        online.sendMessage(ChatColor.YELLOW + (online.equals(player) ? "You" : player.getName())
                                + " left the queue: your party left the queue for " + left.name() + ChatColor.YELLOW + ".");
                    }
                }
            }
            onChange.run();
        }
        return left;
    }

    /** The player left the server: their party leaves the queue with them. */
    void forget(UUID player) {
        List<UUID> together = matchmaker.partyOf(player);
        GameMode left = matchmaker.leave(player);
        if (left != null) {
            for (UUID member : together) {
                Player online = Bukkit.getPlayer(member);
                if (online != null && !member.equals(player)) {
                    online.sendMessage(ChatColor.YELLOW + "A player of your party left the server: your party left the queue for "
                            + left.name() + ChatColor.YELLOW + ".");
                }
            }
            onChange.run();
        }
        toldNoArena.remove(player);
    }

    GameMode queueOf(Player player) {
        return matchmaker.queueOf(player.getUniqueId());
    }

    /** How long the player has waited, in milliseconds, or -1 if not waiting. */
    long waited(Player player, long nowMillis) {
        QueueEntry entry = matchmaker.entryOf(player.getUniqueId());
        return entry == null ? -1L : entry.waitedMillis(nowMillis);
    }

    int waiting(String modeId) {
        return matchmaker.waiting(modeId);
    }

    // --- Matches --------------------------------------------------------------------------------

    /** Once a second: starts the matches that were found. */
    void tick() {
        List<MatchProposal> matches = matchmaker.poll(System.currentTimeMillis());
        for (MatchProposal match : matches) {
            start(match);
        }
        if (!matches.isEmpty()) {
            onChange.run();
        }
    }

    private void start(MatchProposal match) {
        start(match, true);
    }

    /**
     * Starts a match without the queue (a /duel between two players in the lobby, or the private
     * game of a party, which counts in no stats). The players leave any queue first. False, with the
     * players told why, if the game could not start.
     */
    boolean startDirect(MatchProposal match, boolean privateGame) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                matchmaker.leave(id);
            }
        }
        boolean started = start(match, false, privateGame);
        onChange.run();
        return started;
    }

    private boolean start(MatchProposal match, boolean requeue) {
        return start(match, requeue, false);
    }

    private boolean start(MatchProposal match, boolean requeue, boolean privateGame) {
        String modeId = match.mode().id();
        MatchHost host = hosts.get(modeId);
        List<Player> players = new ArrayList<>();
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            // A player who left the lobby without leaving the queue (sent elsewhere by a command)
            // is not started: the others go back to the queue.
            if (player != null && state.isInLobby(player)) {
                players.add(player);
            }
        }
        if (host == null || players.size() != match.players().size()) {
            if (requeue) {
                requeueOnline(match);
            }
            return false;
        }
        // The players belong to the game before it starts: the host teleports them during start().
        for (Player player : players) {
            lobby.enterGame(player, modeId);
            if (privateGame) {
                state.markPrivate(player.getUniqueId());
            }
        }
        for (List<QueueEntry> team : match.teams()) {
            List<UUID> others = new ArrayList<>(match.players());
            for (QueueEntry entry : team) {
                others.remove(entry.player());
            }
            for (QueueEntry entry : team) {
                state.setOpponents(entry.player(), others);
            }
        }
        boolean started;
        try {
            started = host.start(match);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "The mode " + modeId + " could not start a game", e);
            started = false;
        }
        if (started) {
            for (Player player : players) {
                toldNoArena.remove(player.getUniqueId());
            }
            return true;
        }
        // Not started: still in the lobby, unless the host had already teleported someone.
        for (Player player : players) {
            lobby.cancelGame(player);
        }
        if (!requeue) {
            for (Player player : players) {
                player.sendMessage(ChatColor.YELLOW + "No free arena right now: try again in a moment.");
            }
            return false;
        }
        requeueOnline(match);
        for (Player player : players) {
            if (toldNoArena.add(player.getUniqueId())) {
                player.sendMessage(ChatColor.YELLOW + "No free arena right now: you keep your place in the queue.");
            }
        }
        return false;
    }

    /**
     * Puts back in the queue, at their original place, the players of this match who are still
     * online, in the lobby and not waiting elsewhere; a party only when all of its players are.
     */
    private void requeueOnline(MatchProposal match) {
        Set<UUID> brokenParties = new HashSet<>();
        for (QueueEntry entry : match.entries()) {
            Player player = Bukkit.getPlayer(entry.player());
            if (entry.party() != null && (player == null || !state.isInLobby(player))) {
                brokenParties.add(entry.party());
            }
        }
        List<QueueEntry> back = new ArrayList<>();
        for (QueueEntry entry : match.entries()) {
            Player player = Bukkit.getPlayer(entry.player());
            if (player != null && state.isInLobby(player) && !brokenParties.contains(entry.party())) {
                back.add(entry);
            } else if (player != null && entry.party() != null && brokenParties.contains(entry.party())) {
                player.sendMessage(ChatColor.YELLOW + "A player of your party is not in the lobby any more: your party left the queue.");
            }
        }
        matchmaker.requeue(match.mode().id(), back);
    }

    /** A mode closed (its plugin was disabled): its players leave the queue. */
    private void closeQueue(String modeId) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            GameMode mode = matchmaker.queueOf(player.getUniqueId());
            if (mode != null && mode.id().equals(modeId)) {
                matchmaker.leave(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "The queue for " + mode.name() + ChatColor.RED + " is closed.");
            }
        }
    }
}
