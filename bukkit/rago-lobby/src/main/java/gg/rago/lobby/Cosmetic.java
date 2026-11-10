package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Effect;
import org.bukkit.Material;

/**
 * A cosmetic of the shop, bought with Rago Points once a player reaches its Network level: a trail
 * behind their projectiles, an effect where their victims die, or an effect around them when they
 * win. A player equips one of each type. The rarest are PREMIUM's: its players own them, nobody
 * buys them. The catalogue is the plugin's own; config.yml (boutique:) may change the price and the
 * level of each. Immutable.
 */
final class Cosmetic {

    /** The kinds of cosmetics, one equipped of each. */
    enum Type {
        TRAIL("trail", "Projectile trails", Material.ARROW, "Behind your arrows, pearls, eggs", "and snowballs."),
        KILL("kill", "Kill effects", Material.DIAMOND_SWORD, "Where a player you kill dies."),
        WIN("win", "Victory effects", Material.FIREWORK, "Around you when you win a game.");

        private final String id;
        private final String title;
        private final Material icon;
        private final String[] description;

        Type(String id, String title, Material icon, String... description) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.description = description;
        }

        String id() {
            return id;
        }

        String title() {
            return title;
        }

        Material icon() {
            return icon;
        }

        String[] description() {
            return description;
        }

        static Type of(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final String id;
    private final Type type;
    private final String name;
    private final Material icon;
    private final Effect particle;
    private final int price;
    private final int level;
    private final boolean premium;

    Cosmetic(String id, Type type, String name, Material icon, Effect particle, int price, int level) {
        this(id, type, name, icon, particle, price, level, false);
    }

    Cosmetic(String id, Type type, String name, Material icon, Effect particle, int price, int level, boolean premium) {
        this.premium = premium;
        this.id = id;
        this.type = type;
        this.name = name;
        this.icon = icon;
        this.particle = particle;
        this.price = price;
        this.level = level;
    }

    String id() {
        return id;
    }

    Type type() {
        return type;
    }

    String name() {
        return name;
    }

    Material icon() {
        return icon;
    }

    /** The particle of the effect. */
    Effect particle() {
        return particle;
    }

    int price() {
        return price;
    }

    int level() {
        return level;
    }

    /** True for a cosmetic of PREMIUM: owned with the rank (rago.perk.gadgets), never sold. */
    boolean premium() {
        return premium;
    }

    /** The same cosmetic at another price and level. */
    Cosmetic priced(int newPrice, int newLevel) {
        return new Cosmetic(id, type, name, icon, particle, newPrice, newLevel, premium);
    }

    /** The plugin's catalogue, by id, in the order of the shop. */
    static Map<String, Cosmetic> catalogue() {
        List<Cosmetic> all = new ArrayList<>();
        all.add(new Cosmetic("trail-clouds", Type.TRAIL, "Clouds", Material.WOOL, Effect.CLOUD, 2_000, 5));
        all.add(new Cosmetic("trail-flames", Type.TRAIL, "Flames", Material.BLAZE_POWDER, Effect.FLAME, 2_500, 5));
        all.add(new Cosmetic("trail-hearts", Type.TRAIL, "Hearts", Material.RED_ROSE, Effect.HEART, 3_000, 10));
        all.add(new Cosmetic("trail-emeralds", Type.TRAIL, "Emerald sparks", Material.EMERALD, Effect.HAPPY_VILLAGER, 3_000, 10));
        all.add(new Cosmetic("trail-notes", Type.TRAIL, "Notes", Material.NOTE_BLOCK, Effect.NOTE, 4_000, 15));
        all.add(new Cosmetic("trail-magic", Type.TRAIL, "Magic", Material.GLOWSTONE_DUST, Effect.WITCH_MAGIC, 5_000, 20));
        all.add(new Cosmetic("trail-ender", Type.TRAIL, "Ender", Material.EYE_OF_ENDER, Effect.PORTAL, 6_000, 25));
        all.add(new Cosmetic("trail-redstone", Type.TRAIL, "Redstone", Material.REDSTONE, Effect.COLOURED_DUST, 8_000, 35));
        all.add(new Cosmetic("kill-blood", Type.KILL, "Blood", Material.REDSTONE_BLOCK, Effect.TILE_BREAK, 3_000, 10));
        all.add(new Cosmetic("kill-hearts", Type.KILL, "Heart burst", Material.RED_ROSE, Effect.HEART, 3_000, 10));
        all.add(new Cosmetic("kill-flames", Type.KILL, "Flame burst", Material.BLAZE_POWDER, Effect.FLAME, 4_000, 15));
        all.add(new Cosmetic("kill-lightning", Type.KILL, "Lightning", Material.BLAZE_ROD, Effect.FIREWORKS_SPARK, 5_000, 20));
        all.add(new Cosmetic("kill-firework", Type.KILL, "Firework", Material.FIREWORK, Effect.FIREWORKS_SPARK, 6_000, 25));
        all.add(new Cosmetic("kill-explosion", Type.KILL, "Explosion", Material.TNT, Effect.EXPLOSION_LARGE, 8_000, 35));
        all.add(new Cosmetic("win-hearts", Type.WIN, "Love", Material.RED_ROSE, Effect.HEART, 4_000, 10));
        all.add(new Cosmetic("win-fireworks", Type.WIN, "Fireworks", Material.FIREWORK, Effect.FIREWORKS_SPARK, 5_000, 10));
        all.add(new Cosmetic("win-flames", Type.WIN, "Ring of fire", Material.BLAZE_POWDER, Effect.FLAME, 6_000, 20));
        all.add(new Cosmetic("win-party", Type.WIN, "Party", Material.JUKEBOX, Effect.NOTE, 8_000, 30));
        // The rarest, PREMIUM's.
        all.add(new Cosmetic("kill-squid", Type.KILL, "Squid rocket", Material.INK_SACK, Effect.FLAME, 0, 1, true));
        all.add(new Cosmetic("kill-meteor", Type.KILL, "Meteor strike", Material.FIREBALL, Effect.LAVA_POP, 0, 1, true));
        all.add(new Cosmetic("win-storm", Type.WIN, "Thunder god", Material.GOLD_AXE, Effect.FIREWORKS_SPARK, 0, 1, true));
        all.add(new Cosmetic("win-meteors", Type.WIN, "Meteor shower", Material.MAGMA_CREAM, Effect.FLAME, 0, 1, true));
        Map<String, Cosmetic> byId = new LinkedHashMap<>();
        for (Cosmetic cosmetic : all) {
            byId.put(cosmetic.id(), cosmetic);
        }
        return Collections.unmodifiableMap(byId);
    }
}
