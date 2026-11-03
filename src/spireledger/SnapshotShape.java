package spireledger;

import com.google.gson.*;
import java.util.*;

/**
 * The single definition of "the same board".
 *
 * Two places need that answer: the on-disk deduplication (two S/L sessions reaching the same turn must cost one
 * file) and the rollback check that compares a restored board with the snapshot it came from. Keeping two copies of
 * the rule let them drift: `reticle_rendered` was listed as presentation for the comparison but not for the hash, so
 * six sessions of the same turn produced two blobs instead of one (floor 20, run 416004e7).
 *
 * Presentation-only fields are dropped by name. Everything else is state and always counts, including
 * `MapRoomNode.taken`: it really does differ between a first entry and a reload, and restoring the wrong value would
 * put the map node back to unvisited.
 */
final class SnapshotShape {
    static final Set<String> VISUAL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "inspect_hb", "hb", "health_hb", "intent_hb",
            "hb_x", "hb_y", "hb_w", "hb_h", "hb_alpha",
            "draw_x", "draw_y", "dialog_x", "dialog_y", "anim_x", "anim_y",
            "reticle_alpha", "reticle_rendered", "intent_alpha", "intent_alpha_target", "intent_offset_x",
            "previous_screen_name",
            // Card identity and per-frame animation timers: the same card has a new uuid every session.
            "uuid", "wait_timer", "hovered", "just_hovered")));

    /** Drops presentation values, keeping the same keys elsewhere; a dead monster's fade timer is clamped, not removed. */
    static JsonElement logical(JsonElement value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : value.getAsJsonArray()) out.add(logical(e));
            return out;
        }
        if (!value.isJsonObject()) return value;
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet())
            if (!VISUAL.contains(e.getKey())) out.add(e.getKey(), logical(e.getValue()));
        if ("Potion Slot".equals(Recorder.string(out, "id"))) out.remove("slot");
        if (out.has("wait_timer") && out.get("wait_timer").getAsDouble() <= 0) out.addProperty("wait_timer", 0);
        JsonObject creature = out.getAsJsonObject("creature");
        if (creature != null && creature.has("is_dead") && creature.get("is_dead").getAsBoolean()
                && out.has("death_timer") && out.get("death_timer").getAsDouble() < 0) out.addProperty("death_timer", 0);
        return out;
    }

    /** The part of a snapshot that identifies the board: no identity block, no presentation values. */
    static JsonObject body(JsonObject snapshot) {
        JsonObject copy = Recorder.copy(snapshot);
        copy.remove("identity");
        JsonElement shape = logical(copy);
        return shape.isJsonObject() ? shape.getAsJsonObject() : new JsonObject();
    }
}
