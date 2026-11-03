package spireledger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.saveAndContinue.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** The game's own room-entry autosave, copied byte for byte per floor so practice can continue from that floor on the map (architecture, 2026-11-03). */
final class FloorSaves {
    static SaveFile pending;static String status="";
    static void saving(SaveFile save){pending=save;}
    /** AsyncSaver writes on its own thread, so the copy is taken from the string handed to it rather than read back from disk. */
    static void writing(String path,String data){
        SaveFile save=pending;pending=null;
        if(save==null||save.post_combat||data==null||ReplaySafety.practice()||!StateMod.ready())return;
        try{if(!path.equals(SaveAndContinue.getPlayerSavePath(AbstractDungeon.player.chosenClass)))return;StateMod.ensure();if(StateMod.recorder!=null){store(StateMod.recorder,facts(save),data.getBytes(StandardCharsets.UTF_8));status="";}}
        // A full disk stops floor saves only; the run keeps recording.
        catch(Exception ex){status=HistoryScreen.tr("楼层存档未保存：","Floor save not stored: ")+ex.getClass().getSimpleName();DiagnosticReport.error("floor_save",ex);}
    }
    static JsonObject facts(SaveFile save){JsonObject e=Recorder.object("floor_save");e.addProperty("floor",save.floor_num);e.addProperty("room_x",save.room_x);e.addProperty("room_y",save.room_y);e.addProperty("current_room",save.current_room);e.addProperty("class",AbstractDungeon.player.chosenClass.name());return e;}
    /** A floor keeps its newest room-entry save; each copy is logged so a reader can tell which branch wrote it. */
    static JsonObject store(Recorder recorder,JsonObject facts,byte[] data)throws IOException{
        String name="floors/"+facts.get("floor").getAsInt()+".autosave";Path path=recorder.directory.resolve(name);Files.createDirectories(path.getParent());TurnFiles.atomic(path,data);
        JsonObject e=Recorder.copy(facts);e.addProperty("branch_id",recorder.branch);e.addProperty("relative_path",name);e.addProperty("bytes",data.length);e.addProperty("sha256",TurnFiles.sha256(data));
        recorder.append(e);return e;
    }
    /** The file holds only the floor's newest save, so it serves a view only when taken at the node the path last entered that floor at (before the snapshot, when one is named). */
    static JsonObject usable(Iterable<? extends JsonElement> all,Iterable<? extends JsonElement> path,int floor,String snapshotId){
        JsonObject save=null,enter=null;boolean found=snapshotId==null;
        for(JsonElement x:all){JsonObject e=x.getAsJsonObject();if("floor_save".equals(Recorder.string(e,"type"))&&HistoryIndex.num(e,"floor",-1)==floor)save=e;}
        for(JsonElement x:path){JsonObject e=x.getAsJsonObject();String type=Recorder.string(e,"type");
            if(snapshotId!=null&&"turn_snapshot".equals(type)&&snapshotId.equals(Recorder.string(e,"snapshot_id"))){found=true;break;}
            if("floor_enter".equals(type)&&HistoryIndex.num(e,"floor",-1)==floor)enter=e;}
        if(!found||save==null||enter==null||!save.has("room_x")||!enter.has("x"))return null;
        return HistoryIndex.num(save,"room_x",-1)==HistoryIndex.num(enter,"x",-2)&&HistoryIndex.num(save,"room_y",-1)==HistoryIndex.num(enter,"y",-2)?save:null;
    }
    /** Only a fight that starts on room entry reaches a recorded turn by itself; an event fight waits for the player's choice. */
    static boolean fightOnEntry(JsonObject save){return save!=null&&Recorder.string(save,"current_room").contains("MonsterRoom");}
}
