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
 * them, with those of the ranks each one inherits. Immutable.
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

    /** Every permission of these ranks and of the ranks they inherit. */
    Set<String> permissions(String given, Collection<String> bought) {
        Set<String> permissions = new LinkedHashSet<>();
        List<String> ids = new ArrayList<>(bought);
        ids.add(given);
        for (String id : ids) {
            RankDef at = id == null ? null : byId.get(id);
            while (at != null) {
                permissions.addAll(at.permissions());
                at = at.parent() == null ? null : byId.get(at.parent());
            }
        }
        return permissions;
    }

    /** The ranks of a config.yml without rangs: the staff, then the ranks of the shop. */
    static Ranks defaults() {
        List<RankDef> ranks = new ArrayList<>();
        ranks.add(staff("owner", "§4[OWNER] ", "§4", "admin", "rago.admin"));
        ranks.add(staff("admin", "§c[ADMIN] ", "§c", "mod", "rago.admin"));
        ranks.add(staff("dev", "§b[DEV] ", "§b", "mod", "rago.admin"));
        ranks.add(staff("mod", "§2[MOD] ", "§2", "helper", "rago.mod"));
        ranks.add(staff("helper", "§9[HELPER] ", "§9", "elite", "rago.staff"));
        ranks.add(staff("builder", "§3[BUILDER] ", "§3", "vipplus", "rago.staff", "rago.lobby.admin"));
        ranks.add(new RankDef("media", "§5[MEDIA] ", "§5", "§f", false, Collections.<String>emptyList(), "elite",
                0, 0, Material.NAME_TAG, Arrays.asList("For content creators", "Everything of ELITE")));
        ranks.add(new RankDef("legend", "§6[LEGEND] ", "§6", "§f", false, Collections.<String>emptyList(), "elite",
                100_000, 100, Material.GOLD_BLOCK, Arrays.asList("The [LEGEND] prefix in gold", "Everything of ELITE")));
        ranks.add(new RankDef("elite", "§d[ELITE] ", "§d", "§f", false, Arrays.asList("rago.perk.join"), "vipplus",
                40_000, 50, Material.DIAMOND_BLOCK, Arrays.asList("Your arrival is announced in the lobby",
                        "Everything of VIP+")));
        ranks.add(new RankDef("vipplus", "§a[VIP§6+§a] ", "§a", "§f", false, Arrays.asList("rago.perk.fly"), "vip",
                15_000, 25, Material.EMERALD_BLOCK, Arrays.asList("/fly in the lobby", "Everything of VIP")));
        ranks.add(new RankDef("vip", "§a[VIP] ", "§a", "§f", false, Arrays.asList("rago.perk.chat"), null,
                5_000, 10, Material.EMERALD, Arrays.asList("White messages in the chat", "No chat cooldown")));
        return new Ranks(ranks);
    }

    private static RankDef staff(String id, String prefix, String colour, String parent, String... permissions) {
        return new RankDef(id, prefix, colour, "§f", true, Arrays.asList(permissions), parent, 0, 0, Material.NAME_TAG,
                Collections.<String>emptyList());
    }
}
