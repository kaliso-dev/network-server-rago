package gg.rago.lobby;

import gg.rago.lobby.api.FireballAim;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.Random;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

/**
 * The toys of the lobby hotbar. The fireball flies where the player looks, as in Bed Wars, and its
 * blast pushes the players around it as on Hypixel (a fireball under one's own feet is a fireball
 * jump); it hurts no one and breaks nothing. The teleport stick takes the player onto the block they
 * look at, {@link #REACH} blocks away at most, never through a wall: the first block in the way is
 * the one they land on (or next to, when there is no room on it); looking at the air, {@link #REACH}
 * blocks ahead, in the air. Once every two seconds.
 *
 * <p>The teleport stick is one of the gadgets of the hotbar's fourth slot, as on Hypixel; a left
 * click on it opens the others: the grappling hook (flies to the block hooked), the paintball gun
 * (colours the blocks it hits for each player's eyes only, for a few seconds), the firework
 * launcher, the party popper (cookies and sweets nobody can pick up, and music) and the leap
 * feather. Main thread only.
 */
final class LobbyGadgets implements Listener {

    /** How far the teleport stick reaches, in blocks. */
    static final int REACH = 20;
    /** Half a second between two fireballs, as on Hypixel. */
    static final long FIREBALL_COOLDOWN_MS = 500L;
    static final long TELEPORT_COOLDOWN_MS = 2000L;
    /** From the eyes to the feet of a standing player. */
    static final double EYE_HEIGHT = 1.62;
    /** The blast of Hypixel's fireball (BedWars1058's values): who it reaches, how far it pushes. */
    static final double RADIUS = 3.0;
    static final double HORIZONTAL = 1.0;
    static final double VERTICAL = 0.65;
    /** Between two paintballs: the gun never runs out. */
    static final long PAINTBALL_COOLDOWN_MS = 300L;
    static final long FIREWORK_COOLDOWN_MS = 500L;
    static final long LEAP_COOLDOWN_MS = 800L;
    /** How far the grappling hook reaches. */
    static final int HOOK_REACH = 32;
    /** How long the paint stays, in ticks. */
    private static final long PAINT_TICKS = 100L;
    /** The colours of the paint: the bright ones of the stained clay. */
    private static final byte[] PAINT = {1, 2, 3, 4, 5, 6, 9, 10, 11, 14};
    private static final Material[] SWEETS = {Material.COOKIE, Material.CAKE, Material.GOLD_NUGGET, Material.EMERALD,
            Material.DIAMOND, Material.SUGAR, Material.APPLE, Material.MELON};
    private static final Color[] COLOURS = {Color.RED, Color.ORANGE, Color.YELLOW, Color.LIME, Color.AQUA, Color.BLUE,
            Color.FUCHSIA, Color.PURPLE, Color.WHITE};
    /** Between two throws of the slime blocks. */
    static final long SLIME_COOLDOWN_MS = 600L;
    /** A fireball that hits nothing goes after five seconds. */
    private static final long FIREBALL_LIFE_TICKS = 100L;

    private final Plugin plugin;
    private final Map<UUID, Long> lastFireball = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private final Map<UUID, Long> lastSlime = new HashMap<>();
    /** When each player last used each gadget (by name). */
    private final Map<String, Long> lastGadget = new HashMap<>();
    /** The paintballs in flight, by entity id. */
    private final Set<UUID> paintballs = new HashSet<>();
    /** The sweets of the party poppers on the ground. */
    private final Set<Item> sweets = new HashSet<>();
    private final Random random = new Random();
    /** The fireballs of the lobby in flight, by entity id. */
    private final Set<UUID> fireballs = new HashSet<>();

    LobbyGadgets(Plugin plugin) {
        this.plugin = plugin;
    }

    void forget(UUID player) {
        lastFireball.remove(player);
        lastTeleport.remove(player);
        lastSlime.remove(player);
        lastGadget.keySet().removeIf(key -> key.startsWith(player + ":"));
    }

    /** The gadgets of the hotbar's fourth slot, in the order of their menu: those of PREMIUM last. */
    static final HotbarItem[] GADGETS = {HotbarItem.TELEPORT, HotbarItem.GRAPPLE, HotbarItem.PAINTBALL, HotbarItem.FIREWORKS,
        HotbarItem.POPPER, HotbarItem.LEAP, HotbarItem.BAT_BLASTER, HotbarItem.EXPLOSIVE_SHEEP, HotbarItem.DISCO_BALL};
    /** The permission of the PREMIUM gadgets. */
    static final String PREMIUM_PERMISSION = "rago.perk.gadgets";
    static final long BATS_COOLDOWN_MS = 5_000L;
    static final long SHEEP_COOLDOWN_MS = 8_000L;
    static final long DISCO_COOLDOWN_MS = 30_000L;
    /** How long a disco lasts, in ticks. */
    static final int DISCO_TICKS = 200;

    /** True for a gadget of PREMIUM only. */
    static boolean premiumOnly(HotbarItem gadget) {
        return gadget == HotbarItem.BAT_BLASTER || gadget == HotbarItem.EXPLOSIVE_SHEEP || gadget == HotbarItem.DISCO_BALL;
    }

    /** The gadget of this name, the teleport stick when there is none. */
    static HotbarItem gadget(String name) {
        for (HotbarItem gadget : GADGETS) {
            if (gadget.name().equals(name)) {
                return gadget;
            }
        }
        return HotbarItem.TELEPORT;
    }

    /** Uses a gadget of slot 3. */
    void use(Player player, HotbarItem gadget) {
        switch (gadget) {
            case TELEPORT:
                teleport(player);
                break;
            case GRAPPLE:
                if (ready(player, gadget, 1500L)) {
                    grapple(player);
                }
                break;
            case PAINTBALL:
                if (ready(player, gadget, PAINTBALL_COOLDOWN_MS)) {
                    paintball(player);
                }
                break;
            case FIREWORKS:
                if (ready(player, gadget, FIREWORK_COOLDOWN_MS)) {
                    firework(player);
                }
                break;
            case POPPER:
                if (ready(player, gadget, 10_000L)) {
                    popper(player);
                }
                break;
            case LEAP:
                if (ready(player, gadget, LEAP_COOLDOWN_MS)) {
                    leap(player);
                }
                break;
            case BAT_BLASTER:
                if (premium(player) && ready(player, gadget, BATS_COOLDOWN_MS)) {
                    bats(player);
                }
                break;
            case EXPLOSIVE_SHEEP:
                if (premium(player) && ready(player, gadget, SHEEP_COOLDOWN_MS)) {
                    sheep(player);
                }
                break;
            case DISCO_BALL:
                if (premium(player) && ready(player, gadget, DISCO_COOLDOWN_MS)) {
                    disco(player);
                }
                break;
            default:
                break;
        }
    }

    private static boolean premium(Player player) {
        if (player.hasPermission(PREMIUM_PERMISSION)) {
            return true;
        }
        player.sendMessage(ChatColor.LIGHT_PURPLE + "This gadget comes with the [PREMIUM+] rank.");
        return false;
    }

    // --- The gadgets of PREMIUM -----------------------------------------------------------------

    /** The bats, sheep and disco balls of the gadgets: nobody hurts them, and they go when the plugin stops. */
    private final Set<Entity> creatures = new HashSet<>();

    /** Ten bats fly out where the player looks, then vanish in smoke. */
    private void bats(Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        final Vector way = eye.getDirection().normalize();
        final List<Entity> swarm = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Entity bat = world.spawn(eye.clone().add(way.clone().multiply(1.5)), org.bukkit.entity.Bat.class);
            swarm.add(bat);
        }
        creatures.addAll(swarm);
        world.playSound(eye, Sound.BAT_TAKEOFF, 1f, 0.8f);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (++ticks > 40) {
                    for (Entity bat : swarm) {
                        if (bat.isValid()) {
                            world.spigot().playEffect(bat.getLocation(), Effect.LARGE_SMOKE, 0, 0, 0.2f, 0.2f, 0.2f, 0.02f, 4, 48);
                            bat.remove();
                        }
                    }
                    creatures.removeAll(swarm);
                    cancel();
                    return;
                }
                for (Entity bat : swarm) {
                    if (bat.isValid()) {
                        bat.setVelocity(way.clone().multiply(0.7).add(new Vector(random.nextGaussian() * 0.15,
                                random.nextGaussian() * 0.12, random.nextGaussian() * 0.15)));
                    }
                }
                if (ticks % 8 == 0) {
                    world.playSound(swarm.get(0).getLocation(), Sound.BAT_LOOP, 0.6f, 1.2f);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** A sheep of every colour ticks three seconds, then bursts into wool nobody picks up. */
    @SuppressWarnings("deprecation") // DyeColor#getWoolData: the 1.8 data of the wool thrown
    private void sheep(Player player) {
        Location eye = player.getEyeLocation();
        Vector way = eye.getDirection().setY(0);
        if (way.lengthSquared() < 1e-6) {
            way = new Vector(0, 0, 1);
        }
        Location at = player.getLocation().add(way.normalize().multiply(2));
        World world = at.getWorld();
        final org.bukkit.entity.Sheep sheep = world.spawn(at, org.bukkit.entity.Sheep.class);
        sheep.setVelocity(way.clone().multiply(0.4).setY(0.3));
        creatures.add(sheep);
        final org.bukkit.DyeColor[] dyes = org.bukkit.DyeColor.values();
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!sheep.isValid()) {
                    creatures.remove(sheep);
                    cancel();
                    return;
                }
                ticks += 2;
                sheep.setColor(dyes[(ticks / 2) % dyes.length]);
                if (ticks % 6 == 0) {
                    world.playSound(sheep.getLocation(), Sound.NOTE_STICKS, 1f, 0.5f + ticks / 60f);
                }
                if (ticks < 60) {
                    return;
                }
                Location blast = sheep.getLocation().add(0, 0.6, 0);
                sheep.remove();
                creatures.remove(sheep);
                world.playEffect(blast, Effect.EXPLOSION_LARGE, 0);
                world.playSound(blast, Sound.EXPLODE, 0.8f, 1.3f);
                world.playSound(blast, Sound.SHEEP_IDLE, 1f, 1.5f);
                final List<Item> wool = new ArrayList<>();
                for (int i = 0; i < 16; i++) {
                    ItemStack stack = new ItemStack(Material.WOOL, 1, dyes[random.nextInt(dyes.length)].getWoolData());
                    ItemMeta meta = stack.getItemMeta();
                    meta.setDisplayName("wool " + i + " " + random.nextInt());
                    stack.setItemMeta(meta);
                    Item item = world.dropItem(blast, stack);
                    item.setPickupDelay(Integer.MAX_VALUE);
                    item.setVelocity(new Vector(random.nextGaussian() * 0.25, 0.4 + random.nextDouble() * 0.3,
                            random.nextGaussian() * 0.25));
                    wool.add(item);
                }
                sweets.addAll(wool);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (Item item : wool) {
                        item.remove();
                    }
                    sweets.removeAll(wool);
                }, 60L);
                cancel();
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** The players with a disco on: one at a time each. */
    private final Set<UUID> discos = new HashSet<>();

    /**
     * Ten seconds of party around the player: a disco ball (a glass block on an invisible stand)
     * turns above them, the floor around flashes in every colour for everyone's eyes, notes play.
     */
    @SuppressWarnings("deprecation") // Block#getData: the 1.8 data of the floor put back
    private void disco(Player player) {
        if (!discos.add(player.getUniqueId())) {
            return;
        }
        final World world = player.getWorld();
        final Location centre = player.getLocation();
        final org.bukkit.entity.ArmorStand ball = world.spawn(centre.clone().add(0, 2.2, 0), org.bukkit.entity.ArmorStand.class);
        ball.setVisible(false);
        ball.setGravity(false);
        ball.setSmall(false);
        ball.setHelmet(new ItemStack(Material.STAINED_GLASS, 1, (short) 0));
        creatures.add(ball);
        final List<Block> floor = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 18) {
                    continue;
                }
                Block block = centre.getBlock().getRelative(dx, -1, dz);
                for (int dy = 0; dy < 3 && !block.getType().isSolid(); dy++) {
                    block = block.getRelative(0, -1, 0);
                }
                if (block.getType().isSolid() && block.getType().isOccluding()) {
                    floor.add(block);
                }
            }
        }
        final UUID owner = player.getUniqueId();
        world.playSound(centre, Sound.LEVEL_UP, 1f, 0.8f);
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ticks += 2;
                if (ticks > DISCO_TICKS || !ball.isValid()) {
                    for (Player viewer : world.getPlayers()) {
                        for (Block block : floor) {
                            sendBlock(viewer, block.getLocation(), block.getType(), block.getData());
                        }
                    }
                    ball.remove();
                    creatures.remove(ball);
                    discos.remove(owner);
                    cancel();
                    return;
                }
                ball.setHeadPose(new org.bukkit.util.EulerAngle(0, ticks * 0.15, 0));
                if (ticks % 10 == 0) {
                    ball.setHelmet(new ItemStack(Material.STAINED_GLASS, 1, PAINT[random.nextInt(PAINT.length)]));
                    for (Player viewer : world.getPlayers()) {
                        for (Block block : floor) {
                            sendBlock(viewer, block.getLocation(), Material.WOOL, PAINT[random.nextInt(PAINT.length)]);
                        }
                    }
                }
                Location head = ball.getLocation().add(0, 1.6, 0);
                world.spigot().playEffect(head, Effect.COLOURED_DUST, 0, 0, 2.5f, 1.2f, 2.5f, 1f, 12, 48);
                if (ticks % 4 == 0) {
                    float pitch = 0.5f + random.nextInt(13) / 8f;
                    world.playSound(head, ticks % 8 == 0 ? Sound.NOTE_PIANO : Sound.NOTE_BASS_DRUM, 1f, pitch);
                    world.spigot().playEffect(head, Effect.NOTE, 0, 0, 1.5f, 0.5f, 1.5f, 1f, 2, 48);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Nobody hurts, shears or rides the creatures of the gadgets. */
    @EventHandler(priority = EventPriority.LOW)
    public void onCreatureDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (creatures.contains(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCreatureTouched(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (creatures.contains(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onStandTouched(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        if (creatures.contains(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    /** True (and the time noted) if the player may use this gadget again; else says how long to wait. */
    private boolean ready(Player player, HotbarItem gadget, long cooldown) {
        String key = player.getUniqueId() + ":" + gadget.name();
        long now = System.currentTimeMillis();
        Long last = lastGadget.get(key);
        if (last != null && now - last < cooldown) {
            if (cooldown >= 1000L) {
                player.sendMessage(ChatColor.RED + "Wait " + String.format(java.util.Locale.ROOT, "%.1f",
                        (cooldown - (now - last)) / 1000.0) + "s before using it again.");
            }
            return false;
        }
        lastGadget.put(key, now);
        return true;
    }

    /** The grappling hook: the player flies to the block they look at. */
    private void grapple(Player player) {
        Block hooked = null;
        Iterator<Block> blocks = new BlockIterator(player, HOOK_REACH);
        while (blocks.hasNext()) {
            Block block = blocks.next();
            if (block.getType().isSolid()) {
                hooked = block;
                break;
            }
        }
        if (hooked == null) {
            player.sendMessage(ChatColor.GRAY + "Nothing to hook within " + HOOK_REACH + " blocks.");
            lastGadget.remove(player.getUniqueId() + ":" + HotbarItem.GRAPPLE.name());
            return;
        }
        Location from = player.getLocation();
        double[] pull = pull(from.getX(), from.getY(), from.getZ(), hooked.getX() + 0.5, hooked.getY() + 1, hooked.getZ() + 0.5);
        Location eye = player.getEyeLocation();
        Vector step = new Vector(hooked.getX() + 0.5, hooked.getY() + 0.5, hooked.getZ() + 0.5).subtract(eye.toVector());
        double length = step.length();
        step.normalize();
        for (double d = 1; d < length; d += 1) {
            player.getWorld().spigot().playEffect(eye.clone().add(step.clone().multiply(d)), Effect.CRIT, 0, 0, 0f, 0f, 0f, 0f, 1, 48);
        }
        player.setVelocity(new Vector(pull[0], pull[1], pull[2]));
        player.setFallDistance(0f);
        player.getWorld().playSound(from, Sound.SHOOT_ARROW, 1f, 0.6f);
    }

    /**
     * The velocity of the grappling hook from the feet (fx, fy, fz) to the top of the block hooked
     * (tx, ty, tz): towards it, faster the farther it is, a little up so that the player arcs to it.
     */
    static double[] pull(double fx, double fy, double fz, double tx, double ty, double tz) {
        double dx = tx - fx;
        double dy = ty - fy;
        double dz = tz - fz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1e-6) {
            return new double[] {0, 0.5, 0};
        }
        double speed = Math.min(3.0, 0.5 + distance * 0.09);
        double y = dy / distance * speed + 0.35 + Math.min(0.5, distance * 0.02);
        return new double[] {dx / distance * speed, Math.min(2.5, y), dz / distance * speed};
    }

    private void paintball(Player player) {
        Snowball ball = player.launchProjectile(Snowball.class);
        ball.setVelocity(ball.getVelocity().multiply(1.5));
        paintballs.add(ball.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.CHICKEN_EGG_POP, 1f, 1.6f);
    }

    /** A paintball lands: the blocks around colour for everyone's eyes, then come back. */
    @SuppressWarnings("deprecation") // Block#getData: the 1.8 data of the block put back
    @EventHandler
    public void onPaintballHit(ProjectileHitEvent event) {
        if (!paintballs.remove(event.getEntity().getUniqueId())) {
            return;
        }
        Location at = event.getEntity().getLocation();
        World world = at.getWorld();
        Vector way = event.getEntity().getVelocity();
        Block centre = null;
        if (way.lengthSquared() > 1e-6) {
            Iterator<Block> ahead = new BlockIterator(world, at.toVector(), way.normalize(), 0, 3);
            while (ahead.hasNext()) {
                Block block = ahead.next();
                if (block.getType().isSolid()) {
                    centre = block;
                    break;
                }
            }
        }
        if (centre == null) {
            centre = at.getBlock().getRelative(0, -1, 0);
        }
        final List<Block> painted = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 5 || random.nextInt(5) == 0) {
                        continue;
                    }
                    Block block = centre.getRelative(dx, dy, dz);
                    if (block.getType().isSolid() && block.getType().isOccluding()) {
                        painted.add(block);
                    }
                }
            }
        }
        byte colour = PAINT[random.nextInt(PAINT.length)];
        for (Player viewer : world.getPlayers()) {
            for (Block block : painted) {
                sendBlock(viewer, block.getLocation(), Material.STAINED_CLAY, colour);
            }
        }
        world.playSound(at, Sound.SLIME_WALK, 1f, 1.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : world.getPlayers()) {
                for (Block block : painted) {
                    sendBlock(viewer, block.getLocation(), block.getType(), block.getData());
                }
            }
        }, PAINT_TICKS);
    }

    @SuppressWarnings("deprecation") // Player#sendBlockChange(Location, Material, byte): the 1.8 way
    private static void sendBlock(Player viewer, Location at, Material type, byte data) {
        viewer.sendBlockChange(at, type, data);
    }

    private void firework(Player player) {
        Firework firework = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        meta.addEffect(FireworkEffect.builder().with(types[random.nextInt(types.length)])
                .withColor(COLOURS[random.nextInt(COLOURS.length)], COLOURS[random.nextInt(COLOURS.length)])
                .withFade(COLOURS[random.nextInt(COLOURS.length)]).flicker(random.nextBoolean()).trail(random.nextBoolean())
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    /** Sweets fly out of the player (nobody picks them up, they go after three seconds), with music. */
    private void popper(Player player) {
        Location at = player.getLocation().add(0, 1.2, 0);
        World world = at.getWorld();
        final List<Item> thrown = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            ItemStack stack = new ItemStack(SWEETS[random.nextInt(SWEETS.length)]);
            ItemMeta meta = stack.getItemMeta();
            meta.setDisplayName("popper " + i + " " + random.nextInt());
            stack.setItemMeta(meta);
            Item item = world.dropItem(at, stack);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setVelocity(new Vector(random.nextGaussian() * 0.15, 0.35 + random.nextDouble() * 0.25,
                    random.nextGaussian() * 0.15));
            thrown.add(item);
        }
        sweets.addAll(thrown);
        world.spigot().playEffect(at, Effect.FIREWORKS_SPARK, 0, 0, 0.6f, 0.6f, 0.6f, 0.1f, 40, 48);
        float[] notes = {0.7f, 0.9f, 1.05f, 1.2f, 1.4f, 1.6f};
        for (int i = 0; i < notes.length; i++) {
            final float pitch = notes[i];
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> world.playSound(at, Sound.NOTE_PLING, 1f, pitch), i * 3L);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Item item : thrown) {
                item.remove();
            }
            sweets.removeAll(thrown);
        }, 60L);
    }

    private void leap(Player player) {
        Vector way = player.getEyeLocation().getDirection();
        Vector jump = way.clone().setY(0);
        if (jump.lengthSquared() > 1e-6) {
            jump.normalize().multiply(1.6);
        }
        jump.setY(Math.max(0.7, way.getY() * 1.4 + 0.5));
        player.setVelocity(jump);
        player.setFallDistance(0f);
        player.getWorld().playSound(player.getLocation(), Sound.BAT_TAKEOFF, 1f, 1f);
        player.getWorld().spigot().playEffect(player.getLocation(), Effect.CLOUD, 0, 0, 0.3f, 0.1f, 0.3f, 0.05f, 15, 48);
    }

    /**
     * The gadget menu: a click puts the gadget in the hotbar's fourth slot. The gadgets of PREMIUM,
     * on the row below, are locked for the others: a click tells them where the rank is sold.
     */
    void openMenu(Player player, HotbarItem current, java.util.function.BiConsumer<Player, HotbarItem> choose,
            java.util.function.Consumer<Player> locked) {
        ClickMenu menu = new ClickMenu(4, "Gadgets");
        boolean premium = player.hasPermission(PREMIUM_PERMISSION);
        int free = 0;
        int paid = 0;
        for (int i = 0; i < GADGETS.length; i++) {
            final HotbarItem gadget = GADGETS[i];
            boolean open = !premiumOnly(gadget) || premium;
            ItemStack item = gadget.create();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>(meta.getLore());
            if (lore.size() >= 2 && lore.get(lore.size() - 1).contains("another gadget")) {
                lore.remove(lore.size() - 1);
                lore.remove(lore.size() - 1);
            }
            lore.add("");
            lore.add(!open ? ChatColor.RED + "Locked: " + ChatColor.LIGHT_PURPLE + "[PREMIUM+] only"
                    : gadget == current ? ChatColor.GREEN + "In your hotbar" : ChatColor.YELLOW + "Click to take it");
            meta.setLore(lore);
            String name = meta.getDisplayName();
            meta.setDisplayName(name.contains(" " + ChatColor.GRAY) ? name.substring(0, name.indexOf(" " + ChatColor.GRAY)) : name);
            item.setItemMeta(meta);
            int slot = premiumOnly(gadget) ? 20 + 2 * paid++ : 10 + free + (free++ >= 3 ? 1 : 0);
            menu.set(slot, item, (p, right) -> {
                if (!open) {
                    p.closeInventory();
                    locked.accept(p);
                    return;
                }
                choose.accept(p, gadget);
                p.closeInventory();
                p.playSound(p.getLocation(), Sound.CLICK, 1f, 1f);
            });
        }
        menu.open(player);
    }

    /**
     * The player stands on a slime block of the lobby: it throws them where they look (on the
     * ground), and up, as the launch pads of Hypixel.
     */
    void slime(Player player, double forward, double up) {
        if (forward <= 0 && up <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSlime.get(player.getUniqueId());
        if (last != null && now - last < SLIME_COOLDOWN_MS) {
            return;
        }
        lastSlime.put(player.getUniqueId(), now);
        double[] push = launch(player.getLocation().getYaw(), forward, up);
        player.setVelocity(new Vector(push[0], push[1], push[2]));
        player.setFallDistance(0f);
        player.getWorld().playSound(player.getLocation(), Sound.SLIME_WALK2, 1f, 1.4f);
        player.getWorld().playEffect(player.getLocation(), Effect.CLOUD, 0);
    }

    /** The throw of a slime block for a player looking at this yaw: forward on the ground, and up. */
    static double[] launch(float yaw, double forward, double up) {
        double radians = Math.toRadians(yaw);
        return new double[] {-Math.sin(radians) * forward, up, Math.cos(radians) * forward};
    }

    /** The player throws a fireball where they look; the item stays in the hotbar. */
    void throwFireball(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastFireball.get(player.getUniqueId());
        if (last != null && now - last < FIREBALL_COOLDOWN_MS) {
            return;
        }
        lastFireball.put(player.getUniqueId(), now);
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(0f);
        Vector direction = player.getEyeLocation().getDirection();
        // not Fireball#setDirection: on 1.8 it makes the fireball ten times too fast
        FireballAim.aim(fireball, direction);
        fireball.setVelocity(direction.clone().multiply(1.0));
        UUID id = fireball.getUniqueId();
        fireballs.add(id);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (fireballs.remove(id) && !fireball.isDead()) {
                fireball.remove();
            }
        }, FIREBALL_LIFE_TICKS);
    }

    /** The blast of a lobby fireball: the players around are pushed, nothing else happens. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!fireballs.remove(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        Location blast = event.getEntity().getLocation();
        World world = blast.getWorld();
        world.playEffect(blast, Effect.EXPLOSION_LARGE, 0);
        world.playSound(blast, Sound.EXPLODE, 1f, 1f);
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            if (!reaches(at.getX() - blast.getX(), at.getY() - blast.getY(), at.getZ() - blast.getZ())) {
                continue;
            }
            double[] push = push(blast.getX(), blast.getY(), blast.getZ(), at.getX(), at.getY(), at.getZ());
            player.setVelocity(new Vector(push[0], push[1], push[2]));
            player.setFallDistance(0f);
        }
    }

    /** A lobby fireball that hits a player straight on hurts no one. */
    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent event) {
        if (fireballs.contains(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Removes the fireballs in flight and the sweets on the ground (the plugin stops). */
    void clear() {
        for (Item item : sweets) {
            item.remove();
        }
        sweets.clear();
        for (Entity creature : creatures) {
            creature.remove();
        }
        creatures.clear();
        paintballs.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Fireball.class)) {
                if (fireballs.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
        fireballs.clear();
    }

    /** The teleport stick: onto the block the player looks at if it is near enough, else ahead in the air. */
    void teleport(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastTeleport.get(player.getUniqueId());
        if (last != null && now - last < TELEPORT_COOLDOWN_MS) {
            long left = TELEPORT_COOLDOWN_MS - (now - last);
            player.sendMessage(ChatColor.RED + "Wait " + String.format(java.util.Locale.ROOT, "%.1f", left / 1000.0)
                    + "s before teleporting again.");
            return;
        }
        List<int[]> ray = new ArrayList<>();
        Iterator<Block> blocks = new BlockIterator(player, REACH);
        while (blocks.hasNext() && ray.size() <= REACH + 1) {
            Block block = blocks.next();
            ray.add(new int[] {block.getX(), block.getY(), block.getZ()});
        }
        World world = player.getWorld();
        Predicate<int[]> passable = place -> place[1] < 0 || place[1] > 255
                || !world.getBlockAt(place[0], place[1], place[2]).getType().isSolid();
        int[] feet = destination(ray, passable);
        Location from = player.getLocation();
        Location to;
        if (feet != null) {
            to = new Location(world, feet[0] + 0.5, feet[1], feet[2] + 0.5, from.getYaw(), from.getPitch());
        } else if (allPassable(ray, passable)) {
            Location eye = player.getEyeLocation();
            Vector way = eye.getDirection();
            double[] air = airDestination(new double[] {eye.getX(), eye.getY(), eye.getZ()},
                    new double[] {way.getX(), way.getY(), way.getZ()}, REACH, passable);
            if (air == null) {
                player.sendMessage(ChatColor.GRAY + "No room to teleport to.");
                return;
            }
            to = new Location(world, air[0], air[1], air[2], from.getYaw(), from.getPitch());
        } else {
            player.sendMessage(ChatColor.GRAY + "No room to teleport to.");
            return;
        }
        lastTeleport.put(player.getUniqueId(), now);
        world.playEffect(from, Effect.ENDER_SIGNAL, 0);
        player.setFallDistance(0f);
        player.teleport(to);
        world.playSound(to, Sound.ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    /**
     * Where the teleport stick takes the player: the block of the feet, or null. The ray holds the
     * blocks along the look, from the eyes on; the first block that is not passable is the one
     * looked at, and nothing beyond it counts (no going through a wall). On top of it when there is
     * room for a player there and the look did not come from under it; else in the last free block
     * before it, standing in it or just under it.
     */
    static int[] destination(List<int[]> ray, Predicate<int[]> passable) {
        int[] previous = null;
        for (int[] block : ray) {
            if (passable.test(block)) {
                previous = block;
                continue;
            }
            if (previous == null) {
                // the player's eyes are in the block: nowhere to go
                return null;
            }
            boolean fromBelow = previous[1] < block[1] && previous[0] == block[0] && previous[2] == block[2];
            int[] top = {block[0], block[1] + 1, block[2]};
            if (!fromBelow && room(top, passable)) {
                return top;
            }
            if (room(previous, passable)) {
                return previous;
            }
            int[] under = {previous[0], previous[1] - 1, previous[2]};
            if (room(under, passable)) {
                return under;
            }
            return null;
        }
        return null;
    }

    private static boolean allPassable(List<int[]> ray, Predicate<int[]> passable) {
        for (int[] block : ray) {
            if (!passable.test(block)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the stick takes a player who looks at the air: the eyes {@code reach} blocks ahead along
     * the look (eye, unit way), or nearer when the body would be in a block there; null when there
     * is no room on the whole way. The feet: x, y, z.
     */
    static double[] airDestination(double[] eye, double[] way, int reach, Predicate<int[]> passable) {
        for (double distance = reach; distance >= 1; distance -= 0.5) {
            double x = eye[0] + way[0] * distance;
            double y = eye[1] + way[1] * distance - EYE_HEIGHT;
            double z = eye[2] + way[2] * distance;
            int[] feet = {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
            int[] head = {feet[0], (int) Math.floor(y + 1.8), feet[2]};
            if (passable.test(feet) && passable.test(head)
                    && passable.test(new int[] {feet[0], feet[1] + 1, feet[2]})) {
                return new double[] {x, y, z};
            }
        }
        return null;
    }

    /** True if a player stands in these feet: the block and the one above are passable. */
    private static boolean room(int[] feet, Predicate<int[]> passable) {
        return passable.test(feet) && passable.test(new int[] {feet[0], feet[1] + 1, feet[2]});
    }

    /**
     * The velocity the blast at (bx, by, bz) gives a player standing at (px, py, pz), as
     * BedWars1058 computes it: away from the blast horizontally, and up; more up when the blast is
     * under the player's feet (a fireball jump).
     */
    static double[] push(double bx, double by, double bz, double px, double py, double pz) {
        double dx = bx - px;
        double dy = by - py;
        double dz = bz - pz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) {
            dx = 0;
            dy = 1;
            dz = 0;
            length = 1;
        }
        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;
        double y = ny < 0 ? ny + 1.5 : ny;
        y = y <= 0.5 ? VERTICAL * 1.5 : y * VERTICAL * 1.5;
        return new double[] {-nx * HORIZONTAL, y, -nz * HORIZONTAL};
    }

    /** True if a player at this offset from the blast is pushed by it. */
    static boolean reaches(double dx, double dy, double dz) {
        return Math.abs(dx) <= RADIUS && Math.abs(dy) <= RADIUS && Math.abs(dz) <= RADIUS;
    }
}
