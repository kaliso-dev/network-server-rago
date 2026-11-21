package gg.rago.practice;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * The enemy of the Block in training, as a Bed Wars player standing at one side of the player. The
 * side open at the feet or at the head, it hits them through it. Closed by a block of the player,
 * it breaks that block first, in the time a 1.8 player takes and with the cracks on it, then hits
 * through the hole; a block put back closes the side again, and it has to break that one too. It
 * never hits through another side, and never breaks a block that is not the player's (the defense,
 * the map): a side closed by those, it goes to another side it can reach, if there is one.
 *
 * <p>Its moves (a hit, or starting to break a block) come about every 1.1 s, as the enemy of
 * before acted. Counted in ticks of the game, main thread only.
 */
final class BlockInEnemy {

    /** What the enemy does in the world: the training's own. */
    interface Hands {
        /** Hits the player from this side ({dx, dz} from them). */
        void hit(int[] side);

        /** The cracks of this stage (0 to 9) on the block it breaks; -1: none any more. */
        void cracks(Block block, int stage);

        /** Breaks this block of the player. */
        void breaks(Block block);
    }

    /** The four sides of the player: {dx, dz}. */
    static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** A move about every 22 ticks (1.1 s), 6 ticks (0.3 s) more or less. */
    static final int MOVE_TICKS = 22;
    static final int MOVE_SPREAD = 6;

    private final Random random;
    /** The blocks of the player: the ones the enemy can break. */
    private final Predicate<Block> theirs;
    private final Hands hands;
    /** The side it stands at, or null until it comes (or with no side it can reach). */
    private int[] side;
    /** The block it breaks, or null; since this tick, broken after so many ticks. */
    private Block target;
    private int targetSince;
    private int targetTicks;
    private int stage = -1;
    /** The tick of its next move. */
    private int nextMove;

    BlockInEnemy(Random random, Predicate<Block> theirs, Hands hands) {
        this.random = random;
        this.theirs = theirs;
        this.hands = hands;
    }

    /** The side it stands at ({dx, dz} from the player), or null. */
    int[] side() {
        return side;
    }

    /**
     * How many ticks a 1.8 player on the ground takes to break this block with the best tool of
     * the kit, or -1 for a block the enemy does not break (the player has only wool to place).
     * Wool: hardness 0.8, shears 5 times as fast as a hand, a block any tool takes; each tick
     * breaks 5 / 0.8 / 30 of it, added up as the client does: 5 ticks.
     */
    static int breakTicks(Material type) {
        if (type != Material.WOOL) {
            return -1;
        }
        return ticksToBreak(0.8f, 5.0f);
    }

    /** The ticks to break a block of this hardness at this speed, as the 1.8 client adds them up. */
    static int ticksToBreak(float hardness, float speed) {
        float perTick = speed / hardness / 30.0f;
        float damage = 0.0f;
        int ticks = 0;
        while (damage < 1.0f) {
            damage += perTick;
            ticks++;
        }
        return ticks;
    }

    /** A tick of the game, the player's feet in this block. */
    void tick(Block feet, int now) {
        if (side == null || !reachable(feet, side)) {
            stopBreaking();
            side = reachableSide(feet);
            if (side == null) {
                return;
            }
        }
        Block low = feet.getRelative(side[0], 0, side[1]);
        Block high = low.getRelative(0, 1, 0);
        if (!low.getType().isSolid() || !high.getType().isSolid()) {
            stopBreaking();
            if (now >= nextMove) {
                nextMove = now + moveDelay();
                hands.hit(side);
            }
            return;
        }
        if (target != null && (!(target.equals(low) || target.equals(high)) || !breakable(target))) {
            // put back or moved away from: what it broke is lost
            stopBreaking();
        }
        if (target == null) {
            if (now < nextMove) {
                return;
            }
            List<Block> blocks = new ArrayList<>();
            for (Block block : new Block[] {low, high}) {
                if (breakable(block)) {
                    blocks.add(block);
                }
            }
            if (blocks.isEmpty()) {
                return;
            }
            target = blocks.get(random.nextInt(blocks.size()));
            targetSince = now;
            targetTicks = breakTicks(target.getType());
        }
        int age = now - targetSince;
        if (age >= targetTicks) {
            Block broken = target;
            stopBreaking();
            hands.breaks(broken);
            nextMove = now + moveDelay();
            return;
        }
        int at = BlockCracks.stage(age, targetTicks);
        if (at != stage) {
            stage = at;
            hands.cracks(target, at);
        }
    }

    /** It stops breaking (the cracks go), and stays at its side. */
    void stop() {
        stopBreaking();
    }

    /** A new round: it comes again, at once, at a side it chooses then. */
    void reset() {
        stopBreaking();
        side = null;
        nextMove = 0;
    }

    private void stopBreaking() {
        if (target != null) {
            hands.cracks(target, -1);
        }
        target = null;
        stage = -1;
    }

    private int moveDelay() {
        return MOVE_TICKS - MOVE_SPREAD + random.nextInt(2 * MOVE_SPREAD + 1);
    }

    private boolean breakable(Block block) {
        return block.getType().isSolid() && theirs.test(block) && breakTicks(block.getType()) > 0;
    }

    /** From this side, it can hit the player (a block open), or open the way (a block of theirs to break). */
    private boolean reachable(Block feet, int[] at) {
        Block low = feet.getRelative(at[0], 0, at[1]);
        Block high = low.getRelative(0, 1, 0);
        return !low.getType().isSolid() || !high.getType().isSolid() || breakable(low) || breakable(high);
    }

    private int[] reachableSide(Block feet) {
        List<int[]> sides = new ArrayList<>();
        for (int[] at : SIDES) {
            if (reachable(feet, at)) {
                sides.add(at);
            }
        }
        return sides.isEmpty() ? null : sides.get(random.nextInt(sides.size()));
    }
}
