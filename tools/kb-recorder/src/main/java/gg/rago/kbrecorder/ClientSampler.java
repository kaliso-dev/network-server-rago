package gg.rago.kbrecorder;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.util.EnumChatFormatting;

/** Reads, on the game thread, what the client knows at the end of a tick. */
final class ClientSampler {

    /** Players closer than this are written in each tick line and their packets are kept. */
    static final double TRACKING_RADIUS = 16.0;

    /** Scoreboard slot of the sidebar, where Hypixel shows the game mode. */
    private static final int SIDEBAR_SLOT = 1;

    private ClientSampler() {
    }

    /** Completes a tick line and returns the entity ids to keep packets for. */
    static int[] sample(Minecraft mc, JsonLine tick) {
        EntityPlayerSP player = mc.thePlayer;
        tick.add("eid", player.getEntityId())
                .add("x", player.posX)
                .add("y", player.posY)
                .add("z", player.posZ)
                .add("mx", player.motionX)
                .add("my", player.motionY)
                .add("mz", player.motionZ)
                .add("yaw", player.rotationYaw)
                .add("pitch", player.rotationPitch)
                .add("ground", player.onGround)
                .add("sprint", player.isSprinting())
                .add("sneak", player.isSneaking())
                .add("hurt", player.hurtTime)
                .add("health", player.getHealth())
                .raw("keys", keys(mc.gameSettings));
        int ping = ping(mc, player);
        if (ping >= 0) {
            tick.add("ping", ping);
        }

        List<String> others = new ArrayList<>();
        List<Integer> tracked = new ArrayList<>();
        tracked.add(player.getEntityId());
        for (EntityPlayer other : mc.theWorld.playerEntities) {
            if (other == player || player.getDistanceToEntity(other) > TRACKING_RADIUS) {
                continue;
            }
            tracked.add(other.getEntityId());
            others.add(new JsonLine()
                    .add("eid", other.getEntityId())
                    .add("name", other.getName())
                    .add("x", other.posX)
                    .add("y", other.posY)
                    .add("z", other.posZ)
                    .add("yaw", other.rotationYaw)
                    .add("headYaw", other.rotationYawHead)
                    .add("pitch", other.rotationPitch)
                    .add("ground", other.onGround)
                    .add("sprint", other.isSprinting())
                    .add("sneak", other.isSneaking())
                    .add("swing", other.isSwingInProgress)
                    .add("hurt", other.hurtTime)
                    .build());
        }
        tick.raw("players", JsonLine.array(others));

        int[] ids = new int[tracked.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = tracked.get(i);
        }
        return ids;
    }

    /** The server address, or "singleplayer". */
    static String server(Minecraft mc) {
        ServerData data = mc.getCurrentServerData();
        if (data != null) {
            return data.serverIP;
        }
        return mc.isSingleplayer() ? "singleplayer" : null;
    }

    /** The sidebar title without colours: on Hypixel, the game mode ("BED WARS"). */
    static String sidebarTitle(Minecraft mc) {
        if (mc.theWorld == null) {
            return null;
        }
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(SIDEBAR_SLOT);
        return objective == null ? null : EnumChatFormatting.getTextWithoutFormattingCodes(objective.getDisplayName());
    }

    private static String keys(GameSettings settings) {
        return new JsonLine()
                .add("forward", settings.keyBindForward.isKeyDown())
                .add("back", settings.keyBindBack.isKeyDown())
                .add("left", settings.keyBindLeft.isKeyDown())
                .add("right", settings.keyBindRight.isKeyDown())
                .add("jump", settings.keyBindJump.isKeyDown())
                .add("sneak", settings.keyBindSneak.isKeyDown())
                .add("sprint", settings.keyBindSprint.isKeyDown())
                .add("attack", settings.keyBindAttack.isKeyDown())
                .add("use", settings.keyBindUseItem.isKeyDown())
                .build();
    }

    /** The player's ping as the server reports it in the tab list, or -1 when unknown. */
    private static int ping(Minecraft mc, EntityPlayerSP player) {
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null) {
            return -1;
        }
        NetworkPlayerInfo info = handler.getPlayerInfo(player.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }
}
