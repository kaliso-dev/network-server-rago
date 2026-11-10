package gg.rago.lobby;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * The skins of the NPCs (skin: under an NPC of config.yml). A skin is a PNG, skins/name.png in
 * the folder of the plugin, else the one of that name in the jar. A 1.8 client shows a skin on an
 * NPC only with Mojang's signature, so each PNG goes once to MineSkin ({@link MineSkin}), away
 * from the main thread, and its signed textures are kept in skins.yml with the hash of the PNG: a
 * PNG that changes is signed again. Until then the NPC wears Steve. Main thread, but for the requests.
 */
final class NpcSkins {

    static final String FILE_NAME = "skins.yml";
    static final String FOLDER = "skins";
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
    /** The least time between two requests to MineSkin. */
    private static final long BETWEEN_MS = 2_000L;
    /** How many times a skin is asked again when MineSkin says to wait. */
    private static final int TRIES = 4;

    /** A PNG and, once signed, its textures. */
    static final class Signed {
        final String hash;
        final Skin skin;

        Signed(String hash, Skin skin) {
            this.hash = hash;
            this.skin = skin;
        }
    }

    private final Plugin plugin;
    private final String api;
    private final String key;
    private final BiConsumer<File, String> writer;
    private final ExecutorService web;
    /** Every skin signed so far (skins.yml), by name, even of a PNG gone: it costs nothing to keep. */
    private final Map<String, Signed> signed = new TreeMap<>();
    /** The hash of the PNG of each skin the NPCs wear now. */
    private final Map<String, String> hashes = new HashMap<>();
    /** The PNGs to sign, by name. */
    private final Map<String, byte[]> pending = new HashMap<>();

    NpcSkins(Plugin plugin, String api, String key, BiConsumer<File, String> writer) {
        this.plugin = plugin;
        this.api = api;
        this.key = key;
        this.writer = writer;
        this.web = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby NPC skins");
            thread.setDaemon(true);
            return thread;
        });
    }

    static boolean validId(String id) {
        return ID.matcher(id).matches();
    }

    void stop() {
        web.shutdownNow();
    }

    /** Reads skins.yml and the PNGs of these skins; the ones never signed are kept for {@link #sign}. */
    void load(Collection<String> ids) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (file.isFile()) {
            try {
                signed.putAll(read(new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8)));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Cannot read " + file + ": the skins of the NPCs are signed again", e);
            }
        }
        for (String id : new LinkedHashSet<>(ids)) {
            byte[] png = png(id);
            if (png == null) {
                plugin.getLogger().warning("No skin " + id + ": put " + FOLDER + "/" + id + ".png (64x64) in "
                        + plugin.getDataFolder() + ". The NPC wears Steve.");
                continue;
            }
            String hash = sha1(png);
            hashes.put(id, hash);
            Signed known = signed.get(id);
            if (known == null || !known.hash.equals(hash)) {
                pending.put(id, png);
            }
        }
    }

    /** The signed skin of this name, or null (Steve) while it is not signed, or for null. */
    Skin get(String id) {
        if (id == null) {
            return null;
        }
        Signed known = signed.get(id);
        String hash = hashes.get(id);
        return known != null && known.hash.equals(hash) ? known.skin : null;
    }

    /** How many skins wait for MineSkin. */
    int pending() {
        return pending.size();
    }

    /**
     * Sends the skins never signed to MineSkin, one at a time; after each one signed, on the main
     * thread, keeps it in skins.yml and runs then (the NPCs are placed again in it).
     */
    void sign(final Runnable then) {
        if (pending.isEmpty()) {
            return;
        }
        final List<Map.Entry<String, byte[]>> work = new ArrayList<>(pending.entrySet());
        pending.clear();
        plugin.getLogger().info("Signing " + work.size() + " NPC skin(s) through MineSkin"
                + (key.isEmpty() ? " (no cle-mineskin: slower)" : "") + "...");
        web.execute(() -> {
            for (Map.Entry<String, byte[]> entry : work) {
                final String id = entry.getKey();
                final byte[] png = entry.getValue();
                Skin skin = null;
                for (int attempt = 1; attempt <= TRIES && skin == null; attempt++) {
                    try {
                        skin = MineSkin.upload(api, png, "rago-" + id, key);
                    } catch (MineSkin.Refused e) {
                        if (e.status() == 401 || e.status() == 403) {
                            plugin.getLogger().warning(e.getMessage() + ". Put a free key from https://account.mineskin.org/keys"
                                    + " in cle-mineskin of config.yml, then restart: the NPCs wear Steve until then.");
                            return;
                        }
                        if (e.status() != 429 && e.waitMs() == 0 || attempt == TRIES) {
                            plugin.getLogger().warning("Skin " + id + " not signed: " + e.getMessage());
                            break;
                        }
                        if (!pause(Math.max(BETWEEN_MS, e.waitMs()))) {
                            return;
                        }
                    } catch (IOException e) {
                        plugin.getLogger().warning("Skin " + id + " not signed (MineSkin out of reach?): " + e);
                        break;
                    }
                }
                if (skin != null) {
                    final Skin done = skin;
                    final String hash = sha1(png);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        signed.put(id, new Signed(hash, done));
                        writer.accept(new File(plugin.getDataFolder(), FILE_NAME), yaml(signed));
                        plugin.getLogger().info("Skin " + id + " signed.");
                        then.run();
                    });
                }
                if (!pause(BETWEEN_MS)) {
                    return;
                }
            }
        });
    }

    private static boolean pause(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** The PNG of a skin: the one of the plugin's folder first, else the jar's. Null if neither. */
    private byte[] png(String id) {
        File own = new File(new File(plugin.getDataFolder(), FOLDER), id + ".png");
        try {
            if (own.isFile()) {
                return Files.readAllBytes(own.toPath());
            }
            InputStream in = plugin.getResource(FOLDER + "/" + id + ".png");
            if (in == null) {
                return null;
            }
            try (InputStream stream = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = stream.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot read the skin " + id, e);
            return null;
        }
    }

    static String sha1(byte[] data) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-1").digest(data)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The skins of a skins.yml; an entry that is not one is left out. */
    static Map<String, Signed> read(String text) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IOException("not valid YAML: " + e.getMessage(), e);
        }
        Map<String, Signed> result = new TreeMap<>();
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            Skin skin = section == null ? null : Skin.of(section.getString("value"), section.getString("signature"));
            String hash = section == null ? null : section.getString("png");
            if (validId(id) && skin != null && hash != null) {
                result.put(id, new Signed(hash, skin));
            }
        }
        return result;
    }

    static String yaml(Map<String, Signed> skins) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("The skins of the NPCs as Mojang signed them (through MineSkin), by name, with the hash"
                + " of their PNG.\nDelete an entry, or the file, to sign again.");
        for (Map.Entry<String, Signed> entry : skins.entrySet()) {
            ConfigurationSection section = yaml.createSection(entry.getKey());
            section.set("png", entry.getValue().hash);
            section.set("value", entry.getValue().skin.value());
            section.set("signature", entry.getValue().skin.signature());
        }
        return yaml.saveToString();
    }
}
