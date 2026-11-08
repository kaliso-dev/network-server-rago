package gg.rago.bedwars;

import gg.rago.bedwars.ShopItem.Category;
import gg.rago.bedwars.ShopItem.Currency;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * One Bed Wars game in one game world: the teams on their islands, their beds, generators and
 * shops, the deaths, the beds broken and the final kills, the timed events (the diamond and emerald
 * generators going up a tier, the end of the beds, the end of the game), until one team is left.
 * The result goes to the lobby's stats; everyone goes back to the lobby at once. Main thread only.
 */
final class BedwarsGame {

    enum Phase { STARTING, PLAYING, ENDED }

    /** A team in this game. */
    static final class Team {
        final TeamColor color;
        final BedwarsMap.Team points;
        final List<UUID> members = new ArrayList<>();
        boolean bed = true;

        Team(TeamColor color, BedwarsMap.Team points) {
            this.color = color;
            this.points = points;
        }
    }

    /** A player of this game. */
    static final class Gamer {
        final UUID id;
        final String name;
        final Team team;
        final List<String> hotbar;
        final List<ShopItem> quickBuy;
        boolean eliminated;
        boolean respawning;
        /** 0 leather, 1 chainmail, 2 iron, 3 diamond. */
        int armor;
        /** -1 none, else the tier in {@link ShopCatalog#pickaxes()}. */
        int pickaxe = -1;
        int axe = -1;
        boolean shears;
        UUID lastDamager;
        long lastDamageAt;
        int kills;
        int finalKills;
        int beds;
        long lastFireball;
        BukkitTask respawn;

        Gamer(Player player, Team team, List<String> hotbar, List<ShopItem> quickBuy) {
            this.id = player.getUniqueId();
            this.name = player.getName();
            this.team = team;
            this.hotbar = hotbar;
            this.quickBuy = quickBuy;
        }

        String coloured() {
            return team.color.chat() + name;
        }
    }

    /** A hit gives the kill to its author if the player dies within this time (a fall in the void). */
    static final long CREDIT_MS = 10_000L;
    /** Around a spawn, no block can be placed: this many blocks, horizontally. */
    static final double SPAWN_PROTECTION = 3.0;
    /** Around a generator, no block can be placed. */
    static final double GENERATOR_PROTECTION = 2.0;
    static final long FIREBALL_COOLDOWN_MS = 500L;
    static final int FIREBALL_YIELD = 3;
    static final int TNT_FUSE_TICKS = 52;
    private static final String CHECK = "✔";
    private static final String CROSS = "✘";

    private final RagoBedwars plugin;
    private final BedwarsHost host;
    private final Instance instance;
    private final String modeId;
    private final String modeName;
    private final BedwarsSettings settings;
    private final ShopCatalog catalog;
    private final List<Team> teams = new ArrayList<>();
    private final Map<UUID, Gamer> gamers = new LinkedHashMap<>();
    private final List<Generator> generators = new ArrayList<>();
    private final List<Generator> diamonds = new ArrayList<>();
    private final List<Generator> emeralds = new ArrayList<>();
    /** The shop and upgrades NPCs, and where they stand. */
    private final Map<Entity, Location> npcs = new HashMap<>();
    private final Map<Entity, Team> upgradeNpcs = new HashMap<>();
    private Phase phase = Phase.STARTING;
    private int seconds;
    private BukkitTask clock;
    private BukkitTask ticker;
    private BukkitTask countdown;

    BedwarsGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        this.plugin = plugin;
        this.host = host;
        this.instance = instance;
        this.modeId = modeId;
        this.modeName = modeName;
        this.settings = plugin.settings();
        this.catalog = plugin.catalog();
        List<TeamColor> colors = instance.map().completeTeams();
        for (int i = 0; i < sides.size(); i++) {
            TeamColor color = colors.get(i);
            Team team = new Team(color, instance.map().teams().get(color));
            teams.add(team);
            for (Player player : sides.get(i)) {
                team.members.add(player.getUniqueId());
                gamers.put(player.getUniqueId(), new Gamer(player, team, plugin.lobby().hypixelHotbar(player),
                        catalog.quickBuy(plugin.lobby().hypixelQuickBuy(player))));
            }
        }
    }

    Instance instance() {
        return instance;
    }

    Phase phase() {
        return phase;
    }

    String modeId() {
        return modeId;
    }

    Gamer gamer(UUID player) {
        return gamers.get(player);
    }

    List<UUID> players() {
        return new ArrayList<>(gamers.keySet());
    }

    ShopCatalog catalog() {
        return catalog;
    }

    // --- Start ----------------------------------------------------------------------------------

    void begin() {
        World world = instance.world();
        BedwarsMap map = instance.map();
        for (Team team : teams) {
            Location spot = team.points.generator.in(world);
            generators.add(new Generator(spot, Material.IRON_INGOT, settings.iron(), null));
            generators.add(new Generator(spot, Material.GOLD_INGOT, settings.gold(), null));
            spawnNpc(team.points.shop.in(world), ChatColor.AQUA + "" + ChatColor.BOLD + "ITEM SHOP", null);
            if (team.points.upgrades != null) {
                spawnNpc(team.points.upgrades.in(world), ChatColor.AQUA + "" + ChatColor.BOLD + "TEAM UPGRADES", team);
            }
        }
        for (MapPoint point : map.diamonds()) {
            Generator generator = new Generator(point.in(world), Material.DIAMOND, settings.diamond().get(0), "Diamond");
            diamonds.add(generator);
        }
        for (MapPoint point : map.emeralds()) {
            Generator generator = new Generator(point.in(world), Material.EMERALD, settings.emerald().get(0), "Emerald");
            emeralds.add(generator);
        }
        generators.addAll(diamonds);
        generators.addAll(emeralds);
        for (Generator generator : generators) {
            generator.showText();
        }
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null) {
                continue;
            }
            prepare(player);
            player.teleport(gamer.team.points.spawn.in(world));
            giveKit(player, gamer);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + modeName + ChatColor.GREEN + " on "
                    + ChatColor.WHITE + map.world() + ChatColor.GREEN + ": you are in the " + gamer.team.color.coloured()
                    + ChatColor.GREEN + " team. Protect your bed and destroy the others'!");
        }
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        countdown = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = 3;

            @Override
            public void run() {
                if (left > 0) {
                    broadcast(ChatColor.YELLOW + "The game starts in " + ChatColor.RED + left + ChatColor.YELLOW + "...",
                            Sound.NOTE_STICKS);
                    title(ChatColor.RED + "" + ChatColor.BOLD + left, "");
                    left--;
                    return;
                }
                cancel(countdown);
                countdown = null;
                phase = Phase.PLAYING;
                broadcast(ChatColor.GREEN + "Fight!", Sound.NOTE_PLING);
                title(ChatColor.GREEN + "" + ChatColor.BOLD + "Fight!", "");
                ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    for (Generator generator : generators) {
                        generator.tick(5);
                    }
                }, 5L, 5L);
            }
        }, 0L, 20L);
        sidebars();
    }

    private void spawnNpc(Location spot, String name, Team upgradesOf) {
        spot.getChunk().load();
        Villager villager = spot.getWorld().spawn(spot, Villager.class);
        villager.setProfession(Villager.Profession.FARMER);
        villager.setAdult();
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(false);
        villager.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false), true);
        npcs.put(villager, spot);
        if (upgradesOf != null) {
            upgradeNpcs.put(villager, upgradesOf);
        }
    }

    /** True for the shop NPCs of this game. */
    boolean isShop(Entity entity) {
        return npcs.containsKey(entity) && !upgradeNpcs.containsKey(entity);
    }

    boolean isNpc(Entity entity) {
        return npcs.containsKey(entity);
    }

    private void prepare(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        heal(player);
        player.setLevel(0);
        player.setExp(0f);
    }

    private static void heal(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    // --- Every second ---------------------------------------------------------------------------

    private void second() {
        if (phase == Phase.ENDED) {
            return;
        }
        if (phase == Phase.PLAYING) {
            seconds++;
            events();
        }
        for (Generator generator : diamonds) {
            generator.refreshText();
        }
        for (Generator generator : emeralds) {
            generator.refreshText();
        }
        for (Map.Entry<Entity, Location> npc : npcs.entrySet()) {
            if (npc.getKey().isValid() && npc.getKey().getLocation().distanceSquared(npc.getValue()) > 0.04) {
                npc.getKey().teleport(npc.getValue());
            }
        }
        if (phase != Phase.ENDED) {
            sidebars();
        }
    }

    private void events() {
        if (seconds == settings.diamondII()) {
            upgrade(diamonds, 2, settings.diamond().get(1), "Diamond");
        } else if (seconds == settings.emeraldII()) {
            upgrade(emeralds, 2, settings.emerald().get(1), "Emerald");
        } else if (seconds == settings.diamondIII()) {
            upgrade(diamonds, 3, settings.diamond().get(2), "Diamond");
        } else if (seconds == settings.emeraldIII()) {
            upgrade(emeralds, 3, settings.emerald().get(2), "Emerald");
        } else if (seconds == settings.bedsGone()) {
            for (Team team : teams) {
                if (team.bed) {
                    destroyBed(team);
                }
            }
            broadcast(ChatColor.RED + "" + ChatColor.BOLD + "All beds have been destroyed!", Sound.ENDERDRAGON_GROWL);
            title(ChatColor.RED + "" + ChatColor.BOLD + "BEDS DESTROYED", ChatColor.WHITE + "Nobody respawns any more!");
        } else if (seconds >= settings.gameEnd()) {
            broadcast(ChatColor.YELLOW + "Time is up: the game ends in a draw.", Sound.NOTE_BASS);
            end(null);
        }
    }

    private void upgrade(List<Generator> list, int tier, BedwarsSettings.Rate rate, String what) {
        for (Generator generator : list) {
            generator.upgrade(tier, rate);
        }
        if (!list.isEmpty()) {
            broadcast(ChatColor.YELLOW + what + " generators are now " + ChatColor.RED + "Tier " + Generator.roman(tier),
                    Sound.LEVEL_UP);
        }
    }

    /** The next timed event and when: "Diamond II", 180 seconds. */
    private String nextEvent() {
        int[] times = {settings.diamondII(), settings.emeraldII(), settings.diamondIII(), settings.emeraldIII(),
            settings.bedsGone(), settings.gameEnd()};
        String[] names = {"Diamond II", "Emerald II", "Diamond III", "Emerald III", "Bed Gone", "Game End"};
        for (int i = 0; i < times.length; i++) {
            if (times[i] > seconds) {
                return ChatColor.WHITE + names[i] + " in " + ChatColor.GREEN + clock(times[i] - seconds);
            }
        }
        return ChatColor.WHITE + "Game End";
    }

    static String clock(int seconds) {
        int s = seconds % 60;
        return (seconds / 60) + ":" + (s < 10 ? "0" : "") + s;
    }

    private void sidebars() {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                plugin.lobby().setGameSidebar(player, lines(gamer));
            }
        }
    }

    private List<String> lines(Gamer viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(phase == Phase.STARTING ? ChatColor.WHITE + "Starting..." : nextEvent());
        lines.add("");
        for (Team team : teams) {
            int alive = 0;
            for (UUID member : team.members) {
                if (!gamers.get(member).eliminated) {
                    alive++;
                }
            }
            String state = team.bed ? ChatColor.GREEN + CHECK : alive > 0 ? ChatColor.GREEN + "" + alive : ChatColor.RED + CROSS;
            lines.add(team.color.letter() + " " + ChatColor.WHITE + team.color.displayName() + ": " + state
                    + (team == viewer.team ? ChatColor.GRAY + " YOU" : ""));
        }
        lines.add("");
        lines.add(ChatColor.WHITE + "Kills: " + ChatColor.GREEN + viewer.kills);
        lines.add(ChatColor.WHITE + "Final Kills: " + ChatColor.GREEN + viewer.finalKills);
        lines.add(ChatColor.WHITE + "Beds Broken: " + ChatColor.GREEN + viewer.beds);
        return lines;
    }

    // --- Blocks ---------------------------------------------------------------------------------

    /**
     * Why no block can be placed there (null when one can, "" to say nothing): above the build
     * limit, or too close to a spawn or a generator.
     */
    String cannotBuildAt(Block block) {
        BedwarsMap map = instance.map();
        if (block.getY() > map.buildLimit()) {
            return "Build height limit reached!";
        }
        for (BedwarsMap.Team team : map.teams().values()) {
            if (near(team.spawn, block, SPAWN_PROTECTION, 3) || near(team.generator, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        for (MapPoint point : map.diamonds()) {
            if (near(point, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        for (MapPoint point : map.emeralds()) {
            if (near(point, block, GENERATOR_PROTECTION, 2)) {
                return "You can't place blocks here!";
            }
        }
        return null;
    }

    /** True if the block is within this horizontal radius of the point, from just under it to this height above. */
    static boolean near(MapPoint point, Block block, double radius, int height) {
        return point != null && point.flatDistanceSquared(block.getX(), block.getZ()) < radius * radius
                && block.getY() >= point.blockY() - 1 && block.getY() <= point.blockY() + height;
    }

    // --- Beds -----------------------------------------------------------------------------------

    /** The team whose bed this block is part of, or null. */
    Team bedTeam(Block block) {
        for (Team team : teams) {
            for (Block half : Instance.bedOf(team.points.bed.blockIn(instance.world()))) {
                if (half.equals(block)) {
                    return team;
                }
            }
        }
        return null;
    }

    /** A player broke the bed of another team. */
    void breakBed(Player breaker, Team team) {
        Gamer gamer = gamers.get(breaker.getUniqueId());
        if (gamer == null || !team.bed) {
            return;
        }
        gamer.beds++;
        destroyBed(team);
        broadcast(ChatColor.WHITE + "" + ChatColor.BOLD + "BED DESTRUCTION > " + team.color.coloured() + " Bed"
                + ChatColor.GRAY + " was destroyed by " + gamer.coloured() + ChatColor.GRAY + "!", null);
        for (Gamer other : gamers.values()) {
            Player player = Bukkit.getPlayer(other.id);
            if (player == null || host.gameOf(other.id) != this) {
                continue;
            }
            player.playSound(player.getLocation(), Sound.ENDERDRAGON_GROWL, 1f, 1f);
            if (other.team == team) {
                player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "BED DESTROYED!",
                        ChatColor.WHITE + "You will no longer respawn!");
            }
        }
        sidebars();
    }

    private void destroyBed(Team team) {
        team.bed = false;
        for (Block half : Instance.bedOf(team.points.bed.blockIn(instance.world()))) {
            half.setType(Material.AIR, false);
        }
    }

    // --- Deaths ---------------------------------------------------------------------------------

    /** A hit by another player of the game: the kill goes to them if this player dies soon. */
    void hit(Gamer victim, Gamer attacker) {
        victim.lastDamager = attacker.id;
        victim.lastDamageAt = System.currentTimeMillis();
    }

    /**
     * The player died: killed by a player (or null), or fell into the void. They respawn after a
     * few seconds if their team still has its bed, else they are out of the game.
     */
    void kill(Player victim, Player killer, boolean voidDeath) {
        Gamer gamer = gamers.get(victim.getUniqueId());
        if (gamer == null || phase != Phase.PLAYING || gamer.respawning || gamer.eliminated) {
            if (gamer != null && voidDeath) {
                victim.teleport(phase == Phase.STARTING ? gamer.team.points.spawn.in(instance.world())
                        : instance.map().spectatorPoint().in(instance.world()));
            }
            return;
        }
        Gamer author = killer == null ? null : gamers.get(killer.getUniqueId());
        if (author == null && gamer.lastDamager != null
                && System.currentTimeMillis() - gamer.lastDamageAt < CREDIT_MS) {
            author = gamers.get(gamer.lastDamager);
        }
        if (author != null && (author.team == gamer.team || author == gamer)) {
            author = null;
        }
        gamer.lastDamager = null;
        boolean finalKill = !gamer.team.bed;
        String how = voidDeath ? (author == null ? " fell into the void." : " was knocked into the void by ")
                : (author == null ? " died." : " was killed by ");
        broadcast(gamer.coloured() + ChatColor.GRAY + how + (author == null ? "" : author.coloured() + ChatColor.GRAY + ".")
                + (finalKill ? ChatColor.AQUA + "" + ChatColor.BOLD + " FINAL KILL!" : ""), null);
        if (author != null) {
            if (finalKill) {
                author.finalKills++;
            } else {
                author.kills++;
            }
            plugin.lobby().recordKill(modeId, author.id, gamer.id);
            Player authorPlayer = Bukkit.getPlayer(author.id);
            if (authorPlayer != null) {
                loot(victim, authorPlayer);
                authorPlayer.playSound(authorPlayer.getLocation(), Sound.ORB_PICKUP, 1f, 1f);
            }
        }
        // Tools lose a tier at each death; armour and shears stay.
        if (gamer.pickaxe > 0) {
            gamer.pickaxe--;
        }
        if (gamer.axe > 0) {
            gamer.axe--;
        }
        victim.closeInventory();
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(null);
        heal(victim);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.teleport(instance.map().spectatorPoint().in(instance.world()));
        if (finalKill) {
            gamer.eliminated = true;
            victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.GRAY + "You are now a spectator.");
            victim.sendMessage(ChatColor.GRAY + "You are out of the game. /spawn to go back to the lobby.");
            sidebars();
            checkEnd();
            return;
        }
        gamer.respawning = true;
        respawnLater(victim, gamer);
        sidebars();
    }

    /** The victim's iron, gold, diamonds and emeralds go to the killer. */
    private void loot(Player victim, Player killer) {
        List<String> got = new ArrayList<>();
        for (Currency currency : Currency.values()) {
            int count = count(victim, currency);
            if (count > 0) {
                for (ItemStack left : killer.getInventory().addItem(new ItemStack(currency.material(), count)).values()) {
                    killer.getWorld().dropItem(killer.getLocation(), left);
                }
                got.add(currency.amount(count));
            }
        }
        if (!got.isEmpty()) {
            killer.sendMessage(ChatColor.GRAY + "+" + String.join(ChatColor.GRAY + ", +", got));
        }
    }

    private void respawnLater(final Player victim, final Gamer gamer) {
        final int total = settings.respawnSeconds();
        if (total <= 0) {
            respawn(victim, gamer);
            return;
        }
        victim.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!",
                ChatColor.YELLOW + "You will respawn in " + ChatColor.RED + total + ChatColor.YELLOW + " seconds!");
        gamer.respawn = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int left = total;

            @Override
            public void run() {
                left--;
                Player player = Bukkit.getPlayer(gamer.id);
                if (player == null || phase != Phase.PLAYING || host.gameOf(gamer.id) != BedwarsGame.this) {
                    cancel(gamer.respawn);
                    return;
                }
                if (left > 0) {
                    player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "YOU DIED!", ChatColor.YELLOW
                            + "You will respawn in " + ChatColor.RED + left + ChatColor.YELLOW + " seconds!");
                    return;
                }
                cancel(gamer.respawn);
                respawn(player, gamer);
            }
        }, 20L, 20L);
    }

    private void respawn(Player player, Gamer gamer) {
        gamer.respawning = false;
        gamer.respawn = null;
        heal(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(gamer.team.points.spawn.in(instance.world()));
        giveKit(player, gamer);
        player.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "RESPAWNED!", "");
        player.sendMessage(ChatColor.YELLOW + "You have respawned!");
    }

    /** The player left the game: out of the server, or /spawn. Their team plays on without them. */
    void leave(Player player, boolean toLobby) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null) {
            return;
        }
        host.release(gamer.id);
        if (!gamer.eliminated && phase != Phase.ENDED) {
            gamer.eliminated = true;
            cancel(gamer.respawn);
            broadcast(gamer.coloured() + ChatColor.GRAY + " left the game.", null);
            sidebars();
            checkEnd();
        }
        if (toLobby) {
            plugin.lobby().sendToLobby(player);
        }
    }

    private void checkEnd() {
        if (phase == Phase.ENDED) {
            return;
        }
        List<Team> alive = new ArrayList<>();
        for (Team team : teams) {
            for (UUID member : team.members) {
                if (!gamers.get(member).eliminated) {
                    alive.add(team);
                    break;
                }
            }
        }
        if (alive.size() <= 1) {
            end(alive.isEmpty() ? null : alive.get(0));
        }
    }

    // --- End ------------------------------------------------------------------------------------

    private void end(Team winner) {
        phase = Phase.ENDED;
        stopTasks();
        if (winner != null) {
            List<UUID> winners = new ArrayList<>(winner.members);
            List<UUID> losers = new ArrayList<>();
            for (Team team : teams) {
                if (team != winner) {
                    losers.addAll(team.members);
                }
            }
            plugin.lobby().recordMatch(modeId, winners, losers);
        }
        Gamer best = null;
        for (Gamer gamer : gamers.values()) {
            if (best == null || gamer.kills + gamer.finalKills > best.kills + best.finalKills) {
                best = gamer;
            }
        }
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player == null || host.gameOf(gamer.id) != this) {
                continue;
            }
            if (winner == null) {
                player.sendTitle(ChatColor.YELLOW + "" + ChatColor.BOLD + "DRAW", "");
            } else if (gamer.team == winner) {
                player.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "VICTORY!", "");
                player.playSound(player.getLocation(), Sound.LEVEL_UP, 1f, 1f);
            } else {
                player.sendTitle(ChatColor.RED + "" + ChatColor.BOLD + "GAME OVER!", "");
            }
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
            player.sendMessage(ChatColor.WHITE + "" + ChatColor.BOLD + "  " + modeName);
            player.sendMessage(winner == null ? ChatColor.YELLOW + "  Draw: no team won."
                    : ChatColor.YELLOW + "  Winner: " + winner.color.coloured() + ChatColor.YELLOW + " - " + names(winner));
            if (best != null && best.kills + best.finalKills > 0) {
                player.sendMessage(ChatColor.YELLOW + "  Most kills: " + best.coloured() + ChatColor.GRAY + " ("
                        + (best.kills + best.finalKills) + ")");
            }
            player.sendMessage(ChatColor.GRAY + "  You: " + gamer.kills + " kills, " + gamer.finalKills
                    + " final kills, " + gamer.beds + " beds broken");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.STRIKETHROUGH + "                                        ");
        }
        // The next tick, out of the event that ended the game: everyone goes back to the lobby.
        Bukkit.getScheduler().runTask(plugin, this::backToLobby);
    }

    private String names(Team team) {
        List<String> names = new ArrayList<>();
        for (UUID member : team.members) {
            names.add(gamers.get(member).name);
        }
        return String.join(", ", names);
    }

    private void backToLobby() {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                host.release(gamer.id);
                plugin.lobby().sendToLobby(player);
            }
        }
        cleanUp();
        host.finished(this);
    }

    /** The plugin stops: the game ends without a result. */
    void abort() {
        phase = Phase.ENDED;
        stopTasks();
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendMessage(ChatColor.RED + "The game is stopped: the server is restarting its modes.");
                host.release(gamer.id);
                plugin.lobby().sendToLobby(player);
            }
        }
        cleanUp();
    }

    private void stopTasks() {
        cancel(clock);
        cancel(ticker);
        cancel(countdown);
        clock = null;
        ticker = null;
        countdown = null;
        for (Gamer gamer : gamers.values()) {
            cancel(gamer.respawn);
            gamer.respawn = null;
        }
    }

    private void cleanUp() {
        for (Generator generator : generators) {
            generator.remove();
        }
        for (Entity npc : npcs.keySet()) {
            npc.remove();
        }
        npcs.clear();
        upgradeNpcs.clear();
    }

    private static void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    // --- Shop -----------------------------------------------------------------------------------

    /** What the shop shows in place of this item for the player: their next pickaxe or axe. */
    ShopItem shown(Gamer gamer, ShopItem item) {
        if (item.kind() == ShopItem.Kind.PICKAXE) {
            return catalog.pickaxes().get(Math.min(gamer.pickaxe + 1, catalog.pickaxes().size() - 1));
        }
        if (item.kind() == ShopItem.Kind.AXE) {
            return catalog.axes().get(Math.min(gamer.axe + 1, catalog.axes().size() - 1));
        }
        return item;
    }

    /** Why the player cannot buy it now (they have it already...), or null. */
    String cannotBuy(Gamer gamer, ShopItem item) {
        switch (item.kind()) {
            case COMING_SOON:
                return "Coming soon: not in this version yet.";
            case PICKAXE:
                return gamer.pickaxe >= catalog.pickaxes().size() - 1 ? "You already have the best pickaxe!" : null;
            case AXE:
                return gamer.axe >= catalog.axes().size() - 1 ? "You already have the best axe!" : null;
            case ARMOR:
                return gamer.armor >= item.tier() ? "You already have this armor or better!" : null;
            case SHEARS:
                return gamer.shears ? "You already have shears!" : null;
            default:
                return null;
        }
    }

    /** The player buys this item of the shop. True if they did. */
    boolean buy(Player player, ShopItem item) {
        Gamer gamer = gamers.get(player.getUniqueId());
        if (gamer == null || phase == Phase.ENDED || gamer.respawning || gamer.eliminated) {
            return false;
        }
        ShopItem bought = shown(gamer, item);
        String refused = cannotBuy(gamer, bought);
        if (refused != null) {
            player.sendMessage(ChatColor.RED + refused);
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        int have = count(player, bought.currency());
        if (have < bought.price()) {
            player.sendMessage(ChatColor.RED + "You don't have enough " + bought.currency().displayName() + "! Need "
                    + (bought.price() - have) + " more!");
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        player.getInventory().removeItem(new ItemStack(bought.currency().material(), bought.price()));
        PlayerInventory inventory = player.getInventory();
        switch (bought.kind()) {
            case SWORD:
                inventory.remove(Material.WOOD_SWORD);
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
            case ARMOR:
                gamer.armor = bought.tier();
                wearArmor(player, gamer);
                break;
            case PICKAXE:
                gamer.pickaxe = bought.tier();
                removeAll(inventory, "_PICKAXE");
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
            case AXE:
                gamer.axe = bought.tier();
                for (ItemStack stack : inventory.getContents()) {
                    if (stack != null && stack.getType().name().endsWith("_AXE") && !stack.getType().name().endsWith("PICKAXE")) {
                        inventory.remove(stack);
                    }
                }
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
            case SHEARS:
                gamer.shears = true;
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
            default:
                give(player, gamer, stack(bought, gamer), bought.category());
                break;
        }
        player.sendMessage(ChatColor.GREEN + "You purchased " + ChatColor.GOLD + bought.name());
        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1f, 2f);
        return true;
    }

    private static void removeAll(PlayerInventory inventory, String suffix) {
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType().name().endsWith(suffix)) {
                inventory.remove(stack);
            }
        }
    }

    /** How much of this currency the player carries. */
    static int count(Player player, Currency currency) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == currency.material()) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** The item the shop gives: in the team's colour, enchanted, unbreakable, with its potion effect. */
    ItemStack stack(ShopItem item, Gamer gamer) {
        short data = item.kind() == ShopItem.Kind.TEAM_BLOCK ? gamer.team.color.woolData() : item.data();
        ItemStack stack = new ItemStack(item.material(), item.amount(), data);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (item.enchantment() != null) {
            meta.addEnchant(item.enchantment(), item.enchantmentLevel(), true);
        }
        if (item.second() != null) {
            meta.addEnchant(item.second(), item.secondLevel(), true);
        }
        switch (item.kind()) {
            case SWORD:
            case PICKAXE:
            case AXE:
            case SHEARS:
                meta.spigot().setUnbreakable(true);
                break;
            default:
                break;
        }
        if (item.kind() == ShopItem.Kind.POTION && meta instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) meta;
            potion.clearCustomEffects();
            potion.addCustomEffect(new PotionEffect(item.effect(), item.effectSeconds() * 20, item.effectLevel() - 1), true);
            potion.setDisplayName(ChatColor.WHITE + item.name());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** At the start and after each death: the wooden sword, the armour, the tools and shears kept. */
    private void giveKit(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        wearArmor(player, gamer);
        ItemStack sword = new ItemStack(Material.WOOD_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.spigot().setUnbreakable(true);
        sword.setItemMeta(meta);
        give(player, gamer, sword, Category.MELEE);
        if (gamer.pickaxe >= 0) {
            give(player, gamer, stack(catalog.pickaxes().get(gamer.pickaxe), gamer), Category.TOOLS);
        }
        if (gamer.axe >= 0) {
            give(player, gamer, stack(catalog.axes().get(gamer.axe), gamer), Category.TOOLS);
        }
        if (gamer.shears) {
            give(player, gamer, stack(catalog.byId("shears"), gamer), Category.TOOLS);
        }
        player.updateInventory();
    }

    private static final Material[][] ARMOR = {
        {Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
        {Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS},
        {Material.IRON_LEGGINGS, Material.IRON_BOOTS},
        {Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
    };

    private void wearArmor(Player player, Gamer gamer) {
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(armor(Material.LEATHER_HELMET, gamer));
        inventory.setChestplate(armor(Material.LEATHER_CHESTPLATE, gamer));
        inventory.setLeggings(armor(ARMOR[gamer.armor][0], gamer));
        inventory.setBoots(armor(ARMOR[gamer.armor][1], gamer));
    }

    private static ItemStack armor(Material material, Gamer gamer) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta) {
            ((LeatherArmorMeta) meta).setColor(gamer.team.color.leather());
        }
        meta.spigot().setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Puts the item in the hotbar slot the player's Hypixel hotbar manager gives its kind, if free
     * (or holding the same item, with room); else wherever there is room; else at their feet.
     */
    static void give(Player player, Gamer gamer, ItemStack item, Category category) {
        PlayerInventory inventory = player.getInventory();
        if (gamer.hotbar != null && category != null && category.hotbar() != null) {
            for (int slot = 0; slot < 9 && slot < gamer.hotbar.size(); slot++) {
                if (!category.hotbar().equalsIgnoreCase(gamer.hotbar.get(slot))) {
                    continue;
                }
                ItemStack there = inventory.getItem(slot);
                if (there == null || there.getType() == Material.AIR) {
                    inventory.setItem(slot, item);
                    return;
                }
                if (there.isSimilar(item) && there.getAmount() + item.getAmount() <= there.getMaxStackSize()) {
                    there.setAmount(there.getAmount() + item.getAmount());
                    return;
                }
            }
        }
        for (ItemStack left : inventory.addItem(item).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    /** Right click with a fireball: it flies where the player looks. */
    boolean throwFireball(Player player) {
        Gamer gamer = gamers.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (gamer == null || phase != Phase.PLAYING || now - gamer.lastFireball < FIREBALL_COOLDOWN_MS) {
            return false;
        }
        gamer.lastFireball = now;
        ItemStack hand = player.getItemInHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.setItemInHand(null);
        }
        org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.Fireball.class);
        fireball.setIsIncendiary(false);
        fireball.setYield(FIREBALL_YIELD);
        Vector direction = player.getLocation().getDirection();
        fireball.setDirection(direction);
        fireball.setVelocity(direction.multiply(1.5));
        return true;
    }

    /** The team upgrades NPC was clicked. */
    void upgradesClicked(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Team upgrades (Sharpness, Protection, Haste, traps...) come in the next version.");
    }

    /** True if the NPC is the upgrades NPC of a team. */
    boolean isUpgrades(Entity entity) {
        return upgradeNpcs.containsKey(entity);
    }

    // --- Messages -------------------------------------------------------------------------------

    void broadcast(String message, Sound sound) {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendMessage(message);
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, 1f, 1f);
                }
            }
        }
    }

    private void title(String title, String subtitle) {
        for (Gamer gamer : gamers.values()) {
            Player player = Bukkit.getPlayer(gamer.id);
            if (player != null && host.gameOf(gamer.id) == this) {
                player.sendTitle(title, subtitle);
            }
        }
    }

    /** The teams, for tests and the shop. */
    List<Team> teams() {
        return Collections.unmodifiableList(teams);
    }
}
