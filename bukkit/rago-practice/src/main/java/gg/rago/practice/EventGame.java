package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * An event a PREMIUM player hosts with /host, as on Minemen: the whole server is told for a
 * minute ([Click to join], /join), the players wait on the map watching, then the event plays on
 * one of the sumo maps. Sumo and Brackets: one 1v1 at a time on the platform, the others watching,
 * in rounds (a player without an opponent goes through); the loser is out, until one is left.
 * Sumo is knockback only; Brackets is a fight with a Bed Wars kit (a fall or a death is out). Last
 * Man Standing: everyone at once with the kit, the last one standing wins. The winner is told to
 * the whole server and gets Network XP; every player gets a little.
 */
final class EventGame extends PracticeGame {

    /** The events a player can host. */
    enum Kind {
        SUMO("sumo", "Sumo", Material.SLIME_BALL, "One 1v1 at a time on the platform:", "knock your opponent off.",
            "The last one in wins."),
        BRACKETS("brackets", "Brackets", Material.IRON_SWORD, "A 1v1 tournament with a Bed Wars kit:", "kill or knock off,",
            "the last one in wins."),
        LMS("lms", "Last Man Standing", Material.GOLD_SWORD, "Everyone at once with a Bed Wars kit:", "the last one standing",
            "wins.");

        final String id;
        final String label;
        final Material icon;
        final String[] description;

        Kind(String id, String label, Material icon, String... description) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.description = description;
        }

        /** The id of the mode of the lobby (a mode hidden from the selector). */
        String modeId() {
            return "event-" + id;
        }

        static Kind ofMode(String modeId) {
            for (Kind kind : values()) {
                if (kind.modeId().equals(modeId)) {
                    return kind;
                }
            }
            return null;
        }

        static Kind of(String word) {
            for (Kind kind : values()) {
                if (kind.id.equalsIgnoreCase(word) || kind.label.equalsIgnoreCase(word)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** Waiting for players, between two fights, the countdown of a fight, the fight, over. */
    enum Stage { WAITING, BETWEEN, COUNTDOWN, FIGHT, OVER }

    /** Distance of each fighter from the centre at the start of a 1v1. */
    static final double START_DISTANCE = 3.0;
    /** The largest circle of a Last Man Standing (the sumo maps are small). */
    static final double MAX_CIRCLE = 5.0;
    /** The countdown of a fight, frozen in place. */
    static final int FIGHT_COUNTDOWN = 3;
    /** A 1v1 ends after this long: the one who landed more hits goes through. */
    static final int FIGHT_SECONDS = 90;
    /** The seconds before the end of the wait when the server is told again. */
    static final int[] ANNOUNCE_AT = {45, 30, 15, 5};

    private final Events events;
    private final Kind kind;
    private final String hostName;
    private final Random random = new Random();
    private Stage stage = Stage.WAITING;
    private int waitLeft;
    /** The players still in, in the order they joined. */
    private final List<UUID> alive = new ArrayList<>();
    /** Sumo and Brackets: who still fights in this round, and who goes to the next one. */
    private final List<UUID> roundQueue = new ArrayList<>();
    private final List<UUID> nextRound = new ArrayList<>();
    private int roundNumber;
    /** The fighters of the 1v1 (two), or everyone in a Last Man Standing. */
    private final List<UUID> fighters = new ArrayList<>();
    /** Where each fighter starts the fight. */
    private final Map<UUID, Location> starts = new HashMap<>();
    private final Map<UUID, Integer> hits = new HashMap<>();
    private int countdownLeft;
    private int fightSeconds;
    private int players;

    EventGame(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams, Events events) {
        super(host, mode, arena, teams);
        this.events = events;
        Kind ofMode = Kind.ofMode(mode.id());
        this.kind = ofMode == null ? Kind.SUMO : ofMode;
        Events.Hosting hosting = events.hosting();
        this.hostName = hosting != null && hosting.kind == kind ? hosting.hostName : teams.get(0).get(0).getName();
        this.waitLeft = events.settings().waitSeconds;
        for (List<Player> team : teams) {
            for (Player player : team) {
                alive.add(player.getUniqueId());
            }
        }
    }

    Kind kind() {
        return kind;
    }

    Stage stage() {
        return stage;
    }

    String hostName() {
        return hostName;
    }

    int waitLeft() {
        return waitLeft;
    }

    /** The players still in. */
    int aliveCount() {
        return alive.size();
    }

    boolean full() {
        return alive.size() >= events.settings().maxPlayers;
    }

    // --- The game -------------------------------------------------------------------------------

    @Override
    void build() {
        // without the sumo maps: a round platform of quartz
        Shapes.disc(arena, 0, 0, 0, 7, Material.QUARTZ_BLOCK, 0);
    }

    @Override
    int countdownSeconds() {
        return 0;
    }

    @Override
    boolean freezes() {
        return false;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean earnsXp() {
        return false;
    }

    /** Not a match of the stats: the XP of the event is its own (see {@link Events}). */
    @Override
    boolean counts() {
        return false;
    }

    @Override
    double fallY() {
        return arena.floorY() - 2;
    }

    @Override
    Location viewPoint() {
        Location view = arena.at(0.5, 8, -11.5, 0f);
        view.setPitch(35f);
        return view;
    }

    @Override
    Location spawnOf(UUID player) {
        if (stage == Stage.WAITING) {
            return arena.at(0.5, 1, 0.5, 0f);
        }
        Location start = starts.get(player);
        return start == null || !fighters.contains(player) ? viewPoint() : start.clone();
    }

    /**
     * Where the fighter at this place of the list starts: in a 1v1, 3 blocks on each side of the
     * centre, facing each other; in a Last Man Standing, on a circle around the centre, facing it.
     * Offsets from the centre: x, z, yaw.
     */
    static double[] startOf(int index, int count, boolean everyone) {
        if (everyone) {
            // about a block and a half between two players, from 3 to 5 blocks from the centre
            double radius = Math.max(START_DISTANCE, Math.min(MAX_CIRCLE, count * 1.5 / (2 * Math.PI)));
            double angle = 2 * Math.PI * index / Math.max(1, count);
            double x = Math.sin(angle) * radius;
            double z = -Math.cos(angle) * radius;
            // the yaw that looks from (x, z) towards the centre
            double yaw = Math.toDegrees(Math.atan2(x, -z));
            return new double[] {x, z, yaw};
        }
        return index == 0 ? new double[] {-START_DISTANCE, 0, -90} : new double[] {START_DISTANCE, 0, 90};
    }

    /**
     * While the event waits: everyone on the platform, without a kit, as in a lobby (no hit
     * counts). Then the fighters go to their place with the kit, and the others watch.
     */
    @Override
    void placeAtStart(Player player) {
        if (stage == Stage.WAITING) {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            heal(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.updateInventory();
            player.teleport(spawnOf(player.getUniqueId()));
            player.setFallDistance(0f);
            return;
        }
        if (!fighters.contains(player.getUniqueId())) {
            spectate(player);
            return;
        }
        super.placeAtStart(player);
    }

    @Override
    ItemStack[] defaultKit(Player player) {
        if (kind == Kind.SUMO) {
            return null;
        }
        return new ItemStack[] {new ItemStack(Material.STONE_SWORD)};
    }

    @Override
    void armour(Player player) {
        if (kind != Kind.SUMO) {
            ironArmour(player, fighters.indexOf(player.getUniqueId()) % 2 == 0 ? Color.RED : Color.BLUE);
        }
    }

    /** Frozen in place during the countdown of a fight. */
    @Override
    Location heldAt(Player player) {
        return stage == Stage.COUNTDOWN && fighters.contains(player.getUniqueId()) ? spawnOf(player.getUniqueId()) : null;
    }

    @Override
    void intro(Player player) {
        player.sendMessage(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + "You joined the " + kind.label + " event of "
                + hostName + ". " + ChatColor.GRAY + String.join(" ", kind.description) + " /spawn to leave.");
    }

    @Override
    void onFight() {
        if (!events.accepts(this)) {
            // a game of an event mode nobody hosted (from a command of the lobby): nothing to play
            stage = Stage.OVER;
            for (Player player : online()) {
                player.sendMessage(ChatColor.GRAY + "No " + kind.label + " event is hosted right now. PREMIUM players host them with /host.");
                host.release(player.getUniqueId());
                plugin.lobby().sendToLobby(player);
            }
            finish(-1, null);
            return;
        }
        stage = Stage.WAITING;
        events.announce(this, waitLeft);
    }

    @Override
    void onSecond() {
        if (stage == Stage.WAITING) {
            waitLeft--;
            for (int at : ANNOUNCE_AT) {
                if (waitLeft == at) {
                    events.announce(this, waitLeft);
                }
            }
            if (waitLeft <= 0) {
                startEvent();
            }
            return;
        }
        if (stage == Stage.COUNTDOWN) {
            if (countdownLeft > 0) {
                for (Player player : online()) {
                    if (fighters.contains(player.getUniqueId())) {
                        title(player, ChatColor.YELLOW + "" + ChatColor.BOLD + countdownLeft, "");
                        sound(player, Sound.NOTE_STICKS, 1f);
                    }
                }
                countdownLeft--;
                return;
            }
            stage = Stage.FIGHT;
            fightSeconds = 0;
            for (UUID id : fighters) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    title(player, ChatColor.GREEN + "" + ChatColor.BOLD + "Fight!", "");
                    sound(player, Sound.NOTE_PLING, 1.5f);
                }
            }
            return;
        }
        if (stage == Stage.FIGHT && kind != Kind.LMS && ++fightSeconds >= FIGHT_SECONDS) {
            UUID a = fighters.get(0);
            UUID b = fighters.get(1);
            int ha = hits.getOrDefault(a, 0);
            int hb = hits.getOrDefault(b, 0);
            UUID loser = ha == hb ? (random.nextBoolean() ? a : b) : ha < hb ? a : b;
            broadcast(ChatColor.GRAY + "Time! The one who landed more hits goes through.", null);
            out(loser, null);
        }
    }

    @Override
    void moved(Player player, Location from, Location to) {
        if (stage == Stage.FIGHT && fighters.contains(player.getUniqueId()) && to.getBlock().isLiquid()) {
            out(player.getUniqueId(), killerOf(player.getUniqueId()));
        }
    }

    @Override
    void fell(Player player) {
        if (stage == Stage.FIGHT && fighters.contains(player.getUniqueId())) {
            out(player.getUniqueId(), killerOf(player.getUniqueId()));
        } else {
            player.teleport(spawnOf(player.getUniqueId()));
            player.setFallDistance(0f);
        }
    }

    @Override
    void lethal(Player victim, Player killer) {
        if (stage == Stage.FIGHT && fighters.contains(victim.getUniqueId())) {
            out(victim.getUniqueId(), killer == null ? null : killer.getUniqueId());
        } else {
            heal(victim);
        }
    }

    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (stage != Stage.FIGHT || !fighters.contains(victim.getUniqueId())) {
            return false;
        }
        if (attacker != null && !fighters.contains(attacker.getUniqueId())) {
            return false;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            return false;
        }
        if (kind == Kind.SUMO) {
            // the knockback only
            event.setDamage(0.0);
        }
        return true;
    }

    @Override
    void landed(Player victim, Player attacker, EntityDamageEvent event) {
        if (attacker != null) {
            hits.merge(attacker.getUniqueId(), 1, Integer::sum);
        }
    }

    // --- Joining and leaving --------------------------------------------------------------------

    /** Only while the event waits for its players, and not past the most players. */
    @Override
    void join(Player player) {
        if (stage != Stage.WAITING || full()) {
            player.sendMessage(ChatColor.RED + (stage != Stage.WAITING ? "The " + kind.label + " event has started already."
                    : "The " + kind.label + " event is full."));
            plugin.lobby().sendToLobby(player);
            return;
        }
        alive.add(player.getUniqueId());
        super.join(player);
        broadcast(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " joined ("
                + alive.size() + "/" + events.settings().maxPlayers + ").", null);
    }

    @Override
    void leave(Player player, boolean toLobby) {
        UUID id = player.getUniqueId();
        boolean fighting = fighters.contains(id) && (stage == Stage.COUNTDOWN || stage == Stage.FIGHT);
        boolean wasIn = alive.contains(id);
        host.release(id);
        if (phase != Phase.ENDED && stage != Stage.OVER) {
            if (fighting) {
                broadcast(ChatColor.GRAY + player.getName() + " left the fight.", null);
                out(id, null);
            } else if (wasIn) {
                drop(id);
                if (stage != Stage.WAITING) {
                    broadcast(ChatColor.GRAY + player.getName() + " left the event.", null);
                    checkWinner();
                }
            }
            if (online().isEmpty()) {
                stage = Stage.OVER;
                events.ended(this, null);
                finish(-1, null);
            }
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    /** Forgets a player who is out or gone. */
    private void drop(UUID id) {
        alive.remove(id);
        roundQueue.remove(id);
        nextRound.remove(id);
        fighters.remove(id);
    }

    // --- The event ------------------------------------------------------------------------------

    /** The wait is over: the event starts, or is called off without enough players. */
    private void startEvent() {
        alive.removeIf(id -> Bukkit.getPlayer(id) == null || host.gameOf(id) != this);
        if (alive.size() < events.settings().minPlayers) {
            cancel("not enough players (" + alive.size() + "/" + events.settings().minPlayers + ")");
            return;
        }
        players = alive.size();
        events.started(this);
        broadcast(ChatColor.GOLD + "[Event] " + ChatColor.GREEN + "The " + kind.label + " event starts with " + players + " players!",
                Sound.NOTE_PLING);
        if (kind == Kind.LMS) {
            fighters.clear();
            fighters.addAll(alive);
            Collections.shuffle(fighters, random);
            beginFight();
            return;
        }
        roundQueue.addAll(alive);
        Collections.shuffle(roundQueue, random);
        roundNumber = 1;
        nextMatch();
    }

    /** The next 1v1 of the round, the next round, or the winner. */
    private void nextMatch() {
        if (stage == Stage.OVER || phase == Phase.ENDED) {
            return;
        }
        roundQueue.removeIf(id -> !alive.contains(id));
        nextRound.removeIf(id -> !alive.contains(id));
        if (checkWinner()) {
            return;
        }
        if (roundQueue.size() < 2) {
            if (roundQueue.size() == 1) {
                UUID bye = roundQueue.remove(0);
                nextRound.add(bye);
                broadcast(ChatColor.GRAY + nameOf(bye) + " has no opponent in this round and goes through.", null);
            }
            roundQueue.addAll(nextRound);
            nextRound.clear();
            Collections.shuffle(roundQueue, random);
            roundNumber++;
            if (checkWinner()) {
                return;
            }
            broadcast(ChatColor.GOLD + "[Event] " + ChatColor.YELLOW + "Round " + roundNumber + ChatColor.GRAY + ": " + alive.size()
                    + " players left.", Sound.NOTE_BASS);
        }
        fighters.clear();
        fighters.add(roundQueue.remove(0));
        fighters.add(roundQueue.remove(0));
        broadcast(ChatColor.GOLD + "[Event] " + ChatColor.WHITE + nameOf(fighters.get(0)) + ChatColor.GRAY + " vs "
                + ChatColor.WHITE + nameOf(fighters.get(1)), null);
        beginFight();
    }

    /** The fighters to their places, frozen for the countdown; the others watch. */
    private void beginFight() {
        hits.clear();
        starts.clear();
        for (int i = 0; i < fighters.size(); i++) {
            double[] start = startOf(i, fighters.size(), kind == Kind.LMS);
            starts.put(fighters.get(i), arena.at(0.5 + start[0], 1, 0.5 + start[1], (float) start[2]));
        }
        stage = Stage.COUNTDOWN;
        countdownLeft = kind == Kind.LMS ? 5 : FIGHT_COUNTDOWN;
        arena.restore();
        for (Player player : online()) {
            forgetHits(player.getUniqueId());
            placeAtStart(player);
        }
        sidebars();
    }

    /** This fighter is out: the kill to whoever knocked them, back to watching. */
    private void out(UUID loser, UUID killer) {
        if (!fighters.contains(loser) || stage == Stage.OVER) {
            return;
        }
        Player player = Bukkit.getPlayer(loser);
        if (killer != null && fighters.contains(killer) && !killer.equals(loser)) {
            plugin.lobby().recordKill(mode.id(), killer, loser);
            broadcast(ChatColor.RED + nameOf(loser) + ChatColor.GRAY + " was eliminated by " + ChatColor.GREEN + nameOf(killer)
                    + ChatColor.GRAY + ".", null);
        } else {
            broadcast(ChatColor.RED + nameOf(loser) + ChatColor.GRAY + " was eliminated.", null);
        }
        drop(loser);
        events.participated(loser, kind);
        if (player != null && host.gameOf(loser) == this) {
            player.sendMessage(ChatColor.GRAY + "You are out: watch the rest, or /spawn to leave.");
            spectate(player);
        }
        if (kind == Kind.LMS) {
            checkWinner();
            return;
        }
        // the 1v1 is over: the winner goes to the next round and watches
        UUID winner = fighters.isEmpty() ? null : fighters.get(0);
        fighters.clear();
        if (winner != null) {
            nextRound.add(winner);
            Player won = Bukkit.getPlayer(winner);
            if (won != null && host.gameOf(winner) == this) {
                sound(won, Sound.LEVEL_UP, 1.2f);
                spectate(won);
            }
        }
        stage = Stage.BETWEEN;
        later(40L, this::nextMatch);
    }

    /** True (and the event over) when one player is left. */
    private boolean checkWinner() {
        if (stage == Stage.WAITING || stage == Stage.OVER) {
            return false;
        }
        if (alive.size() > 1) {
            return false;
        }
        UUID winner = alive.isEmpty() ? null : alive.get(0);
        if (winner == null) {
            cancel("nobody is left");
            return true;
        }
        stage = Stage.OVER;
        events.ended(this, winner);
        Player player = Bukkit.getPlayer(winner);
        if (player != null) {
            title(player, ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", ChatColor.YELLOW + "You won the " + kind.label + " event");
        }
        final int side = sideOf(winner);
        later(60L, () -> finish(side, null));
        return true;
    }

    /** Calls the event off: everyone back to the lobby, the server told why. */
    void cancel(String why) {
        if (stage == Stage.OVER && phase == Phase.ENDED) {
            return;
        }
        stage = Stage.OVER;
        events.cancelled(this, why);
        for (Player player : online()) {
            host.release(player.getUniqueId());
            plugin.lobby().sendToLobby(player);
        }
        finish(-1, null);
    }

    @Override
    String result(int winner) {
        return "(" + kind.label + " event, " + players + " players)";
    }

    // --- The sidebar ----------------------------------------------------------------------------

    @Override
    List<String> sidebar(UUID viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + kind.label + " event");
        lines.add(Duel.cut(ChatColor.WHITE + "Host: " + ChatColor.YELLOW + hostName));
        lines.add("");
        if (stage == Stage.WAITING) {
            lines.add(ChatColor.WHITE + "Starts in: " + ChatColor.GREEN + clock(waitLeft));
            lines.add(ChatColor.WHITE + "Players: " + ChatColor.GREEN + alive.size() + ChatColor.GRAY + "/"
                    + events.settings().maxPlayers);
            lines.add(ChatColor.GRAY + "Invite them: /join");
        } else {
            if (kind != Kind.LMS) {
                lines.add(ChatColor.WHITE + "Round: " + ChatColor.GREEN + roundNumber);
            }
            lines.add(ChatColor.WHITE + "Players left: " + ChatColor.GREEN + alive.size() + ChatColor.GRAY + "/" + players);
            if (kind != Kind.LMS && fighters.size() == 2) {
                lines.add("");
                lines.add(Duel.cut(ChatColor.RED + nameOf(fighters.get(0))));
                lines.add(ChatColor.GRAY + "vs");
                lines.add(Duel.cut(ChatColor.BLUE + nameOf(fighters.get(1))));
            }
            lines.add("");
            lines.add(alive.contains(viewer) ? ChatColor.GREEN + "You are still in" : ChatColor.GRAY + "You are out");
        }
        return lines;
    }
}
