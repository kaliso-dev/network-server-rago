package gg.rago.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The starter pack of the web store (section pack-depart of config.yml): a lot of Rago Points, XP
 * boosters and cosmetics without their level, once per player, for less than its contents. The
 * web store gives it from the console with /pack give; the Shop NPC and /pack show it. Immutable.
 */
final class StarterPack {

    /** What a profile owns once it got the pack (in its list of cosmetics, so that it is saved). */
    static final String OWNED_KEY = "pack:starter";

    final String name;
    /** The price on the web store, and its usual price (the price shown crossed out). */
    final double price;
    final double usualPrice;
    final String currency;
    final int points;
    final int boosters;
    /** The cosmetics it unlocks, without their level. */
    final List<String> cosmetics;
    /** Shown on a click, to open the web store. */
    final String url;

    StarterPack(String name, double price, double usualPrice, String currency, int points, int boosters, List<String> cosmetics,
            String url) {
        this.name = name;
        this.price = price;
        this.usualPrice = usualPrice;
        this.currency = currency;
        this.points = points;
        this.boosters = boosters;
        this.cosmetics = Collections.unmodifiableList(new ArrayList<>(cosmetics));
        this.url = url;
    }

    /** The pack of a config.yml without the section. */
    static StarterPack defaults() {
        return new StarterPack(ChatColor.GOLD + "" + ChatColor.BOLD + "Starter Pack", 4.99, 9.99, "€", 20_000, 3,
                java.util.Arrays.asList("trail-magic", "kill-firework", "win-fireworks"), "https://rago-optimizer.com");
    }

    static StarterPack read(ConfigurationSection section, Map<String, Cosmetic> catalogue) {
        StarterPack d = defaults();
        if (section == null) {
            return d;
        }
        String where = "pack-depart.";
        double price = section.getDouble("prix", d.price);
        double usual = section.getDouble("prix-normal", d.usualPrice);
        if (price <= 0 || price > 1000 || usual < price || usual > 10_000) {
            throw new IllegalArgumentException(where + "prix: more than 0 and up to 1000; prix-normal: not less than prix");
        }
        int points = section.getInt("rago-points", d.points);
        if (points < 0 || points > 1_000_000) {
            throw new IllegalArgumentException(where + "rago-points: from 0 to 1,000,000");
        }
        int boosters = section.getInt("boosters", d.boosters);
        if (boosters < 0 || boosters > XpBoosters.MAX_STOCK) {
            throw new IllegalArgumentException(where + "boosters: from 0 to " + XpBoosters.MAX_STOCK);
        }
        List<String> cosmetics = section.contains("cosmetiques") ? section.getStringList("cosmetiques") : d.cosmetics;
        for (String id : cosmetics) {
            Cosmetic cosmetic = catalogue.get(id);
            if (cosmetic == null || cosmetic.premium()) {
                throw new IllegalArgumentException(where + "cosmetiques: \"" + id + "\" is not a cosmetic of the shop");
            }
        }
        String currency = section.getString("devise", d.currency);
        String url = section.getString("lien", d.url);
        if (url == null || !url.matches("https?://[^\\s\"\\\\]+")) {
            throw new IllegalArgumentException(where + "lien: an address that starts with https://");
        }
        String name = ChatColor.translateAlternateColorCodes('&', section.getString("nom", "&6&lStarter Pack"));
        return new StarterPack(name, price, usual, currency == null ? "" : currency, points, boosters, cosmetics, url);
    }

    /** The saving, in percent, rounded (50 for 4.99 instead of 9.99); 0 without a usual price above. */
    int discount() {
        if (usualPrice <= price) {
            return 0;
        }
        return (int) Math.round((1 - price / usualPrice) * 100);
    }

    /** "4.99€". */
    String money(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount) + currency;
    }

    /** The price line: the usual price crossed out, then the price. */
    String priceLine() {
        return discount() > 0 ? ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + money(usualPrice) + ChatColor.RESET + " "
                + ChatColor.GREEN + ChatColor.BOLD + money(price) : ChatColor.GREEN + "" + ChatColor.BOLD + money(price);
    }

    /** What is inside, one line each, with the names of the cosmetics from the shop's catalogue. */
    List<String> contents(Map<String, Cosmetic> catalogue) {
        List<String> lines = new ArrayList<>();
        if (points > 0) {
            lines.add(ChatColor.WHITE + "• " + ChatColor.GOLD + String.format("%,d", points) + " Rago Points");
        }
        if (boosters > 0) {
            lines.add(ChatColor.WHITE + "• " + ChatColor.AQUA + boosters + " XP booster" + (boosters > 1 ? "s" : "") + ChatColor.GRAY
                    + " (x2 XP, 30 min)");
        }
        for (String id : cosmetics) {
            Cosmetic cosmetic = catalogue.get(id);
            if (cosmetic != null) {
                lines.add(ChatColor.WHITE + "• " + ChatColor.GREEN + cosmetic.name() + ChatColor.GRAY + " ("
                        + singular(cosmetic.type().title()).toLowerCase(Locale.ROOT) + ")");
            }
        }
        return lines;
    }

    /** What the contents cost in Rago Points in the shop (the cosmetics and boosters), for the lore. */
    int pointsWorth(Map<String, Cosmetic> catalogue) {
        int worth = points + boosters * XpBoosters.PRICE;
        for (String id : cosmetics) {
            Cosmetic cosmetic = catalogue.get(id);
            if (cosmetic != null) {
                worth += cosmetic.price();
            }
        }
        return worth;
    }

    private static String singular(String title) {
        return title.endsWith("s") ? title.substring(0, title.length() - 1) : title;
    }

    /** The JSON of the line to click, that opens the web store. */
    String button() {
        return "[\"\",{\"text\":\"[Open the store]\",\"color\":\"green\",\"bold\":true,"
                + "\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + url + "\"},"
                + "\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"" + url + "\"}},"
                + "{\"text\":\" " + url.replaceFirst("^https?://", "") + "\",\"color\":\"gray\"}]";
    }
}
