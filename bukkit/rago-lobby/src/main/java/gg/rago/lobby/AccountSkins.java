package gg.rago.lobby;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
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
 * The skins of the NPCs that wear the skin of a Minecraft account (skin-joueur: under an NPC of
 * config.yml, plan 5.6). Mojang signs the skin of an account itself: it is read from Mojang (the
 * UUID of the name, then the session server), away from the main thread, the first time an NPC
 * standing on its point wears it in a run, so that a skin changed since shows after a restart. It
 * is kept in skins-joueurs.yml: the NPC wears it at once on the next start, even if Mojang does not
 * answer then. Until the first answer the NPC wears Steve. Main thread, but for the requests.
 */
final class AccountSkins {

    static final String FILE_NAME = "skins-joueurs.yml";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String SESSION = "https://sessionserver.mojang.com/session/minecraft/profile/";
    /** An account Mojang did not answer for is asked again after this long. */
    private static final long RETRY_MS = 30 * 60_000L;

    private final Plugin plugin;
    private final BiConsumer<File, String> writer;
    private final ExecutorService web;
    /** The skins known, by name in lowercase (a Minecraft name has one owner, whatever its case). */
    private final Map<String, Skin> known = new TreeMap<>();
    /** The names read from Mojang in this run. */
    private final Set<String> fresh = new HashSet<>();
    /** When Mojang was last asked for a name it has not answered for yet. */
    private final Map<String, Long> asked = new HashMap<>();
    /** Runs on the main thread when a skin arrives that differs from the one known. */
    private Runnable changed = () -> { };

    AccountSkins(Plugin plugin, BiConsumer<File, String> writer) {
        this.plugin = plugin;
        this.writer = writer;
        this.web = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "RagoLobby account skins");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** A Minecraft name: 3 to 16 letters, digits or underscores. */
    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    void stop() {
        web.shutdownNow();
    }

    /** What runs (main thread) when a skin arrives that differs from the one known: the NPCs are placed again. */
    void onChange(Runnable then) {
        this.changed = then;
    }

    /** Reads skins-joueurs.yml. */
    void load() {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.isFile()) {
            return;
        }
        try {
            known.putAll(read(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Cannot read " + file + ": the skins of the accounts are asked again", e);
        }
    }

    /**
     * The skin of this account, or null (Steve) while it is unknown. The first call of a run asks
     * Mojang for it, away from the main thread; a name it did not answer for is asked again later.
     */
    Skin get(String name) {
        if (name == null) {
            return null;
        }
        String key = name.toLowerCase(Locale.ROOT);
        Long last = asked.get(key);
        long now = System.currentTimeMillis();
        if (!fresh.contains(key) && (last == null || now - last >= RETRY_MS) && !web.isShutdown()) {
            asked.put(key, now);
            ask(key, name);
        }
        return known.get(key);
    }

    private void ask(final String key, final String name) {
        web.execute(() -> {
            Skin skin;
            try {
                UUID id = HypixelProfiles.mojangUuid(name);
                if (id == null) {
                    plugin.getLogger().warning("skin-joueur: no Minecraft account is named " + name + ": its NPC wears Steve.");
                    return;
                }
                skin = Skin.fromSession(HypixelProfiles.fetch("Mojang", SESSION + id.toString().replace("-", "")
                        + "?unsigned=false", null));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("skin-joueur: no skin from Mojang for " + name + " (asked again in 30 minutes): " + e);
                return;
            }
            if (skin == null || !plugin.isEnabled()) {
                return;
            }
            final Skin found = skin;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                fresh.add(key);
                asked.remove(key);
                if (found.equals(known.get(key))) {
                    return;
                }
                known.put(key, found);
                writer.accept(new File(plugin.getDataFolder(), FILE_NAME), yaml(known));
                plugin.getLogger().info("Skin of " + name + " read from Mojang.");
                changed.run();
            });
        });
    }

    /** The skins of a skins-joueurs.yml, by name in lowercase; an entry that is not one is left out. */
    static Map<String, Skin> read(String text) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IOException("not valid YAML: " + e.getMessage(), e);
        }
        Map<String, Skin> result = new TreeMap<>();
        for (String name : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(name);
            Skin skin = section == null ? null : Skin.of(section.getString("value"), section.getString("signature"));
            if (validName(name) && skin != null) {
                result.put(name.toLowerCase(Locale.ROOT), skin);
            }
        }
        return result;
    }

    static String yaml(Map<String, Skin> skins) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("The skins of the Minecraft accounts the NPCs wear (skin-joueur: in config.yml), as Mojang"
                + " signs them,\nby name. Read again from Mojang at each start; delete the file to forget them.");
        for (Map.Entry<String, Skin> entry : skins.entrySet()) {
            ConfigurationSection section = yaml.createSection(entry.getKey());
            section.set("value", entry.getValue().value());
            section.set("signature", entry.getValue().signature());
        }
        return yaml.saveToString();
    }
}
