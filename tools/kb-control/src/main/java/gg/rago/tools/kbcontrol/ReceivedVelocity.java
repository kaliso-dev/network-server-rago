package gg.rago.tools.kbcontrol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.rago.combat.model.VelocityPacket;

/** A velocity packet a test bot received for its own entity (type "velocity" in received.jsonl). */
final class ReceivedVelocity {

    final long time;
    final int entityId;
    final VelocityPacket packet;

    ReceivedVelocity(long time, int entityId, VelocityPacket packet) {
        this.time = time;
        this.entityId = entityId;
        this.packet = packet;
    }

    static ReceivedVelocity fromJson(JsonObject json) {
        JsonArray packet = json.getAsJsonArray("packet");
        return new ReceivedVelocity(
                json.get("time").getAsLong(),
                json.get("entityId").getAsInt(),
                new VelocityPacket(packet.get(0).getAsInt(), packet.get(1).getAsInt(), packet.get(2).getAsInt()));
    }
}
