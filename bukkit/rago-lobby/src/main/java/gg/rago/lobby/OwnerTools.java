package gg.rago.lobby;

import gg.rago.game.GameMode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The Owner's test tools (plan 7.1, outils-owner: of config.yml), to test the PvP and the
 * protections of the server: Reach (the Owner's hits land up to 6 blocks away, as a reach cheat's)
 * and Scaffold (a block of the one in hand goes under the Owner's feet as they walk, as a scaffold
 * cheat's). For the account of outils-owner only: anyone else is told the command does not exist.
 * Each tool is off until the Owner turns it on (/owner), and off again once they leave the server.
 * Never in a ranked game: there they do nothing. A game where the Owner has a tool on counts for
 * nobody: no stats, no XP, no ELO, no record. Main thread.
 */
final class OwnerTools implements Listener, CommandExecutor, TabCompleter {

    /** What a player who is not the Owner reads: what Spigot answers for a command it does not know. */
    static final String UNKNOWN = "Unknown command. Type \"/help\" for help.";

    /** outils-owner: of config.yml. Immutable. */
    static final class Settings {
        /** The Owner's account (qRago); empty: no tool for anyone. */
        final String account;
        /** Their id, for a server that does not verify names (offline mode); null: their name, verified. */
        final UUID uuid;

        Settings(String account, UUID uuid) {
            this.account = account;
            this.uuid = uuid;
        }

        static Settings read(ConfigurationSection section) {
            if (section == null) {
                return new Settings("qRago", null);
            }
            String account = section.getString("compte", "qRago").trim();
            if (!account.isEmpty() && !AccountSkins.validName(account)) {
                throw new IllegalArgumentException("outils-owner.compte: the name of a Minecraft account, as qRago");
            }
            String id = section.getString("uuid", "").trim();
            UUID uuid = null;
            if (!id.isEmpty()) {
                try {
                    uuid = UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("outils-owner.uuid: an id as 069a79f4-44e9-4726-a5be-fca90e38aaf5, or \"\"");
                }
            }
            return new Settings(account, uuid);
        }
    }

    private final RagoLobby lobby;
    private final LobbyState state;
    private final Settings settings;
    /** The Owner's reach, while Reach is on. */
    private final Map<UUID, Double> reach = new HashMap<>();
    private final Set<UUID> scaffold = new HashSet<>();
    /** CraftPlayer.getHandle, CraftEntity.getHandle, EntityHuman.attack: a hit as the server makes one. */
    private Method playerHandle;
    private Method entityHandle;
    private Method attack;
    private boolean noAttack;

    OwnerTools(RagoLobby lobby, LobbyState state, Settings settings) {
        this.lobby = lobby;
        this.state = state;
        this.settings = settings;
        if (!settings.account.isEmpty() && settings.uuid == null && !namesVerified()) {
            lobby.getLogger().warning("outils-owner: this server does not verify the names (offline mode): the Owner"
                    + " tools stay off until outils-owner.uuid gives the id of " + settings.account);
        }
    }

    /** True if the server knows a name is its player's: online mode, or behind BungeeCord, which checks it. */
    static boolean namesVerified() {
        if (Bukkit.getOnlineMode()) {
            return true;
        }
        try {
            return Bukkit.getServer().spigot().getConfig().getBoolean("settings.bungeecord", false);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    boolean isOwner(Player player) {
        return OwnerRules.isOwner(settings.account, settings.uuid, player.getName(), player.getUniqueId(), namesVerified());
    }

    // --- /owner ---------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !isOwner((Player) sender)) {
            sender.sendMessage(UNKNOWN);
            return true;
        }
        Player owner = (Player) sender;
        UUID id = owner.getUniqueId();
        String what = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (what.equals("reach") && args.length == 2) {
            if (args[1].equalsIgnoreCase("off")) {
                reach.remove(id);
                owner.sendMessage(ChatColor.GRAY + "Reach " + ChatColor.RED + "off" + ChatColor.GRAY + ".");
                return true;
            }
            double blocks = OwnerRules.reach(args[1]);
            if (Double.isNaN(blocks)) {
                owner.sendMessage(ChatColor.RED + "Reach: from " + OwnerRules.blocks(OwnerRules.FAIR_REACH) + " to "
                        + OwnerRules.blocks(OwnerRules.MAX_REACH) + " blocks (/owner reach 4.5), or /owner reach off.");
                return true;
            }
            reach.put(id, blocks);
            turnedOn(owner, "Reach " + OwnerRules.blocks(blocks) + " blocks");
            return true;
        }
        if (what.equals("scaffold") && args.length <= 2) {
            boolean on = args.length == 2 ? args[1].equalsIgnoreCase("on") : !scaffold.contains(id);
            if (args.length == 2 && !on && !args[1].equalsIgnoreCase("off")) {
                status(owner);
                return true;
            }
            if (on) {
                scaffold.add(id);
                turnedOn(owner, "Scaffold (hold the blocks to place)");
            } else {
                scaffold.remove(id);
                owner.sendMessage(ChatColor.GRAY + "Scaffold " + ChatColor.RED + "off" + ChatColor.GRAY + ".");
            }
            return true;
        }
        if (what.equals("off") && args.length == 1) {
            reach.remove(id);
            scaffold.remove(id);
            owner.sendMessage(ChatColor.GRAY + "Owner tools " + ChatColor.RED + "off" + ChatColor.GRAY + ".");
            return true;
        }
        status(owner);
        return true;
    }

    private void turnedOn(Player owner, String tool) {
        owner.sendMessage(ChatColor.GRAY + tool + ": " + ChatColor.GREEN + "on" + ChatColor.GRAY + ".");
        if (inRanked(owner.getUniqueId())) {
            owner.sendMessage(ChatColor.RED + "Never in a ranked game: it does nothing here.");
        } else if (state.gameOf(owner.getUniqueId()) != null) {
            markGame(owner.getUniqueId());
            owner.sendMessage(ChatColor.YELLOW + "This game counts for nobody now (no stats, no XP, no ELO).");
        }
    }

    private void status(Player owner) {
        Double blocks = reach.get(owner.getUniqueId());
        owner.sendMessage(ChatColor.GOLD + "Owner tools" + ChatColor.GRAY + ": Reach "
                + (blocks == null ? ChatColor.RED + "off" : ChatColor.GREEN + OwnerRules.blocks(blocks) + " blocks")
                + ChatColor.GRAY + ", Scaffold " + (scaffold.contains(owner.getUniqueId()) ? ChatColor.GREEN + "on"
                : ChatColor.RED + "off") + ChatColor.GRAY + ".");
        owner.sendMessage(ChatColor.GRAY + "/owner reach <" + OwnerRules.blocks(OwnerRules.FAIR_REACH) + "-"
                + OwnerRules.blocks(OwnerRules.MAX_REACH) + "|off>, /owner scaffold [on|off], /owner off. Never in a"
                + " ranked game; a game with a tool on counts for nobody.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !isOwner((Player) sender)) {
            return Collections.emptyList();
        }
        List<String> options = args.length == 1 ? Arrays.asList("reach", "scaffold", "off")
                : args.length == 2 && args[0].equalsIgnoreCase("reach") ? Arrays.asList("4", "4.5", "5", "6", "off")
                : args.length == 2 && args[0].equalsIgnoreCase("scaffold") ? Arrays.asList("on", "off")
                : Collections.<String>emptyList();
        List<String> matching = new ArrayList<>();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.startsWith(typed)) {
                matching.add(option);
            }
        }
        return matching;
    }

    // --- The rules ------------------------------------------------------------------------------

    /** True if the player plays a ranked mode now. */
    private boolean inRanked(UUID player) {
        String modeId = state.gameOf(player);
        GameMode mode = modeId == null ? null : lobby.mode(modeId);
        return mode != null && mode.ranked();
    }

    private boolean anyOn(UUID player) {
        return reach.containsKey(player) || scaffold.contains(player);
    }

    /** The Owner's game, and everyone in it, counts for nobody. */
    private void markGame(UUID owner) {
        state.markOwnerTool(owner);
        for (UUID player : state.togetherWith(owner)) {
            state.markOwnerTool(player);
        }
    }

    /** Once a second: the game of an Owner with a tool on counts for nobody (a game started since). */
    void second() {
        for (UUID owner : new ArrayList<>(unionOfOn())) {
            if (state.gameOf(owner) != null && !inRanked(owner)) {
                markGame(owner);
            }
        }
    }

    private Set<UUID> unionOfOn() {
        Set<UUID> on = new HashSet<>(reach.keySet());
        on.addAll(scaffold);
        return on;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reach.remove(event.getPlayer().getUniqueId());
        scaffold.remove(event.getPlayer().getUniqueId());
    }

    // --- Reach ----------------------------------------------------------------------------------

    /**
     * A swing of the Owner with Reach on: the first player along their aim, past a fair reach and
     * within theirs, with no block between, takes the hit, as the server makes one (the game's
     * rules, the knockback of the server).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player owner = event.getPlayer();
        Double blocks = reach.get(owner.getUniqueId());
        if (blocks == null || inRanked(owner.getUniqueId()) || owner.isDead()
                || owner.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        Location eyes = owner.getEyeLocation();
        Vector way = eyes.getDirection();
        Player target = null;
        double nearest = Double.MAX_VALUE;
        for (Player other : owner.getWorld().getPlayers()) {
            if (other.equals(owner) || other.isDead() || other.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || !owner.canSee(other)) {
                continue;
            }
            Location at = other.getLocation();
            double distance = OwnerRules.hit(eyes.getX(), eyes.getY(), eyes.getZ(), way.getX(), way.getY(), way.getZ(),
                    at.getX(), at.getY(), at.getZ());
            if (distance >= 0 && distance <= blocks && distance < nearest) {
                nearest = distance;
                target = other;
            }
        }
        if (target == null || nearest <= OwnerRules.FAIR_REACH || blocked(eyes, way, nearest)) {
            // nobody, or within a fair reach: the client hits them itself
            return;
        }
        if (hit(owner, target) && state.gameOf(owner.getUniqueId()) != null) {
            markGame(owner.getUniqueId());
        }
    }

    /** True if a solid block stands on the aim before this distance. */
    private static boolean blocked(Location eyes, Vector way, double distance) {
        World world = eyes.getWorld();
        double step = 0.1;
        int lastX = Integer.MIN_VALUE;
        int lastY = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (double along = 0; along < distance; along += step) {
            int x = (int) Math.floor(eyes.getX() + way.getX() * along);
            int y = (int) Math.floor(eyes.getY() + way.getY() * along);
            int z = (int) Math.floor(eyes.getZ() + way.getZ() * along);
            if (x == lastX && y == lastY && z == lastZ) {
                continue;
            }
            lastX = x;
            lastY = y;
            lastZ = z;
            if (!world.isChunkLoaded(x >> 4, z >> 4) || world.getBlockAt(x, y, z).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    /** The Owner hits this player as the server makes a player's hit. */
    private boolean hit(Player owner, Player target) {
        if (noAttack) {
            return false;
        }
        try {
            if (attack == null) {
                playerHandle = owner.getClass().getMethod("getHandle");
                Object handle = playerHandle.invoke(owner);
                entityHandle = target.getClass().getMethod("getHandle");
                Object other = entityHandle.invoke(target);
                for (Method method : handle.getClass().getMethods()) {
                    if (method.getName().equals("attack") && method.getParameterTypes().length == 1
                            && method.getParameterTypes()[0].isAssignableFrom(other.getClass())) {
                        attack = method;
                        break;
                    }
                }
                if (attack == null) {
                    throw new NoSuchMethodException("EntityHuman.attack");
                }
            }
            attack.invoke(playerHandle.invoke(owner), entityHandle.invoke(target));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            noAttack = true;
            lobby.getLogger().log(Level.WARNING, "outils-owner: Reach cannot hit on this server (it needs CraftBukkit 1.8)", e);
            return false;
        }
    }

    // --- Scaffold -------------------------------------------------------------------------------

    /**
     * A step of the Owner with Scaffold on, holding a block: under their feet, a block of it, as a
     * player places one (against the block beside it), if the game lets them place it there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player owner = event.getPlayer();
        if (!scaffold.contains(owner.getUniqueId()) || inRanked(owner.getUniqueId()) || owner.isDead()) {
            return;
        }
        org.bukkit.GameMode mode = owner.getGameMode();
        if (mode != org.bukkit.GameMode.SURVIVAL && mode != org.bukkit.GameMode.CREATIVE) {
            // the lobby (adventure), a spectator: nobody places a block there
            return;
        }
        if (!owner.isOnGround()) {
            // on the ground the step before (the move is not applied yet): walking off an edge,
            // not a jump or a fall, which would pile blocks up
            return;
        }
        ItemStack hand = owner.getItemInHand();
        if (hand == null || hand.getAmount() <= 0 || !hand.getType().isBlock() || !hand.getType().isSolid()) {
            return;
        }
        Location to = event.getTo();
        final World world = to.getWorld();
        int[] spot = OwnerRules.scaffold(to.getX(), to.getY(), to.getZ(),
                (x, y, z) -> world.isChunkLoaded(x >> 4, z >> 4) && world.getBlockAt(x, y, z).getType().isSolid());
        if (spot == null || spot[1] < 0 || spot[1] > 255) {
            return;
        }
        Block block = world.getBlockAt(spot[0], spot[1], spot[2]);
        if (block.getType() != Material.AIR && !block.isLiquid()) {
            return;
        }
        for (Player other : world.getPlayers()) {
            if (!other.equals(owner) && inside(other.getLocation(), spot)) {
                return;
            }
        }
        place(owner, hand, block, world.getBlockAt(spot[3], spot[4], spot[5]));
    }

    /** True if a player standing there is in the block (a box 0.6 wide, 1.8 high). */
    private static boolean inside(Location at, int[] block) {
        return at.getX() + 0.3 > block[0] && at.getX() - 0.3 < block[0] + 1 && at.getZ() + 0.3 > block[2]
                && at.getZ() - 0.3 < block[2] + 1 && at.getY() + 1.8 > block[1] && at.getY() < block[1] + 1;
    }

    /** As the server places a player's block: put there, then the event, taken back if refused. */
    @SuppressWarnings("deprecation")
    private void place(Player owner, ItemStack hand, Block block, Block against) {
        BlockState replaced = block.getState();
        block.setTypeIdAndData(hand.getTypeId(), (byte) hand.getDurability(), true);
        BlockPlaceEvent place = new BlockPlaceEvent(block, replaced, against, hand, owner, true);
        Bukkit.getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) {
            replaced.update(true, false);
            return;
        }
        if (owner.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (hand.getAmount() <= 1) {
                owner.setItemInHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                owner.setItemInHand(hand);
            }
        }
        if (state.gameOf(owner.getUniqueId()) != null) {
            markGame(owner.getUniqueId());
        }
    }
}
