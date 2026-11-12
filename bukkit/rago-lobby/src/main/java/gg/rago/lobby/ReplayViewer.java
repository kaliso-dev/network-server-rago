package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;

/**
 * One player watching a replay: the game's players are bodies sent to their client alone (through
 * PacketEvents), moved each tick as they moved, with their items, hits and swings; the blocks
 * change in the stage world as they changed in the game, and are put back when the replay jumps
 * back or ends. The viewer flies around, and the hotbar runs the replay: the players, 10 seconds
 * back, pause, 10 seconds forward, the speed, leave. Main thread only.
 */
final class ReplayViewer {

    /** The bodies within this distance of the viewer are sent, and taken back beyond the second. */
    static final double SHOW_RANGE = 64;
    static final double HIDE_RANGE = 80;
    /** How far a jump of the hotbar goes. */
    static final int JUMP_TICKS = 200;
    static final double[] SPEEDS = {0.25, 0.5, 1, 2, 4};
    private static final int NORMAL_SPEED = 2;
    /** The tab list keeps a body this long: the client needs the entry to make the player. */
    private static final long TAB_LIST_TICKS = 40L;
    private static final int SKIN_PARTS = 10;
    private static final byte ALL_PARTS = 0x7F;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1_700_000_000);

    static final int SLOT_PLAYERS = 0;
    static final int SLOT_BACK = 2;
    static final int SLOT_PAUSE = 3;
    static final int SLOT_FORWARD = 4;
    static final int SLOT_SPEED = 6;
    static final int SLOT_LEAVE = 8;
    static final String PLAYERS = ChatColor.GREEN + "Players" + ChatColor.GRAY + " (right click)";
    static final String BACK = ChatColor.YELLOW + "« 10 seconds" + ChatColor.GRAY + " (right click)";
    static final String FORWARD = ChatColor.YELLOW + "10 seconds »" + ChatColor.GRAY + " (right click)";
    static final String PAUSE = ChatColor.GOLD + "Pause" + ChatColor.GRAY + " (right click)";
    static final String PLAY = ChatColor.GREEN + "Play" + ChatColor.GRAY + " (right click)";
    static final String SPEED = ChatColor.AQUA + "Speed";
    static final String LEAVE = ChatColor.RED + "Leave the replay" + ChatColor.GRAY + " (right click)";

    private final ReplayService service;
    private final Player viewer;
    private final ReplayData data;
    private final World world;
    private final int ox;
    private final int oy;
    private final int oz;
    private final Runnable release;
    private final String title;
    /** The middle of the game: where its players first were, on average. */
    private final Location centre;

    /** The last tick shown, -1 before the first. */
    private int tick = -1;
    /** Where the replay is, in ticks (with the fraction of the slow speeds). */
    private double position;
    private boolean paused;
    private boolean ended;
    private int speed = NORMAL_SPEED;
    private int age;

    private final int[] entityIds;
    private final UserProfile[] profiles;
    private final boolean[] shown;
    private final int[][] sent;
    private final ReplayData.Item[][] sentItems;

    /** The projectiles shown: their id in the replay, the entity sent. */
    private final Map<Integer, Integer> thingIds = new HashMap<>();
    /** The blocks changed, to put back: {tick, key, id << 4 | data before}. */
    private final Deque<long[]> undo = new ArrayDeque<>();
    private final Map<ReplayData.Item, com.github.retrooper.packetevents.protocol.item.ItemStack> items = new HashMap<>();

    ReplayViewer(ReplayService service, Player viewer, ReplayData data, World world, int ox, int oy, int oz, Runnable release) {
        this.service = service;
        this.viewer = viewer;
        this.data = data;
        this.world = world;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.release = release;
        String mode = data.info.modeName.isEmpty() ? data.info.modeId : data.info.modeName;
        this.title = mode + (data.info.mapName.isEmpty() ? "" : " on " + data.info.mapName);
        int actors = data.actors();
        entityIds = new int[actors];
        profiles = new UserProfile[actors];
        shown = new boolean[actors];
        sent = new int[actors][];
        sentItems = new ReplayData.Item[actors][ReplayData.SLOTS];
        for (int a = 0; a < actors; a++) {
            ReplayData.Actor actor = data.info.actors.get(a);
            entityIds[a] = NEXT_ID.getAndIncrement();
            String name = ChatColor.COLOR_CHAR + "" + actor.colour + actor.name;
            List<TextureProperty> skin = actor.skinValue.isEmpty() || actor.skinSignature.isEmpty()
                    ? Collections.<TextureProperty>emptyList()
                    : Collections.singletonList(new TextureProperty("textures", actor.skinValue, actor.skinSignature));
            profiles[a] = new UserProfile(FakePlayer.steveId(), name.length() > 16 ? name.substring(0, 16) : name, skin);
        }
        centre = centreOf();
    }

    /** Where the players were the first time each was in the game, on average; the origin without any. */
    private Location centreOf() {
        double x = 0;
        double y = 0;
        double z = 0;
        int count = 0;
        for (int a = 0; a < data.actors(); a++) {
            for (int t = 0; t < data.ticks; t++) {
                if ((data.flags[a][t] & ReplayData.HIDDEN) == 0) {
                    Location at = at(a, t);
                    x += at.getX();
                    y += at.getY();
                    z += at.getZ();
                    count++;
                    break;
                }
            }
        }
        return count == 0 ? origin() : new Location(world, x / count, y / count, z / count);
    }

    Player player() {
        return viewer;
    }

    World world() {
        return world;
    }

    String id() {
        return data.info.id;
    }

    Runnable release() {
        return release;
    }

    /** Where the stage's origin is in its world. */
    Location origin() {
        return new Location(world, ox + 0.5, oy, oz + 0.5);
    }

    /** The middle of the game in the stage world (a map's games are far from its origin). */
    Location centre() {
        return centre.clone();
    }

    // --- Start and end --------------------------------------------------------------------------

    /** The viewer goes to the stage, where the first player starts, flying, with the hotbar of the replay. */
    void begin() {
        Location start = startPoint();
        viewer.teleport(start);
        viewer.setGameMode(GameMode.ADVENTURE);
        viewer.setAllowFlight(true);
        viewer.setFlying(true);
        viewer.setHealth(viewer.getMaxHealth());
        viewer.setFoodLevel(20);
        viewer.setFireTicks(0);
        viewer.setFallDistance(0);
        for (PotionEffect effect : viewer.getActivePotionEffects()) {
            viewer.removePotionEffect(effect.getType());
        }
        hotbar();
        viewer.sendMessage(ChatColor.GREEN + "Replay of " + ChatColor.WHITE + title + ChatColor.GREEN + ", "
                + ReplayData.clock(data.ticks) + " long. " + ChatColor.GRAY + "Fly around; the hotbar runs the replay.");
        if (!data.info.result.isEmpty()) {
            viewer.sendMessage(ChatColor.GRAY + "Result: " + ChatColor.WHITE + data.info.result);
        }
        sidebar();
    }

    /** Above the first player's start, looking the way they look. */
    private Location startPoint() {
        for (int t = 0; t < Math.min(data.ticks, 200); t++) {
            for (int a = 0; a < data.actors(); a++) {
                if ((data.flags[a][t] & ReplayData.HIDDEN) == 0) {
                    Location at = at(a, t);
                    at.setY(at.getY() + 4);
                    at.setPitch(30);
                    return at;
                }
            }
        }
        return centre().add(0, 10, 0);
    }

    /**
     * The end: the bodies leave the client, the blocks are put back as the stage was. The stage
     * is released by the service.
     */
    void end() {
        if (viewer.isOnline()) {
            List<Integer> ids = new ArrayList<>();
            for (int a = 0; a < data.actors(); a++) {
                if (shown[a]) {
                    ids.add(entityIds[a]);
                }
            }
            ids.addAll(thingIds.values());
            if (!ids.isEmpty()) {
                send(new WrapperPlayServerDestroyEntities(toArray(ids)));
            }
        }
        thingIds.clear();
        undoTo(-1);
    }

    // --- Each tick ------------------------------------------------------------------------------

    void tick() {
        age++;
        if (!paused && !ended) {
            position += SPEEDS[speed];
            int target = (int) Math.min(data.ticks - 1, Math.floor(position));
            if (target > tick) {
                forward(target, target - tick > 4);
            }
            if (position >= data.ticks - 1) {
                position = data.ticks - 1;
                ended = true;
                paused = true;
                hotbar();
                viewer.sendMessage(ChatColor.YELLOW + "End of the replay. " + ChatColor.GRAY
                        + "Play watches it again from the start; the bed leaves.");
            }
        }
        bodies();
        if (age % 10 == 0) {
            sidebar();
        }
    }

    /** Forward to this tick: what happened on the way, at once when it is a jump. */
    @SuppressWarnings("deprecation") // Player#playEffect(Location, Effect, int): the 1.8 way to show a blast to one player
    private void forward(int target, boolean jump) {
        int lines = 0;
        for (int t = tick + 1; t <= target; t++) {
            boolean last = !jump || t > target - 2;
            for (ReplayData.Event event : data.events(t)) {
                switch (event.type) {
                    case ReplayData.BLOCK:
                        block(t, event.a, event.b, event.c, event.d, event.e);
                        break;
                    case ReplayData.SWING:
                        if (last && shown[event.a]) {
                            send(new WrapperPlayServerEntityAnimation(entityIds[event.a],
                                    WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM));
                        }
                        break;
                    case ReplayData.HURT:
                        if (last && shown[event.a]) {
                            send(new WrapperPlayServerEntityStatus(entityIds[event.a], 2));
                        }
                        break;
                    case ReplayData.ENT_ADD:
                        if (!jump) {
                            spawnThing(event.a, event.b, event.c, event.d, event.e, event.f, event.g);
                        }
                        break;
                    case ReplayData.ENT_MOVE:
                        if (!jump) {
                            moveThing(event.a, event.b, event.c, event.d, event.e);
                        }
                        break;
                    case ReplayData.ENT_GONE:
                        if (!jump) {
                            removeThing(event.a);
                        }
                        break;
                    case ReplayData.CHAT:
                        if (lines++ < 12) {
                            viewer.sendMessage(event.text);
                        }
                        break;
                    case ReplayData.EXPLODE:
                        if (last) {
                            Location at = new Location(world, ox + event.a / (double) ReplayData.UNIT,
                                    oy + event.b / (double) ReplayData.UNIT, oz + event.c / (double) ReplayData.UNIT);
                            viewer.playEffect(at, Effect.EXPLOSION_LARGE, 0);
                            viewer.playSound(at, Sound.EXPLODE, 2.0F, 1.0F);
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        tick = target;
        if (jump) {
            things();
        }
    }

    /** Jumps to this tick, before or after the one shown. */
    void seek(int target) {
        target = Math.max(0, Math.min(data.ticks - 1, target));
        if (target > tick) {
            forward(target, true);
        } else if (target < tick) {
            undoTo(target);
            tick = target;
            things();
        }
        position = target;
        if (ended && target < data.ticks - 1) {
            ended = false;
        }
        sidebar();
    }

    // --- Blocks ---------------------------------------------------------------------------------

    @SuppressWarnings("deprecation") // Block#getTypeId, getData and setTypeIdAndData: the 1.8 block id and data
    private void block(int t, int x, int y, int z, int id, int value) {
        int wy = oy + y;
        if (wy < 0 || wy > 255) {
            return;
        }
        Block block = world.getBlockAt(ox + x, wy, oz + z);
        undo.push(new long[] {t, ReplayRecorder.key(ox + x, wy, oz + z), block.getTypeId() << 4 | (block.getData() & 0xF)});
        block.setTypeIdAndData(id, (byte) value, false);
    }

    /** Puts back the blocks changed after this tick (all of them for -1). */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData
    private void undoTo(int target) {
        while (!undo.isEmpty() && undo.peek()[0] > target) {
            long[] change = undo.pop();
            long key = change[1];
            int state = (int) change[2];
            world.getBlockAt(ReplayRecorder.xOf(key), ReplayRecorder.yOf(key), ReplayRecorder.zOf(key))
                    .setTypeIdAndData(state >> 4, (byte) (state & 0xF), false);
        }
    }

    // --- The players ----------------------------------------------------------------------------

    /** Where this player is at this tick, in the stage world. */
    Location at(int actor, int t) {
        Location at = new Location(world, ox + data.x[actor][t] / (double) ReplayData.UNIT,
                oy + data.y[actor][t] / (double) ReplayData.UNIT, oz + data.z[actor][t] / (double) ReplayData.UNIT);
        at.setYaw(ReplayData.degrees(data.yaw[actor][t]));
        at.setPitch(ReplayData.degrees(data.pitch[actor][t]));
        return at;
    }

    /** Each player's body as they were at the tick shown: sent, moved, changed or taken back. */
    private void bodies() {
        if (tick < 0 || !viewer.getWorld().equals(world)) {
            return;
        }
        Location eyes = viewer.getLocation();
        for (int a = 0; a < data.actors(); a++) {
            int flags = data.flags[a][tick];
            boolean in = (flags & ReplayData.HIDDEN) == 0;
            if (in) {
                double range = shown[a] ? HIDE_RANGE : SHOW_RANGE;
                in = at(a, tick).distanceSquared(eyes) < range * range;
            }
            if (in && !shown[a]) {
                spawn(a);
            } else if (!in && shown[a]) {
                shown[a] = false;
                send(new WrapperPlayServerDestroyEntities(entityIds[a]));
            } else if (in) {
                update(a);
            }
        }
    }

    private void spawn(final int a) {
        final UserProfile profile = profiles[a];
        send(new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER,
                new WrapperPlayServerPlayerInfo.PlayerData(null, profile,
                        com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL, 0)));
        Location at = at(a, tick);
        List<EntityData<?>> metadata = new ArrayList<>();
        metadata.add(new EntityData<>(SKIN_PARTS, EntityDataTypes.BYTE, ALL_PARTS));
        metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, metadataFlags(data.flags[a][tick])));
        send(new WrapperPlayServerSpawnPlayer(entityIds[a], profile.getUUID(),
                new com.github.retrooper.packetevents.protocol.world.Location(at.getX(), at.getY(), at.getZ(), at.getYaw(),
                        at.getPitch()), metadata));
        send(new WrapperPlayServerEntityHeadLook(entityIds[a], at.getYaw()));
        shown[a] = true;
        sent[a] = new int[] {data.x[a][tick], data.y[a][tick], data.z[a][tick], data.yaw[a][tick], data.pitch[a][tick],
            data.flags[a][tick]};
        for (int slot = 0; slot < ReplayData.SLOTS; slot++) {
            sentItems[a][slot] = null;
        }
        equipment(a);
        service.later(TAB_LIST_TICKS, () -> {
            if (viewer.isOnline()) {
                send(new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER,
                        new WrapperPlayServerPlayerInfo.PlayerData(null, profile,
                                com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL, 0)));
            }
        });
    }

    private void update(int a) {
        int[] last = sent[a];
        int x = data.x[a][tick];
        int y = data.y[a][tick];
        int z = data.z[a][tick];
        int yaw = data.yaw[a][tick];
        int pitch = data.pitch[a][tick];
        if (x != last[0] || y != last[1] || z != last[2] || yaw != last[3] || pitch != last[4]) {
            Location at = at(a, tick);
            send(new WrapperPlayServerEntityTeleport(entityIds[a],
                    new com.github.retrooper.packetevents.protocol.world.Location(at.getX(), at.getY(), at.getZ(), at.getYaw(),
                            at.getPitch()), false));
            if (yaw != last[3]) {
                send(new WrapperPlayServerEntityHeadLook(entityIds[a], at.getYaw()));
            }
            last[0] = x;
            last[1] = y;
            last[2] = z;
            last[3] = yaw;
            last[4] = pitch;
        }
        int flags = data.flags[a][tick];
        if (flags != last[5]) {
            last[5] = flags;
            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, metadataFlags(flags)));
            send(new WrapperPlayServerEntityMetadata(entityIds[a], metadata));
        }
        equipment(a);
    }

    /** The items in hand and worn, those that changed; an invisible player shows no armour. */
    private void equipment(int a) {
        boolean invisible = (data.flags[a][tick] & ReplayData.INVISIBLE) != 0;
        for (int slot = 0; slot < ReplayData.SLOTS; slot++) {
            ReplayData.Item item = invisible && slot != ReplayData.HAND ? ReplayData.Item.NONE : data.item(a, slot, tick);
            if (item.equals(sentItems[a][slot])) {
                continue;
            }
            sentItems[a][slot] = item;
            send(new WrapperPlayServerEntityEquipment(entityIds[a],
                    Collections.singletonList(new Equipment(slotOf(slot), itemOf(item)))));
        }
    }

    private static EquipmentSlot slotOf(int slot) {
        switch (slot) {
            case 1:
                return EquipmentSlot.BOOTS;
            case 2:
                return EquipmentSlot.LEGGINGS;
            case 3:
                return EquipmentSlot.CHEST_PLATE;
            case 4:
                return EquipmentSlot.HELMET;
            default:
                return EquipmentSlot.MAIN_HAND;
        }
    }

    @SuppressWarnings("deprecation") // ItemStack(int, int, short) and Material#getMaterial(int): the 1.8 ids
    private com.github.retrooper.packetevents.protocol.item.ItemStack itemOf(ReplayData.Item item) {
        com.github.retrooper.packetevents.protocol.item.ItemStack converted = items.get(item);
        if (converted == null) {
            Material material = item.id == 0 ? null : Material.getMaterial(item.id);
            ItemStack stack = material == null ? new ItemStack(Material.AIR) : new ItemStack(material, 1, (short) item.data);
            if (material != null) {
                if (item.glow) {
                    stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DURABILITY, 1);
                }
                ItemMeta meta = stack.getItemMeta();
                if (item.colour >= 0 && meta instanceof LeatherArmorMeta) {
                    ((LeatherArmorMeta) meta).setColor(Color.fromRGB(item.colour & 0xFFFFFF));
                    stack.setItemMeta(meta);
                }
            }
            converted = SpigotConversionUtil.fromBukkitItemStack(stack);
            items.put(item, converted);
        }
        return converted;
    }

    /** The flags of the replay as the first byte of a 1.8 entity's metadata. */
    static byte metadataFlags(int flags) {
        int value = 0;
        if ((flags & ReplayData.BURNING) != 0) {
            value |= 0x01;
        }
        if ((flags & ReplayData.SNEAK) != 0) {
            value |= 0x02;
        }
        if ((flags & ReplayData.SPRINT) != 0) {
            value |= 0x08;
        }
        if ((flags & ReplayData.USING) != 0) {
            value |= 0x10;
        }
        if ((flags & ReplayData.INVISIBLE) != 0) {
            value |= 0x20;
        }
        return (byte) value;
    }

    // --- Projectiles ----------------------------------------------------------------------------

    /** Every projectile there at the tick shown, sent again (after a jump). */
    private void things() {
        if (!thingIds.isEmpty()) {
            send(new WrapperPlayServerDestroyEntities(toArray(new ArrayList<>(thingIds.values()))));
            thingIds.clear();
        }
        for (ReplayData.Thing thing : data.thingsAt(tick).values()) {
            spawnThing(thing.id, thing.kind, thing.x, thing.y, thing.z, (thing.yaw & 0xFF) << 8 | (thing.pitch & 0xFF), thing.extra);
        }
    }

    private void spawnThing(int id, int kind, int x, int y, int z, int angles, int extra) {
        EntityType type = typeOf(kind);
        if (type == null || thingIds.containsKey(id)) {
            return;
        }
        int entity = NEXT_ID.getAndIncrement();
        thingIds.put(id, entity);
        double[] velocity = data.velocityOf(id);
        // with a data above 0 the client takes the velocity: its own flight between two positions sent
        int value = kind == ReplayData.FALLING_BLOCK || kind == ReplayData.POTION ? Math.max(1, extra) : 1;
        if (kind == ReplayData.TNT) {
            value = 0;
        }
        send(new WrapperPlayServerSpawnEntity(entity, Optional.<UUID>empty(), type,
                new Vector3d(ox + x / (double) ReplayData.UNIT, oy + y / (double) ReplayData.UNIT, oz + z / (double) ReplayData.UNIT),
                ReplayData.degrees((byte) angles), ReplayData.degrees((byte) (angles >> 8)), 0F, value,
                value > 0 ? Optional.of(new Vector3d(velocity[0], velocity[1], velocity[2])) : Optional.<Vector3d>empty()));
    }

    private void moveThing(int id, int x, int y, int z, int angles) {
        Integer entity = thingIds.get(id);
        if (entity == null) {
            return;
        }
        send(new WrapperPlayServerEntityTeleport(entity, new com.github.retrooper.packetevents.protocol.world.Location(
                ox + x / (double) ReplayData.UNIT, oy + y / (double) ReplayData.UNIT, oz + z / (double) ReplayData.UNIT,
                ReplayData.degrees((byte) (angles >> 8)), ReplayData.degrees((byte) angles)), false));
    }

    private void removeThing(int id) {
        Integer entity = thingIds.remove(id);
        if (entity != null) {
            send(new WrapperPlayServerDestroyEntities(entity));
        }
    }

    private static EntityType typeOf(int kind) {
        switch (kind) {
            case ReplayData.ARROW:
                return EntityTypes.ARROW;
            case ReplayData.SNOWBALL:
                return EntityTypes.SNOWBALL;
            case ReplayData.EGG:
                return EntityTypes.EGG;
            case ReplayData.FIREBALL:
                return EntityTypes.FIREBALL;
            case ReplayData.SMALL_FIREBALL:
                return EntityTypes.SMALL_FIREBALL;
            case ReplayData.ENDER_PEARL:
                return EntityTypes.ENDER_PEARL;
            case ReplayData.TNT:
                return EntityTypes.TNT;
            case ReplayData.FALLING_BLOCK:
                return EntityTypes.FALLING_BLOCK;
            case ReplayData.POTION:
                return EntityTypes.POTION;
            default:
                return null;
        }
    }

    // --- The hotbar -----------------------------------------------------------------------------

    /** A right or left click with an item of the hotbar. Returns true if it was one of the replay's. */
    boolean click(ItemStack item, boolean right) {
        String name = item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName() ? null
                : item.getItemMeta().getDisplayName();
        if (name == null) {
            return false;
        }
        // the menu's items start with a reset of the colour
        String reset = ChatColor.RESET.toString();
        while (name.startsWith(reset)) {
            name = name.substring(reset.length());
        }
        if (name.equals(PLAYERS)) {
            service.playersMenu(this);
        } else if (name.equals(BACK)) {
            seek(tick - JUMP_TICKS);
        } else if (name.equals(FORWARD)) {
            seek(tick + JUMP_TICKS);
        } else if (name.equals(PAUSE) || name.equals(PLAY)) {
            if (ended) {
                seek(0);
                paused = false;
            } else {
                paused = !paused;
            }
            hotbar();
        } else if (name.startsWith(SPEED)) {
            speed = Math.max(0, Math.min(SPEEDS.length - 1, speed + (right ? 1 : -1)));
            hotbar();
            sidebar();
        } else if (name.equals(LEAVE)) {
            service.leave(viewer);
        } else {
            return false;
        }
        return true;
    }

    /** The viewer goes to this player of the replay: a few blocks behind them, looking at them. */
    void goTo(int actor) {
        int t = Math.max(0, tick);
        if ((data.flags[actor][t] & ReplayData.HIDDEN) != 0) {
            viewer.sendMessage(ChatColor.RED + data.info.actors.get(actor).name + " is not in the game at this moment.");
            return;
        }
        Location target = at(actor, t);
        Location behind = target.clone();
        double yaw = Math.toRadians(target.getYaw());
        behind.add(Math.sin(yaw) * 3, 1.5, -Math.cos(yaw) * 3);
        behind.setYaw(target.getYaw());
        behind.setPitch(20);
        viewer.teleport(behind);
        viewer.setFlying(true);
    }

    /** The items of the replay in the hotbar, the rest of the inventory empty. */
    void hotbar() {
        PlayerInventory inventory = viewer.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItem(SLOT_PLAYERS, ClickMenu.item(Material.COMPASS, PLAYERS, "Go to a player of the game."));
        inventory.setItem(SLOT_BACK, ClickMenu.item(Material.ARROW, BACK, "Back in the game."));
        boolean playing = !paused;
        inventory.setItem(SLOT_PAUSE, ClickMenu.item(Material.INK_SACK, playing ? 10 : 8, playing ? PAUSE : PLAY,
                Collections.singletonList(ended ? "From the start again." : playing ? "Stop the replay here." : "Go on.")));
        inventory.setItem(SLOT_FORWARD, ClickMenu.item(Material.ARROW, FORWARD, "On in the game."));
        inventory.setItem(SLOT_SPEED, ClickMenu.item(Material.WATCH, 0, SPEED + ": " + ChatColor.WHITE + speedName(),
                java.util.Arrays.asList("Right click: faster.", "Left click: slower.")));
        inventory.setItem(SLOT_LEAVE, ClickMenu.item(Material.BED, LEAVE, "Back to the lobby."));
        viewer.updateInventory();
    }

    private String speedName() {
        double value = SPEEDS[speed];
        return "x" + (value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value));
    }

    /** The sidebar: the game, where the replay is, its speed, and its players with their health. */
    private void sidebar() {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.WHITE + "Mode: " + ChatColor.GREEN + cut(data.info.modeName.isEmpty() ? data.info.modeId : data.info.modeName));
        if (!data.info.mapName.isEmpty()) {
            lines.add(ChatColor.WHITE + "Map: " + ChatColor.GREEN + cut(data.info.mapName));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + (paused ? "❚❚ " : "▶ ") + ReplayData.clock(Math.max(0, tick)) + ChatColor.GRAY
                + " / " + ReplayData.clock(data.ticks));
        lines.add(ChatColor.WHITE + "Speed: " + ChatColor.AQUA + speedName());
        lines.add(" ");
        // the players, as many as the sidebar has room for (12 lines, 3 of them after the players)
        int room = LobbyScoreboard.MAX_GAME_LINES - lines.size() - 2;
        int listed = data.actors() <= room ? data.actors() : room - 1;
        int t = Math.max(0, tick);
        for (int a = 0; a < listed; a++) {
            ReplayData.Actor actor = data.info.actors.get(a);
            boolean in = (data.flags[a][t] & ReplayData.HIDDEN) == 0;
            lines.add(ChatColor.COLOR_CHAR + "" + actor.colour + cut(actor.name) + " "
                    + (in ? ChatColor.RED + "❤" + ChatColor.WHITE + data.health[a][t] : ChatColor.GRAY + "-"));
        }
        if (data.actors() > listed) {
            lines.add(ChatColor.GRAY + "+" + (data.actors() - listed) + " more");
        }
        lines.add("  ");
        lines.add(ChatColor.GRAY + "#" + data.info.id + " " + new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT)
                .format(new Date(data.info.startedAt)));
        service.sidebar(viewer, lines);
    }

    private static String cut(String text) {
        return text.length() > 16 ? text.substring(0, 16) : text;
    }

    // --- Packets --------------------------------------------------------------------------------

    private void send(PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }

    private static int[] toArray(List<Integer> ids) {
        int[] array = new int[ids.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = ids.get(i);
        }
        return array;
    }
}
