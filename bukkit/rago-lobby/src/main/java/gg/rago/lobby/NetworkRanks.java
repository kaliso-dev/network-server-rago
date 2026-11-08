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
 * The Network level and rank in the chat: "[12] [Gold] Sofiene: hello". The chat runs on its own
 * threads, so the prefix of each player is computed on the main thread ({@link #refresh}) and only
 * read from the chat.
 */
final class NetworkRanks implements Listener {

    private final StatsBook book;
    private final Map<UUID, String> prefixes = new ConcurrentHashMap<>();

    NetworkRanks(StatsBook book) {
        this.book = book;
    }

    /** Main thread: the player's level or rank may have changed. */
    void refresh(Player player) {
        prefixes.put(player.getUniqueId(), prefix(book, player.getUniqueId()));
    }

    void forget(UUID player) {
        prefixes.remove(player);
    }

    /** "§7[12] §6[Gold] ", or "§7[3] §7" below the first rank. */
    static String prefix(StatsBook book, UUID player) {
        int level = book.level(player);
        String rank = book.levels().rank(level);
        return ChatColor.GRAY + "[" + level + "] " + (rank == null ? ChatColor.GRAY.toString() : rank + " ");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String prefix = prefixes.get(event.getPlayer().getUniqueId());
        if (prefix == null) {
            prefix = ChatColor.GRAY + "[1] ";
        }
        // The format goes through String.format: a % in a rank must not be read as a placeholder.
        event.setFormat(prefix.replace("%", "%%") + "%1$s" + ChatColor.GRAY + ": " + ChatColor.WHITE + "%2$s");
    }
}
