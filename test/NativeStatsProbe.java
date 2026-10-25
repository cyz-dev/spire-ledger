package spireledger;
import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.actions.*;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.*;
import com.megacrit.cardcrawl.monsters.exordium.Cultist;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.relics.PenNib;
import javassist.expr.*;
import javassist.CannotCompileException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Original methods in independent JVMs. Never shipped, no formal saves. */
@SpireInitializer public class NativeStatsProbe implements PostUpdateSubscriber {
 int stage;long next;JsonObject report=new JsonObject();String firstRun,firstId;
 public static void initialize(){BaseMod.subscribe(new NativeStatsProbe());}
 @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {@SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(String distributor){return SpireReturn.Return(new GogIntegration());}}
 @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {@SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 String observed(){String id="";for(JsonElement e:StateMod.recorder.document().getAsJsonArray("records"))if("game_play_id".equals(Recorder.string(e.getAsJsonObject(),"type")))id=Recorder.string(e.getAsJsonObject(),"play_id");return id;}
 JsonObject nativeFile()throws Exception{Path dir=Paths.get("runs/IRONCLAD");Path latest=null;try(DirectoryStream<Path> files=Files.newDirectoryStream(dir,"*.run")){for(Path f:files)if(latest==null||f.getFileName().toString().compareTo(latest.getFileName().toString())>0)latest=f;}check(latest!=null,"original .run exists");return new JsonParser().parse(new String(Files.readAllBytes(latest),StandardCharsets.UTF_8)).getAsJsonObject();}
 void setup(int floor){AbstractDungeon.floorNum=floor;AbstractDungeon.actNum=floor>=53?4:1;MapRoomNode n=new MapRoomNode(0,0);n.room=new MonsterRoom();n.room.phase=AbstractRoom.RoomPhase.COMPLETE;n.room.monsters=new MonsterGroup(new Cultist(0,0));AbstractDungeon.setCurrMapNode(n);AbstractDungeon.isScreenUp=false;AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.actionManager.clear();AbstractDungeon.effectList.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();AbstractDungeon.ftue=null;TipTracker.disableAllFtues();}
 void finish(Throwable e){try{report.addProperty("status",e==null?"pass":"fail");report.addProperty("dataset_origin","test_fixture");report.addProperty("error",e==null?"":e.toString());Files.write(Paths.get("stats-probe-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ex){ex.printStackTrace();}if(e!=null)e.printStackTrace();Gdx.app.exit();}
 public void receivePostUpdate(){if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+1200;try{
  if(stage==0){if(CardCrawlGame.mainMenuScreen==null)return;Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage=1;return;}
  if(stage==1){if(!StateMod.ready())return;setup(57);StateMod.ensure();firstRun=StateMod.recorder.runId;AbstractDungeon.victoryScreen=new com.megacrit.cardcrawl.screens.VictoryScreen(AbstractDungeon.getCurrRoom().monsters);firstId=observed();check(!firstId.isEmpty()&&firstId.equals(Recorder.string(nativeFile(),"play_id")),"native VictoryScreen terminal ID matches exported .run");CardCrawlGame.mode=CardCrawlGame.GameMode.CHAR_SELECT;check(HistoryIndex.readSummary(StateMod.recorder.directory.resolve("run.json")).playId.equals(firstId),"ID persisted before menu update");stage=2;return;}
  if(stage==2){CardCrawlGame.mode=CardCrawlGame.GameMode.GAMEPLAY;setup(1);StateMod.INSTANCE.receiveStartGame();StateMod.ensure();check(!StateMod.recorder.runId.equals(firstRun),"same seed new run ID");AbstractDungeon.player.currentHealth=0;AbstractDungeon.lastCombatMetricKey="Cultist";AbstractDungeon.deathScreen=new com.megacrit.cardcrawl.screens.DeathScreen(AbstractDungeon.getCurrRoom().monsters);String second=observed();check(!second.isEmpty()&&!second.equals(firstId)&&second.equals(Recorder.string(nativeFile(),"play_id")),"native DeathScreen new UUID not previous/upload UUID");check(HistoryIndex.readSummary(StateMod.recorder.directory.resolve("run.json")).playId.equals(second),"second persisted immediately");report.addProperty("victory_run_id",firstRun);report.addProperty("victory_play_id",firstId);report.addProperty("death_run_id",StateMod.recorder.runId);report.addProperty("death_play_id",second);report.addProperty("native_terminal_and_immediate_menu",true);report.addProperty("same_seed_new_run_no_id_leak",true);finish(null);stage=99;}
 }catch(Throwable e){finish(e);stage=99;}}
}
