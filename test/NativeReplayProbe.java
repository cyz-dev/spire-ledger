package spireledger;
import basemod.BaseMod;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.evacipated.cardcrawl.modthespire.ModInfo;
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
import savestate.SaveState;
import javassist.expr.*;
import javassist.CannotCompileException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Original methods in independent JVMs. Never shipped, no formal saves. */
@SpireInitializer public class NativeReplayProbe implements PostUpdateSubscriber,PostRenderSubscriber {
 int stage;long started=System.currentTimeMillis(),wait;String mode=System.getProperty("spireledger.replayMode","save");JsonObject report=new JsonObject(),expected,expectedAfter;int targetTurn;
 public static void initialize(){BaseMod.subscribe(new NativeReplayProbe());}
 final Map<Path,byte[]> sourceFiles=new LinkedHashMap<>();
 static boolean faultArmed;static int loadCalls;
 String replayBranch;boolean superFastChecked,solverChecked,extraChecked;String shot;Object solver;long oldSolverId;
 public void receivePostRender(SpriteBatch sb){if(shot==null)return;try{sb.flush();Pixmap image=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);png.write(new FileHandle(shot),image);png.dispose();image.dispose();shot=null;}catch(Throwable e){finish(e);stage=99;}}
 void verifySuperFastMode()throws Exception{
  verifySolverCombination();
  if(superFastChecked||!Boolean.getBoolean("spireledger.superFastModeProbe"))return;
  ModInfo found=null;for(ModInfo mod:com.evacipated.cardcrawl.modthespire.Loader.MODINFOS)if("superfastmode".equals(mod.ID))found=mod;
  check(found!=null&&"1.0.8".equals(found.ModVersion.toString()),"SuperFastMode 1.0.8 actually loaded");
  Class<?> type=Class.forName("skrelpoid.superfastmode.SuperFastMode");float multiplier=type.getField("deltaMultiplier").getFloat(null);
  check(type.getField("isDeltaMultiplied").getBoolean(null)&&multiplier==10f,"SuperFastMode acceleration enabled at player configuration 10x");
  check(type.getField("isInstantLerp").getBoolean(null),"SuperFastMode instant lerp enabled");
  float raw=((Number)type.getMethod("getDelta").invoke(null)).floatValue(),scaled=((Number)type.getMethod("getMultDelta").invoke(null)).floatValue();
  check(raw>0&&Math.abs(scaled/raw-10f)<.001f,"live graphics delta actually multiplied by ten");
  report.addProperty("superfastmode_version",found.ModVersion.toString());report.addProperty("superfastmode_multiplier",multiplier);report.addProperty("superfastmode_live_delta_ratio",scaled/raw);superFastChecked=true;
 }
 static void set(Object target,String field,Object value)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
 void verifyExtraMods()throws Exception{
  String wanted=System.getProperty("spireledger.extraModsProbe","");if(extraChecked||wanted.isEmpty())return;
  JsonObject loaded=new JsonObject();
  for(String id:wanted.split(",")){String version=null;for(ModInfo info:com.evacipated.cardcrawl.modthespire.Loader.MODINFOS)if(id.equals(info.ID))version=String.valueOf(info.ModVersion);check(version!=null,id+" actually loaded");loaded.addProperty(id,version);}
  check(ReplayEnvironment.loaded(),"optional mods "+loaded+" accepted by the replay whitelist");
  report.add("extra_mods",loaded);extraChecked=true;
 }
 void verifySolverCombination()throws Exception{
  verifyExtraMods();
  if(solverChecked||!Boolean.getBoolean("spireledger.solverCombinationProbe"))return;
  check(SolverReplay.contract()&&ReplayEnvironment.loaded(),"solver contract present; solver and three visual mods accepted");
  solver=Class.forName("sts1solver.SolverMod").getField("instance").get(null);
  check(solver!=null,"real solver initialized");set(solver,"runAutoEnabled",false);set(solver,"autoPotionRewards",false);
  String version="";for(com.evacipated.cardcrawl.modthespire.ModInfo info:com.evacipated.cardcrawl.modthespire.Loader.MODINFOS)if("sts1solver".equals(info.ID))version=String.valueOf(info.ModVersion);
  report.addProperty("solver_version",version);report.addProperty("player_eight_mod_combination",true);solverChecked=true;
 }
 void armStaleSolver()throws Exception{
  if(solver==null)return;
  oldSolverId=((Number)StateMod.field(solver,"activeId")).longValue();
  set(solver,"result",new JsonObject());set(solver,"auto",true);set(solver,"runAuto",true);
  solver.getClass().getMethod("receivePostUpdate").invoke(solver);
  check(StateMod.field(solver,"result")!=null&&(Boolean)StateMod.field(solver,"auto"),"solver update is paused while review is open");
 }
 void verifyStoppedSolver()throws Exception{
  if(solver==null)return;
  check(StateMod.field(solver,"result")==null&&!(Boolean)StateMod.field(solver,"auto")&&!(Boolean)StateMod.field(solver,"runAuto")&&(Boolean)StateMod.field(solver,"paused"),"old plan cleared and automation stopped");
  check(((Number)StateMod.field(solver,"activeId")).longValue()>oldSolverId,"late solver reply generation invalidated");
  check(StateMod.field(solver,"runAutoPlayer")==AbstractDungeon.player,"restored player does not rearm run auto");
  report.addProperty("solver_paused_during_review_and_old_plan_invalidated",true);
 }
 static void clicked(Hitbox hb){hb.clicked=true;com.megacrit.cardcrawl.helpers.input.InputHelper.mX=(int)hb.cX;com.megacrit.cardcrawl.helpers.input.InputHelper.mY=(int)hb.cY;}
 void replayHistoryUI()throws Exception{
  HistoryScreen history=HistoryScreen.instance;check(history!=null,"history remains open until native replay click");
  if(stage==62){
   if(history.indexTask!=null||history.detailTask!=null)return;
   check(history.selected!=null&&history.selected.current,"native history selected current run");
   history.review.sync();history.review.pinned=true;check(history.turnMenu!=null&&history.availableTurns.size()>=2,"native history displays captured turns");
   clicked(history.turnMenu.getHitbox());history.update();check(history.turnMenu.isOpen,"native turn dropdown opens");stage=63;return;
  }
  if(stage==63){
   int row=-1;for(int i=0;i<history.availableTurns.size();i++)if(history.availableTurns.get(i).get("turn").getAsInt()==2){row=i;break;}
   check(row>=0,"turn two available in native dropdown");
   clicked((Hitbox)StateMod.field(history.turnMenu.rows.get(row),"hb"));history.update();
   check(!history.turnMenu.isOpen&&history.turnSelection==row,"native dropdown selects turn two");
   check(ReplayLauncher.canReplayCurrent(history.availableTurns.get(row)),"selected UI action is current combat restart");
   check(facts().equals(expectedAfter),"history selection preserves state and all RNGs");
   shot="replay-current-ui.png";stage=64;return;
  }
  if(shot!=null)return;
  armStaleSolver();clicked(history.replayButton);history.update();verifyStoppedSolver();
  check(!HistoryScreen.active(),"native current combat replay button closes history");
  check(!replayBranch.equals(StateMod.recorder.branch),"current replay forks history branch");
  report.addProperty("production_current_replay",true);report.addProperty("native_history_turn_selection_and_replay_button",true);report.addProperty("history_selection_rng_unchanged",true);
  stage=7;wait=System.currentTimeMillis()+600;
 }
 @SpirePatch(clz=SaveState.class,method=SpirePatch.CONSTRUCTOR,paramtypez={}) public static class BackupFault {
  @SpirePrefixPatch public static void before(){if(faultArmed&&"backup".equals(System.getProperty("spireledger.replayFailure")))throw new IllegalStateException("injected backup capture failure");}
 }
 @SpirePatch(clz=SaveState.class,method="loadState") public static class LoadFault {
  @SpirePostfixPatch public static void after(){if(!faultArmed)return;loadCalls++;String fault=System.getProperty("spireledger.replayFailure","");
   if(("load".equals(fault)&&loadCalls==1)||"rollback".equals(fault))throw new IllegalStateException("injected load failure after mutation "+loadCalls);
   if("repair".equals(fault)&&loadCalls==1)AbstractDungeon.miscRng=null;
  }
 }
 void failureCheck()throws Exception{
  String fault=System.getProperty("spireledger.replayFailure");JsonObject before=facts(),env=TurnAdapter.environment(),target=TurnAdapter.capture();
  JsonObject creature=target.getAsJsonObject("state").getAsJsonObject("player_state").getAsJsonObject("creature");
  check(creature.has("current_health"),"test targets real creature health field");creature.addProperty("current_health",AbstractDungeon.player.currentHealth-7);
  faultArmed=true;boolean threw=false;try{TurnAdapter.restore(target);}catch(Exception expectedFailure){threw=true;}finally{faultArmed=false;}
  check(threw,"injected failure reaches caller");
  if("rollback".equals(fault)){
   check(ReplaySafety.quarantined&&loadCalls==2,"rollback failure quarantines real implementation");
   CardCrawlGame game=(CardCrawlGame)Gdx.app.getApplicationListener();game.update();game.render();
   com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue.save(null);
   com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue.deleteSave(AbstractDungeon.player);
   check(ReplaySafety.quarantined,"native update and save calls return while frozen");report.addProperty("native_update_and_save_blocked",true);
  }else{
   check(!ReplaySafety.quarantined,"healthy backup keeps gameplay available");write("failure-before-facts.json",before);write("failure-after-facts.json",facts());write("failure-before-environment.json",env);write("failure-after-environment.json",TurnAdapter.environment());check(before.equals(facts())&&env.equals(TurnAdapter.environment()),"failed restore preserves live facts/RNG/environment");
   check(loadCalls==("backup".equals(fault)?0:2),"backup failure precedes writes; restore failure invokes rollback");report.addProperty("live_state_preserved",true);
  }
  report.addProperty("fault",fault);report.addProperty("load_calls",loadCalls);report.addProperty("quarantined",ReplaySafety.quarantined);finish(null);stage=99;
 }
 @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {@SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(String distributor){return Boolean.getBoolean("spireledger.productionPractice")?SpireReturn.Continue():SpireReturn.Return(new GogIntegration());}}
 @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {@SpireInstrumentPatch public static ExprEditor edit(){if(Boolean.getBoolean("spireledger.productionPractice"))return new ExprEditor();return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void verifyTurnFiles()throws Exception{if(!Boolean.getBoolean("spireledger.experimentalTurns"))return;check(StateMod.recorder!=null,"production recorder created by hooks");JsonObject manifest=TurnFiles.read(StateMod.recorder.directory.resolve("replay/manifest.json"));JsonArray points=manifest.getAsJsonArray("snapshots");Set<String> keys=new HashSet<>();int recorded=0;for(JsonElement item:points){JsonObject point=item.getAsJsonObject();String key=Recorder.string(point,"branch_id")+":"+Recorder.string(point,"combat_id")+":"+point.get("turn");check(keys.add(key),"one point per turn key");check("recorded".equals(Recorder.string(point,"availability")),"native capture compatibility: "+point);TurnFiles.read(StateMod.recorder.directory.resolve("replay").resolve(Recorder.string(point,"relative_path")));recorded++;}check(recorded>=2,"automatic first and second turn points before original card actions");StateMod.recorder.export();}
 static void write(String name,JsonObject o)throws Exception{Files.write(Paths.get(name),o.toString().getBytes(StandardCharsets.UTF_8));}
 static JsonObject read(String name)throws Exception{return new JsonParser().parse(new String(Files.readAllBytes(Paths.get(name)),StandardCharsets.UTF_8)).getAsJsonObject();}
 static JsonArray ids(List<AbstractCard> cards){JsonArray a=new JsonArray();for(AbstractCard c:cards)a.add(c.cardID+"+"+c.timesUpgraded+":"+c.misc);return a;}
 static JsonObject facts()throws Exception{JsonObject o=new JsonObject();o.add("hand",ids(AbstractDungeon.player.hand.group));o.add("draw",ids(AbstractDungeon.player.drawPile.group));o.add("discard",ids(AbstractDungeon.player.discardPile.group));o.add("exhaust",ids(AbstractDungeon.player.exhaustPile.group));o.add("master",ids(AbstractDungeon.player.masterDeck.group));o.add("history",ids(AbstractDungeon.actionManager.cardsPlayedThisCombat));o.addProperty("turn",GameActionManager.turn);o.addProperty("hp",AbstractDungeon.player.currentHealth);o.addProperty("energy",com.megacrit.cardcrawl.ui.panels.EnergyPanel.totalCount);o.addProperty("pen_nib",AbstractDungeon.player.getRelic("Pen Nib").counter);o.addProperty("shuffle_counter",AbstractDungeon.shuffleRng.counter);o.addProperty("card_counter",AbstractDungeon.cardRandomRng.counter);JsonArray monsters=new JsonArray();for(AbstractMonster m:AbstractDungeon.getCurrRoom().monsters.monsters){JsonObject n=new JsonObject();n.addProperty("id",m.id);n.addProperty("hp",m.currentHealth);n.addProperty("intent",m.nextMove);n.addProperty("block",m.currentBlock);JsonArray powers=new JsonArray();for(com.megacrit.cardcrawl.powers.AbstractPower pow:m.powers)powers.add(pow.ID+":"+pow.amount);n.add("powers",powers);monsters.add(n);}o.add("monsters",monsters);o.add("supplement",supplement());o.addProperty("character",AbstractDungeon.player.chosenClass.name());o.addProperty("stance",AbstractDungeon.player.stance.ID);return o;}
 static final String[] RNGS={"monsterRng","mapRng","eventRng","merchantRng","cardRng","treasureRng","relicRng","potionRng","monsterHpRng","aiRng","shuffleRng","cardRandomRng","miscRng"};
 static String character=System.getProperty("spireledger.replayCharacter","IRONCLAD");
 static String scenario=System.getProperty("spireledger.replayScenario","ordinary");
 static List<AbstractCard> allCards(){List<AbstractCard> cards=new ArrayList<>();for(CardGroup pile:Arrays.asList(AbstractDungeon.player.masterDeck,AbstractDungeon.player.drawPile,AbstractDungeon.player.hand,AbstractDungeon.player.discardPile,AbstractDungeon.player.exhaustPile,AbstractDungeon.player.limbo))cards.addAll(pile.group);return cards;}
 static JsonArray history(List<AbstractCard> played){JsonArray result=new JsonArray();List<AbstractCard> cards=allCards();for(AbstractCard c:played){JsonObject e=new JsonObject();int index=cards.indexOf(c);e.addProperty("index",index);if(index<0)e.add("card",savestate.CardState.forCard(c).jsonEncode());result.add(e);}return result;}
 static JsonArray orbs(List<com.megacrit.cardcrawl.orbs.AbstractOrb> played){JsonArray result=new JsonArray();for(com.megacrit.cardcrawl.orbs.AbstractOrb orb:played){JsonObject e=new JsonObject();e.addProperty("active_index",AbstractDungeon.player.orbs.indexOf(orb));e.addProperty("combat_index",AbstractDungeon.actionManager.orbsChanneledThisCombat.indexOf(orb));e.add("orb",savestate.orbs.OrbState.forOrb(orb).jsonEncode());result.add(e);}return result;}
 static void loadOrbs(JsonArray entries,List<com.megacrit.cardcrawl.orbs.AbstractOrb> target,boolean turn){target.clear();for(JsonElement value:entries){JsonObject e=value.getAsJsonObject();int active=e.get("active_index").getAsInt(),combat=e.get("combat_index").getAsInt();target.add(active>=0?AbstractDungeon.player.orbs.get(active):turn&&combat>=0?AbstractDungeon.actionManager.orbsChanneledThisCombat.get(combat):savestate.orbs.OrbState.forJsonObject(e.getAsJsonObject("orb")).loadOrb());}}
 static void repairManager(JsonObject extra){if(!extra.has("manager"))return;JsonObject m=extra.getAsJsonObject("manager");GameActionManager a=AbstractDungeon.actionManager;a.uniqueStancesThisCombat.clear();for(Map.Entry<String,JsonElement> e:m.getAsJsonObject("unique_stances").entrySet())a.uniqueStancesThisCombat.put(e.getKey(),e.getValue().getAsInt());loadOrbs(m.getAsJsonArray("orb_combat_history"),a.orbsChanneledThisCombat,false);loadOrbs(m.getAsJsonArray("orb_turn_history"),a.orbsChanneledThisTurn,true);if(m.get("last_card").isJsonNull())a.lastCard=null;else {JsonArray list=new JsonArray();list.add(m.get("last_card"));List<AbstractCard> cards=new ArrayList<>();loadHistory(list,cards);a.lastCard=cards.get(0);}}
 static JsonObject manager(){GameActionManager a=AbstractDungeon.actionManager;JsonObject o=new JsonObject();o.add("unique_stances",new Gson().toJsonTree(a.uniqueStancesThisCombat));o.add("orb_combat_history",orbs(a.orbsChanneledThisCombat));o.add("orb_turn_history",orbs(a.orbsChanneledThisTurn));o.add("last_card",a.lastCard==null?JsonNull.INSTANCE:history(Collections.singletonList(a.lastCard)).get(0));return o;}
 static JsonObject supplement()throws Exception{JsonObject out=new JsonObject(),rng=new JsonObject();for(String name:RNGS){com.megacrit.cardcrawl.random.Random r=(com.megacrit.cardcrawl.random.Random)AbstractDungeon.class.getField(name).get(null);if(r==null)continue;JsonObject value=new JsonObject();value.addProperty("counter",r.counter);value.addProperty("state0",String.valueOf(r.random.getState(0)));value.addProperty("state1",String.valueOf(r.random.getState(1)));rng.add(name,value);}out.add("rng",rng);out.add("combat_history",history(AbstractDungeon.actionManager.cardsPlayedThisCombat));out.add("turn_history",history(AbstractDungeon.actionManager.cardsPlayedThisTurn));out.add("drawn",history(com.megacrit.cardcrawl.actions.common.DrawCardAction.drawnCards));JsonObject counters=new JsonObject();for(String name:Arrays.asList("damageReceivedThisTurn","damageReceivedThisCombat","hpLossThisCombat","playerHpLastTurn","energyGainedThisCombat"))counters.addProperty(name,GameActionManager.class.getField(name).getInt(null));out.add("counters",counters);out.add("manager",manager());return out;}
 static void loadHistory(JsonArray entries,List<AbstractCard> target){target.clear();List<AbstractCard> cards=allCards();for(JsonElement e:entries){JsonObject value=e.getAsJsonObject();int index=value.get("index").getAsInt();target.add(index>=0?cards.get(index):savestate.CardState.forJson(value.getAsJsonObject("card")).loadCard());}}
 static void repair(JsonObject extra)throws Exception{for(Map.Entry<String,JsonElement> e:extra.getAsJsonObject("rng").entrySet()){JsonObject value=e.getValue().getAsJsonObject();com.megacrit.cardcrawl.random.Random r=(com.megacrit.cardcrawl.random.Random)AbstractDungeon.class.getField(e.getKey()).get(null);r.random.setState(Long.parseLong(value.get("state0").getAsString()),Long.parseLong(value.get("state1").getAsString()));r.counter=value.get("counter").getAsInt();}loadHistory(extra.getAsJsonArray("combat_history"),AbstractDungeon.actionManager.cardsPlayedThisCombat);loadHistory(extra.getAsJsonArray("turn_history"),AbstractDungeon.actionManager.cardsPlayedThisTurn);loadHistory(extra.getAsJsonArray("drawn"),com.megacrit.cardcrawl.actions.common.DrawCardAction.drawnCards);for(Map.Entry<String,JsonElement> e:extra.getAsJsonObject("counters").entrySet())GameActionManager.class.getField(e.getKey()).setInt(null,e.getValue().getAsInt());repairManager(extra);}
 static boolean replayEffects(List<com.megacrit.cardcrawl.vfx.AbstractGameEffect> effects){List<com.megacrit.cardcrawl.vfx.AbstractGameEffect> rest=new ArrayList<>();for(com.megacrit.cardcrawl.vfx.AbstractGameEffect e:effects)if(!Arrays.asList("com.megacrit.cardcrawl.vfx.stance.StanceAuraEffect","com.megacrit.cardcrawl.vfx.stance.CalmParticleEffect","com.megacrit.cardcrawl.vfx.stance.WrathParticleEffect","com.megacrit.cardcrawl.vfx.stance.DivinityParticleEffect","com.megacrit.cardcrawl.vfx.combat.DarkOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.LightningOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.FrostOrbPassiveEffect","com.megacrit.cardcrawl.vfx.combat.PlasmaOrbPassiveEffect").contains(e.getClass().getName()))rest.add(e);return Snapshot.blockingEffects(rest);}static boolean stable(){GameActionManager a=AbstractDungeon.actionManager;return AbstractDungeon.getCurrRoom().phase==AbstractRoom.RoomPhase.COMBAT&&!AbstractDungeon.isScreenUp&&a.currentAction==null&&a.turnStartCurrentAction==null&&a.actions.isEmpty()&&a.preTurnActions.isEmpty()&&a.cardQueue.isEmpty()&&a.monsterQueue.isEmpty()&&!a.turnHasEnded&&!AbstractDungeon.player.isEndingTurn&&!replayEffects(AbstractDungeon.effectList)&&!replayEffects(AbstractDungeon.topLevelEffects)&&!replayEffects(AbstractDungeon.effectsQueue)&&!replayEffects(AbstractDungeon.topLevelEffectsQueue)&&a.phase==GameActionManager.Phase.WAITING_ON_USER&&AbstractDungeon.overlayMenu.endTurnButton.enabled;}
 void prepare(){TipTracker.disableAllFtues();AbstractDungeon.ftue=null;AbstractDungeon.actionManager.clear();AbstractDungeon.effectList.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.previousScreen=null;AbstractDungeon.isScreenUp=false;AbstractDungeon.floorNum=scenario.endsWith("act2")?19:1;AbstractDungeon.actNum=scenario.endsWith("act2")?2:1;AbstractDungeon.lastCombatMetricKey="Cultist";MapRoomNode node=new MapRoomNode(0,0);MonsterRoom room=new MonsterRoom();room.monsters=new MonsterGroup((scenario.equals("multiphase")?new com.megacrit.cardcrawl.monsters.exordium.SlimeBoss():new Cultist(0,0)));node.room=room;AbstractDungeon.setCurrMapNode(node);room.monsters.init();if(scenario.equals("multiphase"))room.monsters.monsters.get(0).currentHealth=70;AbstractDungeon.player.masterDeck.clear();for(String id:Arrays.asList("Strike_R","Strike_R","Strike_R","Defend_R","Defend_R","Defend_R","Bash","Anger"))AbstractDungeon.player.masterDeck.addToBottom(CardLibrary.getCard(id).makeCopy());new PenNib().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),false);if(scenario.equals("gambling"))new com.megacrit.cardcrawl.relics.GamblingChip().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),false);AbstractDungeon.player.preBattlePrep();room.onPlayerEntry();if(scenario.startsWith("generated"))AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction(CardLibrary.getCard("Anger").makeCopy()));if(character.equals("WATCHER"))AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction("Calm"));}
 void play(){AbstractMonster target=null;for(AbstractMonster m:AbstractDungeon.getCurrRoom().monsters.monsters)if(!m.isDeadOrEscaped()){target=m;break;}check(target!=null,"live native target");for(AbstractCard c:AbstractDungeon.player.hand.group)if(c.type==AbstractCard.CardType.ATTACK&&c.canUse(AbstractDungeon.player,target)){if(stage>=7){AbstractPlayer p=AbstractDungeon.player;p.hoveredCard=c;p.inSingleTargetMode=true;p.isDraggingCard=false;p.isInKeyboardMode=false;com.megacrit.cardcrawl.helpers.input.InputHelper.mX=(int)target.hb.cX;com.megacrit.cardcrawl.helpers.input.InputHelper.mY=(int)target.hb.cY;com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft=true;try{p.updateInput();}finally{com.megacrit.cardcrawl.helpers.input.InputHelper.justClickedLeft=false;}boolean queued=false;for(CardQueueItem item:AbstractDungeon.actionManager.cardQueue)if(item.card==c&&item.monster==target)queued=true;check(queued,"restored native player input queues selected card");report.addProperty("native_player_input",true);}else AbstractDungeon.actionManager.addCardQueueItem(new CardQueueItem(c,target));return;}throw new AssertionError("script has no attack");}
 void endTurn(){if(stage>=7){com.megacrit.cardcrawl.ui.buttons.EndTurnButton button=AbstractDungeon.overlayMenu.endTurnButton;com.megacrit.cardcrawl.helpers.Hitbox hb=(com.megacrit.cardcrawl.helpers.Hitbox)StateMod.field(button,"hb");com.megacrit.cardcrawl.helpers.input.InputHelper.mX=(int)hb.cX;com.megacrit.cardcrawl.helpers.input.InputHelper.mY=(int)hb.cY;hb.clickStarted=true;com.megacrit.cardcrawl.helpers.input.InputHelper.justReleasedClickLeft=true;try{button.update();}finally{com.megacrit.cardcrawl.helpers.input.InputHelper.justReleasedClickLeft=false;}check(!button.enabled,"restored native end-turn input is accepted");report.addProperty("native_end_turn_input",true);}else AbstractDungeon.overlayMenu.endTurnButton.disable(true);}
 void postSnapshotPlay(){if(scenario.startsWith("generated"))AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.common.MakeTempCardInHandAction(AbstractDungeon.returnTrulyRandomCardInCombat().makeCopy()));play();}
 boolean diagnosticRunning,diagnosticDone;
 void nativeDiagnostic()throws Exception{
  if(!Boolean.getBoolean("spireledger.diagnosticProbe")||diagnosticDone)return;diagnosticDone=true;diagnosticRunning=true;java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);DiagnosticReport.writer.submit(()->{try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  DiagnosticReport.error("native-test",new IllegalStateException("private path C:\\Users\\private-player\\save.autosave and /home/private-player/data"));
  com.badlogic.gdx.Input original=Gdx.input;
  Gdx.input=(com.badlogic.gdx.Input)java.lang.reflect.Proxy.newProxyInstance(com.badlogic.gdx.Input.class.getClassLoader(),new Class<?>[]{com.badlogic.gdx.Input.class},(proxy,method,args)->{
   if(method.getName().equals("isKeyPressed")){int key=(Integer)args[0];return key==com.badlogic.gdx.Input.Keys.R||key==com.badlogic.gdx.Input.Keys.CONTROL_LEFT||key==com.badlogic.gdx.Input.Keys.SHIFT_LEFT;}
   return method.invoke(original,args);
  });
  CardCrawlGame game=(CardCrawlGame)Gdx.app.getApplicationListener();long start=System.nanoTime();try{game.render();}finally{Gdx.input=original;}
  report.addProperty("diagnostic_request_ms",(System.nanoTime()-start)/1000000.0);check(!DiagnosticReport.status.isEmpty(),"real render hotkey reaches report exporter");check(!DiagnosticReport.request(),"repeated request cannot create concurrent exports");gate.countDown();
  long limit=System.currentTimeMillis()+5000;while(DiagnosticReport.busy.get()&&System.currentTimeMillis()<limit)Thread.sleep(10);
  check(DiagnosticReport.lastOutput!=null&&Files.exists(DiagnosticReport.lastOutput),"native diagnostic ZIP produced");check(DiagnosticReport.status.contains("saved"),"successful report status visible");
  boolean context=false,checkpoint=false,target=false;{check(DiagnosticReport.lastOutput.getFileName().toString().endsWith(".json"),"single JSON report");String json=new String(Files.readAllBytes(DiagnosticReport.lastOutput),StandardCharsets.UTF_8);check(!json.contains("private-player")&&!json.contains(System.getProperty("user.home")),"native diagnostic private paths filtered");JsonObject report=new JsonParser().parse(json).getAsJsonObject();if(report.has("context")){context=true;String c=report.get("context").toString();check(c.contains("native-test")&&c.contains(Recorder.VERSION),"context includes error and version");}checkpoint=report.has("last_checkpoint");target=report.has("replay_target");}
  check(context&&target,"native report retains recovery source and errors");check(Files.size(DiagnosticReport.lastOutput)<DiagnosticReport.MAX_TOTAL,"native report bounded bytes");report.addProperty("diagnostic_zip",DiagnosticReport.lastOutput.toString());report.addProperty("diagnostic_bytes",Files.size(DiagnosticReport.lastOutput));report.addProperty("diagnostic_render_hotkey",true);report.addProperty("diagnostic_frozen_export",ReplaySafety.quarantined);report.addProperty("diagnostic_checkpoint",checkpoint);game.render();
  if(!ReplaySafety.quarantined){Path first=DiagnosticReport.lastOutput;com.megacrit.cardcrawl.helpers.Hitbox button=(com.megacrit.cardcrawl.helpers.Hitbox)StateMod.field(StateMod.reportEntry,"hb");button.clicked=true;com.megacrit.cardcrawl.helpers.input.InputHelper.mX=(int)button.cX;com.megacrit.cardcrawl.helpers.input.InputHelper.mY=(int)button.cY;StateMod.reportEntry.update();limit=System.currentTimeMillis()+5000;while(DiagnosticReport.busy.get()&&System.currentTimeMillis()<limit)Thread.sleep(10);check(!first.equals(DiagnosticReport.lastOutput),"native Mods report button exports");report.addProperty("diagnostic_mods_button",true);}

 }
 void finish(Throwable error){if(error==null)try{nativeDiagnostic();}catch(Throwable diagnosticFailure){error=diagnosticFailure;}try{report.addProperty("status",error==null?"pass":"fail");report.addProperty("dataset_origin","test_fixture");report.addProperty("input","synthetic native hitbox/dropdown controls; not a human mouse demonstration");report.addProperty("mode",mode);report.addProperty("character",character);report.addProperty("scenario",scenario);report.addProperty("finite_repair",Boolean.getBoolean("spireledger.replayRepair"));report.addProperty("stage",stage);report.addProperty("error",error==null?"":error.toString());write("replay-probe-result.json",report);}catch(Exception e){e.printStackTrace();}if(error!=null)error.printStackTrace();Gdx.app.exit();}
 public void receivePostUpdate(){if(diagnosticRunning)return;try{
  if(System.currentTimeMillis()-started>240000)throw new AssertionError("stable point timeout stage "+stage+" turn="+GameActionManager.turn+" screen="+AbstractDungeon.screen+" manager="+AbstractDungeon.actionManager.phase+" current="+AbstractDungeon.actionManager.currentAction+" turnAction="+AbstractDungeon.actionManager.turnStartCurrentAction+" ended="+AbstractDungeon.actionManager.turnHasEnded+" enabled="+AbstractDungeon.overlayMenu.endTurnButton.enabled+" effects="+AbstractDungeon.effectList+" top="+AbstractDungeon.topLevelEffects);
  if(CardCrawlGame.mainMenuScreen!=null)verifySuperFastMode();
  if(stage>=62&&stage<=64){replayHistoryUI();return;}
  if(Boolean.getBoolean("spireledger.productionPractice")){
   if(!ReplayLauncher.practiceInitialized)return;
   if(stage==0){
    check(CardCrawlGame.dungeon!=null,"practice dungeon created");
    expected=read(System.getProperty("spireledger.replaySource")+"/before-facts.json");
    Path source=Paths.get(System.getProperty("spireledger.replaySource"),"home",".spire-ledger","runs",ReplayLauncher.practiceTargetRunId);JsonObject manifest=TurnFiles.read(source.resolve("replay/manifest.json"));JsonObject ref=null;for(JsonElement e:manifest.getAsJsonArray("snapshots"))if(ReplayLauncher.practiceTargetSnapshotId.equals(Recorder.string(e.getAsJsonObject(),"snapshot_id")))ref=e.getAsJsonObject();check(ref!=null,"source reference");check(ReplayLauncher.readTarget(source,ReplayLauncher.practiceTargetRunId,ref).get("environment").getAsJsonObject().get("dungeon_id").getAsString().equals(CardCrawlGame.dungeon.getClass().getSimpleName()),"actual dungeon subclass matches attachment");check(ReplayLauncher.readTarget(source,ReplayLauncher.practiceTargetRunId,ref).get("environment").equals(TurnAdapter.environment()),"production environment and card pools restored exactly");com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue.save(null);report.addProperty("production_environment_match",true);report.addProperty("production_save_blocked",true);report.addProperty("production_practice_startup",true);
    stage=7;wait=System.currentTimeMillis()+600;return;
   }
  }
  if(stage==0){if(CardCrawlGame.mainMenuScreen==null)return;Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.valueOf(character);CardCrawlGame.mainMenuScreen.fadedOut=true;stage=1;return;}
  if(!StateMod.ready()||CardCrawlGame.dungeon==null)return;
  // Production restore refuses a combat that has not reached an idle player turn, so wait for one after entering it.
  if(stage==15&&!TurnAdapter.restoreReady())return;
  if(stage==1||stage==15){
   if(stage==1){if(scenario.endsWith("act2")&&!(CardCrawlGame.dungeon instanceof com.megacrit.cardcrawl.dungeons.TheCity))CardCrawlGame.dungeon=new com.megacrit.cardcrawl.dungeons.TheCity(AbstractDungeon.player,new ArrayList<>());prepare();if(mode.equals("restore")&&Boolean.getBoolean("spireledger.productionRestore")){stage=15;return;}}if(mode.equals("restore")){JsonObject snapshot=read(System.getProperty("spireledger.replaySource")+"/turn.json");long t=System.nanoTime();if(Boolean.getBoolean("spireledger.productionRestore")){Path root=Paths.get(System.getProperty("spireledger.replaySource"),"home",".spire-ledger","runs");JsonObject point=null;try(java.nio.file.DirectoryStream<Path> dirs=Files.newDirectoryStream(root)){for(Path dir:dirs){Path manifest=dir.resolve("replay/manifest.json");if(!Files.exists(manifest))continue;JsonObject data=TurnFiles.read(manifest);for(JsonElement item:data.getAsJsonArray("snapshots")){JsonObject ref=item.getAsJsonObject();if(ref.get("turn").getAsInt()==2&&"recorded".equals(Recorder.string(ref,"availability")))point=TurnFiles.read(dir.resolve("replay").resolve(Recorder.string(ref,"relative_path")));}}}check(point!=null,"completed production turn two attachment");TurnAdapter.restore(point);report.addProperty("production_adapter_restore",true);}else{new SaveState(snapshot).loadState();if(Boolean.getBoolean("spireledger.replayRepair"))repair(read(System.getProperty("spireledger.replaySource")+"/supplement.json"));}report.addProperty("restore_ms",(System.nanoTime()-t)/1000000.0);expected=read(System.getProperty("spireledger.replaySource")+"/before-facts.json");stage=7;wait=System.currentTimeMillis()+600;return;}stage=2;return;}
  if(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.HAND_SELECT&&scenario.equals("gambling")){check(!stable(),"opening selection is not a stable capture point");report.addProperty("opening_selection_observed",true);AbstractDungeon.handCardSelectScreen.button.hb.clicked=true;return;}if(!stable())return;
  switch(stage){
   case 2:check(GameActionManager.turn==1,"turn one initial native draw");if(character.equals("DEFECT")){AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.defect.ChannelAction(new com.megacrit.cardcrawl.orbs.Dark()));stage=21;break;}if(character.equals("WATCHER")){AbstractDungeon.actionManager.addToBottom(new com.megacrit.cardcrawl.actions.watcher.ChangeStanceAction("Wrath"));stage=21;break;}play();stage=3;break;case 21:play();stage=3;break;
   case 3:check(!AbstractDungeon.actionManager.cardsPlayedThisCombat.isEmpty(),"native played card retained");AbstractDungeon.overlayMenu.endTurnButton.disable(true);stage=4;break;
   case 4:if(GameActionManager.turn<2)return;if(wait==0){wait=System.currentTimeMillis()+250;return;}if(System.currentTimeMillis()<wait)return;if(!System.getProperty("spireledger.replayFailure","").isEmpty()){failureCheck();return;}check(!AbstractDungeon.actionManager.cardsPlayedThisCombat.isEmpty(),"history across turn");JsonObject before=facts();write("before-facts.json",before);long t=System.nanoTime();SaveState state=Boolean.getBoolean("spireledger.replayNoCapture")?null:new SaveState();JsonObject json=state==null?new JsonObject():state.jsonEncode();write("supplement.json",supplement());report.addProperty("capture_ms",(System.nanoTime()-t)/1000000.0);check(before.equals(facts()),"capture does not mutate state/RNG");write("turn.json",json);report.addProperty("json_bytes",json.toString().getBytes(StandardCharsets.UTF_8).length);report.addProperty("history_size",AbstractDungeon.actionManager.cardsPlayedThisCombat.size());report.addProperty("capture_rng_unchanged",true);report.addProperty("without_capture_control",Boolean.getBoolean("spireledger.replayNoCapture"));postSnapshotPlay();stage=5;break;
   case 5:AbstractDungeon.overlayMenu.endTurnButton.disable(true);targetTurn=GameActionManager.turn+1;stage=6;break;
   case 6:if(GameActionManager.turn<targetTurn)return;verifyTurnFiles();write("after-facts.json",facts());write("after-state.json",new SaveState().jsonEncode());write("after-supplement.json",supplement());if(Boolean.getBoolean("spireledger.replayCurrentProbe")){expectedAfter=facts();expected=read("before-facts.json");replayBranch=StateMod.recorder.branch;HistoryScreen.open();check(HistoryScreen.active(),"native history overlay opened");stage=62;break;}if(Boolean.getBoolean("spireledger.launchPracticeProbe")){JsonObject manifest=TurnFiles.read(StateMod.recorder.directory.resolve("replay/manifest.json"));JsonObject ref=null;for(JsonElement item:manifest.getAsJsonArray("snapshots"))if(item.getAsJsonObject().get("turn").getAsInt()==2)ref=item.getAsJsonObject();check(ref!=null,"turn two launch source");HistoryIndex.Summary summary=new HistoryIndex.Summary();summary.id=StateMod.recorder.runId;TurnRecorder.enabled=false;sourceFiles.put(StateMod.recorder.directory.resolve("run.json"),Files.readAllBytes(StateMod.recorder.directory.resolve("run.json")));try(java.util.stream.Stream<Path> paths=Files.walk(StateMod.recorder.directory.resolve("replay"))){for(Iterator<Path> it=paths.iterator();it.hasNext();){Path file=it.next();if(Files.isRegularFile(file))sourceFiles.put(file,Files.readAllBytes(file));}}Path child=ReplayLauncher.launchPracticeInstance(summary,ref,StateMod.recorder.directory);report.addProperty("launched_practice_directory",child.toAbsolutePath().toString());stage=61;break;}finish(null);stage=99;break;
   case 61:if(ReplayLauncher.launchedProcess.isAlive())return;Path child=Paths.get(report.get("launched_practice_directory").getAsString());int exit=ReplayLauncher.launchedProcess.exitValue();JsonObject process=new JsonObject();process.addProperty("exit_code",exit);Files.write(child.resolve("process-result.json"),process.toString().getBytes(StandardCharsets.UTF_8));check(exit==0,"launched practice child exits cleanly");JsonObject childReport=read(child.resolve("replay-probe-result.json").toString());check("pass".equals(childReport.get("status").getAsString()),"launched practice child same-actions check");if(Boolean.getBoolean("spireledger.superFastModeProbe"))check(childReport.has("superfastmode_multiplier")&&childReport.get("superfastmode_multiplier").getAsFloat()==10f,"launched practice child actually runs SuperFastMode at 10x");for(Map.Entry<Path,byte[]> file:sourceFiles.entrySet())check(Arrays.equals(file.getValue(),Files.readAllBytes(file.getKey())),"practice preserves parent source: "+file.getKey());report.addProperty("source_files_unchanged",true);report.addProperty("child_exit",exit);finish(null);stage=99;break;
   case 7:if(System.currentTimeMillis()<wait)return;JsonObject actual=facts();write("restored-supplement.json",supplement());write("restored-facts.json",actual);write("restored-state.json",new SaveState().jsonEncode());report.addProperty("snapshot_facts_match",actual.equals(expected));report.addProperty("expected_history_size",expected.getAsJsonArray("history").size());report.addProperty("restored_history_size",actual.getAsJsonArray("history").size());postSnapshotPlay();stage=8;break;
   case 8:endTurn();targetTurn=GameActionManager.turn+1;stage=9;break;
   case 9:if(GameActionManager.turn<targetTurn)return;JsonObject after=facts();write("after-facts.json",after);write("after-state.json",new SaveState().jsonEncode());write("after-supplement.json",supplement());report.addProperty("same_actions_facts_match",after.equals(expectedAfter!=null?expectedAfter:read(System.getProperty("spireledger.replaySource")+"/after-facts.json")));check(report.get("snapshot_facts_match").getAsBoolean()&&report.get("same_actions_facts_match").getAsBoolean(),"cross JVM state/history and same-action results must match");if(Boolean.getBoolean("spireledger.replayCurrentProbe")){HistoryScreen history=new HistoryScreen();history.detail=StateMod.recorder.document();history.selected=HistoryIndex.summarize(history.detail,true);history.manifest=TurnFiles.read(StateMod.recorder.directory.resolve("replay/manifest.json"));history.prepareFloors();history.makeLines();boolean earlier=false;for(JsonObject point:history.availableTurns)if(point.get("turn").getAsInt()==2&&!Recorder.string(point,"branch_id").equals(StateMod.recorder.branch))earlier=true;check(earlier,"native history default replay list retains earlier ancestor turn");history.close();report.addProperty("history_ancestor_turn_available",true);}finish(null);stage=99;break;
  }
 }catch(Throwable e){finish(e);stage=99;}}
}
