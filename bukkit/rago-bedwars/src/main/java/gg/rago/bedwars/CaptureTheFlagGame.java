package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Capture the Flag (plan 6.2), in its two kinds, the owner's two of 22/11: « 1. The Bridge 2. type
 * normal avec une map assez grande type Château vs Château et 1 mid ».
 *
 * <p>Each team has its flag, a banner of its colour on gold at the back of its base (the bed point
 * of the map). An enemy who walks onto it takes it: it goes on their head, and everyone hears it.
 * They score by bringing it to their own flag, while that one is at home; the flag they carried
 * goes back to its base. A carrier who dies drops the flag where they fell, as a banner floating
 * there (into the void: it goes back at once); an enemy who walks onto it takes it again, one of its
 * team brings it back home, and after the seconds of ctf.retour-drapeau-secondes it goes back by
 * itself. Nobody is ever out: everyone comes back after each death, there is no bed. The first team
 * to the captures of ctf.captures wins; at the end of the time, the one with the most, a draw when
 * they have as many. A carrier who leaves the game: the flag goes back. Nobody builds on a flag.
 *
 * <ul>
 *   <li><b>The Bridge</b> (kind ctf-bridge, map CtfBridge): two bases joined by a bridge of clay
 *       that breaks, as in Hypixel's The Bridge, and its kit (the Hypixel wiki): leather armour of
 *       the team without the cap, an iron sword, a bow and one arrow that comes back
 *       {@value #ARROW_MILLIS} ms after being shot, a diamond pickaxe with Efficiency II, two
 *       stacks of clay of the team and 8 golden apples, which heal at once and give two hearts of
 *       absorption; the mode's kit as each player arranged it in the lobby, kept in place from a life
 *       to the next. No forge, no shop; back at once after a death. ctf.minutes-bridge minutes.</li>
 *   <li><b>Castle</b> (kind ctf-chateau, map CtfCastle): a game of Bed Wars, with its forge, its
 *       shop and its upgrades in each castle, the diamonds and emeralds in the middle, its armour
 *       and tools; back after the server's seconds. ctf.minutes-chateau minutes.</li>
 * </ul>
 *
 * <p>The captures to win, the times (ctf: in config.yml) and the kit are starting values, to confirm
 * with the owner.
 * Main thread only.
 */
final class CaptureTheFlagGame extends BedwarsGame {

    /**
     * The kit of The Bridge in the first slots of the hotbar, when the lobby has none for the mode
     * (the clay in the team's colour); its arrow in {@link #ARROW_SLOT}.
     */
    static final String[] BRIDGE_KIT = {"IRON_SWORD", "BOW", "DIAMOND_PICKAXE", "STAINED_CLAY 64", "STAINED_CLAY 64",
        "GOLDEN_APPLE 8"};
    /** The slot of the arrow of The Bridge: the last of the hotbar. */
    static final int ARROW_SLOT = 8;
    /** The arrow of The Bridge comes back this long after it was shot. */
    static final long ARROW_MILLIS = 3_500L;
    /** A carrier at their own base while their flag is away hears it once in this long. */
    private static final long NOT_HOME_EVERY_MS = 3_000L;

    /** The Bridge (true) or the castles (false). */
    private final boolean bridge;
    private final CaptureFlags flags;
    /** How long a game lasts at most. */
    private final int minutes;
    /** For each player of The Bridge, where they had put each item of the kit at their last death. */
    private final Map<UUID, Material[]> layouts = new HashMap<>();
    /** Since when a player of The Bridge has had no arrow. */
    private final Map<UUID, Long> arrowGone = new HashMap<>();
    /** Where a player of The Bridge last had their arrow: it comes back there. */
    private final Map<UUID, Integer> arrowSlot = new HashMap<>();
    private final Map<UUID, Long> toldNotHome = new HashMap<>();
    private final Map<UUID, Integer> captures = new HashMap<>();
    /** The banners of the dropped flags, by team. */
    private final Map<Integer, ArmorStand> dropped = new HashMap<>();
    /** The name over each flag's base, by team. */
    private final Map<Integer, ArmorStand> names = new HashMap<>();

    CaptureTheFlagGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides, boolean bridge) {
        super(plugin, host, instance, modeId, modeName, sides);
        this.bridge = bridge;
        BedwarsSettings.Ctf ctf = settings.ctf();
        this.flags = new CaptureFlags(teams.size(), ctf.captures, ctf.returnSeconds);
        this.minutes = bridge ? ctf.bridgeMinutes : ctf.castleMinutes;
    }

    /** The Bridge: no forge, no shop. The castles: a game of Bed Wars. */
    @Override
    boolean economy() {
        return !bridge;
    }

    @Override
    boolean upgrades() {
        return !bridge;
    }

    /** The Bridge: back at once; the castles: after the server's seconds. */
    @Override
    int respawnSeconds() {
        return bridge ? 0 : super.respawnSeconds();
    }

    /** No bed: everyone comes back after each death. */
    @Override
    boolean finalDeath(Gamer gamer) {
        return false;
    }

    /** The Bridge: the clay of the map (its bridge) breaks, as in The Bridge; the bases do not. */
    @Override
    boolean breaksMap(Block block) {
        return bridge && block.getType() == Material.STAINED_CLAY && block.getY() >= instance.map().voidY()
                && cannotBuildAt(block) == null;
    }

    int minutes() {
        return minutes;
    }

    /** Nobody builds on a flag's base (its block and the one over it), so that the flag can always go back. */
    @Override
    String cannotBuildAt(Block block) {
        for (int team = 0; team < teams.size(); team++) {
            Block home = home(team);
            if (onFlag(block.getX() - home.getX(), block.getY() - home.getY(), block.getZ() - home.getZ())) {
                return "You can't place blocks on a flag!";
            }
        }
        return super.cannotBuildAt(block);
    }

    /** True for the block of a flag and the one over it, from this offset to the flag's block. */
    static boolean onFlag(int dx, int dy, int dz) {
        return dx == 0 && dz == 0 && (dy == 0 || dy == 1);
    }

    // --- The flags ------------------------------------------------------------------------------

    /** Each flag at its base, its name over it. */
    @Override
    void setUp() {
        for (int i = 0; i < teams.size(); i++) {
            putHome(i);
            Block block = home(i);
            names.put(i, label(block.getLocation().add(0.5, 2.6, 0.5), flagName(i)));
        }
    }

    private Block home(int team) {
        return teams.get(team).points.bed.blockIn(instance.world());
    }

    String flagName(int team) {
        Team owner = teams.get(team);
        return owner.color.chat() + owner.color.displayName() + " Flag";
    }

    @SuppressWarnings("deprecation") // DyeColor#getByWoolData: the colour of the team's wool in 1.8
    static DyeColor dye(TeamColor color) {
        return DyeColor.getByWoolData((byte) color.woolData());
    }

    /** The banner of a flag, standing, its face towards the team's spawn (0 south, 4 west, 8 north, 12 east). */
    static int rotation(float yaw) {
        return Math.round(yaw / 22.5f) & 15;
    }

    private void putHome(int team) {
        Block block = home(team);
        Team owner = teams.get(team);
        float yaw = owner.points.spawn == null ? 0f : owner.points.spawn.yaw();
        instance.build(block, Material.STANDING_BANNER, (byte) rotation(yaw));
        BlockState state = block.getState();
        if (state instanceof Banner) {
            ((Banner) state).setBaseColor(dye(owner.color));
            state.update(true, false);
        }
        rename(team);
    }

    private void takeHome(int team) {
        instance.build(home(team), Material.AIR, (byte) 0);
        rename(team);
    }

    private void rename(int team) {
        ArmorStand name = names.get(team);
        if (name != null && name.isValid()) {
            CaptureFlags.State state = flags.flag(team).state;
            name.setCustomName(flagName(team) + (state == CaptureFlags.State.HOME ? ""
                    : ChatColor.GRAY + (state == CaptureFlags.State.CARRIED ? " - taken" : " - dropped")));
        }
    }

    /** The banner of a flag as an item: on the head of its carrier, on the stand of a dropped flag. */
    @SuppressWarnings("deprecation") // DyeColor#getDyeData: the item's own colour in 1.8
    ItemStack bannerOf(int team) {
        Team owner = teams.get(team);
        DyeColor dye = dye(owner.color);
        ItemStack item = new ItemStack(Material.BANNER, 1, dye.getDyeData());
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BannerMeta) {
            ((BannerMeta) meta).setBaseColor(dye);
        }
        if (meta != null) {
            meta.setDisplayName(flagName(team));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The armour of the mode, the flag on the head of its carrier; The Bridge has no cap. */
    @Override
    void wearArmor(Player player, Gamer gamer) {
        super.wearArmor(player, gamer);
        int carried = flags.carriedBy(gamer.id);
        if (carried >= 0) {
            player.getInventory().setHelmet(bannerOf(carried));
        } else if (bridge) {
            player.getInventory().setHelmet(null);
        }
    }

    /** Four times a second: who touches which flag, the arrow of The Bridge. */
    @Override
    void quarter() {
        long now = System.currentTimeMillis();
        for (Gamer gamer : new ArrayList<>(gamers.values())) {
            if (phase != Phase.PLAYING) {
                return;
            }
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || gamer.eliminated || gamer.respawning || !present(gamer)
                    || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (flags.carriedBy(gamer.id) >= 0 && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                // the flag on the head shows where its carrier is: never hidden by a potion
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.sendMessage(ChatColor.RED + "Nobody is invisible while carrying a flag!");
            }
            Location at = player.getLocation();
            for (int team = 0; team < teams.size(); team++) {
                CaptureFlags.Flag flag = flags.flag(team);
                double fx;
                double fy;
                double fz;
                if (flag.state == CaptureFlags.State.HOME) {
                    Block block = home(team);
                    fx = block.getX() + 0.5;
                    fy = block.getY();
                    fz = block.getZ() + 0.5;
                } else if (flag.state == CaptureFlags.State.DROPPED) {
                    fx = flag.x;
                    fy = flag.y;
                    fz = flag.z;
                } else {
                    continue;
                }
                if (CaptureFlags.touches(at.getX() - fx, at.getY() - fy, at.getZ() - fz)) {
                    touched(player, gamer, team, now);
                    if (phase != Phase.PLAYING) {
                        // that capture won the game
                        return;
                    }
                }
            }
            if (bridge) {
                arrow(player, gamer, now);
            }
        }
    }

    private void touched(Player player, Gamer gamer, int team, long now) {
        int mine = teams.indexOf(gamer.team);
        CaptureFlags.State before = flags.flag(team).state;
        int carried = flags.carriedBy(gamer.id);
        switch (flags.touch(mine, gamer.id, team)) {
            case TAKEN:
                if (before == CaptureFlags.State.HOME) {
                    takeHome(team);
                } else {
                    removeDropped(team);
                }
                rename(team);
                player.getInventory().setHelmet(bannerOf(team));
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                broadcast(gamer.coloured() + ChatColor.YELLOW + " took the " + flagName(team) + ChatColor.YELLOW + "!",
                        Sound.ENDERDRAGON_HIT);
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "YOU HAVE THE FLAG!",
                        ChatColor.WHITE + "Bring it to your own flag");
                for (UUID member : teams.get(team).members) {
                    Player owner = Bukkit.getPlayer(member);
                    if (owner != null && present(gamers.get(member))) {
                        owner.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "FLAG TAKEN!",
                                ChatColor.WHITE + "Kill " + gamer.name + " to get it back");
                    }
                }
                sidebars();
                break;
            case RETURNED:
                removeDropped(team);
                putHome(team);
                broadcast(gamer.coloured() + ChatColor.YELLOW + " returned the " + flagName(team) + ChatColor.YELLOW
                        + " to its base!", Sound.ORB_PICKUP);
                sidebars();
                break;
            case CAPTURED:
                putHome(carried);
                wearArmor(player, gamer);
                captures.merge(gamer.id, 1, Integer::sum);
                broadcast(gamer.coloured() + ChatColor.YELLOW + " captured the " + flagName(carried) + ChatColor.YELLOW
                        + "! " + score(), Sound.LEVEL_UP);
                ding(gamer.team);
                title(gamer.team.color.chat() + "" + ChatColor.BOLD + gamer.team.color.displayName().toUpperCase(Locale.ROOT)
                        + " SCORES!", score());
                sidebars();
                int winner = flags.winner();
                if (winner >= 0) {
                    end(teams.get(winner));
                }
                break;
            case NOT_HOME:
                Long told = toldNotHome.get(gamer.id);
                if (told == null || now - told >= NOT_HOME_EVERY_MS) {
                    toldNotHome.put(gamer.id, now);
                    player.sendMessage(ChatColor.RED + "Your flag must be at your base for you to score!");
                }
                break;
            default:
                break;
        }
    }

    /** "Red 1 - 0 Blue". */
    String score() {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            parts.add(teams.get(i).color.chat() + teams.get(i).color.displayName() + " " + flags.score(i));
        }
        return String.join(ChatColor.GRAY + " - ", parts);
    }

    /** A carrier died: the flag lies where they fell, or goes back to its base from the void. */
    private void drop(Gamer gamer, Location at) {
        int team = flags.carriedBy(gamer.id);
        if (team < 0) {
            return;
        }
        Location ground = groundUnder(at);
        if (ground == null) {
            flags.home(team);
            putHome(team);
            broadcast(ChatColor.YELLOW + "The " + flagName(team) + ChatColor.YELLOW + " went back to its base.", null);
            return;
        }
        flags.drop(team, ground.getX(), ground.getY(), ground.getZ(), seconds);
        ArmorStand stand = ground.getWorld().spawn(ground, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setHelmet(bannerOf(team));
        stand.setCustomName(flagName(team));
        stand.setCustomNameVisible(true);
        try {
            // a marker has no box: nobody can hit it or take the banner off it
            ArmorStand.class.getMethod("setMarker", boolean.class).invoke(stand, true);
        } catch (ReflectiveOperationException e) {
            // an older server: a little lower, the same
        }
        dropped.put(team, stand);
        rename(team);
        broadcast(gamer.coloured() + ChatColor.YELLOW + " dropped the " + flagName(team) + ChatColor.YELLOW + "! It goes back"
                + " to its base in " + flags.returnSeconds + " seconds.", null);
    }

    /**
     * Where a flag dropped here lies: on the first block that holds a player, at the feet or up to
     * 4 blocks under them; null over the void.
     */
    private Location groundUnder(Location at) {
        World world = at.getWorld();
        int fall = (int) Math.floor(fallLine());
        for (int y = at.getBlockY(); y >= at.getBlockY() - 4 && y > fall; y--) {
            Block feet = world.getBlockAt(at.getBlockX(), y, at.getBlockZ());
            if (feet.getType().isSolid()) {
                continue;
            }
            if (feet.getRelative(0, -1, 0).getType().isSolid()) {
                return new Location(world, at.getBlockX() + 0.5, y, at.getBlockZ() + 0.5);
            }
        }
        return null;
    }

    private void removeDropped(int team) {
        ArmorStand stand = dropped.remove(team);
        if (stand != null) {
            stand.remove();
        }
    }

    /** Every death: the carrier drops the flag; in The Bridge, where the player had put the items. */
    @Override
    void beforeDeath(Player victim, Gamer gamer) {
        drop(gamer, victim.getLocation());
        if (bridge) {
            layouts.put(gamer.id, BedFightGame.layoutOf(victim.getInventory().getContents(), layouts.get(gamer.id)));
        }
    }

    /** A carrier who leaves: the flag goes back to its base. */
    @Override
    void left(Gamer gamer) {
        int team = flags.carriedBy(gamer.id);
        if (team >= 0) {
            flags.home(team);
            putHome(team);
            broadcast(ChatColor.YELLOW + "The " + flagName(team) + ChatColor.YELLOW + " went back to its base.", null);
        }
    }

    /** Once a second: the dropped flags that waited long enough go back; their banners show the time left. */
    @Override
    void everySecond() {
        for (int team : flags.expired(seconds)) {
            removeDropped(team);
            flags.home(team);
            putHome(team);
            broadcast(ChatColor.YELLOW + "The " + flagName(team) + ChatColor.YELLOW + " went back to its base.", Sound.ORB_PICKUP);
        }
        for (Map.Entry<Integer, ArmorStand> entry : dropped.entrySet()) {
            entry.getValue().setCustomName(flagName(entry.getKey()) + ChatColor.GRAY + " "
                    + flags.secondsLeft(entry.getKey(), seconds) + "s");
        }
    }

    /** The generators go up a tier as in Bed Wars (the castles); no end of the beds; the end at the time of the mode. */
    @Override
    void events() {
        if (!bridge && seconds != settings.bedsGone() && seconds < settings.gameEnd()) {
            super.events();
        }
        if (phase == Phase.PLAYING && seconds >= minutes() * 60) {
            int leader = flags.leader();
            broadcast(ChatColor.YELLOW + "Time is up! " + score(), Sound.NOTE_BASS);
            end(leader < 0 ? null : teams.get(leader));
        }
    }

    // --- The kit of The Bridge -----------------------------------------------------------------

    /** The Bridge: the mode's kit at each life, where the player had put it; the castles: Bed Wars'. */
    @Override
    void giveKit(Player player, Gamer gamer) {
        if (!bridge) {
            super.giveKit(player, gamer);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack[] kit = lobbyKit(player, gamer);
        if (kit != null) {
            inventory.setContents(kit);
        } else {
            ItemStack[] items = BedFightGame.items(BRIDGE_KIT);
            for (int slot = 0; slot < items.length; slot++) {
                inventory.setItem(slot, kitItem(items[slot], gamer));
            }
            inventory.setItem(ARROW_SLOT, new ItemStack(Material.ARROW));
        }
        ItemStack[] contents = inventory.getContents();
        for (ItemStack item : contents) {
            if (item != null && item.getType() == Material.DIAMOND_PICKAXE && item.getEnchantmentLevel(Enchantment.DIG_SPEED) < 2) {
                item.addUnsafeEnchantment(Enchantment.DIG_SPEED, 2);
            }
        }
        inventory.setContents(BedFightGame.arranged(contents, layouts.get(gamer.id)));
        arrowGone.remove(gamer.id);
        player.updateInventory();
    }

    /** The one arrow of The Bridge: back {@value #ARROW_MILLIS} ms after it was shot; never more than one. */
    private void arrow(Player player, Gamer gamer, long now) {
        PlayerInventory inventory = player.getInventory();
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == Material.ARROW) {
                count += item.getAmount();
            }
        }
        if (count > 1) {
            inventory.remove(Material.ARROW);
            inventory.addItem(new ItemStack(Material.ARROW));
            return;
        }
        if (count == 1) {
            arrowGone.remove(gamer.id);
            arrowSlot.put(gamer.id, inventory.first(Material.ARROW));
            return;
        }
        Long gone = arrowGone.get(gamer.id);
        if (gone == null) {
            arrowGone.put(gamer.id, now);
        } else if (arrowBack(gone, now)) {
            arrowGone.remove(gamer.id);
            Integer slot = arrowSlot.get(gamer.id);
            if (slot != null && slot >= 0 && inventory.getItem(slot) == null) {
                inventory.setItem(slot, new ItemStack(Material.ARROW));
            } else {
                inventory.addItem(new ItemStack(Material.ARROW));
            }
        }
    }

    static boolean arrowBack(long goneAt, long now) {
        return now - goneAt >= ARROW_MILLIS;
    }

    /** The golden apple of The Bridge: health full at once, two hearts of absorption (24 HP in all). */
    @Override
    void ate(Player player, ItemStack item) {
        if (!bridge || item.getType() != Material.GOLDEN_APPLE) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || host.gameOf(player.getUniqueId()) != this) {
                return;
            }
            player.removePotionEffect(PotionEffectType.REGENERATION);
            player.setHealth(player.getMaxHealth());
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0), true);
        });
    }

    // --- What the players read ------------------------------------------------------------------

    @Override
    void welcome(Player player, Gamer gamer) {
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on " + ChatColor.WHITE
                + instance.map().world() + ChatColor.GREEN + ": you are in the " + gamer.team.color.coloured()
                + ChatColor.GREEN + " team. Take the enemy flag and bring it to your own, at your base.");
        player.sendMessage(ChatColor.GRAY + "Walk onto a flag to take it. You score while your flag is at home; a dropped"
                + " flag goes back after " + flags.returnSeconds + " s, or when one of its team touches it. First to "
                + flags.captures + " captures, " + minutes() + " minutes at most. You always come back after a death."
                + (bridge ? " The bridge breaks." : ""));
    }

    @Override
    List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        int left = Math.max(0, minutes() * 60 - seconds);
        lines.add(phase == Phase.STARTING ? ChatColor.WHITE + "Starting..."
                : ChatColor.WHITE + "Game End in " + ChatColor.GREEN + clock(left));
        lines.add("");
        for (int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);
            lines.add(team.color.letter() + " " + ChatColor.WHITE + team.color.displayName() + ": " + ChatColor.GREEN
                    + flags.score(i) + " " + ChatColor.GRAY + flags.stateLine(i, seconds)
                    + (team == viewer.team ? ChatColor.GRAY + " YOU" : ""));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "First to " + ChatColor.GREEN + flags.captures + ChatColor.WHITE + " captures");
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Captures: " + ChatColor.GREEN + captures.getOrDefault(viewer.id, 0));
        return lines;
    }

    @Override
    String summaryLine(Gamer gamer) {
        return ChatColor.GRAY + "  You: " + gamer.kills + " kills, " + captures.getOrDefault(gamer.id, 0) + " captures";
    }
}
