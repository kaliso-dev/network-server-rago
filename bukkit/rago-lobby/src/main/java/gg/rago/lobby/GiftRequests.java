package gg.rago.lobby;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ranks offered and not answered yet: a player of PREMIUM (rago.perk.gift) offers a rank, the
 * one who gets it says YES or NO in a book, within a minute; the giver pays at YES. One offer per
 * giver and player: a new one replaces the last. Main thread only.
 */
final class GiftRequests {

    static final long EXPIRY_MS = 60_000;
    /** Who may offer a rank: PREMIUM, and the ranks above it. */
    static final String PERMISSION = "rago.perk.gift";

    private static final class Offer {
        final String rankId;
        final long expiresAt;

        Offer(String rankId, long expiresAt) {
            this.rankId = rankId;
            this.expiresAt = expiresAt;
        }
    }

    /** The one who gets it, then the giver, then the offer. */
    private final Map<UUID, Map<UUID, Offer>> pending = new HashMap<>();

    void offer(UUID target, UUID giver, String rankId, long now) {
        pending.computeIfAbsent(target, id -> new HashMap<>()).put(giver, new Offer(rankId, now + EXPIRY_MS));
    }

    /** Removes the giver's offer to the target and returns its rank, or null if none is still open. */
    String take(UUID target, UUID giver, long now) {
        Map<UUID, Offer> forTarget = pending.get(target);
        Offer offer = forTarget == null ? null : forTarget.remove(giver);
        if (forTarget != null && forTarget.isEmpty()) {
            pending.remove(target);
        }
        return offer == null || offer.expiresAt < now ? null : offer.rankId;
    }

    /** A player left: the offers to them and theirs go. */
    void forget(UUID player) {
        pending.remove(player);
        for (Map<UUID, Offer> forTarget : pending.values()) {
            forTarget.remove(player);
        }
        pending.values().removeIf(Map::isEmpty);
    }

    /**
     * The page of the book, as JSON: "[PREMIUM] Sofiene wants to give you [VIP]!" and the YES and NO
     * buttons, which answer with /gift accept or deny and the giver's name (letters, digits, _).
     */
    static String page(String giver, String giverName, String rank) {
        return "[\"\","
                + text("§6§l   ✦ RANK GIFT ✦\n\n") + ","
                + text(paper(giver) + "\n") + ","
                + text("§0wants to give you\n") + ","
                + text(paper(rank) + "§0!\n\n") + ","
                + text("§0Do you accept it?\n\n\n") + ","
                + text("      ") + ","
                + button("§a§lYES", "/gift accept " + giverName, "Accept the rank") + ","
                + text("        ") + ","
                + button("§c§lNO", "/gift deny " + giverName, "Refuse the rank")
                + "]";
    }

    /** The [YES] [NO] line of the chat, as JSON, should the book be closed. */
    static String buttons(String giverName) {
        return "[\"\","
                + button("§a§l[YES]", "/gift accept " + giverName, "Accept the rank") + ","
                + text("   ") + ","
                + button("§c§l[NO]", "/gift deny " + giverName, "Refuse the rank")
                + "]";
    }

    /** Text for the paper of a book: white (unreadable there) becomes black. */
    static String paper(String text) {
        return text.replace("§f", "§0").replace("§F", "§0");
    }

    private static String text(String text) {
        return "{\"text\":\"" + escape(text) + "\"}";
    }

    private static String button(String text, String command, String hover) {
        return "{\"text\":\"" + escape(text) + "\","
                + "\"clickEvent\":{\"action\":\"run_command\",\"value\":\"" + escape(command) + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + escape(hover) + "\"}}";
    }

    /** The text inside a JSON string. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
