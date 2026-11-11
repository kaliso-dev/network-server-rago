package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Every rank of config.yml (rangs:), the highest first: the order of the list is the order of the
 * tab list, and the rank a player shows is the highest of theirs. A player has the rank the staff
 * gave them (/rank set) and the ranks they bought in the shop; they have the permissions of all of
 * them, with those of the ranks each one inherits, and the colours of name and badges they give.
 * Immutable.
 */
final class Ranks {

    /** A player without a rank: grey name and messages, no prefix. */
    static final RankDef DEFAULT = new RankDef("default", "", "§7", "§7", false, Collections.<String>emptyList(),
            null, 0, 0, Material.NAME_TAG, Collections.<String>emptyList());

    private final List<RankDef> ranks;
    private final Map<String, RankDef> byId = new LinkedHashMap<>();

    Ranks(List<RankDef> ranks) {
        this.ranks = Collections.unmodifiableList(new ArrayList<>(ranks));
        for (RankDef rank : ranks) {
            if (rank.id().equals(DEFAULT.id()) || byId.put(rank.id(), rank) != null) {
                throw new IllegalArgumentException("rangs." + rank.id() + ": a second rank with this name");
            }
        }
        for (RankDef rank : ranks) {
            Set<String> seen = new HashSet<>();
            RankDef at = rank;
            while (at.parent() != null) {
                if (!seen.add(at.id())) {
                    throw new IllegalArgumentException("rangs." + rank.id() + ".herite: the ranks inherit from each other in a loop");
                }
                RankDef parent = byId.get(at.parent());
                if (parent == null) {
                    throw new IllegalArgumentException("rangs." + at.id() + ".herite: no rank " + at.parent());
                }
                at = parent;
            }
        }
    }

    /** The rank with this id, or null ("default" and null give the rank of a player without one). */
    RankDef get(String id) {
        if (id == null || id.equals(DEFAULT.id())) {
            return DEFAULT;
        }
        return byId.get(id);
    }

    /** Every rank, the highest first. */
    List<RankDef> all() {
        return ranks;
    }

    /** The ranks the shop sells, the cheapest first. */
    List<RankDef> sold() {
        List<RankDef> sold = new ArrayList<>();
        for (RankDef rank : ranks) {
            if (rank.sold()) {
                sold.add(rank);
            }
        }
        Collections.sort(sold, (a, b) -> a.price() != b.price() ? Integer.compare(a.price(), b.price())
                : Integer.compare(position(b), position(a)));
        return sold;
    }

    /** The ranks a player can gift another one with Rago Points, the cheapest first. */
    List<RankDef> giftable() {
        List<RankDef> giftable = new ArrayList<>();
        for (RankDef rank : ranks) {
            if (rank.giftable()) {
                giftable.add(rank);
            }
        }
        Collections.sort(giftable, (a, b) -> a.giftPrice() != b.giftPrice() ? Integer.compare(a.giftPrice(), b.giftPrice())
                : Integer.compare(position(b), position(a)));
        return giftable;
    }

    /**
     * The ranks the shop shows, from left to right: those of the web store, the lowest first (VIP,
     * then PREMIUM+), then those sold for Rago Points, the cheapest first.
     */
    List<RankDef> forSale() {
        List<RankDef> shown = new ArrayList<>();
        // the lowest first, from left to right: VIP, then PREMIUM+
        for (int i = ranks.size() - 1; i >= 0; i--) {
            RankDef rank = ranks.get(i);
            if (rank.webStore() && !rank.staff()) {
                shown.add(rank);
            }
        }
        for (RankDef rank : sold()) {
            if (!shown.contains(rank)) {
                shown.add(rank);
            }
        }
        return shown;
    }

    /** 0 for the highest rank; a player without a rank comes after all of them. */
    int position(RankDef rank) {
        int index = ranks.indexOf(rank);
        return index < 0 ? ranks.size() : index;
    }

    /** The highest of these ranks (unknown ids left out), or {@link #DEFAULT}. */
    RankDef best(String given, Collection<String> bought) {
        RankDef best = DEFAULT;
        List<String> ids = new ArrayList<>(bought);
        ids.add(given);
        for (String id : ids) {
            RankDef rank = id == null ? null : byId.get(id);
            if (rank != null && position(rank) < position(best)) {
                best = rank;
            }
        }
        return best;
    }

    /** True if the player owns this rank, or a higher one that inherits it. */
    boolean has(String given, Collection<String> bought, String id) {
        List<String> ids = new ArrayList<>(bought);
        ids.add(given);
        for (String own : ids) {
            RankDef at = own == null ? null : byId.get(own);
            while (at != null) {
                if (at.id().equals(id)) {
                    return true;
                }
                at = at.parent() == null ? null : byId.get(at.parent());
            }
        }
        return false;
    }

    /** These ranks and every rank they inherit, each once (unknown ids left out). */
    private List<RankDef> withInherited(String given, Collection<String> bought) {
        Set<RankDef> all = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>(bought);
        ids.add(given);
        for (String id : ids) {
            RankDef at = id == null ? null : byId.get(id);
            while (at != null && all.add(at)) {
                at = at.parent() == null ? null : byId.get(at.parent());
            }
        }
        return new ArrayList<>(all);
    }

    /** Every permission of these ranks and of the ranks they inherit. */
    Set<String> permissions(String given, Collection<String> bought) {
        Set<String> permissions = new LinkedHashSet<>();
        for (RankDef rank : withInherited(given, bought)) {
            permissions.addAll(rank.permissions());
        }
        return permissions;
    }

    /** The place in the queues of these ranks and of the ranks they inherit: the highest, 0 for none. */
    int queuePriority(String given, Collection<String> bought) {
        int priority = 0;
        for (RankDef rank : withInherited(given, bought)) {
            priority = Math.max(priority, rank.queuePriority());
        }
        return priority;
    }

    /** The highest XP boost of these ranks and of the ranks they inherit: 1 for none. */
    double xpBoost(String given, Collection<String> bought) {
        double boost = 1.0;
        for (RankDef rank : withInherited(given, bought)) {
            boost = Math.max(boost, rank.xpBoost());
        }
        return boost;
    }

    /**
     * The colours and badges these ranks give, with those of the ranks they inherit, by key
     * ("couleur-rose", "badge-premium"): a player owns them as long as they have the rank.
     */
    Set<String> styles(String given, Collection<String> bought) {
        Set<String> keys = new LinkedHashSet<>();
        for (RankDef rank : withInherited(given, bought)) {
            if (rank.style() != null) {
                keys.add(NameStyle.key(NameStyle.Kind.COLOUR, rank.style()));
            }
            if (rank.badge() != null) {
                keys.add(NameStyle.key(NameStyle.Kind.BADGE, rank.badge()));
            }
        }
        return keys;
    }

    /** The ranks of a config.yml without rangs: the staff, then the ranks of the web store (PREMIUM+, VIP). */
    static Ranks defaults() {
        List<RankDef> ranks = new ArrayList<>();
        ranks.add(staff("owner", "§4[OWNER] ", "§4", "admin", "rago.admin"));
        ranks.add(staff("admin", "§c[ADMIN] ", "§c", "mod", "rago.admin"));
        ranks.add(staff("dev", "§b[DEV] ", "§b", "mod", "rago.admin"));
        ranks.add(staff("mod", "§2[MOD] ", "§2", "helper", "rago.mod"));
        ranks.add(staff("helper", "§9[HELPER] ", "§9", PREMIUM, "rago.staff"));
        ranks.add(staff("builder", "§3[BUILDER] ", "§3", PREMIUM, "rago.staff", "rago.lobby.admin"));
        ranks.add(new RankDef("media", "§c[§fMEDIA§c] ", "§c", "§f", false, Collections.<String>emptyList(), PREMIUM,
                0, 0, Material.NAME_TAG, Arrays.asList("For content creators", "Everything of PREMIUM")));
        ranks.add(premium());
        ranks.add(vip());
        return new Ranks(ranks);
    }

    /** The id of the high rank of the web store. */
    static final String PREMIUM = "premium";
    /** The id of the small rank of the web store, below PREMIUM+. */
    static final String VIP = "vip";

    /**
     * The VIP rank: sold on the web store, below PREMIUM+ (which has all of it, and keeps its nick,
     * gadgets, booster, events, message, gradient name and rarest cosmetics): a light blue name, a
     * little more XP, /fly and the speed of the lobby, three cosmetics of its own.
     */
    static RankDef vip() {
        List<String> permissions = new ArrayList<>(Arrays.asList("rago.perk.chat", "rago.perk.fly", "rago.perk.join", "rago.perk.speed"));
        for (String cosmetic : VIP_COSMETICS) {
            permissions.add(Cosmetic.PERMISSION + cosmetic);
        }
        return new RankDef(VIP, "§b[VIP] ", "§b", "§f", false, permissions, null, 0, 0, Material.DIAMOND,
                Arrays.asList("A light blue name and the §b[VIP]§7 tag", "+25% Network XP in every game", "/fly in the lobby",
                        "Speed I, II or III in the lobby (settings)", "Your arrival announced, with a firework",
                        "White messages, no chat cooldown", "Priority in the queues (after PREMIUM+)", "3 cosmetics: the Clouds trail,",
                        "the Heart burst kill, the Love win"),
                1.25, "aqua", null, true, "4.99€", 1, VIP_GIFT);
    }

    /** What gifting VIP and PREMIUM+ to another player costs in Rago Points (prix-cadeau). */
    static final int VIP_GIFT = 25000;
    static final int PREMIUM_GIFT = 50000;

    /** The cosmetics of VIP: three of the cheapest, far from the rarest of PREMIUM+. */
    static final List<String> VIP_COSMETICS = Arrays.asList("trail-clouds", "kill-hearts", "win-hearts");

    /**
     * The PREMIUM rank: sold on the web store, with the perks of the network and a pink gradient
     * name; it inherits VIP (its cosmetics, its colour to choose).
     */
    static RankDef premium() {
        return new RankDef(PREMIUM, "§d[PREMIUM" + RankDef.PLUS + "] ", "§d", "§f", false, Arrays.asList("rago.perk.chat", "rago.perk.fly",
                "rago.perk.join", "rago.perk.nick", "rago.perk.message", "rago.perk.speed", "rago.perk.gadgets", "rago.perk.booster",
                "rago.perk.host", "rago.perk.gift"), VIP, 0, 0,
                Material.NETHER_STAR,
                Arrays.asList("A pink gradient name and the §d§l✦§7 badge", "Your §f+§7 in the colour you choose (/style)", "+50% Network XP in every game",
                        "/fly in the lobby", "Speed I, II or III in the lobby (settings)", "/nick: play under another name", "Your own message above your head",
                        "Your arrival announced, with a firework", "White messages, no chat cooldown",
                        "Gadgets of your own: bat blaster, explosive sheep, disco ball",
                        "A free XP booster every day (x2 XP for 30 minutes; bought ones: x3)",
                        "Host events for the server: /host (Sumo, Brackets, LMS)",
                        "Gift ranks to your friends: /gift <player>",
                        "The rarest kill effects and victory dances", "The first place in the queues",
                        "Everything of VIP, its 3 cosmetics too"),
                1.5, "rose", PREMIUM, true, "9.99€", 2, PREMIUM_GIFT);
    }

    private static RankDef staff(String id, String prefix, String colour, String parent, String... permissions) {
        return new RankDef(id, prefix, colour, "§f", true, Arrays.asList(permissions), parent, 0, 0, Material.NAME_TAG,
                Collections.<String>emptyList());
    }
}
