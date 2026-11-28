package gg.rago.lobby;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * The body of RagoBot on the server: a player of the server with no client behind it. It logs in
 * as a client's login does ({@code PlayerList.a(NetworkManager, EntityPlayer)}: the spawn location
 * event, the join event, the tab list, the other players see it), on a connection whose channel
 * (a netty EmbeddedChannel, which PacketEvents leaves alone) drops every packet sent to it, but
 * reads two: the velocity of its own entity (a knockback, as its client would get it) and a
 * teleport (which stops it, as a client stops).
 *
 * <p>Each tick, what a client sends, in the order a client sends it: the slot held, sprint, the
 * clicks (the swing of the arm as its packet is handled, then the hit), eating, then the keys and
 * where it looks; the server's own player physics moves it ({@code EntityPlayer.l()}, as for a
 * move packet: the jump, the friction, the gravity, the collisions), the fall damage as for a
 * move, the chunks around it, and {@link PlayerMoveEvent} as {@code PlayerConnection} fires it
 * (the games stop it during a countdown, kill it under their fall line). Craftbukkit 1.8.8 has no
 * API for any of this: it is reached by reflection, as the rest of the plugin does for NMS. Main
 * thread, but for the packets read, which come from any thread.
 */
final class BotBody {

    private static final double VELOCITY = 0;
    private static final double TELEPORT = 1;
    /** PlayerConnection fires a move event after this much movement (squared) or turn (degrees). */
    private static final double MOVE_SQUARED = 1.0 / 256;
    private static final float TURN = 10f;
    /** The protocol of a 1.8 client. */
    private static final int PROTOCOL_1_8 = 47;

    private static Nms nms;

    final Player player;
    private final int entityId;
    /** The velocities and teleports sent to it, in their order: {kind, x, y, z}. */
    private final Queue<double[]> arrived;
    /** Where it was at the last move event, null: from where it stands. */
    private Location lastMove;
    private int deadTicks;
    /** The block it digs {x, y, z, side}, null: none; and the damage done to it so far (1: broken). */
    private int[] digging;
    private double digDamage;
    /** A dig just finished: the block, and what it was, to see next tick if it was broken. */
    private int[] checking;
    private Material checkedType;
    /** The blocks whose dig the game refused (the map's own): never tried again. */
    private final Set<Long> refused = new java.util.HashSet<>();
    private static final int MAX_REFUSED = 4096;

    private BotBody(Player player, int entityId, Queue<double[]> arrived) {
        this.player = player;
        this.entityId = entityId;
        this.arrived = arrived;
    }

    /** The reflection, once; a failure tells what this server lacks. */
    static synchronized Nms nms() throws ReflectiveOperationException {
        if (nms == null) {
            nms = new Nms();
        }
        return nms;
    }

    /**
     * A new player of this name and id, with this skin (null: none), logged in now: the join event
     * runs before this returns (the lobby puts it at its spawn).
     */
    static BotBody login(UUID id, String name, Skin skin, Location at) throws ReflectiveOperationException {
        Nms n = nms();
        Object minecraft = n.minecraftServer.invoke(Bukkit.getServer());
        Object world = n.worldHandle.invoke(at.getWorld());
        Object profile = n.newProfile.newInstance(id, name);
        if (skin != null) {
            Object properties = n.profileProperties.invoke(profile);
            n.put.invoke(properties, "textures", n.newProperty.newInstance("textures", skin.value(), skin.signature()));
        }
        Object interact = n.newInteractManager.newInstance(world);
        Object entity = n.newEntityPlayer.newInstance(minecraft, world, profile, interact);
        int entityId = (Integer) n.getId.invoke(entity);
        final Queue<double[]> arrived = new ConcurrentLinkedQueue<>();
        final int self = entityId;
        Object channel = n.channel(packet -> read(n, packet, self, arrived));
        Object network = n.newNetworkManager.newInstance(n.serverbound);
        n.channelField.set(network, channel);
        n.addressField.set(network, new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
        if (n.protocolVersion != null) {
            n.protocolVersion.setInt(network, PROTOCOL_1_8);
        }
        n.protocol.invoke(network, n.play);
        Object players = n.playerList.invoke(Bukkit.getServer());
        n.login.invoke(players, network, entity);
        Player player = (Player) n.bukkitEntity.invoke(entity);
        return new BotBody(player, entityId, arrived);
    }

    /** A packet sent to the bot: its own velocity, or a teleport, kept for its next tick. */
    private static void read(Nms n, Object packet, int self, Queue<double[]> arrived) {
        try {
            Class<?> type = packet.getClass();
            if (type == n.velocityPacket) {
                if (n.velocityId.getInt(packet) == self) {
                    arrived.add(new double[] {VELOCITY, n.velocityX.getInt(packet) / 8000.0,
                            n.velocityY.getInt(packet) / 8000.0, n.velocityZ.getInt(packet) / 8000.0});
                }
            } else if (type == n.positionPacket) {
                Set<?> relative = (Set<?>) n.positionFlags.get(packet);
                arrived.add(new double[] {TELEPORT, has(relative, "X") ? 0 : 1, has(relative, "Y") ? 0 : 1,
                        has(relative, "Z") ? 0 : 1});
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // a packet of another shape: as a client that missed it
        }
    }

    private static boolean has(Set<?> flags, String name) {
        if (flags != null) {
            for (Object flag : flags) {
                if (flag instanceof Enum && ((Enum<?>) flag).name().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- Each tick --------------------------------------------------------------------------------

    /**
     * Dead: respawned (as its client clicks Respawn) a tick after the death, so that the games see
     * the death first. True while it is dead.
     */
    boolean respawnIfDead() {
        if (!player.isDead()) {
            deadTicks = 0;
            return false;
        }
        if (++deadTicks >= 2) {
            player.spigot().respawn();
            lastMove = null;
        }
        return true;
    }

    /**
     * What reached it since the last tick, in order: a knockback sets its speed (as its client
     * does), a teleport stops it. True if a knockback came.
     */
    boolean receive() throws ReflectiveOperationException {
        Object entity = handle();
        boolean knocked = false;
        for (double[] packet; (packet = arrived.poll()) != null;) {
            if (packet[0] == VELOCITY) {
                nms.motX.setDouble(entity, packet[1]);
                nms.motY.setDouble(entity, packet[2]);
                nms.motZ.setDouble(entity, packet[3]);
                knocked = true;
            } else {
                // its client confirms the teleport at once (else the server takes none of its blocks)
                if (nms.checkMovement != null) {
                    nms.checkMovement.setBoolean(nms.connection.get(entity), true);
                }
                if (packet[1] > 0) {
                    nms.motX.setDouble(entity, 0);
                }
                if (packet[2] > 0) {
                    nms.motY.setDouble(entity, 0);
                }
                if (packet[3] > 0) {
                    nms.motZ.setDouble(entity, 0);
                }
                lastMove = null;
            }
        }
        return knocked;
    }

    /** True while it eats (an item in use). */
    boolean eating() throws ReflectiveOperationException {
        return (Boolean) nms.usingItem.invoke(handle());
    }

    /**
     * Presses these keys for one tick (target: the player it fights, or null): the slot held,
     * sprint, where it looks, its clicks on the other. Then {@link #blocks}, then {@link #move}.
     */
    void act(BotBrain.Controls controls, Player target) throws ReflectiveOperationException {
        Nms n = nms;
        Object entity = handle();
        PlayerInventory inventory = player.getInventory();
        if (controls.slot >= 0 && controls.slot <= 8 && controls.slot != inventory.getHeldItemSlot()) {
            PlayerItemHeldEvent held = new PlayerItemHeldEvent(player, inventory.getHeldItemSlot(), controls.slot);
            Bukkit.getPluginManager().callEvent(held);
            if (!held.isCancelled()) {
                inventory.setHeldItemSlot(controls.slot);
            }
        }
        if (controls.sneak != player.isSneaking()) {
            // as a client's sneak key: the server holds a sneaking player at the edge of a block
            PlayerToggleSneakEvent sneak = new PlayerToggleSneakEvent(player, controls.sneak);
            Bukkit.getPluginManager().callEvent(sneak);
            if (!sneak.isCancelled()) {
                player.setSneaking(controls.sneak);
            }
        }
        if (controls.sprint != player.isSprinting()) {
            PlayerToggleSprintEvent sprint = new PlayerToggleSprintEvent(player, controls.sprint);
            Bukkit.getPluginManager().callEvent(sprint);
            if (!sprint.isCancelled()) {
                player.setSprinting(controls.sprint);
            }
        }
        n.yaw.setFloat(entity, controls.yaw);
        n.pitch.setFloat(entity, controls.pitch);
        n.headYaw.setFloat(entity, controls.yaw);
        Object connection = n.connection.get(entity);
        for (int i = 0; i < controls.swings && player.isOnline() && !player.isDead(); i++) {
            n.swing.invoke(connection, n.newSwing.newInstance());
            if (i == 0 && controls.hit && target != null && target.isOnline() && !target.isDead()
                    && target.getWorld().equals(player.getWorld())) {
                n.attack.invoke(handle(), n.entityHandle.invoke(target));
            }
        }
    }

    /**
     * Its clicks on the blocks this tick, as a client sends them after its clicks on the other: a
     * right click on the side of a block with the block held (the server places it as for a
     * player: its events, the rules of the game, the anti full box), the left button held on a
     * block (a dig started, its damage added a tick at a time as the client adds it, the dig
     * finished once it reaches 1, as long as a player's break takes; stopped if it looks away). A
     * dig the game refused (the block still there) is not tried again ({@link #refused}).
     */
    void blocks(BotBrain.Controls controls) throws ReflectiveOperationException {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        BlockNms b = blockNms();
        Nms n = nms;
        Object entity = handle();
        Object connection = n.connection.get(entity);
        if (checking != null) {
            if (player.getWorld().getBlockAt(checking[0], checking[1], checking[2]).getType() == checkedType) {
                refuse(checking);
            }
            checking = null;
        }
        if (controls.place != null) {
            int[] at = controls.place;
            float[] cursor = controls.cursor == null ? new float[] {0.5f, 0.5f, 0.5f} : controls.cursor;
            Object item = n.itemInHand.invoke(n.inventory.get(entity));
            Object packet = b.newPlace.newInstance(b.newPosition.newInstance(at[0], at[1], at[2]), at[3], item,
                    cursor[0], cursor[1], cursor[2]);
            b.placeTime.setLong(packet, System.currentTimeMillis());
            b.place.invoke(connection, packet);
            n.swing.invoke(connection, n.newSwing.newInstance());
        }
        int[] dig = controls.dig;
        if (dig == null) {
            if (digging != null) {
                b.dig(connection, b.abort, digging);
                digging = null;
            }
            return;
        }
        if (digging == null || digging[0] != dig[0] || digging[1] != dig[1] || digging[2] != dig[2]) {
            if (digging != null) {
                b.dig(connection, b.abort, digging);
            }
            digging = dig.clone();
            digDamage = 0;
            b.dig(connection, b.start, dig);
            n.swing.invoke(connection, n.newSwing.newInstance());
            digDamage = b.damage(entity, dig);
            if (digDamage >= 1) {
                // broken at once, as the server breaks it with the start
                finishDig(dig);
            }
            return;
        }
        n.swing.invoke(connection, n.newSwing.newInstance());
        digDamage += b.damage(entity, dig);
        if (digDamage >= 1) {
            b.dig(connection, b.stop, dig);
            finishDig(dig);
        }
    }

    /** The dig ended: next tick, the block still there was refused. */
    private void finishDig(int[] dig) {
        digging = null;
        digDamage = 0;
        checking = dig.clone();
        checkedType = player.getWorld().getBlockAt(dig[0], dig[1], dig[2]).getType();
    }

    private void refuse(int[] block) {
        if (refused.size() >= MAX_REFUSED) {
            refused.clear();
        }
        refused.add(key(block[0], block[1], block[2]));
    }

    /** True if the game refused it the dig of this block before. */
    boolean refused(int x, int y, int z) {
        return refused.contains(key(x, y, z));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) y & 0xFFF) << 26 | (long) z & 0x3FFFFFF;
    }

    /** Then the keys: eating, the way it walks and jumps; the server moves it as it moves a player. */
    void move(BotBrain.Controls controls) throws ReflectiveOperationException {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        Nms n = nms;
        Object entity = handle();
        if (controls.eat && !eating()) {
            eat(entity);
        }
        n.strafe.setFloat(entity, (float) controls.strafe);
        n.forward.setFloat(entity, (float) controls.forward);
        n.jumping.setBoolean(entity, controls.jump);

        if (lastMove == null) {
            lastMove = player.getLocation();
        }
        double y = n.locY.getDouble(entity);
        n.livingTick.invoke(entity);
        n.fall.invoke(entity, n.locY.getDouble(entity) - y, n.onGround.getBoolean(entity));
        n.resetIdle.invoke(entity);
        n.movePlayer.invoke(n.playerList.invoke(Bukkit.getServer()), entity);
        moved();
    }

    /** As a client's right click with the item held: a golden apple starts to be eaten. */
    private void eat(Object entity) throws ReflectiveOperationException {
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() != Material.GOLDEN_APPLE) {
            return;
        }
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, hand, null, BlockFace.SELF);
        Bukkit.getPluginManager().callEvent(click);
        if (click.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Object item = nms.itemInHand.invoke(nms.inventory.get(entity));
        if (item != null) {
            nms.useItem.invoke(nms.interactManager.get(entity), entity, nms.entityWorld.get(entity), item);
        }
    }

    /**
     * The move event, as PlayerConnection fires it: from where the last one left it, once it moved
     * or turned enough; cancelled, it goes back; another destination, it goes there.
     */
    private void moved() {
        Location to = player.getLocation();
        Location from = lastMove;
        if (from == null || !from.getWorld().equals(to.getWorld())) {
            lastMove = to;
            return;
        }
        float turned = Math.abs(to.getYaw() - from.getYaw()) + Math.abs(to.getPitch() - from.getPitch());
        if (from.distanceSquared(to) <= MOVE_SQUARED && turned <= TURN) {
            return;
        }
        PlayerMoveEvent event = new PlayerMoveEvent(player, from.clone(), to.clone());
        Bukkit.getPluginManager().callEvent(event);
        if (!player.isOnline()) {
            return;
        }
        if (event.isCancelled()) {
            player.teleport(from);
            lastMove = from;
            return;
        }
        Location wanted = event.getTo();
        if (!same(wanted, to)) {
            player.teleport(wanted);
        }
        lastMove = player.getLocation();
    }

    private static boolean same(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.getX() == b.getX() && a.getY() == b.getY() && a.getZ() == b.getZ()
                && a.getYaw() == b.getYaw() && a.getPitch() == b.getPitch();
    }

    /** Its entity now (a respawn makes a new one). */
    private Object handle() throws ReflectiveOperationException {
        return nms.playerHandle.invoke(player);
    }

    int entityId() {
        return entityId;
    }

    private static BlockNms blockNms;

    /** The reflection of its clicks on blocks, once; a failure tells what this server lacks. */
    static synchronized BlockNms blockNms() throws ReflectiveOperationException {
        if (blockNms == null) {
            blockNms = new BlockNms(nms());
        }
        return blockNms;
    }

    /** The packets of a client's clicks on blocks, and the damage of a dig, from the server's classes. */
    static final class BlockNms {
        final Constructor<?> newPosition;
        final Constructor<?> newPlace;
        final Field placeTime;
        final Method place;
        private final Constructor<?> newDig;
        private final Field digPosition;
        private final Field digSide;
        private final Field digType;
        private final Method digHandler;
        final Object start;
        final Object abort;
        final Object stop;
        private final Object[] sides = new Object[6];
        private final Method getType;
        private final Method getBlock;
        private final Method getDamage;
        private final Field world;

        BlockNms(Nms n) throws ReflectiveOperationException {
            String craftRoot = Bukkit.getServer().getClass().getPackage().getName();
            String root = "net.minecraft.server." + craftRoot.substring(craftRoot.lastIndexOf('.') + 1) + ".";
            Class<?> position = Class.forName(root + "BlockPosition");
            Class<?> item = Class.forName(root + "ItemStack");
            Class<?> placePacket = Class.forName(root + "PacketPlayInBlockPlace");
            Class<?> digPacket = Class.forName(root + "PacketPlayInBlockDig");
            Class<?> digTypes = Class.forName(root + "PacketPlayInBlockDig$EnumPlayerDigType");
            Class<?> direction = Class.forName(root + "EnumDirection");
            Class<?> playerConnection = Class.forName(root + "PlayerConnection");
            Class<?> worldClass = Class.forName(root + "World");
            Class<?> human = Class.forName(root + "EntityHuman");
            newPosition = position.getConstructor(int.class, int.class, int.class);
            newPlace = placePacket.getConstructor(position, int.class, item, float.class, float.class, float.class);
            placeTime = placePacket.getField("timestamp");
            place = playerConnection.getMethod("a", placePacket);
            newDig = digPacket.getConstructor();
            digPosition = Nms.fieldOfType(digPacket, position);
            digSide = Nms.fieldOfType(digPacket, direction);
            digType = Nms.fieldOfType(digPacket, digTypes);
            digHandler = playerConnection.getMethod("a", digPacket);
            start = Nms.enumValue(digTypes, "START_DESTROY_BLOCK");
            abort = Nms.enumValue(digTypes, "ABORT_DESTROY_BLOCK");
            stop = Nms.enumValue(digTypes, "STOP_DESTROY_BLOCK");
            String[] names = {"DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"};
            for (int i = 0; i < names.length; i++) {
                sides[i] = Nms.enumValue(direction, names[i]);
            }
            getType = worldClass.getMethod("getType", position);
            getBlock = getType.getReturnType().getMethod("getBlock");
            getDamage = Class.forName(root + "Block").getMethod("getDamage", human, worldClass, position);
            world = n.entityWorld;
        }

        /** A dig packet of this kind for the block {x, y, z, side}, handled as the server handles a client's. */
        void dig(Object connection, Object kind, int[] block) throws ReflectiveOperationException {
            Object packet = newDig.newInstance();
            digPosition.set(packet, newPosition.newInstance(block[0], block[1], block[2]));
            digSide.set(packet, sides[Math.max(0, Math.min(5, block[3]))]);
            digType.set(packet, kind);
            digHandler.invoke(connection, packet);
        }

        /** The damage a tick of digging does to the block {x, y, z}, with the item held: 1 breaks it. */
        double damage(Object entity, int[] block) throws ReflectiveOperationException {
            Object level = world.get(entity);
            Object at = newPosition.newInstance(block[0], block[1], block[2]);
            Object type = getBlock.invoke(getType.invoke(level, at));
            return ((Number) getDamage.invoke(type, entity, level, at)).doubleValue();
        }
    }

    // --- The reflection -------------------------------------------------------------------------

    /** The classes, fields and methods of the server it uses. */
    static final class Nms {
        final Method minecraftServer;
        final Method playerList;
        final Method worldHandle;
        final Method playerHandle;
        final Method entityHandle;
        final Method bukkitEntity;
        final Constructor<?> newProfile;
        final Method profileProperties;
        final Method put;
        final Constructor<?> newProperty;
        final Constructor<?> newInteractManager;
        final Constructor<?> newEntityPlayer;
        final Method getId;
        final Constructor<?> newNetworkManager;
        final Object serverbound;
        final Object play;
        final Field channelField;
        final Field addressField;
        /** PandaSpigot's version of the client's protocol, null elsewhere. */
        final Field protocolVersion;
        final Method protocol;
        final Method login;
        final Method movePlayer;
        final Method livingTick;
        final Method fall;
        final Method resetIdle;
        final Method attack;
        final Method swing;
        final Constructor<?> newSwing;
        final Method usingItem;
        final Method useItem;
        final Method itemInHand;
        final Field inventory;
        final Field interactManager;
        final Field entityWorld;
        final Field connection;
        /** PlayerConnection.checkMovement: false from a teleport until the client confirms it; null if none. */
        final Field checkMovement;
        final Field motX;
        final Field motY;
        final Field motZ;
        final Field locY;
        final Field onGround;
        final Field yaw;
        final Field pitch;
        final Field headYaw;
        final Field strafe;
        final Field forward;
        final Field jumping;
        final Class<?> velocityPacket;
        final Field velocityId;
        final Field velocityX;
        final Field velocityY;
        final Field velocityZ;
        final Class<?> positionPacket;
        final Field positionFlags;
        private final Constructor<?> newEmbeddedChannel;
        private final Class<?> channelHandler;
        private final Class<?> outboundHandler;
        private final Method release;
        /** ChannelPromise.trySuccess(), on the public interface (some promises are not public classes). */
        private final Method trySuccess;
        private final Class<?> context;

        Nms() throws ReflectiveOperationException {
            String craftRoot = Bukkit.getServer().getClass().getPackage().getName();
            String version = craftRoot.substring(craftRoot.lastIndexOf('.') + 1);
            String root = "net.minecraft.server." + version + ".";
            Class<?> craftServer = Bukkit.getServer().getClass();
            minecraftServer = craftServer.getMethod("getServer");
            playerList = craftServer.getMethod("getHandle");
            worldHandle = Class.forName(craftRoot + ".CraftWorld").getMethod("getHandle");
            playerHandle = Class.forName(craftRoot + ".entity.CraftPlayer").getMethod("getHandle");
            entityHandle = Class.forName(craftRoot + ".entity.CraftEntity").getMethod("getHandle");

            Class<?> profile = Class.forName("com.mojang.authlib.GameProfile");
            newProfile = profile.getConstructor(UUID.class, String.class);
            profileProperties = profile.getMethod("getProperties");
            put = profileProperties.getReturnType().getMethod("put", Object.class, Object.class);
            newProperty = Class.forName("com.mojang.authlib.properties.Property")
                    .getConstructor(String.class, String.class, String.class);

            Class<?> minecraft = Class.forName(root + "MinecraftServer");
            Class<?> world = Class.forName(root + "World");
            Class<?> worldServer = Class.forName(root + "WorldServer");
            Class<?> entity = Class.forName(root + "Entity");
            Class<?> living = Class.forName(root + "EntityLiving");
            Class<?> human = Class.forName(root + "EntityHuman");
            Class<?> entityPlayer = Class.forName(root + "EntityPlayer");
            Class<?> interact = Class.forName(root + "PlayerInteractManager");
            Class<?> network = Class.forName(root + "NetworkManager");
            Class<?> list = Class.forName(root + "PlayerList");
            Class<?> playerConnection = Class.forName(root + "PlayerConnection");
            Class<?> item = Class.forName(root + "ItemStack");
            newInteractManager = interact.getConstructor(world);
            newEntityPlayer = entityPlayer.getConstructor(minecraft, worldServer, profile, interact);
            bukkitEntity = entityPlayer.getMethod("getBukkitEntity");
            getId = entity.getMethod("getId");

            Class<?> direction = Class.forName(root + "EnumProtocolDirection");
            newNetworkManager = network.getConstructor(direction);
            serverbound = enumValue(direction, "SERVERBOUND");
            Class<?> protocols = Class.forName(root + "EnumProtocol");
            play = enumValue(protocols, "PLAY");
            channelField = network.getField("channel");
            Field address = network.getField("l");
            addressField = java.net.SocketAddress.class.isAssignableFrom(address.getType()) ? address
                    : fieldOfType(network, java.net.SocketAddress.class);
            Field clientProtocol = null;
            try {
                // PandaSpigot's Player.getProtocolVersion(): a 1.8 client's, 47
                clientProtocol = network.getField("protocolVersion");
            } catch (NoSuchFieldException e) {
                // another server: none
            }
            protocolVersion = clientProtocol != null && clientProtocol.getType() == int.class ? clientProtocol : null;
            protocol = network.getMethod("a", protocols);
            login = list.getMethod("a", network, entityPlayer);
            movePlayer = list.getMethod("d", entityPlayer);

            livingTick = entityPlayer.getMethod("l");
            fall = entityPlayer.getMethod("a", double.class, boolean.class);
            resetIdle = entityPlayer.getMethod("resetIdleTimer");
            attack = human.getMethod("attack", entity);
            Class<?> swingPacket = Class.forName(root + "PacketPlayInArmAnimation");
            swing = playerConnection.getMethod("a", swingPacket);
            newSwing = swingPacket.getConstructor();
            usingItem = human.getMethod("bS");
            useItem = interact.getMethod("useItem", human, world, item);
            inventory = human.getField("inventory");
            itemInHand = inventory.getType().getMethod("getItemInHand");
            interactManager = entityPlayer.getField("playerInteractManager");
            entityWorld = entity.getField("world");
            connection = entityPlayer.getField("playerConnection");
            Field confirmed = null;
            try {
                confirmed = playerConnection.getDeclaredField("checkMovement");
                confirmed.setAccessible(true);
            } catch (NoSuchFieldException e) {
                // another server: nothing to confirm
            }
            checkMovement = confirmed != null && confirmed.getType() == boolean.class ? confirmed : null;

            motX = entity.getField("motX");
            motY = entity.getField("motY");
            motZ = entity.getField("motZ");
            locY = entity.getField("locY");
            onGround = entity.getField("onGround");
            yaw = entity.getField("yaw");
            pitch = entity.getField("pitch");
            headYaw = living.getField("aK");
            strafe = living.getField("aZ");
            forward = living.getField("ba");
            jumping = declared(living, "aY");

            velocityPacket = Class.forName(root + "PacketPlayOutEntityVelocity");
            velocityId = declared(velocityPacket, "a");
            velocityX = declared(velocityPacket, "b");
            velocityY = declared(velocityPacket, "c");
            velocityZ = declared(velocityPacket, "d");
            positionPacket = Class.forName(root + "PacketPlayOutPosition");
            positionFlags = fieldOfType(positionPacket, Set.class);

            Class<?> embedded = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
            channelHandler = Class.forName("io.netty.channel.ChannelHandler");
            outboundHandler = Class.forName("io.netty.channel.ChannelOutboundHandler");
            newEmbeddedChannel = embedded.getConstructor(Array.newInstance(channelHandler, 0).getClass());
            release = Class.forName("io.netty.util.ReferenceCountUtil").getMethod("release", Object.class);
            trySuccess = Class.forName("io.netty.channel.ChannelPromise").getMethod("trySuccess");
            context = Class.forName("io.netty.channel.ChannelHandlerContext");
        }

        /**
         * A channel of its own, open and active, whose last handler takes every write: it reads the
         * packet, then drops it, the write done. Nothing reaches the wire, from any thread.
         */
        Object channel(Consumer<Object> reader) throws ReflectiveOperationException {
            InvocationHandler drops = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "write":
                        try {
                            reader.accept(args[1]);
                        } finally {
                            release.invoke(null, args[1]);
                            trySuccess.invoke(args[2]);
                        }
                        return null;
                    case "flush":
                    case "handlerAdded":
                    case "handlerRemoved":
                    case "exceptionCaught":
                        return null;
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "RagoBot connection";
                    default:
                        // bind, connect, disconnect, close, deregister, read: on along the pipeline
                        Class<?>[] types = method.getParameterTypes();
                        Class<?>[] rest = new Class<?>[types.length - 1];
                        System.arraycopy(types, 1, rest, 0, rest.length);
                        Object[] values = new Object[rest.length];
                        System.arraycopy(args, 1, values, 0, values.length);
                        Method next = context.getMethod(method.getName(), rest);
                        next.invoke(args[0], values);
                        return null;
                }
            };
            Object handler = Proxy.newProxyInstance(outboundHandler.getClassLoader(), new Class<?>[] {outboundHandler}, drops);
            Object handlers = Array.newInstance(channelHandler, 1);
            Array.set(handlers, 0, handler);
            return newEmbeddedChannel.newInstance(handlers);
        }

        static Object enumValue(Class<?> type, String name) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return constant;
                }
            }
            throw new IllegalStateException(type.getName() + " has no " + name);
        }

        private static Field declared(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        static Field fieldOfType(Class<?> owner, Class<?> type) throws NoSuchFieldException {
            for (Field field : owner.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType()) && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            throw new NoSuchFieldException(owner.getName() + " has no field of " + type.getName());
        }
    }
}
