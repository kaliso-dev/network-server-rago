package gg.rago.lobby;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * A player's Bed Wars settings copied from Hypixel: the quick buy of the shop (21 slots, three
 * rows of seven, Hypixel's item ids such as "wool" or "stone_sword") and the hotbar manager (the
 * category of each of the 9 hotbar slots, such as "Melee" or "Blocks"). Hypixel gives them in
 * player.stats.Bedwars, as favourites_2 and favorite_slots. Immutable.
 */
final class HypixelProfile {

    /** The quick buy of the Hypixel shop: three rows of seven. */
    static final int QUICK_BUY = 21;
    static final int HOTBAR = 9;

    private final String name;
    private final List<String> quickBuy;
    private final List<String> hotbar;
    private final long importedAt;

    HypixelProfile(String name, List<String> quickBuy, List<String> hotbar, long importedAt) {
        this.name = name;
        this.quickBuy = Collections.unmodifiableList(fit(quickBuy, QUICK_BUY));
        this.hotbar = hotbar == null ? null : Collections.unmodifiableList(fit(hotbar, HOTBAR));
        this.importedAt = importedAt;
    }

    /** The Hypixel name the settings come from. */
    String name() {
        return name;
    }

    /** 21 Hypixel item ids, null for an empty slot. */
    List<String> quickBuy() {
        return quickBuy;
    }

    /** 9 categories, null for a free slot; null when Hypixel had no hotbar manager for the player. */
    List<String> hotbar() {
        return hotbar;
    }

    long importedAt() {
        return importedAt;
    }

    /** How many slots of the quick buy hold an item. */
    int items() {
        int count = 0;
        for (String id : quickBuy) {
            if (id != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The settings in an answer of GET https://api.hypixel.net/v2/player. Throws
     * {@link IllegalArgumentException} with a message for the player when there is nothing to copy.
     */
    static HypixelProfile fromApi(String json, String askedName, long now) {
        JsonObject root;
        try {
            JsonElement parsed = new JsonParser().parse(json);
            root = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            root = null;
        }
        if (root == null) {
            throw new IllegalStateException("Hypixel sent an answer that is not JSON");
        }
        if (root.has("success") && !root.get("success").getAsBoolean()) {
            throw new IllegalStateException("Hypixel refused the request: " + text(root, "cause"));
        }
        JsonObject player = object(root, "player");
        if (player == null) {
            throw new IllegalArgumentException(askedName + " has never played on Hypixel.");
        }
        String name = text(player, "displayname");
        if (name == null) {
            name = askedName;
        }
        JsonObject bedwars = object(object(player, "stats"), "Bedwars");
        String favourites = bedwars == null ? null : text(bedwars, "favourites_2");
        if (favourites == null || favourites.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " has no Bed Wars quick buy on Hypixel (never changed it, "
                    + "or never played Bed Wars).");
        }
        String slots = text(bedwars, "favorite_slots");
        return new HypixelProfile(name, split(favourites), slots == null || slots.trim().isEmpty() ? null : split(slots),
                now);
    }

    /** "wool,stone_sword,null,..." to a list, "null" and "" as null. */
    static List<String> split(String text) {
        List<String> entries = new ArrayList<>();
        for (String entry : text.split(",", -1)) {
            String id = entry.trim();
            entries.add(id.isEmpty() || id.equalsIgnoreCase("null") ? null : id);
        }
        return entries;
    }

    String toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Bed Wars settings imported from Hypixel by RagoLobby (/hypixel import).");
        yaml.set("name", name);
        yaml.set("imported", importedAt);
        yaml.set("quick-buy", written(quickBuy));
        if (hotbar != null) {
            yaml.set("hotbar", written(hotbar));
        }
        return yaml.saveToString();
    }

    static HypixelProfile fromYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        List<String> quickBuy = read(yaml.getStringList("quick-buy"));
        List<String> hotbar = yaml.contains("hotbar") ? read(yaml.getStringList("hotbar")) : null;
        return new HypixelProfile(yaml.getString("name", "?"), quickBuy, hotbar, yaml.getLong("imported", 0L));
    }

    private static List<String> written(List<String> entries) {
        List<String> written = new ArrayList<>();
        for (String entry : entries) {
            written.add(entry == null ? "" : entry);
        }
        return written;
    }

    private static List<String> read(List<String> entries) {
        List<String> read = new ArrayList<>();
        for (String entry : entries) {
            String id = entry == null ? "" : entry.trim();
            read.add(id.isEmpty() || id.equalsIgnoreCase("null") ? null : id);
        }
        return read;
    }

    /** The list cut or padded with nulls to its size. */
    private static List<String> fit(List<String> entries, int size) {
        List<String> fitted = new ArrayList<>(entries.subList(0, Math.min(entries.size(), size)));
        while (fitted.size() < size) {
            fitted.add(null);
        }
        return fitted;
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static String text(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonPrimitive()) {
            return null;
        }
        return parent.get(key).getAsString();
    }

    @Override
    public String toString() {
        return name + " " + quickBuy + " " + hotbar;
    }
}
