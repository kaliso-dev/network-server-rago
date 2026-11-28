package gg.rago.lobby;

import java.util.Collections;
import java.util.List;

/**
 * What stands on a lobby point (config.yml, pnj: and classements:): a mode NPC that opens its
 * modes or a whole category, a stats NPC that shows each player their own stats, the Shop, the
 * leaderboard selector, a person of the server, a product of Rago, or a wall of leaderboards. Immutable.
 */
final class LobbyNpc {

    enum Kind {
        /** Opens its modes, or the menu of its category. */
        MODES,
        /** Shows each player their stats above it; a click opens them mode by mode. */
        STATS,
        /** A wall of leaderboards. */
        LEADERBOARDS,
        /** The Shop: opens the store menu; the line under its name is its own (the starter pack). */
        SHOP,
        /**
         * The leaderboard selector (plan 3.1): opens the menu of its stats; the stat chosen shows on
         * every wall that follows it (selecteur: in classements:), each on its own period.
         */
        SELECTOR,
        /**
         * A person of the server (the thank-you room, plan 5.6): their name after the prefix of
         * their rank, as above a player's head; a click does nothing.
         */
        PERSON,
        /**
         * A product of Rago (OUR OTHER PRODUCTS, plan 5.6A: Rago Client, Rago Optimizer): its title
         * above its name, the line under it; a click shows its links in the chat.
         */
        PRODUCT
    }

    private final String point;
    private final String name;
    private final Kind kind;
    private final List<String> modeIds;
    private final String category;
    private final List<List<BoardStat>> boards;
    private final List<String> podium;
    private final String subtitle;
    private final String skin;
    /** The period of every board of a wall, fixed (the wall of the week), or null: each player chooses it. */
    private final StatsPeriod period;
    /** The point of the selector a wall follows, or null: its own stats, its own clicks. */
    private final String selector;
    /** The games a ratio (FKDR, WLR) of a day, of a week needs on the walls of a selector. */
    private final int minimumDay;
    private final int minimumWeek;
    /** The Minecraft account whose skin it wears (skin-joueur:), or null: its skin:, or Steve. */
    private final String account;
    /** The rank whose prefix comes before the name of a person (rang:), or null. */
    private final String rank;
    /** The line above the name of a product (titre:), or null. */
    private final String title;
    /** What a click on a product shows in the chat, line by line (liens:); empty otherwise. */
    private final List<String> links;

    private LobbyNpc(String point, String name, Kind kind, List<String> modeIds, String category,
            List<List<BoardStat>> boards, List<String> podium) {
        this(point, name, kind, modeIds, category, boards, podium, null, null, null);
    }

    private LobbyNpc(String point, String name, Kind kind, List<String> modeIds, String category,
            List<List<BoardStat>> boards, List<String> podium, String subtitle, String skin, StatsPeriod period) {
        this(point, name, kind, modeIds, category, boards, podium, subtitle, skin, period, null, 0, 0, null, null);
    }

    private LobbyNpc(String point, String name, Kind kind, List<String> modeIds, String category,
            List<List<BoardStat>> boards, List<String> podium, String subtitle, String skin, StatsPeriod period,
            String selector, int minimumDay, int minimumWeek, String account, String rank) {
        this(point, name, kind, modeIds, category, boards, podium, subtitle, skin, period, selector, minimumDay,
                minimumWeek, account, rank, null, Collections.<String>emptyList());
    }

    private LobbyNpc(String point, String name, Kind kind, List<String> modeIds, String category,
            List<List<BoardStat>> boards, List<String> podium, String subtitle, String skin, StatsPeriod period,
            String selector, int minimumDay, int minimumWeek, String account, String rank, String title,
            List<String> links) {
        this.title = title;
        this.links = Collections.unmodifiableList(links);
        this.account = account;
        this.rank = rank;
        this.selector = selector;
        this.minimumDay = minimumDay;
        this.minimumWeek = minimumWeek;
        this.period = period;
        this.subtitle = subtitle;
        this.skin = skin;
        this.point = point;
        this.name = name;
        this.kind = kind;
        this.modeIds = Collections.unmodifiableList(modeIds);
        this.category = category;
        this.boards = Collections.unmodifiableList(boards);
        this.podium = Collections.unmodifiableList(podium);
    }

    /** An NPC of these modes, or of a whole category (then modeIds are the category's modes). */
    static LobbyNpc modes(String point, String name, List<String> modeIds, String category) {
        return new LobbyNpc(point, name, Kind.MODES, modeIds, category, Collections.<List<BoardStat>>emptyList(),
                Collections.<String>emptyList());
    }

    /** The Shop NPC: its name, and the line under it. */
    static LobbyNpc shop(String point, String name, String subtitle) {
        return new LobbyNpc(point, name, Kind.SHOP, Collections.<String>emptyList(), null,
                Collections.<List<BoardStat>>emptyList(), Collections.<String>emptyList(), subtitle, null, null);
    }

    /**
     * The leaderboard selector: its stats in the order of its menu, and the games a ratio of a day
     * and of a week needs (FKDR, WLR).
     */
    static LobbyNpc selector(String point, String name, List<BoardStat> stats, int minimumDay, int minimumWeek) {
        return new LobbyNpc(point, name, Kind.SELECTOR, Collections.<String>emptyList(), null,
                Collections.singletonList(stats), Collections.<String>emptyList(), null, null, null, null, minimumDay,
                minimumWeek, null, null);
    }

    /** A person of the server: their name, after the prefix of this rank (null: none). */
    static LobbyNpc person(String point, String name, String rank) {
        return new LobbyNpc(point, name, Kind.PERSON, Collections.<String>emptyList(), null,
                Collections.<List<BoardStat>>emptyList(), Collections.<String>emptyList(), null, null, null, null, 0, 0,
                null, rank);
    }

    /**
     * A product of Rago (OUR OTHER PRODUCTS): its name, the line above it (null: none) and what a
     * click shows in the chat, line by line.
     */
    static LobbyNpc product(String point, String name, String title, List<String> links) {
        return new LobbyNpc(point, name, Kind.PRODUCT, Collections.<String>emptyList(), null,
                Collections.<List<BoardStat>>emptyList(), Collections.<String>emptyList(), null, null, null, null, 0, 0,
                null, null, title, links);
    }

    /** The same NPC in the skin of this id ({@link NpcSkins}), or in Steve's for null. */
    LobbyNpc withSkin(String skin) {
        return new LobbyNpc(point, name, kind, modeIds, category, boards, podium, subtitle, skin, period, selector,
                minimumDay, minimumWeek, account, rank, title, links);
    }

    /** The same NPC in the skin of this Minecraft account (skin-joueur:, {@link AccountSkins}); null: none. */
    LobbyNpc withAccount(String player) {
        return new LobbyNpc(point, name, kind, modeIds, category, boards, podium, subtitle, skin, period, selector,
                minimumDay, minimumWeek, player, rank, title, links);
    }

    /** The same NPC with this line under its name (null: none). */
    LobbyNpc withSubtitle(String line) {
        return new LobbyNpc(point, name, kind, modeIds, category, boards, podium, line, skin, period, selector,
                minimumDay, minimumWeek, account, rank, title, links);
    }

    /** The same wall with the period of its boards fixed (the wall of the week), or chosen by each player (null). */
    LobbyNpc withPeriod(StatsPeriod fixed) {
        return new LobbyNpc(point, name, kind, modeIds, category, boards, podium, subtitle, skin, fixed, selector,
                minimumDay, minimumWeek, account, rank, title, links);
    }

    /**
     * The same wall following this selector: its one board shows the stat each player chose there,
     * on the wall's own period; the boards are the selector's stats.
     */
    LobbyNpc following(LobbyNpc chooser) {
        return new LobbyNpc(point, name, kind, modeIds, category, chooser.boards, podium, subtitle, skin, period,
                chooser.point, chooser.minimumDay, chooser.minimumWeek, account, rank, title, links);
    }

    /** The line above the name of a product (titre:), or null. */
    String title() {
        return title;
    }

    /** What a click on a product shows in the chat, line by line; empty for the other NPCs. */
    List<String> links() {
        return links;
    }

    /** The point of the selector this wall follows, or null. */
    String selector() {
        return selector;
    }

    /** The games a ratio (FKDR, WLR) needs on a wall of the selector, over this period; none ever. */
    int minimumGames(StatsPeriod over) {
        return over == StatsPeriod.DAY ? minimumDay : over == StatsPeriod.WEEK ? minimumWeek : 0;
    }

    /** The period of the boards of a wall, fixed; null when each player chooses it with a click. */
    StatsPeriod period() {
        return period;
    }

    /** The id of its skin (skins/id.png, signed by {@link NpcSkins}), or null for Steve. */
    String skin() {
        return skin;
    }

    /** The Minecraft account whose skin it wears (skin-joueur:), or null. */
    String account() {
        return account;
    }

    /** The rank of a person (rang:, an id of rangs:), or null. */
    String rank() {
        return rank;
    }

    /** The line under the name of the Shop NPC, or of an NPC of modes that has one; else null. */
    String subtitle() {
        return subtitle;
    }

    static LobbyNpc stats(String point, String name) {
        return new LobbyNpc(point, name, Kind.STATS, Collections.<String>emptyList(), null,
                Collections.<List<BoardStat>>emptyList(), Collections.<String>emptyList());
    }

    /** A wall of boards, left to right; each board shows its first stat, a right click the next one. */
    static LobbyNpc leaderboards(String point, String name, List<List<BoardStat>> boards) {
        return leaderboards(point, name, boards, Collections.<String>emptyList());
    }

    /**
     * A wall with a podium: on its steps (first place first), the players at the top of its first
     * board. A step is a /lobby point or the coordinates "x y z" of the feet of who stands on it.
     */
    static LobbyNpc leaderboards(String point, String name, List<List<BoardStat>> boards, List<String> podium) {
        return new LobbyNpc(point, name, Kind.LEADERBOARDS, Collections.<String>emptyList(), null, boards, podium);
    }

    /** The coordinates of a step written "x y z", or null for a point name or anything else. */
    static double[] coordinates(String step) {
        String[] parts = step.trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        double[] xyz = new double[3];
        try {
            for (int i = 0; i < 3; i++) {
                xyz[i] = Double.parseDouble(parts[i]);
                if (Double.isNaN(xyz[i]) || Double.isInfinite(xyz[i])) {
                    return null;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return xyz;
    }

    Kind kind() {
        return kind;
    }

    /** The category whose menu it opens, or null for an NPC of its own modes. */
    String category() {
        return category;
    }

    /** The /lobby point it stands on. */
    String point() {
        return point;
    }

    /** Coloured. */
    String name() {
        return name;
    }

    /** The modes it opens, in order; empty for a stats NPC or a leaderboard wall. */
    List<String> modeIds() {
        return modeIds;
    }

    /** The leaderboards of a wall, left to right, each with its stats; empty otherwise. */
    List<List<BoardStat>> boards() {
        return boards;
    }

    /** The steps of the podium of a wall, first place first: point names or "x y z"; often none. */
    List<String> podium() {
        return podium;
    }

    boolean leaderboard() {
        return kind == Kind.LEADERBOARDS;
    }

    /** Stands as a player (a mode or stats NPC), not as floating text. */
    boolean hasBody() {
        return kind != Kind.LEADERBOARDS;
    }
}
