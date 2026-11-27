package gg.rago.lobby;

import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * The Daily Chest of the spawn, as on Hypixel: a chest on its point ("daily", /lobby point daily,
 * where the chest goes; point-coffre-du-jour: in config.yml names another one), with lines above it
 * that tell each player whether their reward waits, and their streak. Once a day, a click on it,
 * /daily or the profile opens it: a reel of rewards spins in a chest menu between flashing panes,
 * slows down on a rising note and stops on the player's reward (drawn from coffre-du-jour:,
 * {@link DailyRewards}), which lights up in the colour of its rarity; then the menu closes on a big
 * title. At the chest, its lid opens for the spin and fireworks burst for the rare rewards; a
 * legendary one is told to the lobby. A menu closed before the end gives the reward at once, as do
 * a player who leaves and a server that stops. Without the point, there is no chest: /daily and the
 * profile still open the spin. Main thread only.
 */
final class DailyChest {

    static final String POINT = "daily";
    static final int LINES = 3;
    /** The title of the menu of the spin. */
    static final String TITLE = "Daily Chest";
    /** The middle row of the menu is the reel: its first slot, and its centre where it stops. */
    static final int REEL = 9;
    static final int CENTRE = 13;
    /** The pointers above and below the centre. */
    static final int TOP = 4;
    static final int BOTTOM = 22;
    /** Ticks between the stop of the reel and the reveal, then from the reveal to the title. */
    static final int PAUSE = 12;
    static final int SHOW = 50;
    /** The 1.8 id of the chest block, for the packet that opens its lid. */
    private static final int CHEST_ID = 54;
    /** The colours the panes flash while the reel spins (data of a stained glass pane). */
    private static final int[] FLASH = {1, 2, 3, 4, 5, 6, 9, 10, 11, 14};
    private static final int DARK = 15;
    private static final int WHITE = 0;
    private static final int GREY = 7;

    private final RagoLobby lobby;
    private final Profiles profiles;
    private final Random random = new Random();
    private final Map<UUID, Spin> spins = new HashMap<>();
    private final ItemStack[] panes = new ItemStack[16];
    private FakeEntities fakes;
    private Block chest;

    DailyChest(RagoLobby lobby, Profiles profiles) {
        this.lobby = lobby;
        this.profiles = profiles;
    }

    /** Puts the chest and its lines on its point, after the NPCs (which clear every hologram). */
    @SuppressWarnings("deprecation") // Block#getData, Block#setData: the 1.8 way to the facing of a chest
    void place(LobbyPoints points, String pointName, World world, FakeEntities fakes) {
        this.fakes = fakes;
        SpawnPoint point = points.get(pointName);
        Block at = point == null ? null : point.in(world).getBlock();
        if (chest != null && !chest.equals(at) && chest.getType() == Material.CHEST) {
            chest.setType(Material.AIR);
        }
        chest = at;
        if (at == null) {
            return;
        }
        if (at.getType() != Material.CHEST) {
            at.setType(Material.CHEST);
        }
        // its front the way its point was placed, as the builder looked (the owner's ask of 27/11:
        // towards the centre of the lobby)
        byte facing = chestFacing(point.yaw());
        if (at.getData() != facing) {
            at.setData(facing);
        }
        Location bottom = at.getLocation().add(0.5, 1.25, 0.5);
        fakes.add(new FakeHologram(bottom, LINES, viewer -> lines(viewer), (player, left) -> claimAtChest(player)));
    }

    /**
     * The 1.8 data of a chest whose front looks this way (a yaw: 0 south, 90 west, 180 north, 270
     * east): 3 south, 4 west, 2 north, 5 east.
     */
    static byte chestFacing(float yaw) {
        int quarter = Math.round(((yaw % 360f) + 360f) % 360f / 90f) % 4;
        return new byte[] {3, 4, 2, 5}[quarter];
    }

    /** True if the block is the daily chest. */
    boolean isChest(Block block) {
        return chest != null && chest.equals(block);
    }

    List<String> lines(Player viewer) {
        long now = System.currentTimeMillis();
        PlayerStats profile = profiles.of(viewer);
        boolean ready = profiles.dailyReady(viewer.getUniqueId());
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "DAILY REWARD");
        lines.add(ready ? Brand.ACCENT + "Click to claim!"
                : ChatColor.GRAY + StatsPeriod.DAY.resetText(now).replace("Resets in", "Next one in"));
        int streak = profile.dailyStreak();
        lines.add(streak > 0 ? ChatColor.GRAY + "Streak: " + ChatColor.GREEN + streak + (streak == 1 ? " day" : " days") : "");
        return lines;
    }

    /** A click on the chest: the spin, with the lid and the fireworks of the chest. */
    void claimAtChest(Player player) {
        claim(player, chest);
    }

    /** /daily or the profile: the spin. False if the reward was claimed today (the player is told). */
    boolean claim(Player player) {
        return claim(player, null);
    }

    private boolean claim(Player player, Block at) {
        Spin running = spins.get(player.getUniqueId());
        if (running != null) {
            // a second click while it spins: back to it
            running.menu.open(player);
            return true;
        }
        Profiles.DailyClaim claim = profiles.drawDaily(player, random);
        if (claim == null) {
            player.playSound(player.getLocation(), Sound.VILLAGER_NO, 1f, 1f);
            return false;
        }
        Spin spin = new Spin(player, claim, at);
        spins.put(player.getUniqueId(), spin);
        spin.start();
        return true;
    }

    /** A menu closed: if it is the player's spin, the reward at once (or the title, once shown). */
    void closed(Player player, Inventory inventory) {
        Spin spin = spins.get(player.getUniqueId());
        if (spin != null && spin.menu.getInventory().equals(inventory)) {
            spin.finish();
        }
    }

    /** The player leaves: their spin ends, the reward given. */
    void forget(Player player) {
        Spin spin = spins.get(player.getUniqueId());
        if (spin != null) {
            spin.finish();
        }
    }

    /** The server stops: every spin ends, each reward given. */
    void stop() {
        for (Spin spin : new ArrayList<>(spins.values())) {
            spin.finish();
        }
    }

    // --- The spin -------------------------------------------------------------------------------

    /** One player's spin: the reel, then the reveal, then the title. */
    private final class Spin implements Runnable {
        private final Player player;
        private final Profiles.DailyClaim claim;
        /** The chest clicked, for its lid and its fireworks; null from /daily or the profile. */
        private final Block at;
        private final ClickMenu menu = new ClickMenu(3, TITLE);
        private final List<ItemStack> reel = new ArrayList<>();
        private final int[] positions = DailyRewards.positions(DailyRewards.SHIFTS, DailyRewards.SPIN_TICKS);
        /** The ticks of the spin gone, and the moves of the reel (a note each). */
        private int tick;
        private int moves;
        private int shift;
        private int wait;
        private int after;
        private boolean revealed;
        private boolean ended;
        private BukkitTask task;

        Spin(Player player, Profiles.DailyClaim claim, Block at) {
            this.player = player;
            this.claim = claim;
            this.at = at;
            DailyRewards rewards = profiles.server().dailyRewards();
            // the reel stops with its item SHIFTS + 4 in the centre: the reward; the others are
            // draws of the same chances
            for (int i = 0; i < DailyRewards.SHIFTS + 9; i++) {
                reel.add(icon(i == DailyRewards.SHIFTS + CENTRE - REEL ? claim.reward : rewards.draw(random, claim.unowned), false));
            }
        }

        void start() {
            for (int slot = 0; slot < menu.size(); slot++) {
                if (slot < REEL || slot >= REEL + 9) {
                    menu.set(slot, pane(GREY), null);
                }
            }
            List<String> odds = new ArrayList<>();
            odds.add("The chances:");
            odds.addAll(profiles.server().dailyRewards().oddsLines());
            menu.set(TOP, ClickMenu.item(Material.HOPPER, 0, Brand.ACCENT + "\u25bc " + ChatColor.WHITE + "Your reward " + Brand.ACCENT
                    + "\u25bc", odds), null);
            menu.set(BOTTOM, ClickMenu.item(Material.REDSTONE_TORCH_ON, 0, Brand.ACCENT + "\u25b2", Collections.<String>emptyList()),
                    null);
            showReel();
            menu.open(player);
            player.playSound(player.getLocation(), Sound.CHEST_OPEN, 1f, 1f);
            if (at != null) {
                lid(true);
                at.getWorld().playSound(top(), Sound.CHEST_OPEN, 1f, 1f);
            }
            task = lobby.getServer().getScheduler().runTaskTimer(lobby, this, 1L, 1L);
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                finish();
                return;
            }
            if (!revealed) {
                if (wait > 0) {
                    wait--;
                    return;
                }
                if (tick < positions.length) {
                    int next = positions[tick++];
                    if (next != shift) {
                        shift = next;
                        moves++;
                        showReel();
                        for (int slot : frame()) {
                            menu.set(slot, pane(FLASH[random.nextInt(FLASH.length)]), null);
                        }
                        // a note a little higher at each move, as the reel slows down
                        player.playSound(player.getLocation(), Sound.NOTE_PLING, 0.6f, 0.6f + 1.4f * shift / DailyRewards.SHIFTS);
                        if (at != null && moves % 2 == 0) {
                            at.getWorld().spigot().playEffect(top(), Effect.FLYING_GLYPH, 0, 0, 0.4f, 0.4f, 0.4f, 0.6f, 10, 32);
                        }
                    }
                    if (tick == positions.length) {
                        wait = PAUSE;
                    }
                    return;
                }
                reveal();
                return;
            }
            after++;
            if (claim.reward.rarity.ordinal() >= DailyRewards.Rarity.EPIC.ordinal() && after <= 18 && after % 3 == 0) {
                int colour = after % 6 == 0 ? claim.reward.rarity.pane() : WHITE;
                for (int slot : frame()) {
                    menu.set(slot, pane(colour), null);
                }
            }
            if (after >= SHOW) {
                if (player.getOpenInventory().getTopInventory().equals(menu.getInventory())) {
                    // the close of the menu ends the spin (closed)
                    player.closeInventory();
                }
                finish();
            }
        }

        private void showReel() {
            for (int i = 0; i < 9; i++) {
                menu.set(REEL + i, reel.get(shift + i), null);
            }
        }

        /** The stop: the reward lights up in its colour, the reel darkens around it; given, told. */
        private void reveal() {
            revealed = true;
            DailyRewards.Reward reward = claim.reward;
            for (int slot : frame()) {
                menu.set(slot, pane(reward.rarity.pane()), null);
            }
            for (int i = 0; i < 9; i++) {
                if (REEL + i != CENTRE) {
                    menu.set(REEL + i, pane(DARK), null);
                }
            }
            menu.set(CENTRE, icon(reward, true), null);
            profiles.giveDaily(player, claim);
            sounds(reward.rarity);
            if (reward.rarity == DailyRewards.Rarity.LEGENDARY) {
                lobby.announce(Brand.PREFIX + profiles.displayName(player.getUniqueId(), player.getName()) + ChatColor.WHITE
                        + " found " + reward.rarity.tag() + " " + reward.label() + ChatColor.WHITE + " in the " + ChatColor.AQUA
                        + "Daily Chest" + ChatColor.WHITE + "!");
            }
        }

        /**
         * The end, once: the reward if not given yet, the title as the menu closes, the fireworks
         * over the chest then (the menu hid them), the lid closed.
         */
        void finish() {
            if (ended) {
                return;
            }
            ended = true;
            if (task != null) {
                task.cancel();
            }
            spins.remove(player.getUniqueId());
            if (!revealed) {
                reveal();
            }
            if (player.isOnline()) {
                title();
            }
            if (at != null) {
                if (lobby.isEnabled()) {
                    // not while the server stops: the fireworks go off a moment later
                    burst(claim.reward.rarity);
                }
                lid(false);
                at.getWorld().playSound(top(), Sound.CHEST_CLOSE, 1f, 1f);
            }
        }

        @SuppressWarnings("deprecation") // Player#sendTitle is marked "API subject to change" in 1.8.8
        private void title() {
            player.sendTitle(claim.reward.rarity.tag() + "!", claim.reward.label());
        }

        private void sounds(DailyRewards.Rarity rarity) {
            Location ear = player.getLocation();
            switch (rarity) {
                case COMMON:
                    player.playSound(ear, Sound.ORB_PICKUP, 1f, 1f);
                    break;
                case RARE:
                    player.playSound(ear, Sound.LEVEL_UP, 1f, 1.2f);
                    player.playSound(ear, Sound.FIREWORK_BLAST, 1f, 1f);
                    break;
                case EPIC:
                    player.playSound(ear, Sound.LEVEL_UP, 1f, 1f);
                    player.playSound(ear, Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
                    player.playSound(ear, Sound.FIREWORK_TWINKLE, 1f, 1f);
                    break;
                default:
                    player.playSound(ear, Sound.ENDERDRAGON_GROWL, 0.5f, 1.5f);
                    player.playSound(ear, Sound.LEVEL_UP, 1f, 0.8f);
                    player.playSound(ear, Sound.FIREWORK_LARGE_BLAST, 1f, 1f);
                    player.playSound(ear, Sound.FIREWORK_TWINKLE, 1f, 1f);
                    break;
            }
        }

        /** Over the chest, for everyone around: sparks, and fireworks from rare up. */
        private void burst(DailyRewards.Rarity rarity) {
            Location top = top();
            top.getWorld().spigot().playEffect(top, Effect.HAPPY_VILLAGER, 0, 0, 0.5f, 0.5f, 0.5f, 0f, 25, 32);
            top.getWorld().spigot().playEffect(top, Effect.FIREWORKS_SPARK, 0, 0, 0.3f, 0.6f, 0.3f, 0.1f, 20, 32);
            switch (rarity) {
                case RARE:
                    firework(top.clone().add(0, 1.5, 0), FireworkEffect.builder().with(FireworkEffect.Type.BALL)
                            .withColor(Color.BLUE, Color.AQUA).withFade(Color.WHITE).build());
                    break;
                case EPIC:
                    firework(top.clone().add(0, 2, 0), FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                            .withColor(Color.PURPLE, Color.FUCHSIA).withFade(Color.WHITE).flicker(true).build());
                    break;
                case LEGENDARY:
                    // three bursts, one above the other
                    for (int i = 0; i < 3; i++) {
                        final Location where = top.clone().add(0, 1.5 + i, 0);
                        final FireworkEffect.Type type = i == 2 ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR;
                        lobby.getServer().getScheduler().runTaskLater(lobby, () -> firework(where, FireworkEffect.builder()
                                .with(type).withColor(Color.ORANGE, Color.YELLOW).withFade(Color.WHITE).flicker(true).trail(true)
                                .build()), 1L + 6L * i);
                    }
                    top.getWorld().spigot().playEffect(top, Effect.FLAME, 0, 0, 0.4f, 0.6f, 0.4f, 0.05f, 40, 32);
                    break;
                default:
                    break;
            }
        }

        /** A firework that bursts at once, where it is. */
        private void firework(Location where, FireworkEffect effect) {
            Firework firework = (Firework) where.getWorld().spawnEntity(where, EntityType.FIREWORK);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(effect);
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            lobby.getServer().getScheduler().runTaskLater(lobby, () -> {
                if (firework.isValid()) {
                    firework.detonate();
                }
            }, 2L);
        }

        private Location top() {
            return at.getLocation().add(0.5, 1.0, 0.5);
        }

        /** Opens or closes the lid of the chest for the players around (as when someone opens it). */
        private void lid(boolean open) {
            if (fakes == null) {
                return;
            }
            Location centre = top();
            Vector3i position = new Vector3i(at.getX(), at.getY(), at.getZ());
            for (Player viewer : at.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(centre) <= 64 * 64) {
                    // one packet each: PacketEvents writes it for its player
                    fakes.send(viewer, new WrapperPlayServerBlockAction(position, 1, open ? 1 : 0, CHEST_ID));
                }
            }
        }
    }

    /** The slots of the frame around the reel: the top and bottom rows, but their pointers. */
    private static int[] frame() {
        int[] slots = new int[16];
        int n = 0;
        for (int slot = 0; slot < 27; slot++) {
            if ((slot < REEL || slot >= REEL + 9) && slot != TOP && slot != BOTTOM) {
                slots[n++] = slot;
            }
        }
        return slots;
    }

    /** A stained glass pane of this colour, made once. */
    private ItemStack pane(int colour) {
        if (panes[colour] == null) {
            panes[colour] = ClickMenu.item(Material.STAINED_GLASS_PANE, colour, " ", Collections.<String>emptyList());
        }
        return panes[colour];
    }

    /** A reward as the reel shows it: gold for Rago Points, a bottle for XP, its own item for a cosmetic. */
    private static ItemStack icon(DailyRewards.Reward reward, boolean yours) {
        Material material;
        switch (reward.kind) {
            case POINTS:
                material = reward.rarity == DailyRewards.Rarity.COMMON ? Material.GOLD_NUGGET
                        : reward.rarity == DailyRewards.Rarity.RARE ? Material.GOLD_INGOT : Material.GOLD_BLOCK;
                break;
            case XP:
                material = Material.EXP_BOTTLE;
                break;
            default:
                material = reward.cosmetic.icon();
                break;
        }
        List<String> lore = new ArrayList<>();
        lore.add(reward.rarity.colour() + reward.rarity.title());
        if (yours) {
            lore.add("");
            lore.add(ChatColor.GREEN + "Yours!" + (reward.instead ? ChatColor.GRAY + " You own every cosmetic." : ""));
        }
        ItemStack item = ClickMenu.item(material, 0, reward.rarity.tag() + ChatColor.DARK_GRAY + " \u00b7 " + reward.label(), lore);
        return yours || reward.rarity.ordinal() >= DailyRewards.Rarity.EPIC.ordinal() ? ClickMenu.glow(item) : item;
    }
}
