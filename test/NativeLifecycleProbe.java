package spireledger;

import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.patches.com.megacrit.cardcrawl.saveAndContinue.SaveFile.ModSaves;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.saveAndContinue.*;
import com.megacrit.cardcrawl.screens.options.ConfirmPopup;
import javassist.CannotCompileException;
import javassist.expr.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import spireledger.stats.*;

/** Fixture-only native save and main-menu abandon; requires a fresh isolated instance. */
@SpireInitializer
public final class NativeLifecycleProbe implements PostUpdateSubscriber {
    int stage;long next,started=System.currentTimeMillis(),saveSize=-1;
    String runId;JsonObject savedRef;Path savePath;Set<String> nativeBefore;int slot;
    static Path instance;
    public static void initialize(){
        String configured=System.getProperty("spireledger.lifecycleIsolatedRoot","");
        require(!configured.isEmpty(),"explicit lifecycleIsolatedRoot required");
        instance=Paths.get(configured).toAbsolutePath().normalize();
        require(Paths.get("").toAbsolutePath().normalize().equals(instance),"working directory must be isolated root");
        Path home=Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        require(home.startsWith(instance)&&!home.equals(instance),"user.home must be inside isolated root");
        require(Boolean.getBoolean("spireledger.fixture"),"test_fixture recording required");
        BaseMod.subscribe(new NativeLifecycleProbe());
    }
    @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {
        @SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(String distributor){return SpireReturn.Return(new GogIntegration());}
    }
    @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {
        @SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}
    }
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static JsonObject checkpoint(SaveFile save){Map<String,JsonElement> values=ModSaves.modSaves.get(save);JsonElement ref=values==null?null:values.get("spire-ledger:checkpoint");require(ref!=null&&ref.isJsonObject(),"BaseMod checkpoint present in SaveFile");return ref.getAsJsonObject();}
    static Set<String> nativeFiles()throws Exception {Set<String> files=new TreeSet<>();Path root=instance.resolve("runs");if(Files.isDirectory(root))try(Stream<Path> paths=Files.walk(root)){paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".run")).forEach(p->files.add(root.relativize(p).toString()));}return files;}
    static JsonObject last(JsonObject doc,String type){JsonObject found=null;for(JsonElement x:doc.getAsJsonArray("records")){JsonObject e=x.getAsJsonObject();if(type.equals(Recorder.string(e,"type")))found=e;}return found;}
    static void write(JsonObject report)throws Exception{Files.write(instance.resolve("lifecycle-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));}
    void prepare(){
        TipTracker.disableAllFtues();AbstractDungeon.ftue=null;AbstractDungeon.floorNum=1;AbstractDungeon.actNum=1;
        MapRoomNode node=new MapRoomNode(0,0);node.room=new MonsterRoom();node.room.phase=AbstractRoom.RoomPhase.COMPLETE;AbstractDungeon.setCurrMapNode(node);AbstractDungeon.nextRoom=null;
        AbstractDungeon.isScreenUp=false;AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.NONE;
        AbstractDungeon.actionManager.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.effectList.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();
    }
    public void receivePostUpdate(){
        if(stage==99||System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+1000;
        try{
            require(System.currentTimeMillis()-started<90000,"native lifecycle timed out at stage "+stage);
            if(stage==0){
                if(CardCrawlGame.mainMenuScreen==null)return;
                nativeBefore=nativeFiles();require(nativeBefore.isEmpty(),"fresh isolated native history required");
                savePath=Paths.get(SaveAndContinue.getPlayerSavePath(AbstractPlayer.PlayerClass.IRONCLAD)).toAbsolutePath().normalize();
                require(savePath.startsWith(instance)&&!Files.exists(savePath),"fresh isolated character save required");
                Settings.seed=1234567890123456789L;Settings.isDailyRun=false;Settings.isTrial=false;AbstractDungeon.generateSeeds();
                CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage=1;return;
            }
            if(stage==1){
                if(!StateMod.ready()||CardCrawlGame.dungeon==null)return;
                prepare();StateMod.ensure();runId=StateMod.recorder.runId;slot=CardCrawlGame.saveSlot;
                require("test_fixture".equals(Recorder.string(StateMod.recorder.document().getAsJsonObject("metadata"),"dataset_origin")),"fixture tag on original record");
                // BaseMod's real SaveFile constructor patch invokes onSave and embeds its checkpoint.
                SaveFile save=new SaveFile(SaveFile.SaveType.ENTER_ROOM);savedRef=Recorder.copy(checkpoint(save));
                require(runId.equals(Recorder.string(savedRef,"run_id")),"onSave uses current logical run");
                require(last(StateMod.recorder.document(),"checkpoint")!=null,"onSave checkpoint journaled");
                SaveAndContinue.save(save);stage=2;return;
            }
            if(stage==2){
                long size=Files.exists(savePath)?Files.size(savePath):-1;if(size<=0||size!=saveSize){saveSize=size;return;}
                SaveFile loaded=SaveAndContinue.loadSaveFile(AbstractPlayer.PlayerClass.IRONCLAD);
                require(savedRef.equals(checkpoint(loaded)),"real save/load preserves full checkpoint");
                require(CardCrawlGame.saveSlot==slot,"current slot unchanged");
                CardCrawlGame.mode=CardCrawlGame.GameMode.CHAR_SELECT;
                ConfirmPopup popup=new ConfirmPopup();Method abandon=ConfirmPopup.class.getDeclaredMethod("abandonRunFromMainMenu",AbstractPlayer.class);abandon.setAccessible(true);
                // Calls the original private menu method, whose own loadSaveFile is patched by Hooks.
                abandon.invoke(popup,AbstractDungeon.player);
                require(!Files.exists(savePath),"original menu path deleted isolated native save");
                require(nativeBefore.equals(nativeFiles()),"menu abandon creates no native .run");
                Path runFile=StateMod.ROOT.resolve(runId).resolve("run.json");JsonObject doc=HistoryIndex.document(runFile),meta=doc.getAsJsonObject("metadata"),end=last(doc,"ending"),load=last(doc,"load");
                require(runId.equals(Recorder.string(meta,"run_id"))&&meta.get("save_slot").getAsInt()==slot,"same logical run and slot archived");
                require(end!=null&&"abandon".equals(Recorder.string(end,"outcome"))&&"main_menu_saved_checkpoint".equals(Recorder.string(end,"outcome_basis")),"real menu hook appended abandon");
                require(load!=null&&Recorder.string(savedRef,"checkpoint_id").equals(Recorder.string(load,"parent_checkpoint_id")),"menu ending follows exact saved checkpoint");
                require("test_fixture".equals(Recorder.string(meta,"dataset_origin")),"disk record retains fixture exclusion");
                JsonObject statistical=Recorder.copy(doc);statistical.getAsJsonObject("metadata").remove("dataset_origin");
                HistoryIndex.Index history=HistoryIndex.scan(Collections.<Path>emptyList(),statistical);Stats.Index index=StatsIndex.scan(instance.resolve("runs"),history,statistical);
                Stats.Filter filter=new Stats.Filter();filter.slot=slot;filter.includeAbandon=true;Stats.Selection included=Stats.select(index,filter,System.currentTimeMillis());
                require(included.runs.size()==1&&!included.runs.get(0).current&&"abandon".equals(included.runs.get(0).outcome.value),"abandon immediately included via real StatsIndex with live source");
                filter.includeAbandon=false;require(Stats.select(index,filter,System.currentTimeMillis()).runs.isEmpty(),"default abandon exclusion preserved");
                JsonObject report=new JsonObject();report.addProperty("status","pass");report.addProperty("run_id",runId);report.addProperty("save_slot",slot);report.add("checkpoint",savedRef);report.add("ending",end);report.addProperty("native_run_files",nativeFiles().size());report.addProperty("included_abandons",included.runs.size());report.addProperty("fixture_retained",true);report.addProperty("evidence","real BaseMod SaveFile constructor/onSave, SaveAndContinue.save/loadSaveFile, original ConfirmPopup.abandonRunFromMainMenu with production hook; fixture-driven, no menu click");write(report);
                System.out.println("NATIVE_LIFECYCLE_PASS "+report);stage=99;com.badlogic.gdx.Gdx.app.exit();
            }
        }catch(Throwable error){try{JsonObject report=new JsonObject();report.addProperty("status","failed");report.addProperty("stage",stage);report.addProperty("error",error.toString());write(report);}catch(Exception ignored){}error.printStackTrace();stage=99;com.badlogic.gdx.Gdx.app.exit();}
    }
}
