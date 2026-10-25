package spireledger;

import com.google.gson.*;
import com.evacipated.cardcrawl.modthespire.*;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import java.util.*;

/** Stable player-turn capture in the explicitly supported dependency combination. */
final class TurnRecorder {
    static boolean enabled = Boolean.getBoolean("spireledger.experimentalTurns") || readPreference();
    static boolean failed;
    static String status = enabled ? "Turn recording ready" : "Turn recording off";
    static String combatId = "", lastKey = "";
    static Recorder owner;
    static TurnFiles files;
    static boolean restoringCombat;
    static boolean readPreference() {
        try {
            java.nio.file.Path p = StateMod.ROOT.getParent().resolve("turn_recording_enabled.txt");
            if (java.nio.file.Files.exists(p)) return Boolean.parseBoolean(new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8).trim());
        } catch (Exception ignored) {}
        return false;
    }
    static void toggle(boolean value) {
        enabled = value;
        try {
            java.nio.file.Path p = StateMod.ROOT.getParent().resolve("turn_recording_enabled.txt");
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, String.valueOf(enabled).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        status = enabled ? "Turn recording ready" : "Turn recording off";
    }
    static boolean compatible(){if(!EmbeddedSaveState.present())return false;try{if(!"capture-only-1".equals(Class.forName("savestate.SaveStateMod").getField("STATE_NOTES_ADAPTER").get(null)))return false;}catch(Exception ex){return false;}Map<String,String> required=new HashMap<>();required.put("basemod","5.56.0");required.put("stslib","2.12.0");if(Loader.MODINFOS==null)return false;for(ModInfo mod:Loader.MODINFOS){if("spire-ledger".equals(mod.ID))continue;if(Boolean.getBoolean("spireledger.fixture")&&"spire-ledger-replay-probe".equals(mod.ID))continue;String version=required.remove(mod.ID);if(version==null||!version.equals(String.valueOf(mod.ModVersion)))return false;}return required.isEmpty()&&"12-18-2022".equals(Loader.STS_VERSION);}
    static void battle(){if(restoringCombat)restoringCombat=false;else combatId=UUID.randomUUID().toString();lastKey="";}
    static void restoreCombat(JsonObject ref){combatId=Recorder.string(ref,"combat_id");restoringCombat=!combatId.isEmpty();lastKey="";}
    static void observe(){
        if(!enabled||failed||!StateMod.ready()||!TurnAdapter.stable())return;
        try{
            StateMod.ensure();if(combatId.isEmpty())return;
            if(owner!=StateMod.recorder){
                owner=StateMod.recorder;
                JsonObject environment=new JsonObject();
                environment.addProperty("adapter",TurnAdapter.ID);
                environment.addProperty("game_version",Loader.STS_VERSION);
                environment.addProperty("mod_version",Recorder.VERSION);environment.addProperty("save_state","embedded-capture-only-1");
                environment.addProperty("support","vanilla combat turns with verified SaveState 1.3 finite contract");
                environment.addProperty("restore_available",compatible());
                environment.add("mods",owner.document().getAsJsonObject("metadata").get("mods"));
                files=new TurnFiles(owner.directory,owner.runId,environment);
            }
            String key=owner.branch+":"+combatId+":"+GameActionManager.turn;if(key.equals(lastKey))return;
            JsonObject entry=new JsonObject();entry.addProperty("branch_id",owner.branch);entry.addProperty("combat_id",combatId);entry.addProperty("floor",AbstractDungeon.floorNum);entry.addProperty("turn",GameActionManager.turn);entry.addProperty("phase","player_turn_ready");entry.addProperty("captured_at",System.currentTimeMillis());
            long checkpointStart=System.nanoTime();JsonObject snapshot=null;
            if(!compatible()){entry.addProperty("availability","unsupported");entry.addProperty("reason","unverified_mod_versions_or_combination");}
            else if(!AbstractDungeon.actionManager.cardsPlayedThisTurn.isEmpty()||GameActionManager.damageReceivedThisTurn!=0||GameActionManager.totalDiscardedThisTurn!=0){entry.addProperty("availability","missing");entry.addProperty("reason","first_action_or_opening_discard_already_observed");}
            else {long start=System.nanoTime();snapshot=TurnAdapter.capture();entry.addProperty("capture_ms",(System.nanoTime()-start)/1000000.0);entry.addProperty("reason","player_turn_ready");}
            JsonObject ref=files.publish(entry,snapshot);lastKey=key;if(snapshot!=null)DiagnosticReport.rememberCheckpoint(snapshot);JsonObject event=Recorder.object("turn_snapshot");event.addProperty("checkpoint_total_ms",(System.nanoTime()-checkpointStart)/1000000.0);for(Map.Entry<String,JsonElement> e:ref.entrySet())event.add(e.getKey(),e.getValue());owner.append(event);status="recorded".equals(Recorder.string(ref,"availability"))?"Turn saved; practice ready":"Turn unavailable: "+Recorder.string(ref,"reason");
        }catch(Exception ex){DiagnosticReport.error("turn_recording",ex);failed=true;status="Turn recording stopped: "+ex.getClass().getSimpleName();System.err.println("[Spire Ledger] "+status);ex.printStackTrace();}
    }
}
