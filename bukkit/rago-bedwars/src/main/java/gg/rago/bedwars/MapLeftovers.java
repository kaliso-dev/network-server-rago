package gg.rago.bedwars;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;

/**
 * What a map captured on a server kept of the game played there when it was saved: the iron, gold
 * and emeralds lying on its forges and generators, the invisible armour stands of its holograms
 * (ITEM SHOP, Tier I Diamond, Spawns in 18 seconds) and of its floating generator blocks, its mobs.
 * They leave each game world as its chunks load, before a game: a forge starts empty, as on
 * Hypixel. What decorates the map stays: item frames, paintings, the armour stands one can see.
 */
final class MapLeftovers {

    /** Thrown or dropped things that never belong to a map. */
    private static final Set<EntityType> THINGS = EnumSet.of(EntityType.DROPPED_ITEM, EntityType.EXPERIENCE_ORB,
            EntityType.PRIMED_TNT, EntityType.FALLING_BLOCK, EntityType.ARROW, EntityType.SNOWBALL, EntityType.EGG,
            EntityType.FIREBALL, EntityType.SMALL_FIREBALL, EntityType.ENDER_PEARL, EntityType.THROWN_EXP_BOTTLE,
            EntityType.SPLASH_POTION, EntityType.FISHING_HOOK, EntityType.WITHER_SKULL, EntityType.FIREWORK);

    private MapLeftovers() {
    }

    /** True if this entity, found in a map as it loads, is a leftover of the game played there. */
    static boolean leftover(Entity entity) {
        boolean invisibleStand = entity instanceof ArmorStand && !((ArmorStand) entity).isVisible();
        return leftover(entity.getType(), entity instanceof LivingEntity, entity instanceof HumanEntity, invisibleStand);
    }

    static boolean leftover(EntityType type, boolean living, boolean human, boolean invisibleStand) {
        if (type == EntityType.ARMOR_STAND) {
            return invisibleStand;
        }
        return THINGS.contains(type) || (living && !human);
    }
}
