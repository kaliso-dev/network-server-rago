package gg.rago.bedwars;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Drops an item on its spot every so many ticks, unless enough of them lie there already. The
 * diamond and emerald generators show their name, tier and countdown above them. Main thread only.
 */
final class Generator {

    /**
     * How high above its spot the name and tier of a diamond or emerald generator float, the
     * countdown just under them: above the head of a player standing on it.
     */
    static final double TITLE_HEIGHT = 3.6;

    private final Location spot;
    private final Material material;
    private final String label;
    private BedwarsSettings.Rate rate;
    private int tier = 1;
    private int left;
    private final List<Item> dropped = new ArrayList<>();
    private ArmorStand title;
    private ArmorStand countdown;

    /** A generator; label is the text shown above it ("Diamond"), or null for none. */
    Generator(Location spot, Material material, BedwarsSettings.Rate rate, String label) {
        this.spot = spot;
        this.material = material;
        this.rate = rate;
        this.label = label;
        this.left = rate.ticks;
    }

    /**
     * Shows the floating text, for the diamond and emerald generators. Its chunk loads first (and
     * stays loaded, in a game world): in a chunk not loaded yet, neither the text nor the items
     * would come.
     */
    void showText() {
        spot.getChunk().load();
        if (label == null) {
            return;
        }
        title = text(spot.clone().add(0, TITLE_HEIGHT, 0));
        countdown = text(spot.clone().add(0, TITLE_HEIGHT - 0.3, 0));
        refreshText();
    }

    private ArmorStand text(Location where) {
        ArmorStand stand = where.getWorld().spawn(where, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName(" ");
        return stand;
    }

    /** The generator goes up a tier: faster. */
    void upgrade(int tier, BedwarsSettings.Rate rate) {
        this.tier = tier;
        this.rate = rate;
        this.left = Math.min(left, rate.ticks);
        refreshText();
    }

    int tier() {
        return tier;
    }

    /** Time passes: drops an item (a batch of them) when its time has come. */
    void tick(int ticks) {
        left -= ticks;
        while (left <= 0) {
            int batch = rate.batches[rate.batches.length == 1 ? 0 : RANDOM.nextInt(rate.batches.length)];
            left += rate.ticks * batch;
            drop(batch);
        }
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    /** Once a second: the countdown above it. */
    void refreshText() {
        if (title == null || countdown == null) {
            return;
        }
        title.setCustomName(ChatColor.YELLOW + "Tier " + roman(tier) + " " + label);
        countdown.setCustomName(ChatColor.YELLOW + "Spawns in " + ChatColor.RED + ((left + 19) / 20)
                + ChatColor.YELLOW + " seconds");
    }

    private void drop(int batch) {
        int lying = 0;
        for (Iterator<Item> it = dropped.iterator(); it.hasNext();) {
            Item item = it.next();
            if (!item.isValid()) {
                it.remove();
            } else {
                lying += item.getItemStack().getAmount();
            }
        }
        if (lying >= rate.max) {
            return;
        }
        Item item = spot.getWorld().dropItem(spot, new ItemStack(material, Math.min(batch, rate.max - lying)));
        item.setVelocity(new Vector(0, 0, 0));
        dropped.add(item);
    }

    /** As many items lie on its spot as it lets pile up: a forge full from the start. */
    void fill() {
        drop(rate.max);
    }

    /** The game is over: the texts go (the items go with the world's rollback). */
    void remove() {
        if (title != null) {
            title.remove();
        }
        if (countdown != null) {
            countdown.remove();
        }
        dropped.clear();
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** A tier as Hypixel writes it: "II", "IV". */
    static String roman(int tier) {
        return tier >= 1 && tier <= ROMAN.length ? ROMAN[tier - 1] : String.valueOf(tier);
    }
}
