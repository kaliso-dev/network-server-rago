package gg.rago.lobby;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.Collection;
import org.bukkit.entity.Player;

/**
 * A player's skin as Mojang signs it: the "textures" property of their profile. A 1.8 client
 * shows the skin of a player entity only with the signature, so both are kept. Immutable.
 */
final class Skin {

    private final String value;
    private final String signature;

    Skin(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    /** The textures, in base64. */
    String value() {
        return value;
    }

    /** Mojang's signature of the value. */
    String signature() {
        return signature;
    }

    /** A value and a signature, both there: the only skins a client shows. */
    static Skin of(String value, String signature) {
        return value == null || value.isEmpty() || signature == null || signature.isEmpty() ? null : new Skin(value, signature);
    }

    /**
     * The skin of an online player: the textures of the profile the server got from Mojang at the
     * login (or from BungeeCord). Null on an offline-mode server, where profiles have none. Bukkit
     * 1.8.8 has no API for it: the profile of CraftPlayer is read by reflection.
     */
    static Skin of(Player player) {
        try {
            Object profile = player.getClass().getMethod("getProfile").invoke(player);
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            Method get = properties.getClass().getMethod("get", Object.class);
            for (Object property : (Collection<?>) get.invoke(properties, "textures")) {
                Object value = property.getClass().getMethod("getValue").invoke(property);
                Object signature = property.getClass().getMethod("getSignature").invoke(property);
                Skin skin = of(value instanceof String ? (String) value : null,
                        signature instanceof String ? (String) signature : null);
                if (skin != null) {
                    return skin;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // not a CraftPlayer of 1.8 (a test, another server): no skin
        }
        return null;
    }

    /**
     * The skin in an answer of Mojang's session server
     * (sessionserver.mojang.com/session/minecraft/profile/uuid?unsigned=false), or null.
     */
    static Skin fromSession(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement root = new JsonParser().parse(json);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("properties")) {
                return null;
            }
            JsonArray properties = root.getAsJsonObject().getAsJsonArray("properties");
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                if ("textures".equals(text(property, "name"))) {
                    return of(text(property, "value"), text(property, "signature"));
                }
            }
        } catch (RuntimeException e) {
            // not the JSON Mojang sends
        }
        return null;
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? null : element.getAsString();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Skin && value.equals(((Skin) other).value) && signature.equals(((Skin) other).signature);
    }

    @Override
    public int hashCode() {
        return value.hashCode() * 31 + signature.hashCode();
    }
}
