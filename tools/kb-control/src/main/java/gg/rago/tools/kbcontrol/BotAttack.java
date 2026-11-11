package gg.rago.tools.kbcontrol;

import com.google.gson.JsonObject;

/** An attack the test bots sent (type "attack" in received.jsonl). */
final class BotAttack {

    final long time;
    /** Phase, index in the phase and scenario, for example "observe-vanilla #0 (sprint, Knockback 1, victim jump)". */
    final String description;

    BotAttack(long time, String description) {
        this.time = time;
        this.description = description;
    }

    static BotAttack fromJson(JsonObject json) {
        String description = json.get("phase").getAsString() + " #" + json.get("index").getAsInt()
                + " (" + (json.get("sprint").getAsBoolean() ? "sprint" : "no sprint")
                + ", Knockback " + json.get("enchantment").getAsInt()
                + ", victim " + json.get("victim").getAsString() + ")";
        return new BotAttack(json.get("time").getAsLong(), description);
    }

    @Override
    public String toString() {
        return description;
    }
}
