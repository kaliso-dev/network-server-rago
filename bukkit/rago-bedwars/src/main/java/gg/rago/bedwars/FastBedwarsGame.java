package gg.rago.bedwars;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Fast Bedwars (plan 6.1: « un Bedwars simplifié, inspiré du Bedwars de 2018, rapide, simple et
 * amusant »), as proposed on 21/11: a game of Bed Wars on the maps of Bed Wars, made fast by its
 * forges (forge.modes in config.yml, as any mode) and its events earlier (fast-bedwars.evenements),
 * and simple by a smaller shop (fast-bedwars.shop: the items of the default quick buy to start) and
 * no team upgrades (fast-bedwars.ameliorations: false). Without the upgrades, the map has no
 * diamond generator, the diamonds buying only them, and the game no Diamond II nor III. Everything
 * else is a game of Bed Wars. Starting values, to set in game with the owner. Main thread only.
 */
final class FastBedwarsGame extends BedwarsGame {

    FastBedwarsGame(RagoBedwars plugin, BedwarsHost host, Instance instance, String modeId, String modeName,
            List<List<Player>> sides) {
        super(plugin, host, instance, modeId, modeName, sides, plugin.catalog().only(plugin.settings().fast().shop));
    }

    @Override
    boolean upgrades() {
        return settings.fast().upgrades;
    }

    @Override
    boolean diamondGenerators() {
        return settings.fast().upgrades;
    }

    @Override
    int[] times() {
        return timeline(settings.fast(), settings.fast().upgrades);
    }

    /** The events of a Fast Bedwars game, without the tiers of the diamonds when it has none. */
    static int[] timeline(BedwarsSettings.Fast fast, boolean diamonds) {
        int[] times = fast.events.clone();
        if (!diamonds) {
            times[0] = -1;
            times[2] = -1;
        }
        return times;
    }

    @Override
    void welcome(Player player, Gamer gamer) {
        super.welcome(player, gamer);
        player.sendMessage(rules(settings.fast()));
    }

    /** The second line of the welcome: what makes the game fast and simple. */
    static String rules(BedwarsSettings.Fast fast) {
        return ChatColor.GRAY + "Faster forges, a smaller shop" + (fast.upgrades ? "" : ", no team upgrades")
                + ". The beds go at " + clock(fast.events[4]) + ", the game ends at " + clock(fast.events[5]) + ".";
    }
}
