package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The private games whose players choose their teams (parties-privees-equipes: Bedwars Normal, the
 * owner's answers of 22/11). The party leader clicks the mode and chooses a map, in a row for each
 * of its modes (the 4s, then the 2s); then every player of the party has the team menu (the colours
 * of the map) for the countdown of the mode, 20 seconds, and the game starts by itself with the
 * teams of {@link TeamRoom}, in no stats. /pg team opens the menu again. A party that changes, or a
 * player who leaves the lobby, cancels it. Main thread only.
 */
final class TeamRooms {

    /** "Closed the menu? [CHOOSE YOUR TEAM]", the button running /pg team. */
    static final String TEAM_BUTTON = "[\"\",{\"text\":\"Closed the menu? \",\"color\":\"gray\"},"
            + "{\"text\":\"[CHOOSE YOUR TEAM]\",\"color\":\"green\",\"bold\":true,"
            + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/pg team\"},"
            + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"Open the team menu\"}}]";
    /** The rows of a chest menu at most. */
    private static final int MAX_ROWS = 6;
    /** Where the teams stand in their menu: the middle row, two slots apart when they are 4 or fewer. */
    private static final int TEAM_ROW = 9;

    /** A party choosing its teams on its map. */
    static final class Room {
        final GameMode menuMode;
        final GameMode mode;
        final String map;
        final UUID party;
        final TeamRoom teams;
        final List<ItemStack> icons;
        int secondsLeft;
        /** The team menu of each player who has it open: brought up to date at each choice. */
        final Map<UUID, ClickMenu> menus = new HashMap<>();

        Room(GameMode menuMode, GameMode mode, String map, UUID party, TeamRoom teams, List<ItemStack> icons, int seconds) {
            this.menuMode = menuMode;
            this.mode = mode;
            this.map = map;
            this.party = party;
            this.teams = teams;
            this.icons = icons;
            this.secondsLeft = seconds;
        }

        /** The name of a team in its colour: the name of its icon ("§cRed"), or "Team 3". */
        String teamName(int team) {
            ItemMeta meta = icons.get(team).getItemMeta();
            return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : ChatColor.WHITE + "Team " + (team + 1);
        }
    }

    private final QueueController queues;
    private final LobbySettings settings;
    private final LobbyState state;
    private final PartyBook parties;
    private final RagoLobby lobby;
    private final Logger logger;
    private final List<Room> rooms = new ArrayList<>();

    TeamRooms(QueueController queues, LobbySettings settings, LobbyState state, PartyBook parties, RagoLobby lobby) {
        this.queues = queues;
        this.settings = settings;
        this.state = state;
        this.parties = parties;
        this.lobby = lobby;
        this.logger = lobby.getLogger();
    }

    /** True for a mode of parties-privees-equipes. */
    boolean handles(String modeId) {
        return settings.teamRooms().containsKey(modeId);
    }

    /**
     * The mode of parties-privees-equipes whose maps this hidden mode gives (Bedwars Normal for its
     * 4s and 2s, cache: true), or null: such a mode is played only from it, with a party, never from
     * a queue. A mode of the selector keeps its own queue.
     */
    GameMode roomOfTarget(String modeId) {
        ModeDisplay display = settings.display(modeId);
        if (display == null || !display.hidden()) {
            return null;
        }
        for (String id : settings.teamRooms().keySet()) {
            for (GameMode target : settings.privateRoutes().get(id)) {
                if (target.id().equals(modeId)) {
                    return settings.catalog().get(id);
                }
            }
        }
        return null;
    }

    /** The room this player chooses their team in, or null. */
    Room roomOf(UUID player) {
        for (Room room : rooms) {
            if (room.teams.players().contains(player)) {
                return room;
            }
        }
        return null;
    }

    // --- The map ----------------------------------------------------------------------------------

    /** The player clicked the mode: the leader of a party in the lobby chooses its map. */
    void open(Player player, GameMode menuMode) {
        if (roomOf(player.getUniqueId()) != null) {
            openTeams(player);
            return;
        }
        List<Player> members = members(player, menuMode);
        if (members == null) {
            return;
        }
        List<GameMode> modes = settings.privateRoutes().get(menuMode.id());
        List<List<String>> maps = new ArrayList<>();
        int rows = 0;
        for (GameMode mode : modes) {
            List<String> of = queues.hostOf(mode.id()) == null ? new ArrayList<String>() : queues.mapsOf(mode);
            maps.add(of);
            rows += rowsOf(of.size());
        }
        ClickMenu menu = new ClickMenu(Math.min(MAX_ROWS, rows), ChatColor.stripColor(menuMode.name()) + ": choose a map");
        int row = 0;
        for (int m = 0; m < modes.size() && row < MAX_ROWS; m++) {
            GameMode mode = modes.get(m);
            List<String> of = maps.get(m);
            int slot = row * 9;
            menu.set(slot, ClickMenu.item(Material.BED, ChatColor.GOLD + format(mode) + ChatColor.GRAY + " (" + teamsText(mode) + ")",
                    of.isEmpty() ? "No map of this size on the server yet." : "The maps of " + mode.teams() + " teams:",
                    of.isEmpty() ? "" : "click one to play it with your party."), null);
            for (String map : of) {
                slot++;
                if (slot % 9 == 0) {
                    // a new row of the same size: its first slot stays empty, under the label
                    slot++;
                }
                if (slot >= MAX_ROWS * 9) {
                    logger.warning(menuMode.id() + ": too many maps for one menu, " + map + " and the next ones are left out");
                    break;
                }
                final GameMode chosenMode = mode;
                final String chosenMap = map;
                boolean fits = members.size() <= mode.playersPerMatch();
                menu.set(slot, ClickMenu.item(Material.EMPTY_MAP, (fits ? ChatColor.GREEN : ChatColor.RED) + map,
                        teamsText(mode), fits ? "Click to play it with your party." : "Your party is too big for it."),
                        fits ? (who, right) -> chooseMap(who, menuMode, chosenMode, chosenMap) : null);
            }
            row += rowsOf(of.size());
        }
        menu.open(player);
    }

    /** The rows of the menu for a size with this many maps: the label, then 8 maps a row. */
    private static int rowsOf(int maps) {
        return Math.max(1, (maps + 7) / 8);
    }

    /** "4 teams of 4". */
    private static String teamsText(GameMode mode) {
        return mode.teams() + " teams of " + mode.playersPerTeam();
    }

    /** "4s", "2s": the size of a team, as the maps of Hypixel are named. */
    private String format(GameMode mode) {
        ModeDisplay display = settings.display(mode.id());
        return display != null ? display.format() : mode.playersPerTeam() + "s";
    }

    /**
     * The players of the party of this player, all in the lobby, if they may start the game now;
     * else null, the player told why.
     */
    private List<Player> members(Player player, GameMode menuMode) {
        PartyBook.Party party = parties.together(player.getUniqueId());
        if (party == null) {
            Parties.boxed(player, ChatColor.LIGHT_PURPLE + menuMode.name() + ChatColor.LIGHT_PURPLE
                    + " is played with your party: invite your friends with " + ChatColor.WHITE + "/party invite <name>"
                    + ChatColor.LIGHT_PURPLE + ", then click again. You choose the map, everyone chooses their team.");
            return null;
        }
        if (!party.isLeader(player.getUniqueId())) {
            Parties.boxed(player, ChatColor.RED + "Only the party leader can start a private game.");
            return null;
        }
        if (RagoBots.botOf(party) != null) {
            Parties.boxed(player, ChatColor.GRAY + BotSettings.NAME + ChatColor.RED + " does not play "
                    + menuMode.name() + ChatColor.RED + ": " + ChatColor.WHITE + "/p kick " + BotSettings.NAME
                    + ChatColor.RED + " first.");
            return null;
        }
        List<Player> players = new ArrayList<>();
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member == null || !state.isInLobby(member) || state.isInGame(member)) {
                Parties.boxed(player, ChatColor.RED + (member == null ? "A player of your party" : member.getName())
                        + " is not in the lobby: your party cannot start a game.");
                return null;
            }
            if (lobby.isFrozen(id)) {
                Parties.boxed(player, ChatColor.RED + member.getName() + " is frozen by the staff: your party cannot start a game.");
                return null;
            }
            if (roomOf(id) != null) {
                Parties.boxed(player, ChatColor.RED + member.getName() + " is choosing a team already.");
                return null;
            }
            players.add(member);
        }
        return players;
    }

    /** The leader chose the map: the team choice starts for the whole party. */
    private void chooseMap(Player leader, GameMode menuMode, GameMode mode, String map) {
        List<Player> members = members(leader, menuMode);
        if (members == null) {
            leader.closeInventory();
            return;
        }
        if (members.size() > mode.playersPerMatch()) {
            leader.closeInventory();
            Parties.boxed(leader, ChatColor.RED + "Your party has " + members.size() + " players: " + map
                    + " has room for " + mode.playersPerMatch() + ".");
            return;
        }
        List<UUID> ids = new ArrayList<>();
        for (Player member : members) {
            ids.add(member.getUniqueId());
        }
        PartyBook.Party party = parties.together(leader.getUniqueId());
        int seconds = settings.teamRooms().get(menuMode.id());
        Room room = new Room(menuMode, mode, map, party.id(), new TeamRoom(ids, mode.teams(), mode.playersPerTeam()),
                icons(mode, map), seconds);
        rooms.add(room);
        for (Player member : members) {
            member.closeInventory();
            Parties.boxed(member, ChatColor.LIGHT_PURPLE + leader.getName() + " started a private game of "
                    + menuMode.name() + " " + format(mode) + ChatColor.LIGHT_PURPLE + " on " + ChatColor.WHITE + map
                    + ChatColor.LIGHT_PURPLE + ": choose your team (" + teamsText(mode) + "). The game starts in "
                    + seconds + " seconds; with no choice, you go in the emptiest team.");
            // clickable through the vanilla /tellraw, as the other buttons of the lobby: names are letters, digits and _
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + member.getName() + " " + TEAM_BUTTON);
            openTeams(member);
        }
    }

    /** The colours of the map's teams, from its host; numbered white wool when it gives none. */
    private List<ItemStack> icons(GameMode mode, String map) {
        List<ItemStack> icons = new ArrayList<>();
        MatchHost host = queues.hostOf(mode.id());
        if (host != null) {
            try {
                List<ItemStack> given = host.teamIcons(mode, map);
                if (given != null && given.size() == mode.teams()) {
                    for (ItemStack icon : given) {
                        icons.add(icon.clone());
                    }
                    return icons;
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "The mode " + mode.id() + " could not give the teams of " + map, e);
            }
        }
        icons.clear();
        for (int t = 0; t < mode.teams(); t++) {
            icons.add(ClickMenu.item(Material.WOOL, ChatColor.WHITE + "Team " + (t + 1)));
        }
        return icons;
    }

    // --- The teams --------------------------------------------------------------------------------

    /** /pg team: the team menu of the player's room. */
    void openTeams(Player player) {
        Room room = roomOf(player.getUniqueId());
        if (room == null) {
            player.sendMessage(ChatColor.RED + "You are not choosing a team now: the party leader starts a private game"
                    + " of Bedwars Normal from the Private games.");
            return;
        }
        ClickMenu menu = new ClickMenu(3, "Choose your team");
        room.menus.put(player.getUniqueId(), menu);
        fill(room, menu, player.getUniqueId());
        menu.open(player);
    }

    /** The slot of a team in its menu: one row, the teams side by side, two slots apart when they are 4. */
    static int teamSlot(int team, int teams) {
        if (teams <= 4) {
            return TEAM_ROW + 1 + team * 2;
        }
        return TEAM_ROW + (9 - teams) / 2 + team;
    }

    private void fill(Room room, ClickMenu menu, UUID viewer) {
        int mine = room.teams.teamOf(viewer);
        for (int t = 0; t < room.teams.teamCount(); t++) {
            List<UUID> members = room.teams.membersOf(t);
            ItemStack icon = room.icons.get(t).clone();
            icon.setAmount(Math.max(1, members.size()));
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "" + members.size() + "/" + room.teams.teamSize() + " players");
                for (UUID member : members) {
                    Player online = Bukkit.getPlayer(member);
                    lore.add(ChatColor.WHITE + (online == null ? "?" : online.getName()));
                }
                lore.add("");
                lore.add(t == mine ? ChatColor.GREEN + "Your team." : members.size() >= room.teams.teamSize()
                        ? ChatColor.RED + "Full." : ChatColor.YELLOW + "Click to join it.");
                meta.setLore(lore);
                icon.setItemMeta(meta);
            }
            if (t == mine) {
                ClickMenu.glow(icon);
            }
            final int team = t;
            menu.set(teamSlot(t, room.teams.teamCount()), icon, (player, right) -> choose(player, team));
        }
        menu.set(22, ClickMenu.item(Material.WATCH, ChatColor.YELLOW + "The game starts in " + room.secondsLeft + " s",
                ChatColor.stripColor(room.menuMode.name()) + " " + format(room.mode) + " on " + room.map,
                "With no choice, you go in the emptiest team."), null);
    }

    private void choose(Player player, int team) {
        Room room = roomOf(player.getUniqueId());
        if (room == null) {
            player.closeInventory();
            return;
        }
        switch (room.teams.choose(player.getUniqueId(), team)) {
            case CHOSEN:
                player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
                tell(room, ChatColor.GRAY + player.getName() + " joined " + room.teamName(team) + ChatColor.GRAY + ".");
                refresh(room);
                break;
            case FULL:
                player.sendMessage(room.teamName(team) + ChatColor.RED + " is full.");
                break;
            case ALONE:
                player.sendMessage(ChatColor.RED + "Your whole party would be in " + room.teamName(team) + ChatColor.RED
                        + ": a game needs two teams. Choose another one.");
                break;
            default:
                break;
        }
    }

    /** The open team menus show the room as it is now. */
    private void refresh(Room room) {
        for (Iterator<Map.Entry<UUID, ClickMenu>> it = room.menus.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, ClickMenu> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            InventoryHolder open = player == null ? null : player.getOpenInventory().getTopInventory().getHolder();
            if (open != entry.getValue()) {
                it.remove();
                continue;
            }
            fill(room, entry.getValue(), entry.getKey());
        }
    }

    // --- The countdown ----------------------------------------------------------------------------

    /** Once a second: the countdowns go on, a room at 0 starts its game. */
    void tick() {
        for (Room room : new ArrayList<>(rooms)) {
            String why = broken(room);
            if (why != null) {
                cancel(room, why);
                continue;
            }
            room.secondsLeft--;
            if (room.secondsLeft > 0) {
                if (room.secondsLeft <= 5 || room.secondsLeft == 10) {
                    tell(room, ChatColor.YELLOW + "The game starts in " + ChatColor.RED + room.secondsLeft + ChatColor.YELLOW
                            + (room.secondsLeft == 1 ? " second..." : " seconds..."));
                    for (UUID id : room.teams.players()) {
                        Player player = Bukkit.getPlayer(id);
                        if (player != null) {
                            player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1f, 1f);
                        }
                    }
                }
                refresh(room);
                continue;
            }
            start(room);
        }
    }

    /** Why the room cannot go on (a player left the lobby, the party changed), or null. */
    private String broken(Room room) {
        PartyBook.Party party = null;
        for (UUID id : room.teams.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !state.isInLobby(player) || state.isInGame(player)) {
                return (player == null ? "A player" : player.getName()) + " left the lobby";
            }
            PartyBook.Party of = parties.of(id);
            if (of == null || !of.id().equals(room.party)) {
                return "The party changed";
            }
            party = of;
        }
        if (party == null || party.size() != room.teams.players().size()) {
            return "The party changed";
        }
        return null;
    }

    private void start(Room room) {
        rooms.remove(room);
        List<List<UUID>> chosen = room.teams.teams();
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = new ArrayList<>();
        List<String> sides = new ArrayList<>();
        for (int t = 0; t < chosen.size(); t++) {
            List<QueueEntry> team = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (UUID id : chosen.get(t)) {
                team.add(new QueueEntry(id, settings.defaultRating(), now, room.map, room.party));
                Player player = Bukkit.getPlayer(id);
                names.add(player == null ? "?" : player.getName());
            }
            teams.add(team);
            if (!names.isEmpty()) {
                sides.add(room.teamName(t) + ChatColor.GRAY + ": " + ChatColor.WHITE + String.join(", ", names));
            }
        }
        for (UUID id : room.teams.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (room.menus.containsKey(id) && player.getOpenInventory().getTopInventory().getHolder() == room.menus.get(id)) {
                player.closeInventory();
            }
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Private game of your party in " + room.menuMode.name() + " "
                    + format(room.mode) + ChatColor.LIGHT_PURPLE + " on " + ChatColor.WHITE + room.map
                    + ChatColor.LIGHT_PURPLE + ". It counts in no stats.");
            for (String side : sides) {
                player.sendMessage(ChatColor.GRAY + " " + side);
            }
        }
        MatchProposal match;
        try {
            match = MatchProposal.withChosenTeams(room.mode, teams);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "The teams of a private game of " + room.mode.id() + " were wrong", e);
            tell(room, ChatColor.RED + "The game could not start: try again.");
            return;
        }
        queues.startDirect(match, true);
    }

    private void cancel(Room room, String why) {
        rooms.remove(room);
        for (UUID id : room.teams.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                continue;
            }
            if (room.menus.containsKey(id) && player.getOpenInventory().getTopInventory().getHolder() == room.menus.get(id)) {
                player.closeInventory();
            }
            player.sendMessage(ChatColor.RED + why + ": the private game of " + room.menuMode.name() + ChatColor.RED
                    + " is cancelled.");
        }
    }

    /** The players of a party changed: their room, if they choose their teams, is cancelled. */
    void partyChanged(Collection<UUID> players) {
        Set<Room> broken = new HashSet<>();
        for (UUID id : players) {
            Room room = roomOf(id);
            if (room != null) {
                broken.add(room);
            }
        }
        for (Room room : broken) {
            cancel(room, "The party changed");
        }
    }

    private static void tell(Room room, String message) {
        for (UUID id : room.teams.players()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    /** The rooms open now. */
    List<Room> rooms() {
        return new ArrayList<>(rooms);
    }
}
