package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

/**
 * The RagoBots online: one per party that invited it (/p invite RagoBot), each a player of the
 * server ({@link BotBody}) with its mind ({@link BotBrain}). It joins at once next to the leader,
 * plays the party's games of its modes (ragobot.modes, never ranked) as a member of the party,
 * never leads it, and leaves the server once back in the lobby without a party of players (kicked,
 * the party disbanded, its players gone). Its games count for the players (stats, XP), never in
 * the ELO; it has no stats of its own. Main thread, but {@link #isBot}, read from any thread.
 */
final class RagoBots implements Listener {

    /** The ids of the RagoBots online (and joining). */
    private static final Set<UUID> BOTS = ConcurrentHashMap.newKeySet();

    private final RagoLobby lobby;
    private final LobbyState state;
    private final PartyBook parties;
    /** The skin of an account, null until Mojang answered. */
    private final Function<String, Skin> skins;
    private final BotSettings settings;
    private final Map<UUID, Bot> bots = new LinkedHashMap<>();
    /** This server lacks what its body needs: no RagoBot until a restart. */
    private boolean broken;

    private static final class Bot {
        final BotBody body;
        final BotBrain brain;

        Bot(BotBody body, BotBrain brain) {
            this.body = body;
            this.brain = brain;
        }
    }

    RagoBots(RagoLobby lobby, BotSettings settings, LobbyState state, PartyBook parties, Function<String, Skin> skins) {
        this.lobby = lobby;
        this.settings = settings;
        this.state = state;
        this.parties = parties;
        this.skins = skins;
        if (settings.enabled) {
            // asked from Mojang at the start, so that the first RagoBot of the run wears it already
            skins.apply(settings.skin);
        }
    }

    /** True if this player is a RagoBot. Any thread. */
    static boolean isBot(UUID player) {
        return player != null && BOTS.contains(player);
    }

    static boolean isBot(Player player) {
        return player != null && BOTS.contains(player.getUniqueId());
    }

    /** True if one of these players is a RagoBot. */
    static boolean anyBot(Iterable<UUID> players) {
        for (UUID player : players) {
            if (isBot(player)) {
                return true;
            }
        }
        return false;
    }

    /** These players without the RagoBots. */
    static List<UUID> players(List<UUID> players) {
        List<UUID> humans = new ArrayList<>(players.size());
        for (UUID player : players) {
            if (!isBot(player)) {
                humans.add(player);
            }
        }
        return humans;
    }

    BotSettings settings() {
        return settings;
    }

    boolean enabled() {
        return settings.enabled;
    }

    /** The id of the RagoBot of this slot (1 to the max): the same at each start. */
    static UUID idOf(int slot) {
        return UUID.nameUUIDFromBytes((BotSettings.NAME + ":" + slot).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A new RagoBot, for this leader's party, next to them (at the lobby spawn if they are not in
     * the lobby). Null if none can come: they are told why.
     */
    Player join(Player leader) {
        if (broken) {
            Parties.boxed(leader, ChatColor.RED + BotSettings.NAME + " cannot play on this server right now.");
            return null;
        }
        UUID id = null;
        if (bots.size() < settings.max) {
            for (int slot = 1; slot <= BotSettings.MAX_BOTS && id == null; slot++) {
                UUID free = idOf(slot);
                if (!bots.containsKey(free) && !BOTS.contains(free) && Bukkit.getPlayer(free) == null) {
                    id = free;
                }
            }
        }
        if (id == null) {
            Parties.boxed(leader, ChatColor.RED + BotSettings.NAME + " already plays with " + settings.max
                    + " parties: try again in a moment.");
            return null;
        }
        Location at = state.isInLobby(leader) && !state.isInGame(leader) ? leader.getLocation() : state.spawn();
        BOTS.add(id);
        BotBody body;
        try {
            body = BotBody.login(id, BotSettings.NAME, skins.apply(settings.skin), at);
        } catch (InvocationTargetException e) {
            // the server's join itself failed: this time only
            lobby.getLogger().log(Level.SEVERE, BotSettings.NAME + " could not join the server", e.getCause());
            leave(id);
            Parties.boxed(leader, ChatColor.RED + BotSettings.NAME + " could not join: try again in a moment.");
            return null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            lobby.getLogger().log(Level.SEVERE, BotSettings.NAME + " cannot play on this server (its body is made with"
                    + " the classes of CraftBukkit 1.8.8): none until a restart", e);
            leave(id);
            Parties.boxed(leader, ChatColor.RED + BotSettings.NAME + " cannot play on this server right now.");
            return null;
        }
        bots.put(id, new Bot(body, new BotBrain(settings.skill, new Random())));
        if (!at.equals(state.spawn()) && body.player.isOnline()) {
            body.player.teleport(at);
        }
        lobby.getLogger().info(BotSettings.NAME + " joined for the party of " + leader.getName() + " ("
                + bots.size() + " online)");
        return body.player;
    }

    /** A RagoBot that could not join leaves, if it was there at all. */
    private void leave(UUID id) {
        Player half = Bukkit.getPlayer(id);
        if (half != null) {
            half.kickPlayer(BotSettings.NAME + " left");
        }
        BOTS.remove(id);
    }

    /** Every tick: each RagoBot plays its tick, as its client would send it. */
    void tick() {
        if (bots.isEmpty()) {
            return;
        }
        List<Player> failed = new ArrayList<>();
        for (Bot bot : new ArrayList<>(bots.values())) {
            Player player = bot.body.player;
            if (!player.isOnline()) {
                continue;
            }
            try {
                step(bot);
            } catch (ReflectiveOperationException | RuntimeException e) {
                lobby.getLogger().log(Level.SEVERE, BotSettings.NAME + " stopped: it leaves the server", e);
                failed.add(player);
            }
        }
        for (Player player : failed) {
            bots.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.kickPlayer(BotSettings.NAME + " stopped");
            }
        }
    }

    private void step(Bot bot) throws ReflectiveOperationException {
        BotBody body = bot.body;
        if (body.respawnIfDead()) {
            return;
        }
        boolean knocked = body.receive();
        Player player = body.player;
        Player target = target(player);
        BotBrain.Controls controls = bot.brain.think(sight(player, body, knocked, target));
        body.play(controls, target);
    }

    /**
     * The player it fights: the nearest of the other team(s) of its game, in its world, alive and
     * playing. None in the lobby, while it watches or lies dead in a round.
     */
    private Player target(Player bot) {
        UUID id = bot.getUniqueId();
        if (!state.isInGame(bot) || state.isWatching(id) || bot.getGameMode() == GameMode.SPECTATOR) {
            return null;
        }
        Location at = bot.getLocation();
        Player best = null;
        double nearest = Double.MAX_VALUE;
        for (UUID other : state.opponentsOf(id)) {
            Player player = Bukkit.getPlayer(other);
            if (player == null || player.isDead() || !player.getWorld().equals(bot.getWorld())
                    || player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(at);
            if (distance < nearest) {
                nearest = distance;
                best = player;
            }
        }
        return best;
    }

    private static BotBrain.Sight sight(Player player, BotBody body, boolean knocked, Player target)
            throws ReflectiveOperationException {
        BotBrain.Sight sight = new BotBrain.Sight();
        Location at = player.getLocation();
        sight.x = at.getX();
        sight.y = at.getY();
        sight.z = at.getZ();
        sight.yaw = at.getYaw();
        sight.pitch = at.getPitch();
        sight.onGround = player.isOnGround();
        sight.health = player.getHealth();
        sight.regenerating = player.hasPotionEffect(PotionEffectType.REGENERATION);
        sight.knocked = knocked;
        if (target != null) {
            Location there = target.getLocation();
            sight.fighting = true;
            sight.tx = there.getX();
            sight.ty = there.getY();
            sight.tz = there.getZ();
        }
        PlayerInventory inventory = player.getInventory();
        sight.held = inventory.getHeldItemSlot();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) {
                continue;
            }
            if (sight.sword < 0 && item.getType().name().endsWith("_SWORD")) {
                sight.sword = slot;
            }
            if (sight.gapple < 0 && item.getType() == Material.GOLDEN_APPLE) {
                sight.gapple = slot;
            }
        }
        sight.eating = body.eating();
        final World world = player.getWorld();
        sight.ground = (x, y, z) -> world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType().isSolid();
        return sight;
    }

    /**
     * Once a second: a RagoBot back in the lobby without a party of players (kicked from it, the
     * party disbanded, its players gone) leaves the server. In a game, it plays it to the end first.
     */
    void second() {
        for (Bot bot : new ArrayList<>(bots.values())) {
            Player player = bot.body.player;
            if (!player.isOnline() || state.isInGame(player) || !state.isInLobby(player)) {
                continue;
            }
            PartyBook.Party party = parties.of(player.getUniqueId());
            if (party == null || !hasPlayer(party)) {
                player.kickPlayer(BotSettings.NAME + " left");
            }
        }
    }

    /** True if someone else than a RagoBot is in this party. */
    static boolean hasPlayer(PartyBook.Party party) {
        for (UUID member : party.members()) {
            if (!isBot(member)) {
                return true;
            }
        }
        return false;
    }

    /** The RagoBot of this party, or null. */
    static UUID botOf(PartyBook.Party party) {
        if (party == null) {
            return null;
        }
        for (UUID member : party.members()) {
            if (isBot(member)) {
                return member;
            }
        }
        return null;
    }

    /** Gone from the server: forgotten, once the lobby has forgotten it too (the next tick). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        final UUID id = event.getPlayer().getUniqueId();
        if (!BOTS.contains(id)) {
            return;
        }
        bots.remove(id);
        forgetChannel(id);
        if (lobby.isEnabled()) {
            Bukkit.getScheduler().runTask(lobby, () -> {
                if (Bukkit.getPlayer(id) == null) {
                    BOTS.remove(id);
                }
            });
        } else {
            BOTS.remove(id);
        }
    }

    /** PacketEvents keeps the channel of each player it met: the bot's, closed, is no longer it. */
    private static void forgetChannel(UUID id) {
        try {
            PacketEvents.getAPI().getProtocolManager().removeChannelById(id);
        } catch (RuntimeException | LinkageError e) {
            // PacketEvents gone already (the server stops)
        }
    }

    /** Every RagoBot leaves the server (the plugin stops). */
    void removeAll() {
        for (Bot bot : new ArrayList<>(bots.values())) {
            if (bot.body.player.isOnline()) {
                bot.body.player.kickPlayer(BotSettings.NAME + " left");
            }
        }
        bots.clear();
        BOTS.clear();
    }

    /** RagoBots online now. */
    int online() {
        return bots.size();
    }
}
