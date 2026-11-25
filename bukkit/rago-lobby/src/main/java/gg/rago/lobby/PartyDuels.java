package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Party duels (the owner's ask of 25/11: « System dans la category party, ou si on fait /duel en
 * étant le chef d'une party avec 2 ou + on peut voir toutes les party présentes sur le server et les
 * duel dans n'importe quel mod de Duel selon le nombre de joueurs »). The leader of a party of two
 * or more opens the parties of the server with /duel, or with Party Duels in the Party category of
 * the compass; a click on one opens the modes of the Duels whose two teams have the size of the two
 * parties (two parties of two: the 2v2s); a click on a mode sends the challenge. The other leader
 * has a minute to accept ([Accept], or /duel accept with the name of the leader who challenged);
 * the two parties then play it, one team each, without the queue, as a /duel: never ranked. Main
 * thread only.
 */
final class PartyDuels {

    /** The category of the Duels (categories: of config.yml), whose modes the parties duel in. */
    static final String DUELS_CATEGORY = "duels";
    static final long EXPIRY_MS = DuelRequests.EXPIRY_MS;
    /** The parties shown at most: five rows. */
    private static final int MAX_SHOWN = 45;

    private static final class Request {
        final String modeId;
        final long expiresAt;

        Request(String modeId, long expiresAt) {
            this.modeId = modeId;
            this.expiresAt = expiresAt;
        }
    }

    private final LobbySettings settings;
    private final LobbyState state;
    private final QueueController queues;
    private final PartyBook book;
    private final NetworkRanks ranks;
    /** The party challenged (its id), then the party that challenges (its id), then the request. */
    private final Map<UUID, Map<UUID, Request>> pending = new HashMap<>();
    /** Whether a leader may challenge another (their settings, as for /duel): yes by default. */
    private java.util.function.BiPredicate<Player, Player> gate = (from, to) -> true;
    private java.util.function.Predicate<UUID> frozen = id -> false;

    PartyDuels(LobbySettings settings, LobbyState state, QueueController queues, PartyBook book, NetworkRanks ranks) {
        this.settings = settings;
        this.state = state;
        this.queues = queues;
        this.book = book;
        this.ranks = ranks;
    }

    /** Who may challenge whom (the duel requests of their settings): the gate tells the challenger why not. */
    void setGate(java.util.function.BiPredicate<Player, Player> gate) {
        this.gate = gate;
    }

    /** The players frozen by the staff: no party duel for their party. */
    void setFrozen(java.util.function.Predicate<UUID> frozen) {
        this.frozen = frozen;
    }

    /**
     * Whether two parties of these sizes duel in this mode: two teams, each the size of a party
     * (the same for both), never ranked.
     */
    static boolean fits(GameMode mode, int size, int otherSize) {
        return mode.teams() == 2 && !mode.ranked() && size >= 2 && size == otherSize && mode.playersPerTeam() == size;
    }

    /** The modes of the Duels that two parties of these sizes play now, in the order of config.yml. */
    List<ModeDisplay> modesFor(int size, int otherSize) {
        List<ModeDisplay> modes = new ArrayList<>();
        for (ModeDisplay display : settings.modes()) {
            if (DUELS_CATEGORY.equals(display.category()) && !display.hidden() && fits(display.mode(), size, otherSize)
                    && queues.isPlayable(display.mode().id())) {
                modes.add(display);
            }
        }
        return modes;
    }

    // --- The menus --------------------------------------------------------------------------------

    /** /duel, or Party Duels in the compass: the other parties of the server, for a party leader. */
    void open(Player player) {
        PartyBook.Party mine = leading(player);
        if (mine == null) {
            player.closeInventory();
            return;
        }
        List<PartyBook.Party> others = new ArrayList<>();
        for (PartyBook.Party party : book.parties()) {
            // a party whose leader is off the server (back within a minute, or out) cannot answer
            if (party != mine && Bukkit.getPlayer(party.leader()) != null) {
                others.add(party);
            }
        }
        int shown = Math.min(others.size(), MAX_SHOWN);
        ClickMenu menu = new ClickMenu(Math.max(1, (shown + 8) / 9), "Party Duels");
        if (others.isEmpty()) {
            menu.set(4, ClickMenu.item(Material.NAME_TAG, ChatColor.BLUE + "" + ChatColor.BOLD + "No other party",
                    "No other party of two or more", "is on the server right now."), null);
        }
        for (int i = 0; i < shown; i++) {
            final UUID id = others.get(i).id();
            menu.set(i, partyIcon(mine, others.get(i)), (clicker, right) -> openModes(clicker, id));
        }
        menu.open(player);
    }

    /** A party of the list: its leader's head, its players, and whether a mode of the Duels fits. */
    private ItemStack partyIcon(PartyBook.Party mine, PartyBook.Party party) {
        Player leader = Bukkit.getPlayer(party.leader());
        String leaderName = leader == null ? "?" : leader.getName();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "" + party.size() + " players:");
        for (UUID member : party.members()) {
            Player online = Bukkit.getPlayer(member);
            lore.add(online != null ? ChatColor.WHITE + online.getName() : ChatColor.GRAY + "(disconnected)");
        }
        lore.add("");
        int modes = modesFor(mine.size(), party.size()).size();
        lore.add(modes > 0 ? ChatColor.GREEN + "Click to choose the duel (" + modes + (modes == 1 ? " mode)" : " modes)")
                : ChatColor.RED + "No Duel mode for " + mine.size() + " against " + party.size());
        return ClickMenu.head(leaderName, ranks.chatName(party.leader(), leaderName) + ChatColor.GRAY + "'s party", lore);
    }

    /** The modes of the Duels the two parties play, with the back button to the parties. */
    private void openModes(Player player, UUID partyId) {
        PartyBook.Party mine = leading(player);
        PartyBook.Party other = book.party(partyId);
        Player otherLeader = other == null ? null : Bukkit.getPlayer(other.leader());
        if (mine == null || other == null || otherLeader == null) {
            player.closeInventory();
            if (mine != null) {
                player.sendMessage(ChatColor.RED + "That party is no longer on the server.");
            }
            return;
        }
        List<ModeDisplay> modes = modesFor(mine.size(), other.size());
        if (modes.isEmpty()) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "No Duel mode for " + mine.size() + " against " + other.size()
                    + " players: two parties duel with teams of their size.");
            return;
        }
        ClickMenu menu = new ClickMenu((modes.size() + 8) / 9 + 1, "Duel " + otherLeader.getName() + "'s party");
        for (int i = 0; i < modes.size(); i++) {
            final ModeDisplay display = modes.get(i);
            List<String> lore = new ArrayList<>(display.description());
            lore.add("");
            lore.add(ChatColor.GRAY + display.format() + ": your party against " + otherLeader.getName() + "'s");
            lore.add(ChatColor.GREEN + "Click to challenge them");
            menu.set(i, ClickMenu.item(display.icon(), 0, display.mode().name(), lore), (clicker, right) -> {
                clicker.closeInventory();
                challenge(clicker, partyId, display.mode());
            });
        }
        menu.set(menu.size() - 5, ClickMenu.back("the parties"), (clicker, right) -> open(clicker));
        menu.open(player);
    }

    // --- The challenge ----------------------------------------------------------------------------

    /** The leader challenges this party to this mode: its leader has a minute to answer. */
    void challenge(Player player, UUID partyId, GameMode mode) {
        PartyBook.Party mine = leading(player);
        if (mine == null) {
            return;
        }
        PartyBook.Party other = book.party(partyId);
        Player otherLeader = other == null ? null : Bukkit.getPlayer(other.leader());
        if (other == null || otherLeader == null) {
            player.sendMessage(ChatColor.RED + "That party is no longer on the server.");
            return;
        }
        if (!fits(mode, mine.size(), other.size())) {
            player.sendMessage(ChatColor.RED + "No " + mode.name() + ChatColor.RED + " for " + mine.size() + " against "
                    + other.size() + " players.");
            return;
        }
        if (!queues.isPlayable(mode.id())) {
            player.sendMessage(ChatColor.GRAY + mode.name() + ChatColor.GRAY + " is coming soon.");
            return;
        }
        if (!canPlay(player, mine, "Your party") || !canPlay(player, other, otherLeader.getName() + "'s party")
                || !gate.test(player, otherLeader)) {
            return;
        }
        long now = System.currentTimeMillis();
        prune(now);
        pending.computeIfAbsent(other.id(), id -> new HashMap<>()).put(mine.id(), new Request(mode.id(), now + EXPIRY_MS));
        String format = format(mode);
        Parties.boxed(player, ChatColor.GREEN + "Party duel request sent to " + otherLeader.getName() + "'s party in "
                + mode.name() + ChatColor.GREEN + " (" + format + "). They have one minute to accept.");
        tellOthers(mine, player, Brand.ACCENT + player.getName() + " challenged " + otherLeader.getName() + "'s party to "
                + mode.name() + Brand.ACCENT + " (" + format + ").");
        otherLeader.sendMessage(Parties.LINE);
        otherLeader.sendMessage(Brand.ACCENT + player.getName() + "'s party (" + mine.size() + " players) challenges your party to "
                + mode.name() + Brand.ACCENT + " (" + format + ")! Click to answer (or /duel accept " + player.getName() + "):");
        // the [Accept] [Deny] line of /duel: /duel accept and /duel deny find a party duel first
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + otherLeader.getName() + " "
                + DuelRequests.buttons(player.getName()));
        otherLeader.sendMessage(Parties.LINE);
        tellOthers(other, otherLeader, Brand.ACCENT + player.getName() + "'s party challenges your party to " + mode.name()
                + Brand.ACCENT + " (" + format + "): " + otherLeader.getName() + " answers.");
    }

    /**
     * /duel accept: the leader accepts the party duel of the leader of that name; the two parties
     * play it now. False if that leader's party sent none to this player's (then a /duel of one
     * player, perhaps).
     */
    boolean accept(Player target, String challengerName) {
        Player challenger = Bukkit.getPlayerExact(challengerName);
        PartyBook.Party mine = book.together(target.getUniqueId());
        PartyBook.Party theirs = challenger == null ? null : book.together(challenger.getUniqueId());
        if (mine == null || theirs == null || mine == theirs || !mine.isLeader(target.getUniqueId())) {
            return false;
        }
        Request request = take(mine, theirs);
        if (request == null) {
            return false;
        }
        if (!theirs.isLeader(challenger.getUniqueId())) {
            target.sendMessage(ChatColor.RED + challenger.getName() + " no longer leads their party: no party duel.");
            return true;
        }
        GameMode mode = settings.catalog().get(request.modeId);
        if (mode == null || !queues.isPlayable(mode.id())) {
            target.sendMessage(ChatColor.GRAY + "This mode is not open any more.");
            return true;
        }
        if (!fits(mode, theirs.size(), mine.size())) {
            target.sendMessage(ChatColor.RED + "The parties changed: no " + mode.name() + ChatColor.RED + " for "
                    + theirs.size() + " against " + mine.size() + " players.");
            return true;
        }
        if (!canPlay(target, mine, "Your party") || !canPlay(target, theirs, challenger.getName() + "'s party")) {
            return true;
        }
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = Arrays.asList(entries(theirs, now), entries(mine, now));
        String line = Brand.ACCENT + "Party duel: " + challenger.getName() + "'s party against " + target.getName()
                + "'s party in " + mode.name() + Brand.ACCENT + " (" + format(mode) + ").";
        for (PartyBook.Party party : Arrays.asList(theirs, mine)) {
            for (UUID member : party.members()) {
                Player player = Bukkit.getPlayer(member);
                if (player != null) {
                    player.sendMessage(line);
                }
            }
        }
        if (queues.startDirect(new MatchProposal(mode, teams), false)) {
            forgetParty(mine.id());
            forgetParty(theirs.id());
        }
        return true;
    }

    /** /duel deny: the leader denies the party duel of the leader of that name. False if there is none. */
    boolean refuse(Player target, String challengerName) {
        Player challenger = Bukkit.getPlayerExact(challengerName);
        PartyBook.Party mine = book.together(target.getUniqueId());
        PartyBook.Party theirs = challenger == null ? null : book.together(challenger.getUniqueId());
        if (mine == null || theirs == null || mine == theirs || !mine.isLeader(target.getUniqueId())
                || take(mine, theirs) == null) {
            return false;
        }
        target.sendMessage(ChatColor.GRAY + "Party duel request denied.");
        challenger.sendMessage(ChatColor.RED + target.getName() + " denied your party duel request.");
        return true;
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /**
     * The player's party if they lead one of two or more and are in the lobby, else null, the player
     * told why.
     */
    private PartyBook.Party leading(Player player) {
        PartyBook.Party party = book.together(player.getUniqueId());
        if (party == null) {
            Parties.boxed(player, ChatColor.BLUE + "Party duels are played between parties: invite your friends with "
                    + ChatColor.WHITE + "/party invite <name>" + ChatColor.BLUE + ", then " + ChatColor.WHITE + "/duel"
                    + ChatColor.BLUE + " to see the other parties.");
            return null;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Parties.boxed(player, ChatColor.RED + "Only the party leader can challenge another party.");
            return null;
        }
        if (!state.isInLobby(player) || state.isInGame(player)) {
            player.sendMessage(ChatColor.RED + "Go back to the lobby (/spawn) to start a duel.");
            return null;
        }
        return party;
    }

    /** Can every player of this party play now (on the server, in the lobby, not frozen, no RagoBot)? Tells the asker why not. */
    private boolean canPlay(Player asker, PartyBook.Party party, String whose) {
        for (UUID member : party.members()) {
            if (RagoBots.isBot(member)) {
                asker.sendMessage(ChatColor.RED + whose + " has " + BotSettings.NAME + ", who plays no party duel"
                        + (whose.equals("Your party") ? ": /p kick " + BotSettings.NAME + " first." : "."));
                return false;
            }
            Player player = Bukkit.getPlayer(member);
            if (player == null) {
                asker.sendMessage(ChatColor.RED + "A player of " + lower(whose) + " is not on the server: no party duel now.");
                return false;
            }
            if (!state.isInLobby(player) || state.isInGame(player)) {
                asker.sendMessage(ChatColor.RED + player.getName() + " (" + lower(whose) + ") is not in the lobby: no party duel now.");
                return false;
            }
            if (frozen.test(member)) {
                asker.sendMessage(ChatColor.RED + "No party duel with a player frozen by the staff.");
                return false;
            }
        }
        return true;
    }

    private static String lower(String whose) {
        return whose.equals("Your party") ? "your party" : whose;
    }

    /** The team of a party: its players, the leader first, as the party queues. */
    private List<QueueEntry> entries(PartyBook.Party party, long now) {
        List<QueueEntry> team = new ArrayList<>();
        for (UUID member : party.members()) {
            team.add(new QueueEntry(member, settings.defaultRating(), now, null, party.id()));
        }
        return team;
    }

    private String format(GameMode mode) {
        ModeDisplay display = settings.display(mode.id());
        return display != null ? display.format() : mode.format();
    }

    /** Tells the players of the party but this one. */
    private static void tellOthers(PartyBook.Party party, Player but, String message) {
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && !player.equals(but)) {
                player.sendMessage(message);
            }
        }
    }

    /** Removes and returns the live request of the party theirs to the party mine, or null. */
    private Request take(PartyBook.Party mine, PartyBook.Party theirs) {
        Map<UUID, Request> forMine = pending.get(mine.id());
        Request request = forMine == null ? null : forMine.remove(theirs.id());
        if (forMine != null && forMine.isEmpty()) {
            pending.remove(mine.id());
        }
        return request == null || request.expiresAt < System.currentTimeMillis() ? null : request;
    }

    /** The requests of a party that plays now, sent or received, go. */
    private void forgetParty(UUID party) {
        pending.remove(party);
        for (Map<UUID, Request> forTarget : pending.values()) {
            forTarget.remove(party);
        }
    }

    /** The requests that ran out go. */
    private void prune(long now) {
        for (Iterator<Map<UUID, Request>> targets = pending.values().iterator(); targets.hasNext();) {
            Map<UUID, Request> forTarget = targets.next();
            forTarget.values().removeIf(request -> request.expiresAt < now);
            if (forTarget.isEmpty()) {
                targets.remove();
            }
        }
    }
}
