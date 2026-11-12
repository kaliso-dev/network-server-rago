package gg.rago.lobby;

import gg.rago.lobby.api.ReplayRecording;
import gg.rago.lobby.api.ReplaySetup;
import gg.rago.lobby.api.ReplayStages;
import gg.rago.lobby.api.Replays;
import gg.rago.lobby.api.VoidGenerator;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * The replays: each game a game plugin records ({@link #record}) is kept in plugins/RagoLobby/
 * replays/(id).replay, and /replay shows a player their last games, or any game by its code. A
 * replay of an arena whose blocks it holds (a practice duel) is shown in the lobby's own empty
 * world, rago_replays, one stage every 1000 blocks; a replay of a map (a Bed Wars game) in a world
 * the game plugin makes again ({@link ReplayStages}). The files are read and written away from the
 * main thread, kept a few days, within a size. Main thread only, except the file work.
 */
final class ReplayService implements Replays, Listener {

    static final String WORLD = "rago_replays";
    /** In the folder of the replay world: marks it as ours to delete at the next start. */
    static final String MARKER = "rago-replays.txt";
    static final int SLOT_SPACING = 1000;
    /** Blocks placed (or cleared) each tick on the stages of the replay world. */
    static final int BLOCKS_PER_TICK = 2000;
    static final String SUFFIX = ".replay";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int ID_LENGTH = 8;
    private static final Pattern ID = Pattern.compile("[A-Z0-9]{" + ID_LENGTH + "}");
    /** A viewer this far from the stage goes back to it. */
    private static final double MAX_DISTANCE = 400;
    private static final long SWEEP_TICKS = 20L * 60 * 60 * 6;
    /** A replay asked for from a game starts if the player is back in the lobby within this time. */
    private static final long PENDING_MILLIS = 120_000L;
    /** A game this recent gets [REPLAY] in the line after it (see NextGame). */
    private static final long RECENT_MILLIS = 120_000L;

    /** The recordings of one world, and the hook that hears its blocks. */
    private static final class Group {
        final List<ReplayRecorder> recorders = new CopyOnWriteArrayList<>();
        ReplayHook hook;
    }

    /** A plugin's stages. */
    private static final class Registered {
        final Plugin plugin;
        final ReplayStages stages;

        Registered(Plugin plugin, ReplayStages stages) {
            this.plugin = plugin;
            this.stages = stages;
        }
    }

    /** Blocks being placed on a stage of the replay world, or cleared from it. */
    private static final class Job {
        final World world;
        final int ox;
        final int oy;
        final int oz;
        final int[] blocks;
        final boolean clear;
        final Runnable done;
        int next;

        Job(World world, int ox, int oy, int oz, int[] blocks, boolean clear, Runnable done) {
            this.world = world;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.blocks = blocks;
            this.clear = clear;
            this.done = done;
        }
    }

    /** What a sweep of the folder found: the replays kept, and the ids deleted. */
    private static final class Sweep {
        final List<ReplayData.Info> kept = new ArrayList<>();
        final Set<String> deleted = new HashSet<>();
        long bytes;
    }

    private final RagoLobby lobby;
    private final File folder;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "RagoLobby replays");
        thread.setDaemon(true);
        return thread;
    });
    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;
    private final int keepDays;
    private final long maxBytes;
    private final int maxViewers;

    private final Map<World, Group> groups = new HashMap<>();
    private final Map<UUID, ReplayRecorder> byActor = new HashMap<>();
    private boolean hookWarned;
    /** Newest first. */
    private final List<ReplayData.Info> index = new ArrayList<>();
    private final Set<String> writing = new HashSet<>();
    private final Map<String, Registered> stages = new HashMap<>();
    private final Map<UUID, ReplayViewer> viewers = new HashMap<>();
    /** The players whose replay is being read or its stage made, and its kind of stage ("" while read). */
    private final Map<UUID, String> loading = new HashMap<>();
    /** The replay each player asked for from their game (its [REPLAY]), shown when back in the lobby, and when. */
    private final Map<UUID, String> pending = new HashMap<>();
    private final Map<UUID, Long> pendingSince = new HashMap<>();
    /** The worlds a replay is shown in now: their water, fire and falling sand stay still. */
    private final Set<World> stageWorlds = new HashSet<>();
    private World replayWorld;
    private final BitSet slots = new BitSet();
    private final List<Job> jobs = new ArrayList<>();
    private int task = -1;

    ReplayService(RagoLobby lobby, ConfigurationSection config) {
        this.lobby = lobby;
        this.folder = new File(lobby.getDataFolder(), "replays");
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("replays");
        this.enabled = section == null || section.getBoolean("actif", true);
        this.keepDays = section == null ? 3 : Math.max(1, Math.min(60, section.getInt("jours", 3)));
        this.maxBytes = (section == null ? 2000L : Math.max(50L, Math.min(500_000L, section.getLong("taille-max-mo", 2000L))))
                * 1024L * 1024L;
        this.maxViewers = section == null ? 6 : Math.max(1, Math.min(50, section.getInt("spectateurs-max", 6)));
    }

    boolean enabled() {
        return enabled;
    }

    void start() {
        if (!enabled) {
            return;
        }
        final File worldFolder = new File(Bukkit.getWorldContainer(), WORLD);
        io.execute(() -> {
            if (Bukkit.getWorld(WORLD) == null && new File(worldFolder, MARKER).isFile()) {
                try {
                    delete(worldFolder.toPath());
                } catch (IOException e) {
                    lobby.getLogger().log(Level.WARNING, "Replays: cannot delete the old " + WORLD + " world", e);
                }
            }
        });
        sweep(true);
        task = Bukkit.getScheduler().runTaskTimer(lobby, this::tick, 1L, 1L).getTaskId();
        Bukkit.getScheduler().runTaskTimer(lobby, () -> sweep(false), SWEEP_TICKS, SWEEP_TICKS);
    }

    /** The plugin stops: the replays watched end, the files being written are finished. */
    void stop() {
        for (ReplayViewer viewer : new ArrayList<>(viewers.values())) {
            stopViewer(viewer, true);
        }
        // the replay world is unloaded without being saved, and deleted at the next start
        jobs.clear();
        // the games still recorded are not kept (each one cancelled leaves its world's group)
        for (Group group : new ArrayList<>(groups.values())) {
            for (ReplayRecorder recorder : group.recorders) {
                recorder.cancel();
            }
            if (group.hook != null) {
                group.hook.remove();
                group.hook = null;
            }
        }
        groups.clear();
        if (task != -1) {
            Bukkit.getScheduler().cancelTask(task);
        }
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (replayWorld != null) {
            Bukkit.unloadWorld(replayWorld, false);
            replayWorld = null;
        }
    }

    // --- Replays (the game plugins) -------------------------------------------------------------

    @Override
    public ReplayRecording record(ReplaySetup setup) {
        if (!enabled || setup == null || setup.world() == null) {
            return null;
        }
        World world = setup.world();
        ReplayRecorder recorder = new ReplayRecorder(this, setup);
        Group group = groups.get(world);
        if (group == null) {
            group = new Group();
            final Group heard = group;
            try {
                group.hook = ReplayHook.install(world, (x, y, z) -> {
                    if (!Bukkit.isPrimaryThread()) {
                        // a plugin that changes blocks from another thread: not recorded
                        return;
                    }
                    for (ReplayRecorder each : heard.recorders) {
                        each.changed(x, y, z);
                    }
                });
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                if (!hookWarned) {
                    hookWarned = true;
                    lobby.getLogger().warning("Replays: only the blocks of players and explosions are recorded, not those"
                            + " of the plugins (" + e + ")");
                }
            }
            groups.put(world, group);
        }
        group.recorders.add(recorder);
        return recorder;
    }

    @Override
    public void registerStages(Plugin plugin, String kind, ReplayStages stages) {
        if (ReplaySetup.BLOCKS.equals(kind)) {
            throw new IllegalArgumentException(kind + " is the lobby's own stage");
        }
        this.stages.put(kind, new Registered(plugin, stages));
    }

    @Override
    public void unregisterStages(Plugin plugin) {
        for (Iterator<Map.Entry<String, Registered>> it = stages.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, Registered> entry = it.next();
            if (entry.getValue().plugin != plugin) {
                continue;
            }
            // the replays waiting for one of its worlds will not get it
            for (Iterator<Map.Entry<UUID, String>> waiting = loading.entrySet().iterator(); waiting.hasNext();) {
                Map.Entry<UUID, String> each = waiting.next();
                if (each.getValue().equals(entry.getKey())) {
                    waiting.remove();
                    Player player = Bukkit.getPlayer(each.getKey());
                    if (player != null) {
                        player.sendMessage(ChatColor.RED + "This replay's game is not on this server now.");
                    }
                }
            }
            // the replays shown in its worlds end first, while it can still take them back
            for (ReplayViewer viewer : new ArrayList<>(viewers.values())) {
                if (stageOf(viewer) == entry.getValue()) {
                    stopViewer(viewer, true);
                }
            }
            it.remove();
        }
    }

    // --- The recorders --------------------------------------------------------------------------

    void actorAdded(ReplayRecorder recorder, UUID player) {
        byActor.put(player, recorder);
    }

    void stopped(ReplayRecorder recorder) {
        byActor.values().removeIf(each -> each == recorder);
        Group group = groups.get(recorder.world());
        if (group == null) {
            return;
        }
        group.recorders.remove(recorder);
        if (group.recorders.isEmpty()) {
            if (group.hook != null) {
                group.hook.remove();
            }
            groups.remove(recorder.world());
        }
    }

    String nickOf(UUID player) {
        Nicks nicks = lobby.nicks();
        return nicks == null ? null : nicks.nick(player);
    }

    /** A new code, used by no replay. */
    String newId() {
        while (true) {
            StringBuilder id = new StringBuilder();
            for (int i = 0; i < ID_LENGTH; i++) {
                id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = id.toString();
            if (info(code) == null && !writing.contains(code) && !fileOf(code).exists()) {
                return code;
            }
        }
    }

    /** A replay is over: in the menu at once, on the disk a moment later. */
    void save(final ReplayData.Info info, final int[] stage, final byte[] body) {
        final ReplayData.Info light = info.light();
        index.add(0, light);
        writing.add(info.id);
        final File file = fileOf(info.id);
        io.execute(() -> {
            boolean written = false;
            File temporary = new File(file.getPath() + ".tmp");
            try {
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    throw new IOException("cannot create " + folder);
                }
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(temporary), 65536)) {
                    ReplayData.write(out, info, stage, body);
                }
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                written = true;
            } catch (IOException | RuntimeException e) {
                lobby.getLogger().log(Level.WARNING, "Replays: cannot write " + file.getName(), e);
                temporary.delete();
            }
            final boolean ok = written;
            later(0L, () -> {
                writing.remove(info.id);
                if (!ok) {
                    index.remove(light);
                }
            });
        });
    }

    // --- Each tick ------------------------------------------------------------------------------

    private void tick() {
        for (Map.Entry<World, Group> entry : new ArrayList<>(groups.entrySet())) {
            Group group = entry.getValue();
            if (group.recorders.isEmpty()) {
                continue;
            }
            List<Entity> things = new ArrayList<>(entry.getKey().getEntitiesByClasses(Projectile.class, TNTPrimed.class,
                    FallingBlock.class));
            for (ReplayRecorder recorder : group.recorders) {
                recorder.tick(things);
            }
        }
        for (ReplayViewer viewer : new ArrayList<>(viewers.values())) {
            Player player = viewer.player();
            if (!player.isOnline() || !player.getWorld().equals(viewer.world())) {
                stopViewer(viewer, false);
                continue;
            }
            viewer.tick();
        }
        int left = BLOCKS_PER_TICK;
        while (left > 0 && !jobs.isEmpty()) {
            Job job = jobs.get(0);
            left -= step(job, left);
            if (job.next >= job.blocks.length) {
                jobs.remove(0);
                job.done.run();
            }
        }
    }

    /** Places (or clears) up to max blocks of the job; returns how many. */
    @SuppressWarnings("deprecation") // Block#setTypeIdAndData: the 1.8 way to set a block with its data
    private static int step(Job job, int max) {
        int done = 0;
        while (job.next < job.blocks.length && done < max) {
            int i = job.next;
            int y = job.oy + job.blocks[i + 1];
            if (y >= 0 && y < 256) {
                Block block = job.world.getBlockAt(job.ox + job.blocks[i], y, job.oz + job.blocks[i + 2]);
                if (job.clear) {
                    block.setType(Material.AIR, false);
                } else {
                    block.setTypeIdAndData(job.blocks[i + 3], (byte) job.blocks[i + 4], false);
                }
            }
            job.next += 5;
            done++;
        }
        return done;
    }

    void later(long ticks, Runnable task) {
        if (lobby.isEnabled()) {
            Bukkit.getScheduler().runTaskLater(lobby, task, ticks);
        }
    }

    void sidebar(Player player, List<String> lines) {
        lobby.setReplaySidebar(player, lines);
    }

    // --- /replay --------------------------------------------------------------------------------

    boolean command(Player player, String[] args) {
        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Replays are off on this server.");
            return true;
        }
        if (args.length == 0) {
            menu(player);
        } else if (args[0].equalsIgnoreCase("leave")) {
            if (!leave(player)) {
                player.sendMessage(ChatColor.RED + "You are not watching a replay.");
            }
        } else {
            watch(player, args[0]);
        }
        return true;
    }

    /** The player's last games. */
    void menu(final Player player) {
        List<ReplayData.Info> mine = new ArrayList<>();
        for (ReplayData.Info info : index) {
            if (info.actorOf(player.getUniqueId()) >= 0) {
                mine.add(info);
                if (mine.size() >= 45) {
                    break;
                }
            }
        }
        if (mine.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No replay yet: play a duel or a Bed Wars game, its replay comes here"
                    + " (kept " + keepDays + (keepDays == 1 ? " day)." : " days)."));
            return;
        }
        ClickMenu menu = new ClickMenu(6, "Your replays");
        SimpleDateFormat date = new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT);
        for (int i = 0; i < mine.size(); i++) {
            final ReplayData.Info info = mine.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + date.format(new Date(info.startedAt)) + " - " + ReplayData.clock(info.ticks));
            if (!info.mapName.isEmpty()) {
                lore.add(ChatColor.GRAY + "Map: " + ChatColor.WHITE + info.mapName);
            }
            lore.add(ChatColor.GRAY + "Players: " + names(info, 6));
            if (!info.result.isEmpty()) {
                lore.add(ChatColor.GRAY + "Result: " + ChatColor.WHITE + info.result);
            }
            lore.add("");
            lore.add(ChatColor.GRAY + "Code: " + ChatColor.WHITE + info.id);
            lore.add(ChatColor.YELLOW + "Click to watch!");
            String name = ChatColor.GREEN + (info.modeName.isEmpty() ? info.modeId : info.modeName);
            menu.set(i, ClickMenu.item(Material.EMPTY_MAP, 0, name, lore), (clicker, right) -> {
                clicker.closeInventory();
                watch(clicker, info.id);
            });
        }
        menu.set(49, ClickMenu.item(Material.BOOK, ChatColor.GREEN + "Replays",
                "Your games of the last " + keepDays + (keepDays == 1 ? " day." : " days."),
                "Share a game: its code with /replay CODE."), null);
        menu.open(player);
    }

    private static String names(ReplayData.Info info, int max) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < info.actors.size() && i < max; i++) {
            ReplayData.Actor actor = info.actors.get(i);
            if (i > 0) {
                names.append(ChatColor.GRAY).append(", ");
            }
            names.append(ChatColor.COLOR_CHAR).append(actor.colour).append(actor.name);
        }
        if (info.actors.size() > max) {
            names.append(ChatColor.GRAY).append(" +").append(info.actors.size() - max);
        }
        return names.toString();
    }

    /** Shows the replay of this code to the player, once read and its stage ready. */
    void watch(final Player player, String code) {
        final UUID id = player.getUniqueId();
        final String upper = code.toUpperCase(Locale.ROOT);
        if (viewers.containsKey(id) || loading.containsKey(id)) {
            player.sendMessage(ChatColor.RED + "You are already watching a replay: leave it first (the bed).");
            return;
        }
        if (!ID.matcher(upper).matches() || (info(upper) == null && !writing.contains(upper))) {
            player.sendMessage(ChatColor.RED + "No replay with the code " + code + ". " + ChatColor.GRAY
                    + "Replays are kept " + keepDays + (keepDays == 1 ? " day." : " days."));
            return;
        }
        if (lobby.state().isInGame(player)) {
            // the [REPLAY] of the game's end: once the player is back in the lobby
            pending.put(id, upper);
            pendingSince.put(id, System.currentTimeMillis());
            player.sendMessage(ChatColor.GRAY + "The replay starts when you are back in the lobby.");
            return;
        }
        if (!lobby.state().isInLobby(player)) {
            player.sendMessage(ChatColor.RED + "Go back to the lobby to watch a replay: /spawn");
            return;
        }
        if (viewers.size() + loading.size() >= maxViewers) {
            player.sendMessage(ChatColor.RED + "Too many replays are being watched: try again in a minute.");
            return;
        }
        if (lobby.queues().queueOf(player) != null) {
            lobby.queues().leave(player, true);
        }
        loading.put(id, "");
        player.sendMessage(ChatColor.GRAY + "Loading the replay " + upper + "...");
        final File file = fileOf(upper);
        io.execute(() -> {
            ReplayData data;
            try (InputStream in = new BufferedInputStream(new FileInputStream(file), 65536)) {
                data = ReplayData.read(in);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                lobby.getLogger().log(Level.WARNING, "Replays: cannot read " + file.getName(), e);
                later(0L, () -> {
                    loading.remove(id);
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        online.sendMessage(ChatColor.RED + "This replay cannot be read.");
                    }
                });
                return;
            }
            final ReplayData read = data;
            later(0L, () -> stage(id, read));
        });
    }

    /** The replay is read: its stage is made, then shown. */
    private void stage(final UUID id, final ReplayData data) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !lobby.state().isInLobby(player) || lobby.state().isInGame(player)) {
            loading.remove(id);
            return;
        }
        String kind = data.info.stageKind;
        if (ReplaySetup.BLOCKS.equals(kind)) {
            final World world = replayWorld();
            if (world == null) {
                loading.remove(id);
                player.sendMessage(ChatColor.RED + "The replay world cannot be made.");
                return;
            }
            final int slot = slots.nextClearBit(0);
            slots.set(slot);
            final int ox = slot * SLOT_SPACING;
            final int oy = VoidGenerator.PLATFORM_Y;
            final int oz = 0;
            final Runnable release = () -> jobs.add(new Job(world, ox, oy, oz, data.stage, true, () -> slots.clear(slot)));
            player.sendMessage(ChatColor.GRAY + "Building the arena...");
            jobs.add(new Job(world, ox, oy, oz, data.stage, false, () -> show(id, data, world, ox, oy, oz, release)));
            return;
        }
        final Registered registered = stages.get(kind);
        if (registered == null) {
            loading.remove(id);
            player.sendMessage(ChatColor.RED + "This replay's game is not on this server now.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "Preparing the map...");
        loading.put(id, kind);
        registered.stages.prepare(data.info.stageKey, world -> {
            stageWorlds.add(world);
            show(id, data, world, 0, 0, 0, () -> {
                stageWorlds.remove(world);
                if (stages.containsValue(registered)) {
                    registered.stages.release(world);
                }
            });
        }, reason -> {
            loading.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                online.sendMessage(ChatColor.RED + reason);
            }
        });
    }

    private void show(UUID id, ReplayData data, World world, int ox, int oy, int oz, Runnable release) {
        loading.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player == null || !lobby.state().isInLobby(player) || lobby.state().isInGame(player) || viewers.containsKey(id)) {
            release.run();
            return;
        }
        if (lobby.queues().queueOf(player) != null) {
            lobby.queues().leave(player, true);
        }
        ReplayViewer viewer = new ReplayViewer(this, player, data, world, ox, oy, oz, release);
        viewers.put(id, viewer);
        viewer.begin();
    }

    /** The code of the player's game of this mode that ended in the last two minutes, if it was recorded; null else. */
    String recentOf(UUID player, String modeId) {
        if (!enabled) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (ReplayData.Info info : index) {
            if (info.startedAt + info.ticks * 50L < now - RECENT_MILLIS) {
                // newest first: the others are older
                if (now - info.startedAt > ReplayRecorder.MAX_TICKS * 50L + RECENT_MILLIS) {
                    return null;
                }
                continue;
            }
            if (info.modeId.equals(modeId) && info.actorOf(player) >= 0) {
                return info.id;
            }
        }
        return null;
    }

    /** The player is back in the lobby: the replay they asked for from their game starts. */
    void backInLobby(Player player) {
        final UUID id = player.getUniqueId();
        final String code = pending.remove(id);
        Long since = pendingSince.remove(id);
        if (code == null || since == null || System.currentTimeMillis() - since > PENDING_MILLIS) {
            return;
        }
        later(2L, () -> {
            Player online = Bukkit.getPlayer(id);
            if (online != null && lobby.state().isInLobby(online) && !lobby.state().isInGame(online)) {
                watch(online, code);
            }
        });
    }

    /** The player leaves the replay they watch, back to the lobby. False if they watch none. */
    boolean leave(Player player) {
        ReplayViewer viewer = viewers.get(player.getUniqueId());
        if (viewer == null) {
            return false;
        }
        stopViewer(viewer, true);
        return true;
    }

    boolean watching(Player player) {
        return viewers.containsKey(player.getUniqueId());
    }

    private void stopViewer(ReplayViewer viewer, boolean toLobby) {
        Player player = viewer.player();
        if (viewers.remove(player.getUniqueId()) == null) {
            return;
        }
        viewer.end();
        viewer.release().run();
        lobby.setReplaySidebar(player, null);
        if (toLobby && player.isOnline()) {
            lobby.sendToLobby(player);
        }
    }

    /** The players of the replay, to go to one of them. */
    void playersMenu(final ReplayViewer viewer) {
        ReplayData.Info info = infoOf(viewer);
        if (info == null) {
            return;
        }
        int rows = Math.max(1, Math.min(6, (info.actors.size() + 8) / 9));
        ClickMenu menu = new ClickMenu(rows, "Go to a player");
        for (int a = 0; a < info.actors.size() && a < rows * 9; a++) {
            final int actor = a;
            ReplayData.Actor each = info.actors.get(a);
            menu.set(a, ClickMenu.head(each.name, ChatColor.COLOR_CHAR + "" + each.colour + each.name,
                    Arrays.asList(ChatColor.YELLOW + "Click to go to them!")), (clicker, right) -> {
                        clicker.closeInventory();
                        if (viewers.get(clicker.getUniqueId()) == viewer) {
                            viewer.goTo(actor);
                        }
                    });
        }
        menu.open(viewer.player());
    }

    private ReplayData.Info infoOf(ReplayViewer viewer) {
        return info(viewer.id());
    }

    private Registered stageOf(ReplayViewer viewer) {
        ReplayData.Info info = info(viewer.id());
        return info == null ? null : stages.get(info.stageKind);
    }

    ReplayData.Info info(String id) {
        for (ReplayData.Info info : index) {
            if (info.id.equals(id)) {
                return info;
            }
        }
        return null;
    }

    File fileOf(String id) {
        return new File(folder, id + SUFFIX);
    }

    /** The lobby's world of the replays of arenas: empty, made at the first replay. */
    private World replayWorld() {
        if (replayWorld != null) {
            return replayWorld;
        }
        World world = Bukkit.getWorld(WORLD);
        if (world == null) {
            world = new WorldCreator(WORLD).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world == null) {
            return null;
        }
        try {
            Files.write(new File(world.getWorldFolder(), MARKER).toPath(),
                    "The replays of RagoLobby: deleted at each start.\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            lobby.getLogger().log(Level.WARNING, "Replays: cannot mark the " + WORLD + " world", e);
        }
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setTime(6000);
        world.setStorm(false);
        replayWorld = world;
        stageWorlds.add(world);
        return world;
    }

    // --- The files ------------------------------------------------------------------------------

    /**
     * Reads the folder away from the main thread: the replays older than the days kept go, then
     * the oldest ones beyond the size kept; the menu gets the others (at the start: all of them).
     */
    private void sweep(final boolean first) {
        final long now = System.currentTimeMillis();
        io.execute(() -> {
            final Sweep sweep = new Sweep();
            File[] files = folder.listFiles();
            if (files == null) {
                files = new File[0];
            }
            List<Object[]> found = new ArrayList<>();
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(SUFFIX + ".tmp") && now - file.lastModified() > 3_600_000L) {
                    file.delete();
                    continue;
                }
                if (!name.endsWith(SUFFIX)) {
                    continue;
                }
                String code = name.substring(0, name.length() - SUFFIX.length());
                ReplayData.Info info;
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    info = ReplayData.readInfo(in);
                } catch (IOException | RuntimeException e) {
                    info = null;
                }
                if (info == null || !info.id.equals(code) || now - info.startedAt > keepDays * 86_400_000L) {
                    if (file.delete()) {
                        sweep.deleted.add(code);
                    }
                    continue;
                }
                found.add(new Object[] {info, file});
            }
            found.sort(Comparator.comparingLong((Object[] entry) -> ((ReplayData.Info) entry[0]).startedAt).reversed());
            for (Object[] entry : found) {
                ReplayData.Info info = (ReplayData.Info) entry[0];
                File file = (File) entry[1];
                if (sweep.bytes + file.length() > maxBytes) {
                    if (file.delete()) {
                        sweep.deleted.add(info.id);
                    }
                    continue;
                }
                sweep.bytes += file.length();
                sweep.kept.add(info.light());
            }
            later(0L, () -> {
                index.removeIf(info -> sweep.deleted.contains(info.id));
                Set<String> known = new HashSet<>();
                for (ReplayData.Info info : index) {
                    known.add(info.id);
                }
                for (ReplayData.Info info : sweep.kept) {
                    if (known.add(info.id)) {
                        index.add(info);
                    }
                }
                index.sort(Comparator.comparingLong((ReplayData.Info info) -> info.startedAt).reversed());
                if (first) {
                    lobby.getLogger().info("Replays: " + sweep.kept.size() + " kept (" + sweep.bytes / (1024 * 1024) + " MB, "
                            + keepDays + (keepDays == 1 ? " day" : " days") + ")");
                }
            });
        });
    }

    private static void delete(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // --- The games: what the ticks do not see ---------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        ReplayRecorder recorder = byActor.get(event.getPlayer().getUniqueId());
        if (recorder != null) {
            recorder.swing(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            ReplayRecorder recorder = byActor.get(event.getEntity().getUniqueId());
            // a hit without damage too (a sumo's knockback): the body flashes red
            if (recorder != null) {
                recorder.hurt(event.getEntity().getUniqueId());
            }
        }
    }

    /** An explosion, even one the game handles itself (its blast, its sound in the replay). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        Group group = groups.get(event.getLocation().getWorld());
        if (group == null) {
            return;
        }
        for (ReplayRecorder recorder : group.recorders) {
            recorder.explosion(event.getLocation());
            if (!event.isCancelled()) {
                for (Block block : event.blockList()) {
                    recorder.changed(block.getX(), block.getY(), block.getZ());
                }
            }
        }
    }

    /** Without the world hook, the blocks of the players at least (with it, the same block twice changes nothing). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        changed(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlowRecorded(BlockFromToEvent event) {
        changed(event.getToBlock());
    }

    private void changed(Block block) {
        Group group = groups.get(block.getWorld());
        if (group != null) {
            for (ReplayRecorder recorder : group.recorders) {
                recorder.changed(block.getX(), block.getY(), block.getZ());
            }
        }
    }

    // --- The viewers ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        ReplayViewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null || event.getAction() == Action.PHYSICAL) {
            return;
        }
        event.setCancelled(true);
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        viewer.click(event.getItem(), right);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (viewers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onViewerHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && viewers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onHunger(FoodLevelChangeEvent event) {
        if (viewers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (viewers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(PlayerPickupItemEvent event) {
        if (viewers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventory(InventoryClickEvent event) {
        if (viewers.containsKey(event.getWhoClicked().getUniqueId())
                && !(event.getView().getTopInventory().getHolder() instanceof ClickMenu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onViewerPlace(BlockPlaceEvent event) {
        if (viewers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onViewerBreak(BlockBreakEvent event) {
        if (viewers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** A viewer under the stage or far from it goes back to it. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        ReplayViewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer == null || event.getTo() == null) {
            return;
        }
        Location to = event.getTo();
        Location centre = viewer.centre();
        double dx = to.getX() - centre.getX();
        double dz = to.getZ() - centre.getZ();
        if (to.getY() < -5 || dx * dx + dz * dz > MAX_DISTANCE * MAX_DISTANCE) {
            Location back = to.getY() < -5 ? to.clone() : centre.clone();
            back.setY(Math.max(back.getY(), centre.getY() + 10));
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            event.getPlayer().setFlying(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        ReplayViewer viewer = viewers.get(event.getPlayer().getUniqueId());
        if (viewer != null && !event.getPlayer().getWorld().equals(viewer.world())) {
            stopViewer(viewer, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        loading.remove(id);
        pending.remove(id);
        pendingSince.remove(id);
        ReplayViewer viewer = viewers.get(id);
        if (viewer != null) {
            stopViewer(viewer, false);
        }
    }

    // --- The stage worlds stay as the replay sets them ------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onFlow(BlockFromToEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysics(BlockPhysicsEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFade(BlockFadeEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onForm(BlockFormEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpread(BlockSpreadEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDecay(LeavesDecayEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBurn(BlockBurnEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFall(EntityChangeBlockEvent event) {
        if (stageWorlds.contains(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }
}
