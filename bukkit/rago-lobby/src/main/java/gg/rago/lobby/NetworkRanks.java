package gg.rago.lobby;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * The Network level and the rank in the chat, as on Hypixel: "§6[12✫] §c[ADMIN] Sofiene§f: hello",
 * the messages of a player without a rank in grey. The chat runs on its own threads, so the prefix
 * of each player is computed on the main thread ({@link #refresh}) and only read from the chat.
 */
final class NetworkRanks implements Listener {

    /** What the chat shows of a player: before the name, and the colour of the messages. */
    private static final class Look {
        final String prefix;
        final String chat;

        Look(String prefix, String chat) {
            this.prefix = prefix;
            this.chat = chat;
        }
    }

    private static final Look NEW_PLAYER = new Look(ChatColor.GRAY + "[1" + NetworkLevel.STAR + "] " + ChatColor.GRAY,
            ChatColor.GRAY.toString());

    private final StatsBook book;
    private final Ranks ranks;
    private final Map<UUID, Look> looks = new ConcurrentHashMap<>();

    NetworkRanks(StatsBook book, Ranks ranks) {
        this.book = book;
        this.ranks = ranks;
    }

    /** Main thread: the player's level or rank may have changed. */
    void refresh(Player player) {
        UUID id = player.getUniqueId();
        looks.put(id, new Look(prefix(book, ranks, id), rankOf(book, ranks, id).chatColour()));
    }

    void forget(UUID player) {
        looks.remove(player);
    }

    /** The player's level and rank as the chat shows them, before the name. */
    String prefixOf(UUID player) {
        Look look = looks.get(player);
        return (look == null ? NEW_PLAYER : look).prefix;
    }

    /** The rank a player shows: the highest of the one the staff gave and those they bought. */
    static RankDef rankOf(StatsBook book, Ranks ranks, UUID player) {
        PlayerStats stats = book.get(player);
        return stats == null ? Ranks.DEFAULT : ranks.best(stats.rank(), stats.boughtRanks());
    }

    /** "§6[12✫] §c[ADMIN] §c": the level, the rank and the colour of the name. */
    static String prefix(StatsBook book, Ranks ranks, UUID player) {
        RankDef rank = rankOf(book, ranks, player);
        return book.levels().tag(book.level(player)) + " " + rank.prefix() + rank.nameColour();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Look look = looks.get(event.getPlayer().getUniqueId());
        if (look == null) {
            look = NEW_PLAYER;
        }
        // The format goes through String.format: a % in a rank must not be read as a placeholder.
        event.setFormat(look.prefix.replace("%", "%%") + "%1$s" + look.chat + ": " + "%2$s");
    }
}
