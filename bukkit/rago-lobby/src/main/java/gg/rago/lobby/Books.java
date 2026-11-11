package gg.rago.lobby;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTByte;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Opens a written book on a player's screen, as Hypixel does, without giving it: the client is
 * told the book is in their hand, then to open it (MC|BOpen); a tick later it is told the real
 * item again. The pages are JSON texts, with buttons that run commands. Main thread only.
 */
final class Books {

    /** The slot of the first hotbar item in the player's own inventory window. */
    private static final int HOTBAR = 36;

    private Books() {
    }

    static void open(Plugin plugin, final Player player, String title, List<String> jsonPages) {
        NBTList<NBTString> pages = NBTList.createStringList();
        for (String page : jsonPages) {
            pages.addTag(new NBTString(page));
        }
        NBTCompound tag = new NBTCompound();
        tag.setTag("title", new NBTString(title));
        tag.setTag("author", new NBTString("Rago"));
        tag.setTag("resolved", new NBTByte((byte) 1));
        tag.setTag("pages", pages);
        ItemStack book = ItemStack.builder().type(ItemTypes.WRITTEN_BOOK).amount(1).nbt(tag).build();
        int slot = HOTBAR + player.getInventory().getHeldItemSlot();
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetSlot(0, 0, slot, book));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPluginMessage("MC|BOpen", new byte[0]));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.updateInventory();
            }
        });
    }
}
