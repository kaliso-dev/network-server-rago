package gg.rago.practice;

import gg.rago.game.GameMode;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Block edit against a bot, as on the owner's Block Edit Practice map: a wall of wool between the
 * player and a bot in a small wooden room. Break a block of the wall, hit the bot through the
 * hole, and put the block back before it hits you. The bot hits as a player would, from three
 * blocks, once it has seen the hole for its reaction time; the hotbar sets how fast it is. Each
 * clean edit adds to the streak; a hit from the bot ends it.
 */
final class BlockEditTraining extends Training {

    /** The reach of the bot, from its centre to the player's: a player's reach and half a body. */
    static final double BOT_REACH = 3.3;
    /** The reaction of the bot, in ticks, and its pace, in blocks a tick, by the speed setting. */
    private static final int[] REACTION = {10, 7, 4};
    private static final double[] PACE = {0.12, 0.17, 0.22};
    /** After a hit the bot flies back this many ticks before it walks again. */
    private static final int KNOCKED_TICKS = 8;
    /** The bot stays in its room: the x of its centre between these. */
    static final double BOT_MIN_X = 1.32;
    static final double BOT_MAX_X = EditStation.BOT_ROOM + 1 - 0.32;

    private final Setting speed;
    private Zombie bot;
    private int streak;
    /** The time the wall was opened, or -1 while it is whole. */
    private long openedAt = -1;
    private boolean hitBot;
    private int knockedUntil;
    /** The tick from which the bot sees the player through a hole, in reach; -1 when it does not. */
    private int exposedSince = -1;
    private long bestTime;

    static final Variant BOT = new Variant("bot", "Against the bot", false, "edits");
    /** The speeds of the bot, from 1, each with its own board of records. */
    static final int SPEEDS = 3;
    /** The speed of the bot by default, that of the challenge of the day. */
    static final int DEFAULT_SPEED = 2;

    BlockEditTraining(PracticeHost host, GameMode mode, Arena arena, List<List<Player>> teams) {
        super(host, mode, arena, teams, BOT);
        speed = setting("Bot speed", 1, SPEEDS, DEFAULT_SPEED);
    }

    /** The board of the records: each bot speed has its own ("block-edit.bot.speed2"). */
    @Override
    String boardOf(Variant v) {
        return mode.id() + "." + v.id + ".speed" + speed.value;
    }

    /** The challenge of the day ("block-edit.bot") is played against the bot at its speed by default. */
    @Override
    String challengeBoard() {
        return speed.value == DEFAULT_SPEED ? mode.id() + "." + current().id : board();
    }

    @Override
    void forChallenge() {
        speed.value = DEFAULT_SPEED;
    }

    /** A board of the records: the bot at its speed. */
    @Override
    boolean chooseBoard(String board) {
        String speeds = mode.id() + "." + BOT.id + ".speed";
        if (!board.startsWith(speeds)) {
            return false;
        }
        try {
            int wanted = Integer.parseInt(board.substring(speeds.length()));
            if (wanted < speed.min || wanted > speed.max) {
                return false;
            }
            speed.value = wanted;
            variant = 0;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** The reaction of the bot at this speed (1 to 3), in ticks. */
    static int reactionOf(int speed) {
        return REACTION[Math.max(1, Math.min(3, speed)) - 1];
    }

    /** True if the bot, at this horizontal distance from the player, can hit them. */
    static boolean inReach(double dx, double dz) {
        return dx * dx + dz * dz <= BOT_REACH * BOT_REACH;
    }

    /** A fall or a miss gives the next attempt by itself: no blaze rod. */
    @Override
    boolean restartUsed() {
        return false;
    }

    @Override
    String describe() {
        return "break a block of the wall, hit the bot through the hole, and put the block back before it hits you.";
    }

    @Override
    void build() {
        EditStation.build(arena, false, Shapes.RED);
    }

    @Override
    Location spawnOf(UUID player) {
        return arena.at(-2.5, 1, 0.5, -90f);
    }

    private Location botStart() {
        return arena.at(2.0, 1, 0.5, 90f);
    }

    @Override
    ItemStack[] kit() {
        return new ItemStack[] {new ItemStack(Material.WOOD_SWORD), new ItemStack(Material.SHEARS), stack(Material.WOOL, Shapes.RED)};
    }

    @Override
    void afterKit(Player player) {
        Duel.recolour(player.getInventory(), Shapes.RED);
        super.afterKit(player);
    }

    @Override
    double fallY() {
        return arena.floorY() - 5;
    }

    @Override
    boolean ticks() {
        return true;
    }

    @Override
    void roundReset() {
        openedAt = -1;
        hitBot = false;
        exposedSince = -1;
        knockedUntil = 0;
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    void onFight() {
        bot = Bots.spawn(this, botStart(), "Edit bot", Color.BLUE, speed.value - 1);
    }

    @Override
    void ended() {
        Bots.remove(this, bot);
        bot = null;
    }

    @Override
    boolean mayTarget(Entity entity, Entity target) {
        // the bot moves as the training says, it does not hunt
        return false;
    }

    @Override
    boolean canBreak(Player player, Block block) {
        return EditStation.isWall(arena, block);
    }

    @Override
    void broke(Player player, Block block) {
        if (openedAt < 0) {
            openedAt = now();
            hitBot = false;
        }
    }

    @Override
    boolean canPlace(Player player, Block block, BlockPlaceEvent event) {
        return EditStation.isWall(arena, block);
    }

    @Override
    void placed(Player player, Block block) {
        if (openedAt < 0 || EditStation.holes(arena, false) > 0) {
            return;
        }
        long took = now() - openedAt;
        openedAt = -1;
        exposedSince = -1;
        if (!hitBot) {
            player.sendMessage(ChatColor.GRAY + "The wall is closed, but you did not hit the bot.");
            return;
        }
        hitBot = false;
        streak++;
        if (bestTime == 0 || took < bestTime) {
            bestTime = took;
        }
        player.sendMessage(ChatColor.GREEN + "Clean edit! " + ChatColor.WHITE + time(took) + " s" + ChatColor.GRAY + "  Streak: "
                + ChatColor.WHITE + streak);
        sound(player, Sound.ORB_PICKUP, 1.2f);
        score(streak, true);
    }

    /** The player hits the bot: no damage, its knockback only. */
    @Override
    void hitEntity(Player attacker, Entity target, EntityDamageEvent event) {
        if (target != bot) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        if (openedAt >= 0) {
            hitBot = true;
            sound(attacker, Sound.NOTE_PLING, 1.4f);
        }
        knockedUntil = tickCount + KNOCKED_TICKS;
        exposedSince = -1;
    }

    /** The bot came through and hit the player itself: the attempt fails. */
    @Override
    boolean damage(Player victim, Player attacker, EntityDamageEvent event) {
        if (bot != null && damager(event) == bot) {
            botHits(victim);
        }
        return false;
    }

    @Override
    void tick() {
        Player player = player();
        if (player == null || bot == null) {
            return;
        }
        if (!bot.isValid()) {
            bot = Bots.spawn(this, botStart(), "Edit bot", Color.BLUE, speed.value - 1);
            return;
        }
        Location at = bot.getLocation();
        Location target = player.getLocation();
        if (tickCount >= knockedUntil) {
            walk(at, target);
        }
        boolean exposed = EditStation.holes(arena, true) > 0 && inReach(target.getX() - at.getX(), target.getZ() - at.getZ());
        if (!exposed || tickCount < knockedUntil) {
            exposedSince = -1;
        } else if (exposedSince < 0) {
            exposedSince = tickCount;
        } else if (tickCount - exposedSince >= reactionOf(speed.value)) {
            botHits(player);
        }
    }

    /** The bot walks to the wall in front of the player, at its pace, looking at them. */
    private void walk(Location at, Location player) {
        double x = BOT_MIN_X + arena.centreX();
        double z = Math.max(-EditStation.WALL_HALF + 0.3, Math.min(EditStation.WALL_HALF + 0.7, player.getZ() - arena.centreZ()))
                + arena.centreZ();
        Vector way = new Vector(x - at.getX(), 0, z - at.getZ());
        double pace = PACE[speed.value - 1];
        if (way.lengthSquared() > pace * pace) {
            way.normalize().multiply(pace);
        }
        Location next = at.clone().add(way);
        double minX = arena.centreX() + BOT_MIN_X;
        double maxX = arena.centreX() + BOT_MAX_X;
        next.setX(Math.max(minX, Math.min(maxX, next.getX())));
        Vector look = player.toVector().subtract(next.toVector());
        next.setYaw((float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ())));
        next.setPitch(0f);
        bot.teleport(next);
    }

    /** The bot hit the player first: knocked back, and the streak ends. */
    private void botHits(Player player) {
        player.setVelocity(new Vector(-0.4, 0.3, 0));
        player.playEffect(EntityEffect.HURT);
        sound(player, Sound.HURT_FLESH, 1f);
        String end = streak > 0 ? " The streak of " + streak + " ends." : "";
        streak = 0;
        failed((hitBot ? "The bot hit you before the wall was closed." : "The bot hit you first.") + end);
    }

    @Override
    void trainingLines(List<String> lines) {
        lines.add(ChatColor.WHITE + "Streak: " + ChatColor.GREEN + streak);
        lines.add(ChatColor.WHITE + "Bot: " + ChatColor.YELLOW + (reactionOf(speed.value) * 50) + " ms" + ChatColor.GRAY + " to react");
        if (bestTime > 0) {
            lines.add(ChatColor.WHITE + "Fastest edit: " + ChatColor.GREEN + time(bestTime) + " s");
        }
    }
}
