package gg.rago.kbrecorder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.io.IOException;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/**
 * Sits in the connection's Netty pipeline, just before Minecraft's own packet handler, and copies
 * the packets of the {@link PacketCatalog} into the recording. It only reads: every packet goes on
 * unchanged, and a recording error never reaches the connection.
 */
final class PacketTap extends ChannelDuplexHandler {

    static final String NAME = "rago_kb_recorder";

    private final Recorder recorder;

    PacketTap(Recorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (message instanceof Packet) {
            capture(true, (Packet<?>) message);
        }
        super.channelRead(ctx, message);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
        if (message instanceof Packet) {
            capture(false, (Packet<?>) message);
        }
        super.write(ctx, message, promise);
    }

    private void capture(boolean received, Packet<?> packet) {
        if (!recorder.isRecording()) {
            return;
        }
        try {
            PacketCatalog.Kind kind = received
                    ? PacketCatalog.received(packet.getClass())
                    : PacketCatalog.sent(packet.getClass());
            if (kind == null) {
                return;
            }
            byte[] data = serialize(packet);
            if (kind.entityId != PacketBytes.EntityIdFormat.NONE
                    && !recorder.isTracked(PacketBytes.entityId(data, kind.entityId))) {
                return;
            }
            recorder.packet(received, kind.id, kind.name, data);
        } catch (Exception e) {
            recorder.error("packet " + packet.getClass().getSimpleName(), e);
        }
    }

    /** The packet's bytes as Minecraft writes them on the wire, before compression. */
    private static byte[] serialize(Packet<?> packet) throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        try {
            packet.writePacketData(new PacketBuffer(buffer));
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }
}
