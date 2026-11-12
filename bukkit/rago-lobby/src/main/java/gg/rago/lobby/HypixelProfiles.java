package gg.rago.lobby;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The Bed Wars settings players copy from Hypixel (the shop's quick buy and the hotbar manager),
 * one YAML file per player in plugins/RagoLobby/hypixel. An import asks Mojang for the UUID of a
 * name (not needed for the player's own account on an online-mode server), then the Hypixel API
 * for that player, with the server's own API key (hypixel.cle-api in config.yml). Requests run on
 * their own thread and files on the writer thread; the profiles in memory are only touched on the
 * main thread. A player can import once a minute, and the whole server a hundred times in five
 * minutes, so the API key stays below its limits (300 requests in five minutes for a new key).
 */
final class HypixelProfiles {

    static final String FOLDER = "hypixel";
    static final long COOLDOWN_MS = 60_000L;
    /** At most this many imports for the whole server in {@link #WINDOW_MS}. */
    static final int MAX_IMPORTS = 100;
    static final long WINDOW_MS = 5 * 60_000L;
    /** After Hypixel refused the key or asked to slow down, nobody imports for this long. */
    static final long PAUSE_MS = 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    private static final String MOJANG = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String HYPIXEL = "https://api.hypixel.net/v2/player?uuid=";

    private final Plugin plugin;
    private final String apiKey;
    private final File folder;
    private final Executor io;
    private final StatsService.Writer writer;
    private final ExecutorService web;
    /** Online players only. */
    private final Map<UUID, HypixelProfile> profiles = new HashMap<>();
    /** When each player last imported, kept after they leave so that rejoining does not skip the wait. */
    private final Map<UUID, Long> lastImport = new HashMap<>();
    /** When the last imports of the server started, the oldest first. */
    private final Deque<Long> recent = new ArrayDeque<>();
    private final Set<UUID> importing = new HashSet<>();
    /** Until when the import is paused, and what it tells the players meanwhile. */
    private long pausedUntil;
    private String pauseMessage = "";

    /** A request that failed: the console reads the message, the player {@link #forPlayer}. */
    static final class Failure extends IOException {
        private static final long serialVersionUID = 1L;
        final String forPlayer;
        /** True when asking again soon cannot help (a refused key, too many requests). */
        final boolean pause;

        Failure(String log, String forPlayer, boolean pause) {
            super(log);
            this.forPlayer = forPlayer;
            this.pause = pause;
        }
    }

    HypixelProfiles(Plugin plugin, String apiKey, Executor io, StatsService.Writer writer) {
        this.plugin = plugin;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.folder = new File(plugin.getDataFolder(), FOLDER);
        this.io = io;
        this.writer = writer;
        this.web = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby-hypixel");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Told of each import that worked (the player online, main thread): the quests of a new player. */
    private java.util.function.Consumer<UUID> onImported = id -> { };

    void setOnImported(java.util.function.Consumer<UUID> onImported) {
        this.onImported = onImported;
    }

    /** True when config.yml has an API key: without one, nothing can be imported. */
    boolean enabled() {
        return !apiKey.isEmpty();
    }

    /** What the player imported, or null. */
    HypixelProfile get(UUID player) {
        return profiles.get(player);
    }

    /**
     * Copies the Bed Wars settings of a Hypixel player: the player's own account for a null name
     * (one click), else the account with that name. Tells the player how it went.
     */
    void importFor(final Player player, String name) {
        final UUID id = player.getUniqueId();
        if (!enabled()) {
            player.sendMessage(ChatColor.RED + "The Hypixel import is not set up on this server yet.");
            return;
        }
        if (name != null && !name.matches("[A-Za-z0-9_]{1,16}")) {
            player.sendMessage(ChatColor.RED + "\"" + name + "\" is not a Minecraft name.");
            return;
        }
        final long now = System.currentTimeMillis();
        if (now < pausedUntil) {
            player.sendMessage(ChatColor.RED + pauseMessage + ChatColor.GRAY + " Next try in "
                    + ((pausedUntil - now) / 1000 + 1) + " seconds.");
            return;
        }
        lastImport.values().removeIf(time -> now - time >= COOLDOWN_MS);
        while (!recent.isEmpty() && now - recent.peekFirst() >= WINDOW_MS) {
            recent.pollFirst();
        }
        Long last = lastImport.get(id);
        if (last != null) {
            player.sendMessage(ChatColor.GRAY + "Wait " + ((COOLDOWN_MS - (now - last)) / 1000 + 1)
                    + " seconds before importing again.");
            return;
        }
        if (recent.size() >= MAX_IMPORTS) {
            player.sendMessage(ChatColor.GRAY + "Many players are importing right now: try again in a few minutes.");
            return;
        }
        if (!importing.add(id)) {
            return;
        }
        lastImport.put(id, now);
        recent.addLast(now);
        // On an online-mode server, a player's UUID is their Mojang one: no name to look up.
        final UUID own = name == null && id.version() == 4 ? id : null;
        final String asked = name == null ? player.getName() : name;
        player.sendMessage(ChatColor.GRAY + "Asking Hypixel for the shop of " + asked + "...");
        web.execute(() -> {
            HypixelProfile imported = null;
            String problem;
            // The player's own doing (a wrong name, no Bed Wars shop): their minute of waiting stays.
            boolean theirs = false;
            boolean pause = false;
            try {
                UUID uuid = own != null ? own : mojangUuid(asked);
                if (uuid == null) {
                    throw new IllegalArgumentException("No Minecraft account is named " + asked + ".");
                }
                String json = fetch("Hypixel", HYPIXEL + uuid, apiKey);
                if (json == null) {
                    throw new IOException("Hypixel answered nothing");
                }
                imported = HypixelProfile.fromApi(json, asked, System.currentTimeMillis());
                problem = null;
            } catch (IllegalArgumentException e) {
                problem = e.getMessage();
                theirs = true;
            } catch (Failure e) {
                plugin.getLogger().warning("Hypixel import for " + asked + " failed: " + e.getMessage());
                problem = e.forPlayer;
                pause = e.pause;
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Hypixel import for " + asked + " failed: " + e);
                problem = "Hypixel did not answer. Try again in a moment.";
            }
            final HypixelProfile profile = imported;
            final String message = problem;
            final boolean playersFault = theirs;
            final boolean pauseAll = pause;
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                importing.remove(id);
                // The player may have left meanwhile, or come back: the one online now, if any.
                Player online = Bukkit.getPlayer(id);
                if (profile == null) {
                    if (!playersFault) {
                        // Not their fault: they can try again without waiting a minute.
                        lastImport.remove(id);
                    }
                    if (pauseAll) {
                        pausedUntil = System.currentTimeMillis() + PAUSE_MS;
                        pauseMessage = message;
                    }
                    if (online != null) {
                        online.sendMessage(ChatColor.RED + message);
                    }
                    return;
                }
                if (online != null) {
                    profiles.put(id, profile);
                }
                writer.write(file(id), profile.toYaml());
                if (online != null) {
                    online.sendMessage(ChatColor.GREEN + "Imported the Hypixel shop of " + profile.name() + ": "
                            + profile.items() + " items in the quick buy"
                            + (profile.hotbar() != null ? " and the hotbar manager." : ", no hotbar manager.")
                            + ChatColor.YELLOW + " /hypixel shop to see it.");
                    onImported.accept(id);
                }
            });
        });
    }

    /** Reads the player's file on the writer thread, when they join. An import made meanwhile wins. */
    void load(final UUID player) {
        final File file = file(player);
        io.execute(() -> {
            if (!file.isFile()) {
                return;
            }
            final HypixelProfile read;
            try {
                read = HypixelProfile.fromYaml(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.WARNING, "Unreadable Hypixel settings, ignored: " + file, e);
                return;
            }
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(player) != null) {
                        profiles.putIfAbsent(player, read);
                    }
                });
            }
        });
    }

    void forget(UUID player) {
        profiles.remove(player);
    }

    void stop() {
        web.shutdownNow();
    }

    private File file(UUID player) {
        return new File(folder, player + ".yml");
    }

    /** The Mojang UUID of a name, or null if no account has it. */
    static UUID mojangUuid(String name) throws IOException {
        String json = fetch("Mojang", MOJANG + name, null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        JsonElement parsed = new JsonParser().parse(json);
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject().has("id")) {
            return null;
        }
        return undashed(parsed.getAsJsonObject().get("id").getAsString());
    }

    /** "069a79f444e94726a5befca90e38aaf5" to a UUID. */
    static UUID undashed(String id) {
        String hex = id.replace("-", "");
        if (!hex.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalStateException("not a UUID: " + id);
        }
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    /**
     * The body of a GET to Mojang or Hypixel (the service), or null for 204 and 404 (nothing there).
     * An answer with an error is a {@link Failure}. Away from the main thread only.
     */
    static String fetch(String service, String address, String apiKey) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "RagoLobby");
            connection.setRequestProperty("Accept", "application/json");
            if (apiKey != null) {
                connection.setRequestProperty("API-Key", apiKey);
            }
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) {
                return null;
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : read(stream);
            if (status < 400) {
                return body;
            }
            String shown = body.length() > 200 ? body.substring(0, 200) + "..." : body;
            if (status == 403 && apiKey != null) {
                throw new Failure(service + " refused the API key (403): " + shown + ". A new application works once"
                        + " Hypixel has approved it (developer.hypixel.net); otherwise check hypixel.cle-api in config.yml.",
                        "Hypixel refused this server's API key, so the import does not work yet. The console says why.",
                        true);
            }
            if (status == 429) {
                throw new Failure(service + " asks to slow down (429): " + shown,
                        service + " gets too many requests right now.", true);
            }
            throw new Failure(service + " answered " + status + ": " + shown,
                    service + " answered with an error (" + status + "). Try again in a moment.", status == 403);
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
