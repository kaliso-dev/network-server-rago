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
 * the name in the player's colour (a gradient with PREMIUM) and their badge after it, the messages
 * of a player without a rank in grey. The chat runs on its own threads, so the look of each player
 * is computed on the main thread ({@link #refresh}) and only read from the chat.
 */
final class NetworkRanks implements Listener {

    /** What the chat shows of a player: the level and rank, the name and badge, the colour of the messages. */
    private static final class Look {
        final String prefix;
        final String name;
        final String chat;

        Look(String prefix, String name, String chat) {
            this.prefix = prefix;
            this.name = name;
            this.chat = chat;
        }
    }

    private static final String NEW_PREFIX = ChatColor.GRAY + "[1" + NetworkLevel.STAR + "] " + ChatColor.GRAY;

    private final StatsBook book;
    private final NameLooks names;
    private final Map<UUID, Look> looks = new ConcurrentHashMap<>();

    NetworkRanks(StatsBook book, NameLooks names) {
        this.book = book;
        this.names = names;
    }

    /** Main thread: the player's level, rank or style may have changed. */
    void refresh(Player player) {
        UUID id = player.getUniqueId();
        RankDef rank = names.rank(id);
        looks.put(id, new Look(book.levels().tag(book.level(id)) + " " + rank.prefix(),
                names.name(id, player.getName()) + names.badgeAfter(id), rank.chatColour()));
    }

    void forget(UUID player) {
        looks.remove(player);
    }

    /** The player as the chat shows them: "§6[12✫] §d[PREMIUM] §5So§dfi§fene §d§l✦". */
    String chatName(UUID player, String name) {
        Look look = looks.get(player);
        return look == null ? NEW_PREFIX + name : look.prefix + look.name;
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
        // The format goes through String.format: a % in a rank must not be read as a placeholder.
        if (look == null) {
            event.setFormat(NEW_PREFIX + "%1$s" + ChatColor.GRAY + ": %2$s");
        } else {
            event.setFormat((look.prefix + look.name).replace("%", "%%") + look.chat + ": %2$s");
        }
    }
}
