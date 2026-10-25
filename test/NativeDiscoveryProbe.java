package spireledger;
import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import com.badlogic.gdx.Gdx;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.actions.unique.DiscoveryAction;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.monsters.exordium.Cultist;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import javassist.CannotCompileException;
import javassist.expr.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Native Discovery generation with identical seeds, with/without the adapter dependency. */
@SpireInitializer public final class NativeDiscoveryProbe implements PostUpdateSubscriber {
 int stage,iteration;long started=System.currentTimeMillis();JsonObject report=new JsonObject();JsonArray trials=new JsonArray();
 public static void initialize(){BaseMod.subscribe(new NativeDiscoveryProbe());}
 @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {@SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(){return SpireReturn.Return(new GogIntegration());}}
 @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {@SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}}
 void finish(Throwable failure){try{report.addProperty("status",failure==null?"pass":"fail");report.addProperty("error",failure==null?"":failure.toString());report.add("trials",trials);Files.write(Paths.get("discovery-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){e.printStackTrace();}Gdx.app.exit();}
 public void receivePostUpdate(){try{
  if(System.currentTimeMillis()-started>120000)throw new AssertionError("Discovery timeout stage "+stage);
  if(stage==0){if(CardCrawlGame.mainMenuScreen==null)return;StateMod.enabled=false;Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage=1;return;}
  if(CardCrawlGame.dungeon==null||AbstractDungeon.player==null)return;
  if(stage==1){TipTracker.disableAllFtues();AbstractDungeon.ftue=null;AbstractDungeon.actionManager.clear();AbstractDungeon.effectList.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.topLevelEffectsQueue.clear();AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.previousScreen=null;AbstractDungeon.isScreenUp=false;
   MapRoomNode node=new MapRoomNode(0,0);MonsterRoom room=new MonsterRoom();room.monsters=new MonsterGroup(new Cultist(0,0));node.room=room;AbstractDungeon.setCurrMapNode(node);room.monsters.init();AbstractDungeon.player.preBattlePrep();room.onPlayerEntry();stage=2;return;}
  if(stage==2){if(AbstractDungeon.actionManager.currentAction!=null||!AbstractDungeon.actionManager.actions.isEmpty())return;
   AbstractDungeon.cardRandomRng=new com.megacrit.cardcrawl.random.Random(1000L+iteration);JsonObject trial=new JsonObject();trial.addProperty("seed",1000L+iteration);trial.addProperty("fast_mode",Settings.FAST_MODE);trial.addProperty("action_dur_fast",Settings.ACTION_DUR_FAST);trials.add(trial);
   AbstractDungeon.actionManager.addToBottom(new DiscoveryAction());stage=3;return;}
  if(stage==3){if(AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.CARD_REWARD||!AbstractDungeon.isScreenUp)return;JsonObject trial=trials.get(iteration).getAsJsonObject();JsonArray choices=new JsonArray();for(com.megacrit.cardcrawl.cards.AbstractCard c:AbstractDungeon.cardRewardScreen.rewardGroup)choices.add(c.cardID);trial.add("choices",choices);trial.addProperty("rng_counter",AbstractDungeon.cardRandomRng.counter);trial.addProperty("rng_state0",String.valueOf(AbstractDungeon.cardRandomRng.random.getState(0)));trial.addProperty("rng_state1",String.valueOf(AbstractDungeon.cardRandomRng.random.getState(1)));
   if(choices.size()!=3)throw new AssertionError("three Discovery candidates");AbstractDungeon.cardRewardScreen.discoveryCard=AbstractDungeon.cardRewardScreen.rewardGroup.get(0);AbstractDungeon.closeCurrentScreen();stage=4;return;}
  if(stage==4){if(AbstractDungeon.actionManager.currentAction!=null||!AbstractDungeon.actionManager.actions.isEmpty())return;if(++iteration==10){finish(null);stage=99;}else stage=2;}
 }catch(Throwable e){e.printStackTrace();finish(e);stage=99;}}
}
