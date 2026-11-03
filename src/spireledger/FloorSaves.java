package spireledger;

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
        try{if(!path.equals(SaveAndContinue.getPlayerSavePath(AbstractDungeon.player.chosenClass)))return;StateMod.ensure();if(StateMod.recorder!=null){store(StateMod.recorder,save.floor_num,data.getBytes(StandardCharsets.UTF_8));status="";}}
        // A full disk stops floor saves only; the run keeps recording.
        catch(Exception ex){status=HistoryScreen.tr("楼层存档未保存：","Floor save not stored: ")+ex.getClass().getSimpleName();DiagnosticReport.error("floor_save",ex);}
    }
    /** A floor keeps its newest room-entry save; each copy is logged so a reader can tell which branch wrote it. */
    static JsonObject store(Recorder recorder,int floor,byte[] data)throws IOException{
        String name="floors/"+floor+".autosave";Path path=recorder.directory.resolve(name);Files.createDirectories(path.getParent());TurnFiles.atomic(path,data);
        JsonObject e=Recorder.object("floor_save");e.addProperty("floor",floor);e.addProperty("branch_id",recorder.branch);e.addProperty("relative_path",name);e.addProperty("bytes",data.length);e.addProperty("sha256",TurnFiles.sha256(data));
        recorder.append(e);return e;
    }
}
