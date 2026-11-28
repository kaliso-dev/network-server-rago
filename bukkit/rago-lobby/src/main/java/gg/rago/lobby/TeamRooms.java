package gg.rago.lobby;

import gg.rago.game.GameMode;
import gg.rago.game.MatchProposal;
import gg.rago.game.QueueEntry;
import gg.rago.lobby.api.MatchHost;
import gg.rago.lobby.api.PrivateSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
 * owner's answers of 22/11). The party leader clicks the mode and chooses a map in its menu, in the
 * lobby, in a row for each of its modes (the 4s, then the 2s); then the whole party goes to the
 * waiting lobby over that map, as for a game (the owner's ask of 25/11: « dès qu'il choisit la map ça
 * le tp lui et les membres de la party sur le lobby d'attente au-dessus de la map qu'il a choisie »,
 * {@link MatchHost#gather}), where every player has the team menu (the colours of the map; the item
 * of the teams) for the countdown of the mode, 30 seconds (15 once everyone has chosen, the owner's
 * ask of 23/11), while the leader sets the game; then the game starts by itself on that map with the
 * teams of {@link TeamRoom}, in no stats. /pg team opens the menu again. A party that changes, or a
 * player who leaves the waiting lobby (/spawn, the server), cancels it: the others go back to the
 * lobby. A host without a waiting lobby for that map ({@link MatchHost.Gathering#NONE}): the teams
 * are chosen from the lobby, as before. The leader sets the game (Speed, Max Diamond...: the
 * settings its host offers, {@link MatchHost#privateSettings}) from the map menu, the team menu, the
 * item of the settings or /pg settings; they are kept for the leader's next games until they leave
 * the server. Main thread only.
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
    /** The settings of the game in the team menu, for the leader: its last slot. */
    private static final int SETTINGS_SLOT = 26;
    /** The items of the waiting lobby, by their names: the settings for the leader, the teams for everyone. */
    static final String SETTINGS_ITEM = ChatColor.GREEN + "Game settings";
    static final String TEAM_ITEM = ChatColor.GREEN + "Choose your team";
    /** Their slots in the hotbar. */
    static final int SETTINGS_ITEM_SLOT = 1;
    static final int TEAM_ITEM_SLOT = 4;

    /** A party in its private game: in its waiting lobby, or choosing its teams on its map. */
    static final class Room {
        final GameMode menuMode;
        /**
         * The mode of the map chosen, whose host has the waiting lobby of the party, or null: no
         * waiting lobby, the teams chosen from the lobby, as before.
         */
        final GameMode hostMode;
        final UUID party;
        /** The party leader who started it: who chooses the map and sets the game. */
        final UUID leader;
        /** The players, in the order of the party (its leader first). */
        final List<UUID> players;
        /** True once the party is in its waiting lobby. */
        boolean gathered;
        /** Until when the party waits in the lobby for its waiting lobby to be ready (milliseconds). */
        long gatherUntil;
        /** The mode and the map the leader chose, the teams and their colours: null until then. */
        GameMode mode;
        String map;
        TeamRoom teams;
        List<ItemStack> icons;
        int secondsLeft;
        /**
         * The teams and the game, once the countdown is over (the teams no longer change), and until
         * when it waits for its map (milliseconds; 0: not yet).
         */
        List<List<UUID>> chosen;
        MatchProposal match;
        long startUntil;
        /** The team menu of each player who has it open: brought up to date at each choice. */
        final Map<UUID, ClickMenu> menus = new HashMap<>();

        Room(GameMode menuMode, GameMode hostMode, UUID party, UUID leader, List<UUID> players) {
            this.menuMode = menuMode;
            this.hostMode = hostMode;
            this.party = party;
            this.leader = leader;
            this.players = Collections.unmodifiableList(new ArrayList<>(players));
        }

        /** The leader chose this map of this mode: its teams are chosen for this many seconds. */
        void mapChosen(GameMode mode, String map, TeamRoom teams, List<ItemStack> icons, int seconds) {
            this.mode = mode;
            this.map = map;
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
    /** The settings each leader chose (setting id: index of its choice), for their next games. */
    private final Map<UUID, Map<String, Integer>> chosenSettings = new HashMap<>();

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

    /** The room of this player (their private game being set, in its waiting lobby or in the lobby), or null. */
    Room roomOf(UUID player) {
        for (Room room : rooms) {
            if (room.players.contains(player)) {
                return room;
            }
        }
        return null;
    }

    // --- The map ----------------------------------------------------------------------------------

    /**
     * The player clicked the mode: the map menu, in the lobby (the leader chooses the map there, then
     * the party goes to the waiting lobby over it). In a room already: the team menu, once there.
     */
    void open(Player player, GameMode menuMode) {
        Room room = roomOf(player.getUniqueId());
        if (room != null) {
            if (room.hostMode != null && !room.gathered) {
                player.sendMessage(ChatColor.GRAY + "The waiting lobby of the private game is being prepared: in a moment.");
            } else {
                openTeams(player);
            }
            return;
        }
        List<Player> members = members(player, menuMode);
        if (members == null) {
            return;
        }
        openMaps(player, menuMode, members.size());
    }

    /** May a party go to the waiting lobby of the room's map now? NONE without a host, or if it fails to answer. */
    private MatchHost.Gathering canGather(Room room) {
        MatchHost host = queues.hostOf(room.mode.id());
        if (host == null) {
            return MatchHost.Gathering.NONE;
        }
        try {
            MatchHost.Gathering answer = host.canGather(room.mode, room.map);
            return answer == null ? MatchHost.Gathering.NONE : answer;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + room.mode.id() + " could not tell if its waiting lobby is ready", e);
            return MatchHost.Gathering.NONE;
        }
    }

    /**
     * The party goes to its waiting lobby: it belongs to the host's game from now on; the items of
     * the waiting lobby, and the team menu for everyone. If the host cannot take it, the private
     * game is cancelled, the players still in the lobby.
     */
    private void gather(Room room) {
        List<Player> players = new ArrayList<>();
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                cancel(room, "A player left the lobby");
                return;
            }
            players.add(player);
        }
        for (Player player : players) {
            lobby.enterGame(player, room.hostMode.id());
        }
        MatchHost host = queues.hostOf(room.hostMode.id());
        boolean gathered;
        try {
            gathered = host != null && host.gather(room.mode, room.map, players);
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "The mode " + room.hostMode.id() + " could not take a party to its waiting lobby", e);
            gathered = false;
        }
        if (!gathered) {
            for (Player player : players) {
                lobby.cancelGame(player);
            }
            cancel(room, "The waiting lobby could not open");
            return;
        }
        room.gathered = true;
        for (Player player : players) {
            giveItems(room, player);
        }
        startChoosing(room);
    }

    /** The items of the waiting lobby: the settings for the leader, the teams for everyone. */
    private void giveItems(Room room, Player player) {
        if (player.getUniqueId().equals(room.leader)) {
            if (!settingsOf(modesOf(room)).isEmpty()) {
                player.getInventory().setItem(SETTINGS_ITEM_SLOT, ClickMenu.item(Material.REDSTONE_COMPARATOR, SETTINGS_ITEM,
                        "Right-click to set the game."));
            }
        }
        player.getInventory().setItem(TEAM_ITEM_SLOT, ClickMenu.item(Material.WOOL, TEAM_ITEM,
                "Right-click to choose your team."));
        player.updateInventory();
    }

    /** A right-click with an item of the waiting lobby: its menu. False if it is not one, or the player is in no waiting lobby. */
    boolean useItem(Player player, ItemStack item) {
        Room room = roomOf(player.getUniqueId());
        if (room == null || !room.gathered || item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        // ClickMenu.item names them after a reset of the colours
        String name = item.getItemMeta().getDisplayName();
        if (name.equals(ChatColor.RESET + SETTINGS_ITEM)) {
            openSettings(player);
        } else if (name.equals(ChatColor.RESET + TEAM_ITEM)) {
            openTeams(player);
        } else {
            return false;
        }
        player.updateInventory();
        return true;
    }

    /** The modes whose settings the leader of this room sets: the mode of its map. */
    private List<GameMode> modesOf(Room room) {
        return Collections.singletonList(room.mode);
    }

    private static String nameOf(UUID player) {
        Player online = Bukkit.getPlayer(player);
        return online == null ? "The party leader" : online.getName();
    }

    /** The map menu of the leader: a row for each mode (the 4s, then the 2s), its maps for a party of this size. */
    private void openMaps(Player player, GameMode menuMode, int partySize) {
        List<GameMode> modes = settings.privateRoutes().get(menuMode.id());
        List<List<String>> maps = new ArrayList<>();
        int rows = 0;
        for (GameMode mode : modes) {
            List<String> of = queues.hostOf(mode.id()) == null ? new ArrayList<String>() : queues.mapsOf(mode);
            maps.add(of);
            rows += rowsOf(of.size());
        }
        List<PrivateSetting> offered = settingsOf(modes);
        if (!offered.isEmpty()) {
            // the last row: the settings of the game
            rows++;
        }
        ClickMenu menu = new ClickMenu(Math.min(MAX_ROWS, rows), ChatColor.stripColor(menuMode.name()) + ": choose a map");
        int mapRows = offered.isEmpty() ? MAX_ROWS : MAX_ROWS - 1;
        int row = 0;
        for (int m = 0; m < modes.size() && row < mapRows; m++) {
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
                if (slot >= mapRows * 9) {
                    logger.warning(menuMode.id() + ": too many maps for one menu, " + map + " and the next ones are left out");
                    break;
                }
                final GameMode chosenMode = mode;
                final String chosenMap = map;
                boolean fits = partySize <= mode.playersPerMatch();
                menu.set(slot, ClickMenu.item(Material.EMPTY_MAP, (fits ? ChatColor.GREEN : ChatColor.RED) + map,
                        teamsText(mode), fits ? "Click to play it with your party." : "Your party is too big for it."),
                        fits ? (who, right) -> chooseMap(who, menuMode, chosenMode, chosenMap) : null);
            }
            row += rowsOf(of.size());
        }
        if (!offered.isEmpty()) {
            menu.set((menu.size() / 9 - 1) * 9 + 4, settingsItem(player.getUniqueId(), offered),
                    (who, right) -> openSettings(who, menuMode, roomOf(who.getUniqueId())));
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

    /**
     * The leader chose the map, in the lobby: the party goes to the waiting lobby over it, where the
     * team choice starts (with no waiting lobby for it: here, as before).
     */
    private void chooseMap(Player leader, GameMode menuMode, GameMode mode, String map) {
        if (roomOf(leader.getUniqueId()) != null) {
            leader.closeInventory();
            return;
        }
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
        leader.closeInventory();
        List<UUID> ids = new ArrayList<>();
        for (Player member : members) {
            ids.add(member.getUniqueId());
        }
        int seconds = settings.teamRooms().get(menuMode.id());
        Room probe = new Room(menuMode, mode, parties.together(leader.getUniqueId()).id(), leader.getUniqueId(), ids);
        probe.mapChosen(mode, map, new TeamRoom(probe.players, mode.teams(), mode.playersPerTeam()), icons(mode, map), seconds);
        MatchHost.Gathering gathering = canGather(probe);
        Room room = probe;
        if (gathering == MatchHost.Gathering.NONE) {
            // no waiting lobby for this map: the teams chosen from the lobby, as before
            room = new Room(menuMode, null, probe.party, probe.leader, ids);
            room.mapChosen(mode, map, probe.teams, probe.icons, seconds);
        }
        rooms.add(room);
        if (gathering == MatchHost.Gathering.NONE) {
            startChoosing(room);
        } else if (gathering == MatchHost.Gathering.READY) {
            gather(room);
        } else {
            room.gatherUntil = System.currentTimeMillis() + QueueController.DIRECT_WAIT_MILLIS;
            tell(room, ChatColor.GRAY + "The waiting lobby of the private game is being prepared: in a moment.");
        }
    }

    /** The team choice starts for the whole party: told, the button of /pg team, the team menu. */
    private void startChoosing(Room room) {
        String leader = nameOf(room.leader);
        for (UUID id : room.players) {
            Player member = Bukkit.getPlayer(id);
            if (member == null) {
                continue;
            }
            Parties.boxed(member, ChatColor.LIGHT_PURPLE + leader + " started a private game of "
                    + room.menuMode.name() + " " + format(room.mode) + ChatColor.LIGHT_PURPLE + " on " + ChatColor.WHITE + room.map
                    + ChatColor.LIGHT_PURPLE + ": choose your team (" + teamsText(room.mode) + "). The game starts in "
                    + room.secondsLeft + " seconds; with no choice, you go in the emptiest team."
                    + (room.gathered ? " " + leader + " sets the game meanwhile; /spawn takes you back to the lobby." : ""));
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
        if (room.map == null) {
            player.sendMessage(ChatColor.GRAY + nameOf(room.leader) + " has not chosen the map yet: the teams are its colours.");
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
        List<PrivateSetting> offered = settingsOf(Collections.singletonList(room.mode));
        if (viewer.equals(room.leader) && !offered.isEmpty()) {
            menu.set(SETTINGS_SLOT, settingsItem(viewer, offered), (player, right) -> openSettings(player, room.menuMode, room));
        }
    }

    private void choose(Player player, int team) {
        Room room = roomOf(player.getUniqueId());
        if (room == null || room.teams == null) {
            player.closeInventory();
            return;
        }
        if (room.match != null) {
            player.sendMessage(ChatColor.GRAY + "The teams are set: the game starts in a moment.");
            return;
        }
        switch (room.teams.choose(player.getUniqueId(), team)) {
            case CHOSEN:
                player.playSound(player.getLocation(), Sound.CLICK, 1f, 1f);
                tell(room, ChatColor.GRAY + player.getName() + " joined " + room.teamName(team) + ChatColor.GRAY + ".");
                if (room.teams.allChose() && TeamRoom.secondsOnceAllChose(room.secondsLeft) < room.secondsLeft) {
                    room.secondsLeft = TeamRoom.secondsOnceAllChose(room.secondsLeft);
                    tell(room, ChatColor.YELLOW + "Everyone chose a team: the game starts in " + ChatColor.RED
                            + room.secondsLeft + ChatColor.YELLOW + " seconds.");
                }
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

    // --- The settings of the leader -----------------------------------------------------------------

    /** The settings the hosts of these modes offer: those of the first that has some. */
    private List<PrivateSetting> settingsOf(List<GameMode> modes) {
        for (GameMode mode : modes) {
            MatchHost host = queues.hostOf(mode.id());
            if (host == null) {
                continue;
            }
            try {
                List<PrivateSetting> offered = host.privateSettings(mode);
                if (offered != null && !offered.isEmpty()) {
                    return offered;
                }
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "The mode " + mode.id() + " could not give its settings", e);
            }
        }
        return Collections.emptyList();
    }

    /**
     * The choice of this leader for this setting: the index of its choice (the bits of the items
     * ticked for items to tick), 0 (a normal game) by default.
     */
    private int choiceOf(UUID leader, PrivateSetting setting) {
        Map<String, Integer> chosen = chosenSettings.get(leader);
        Integer index = chosen == null ? null : chosen.get(setting.id);
        return index == null || !setting.valid(index) ? 0 : index;
    }

    /** The choices of this leader for these settings, those not as in a normal game only. */
    private Map<String, Integer> settingsFor(UUID leader, List<PrivateSetting> offered) {
        Map<String, Integer> chosen = new HashMap<>();
        for (PrivateSetting setting : offered) {
            int index = choiceOf(leader, setting);
            if (index != 0) {
                chosen.put(setting.id, index);
            }
        }
        return chosen;
    }

    /** "Speed II, Max Diamond: On": the settings of this leader not as in a normal game, or "". */
    private String changed(UUID leader, List<PrivateSetting> offered) {
        List<String> parts = new ArrayList<>();
        for (PrivateSetting setting : offered) {
            int index = choiceOf(leader, setting);
            if (index != 0) {
                parts.add(setting.name + ": " + setting.describe(index));
            }
        }
        return String.join(", ", parts);
    }

    /** The item that opens the settings: what is set now. */
    private ItemStack settingsItem(UUID leader, List<PrivateSetting> offered) {
        List<String> lore = new ArrayList<>();
        lore.add("Set the game, as the host on Hypixel.");
        lore.add("");
        for (PrivateSetting setting : offered) {
            int index = choiceOf(leader, setting);
            lore.add(ChatColor.GRAY + setting.name + ": " + (index == 0 ? ChatColor.WHITE : ChatColor.GREEN)
                    + setting.describe(index));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to set the game.");
        return ClickMenu.item(Material.REDSTONE_COMPARATOR, 0, ChatColor.GREEN + "Game settings", lore);
    }

    /** /pg settings: the settings of the game, for the leader of a party. */
    void openSettings(Player player) {
        Room room = roomOf(player.getUniqueId());
        if (room != null) {
            openSettings(player, room.menuMode, room);
            return;
        }
        for (String id : settings.teamRooms().keySet()) {
            GameMode menuMode = settings.catalog().get(id);
            if (menuMode != null && !settingsOf(settings.privateRoutes().get(id)).isEmpty()) {
                openSettings(player, menuMode, null);
                return;
            }
        }
        player.sendMessage(ChatColor.RED + "No private game has settings on this server.");
    }

    /**
     * The settings menu of the leader: each setting, a click for the next choice, a right-click for
     * the one before (for items to tick, a click opens them: {@link #openToggles}); the party is
     * told each change while it chooses its teams.
     */
    private void openSettings(Player player, GameMode menuMode, Room room) {
        PartyBook.Party party = parties.together(player.getUniqueId());
        if (room != null ? !player.getUniqueId().equals(room.leader) : party == null || !party.isLeader(player.getUniqueId())) {
            player.closeInventory();
            Parties.boxed(player, ChatColor.RED + "Only the party leader sets the game of a private game.");
            return;
        }
        List<PrivateSetting> offered = settingsOf(room != null ? modesOf(room) : settings.privateRoutes().get(menuMode.id()));
        if (offered.isEmpty()) {
            player.sendMessage(ChatColor.RED + ChatColor.stripColor(menuMode.name()) + " has no settings.");
            return;
        }
        int settingRows = offered.size() <= 4 ? 1 : (offered.size() + 6) / 7;
        ClickMenu menu = new ClickMenu(Math.min(MAX_ROWS, settingRows + 2), "Game settings");
        fillSettings(menu, player.getUniqueId(), menuMode, room, offered);
        menu.open(player);
    }

    /** The slot of a setting in its menu: from the second row, side by side, two apart when they are 4 or fewer. */
    static int settingSlot(int setting, int settings) {
        if (settings <= 4) {
            return 9 + 1 + setting * 2 + (4 - settings);
        }
        return 9 * (1 + setting / 7) + 1 + setting % 7;
    }

    private void fillSettings(ClickMenu menu, UUID leader, GameMode menuMode, Room room, List<PrivateSetting> offered) {
        for (int i = 0; i < offered.size(); i++) {
            PrivateSetting setting = offered.get(i);
            int index = choiceOf(leader, setting);
            List<String> lore = new ArrayList<>();
            for (String line : setting.description) {
                lore.add(ChatColor.GRAY + line);
            }
            lore.add("");
            for (int c = 0; c < setting.choices.size(); c++) {
                boolean on = setting.toggles ? (index & 1 << c) != 0 : c == index;
                lore.add(on ? ChatColor.GREEN + "> " + setting.choices.get(c) : ChatColor.GRAY + "  " + setting.choices.get(c));
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + (setting.toggles ? "Click to choose them." : "Click: next. Right-click: back."));
            String shown = setting.toggles && index != 0 ? Integer.bitCount(index) + "/" + setting.choices.size()
                    : setting.describe(index);
            ItemStack item = ClickMenu.item(setting.icon, 0, (index == 0 ? ChatColor.WHITE : ChatColor.GREEN) + setting.name
                    + ChatColor.GRAY + ": " + (index == 0 ? ChatColor.WHITE : ChatColor.GREEN) + shown, lore);
            if (index != 0) {
                ClickMenu.glow(item);
            }
            final PrivateSetting chosen = setting;
            int slot = settingSlot(i, offered.size());
            if (slot < menu.size() - 9) {
                menu.set(slot, item, (who, right) -> {
                    if (chosen.toggles) {
                        openToggles(who, menuMode, room, offered, chosen);
                        return;
                    }
                    choose(who, chosen, right, room);
                    fillSettings(menu, leader, menuMode, room, offered);
                });
            }
        }
        menu.set(menu.size() - 5, ClickMenu.back(room != null && room.map != null ? "the teams" : "the maps"),
                (who, right) -> {
                    if (room != null && !rooms.contains(room)) {
                        who.closeInventory();
                    } else if (room != null && room.map != null) {
                        openTeams(who);
                    } else {
                        open(who, menuMode);
                    }
                });
    }

    /** The leader clicked a setting: its next choice (right: the one before), told to the party. */
    private void choose(Player leader, PrivateSetting setting, boolean back, Room room) {
        int count = setting.choices.size();
        set(leader, setting, (choiceOf(leader.getUniqueId(), setting) + (back ? count - 1 : 1)) % count, room);
    }

    /**
     * The menu of a setting of items to tick (One Hit One Kill: which weapons kill in one hit),
     * for the leader: each item with its icon, a click turns it on or off; the arrow back to the
     * settings.
     */
    private void openToggles(Player player, GameMode menuMode, Room room, List<PrivateSetting> offered,
            PrivateSetting setting) {
        if (room != null ? !rooms.contains(room) || !player.getUniqueId().equals(room.leader) : !leads(player)) {
            player.closeInventory();
            return;
        }
        int itemRows = setting.choices.size() <= 4 ? 1 : (setting.choices.size() + 6) / 7;
        ClickMenu menu = new ClickMenu(Math.min(MAX_ROWS, itemRows + 2), setting.name);
        fillToggles(menu, player.getUniqueId(), menuMode, room, offered, setting);
        menu.open(player);
    }

    private boolean leads(Player player) {
        PartyBook.Party party = parties.together(player.getUniqueId());
        return party != null && party.isLeader(player.getUniqueId());
    }

    private void fillToggles(ClickMenu menu, UUID leader, GameMode menuMode, Room room, List<PrivateSetting> offered,
            PrivateSetting setting) {
        int ticked = choiceOf(leader, setting);
        for (int i = 0; i < setting.choices.size(); i++) {
            boolean on = (ticked & 1 << i) != 0;
            String name = setting.choices.get(i);
            ItemStack item = ClickMenu.item(setting.icons.get(i), 0, (on ? ChatColor.GREEN : ChatColor.RED) + name
                    + ChatColor.GRAY + ": " + (on ? ChatColor.GREEN + "On" : ChatColor.RED + "Off"),
                    Arrays.asList(ChatColor.GRAY + setting.name + ".", "",
                            ChatColor.YELLOW + (on ? "Click to turn it off." : "Click to turn it on.")));
            if (on) {
                ClickMenu.glow(item);
            }
            final int bit = 1 << i;
            int slot = settingSlot(i, setting.choices.size());
            if (slot < menu.size() - 9) {
                menu.set(slot, item, (who, right) -> {
                    if (room != null ? !rooms.contains(room) || !who.getUniqueId().equals(room.leader) : !leads(who)) {
                        who.closeInventory();
                        return;
                    }
                    set(who, setting, choiceOf(who.getUniqueId(), setting) ^ bit, room);
                    fillToggles(menu, leader, menuMode, room, offered, setting);
                });
            }
        }
        menu.set(menu.size() - 5, ClickMenu.back("the settings"), (who, right) -> openSettings(who, menuMode, room));
    }

    /** The leader's choice for this setting is now this one, told to the party. */
    private void set(Player leader, PrivateSetting setting, int index, Room room) {
        chosenSettings.computeIfAbsent(leader.getUniqueId(), id -> new HashMap<>()).put(setting.id, index);
        leader.playSound(leader.getLocation(), Sound.CLICK, 1f, 1f);
        String told = ChatColor.GRAY + leader.getName() + " set " + ChatColor.WHITE + setting.name + ChatColor.GRAY + ": "
                + (index == 0 ? ChatColor.WHITE : ChatColor.GREEN) + setting.describe(index) + ChatColor.GRAY + ".";
        if (room != null && rooms.contains(room)) {
            tell(room, told);
            refresh(room);
        } else {
            leader.sendMessage(told);
        }
    }

    /** The player left the server: their settings go. */
    void forget(UUID player) {
        chosenSettings.remove(player);
    }

    // --- The countdown ----------------------------------------------------------------------------

    /**
     * Once a second: a party waiting for its waiting lobby goes to it once it is ready; the
     * countdowns go on, a room at 0 starts its game (once its map is ready).
     */
    void tick() {
        for (Room room : new ArrayList<>(rooms)) {
            if (!rooms.contains(room)) {
                continue;
            }
            String why = broken(room);
            if (why != null) {
                cancel(room, why);
                continue;
            }
            if (room.hostMode != null && !room.gathered) {
                waitForWaitingLobby(room);
                continue;
            }
            if (room.secondsLeft > 0) {
                room.secondsLeft--;
                if (room.secondsLeft > 0) {
                    if (room.secondsLeft <= 5 || room.secondsLeft == 10) {
                        tell(room, ChatColor.YELLOW + "The game starts in " + ChatColor.RED + room.secondsLeft + ChatColor.YELLOW
                                + (room.secondsLeft == 1 ? " second..." : " seconds..."));
                        for (UUID id : room.players) {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) {
                                player.playSound(player.getLocation(), Sound.NOTE_STICKS, 1f, 1f);
                            }
                        }
                    }
                    refresh(room);
                    continue;
                }
            }
            start(room);
        }
    }

    /** The party waits in the lobby for its waiting lobby: in it once it is ready, or the game cancelled after a while. */
    private void waitForWaitingLobby(Room room) {
        MatchHost.Gathering gathering = canGather(room);
        if (gathering == MatchHost.Gathering.READY) {
            gather(room);
        } else if (gathering == MatchHost.Gathering.NONE) {
            cancel(room, "The waiting lobby is not there any more");
        } else if (System.currentTimeMillis() > room.gatherUntil) {
            cancel(room, "No waiting lobby is free right now");
        }
    }

    /** Why the room cannot go on (a player left the waiting lobby or the lobby, the party changed), or null. */
    private String broken(Room room) {
        PartyBook.Party party = null;
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (room.gathered) {
                // in the waiting lobby: in the game of its host until the game starts
                if (player == null || !room.hostMode.id().equals(state.gameOf(id))) {
                    return (player == null ? "A player" : player.getName()) + " left the waiting lobby";
                }
            } else if (player == null || !state.isInLobby(player) || state.isInGame(player)) {
                return (player == null ? "A player" : player.getName()) + " left the lobby";
            }
            PartyBook.Party of = parties.of(id);
            if (of == null || !of.id().equals(room.party)) {
                return "The party changed";
            }
            party = of;
        }
        if (party == null || party.size() != room.players.size()) {
            return "The party changed";
        }
        return null;
    }

    private void start(Room room) {
        if (room.match == null) {
            room.match = matchOf(room);
            if (room.match == null) {
                cancel(room, "The game could not start");
                return;
            }
        }
        if (room.gathered && !queues.mapReady(room.match)) {
            // a copy of the map is being made: the party waits for it in the waiting lobby
            long now = System.currentTimeMillis();
            if (room.startUntil == 0) {
                room.startUntil = now + QueueController.DIRECT_WAIT_MILLIS;
                tell(room, ChatColor.GRAY + "The map is being prepared: the game starts in a moment.");
            } else if (now > room.startUntil) {
                cancel(room, "No free arena right now");
            }
            return;
        }
        rooms.remove(room);
        List<List<UUID>> chosen = room.chosen;
        List<String> sides = new ArrayList<>();
        for (int t = 0; t < chosen.size(); t++) {
            List<String> names = new ArrayList<>();
            for (UUID id : chosen.get(t)) {
                Player player = Bukkit.getPlayer(id);
                names.add(player == null ? "?" : player.getName());
            }
            if (!names.isEmpty()) {
                sides.add(room.teamName(t) + ChatColor.GRAY + ": " + ChatColor.WHITE + String.join(", ", names));
            }
        }
        String settingsLine = changed(room.leader, settingsOf(Collections.singletonList(room.mode)));
        for (UUID id : room.players) {
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
            if (!settingsLine.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + " Settings: " + ChatColor.WHITE + settingsLine);
            }
        }
        Map<String, Integer> set = settingsFor(room.leader, settingsOf(Collections.singletonList(room.mode)));
        if (room.gathered) {
            // from the waiting lobby to the map; if it does not start, back to the lobby
            if (!queues.startFromWaitingLobby(room.match, set.isEmpty() ? null : set)) {
                backToLobby(room);
            }
        } else if (set.isEmpty()) {
            queues.startDirect(room.match, true);
        } else {
            queues.startDirect(room.match, set);
        }
    }

    /** The game of the room, its teams as chosen; null (told in the log) if they are wrong. */
    private MatchProposal matchOf(Room room) {
        List<List<UUID>> chosen = room.teams.teams();
        room.chosen = chosen;
        long now = System.currentTimeMillis();
        List<List<QueueEntry>> teams = new ArrayList<>();
        for (List<UUID> team : chosen) {
            List<QueueEntry> entries = new ArrayList<>();
            for (UUID id : team) {
                entries.add(new QueueEntry(id, settings.defaultRating(), now, room.map, room.party));
            }
            teams.add(entries);
        }
        try {
            return MatchProposal.withChosenTeams(room.mode, teams);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "The teams of a private game of " + room.mode.id() + " were wrong", e);
            return null;
        }
    }

    private void cancel(Room room, String why) {
        rooms.remove(room);
        for (UUID id : room.players) {
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
        backToLobby(room);
    }

    /** The players of the room still in its waiting lobby go back to the lobby. */
    private void backToLobby(Room room) {
        if (!room.gathered) {
            return;
        }
        for (UUID id : room.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && room.hostMode.id().equals(state.gameOf(id))) {
                lobby.goToSpawn(player);
            }
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
        for (UUID id : room.players) {
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
