package gg.rago.lobby;

import gg.rago.lobby.api.ReplayRecording;
import gg.rago.lobby.api.ReplaySetup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;

/**
 * One game being recorded: each tick, where its players are, how they look and what they hold,
 * the blocks that changed in its part of the world and its projectiles; with the hits, swings,
 * explosions and chat lines the service passes on. Kept gzipped in memory until the end of the
 * game, then written by the service. Main thread only (the world hook may add changed blocks).
 */
final class ReplayRecorder implements ReplayRecording {

    /** A game longer than this stops being recorded (its replay keeps the first 90 minutes). */
    static final int MAX_TICKS = 20 * 60 * 90;
    /** At most this many players in a replay. */
    static final int MAX_ACTORS = 200;
    /** A replay shorter than this is not kept. */
    static final int MIN_TICKS = 20;

    /** A player of the game, and what was last written of them. */
    private static final class Tracked {
        final int index;
        final ReplayData.Actor actor;
        boolean placed;
        int x;
        int y;
        int z;
        int yaw;
        int pitch;
        int flags = -1;
        int health = -1;
        final ReplayData.Item[] items = new ReplayData.Item[ReplayData.SLOTS];

        Tracked(int index, ReplayData.Actor actor) {
            this.index = index;
            this.actor = actor;
        }
    }

    /** A projectile or another entity followed, and where it was last written. */
    private static final class Followed {
        final int id;
        int x;
        int y;
        int z;
        int angles;

        Followed(int id) {
            this.id = id;
        }
    }

    private final ReplayService service;
    private final ReplaySetup setup;
    private final ReplayData.BodyWriter body = new ReplayData.BodyWriter();
    private final long startedAt = System.currentTimeMillis();
    private final List<Tracked> actors = new ArrayList<>();
    private final Map<UUID, Tracked> byId = new HashMap<>();
    /** Blocks changed since the last tick, as keys of {@link #key}. */
    private final Set<Long> dirty = new LinkedHashSet<>();
    /** The state last written of each block that changed: id << 4 | data. */
    private final Map<Long, Integer> written = new HashMap<>();
    private final Map<Integer, Followed> followed = new HashMap<>();
    private int nextThing = 1;
    private boolean done;
    private String id;

    ReplayRecorder(ReplayService service, ReplaySetup setup) {
        this.service = service;
        this.setup = setup;
    }

    ReplaySetup setup() {
        return setup;
    }

    World world() {
        return setup.world();
    }

    boolean done() {
        return done;
    }

    boolean has(UUID player) {
        return byId.containsKey(player);
    }

    // --- ReplayRecording ------------------------------------------------------------------------

    @Override
    public void actor(Player player, ChatColor colour) {
        if (done || byId.containsKey(player.getUniqueId()) || actors.size() >= MAX_ACTORS) {
            return;
        }
        String nick = service.nickOf(player.getUniqueId());
        // a nicked player keeps their nick in the replay, and no skin of theirs
        Skin skin = nick == null ? Skin.of(player) : null;
        ReplayData.Actor actor = new ReplayData.Actor(player.getUniqueId(), nick == null ? player.getName() : nick,
                colour == null ? ChatColor.WHITE.getChar() : colour.getChar(), skin == null ? "" : skin.value(),
                skin == null ? "" : skin.signature());
        Tracked tracked = new Tracked(actors.size(), actor);
        actors.add(tracked);
        byId.put(player.getUniqueId(), tracked);
        service.actorAdded(this, player.getUniqueId());
    }

    @Override
    public void chat(String line) {
        if (!done && line != null && !line.isEmpty()) {
            body.chat(line);
        }
    }

    @Override
    public String finish(String result) {
        if (done) {
            return id;
        }
        done = true;
        service.stopped(this);
        byte[] bytes = body.finish();
        if (body.ticks() < MIN_TICKS || actors.isEmpty()) {
            return null;
        }
        ReplayData.Info info = new ReplayData.Info();
        info.id = service.newId();
        info.modeId = setup.modeId();
        info.modeName = setup.modeName();
        info.mapName = setup.mapName();
        info.startedAt = startedAt;
        info.ticks = body.ticks();
        info.result = result == null ? "" : result;
        info.stageKind = setup.stageKind();
        info.stageKey = setup.stageKey();
        for (Tracked tracked : actors) {
            info.actors.add(tracked.actor);
        }
        service.save(info, setup.blocks(), bytes);
        id = info.id;
        return id;
    }

    @Override
    public void cancel() {
        if (!done) {
            done = true;
            service.stopped(this);
            body.finish();
        }
    }

    // --- What the service passes on -------------------------------------------------------------

    /** A block of the world changed (from the world hook): written at the end of the tick. */
    void changed(int x, int y, int z) {
        if (!done && setup.contains(x, z)) {
            dirty.add(key(x, y, z));
        }
    }

    void swing(UUID player) {
        Tracked tracked = byId.get(player);
        if (!done && tracked != null && tracked.flags != -1 && (tracked.flags & ReplayData.HIDDEN) == 0) {
            body.swing(tracked.index);
        }
    }

    void hurt(UUID player) {
        Tracked tracked = byId.get(player);
        if (!done && tracked != null && tracked.flags != -1 && (tracked.flags & ReplayData.HIDDEN) == 0) {
            body.hurt(tracked.index);
        }
    }

    void explosion(Location at) {
        if (!done && setup.contains(at.getBlockX(), at.getBlockZ())) {
            body.explosion(ReplayData.fixed(at.getX(), setup.originX()), ReplayData.fixed(at.getY(), setup.originY()),
                    ReplayData.fixed(at.getZ(), setup.originZ()));
        }
    }

    /** The end of a tick: the players, the blocks and the projectiles as they are now. */
    @SuppressWarnings("deprecation") // Block#getTypeId and getData: the 1.8 block id and data
    void tick(List<Entity> things) {
        if (done || body.ticks() >= MAX_TICKS) {
            return;
        }
        World world = setup.world();
        for (Tracked tracked : actors) {
            Player player = Bukkit.getPlayer(tracked.actor.id);
            Location at = player == null ? null : player.getLocation();
            boolean shown = player != null && player.isOnline() && !player.isDead() && at.getWorld().equals(world)
                    && player.getGameMode() != GameMode.SPECTATOR && setup.contains(at.getBlockX(), at.getBlockZ());
            if (shown) {
                position(tracked, at);
                equipment(tracked, player.getInventory());
                int health = (int) Math.ceil(player.getHealth());
                if (health != tracked.health) {
                    tracked.health = health;
                    body.health(tracked.index, Math.max(0, Math.min(255, health)));
                }
            }
            int flags = shown ? flagsOf(player) : ReplayData.HIDDEN;
            if (flags != tracked.flags) {
                tracked.flags = flags;
                body.flags(tracked.index, flags);
            }
        }
        if (!dirty.isEmpty()) {
            for (long key : dirty) {
                int x = xOf(key);
                int y = yOf(key);
                int z = zOf(key);
                if (y < 0 || y > 255) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                int state = block.getTypeId() << 4 | (block.getData() & 0xF);
                Integer last = written.put(key, state);
                if (last == null || last != state) {
                    body.block(x - setup.originX(), y - setup.originY(), z - setup.originZ(), state >> 4, state & 0xF);
                }
            }
            dirty.clear();
        }
        follow(things);
        body.tick();
    }

    private void position(Tracked tracked, Location at) {
        int x = ReplayData.fixed(at.getX(), setup.originX());
        int y = ReplayData.fixed(at.getY(), setup.originY());
        int z = ReplayData.fixed(at.getZ(), setup.originZ());
        int yaw = ReplayData.angle(at.getYaw());
        int pitch = ReplayData.angle(at.getPitch());
        int dx = x - tracked.x;
        int dy = y - tracked.y;
        int dz = z - tracked.z;
        if (!tracked.placed || Math.abs(dx) > 127 || Math.abs(dy) > 127 || Math.abs(dz) > 127) {
            body.position(tracked.index, x, y, z, yaw, pitch);
        } else if (dx != 0 || dy != 0 || dz != 0 || yaw != tracked.yaw || pitch != tracked.pitch) {
            body.move(tracked.index, dx, dy, dz, yaw, pitch);
        }
        tracked.placed = true;
        tracked.x = x;
        tracked.y = y;
        tracked.z = z;
        tracked.yaw = yaw;
        tracked.pitch = pitch;
    }

    private void equipment(Tracked tracked, PlayerInventory inventory) {
        ItemStack[] now = {inventory.getItemInHand(), inventory.getBoots(), inventory.getLeggings(), inventory.getChestplate(),
            inventory.getHelmet()};
        for (int slot = 0; slot < ReplayData.SLOTS; slot++) {
            ReplayData.Item item = itemOf(now[slot]);
            if (!item.equals(tracked.items[slot])) {
                tracked.items[slot] = item;
                body.equip(tracked.index, slot, item);
            }
        }
    }

    /** An item as the replay keeps it: what it is, whether it shines, the colour of leather. */
    @SuppressWarnings("deprecation") // ItemStack#getTypeId: the 1.8 item id
    static ReplayData.Item itemOf(ItemStack stack) {
        if (stack == null || stack.getTypeId() == 0) {
            return ReplayData.Item.NONE;
        }
        int colour = -1;
        if (stack.hasItemMeta() && stack.getItemMeta() instanceof LeatherArmorMeta) {
            colour = ((LeatherArmorMeta) stack.getItemMeta()).getColor().asRGB();
        }
        return new ReplayData.Item(stack.getTypeId(), stack.getDurability() & 0xFFFF, !stack.getEnchantments().isEmpty(), colour);
    }

    private static int flagsOf(Player player) {
        int flags = 0;
        if (player.isSneaking()) {
            flags |= ReplayData.SNEAK;
        }
        if (player.isSprinting()) {
            flags |= ReplayData.SPRINT;
        }
        if (player.isBlocking()) {
            flags |= ReplayData.USING;
        }
        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            flags |= ReplayData.INVISIBLE;
        }
        if (player.getFireTicks() > 0) {
            flags |= ReplayData.BURNING;
        }
        return flags;
    }

    /** The projectiles, primed TNT and falling blocks of the game's part of the world. */
    private void follow(List<Entity> things) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : things) {
            Location at = entity.getLocation();
            if (!setup.contains(at.getBlockX(), at.getBlockZ())) {
                continue;
            }
            int kind = kindOf(entity);
            if (kind == 0) {
                continue;
            }
            seen.add(entity.getEntityId());
            int x = ReplayData.fixed(at.getX(), setup.originX());
            int y = ReplayData.fixed(at.getY(), setup.originY());
            int z = ReplayData.fixed(at.getZ(), setup.originZ());
            int yaw = ReplayData.angle(at.getYaw());
            int pitch = ReplayData.angle(at.getPitch());
            int angles = (yaw & 0xFF) << 8 | (pitch & 0xFF);
            Followed thing = followed.get(entity.getEntityId());
            if (thing == null) {
                thing = new Followed(nextThing++);
                followed.put(entity.getEntityId(), thing);
                body.thingAdded(thing.id, kind, x, y, z, yaw, pitch, extraOf(entity));
            } else if (x != thing.x || y != thing.y || z != thing.z || angles != thing.angles) {
                body.thingMoved(thing.id, x, y, z, yaw, pitch);
            }
            thing.x = x;
            thing.y = y;
            thing.z = z;
            thing.angles = angles;
        }
        for (Iterator<Map.Entry<Integer, Followed>> it = followed.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Followed> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                body.thingGone(entry.getValue().id);
                it.remove();
            }
        }
    }

    /** The kind of a followed entity (see ReplayData), 0 for one that is not followed. */
    static int kindOf(Entity entity) {
        if (entity instanceof Arrow) {
            return ReplayData.ARROW;
        } else if (entity instanceof Snowball) {
            return ReplayData.SNOWBALL;
        } else if (entity instanceof Egg) {
            return ReplayData.EGG;
        } else if (entity instanceof SmallFireball) {
            return ReplayData.SMALL_FIREBALL;
        } else if (entity instanceof WitherSkull) {
            return 0;
        } else if (entity instanceof Fireball) {
            return ReplayData.FIREBALL;
        } else if (entity instanceof EnderPearl) {
            return ReplayData.ENDER_PEARL;
        } else if (entity instanceof TNTPrimed) {
            return ReplayData.TNT;
        } else if (entity instanceof FallingBlock) {
            return ReplayData.FALLING_BLOCK;
        } else if (entity instanceof ThrownPotion) {
            return ReplayData.POTION;
        }
        return 0;
    }

    @SuppressWarnings("deprecation") // FallingBlock#getBlockId and getBlockData: the 1.8 block id and data
    private static int extraOf(Entity entity) {
        if (entity instanceof FallingBlock) {
            FallingBlock block = (FallingBlock) entity;
            return block.getBlockId() | (block.getBlockData() & 0xF) << 12;
        }
        if (entity instanceof ThrownPotion) {
            return ((ThrownPotion) entity).getItem().getDurability() & 0xFFFF;
        }
        return 0;
    }

    // --- Block keys -----------------------------------------------------------------------------

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int xOf(long key) {
        return (int) (key >> 38);
    }

    static int zOf(long key) {
        return (int) (key << 26 >> 38);
    }

    static int yOf(long key) {
        return (int) (key & 0xFFF);
    }
}
