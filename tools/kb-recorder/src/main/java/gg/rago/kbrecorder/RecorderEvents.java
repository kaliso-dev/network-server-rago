package gg.rago.kbrecorder;

import io.netty.channel.ChannelPipeline;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Puts the packet tap on each new connection and records one line per client tick. */
public final class RecorderEvents {

    private static final Logger LOGGER = LogManager.getLogger("RagoKbRecorder");
    /** Minecraft's own packet handler, the last handler of the connection's pipeline. */
    private static final String MINECRAFT_HANDLER = "packet_handler";

    private final RecorderControl control;
    private final Recorder recorder;
    private String lastServer;
    private String lastSidebar;

    RecorderEvents(RecorderControl control) {
        this.control = control;
        this.recorder = control.recorder();
    }

    /** Runs on a network thread, while the connection is being set up. */
    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        try {
            ChannelPipeline pipeline = event.manager.channel().pipeline();
            if (pipeline.get(PacketTap.NAME) == null) {
                pipeline.addBefore(MINECRAFT_HANDLER, PacketTap.NAME, new PacketTap(recorder));
            }
        } catch (RuntimeException e) {
            LOGGER.error("Could not add the packet tap; packets will not be recorded on this connection", e);
            recorder.error("connect", e);
        }
        recorder.context("connection", "open");

        String autostart = System.getProperty(KbRecorderMod.AUTOSTART_PROPERTY);
        if (autostart != null && !recorder.isRecording()) {
            try {
                LOGGER.info("Recording started automatically in {}", control.start(autostart));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Could not start the automatic recording", e);
            }
        }
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        recorder.context("connection", "closed");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !recorder.isRecording()) {
            return;
        }
        Minecraft mc = control.minecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        try {
            // A new session writes its context again, even if it did not change since the last one.
            boolean firstTick = recorder.ticks() == 0;
            String server = ClientSampler.server(mc);
            if (firstTick || !Objects.equals(server, lastServer)) {
                recorder.context("server", server);
                lastServer = server;
            }
            String sidebar = ClientSampler.sidebarTitle(mc);
            if (firstTick || !Objects.equals(sidebar, lastSidebar)) {
                recorder.context("sidebar", sidebar);
                lastSidebar = sidebar;
            }
            JsonLine tick = recorder.nextTick();
            recorder.setTrackedEntities(ClientSampler.sample(mc, tick));
            recorder.submit(tick);
        } catch (RuntimeException e) {
            recorder.error("tick", e);
        }
    }
}
