package gg.rago.lobby;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The skins the podiums wear. A player's skin is kept in their stats at each join; an online
 * player's is read from their profile; for a player who has not joined since, Mojang's session
 * server is asked once, away from the main thread, and the answer is kept in their stats. Only an
 * online-mode UUID (version 4) has a skin there. Main thread, but for the requests.
 */
final class Skins {

    private static final String SESSION = "https://sessionserver.mojang.com/session/minecraft/profile/";
    /** At most this many requests to Mojang in a minute (it allows far more). */
    private static final int PER_MINUTE = 30;
    private static final long MINUTE_MS = 60_000L;
    /** A player Mojang did not answer for is asked again after this long. */
    private static final long RETRY_MS = 30 * MINUTE_MS;

    private final Plugin plugin;
    private final StatsService stats;
    private final ExecutorService web;
    /** When each player was last asked for. */
    private final Map<UUID, Long> asked = new HashMap<>();
    private final Deque<Long> recent = new ArrayDeque<>();

    Skins(Plugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
        this.web = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby skins");
            thread.setDaemon(true);
            return thread;
        });
    }

    void stop() {
        web.shutdownNow();
    }

    /** The player's skin, or null while it is unknown (Mojang is asked, the podium waits for it). */
    Skin of(UUID player) {
        PlayerStats profile = stats.book().get(player);
        if (profile != null && profile.skin() != null) {
            return profile.skin();
        }
        Player online = Bukkit.getPlayer(player);
        Skin skin = online == null ? null : Skin.of(online);
        if (skin != null) {
            if (profile != null) {
                profile.setSkin(skin);
                stats.save(profile);
            }
            return skin;
        }
        ask(player);
        return null;
    }

    private void ask(final UUID player) {
        long now = System.currentTimeMillis();
        Long last = asked.get(player);
        if (web.isShutdown() || player.version() != 4 || last != null && now - last < RETRY_MS || busy(now)) {
            return;
        }
        asked.put(player, now);
        recent.addLast(now);
        web.execute(() -> {
            Skin skin;
            try {
                skin = Skin.fromSession(HypixelProfiles.fetch("Mojang", SESSION + player.toString().replace("-", "")
                        + "?unsigned=false", null));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.FINE, "No skin from Mojang for " + player, e);
                return;
            }
            if (skin == null || !plugin.isEnabled()) {
                return;
            }
            final Skin found = skin;
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerStats profile = stats.book().get(player);
                if (profile != null && !found.equals(profile.skin())) {
                    profile.setSkin(found);
                    stats.save(profile);
                }
            });
        });
    }

    /** True when {@link #PER_MINUTE} requests went out in the last minute. */
    private boolean busy(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst() > MINUTE_MS) {
            recent.removeFirst();
        }
        return recent.size() >= PER_MINUTE;
    }
}
