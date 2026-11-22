package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Find Your Sensitivity (plan, step 7, as its proposal describes it): the player says the
 * sensitivity of their Minecraft options (the comparator, 100% by default as in the game), then
 * plays the same series at it and at two sensitivities around it, one that turns 20% slower and
 * one 20% faster. A series: targets that appear at several angles from the crosshair and behind
 * the player (180° turns), each one clicked as fast as possible, then twenty seconds following a
 * strafing target, as in the aim trainer. The test compares the time to the target, the
 * overshoots and the shorts, the corrections and the tracking of the series, says which one was
 * the most precise and which way to go from it (more overshoots: lower; more shorts: higher), and
 * that this holds for this mouse at its DPI only, the ping shifting it a little. Nothing is kept:
 * no record, no history.
 *
 * <p>The player stays on their block (invisible walls around it), so that every target is as far.
 * A click counts with the look of the tick after it: the client sends its look after the swing.
 * The numbers (in {@link Sensitivity} and here) are starting values, to confirm with the owner.
 */
final class FindYourSensitivity extends Training {

    static final Variant TEST = new Variant("test", "Sensitivity test", false, "");
    /** How far the targets of the flicks stand, and the strafing one (blocks; to confirm). */
    static final double FLICK_DISTANCE = 6;
    static final double TRACK_DISTANCE = 4.5;
    /** The pause before each target, the tracking, the still moment before it (ticks; to confirm). */
    static final int PAUSE_TICKS = 10;
    static final int TRACK_TICKS = 400;
    private static final int TRACK_WAIT_TICKS = 20;
    /** The strafes of the tracking target, as those of the aim trainer. */
    private static final double STRAFE_SPEED = 0.22;
    private static final double STRAFE_SIDE = 3;
    /** A pause longer than this (a menu, the inventory) under a target: it appears again. */
    private static final long GAP_MS = 500L;

    private enum Step { READY, FLICKS, TRACKING, BETWEEN, DONE }

    private final Setting sensitivity;
    private Step step = Step.READY;
    private ArmorStand target;
    private Location targetAt;
    private String targetName;
    private Color targetColour;
    /** The test: its seed (the same targets at each sensitivity), the sensitivities, the series played. */
    private long seed;
    private List<Integer> planned = new ArrayList<>();
    private final List<Sensitivity.Series> played = new ArrayList<>();
    private Sensitivity.Series series;
    private int best = -1;
    /** The flicks of the series under way. */
    private List<Double> order = new ArrayList<>();
    private int index;
    private int waitTicks;
    private Sensitivity.Flick flick;
    private long shownAt;
    private float lastYaw;
    private long lastTime = -1;
    /** A click of the player, judged at the next tick. */
    private boolean clicked;
    private long clickedAt;
    private long lastTickAt;
    /** The tracking under way. */
    private Random strafe;
    private int trackTick;
    private double side;
    private int direction;
    private int turnIn;

    FindYourSensitivity(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, TEST);
        sensitivity = setting("Sensitivity", Sensitivity.MIN, Sensitivity.MAX, Sensitivity.DEFAULT).big(10, "%");
    }

    @Override
    String describe() {
        return "enter your sensitivity with the comparator, then the same series of flicks, 180s and tracking at it and"
                + " at two around it: the most precise one for your mouse.";
    }

    @Override
    void build() {
        Shapes.floor(arena, -8, -8, 8, 8, 0, Material.STAINED_CLAY, Shapes.WHITE);
        // the player stays on their block: every target is as far
        Shapes.walls(arena, -1, -1, 1, 1, 1, 2, Material.BARRIER, 0);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(0.5, 1, 0.5, -90f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD)};
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return false;
    }

    /** No score: no top 3 nor best in the sidebar. */
    @Override
    boolean showsRanking() {
        return false;
    }

    @Override
    void roundReset() {
        step = Step.READY;
        seed = new Random().nextLong();
        planned = new ArrayList<>();
        played.clear();
        series = null;
        best = -1;
        flick = null;
        clicked = false;
        lastTime = -1;
        lastTickAt = 0;
    }

    @Override
    void onFight() {
        show(startPlace(), ChatColor.GREEN + "Hit to start", Color.LIME);
        Player player = player();
        if (player != null) {
            player.sendMessage(ChatColor.YELLOW + "Your sensitivity is in Options > Controls > Mouse Settings. This test starts at "
                    + ChatColor.WHITE + sensitivity.value + "%" + ChatColor.YELLOW + ": not yours? Enter it with the comparator."
                    + " Then hit the green target.");
        }
    }

    @Override
    void ended() {
        clear();
    }

    /** Where the green target stands: ahead of the player's start. */
    private Location startPlace() {
        return facing(arena.at(0.5 + FLICK_DISTANCE, 1, 0.5));
    }

    /** The strafing target of the tracking, this far to the side. */
    private Location trackPlace() {
        return facing(arena.at(0.5 + TRACK_DISTANCE, 1, 0.5 + side));
    }

    /** The place looking at the player's block. */
    private Location facing(Location at) {
        Location centre = spawnOf(me);
        double dx = centre.getX() - at.getX();
        double dz = centre.getZ() - at.getZ();
        at.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        at.setPitch(0f);
        return at;
    }

    /**
     * A target here, at once: a new one each time, since a target moved there would slide to its
     * place on the client.
     */
    private void show(Location at, String name, Color colour) {
        clear();
        targetAt = at;
        targetName = name;
        targetColour = colour;
        // a zone used before may have its chunk unloaded: no entity goes into one
        at.getChunk().load();
        target = arena.world().spawn(at, ArmorStand.class);
        target.setGravity(false);
        target.setBasePlate(false);
        target.setArms(true);
        target.setCustomName(name);
        target.setCustomNameVisible(true);
        target.setHelmet(leatherPiece(Material.LEATHER_HELMET, colour));
        target.setChestplate(leatherPiece(Material.LEATHER_CHESTPLATE, colour));
        target.setLeggings(leatherPiece(Material.LEATHER_LEGGINGS, colour));
        target.setBoots(leatherPiece(Material.LEATHER_BOOTS, colour));
        host.own(target, this);
    }

    private void clear() {
        if (target != null) {
            host.disown(target);
            target.remove();
            target = null;
        }
        targetAt = null;
    }

    /** True if the crosshair is on the target now. */
    private boolean onTarget(Location eye) {
        if (target == null || !target.isValid()) {
            return false;
        }
        Vector look = eye.getDirection();
        Location at = target.getLocation();
        return AimTrainer.looksAt(eye.getX(), eye.getY(), eye.getZ(), look.getX(), look.getY(), look.getZ(), at.getX(), at.getY(),
                at.getZ());
    }

    @Override
    void tick() {
        long now = now();
        boolean gap = lastTickAt > 0 && now - lastTickAt > GAP_MS;
        lastTickAt = now;
        Player player = player();
        if (player == null) {
            return;
        }
        if (targetAt != null && (target == null || !target.isValid()) && tickCount % 10 == 0) {
            show(targetAt, targetName, targetColour);
        }
        Location eye = player.getEyeLocation();
        if (step == Step.FLICKS && flick != null && !gap) {
            flick.turned(Sensitivity.wrap(eye.getYaw() - lastYaw));
            lastYaw = eye.getYaw();
        }
        if (clicked) {
            clicked = false;
            click(player, onTarget(eye));
        }
        if (step == Step.FLICKS) {
            flicks(player, eye, now, gap);
        } else if (step == Step.TRACKING) {
            tracking(player, eye);
        }
        refresh();
    }

    @Override
    void swung(Player player) {
        if (!clicked) {
            clicked = true;
            clickedAt = now();
        }
    }

    @Override
    void hitEntity(Player attacker, Entity entity, EntityDamageEvent event) {
        event.setCancelled(true);
    }

    /** A click, with the look the player had: on the green target it starts, on a target it ends the flick. */
    private void click(Player player, boolean on) {
        switch (step) {
            case READY:
            case BETWEEN:
                if (on) {
                    startSeries(player);
                }
                break;
            case DONE:
                if (on) {
                    // a new test, from the sensitivity found
                    startRound();
                }
                break;
            case FLICKS:
                if (flick != null && on) {
                    long time = clickedAt - shownAt;
                    series.hit(flick, time);
                    lastTime = Math.max(0, time);
                    sound(player, Sound.SUCCESSFUL_HIT, 1.6f);
                    nextFlick(player);
                }
                break;
            default:
                break;
        }
    }

    private void startSeries(Player player) {
        if (step == Step.READY) {
            planned = Sensitivity.series(sensitivity.value);
        }
        series = new Sensitivity.Series(sensitivity.value);
        order = Sensitivity.order(seed);
        index = 0;
        flick = null;
        waitTicks = PAUSE_TICKS;
        lastTime = -1;
        step = Step.FLICKS;
        clear();
        player.sendMessage(ChatColor.GREEN + "Series " + (played.size() + 1) + "/" + planned.size() + " at " + ChatColor.WHITE
                + sensitivity.value + "%" + ChatColor.GREEN + ": " + order.size() + " targets, click each one as fast as you can"
                + " (some appear behind you), then follow the last one.");
        sound(player, Sound.NOTE_PLING, 1.2f);
    }

    /** The flicks: a pause, a target at its angle from the crosshair, until its click or its time. */
    private void flicks(Player player, Location eye, long now, boolean gap) {
        if (flick != null && gap) {
            // a menu or the inventory paused the test: this target again in a moment
            flick = null;
            clear();
            waitTicks = PAUSE_TICKS;
            return;
        }
        if (flick == null) {
            if (--waitTicks > 0) {
                return;
            }
            double angle = order.get(index);
            double yaw = Math.toRadians(eye.getYaw() + angle);
            Location at = new Location(arena.world(), eye.getX() - Math.sin(yaw) * FLICK_DISTANCE, arena.floorY() + 1,
                    eye.getZ() + Math.cos(yaw) * FLICK_DISTANCE);
            facing(at);
            show(at, ChatColor.RED + "Target", Color.RED);
            flick = new Sensitivity.Flick(angle, Sensitivity.halfAngle(AimTrainer.HALF_WIDTH, FLICK_DISTANCE));
            shownAt = now;
            lastYaw = eye.getYaw();
            return;
        }
        if (now - shownAt > Sensitivity.TIMEOUT_MS) {
            series.timedOut(flick);
            sound(player, Sound.NOTE_BASS, 0.8f);
            nextFlick(player);
        }
    }

    private void nextFlick(Player player) {
        flick = null;
        clear();
        index++;
        if (index < order.size()) {
            waitTicks = PAUSE_TICKS;
            return;
        }
        step = Step.TRACKING;
        strafe = new Random(seed + 1);
        trackTick = -TRACK_WAIT_TICKS;
        side = 0;
        direction = 1;
        turnIn = 10;
        show(trackPlace(), ChatColor.RED + "Target", Color.RED);
        player.sendMessage(ChatColor.AQUA + "Now keep your crosshair on the target for " + TRACK_TICKS / 20 + " seconds.");
    }

    /** The tracking: a still moment, then the strafes, each tick on the target or not. */
    private void tracking(Player player, Location eye) {
        if (trackTick < 0) {
            trackTick++;
            return;
        }
        // the look of this tick against the place the player saw
        series.tracked(onTarget(eye));
        move();
        Location at = trackPlace();
        targetAt = at;
        if (target != null && target.isValid()) {
            target.teleport(at);
        }
        if (++trackTick >= TRACK_TICKS) {
            endSeries(player);
        }
    }

    /** One tick of the strafes: left and right, changing direction at random (the same at each sensitivity). */
    private void move() {
        if (--turnIn <= 0) {
            direction = strafe.nextInt(3) == 0 ? direction : -direction;
            turnIn = 6 + strafe.nextInt(16);
        }
        side += direction * STRAFE_SPEED;
        if (Math.abs(side) > STRAFE_SIDE) {
            side = Math.signum(side) * STRAFE_SIDE;
            direction = -direction;
        }
    }

    private void endSeries(Player player) {
        played.add(series);
        clear();
        player.sendMessage(ChatColor.GRAY + "Series " + played.size() + ": " + line(series));
        if (played.size() < planned.size()) {
            int next = planned.get(played.size());
            sensitivity.value = next;
            step = Step.BETWEEN;
            controls(player);
            player.updateInventory();
            player.sendMessage(ChatColor.YELLOW + "Now set your sensitivity to " + ChatColor.WHITE + next + "%" + ChatColor.YELLOW
                    + " (Options > Controls > Mouse Settings). Not exactly " + next + "%? Enter yours with the comparator. Then hit"
                    + " the green target.");
            show(startPlace(), ChatColor.GREEN + "Hit to start series " + (played.size() + 1), Color.LIME);
            sound(player, Sound.ORB_PICKUP, 1f);
            return;
        }
        results(player);
    }

    /** The test is over: each series, the most precise one, which way to go, and for what it holds. */
    private void results(Player player) {
        step = Step.DONE;
        best = Sensitivity.mostPrecise(played);
        Sensitivity.Series chosen = played.get(best);
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Find your sensitivity" + ChatColor.GRAY + " - results");
        for (Sensitivity.Series each : played) {
            player.sendMessage((each == chosen ? ChatColor.GREEN + "> " : ChatColor.GRAY + "  ") + line(each));
        }
        player.sendMessage(ChatColor.GREEN + "Most precise: " + ChatColor.WHITE + chosen.percent + "%" + ChatColor.GREEN + ".");
        int way = Sensitivity.direction(chosen);
        String at = "At " + chosen.percent + "% you ";
        player.sendMessage(ChatColor.YELLOW + (way < 0 ? at + "overshoot more often than you fall short: try a little lower."
                : way > 0 ? at + "fall short more often than you overshoot: try a little higher."
                : at + "overshoot as often as you fall short."));
        player.sendMessage(ChatColor.GRAY + "This holds for this mouse at its DPI only, and your ping can shift it a little.");
        sensitivity.value = chosen.percent;
        controls(player);
        player.updateInventory();
        show(startPlace(), ChatColor.GREEN + "Hit to test again", Color.LIME);
        sound(player, Sound.LEVEL_UP, 1f);
    }

    /** A series as the chat shows it. */
    static String line(Sensitivity.Series series) {
        return ChatColor.WHITE + "" + series.percent + "%" + ChatColor.GRAY + ": " + ChatColor.WHITE + series.averageTime() + " ms"
                + ChatColor.GRAY + " to the target, " + ChatColor.WHITE + series.overshoots + ChatColor.GRAY
                + (series.overshoots == 1 ? " overshoot, " : " overshoots, ") + ChatColor.WHITE + series.shorts + ChatColor.GRAY
                + (series.shorts == 1 ? " short, " : " shorts, ") + ChatColor.WHITE
                + String.format(Locale.ROOT, "%.1f", series.correctionsPerFlick()) + ChatColor.GRAY + " corrections a target, tracking "
                + ChatColor.WHITE + series.tracking() + "%" + (series.timeouts > 0 ? ChatColor.GRAY + ", " + ChatColor.RED
                + series.timeouts + ChatColor.GRAY + " not clicked in time" : "");
    }

    /**
     * A new sensitivity in the comparator: during a series, the series starts again at it; before
     * one, the next series is played at it.
     */
    @Override
    void settingsChanged() {
        Player player = player();
        if (step == Step.FLICKS || step == Step.TRACKING) {
            series = null;
            flick = null;
            step = played.isEmpty() ? Step.READY : Step.BETWEEN;
            show(startPlace(), ChatColor.GREEN + (played.isEmpty() ? "Hit to start" : "Hit to start series " + (played.size() + 1)),
                    Color.LIME);
            if (player != null) {
                player.sendMessage(ChatColor.YELLOW + "Sensitivity changed: hit the green target to start the series again at "
                        + ChatColor.WHITE + sensitivity.value + "%" + ChatColor.YELLOW + ".");
            }
        }
        sidebars();
    }

    @Override
    void trainingLines(List<String> lines) {
        int count = planned.isEmpty() ? Sensitivity.series(sensitivity.value).size() : planned.size();
        String number = ChatColor.WHITE + "Series: " + ChatColor.GREEN + Math.min(played.size() + 1, count) + "/" + count;
        switch (step) {
            case FLICKS:
                lines.add(number + ChatColor.GRAY + " (" + sensitivity.value + "%)");
                lines.add(ChatColor.WHITE + "Target: " + ChatColor.GREEN + Math.min(index + 1, order.size()) + "/" + order.size());
                lines.add(ChatColor.WHITE + "Last: " + ChatColor.GREEN + (lastTime < 0 ? "-" : lastTime + " ms"));
                break;
            case TRACKING:
                int left = TRACK_TICKS - Math.max(0, trackTick);
                lines.add(number + ChatColor.GRAY + " (" + sensitivity.value + "%)");
                lines.add(ChatColor.WHITE + "Tracking: " + ChatColor.GREEN + clock((left + 19) / 20));
                lines.add(ChatColor.WHITE + "On target: " + ChatColor.GREEN + (series == null ? 0 : series.tracking()) + "%");
                break;
            case BETWEEN:
                lines.add(number);
                lines.add(ChatColor.WHITE + "Set: " + ChatColor.GREEN + sensitivity.value + "%");
                lines.add(ChatColor.GRAY + "Hit the green target");
                break;
            case DONE:
                lines.add(ChatColor.WHITE + "Most precise: " + ChatColor.GREEN + played.get(best).percent + "%");
                lines.add(ChatColor.GRAY + "Hit the green target");
                lines.add(ChatColor.GRAY + "to test again");
                break;
            default:
                lines.add(ChatColor.WHITE + "Sensitivity: " + ChatColor.GREEN + sensitivity.value + "%");
                lines.add(ChatColor.GRAY + "Hit the green target");
                break;
        }
    }
}
