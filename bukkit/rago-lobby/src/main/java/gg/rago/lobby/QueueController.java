package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.Matchmaker;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
 * <p>A player alone may wait in several queues ({@link Matchmaker#MAX_QUEUES} at most): the first
 * match found takes them out of all of them. They keep their place while they play a training
 * (a game whose host lets them go, {@link MatchHost#canLetGo}), from the lobby or joined from the
 * training itself: the match found takes them straight out of it.
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
    /** How long a duel or a party's private game waits for its map to be ready. */
    static final long DIRECT_WAIT_MILLIS = 30_000L;
    /** Duels and private games of parties waiting for their map: started as soon as it is ready. */
    private final List<DirectWait> waitingDirect = new ArrayList<>();
    private final Runnable onChange;
    private final PartyBook parties;
    /** The ELO of the players, for the ranked queues; null until the stats exist. */
    private StatsBook book;
    /** A player's 2v2 queue is told to the lobby once in this long at most. */
    static final long ANNOUNCE_PAUSE_MS = 60_000L;
    /** When each player's 2v2 queue was last told to the lobby. */
    /** The place of each player in the queues, from their ranks (PREMIUM+ before VIP before the others). */
    private java.util.function.ToIntFunction<UUID> priorities = id -> 0;
    private final Map<UUID, Long> announcedAt = new HashMap<>();

    QueueController(RagoLobby lobby, LobbySettings settings, LobbyState state, PartyBook parties, Runnable onChange) {
        this.lobby = lobby;
        this.settings = settings;
        this.state = state;
        this.parties = parties;
        this.logger = lobby.getLogger();
        this.matchmaker = new Matchmaker(settings.catalog().all(), settings.window());
        this.matchmaker.setPriorities(id -> priorities.applyAsInt(id));
        this.onChange = onChange;
    }

    void setBook(StatsBook book) {
        this.book = book;
    }

    /** The player's ELO in this mode: what a ranked queue matches on. */
    int rating(UUID player, GameMode mode) {
        return book == null ? settings.defaultRating() : book.elo(player, mode.id());
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

    /** True while the player belongs to a game. */
    boolean inGame(Player player) {
        return state.isInGame(player);
    }

    /** The player clicked a mode in the selector, or typed /queue: any map. */
    void join(Player player, GameMode mode) {
        join(player, mode, null);
    }

    /** Joins the mode's queue for this map (null: any map, the host chooses). */
    void join(Player player, GameMode mode, String map) {
        if (state.isInGame(player)) {
            if (!switchGame(player, mode) && !joinFromGame(player, mode, map)) {
                player.sendMessage(ChatColor.RED + "You are already in a game.");
            }
            return;
        }
        if (lobby.isFrozen(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are frozen by the staff: you cannot join a game.");
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
        Matchmaker.JoinResult result = matchmaker.join(player.getUniqueId(), mode.id(), rating(player.getUniqueId(), mode),
                System.currentTimeMillis(), map);
        joined(player, mode, map, result, false);
    }

    /** Tells a player alone what joining this queue did; inGame: from their training. */
    private void joined(Player player, GameMode mode, String map, Matchmaker.JoinResult result, boolean inGame) {
        UUID id = player.getUniqueId();
        switch (result) {
            case JOINED:
            case SWITCHED:
            case ALSO:
                toldNoArena.remove(id);
                boolean also = result == Matchmaker.JoinResult.ALSO && !Matchmaker.alone(mode);
                player.sendMessage(ChatColor.GREEN + (also ? "You also joined" : "You joined") + " the queue for " + mode.name()
                        + ChatColor.GREEN + (map == null ? "" : " on " + ChatColor.WHITE + map + ChatColor.GREEN) + " ("
                        + mode.format() + ", " + matchmaker.waiting(mode.id()) + " waiting)."
                        + (priorities.applyAsInt(id) > 0 ? ChatColor.AQUA + " Priority queue." : ""));
                int queues = matchmaker.queuesOf(id).size();
                if (inGame) {
                    player.sendMessage(ChatColor.GRAY + "Keep training: the game found takes you out of the training"
                            + (queues > 1 ? ", the first of your " + queues + " queues." : "."));
                } else if (also) {
                    player.sendMessage(ChatColor.GRAY + "You wait in " + queues + " queues: the first game found takes you.");
                }
                announceTeam(player, Collections.singletonList(id), mode);
                onChange.run();
                break;
            case ALREADY_WAITING:
                player.sendMessage(ChatColor.GRAY + "You are already in this queue.");
                break;
            case TOO_MANY_QUEUES:
                player.sendMessage(ChatColor.RED + "You already wait in " + Matchmaker.MAX_QUEUES
                        + " queues: leave one first (click it again in the menu).");
                break;
            default:
                player.sendMessage(ChatColor.RED + "This mode does not exist.");
                break;
        }
    }

    /**
     * A player in a training asks for a mode played against others: they wait in its queue while
     * they train on, the match found takes them out of the training ({@link MatchHost#canLetGo}).
     * False when this does not apply (a mode played alone, a game that keeps its players): the
     * player stays in their game, and out of the queue.
     */
    private boolean joinFromGame(Player player, GameMode mode, String map) {
        UUID id = player.getUniqueId();
        if (Matchmaker.alone(mode) || settings.privateRoutes().containsKey(mode.id()) || !canLetGo(player)) {
            return false;
        }
        if (lobby.isFrozen(id)) {
            player.sendMessage(ChatColor.RED + "You are frozen by the staff: you cannot join a game.");
        } else if (!isPlayable(mode.id())) {
            player.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " is coming soon.");
        } else if (parties.together(id) != null) {
            Parties.boxed(player, ChatColor.RED + "Your party joins a queue from the lobby: /spawn first.");
        } else {
            joined(player, mode, map, matchmaker.join(id, mode.id(), rating(id, mode), System.currentTimeMillis(), map), true);
        }
        return true;
    }

    /**
     * A player in a game asks for a mode played alone (a training's Trainings menu): they go straight
     * from their game into a game of that mode, without the lobby in between, when the host of their
     * game lets them go ({@link MatchHost#letGo}). False when this does not apply (a mode of several
     * players, a game that keeps its players): the player stays in their game.
     */
    private boolean switchGame(Player player, GameMode mode) {
        UUID id = player.getUniqueId();
        String from = state.gameOf(id);
        MatchHost host = hosts.get(mode.id());
        if (from == null || !hosts.containsKey(from) || host == null || !Matchmaker.alone(mode)
                || settings.privateRoutes().containsKey(mode.id()) || lobby.isFrozen(id) || !isPlayable(mode.id())) {
            return false;
        }
        if (mode.id().equals(from)) {
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "You are already playing " + mode.name() + ChatColor.GRAY + ".");
            return true;
        }
        MatchProposal match = new MatchProposal(mode, Collections.singletonList(Collections.singletonList(
                new QueueEntry(id, settings.defaultRating(), System.currentTimeMillis()))));
        if (!ready(host, match)) {
            player.sendMessage(ChatColor.YELLOW + "This mode is being prepared: try again in a moment.");
            return true;
        }
        if (!release(player)) {
            return false;
        }
        // the queues the player waits in stay: they keep their place from a training to another
        forgetWaiting(Collections.singleton(id));
        lobby.enterGame(player, mode.id());
        state.markUnrated(id);
        state.setOpponents(id, Collections.<UUID>emptyList());
        boolean started;
        try {
            started = host.start(match);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "The mode " + mode.id() + " could not start a game", e);
            started = false;
        }
        if (started) {
            queuesDuring(player, false);
        } else {
            // back to the lobby: the game the player left is over for them
            lobby.cancelGame(player);
            player.sendMessage(ChatColor.YELLOW + "No free arena right now: try again in a moment.");
        }
        onChange.run();
        return true;
    }

    /** True if the player plays a game that would let them go at once for another one (a training). */
    boolean canLetGo(Player player) {
        String from = state.gameOf(player.getUniqueId());
        MatchHost host = from == null ? null : hosts.get(from);
        if (host == null) {
            return false;
        }
        try {
            return host.canLetGo(player);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + from + " could not tell if it lets a player go", e);
            return false;
        }
    }

    /**
     * The player's game lets them go for another one (a training, {@link MatchHost#letGo}): they
     * belong to no game, where they stand, until the next one starts. False if it keeps them.
     */
    private boolean release(Player player) {
        String from = state.gameOf(player.getUniqueId());
        MatchHost host = from == null ? null : hosts.get(from);
        boolean free;
        try {
            free = host != null && host.letGo(player);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + from + " could not let a player go", e);
            free = false;
        }
        if (free) {
            lobby.betweenGames(player);
        }
        return free;
    }

    /**
     * The player started a game of a mode played alone while waiting in other queues: they keep
     * their place while the game lets them go for the match found (a training), else they leave
     * the queues. tellKept: say so when they keep it.
     */
    private void queuesDuring(Player player, boolean tellKept) {
        UUID id = player.getUniqueId();
        List<GameMode> waiting = matchmaker.queuesOf(id);
        if (waiting.isEmpty()) {
            return;
        }
        if (canLetGo(player)) {
            if (tellKept) {
                player.sendMessage(ChatColor.GRAY + "You keep your place in the " + queuesFor(waiting, ChatColor.GRAY)
                        + ": the game found takes you out of the training.");
            }
            return;
        }
        matchmaker.leave(id);
        toldNoArena.remove(id);
        player.sendMessage(ChatColor.YELLOW + "You left the " + queuesFor(waiting, ChatColor.YELLOW)
                + ": you keep your place in a queue during a training only.");
        onChange.run();
    }

    /** "queue for Sumo", "queues for Sumo and Bridge": each name followed by this colour. */
    static String queuesFor(List<GameMode> modes, ChatColor colour) {
        StringBuilder text = new StringBuilder(modes.size() == 1 ? "queue for " : "queues for ");
        for (int i = 0; i < modes.size(); i++) {
            if (i > 0) {
                text.append(i == modes.size() - 1 ? " and " : ", ");
            }
            text.append(modes.get(i).name()).append(colour);
        }
        return text.toString();
    }

    /**
     * Puts the player alone in the queue of this mode without a word (a game plugin's own way in:
     * an event hosted with /host, a mode hidden from the selector). False when the player is in a
     * game, frozen, or the mode is not played.
     */
    boolean enter(Player player, GameMode mode) {
        if (state.isInGame(player) || lobby.isFrozen(player.getUniqueId()) || !isPlayable(mode.id())) {
            return false;
        }
        Matchmaker.JoinResult result = matchmaker.join(player.getUniqueId(), mode.id(), rating(player.getUniqueId(), mode),
                System.currentTimeMillis(), null);
        if (result == Matchmaker.JoinResult.JOINED || result == Matchmaker.JoinResult.SWITCHED
                || result == Matchmaker.JoinResult.ALSO) {
            toldNoArena.remove(player.getUniqueId());
            onChange.run();
            return true;
        }
        return result == Matchmaker.JoinResult.ALREADY_WAITING;
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
        // from the Private games: the practice of the mode (the top bridge without rounds)
        startPrivate(party, players, chosen, map, true);
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
            if (lobby.isFrozen(id)) {
                Parties.boxed(leader, ChatColor.RED + member.getName() + " is frozen by the staff: your party cannot join a queue.");
                return;
            }
            players.add(member);
        }
        if (mode.teams() >= 2 && players.size() == mode.playersPerMatch()) {
            startPrivate(party, players, mode, map, false);
            return;
        }
        if (mode.teams() == 1 && mode.playersPerTeam() == 1 && players.size() > 1) {
            // a free for all, the invis practice, a training: the party goes in together, one after
            // the other, so that an open game or a practice waiting for its players takes them all
            long now = System.currentTimeMillis();
            for (Player member : players) {
                startDirect(new MatchProposal(mode, Collections.singletonList(Collections.singletonList(
                        new QueueEntry(member.getUniqueId(), settings.defaultRating(), now, map, party.id())))), false);
            }
            return;
        }
        if (players.size() > mode.playersPerTeam()) {
            Parties.boxed(leader, ChatColor.RED + "Your party has " + players.size() + " players: too many for "
                    + mode.name() + ChatColor.RED + " (" + mode.format() + ").");
            return;
        }
        Map<UUID, Integer> ratings = new LinkedHashMap<>();
        for (Player member : players) {
            ratings.put(member.getUniqueId(), rating(member.getUniqueId(), mode));
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
                announceTeam(leader, party.members(), mode);
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

    /**
     * A team of a 2v2 joined its queue (a player alone, or a party): the rest of the lobby (not in a
     * queue nor a game) is told,
     * "Sofiene's team is queuing Bed fight 2v2 [JOIN NOW]", and the button joins the same queue,
     * so that the match is found sooner (a player alone gets a teammate there). Once a minute at
     * most for each player.
     */
    private void announceTeam(Player leader, Collection<UUID> team, GameMode mode) {
        if (mode.teams() != 2 || mode.playersPerTeam() != 2) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = announcedAt.get(leader.getUniqueId());
        if (last != null && now - last < ANNOUNCE_PAUSE_MS) {
            return;
        }
        announcedAt.put(leader.getUniqueId(), now);
        String json = teamQueueLine(leader.getName(), ChatColor.stripColor(mode.name()), mode.id());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!team.contains(other.getUniqueId()) && state.isInLobby(other) && !state.isInGame(other)
                    && queueOf(other) == null) {
                // clickable through the vanilla /tellraw, as the duel buttons: names are letters, digits and _
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + other.getName() + " " + json);
            }
        }
    }

    /** The JSON of "Sofiene's team is queuing Bed fight 2v2 [JOIN NOW]", the button running /queue of the mode. */
    static String teamQueueLine(String leader, String modeName, String modeId) {
        String mode = modeName.replace("\\", "").replace("\"", "");
        return "[\"\",{\"text\":\"" + leader + "'s team\",\"color\":\"green\"},"
                + "{\"text\":\" is queuing \",\"color\":\"gray\"},{\"text\":\"" + mode + "\",\"color\":\"white\"},"
                + "{\"text\":\" \"},{\"text\":\"[JOIN NOW]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/queue " + modeId + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Join the queue of " + mode + "\"}}]";
    }

    /**
     * The party fills the match: a private game between its players, teams dealt in turn, counted
     * nowhere; from the Private games (parties-privees), the practice of the mode.
     */
    private void startPrivate(PartyBook.Party party, List<Player> players, GameMode mode, String map, boolean practice) {
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
        startDirect(new MatchProposal(mode, teams), true, practice);
    }

    /**
     * The players of a party changed (one joined, left, was kicked, the party was disbanded or
     * transferred): those who waited, with their party or alone, leave the queue.
     */
    void partyChanged(Collection<UUID> players) {
        for (UUID id : players) {
            List<GameMode> waiting = matchmaker.queuesOf(id);
            if (waiting.isEmpty()) {
                continue;
            }
            List<UUID> together = matchmaker.partyOf(id);
            matchmaker.leave(id);
            for (UUID member : together) {
                toldNoArena.remove(member);
                Player online = Bukkit.getPlayer(member);
                if (online != null) {
                    online.sendMessage(ChatColor.YELLOW + "The party changed: "
                            + (together.size() > 1 ? "your party left" : "you left") + " the "
                            + queuesFor(waiting, ChatColor.YELLOW) + ".");
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

    /** Where each player stands in the queues: the ranks of the web store first. */
    void setPriorities(java.util.function.ToIntFunction<UUID> priorities) {
        this.priorities = priorities;
    }

    /** The map the player waits for in this mode's queue (null: any map, or not waiting there). */
    String mapOf(Player player, GameMode mode) {
        QueueEntry entry = matchmaker.entryOf(player.getUniqueId(), mode.id());
        return entry == null ? null : entry.map();
    }

    /** How many players wait in this mode's queue for this very map. */
    int waitingFor(String modeId, String map) {
        return matchmaker.waitingFor(modeId, map);
    }

    /**
     * Leaves every queue; returns the first mode left, or null if the player was not waiting. With a
     * party, the whole party leaves.
     */
    GameMode leave(Player player, boolean tell) {
        List<UUID> together = matchmaker.partyOf(player.getUniqueId());
        List<GameMode> waiting = matchmaker.queuesOf(player.getUniqueId());
        GameMode left = matchmaker.leave(player.getUniqueId());
        toldNoArena.remove(player.getUniqueId());
        if (left != null) {
            if (tell && together.size() < 2) {
                player.sendMessage(ChatColor.YELLOW + "You left the " + queuesFor(waiting, ChatColor.YELLOW) + ".");
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

    /**
     * Leaves this mode's queue only, the others kept; with a party (which waits in one queue), the
     * whole party leaves. False if the player was not waiting there.
     */
    boolean leave(Player player, GameMode mode, boolean tell) {
        UUID id = player.getUniqueId();
        if (!matchmaker.isWaiting(id, mode.id())) {
            return false;
        }
        if (matchmaker.queuesOf(id).size() == 1) {
            return leave(player, tell) != null;
        }
        matchmaker.leave(id, mode.id());
        if (tell) {
            player.sendMessage(ChatColor.YELLOW + "You left the queue for " + mode.name() + ChatColor.YELLOW + ".");
        }
        onChange.run();
        return true;
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

    /** The first queue the player joined among those they wait in, or null. */
    GameMode queueOf(Player player) {
        return matchmaker.queueOf(player.getUniqueId());
    }

    /** Every queue the player waits in, in the order they joined them; empty if none. */
    List<GameMode> queuesOf(Player player) {
        return matchmaker.queuesOf(player.getUniqueId());
    }

    boolean isWaiting(Player player, GameMode mode) {
        return matchmaker.isWaiting(player.getUniqueId(), mode.id());
    }

    /** How long the player has waited in their first queue, in milliseconds, or -1 if not waiting. */
    long waited(Player player, long nowMillis) {
        QueueEntry entry = matchmaker.entryOf(player.getUniqueId());
        return entry == null ? -1L : entry.waitedMillis(nowMillis);
    }

    int waiting(String modeId) {
        return matchmaker.waiting(modeId);
    }

    // --- Matches --------------------------------------------------------------------------------

    /** Once a second: starts the matches that were found, and the direct ones whose map is ready. */
    void tick() {
        startWaiting();
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
        return startDirect(match, privateGame, false);
    }

    /** As {@link #startDirect(MatchProposal, boolean)}; practice: a private game played as a practice. */
    private boolean startDirect(MatchProposal match, boolean privateGame, boolean practice) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                matchmaker.leave(id);
            }
        }
        forgetWaiting(match.players());
        MatchHost host = hosts.get(match.mode().id());
        if (host != null && !ready(host, match)) {
            // a copy of the map is being made: the game starts as soon as it is there
            waitingDirect.add(new DirectWait(match, privateGame, practice, System.currentTimeMillis() + DIRECT_WAIT_MILLIS));
            tell(match, ChatColor.YELLOW + "The map is being prepared: the game starts in a moment.");
            onChange.run();
            return true;
        }
        boolean started = start(match, false, privateGame, practice);
        onChange.run();
        return started;
    }

    /** A duel or a private game waiting for its map. */
    private static final class DirectWait {
        final MatchProposal match;
        final boolean privateGame;
        final boolean practice;
        final long until;

        DirectWait(MatchProposal match, boolean privateGame, boolean practice, long until) {
            this.match = match;
            this.privateGame = privateGame;
            this.practice = practice;
            this.until = until;
        }
    }

    private void startWaiting() {
        if (waitingDirect.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (DirectWait wait : new ArrayList<>(waitingDirect)) {
            MatchHost host = hosts.get(wait.match.mode().id());
            if (host == null || !stillFree(wait.match)) {
                waitingDirect.remove(wait);
                tell(wait.match, ChatColor.GRAY + "The game was cancelled: a player is no longer in the lobby.");
            } else if (ready(host, wait.match)) {
                waitingDirect.remove(wait);
                start(wait.match, false, wait.privateGame, wait.practice);
                onChange.run();
            } else if (now > wait.until) {
                waitingDirect.remove(wait);
                tell(wait.match, ChatColor.YELLOW + "No free arena right now: try again in a moment.");
            }
        }
    }

    /** Every player of the match is online, in the lobby, in no game and in no queue. */
    private boolean stillFree(MatchProposal match) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !state.isInLobby(player) || state.isInGame(player) || matchmaker.queueOf(id) != null) {
                return false;
            }
        }
        return true;
    }

    /** These players start something else: a game they waited for is dropped. */
    private void forgetWaiting(Collection<UUID> players) {
        for (java.util.Iterator<DirectWait> it = waitingDirect.iterator(); it.hasNext();) {
            if (!Collections.disjoint(it.next().match.players(), players)) {
                it.remove();
            }
        }
    }

    /** The host's answer; a host that fails to answer is asked to start, which tells the players. */
    private boolean ready(MatchHost host, MatchProposal match) {
        try {
            return host.ready(match);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + match.mode().id() + " could not tell if a game can start", e);
            return true;
        }
    }

    private static void tell(MatchProposal match, String message) {
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private boolean start(MatchProposal match, boolean requeue) {
        return start(match, requeue, false, false);
    }

    private boolean start(MatchProposal match, boolean requeue, boolean privateGame, boolean practice) {
        String modeId = match.mode().id();
        MatchHost host = hosts.get(modeId);
        List<Player> players = new ArrayList<>();
        for (UUID id : match.players()) {
            Player player = Bukkit.getPlayer(id);
            // A player who left the lobby without leaving the queue (sent elsewhere by a command)
            // is not started: the others go back to the queue. From the queue, a player in a
            // training is: the training lets them go.
            if (player != null && (requeue ? available(player) : state.isInLobby(player))) {
                players.add(player);
            }
        }
        if (host == null || players.size() != match.players().size()) {
            if (requeue) {
                requeueOnline(match);
            }
            return false;
        }
        if (requeue && !ready(host, match)) {
            // the map is being copied: the players wait in the queue, where they are
            requeueOnline(match);
            for (Player player : players) {
                if (toldNoArena.add(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "The map is being prepared: you keep your place in the queue.");
                }
            }
            return false;
        }
        if (requeue && !releaseAll(match, players)) {
            return false;
        }
        // The players belong to the game before it starts: the host teleports them during start().
        for (Player player : players) {
            lobby.enterGame(player, modeId);
            if (privateGame) {
                state.markPrivate(player.getUniqueId());
                if (practice) {
                    state.markPractice(player.getUniqueId());
                }
            }
            if (!requeue) {
                // not from the queue (a /duel): no ELO, no win streak
                state.markUnrated(player.getUniqueId());
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
                if (requeue) {
                    queuesDuring(player, true);
                }
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
     * True if a match found in a queue may start for this player: in the lobby, or in a training
     * that lets them go.
     */
    private boolean available(Player player) {
        return state.isInLobby(player) || state.isInGame(player) && canLetGo(player);
    }

    /**
     * The players of this match still in a training leave it: the match found takes them out of it.
     * False if a game did not let its player go after all: the match does not start, the others
     * are back in the queue (from the lobby for those already out of their training), and that
     * player out of the queues.
     */
    private boolean releaseAll(MatchProposal match, List<Player> players) {
        List<Player> freed = new ArrayList<>();
        for (Player player : players) {
            if (!state.isInGame(player)) {
                continue;
            }
            if (release(player)) {
                freed.add(player);
                continue;
            }
            for (Player other : freed) {
                lobby.cancelGame(other);
            }
            requeueOnline(match, player.getUniqueId());
            matchmaker.leave(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Your game could not let you go: you left the queue for " + match.mode().name()
                    + ChatColor.YELLOW + ".");
            return false;
        }
        for (Player player : freed) {
            player.sendMessage(ChatColor.GREEN + "Game found in " + match.mode().name() + ChatColor.GREEN + ": you leave the training.");
        }
        return true;
    }

    /**
     * Puts back in the queue, at their original place, the players of this match who are still
     * online and in the lobby (or a training that lets them go); a party only when all of its
     * players are.
     */
    private void requeueOnline(MatchProposal match) {
        requeueOnline(match, null);
    }

    /** As {@link #requeueOnline(MatchProposal)}, without this player (null: none). */
    private void requeueOnline(MatchProposal match, UUID without) {
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
            if (player != null && !entry.player().equals(without) && available(player)
                    && !brokenParties.contains(entry.party())) {
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
            GameMode mode = matchmaker.leave(player.getUniqueId(), modeId);
            if (mode != null) {
                player.sendMessage(ChatColor.RED + "The queue for " + mode.name() + ChatColor.RED + " is closed.");
            }
        }
    }
}
