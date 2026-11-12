package gg.rago.lobby;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * How a player's name looks: their rank, the colour of their name (a gradient of
 * {@link NameStyle}, or the colour of the rank) and their badge. A player wears what they chose
 * (/style) if they still own it, else the badge of the week if they won it, else what their rank
 * gives. They own a colour or a badge they bought on the store or won with a level (in their
 * cosmetics), the badge of the week until the next results, and those of their ranks (the pink
 * gradient and the ✦ of PREMIUM). Main thread only.
 */
final class NameLooks {

    private final StatsBook book;
    private final ServerSettings server;
    /** The name each player plays under (/nick), or null. */
    private Function<UUID, String> nicks = player -> null;

    NameLooks(StatsBook book, ServerSettings server) {
        this.book = book;
        this.server = server;
    }

    ServerSettings server() {
        return server;
    }

    void setNicks(Function<UUID, String> nicks) {
        this.nicks = nicks;
    }

    // --- What the other players see: a nicked player looks like a new player -------------------

    /** The name the player plays under (/nick), or null. */
    String nick(UUID player) {
        return nicks.apply(player);
    }

    /** The name the others see: the nick, or the real name. */
    String shownName(UUID player, String real) {
        String nick = nick(player);
        return nick != null ? nick : real;
    }

    /** {@link #display}, or for a nicked player a grey name without a rank: as the chat shows them. */
    String shown(UUID player, String real) {
        String nick = nick(player);
        return nick != null ? Ranks.DEFAULT.nameColour() + nick : display(player, real);
    }

    /** {@link #styled}, or for a nicked player a grey name without a rank: as the tab list shows them. */
    String shownStyled(UUID player, String real) {
        String nick = nick(player);
        return nick != null ? Ranks.DEFAULT.nameColour() + nick : styled(player, real);
    }

    /** {@link #badgeAfter}, nothing for a nicked player. */
    String shownBadge(UUID player) {
        return nick(player) != null ? "" : badgeAfter(player);
    }

    /** The rank the others see: none for a nicked player. */
    RankDef shownRank(UUID player) {
        return nick(player) != null ? Ranks.DEFAULT : rank(player);
    }

    /** The prefix of the name above the head: the rank (its + coloured) and the colour of the name. */
    String headPrefix(UUID player) {
        if (nick(player) != null) {
            return Ranks.DEFAULT.tagPrefix();
        }
        return rank(player).headPrefix(plusColour(player), headColour(player));
    }

    /** The rank's prefix with the player's colour of + ("§d[PREMIUM§0+§d] "), "" without a rank. */
    String prefix(UUID player) {
        return rank(player).prefix(plusColour(player));
    }

    /** {@link #prefix}, the plain one of the default rank for a nicked player. */
    String shownPrefix(UUID player) {
        return nick(player) != null ? Ranks.DEFAULT.prefix() : prefix(player);
    }

    /** The colour codes of the player's + (white if they wear no colour of +). */
    String plusColour(UUID player) {
        NameStyle plus = worn(player, NameStyle.Kind.PLUS);
        return plus == null ? RankDef.DEFAULT_PLUS : plus.plusColour();
    }

    /** The colour of + the player wears, or null for the white one. */
    NameStyle plus(UUID player) {
        return worn(player, NameStyle.Kind.PLUS);
    }

    /** The Network level the others see: 1 for a nicked player. */
    int shownLevel(UUID player) {
        return nick(player) != null ? 1 : book.level(player);
    }

    /** The rank a player shows. */
    RankDef rank(UUID player) {
        return NetworkRanks.rankOf(book, server.ranks(), player);
    }

    /** True if the player may wear this colour or badge. */
    boolean owns(PlayerStats profile, NameStyle style) {
        return profile != null && (profile.cosmetics().contains(style.key())
                || server.ranks().styles(profile.rank(), profile.boughtRanks()).contains(style.key()));
    }

    /** The colour of name the player wears, or null for the plain colour of their rank. */
    NameStyle colour(UUID player) {
        return worn(player, NameStyle.Kind.COLOUR);
    }

    /** The badge the player wears, or null. */
    NameStyle badge(UUID player) {
        return worn(player, NameStyle.Kind.BADGE);
    }

    private NameStyle worn(UUID player, NameStyle.Kind kind) {
        PlayerStats profile = book.get(player);
        if (profile == null) {
            return null;
        }
        RankDef rank = rank(player);
        String chosen = profile.choice(kind);
        if (kind == NameStyle.Kind.BADGE && chosen == null) {
            // the crown of the top of the week shows, unless the player chose a badge
            NameStyle weekly = server.weeklyBadge();
            if (weekly != null && owns(profile, weekly)) {
                return weekly;
            }
        }
        String given = kind == NameStyle.Kind.COLOUR ? rank.style() : kind == NameStyle.Kind.BADGE ? rank.badge() : null;
        return wear(chosen, given, id -> server.style(kind, id), style -> owns(profile, style));
    }

    /**
     * What a player wears: the style chosen if they own it, none if they chose none (""), else the
     * one of their rank if they own it.
     */
    static NameStyle wear(String chosen, String given, Function<String, NameStyle> find, Predicate<NameStyle> owned) {
        if (chosen != null && chosen.isEmpty()) {
            return null;
        }
        for (String id : new String[] {chosen, given}) {
            NameStyle style = id == null ? null : find.apply(id);
            if (style != null && owned.test(style)) {
                return style;
            }
        }
        return null;
    }

    /** The step of the animated gradients in the tab list: one more about three times a second. */
    private volatile int phase;

    /** The animated gradients move on one step. */
    void advance() {
        phase++;
    }

    /** The name in the player's colour: a gradient (at the step of its animation in the tab list), or the colour of their rank. */
    String name(UUID player, String plain) {
        NameStyle colour = colour(player);
        return colour != null ? colour.apply(plain, phase) : rank(player).nameColour() + plain;
    }

    /**
     * "§d[PREMIUM§f+§d] §dSofiene": the rank and the name in its main colour, as the chat and the
     * messages show a player (light blue for VIP, pink for PREMIUM, the middle of a gradient): a
     * gradient runs along the name in the tab list only.
     */
    String display(UUID player, String plain) {
        return prefix(player) + headColour(player) + plain;
    }

    /**
     * "§d[PREMIUM§f+§d] §5So§dfi§fene": the rank and the name in the player's colour (a gradient at
     * the step of its animation), as the tab list and the menus show a player.
     */
    String styled(UUID player, String plain) {
        return prefix(player) + name(player, plain);
    }

    /**
     * "§d[PREMIUM§f+§d] §5So§dfi§fene": the rank and the name, still (an animated colour at its first
     * step), as a podium shows a player under their real name.
     */
    String stillDisplay(UUID player, String plain) {
        NameStyle colour = colour(player);
        return prefix(player) + (colour != null ? colour.apply(plain, 0) : rank(player).nameColour() + plain);
    }

    /**
     * "§d[PREMIUM§f+§d] §dSofiene": the rank and the name in one colour, as above the head; short
     * enough for a line of a board in the games.
     */
    String tagName(UUID player, String plain) {
        return display(player, plain);
    }

    /** " §d§l✦": the badge after the name, or "" without one. */
    String badgeAfter(UUID player) {
        NameStyle badge = badge(player);
        return badge == null ? "" : " " + badge.text();
    }

    /** The colour codes of the name above the head and in the chat, where the name takes one colour: fixed, even for an animated colour. */
    String headColour(UUID player) {
        NameStyle colour = colour(player);
        return colour != null ? colour.mainColour() : rank(player).nameColour();
    }

    /** The place of the player in the queues, from their ranks: the higher first, 0 for none. */
    int queuePriority(UUID player) {
        PlayerStats profile = book.get(player);
        return profile == null ? 0 : server.ranks().queuePriority(profile.rank(), profile.boughtRanks());
    }

    /** What the player's Network XP is multiplied by: 1.5 with PREMIUM. */
    double xpBoost(UUID player) {
        PlayerStats profile = book.get(player);
        return profile == null ? 1.0 : server.ranks().xpBoost(profile.rank(), profile.boughtRanks());
    }
}
