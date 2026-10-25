package sts1state;

import basemod.BaseMod;
import basemod.interfaces.*;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.cards.*;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.saveAndContinue.*;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import javassist.expr.*;
import javassist.CannotCompileException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Isolated fixture driver against the ORIGINAL game JAR and real MTS patches.
 * Not shipped in the recorder. Does not claim complete human-played run coverage. */
@SpireInitializer
public class NativeProbe implements PostUpdateSubscriber,PostInitializeSubscriber {
    int stage=0,deckSize;long next;RewardItem reward;String runId,parentBranch;JsonObject saveRef;
    JsonArray anchorChecks=new JsonArray();int curseCount,omamoriCount,upgradeCount;AbstractCard mirrorCard;
    int previousStage=-1;long stageSince;
    static class PendingEffect extends com.megacrit.cardcrawl.vfx.AbstractGameEffect {public void update(){}public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch b){}public void dispose(){}}
    public static void initialize(){BaseMod.subscribe(new NativeProbe());}
    public void receivePostInitialize(){System.out.println("STATE_PROBE_PATCHES_LOADED");}
    @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {
        @SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(String distributor){return SpireReturn.Return(new GogIntegration());}
    }
    @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {
        @SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}
    }
    static void nativeCall(Object instance,String name,Class<?>[] types,Object... args)throws Exception{Method m=instance.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(instance,args);}
    void fixture(int floor){AbstractDungeon.floorNum=floor;AbstractDungeon.actNum=floor>=34?3:1;MapRoomNode n=new MapRoomNode(0,0);n.room=new MonsterRoom();n.room.phase=AbstractRoom.RoomPhase.COMPLETE;AbstractDungeon.setCurrMapNode(n);AbstractDungeon.isScreenUp=false;AbstractDungeon.screen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.previousScreen=AbstractDungeon.CurrentScreen.NONE;AbstractDungeon.actionManager.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.effectList.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();}
    void open(String id){ArrayList<AbstractCard> cards=new ArrayList<>();cards.add(CardLibrary.getCard(id).makeCopy());reward=new RewardItem();reward.cards=cards;AbstractDungeon.combatRewardScreen.rewards.clear();AbstractDungeon.combatRewardScreen.rewards.add(reward);AbstractDungeon.cardRewardScreen.open(cards,reward,"Native fixture");}
    void pick()throws Exception{StateMod.withAutomation("native-integration-fixture",()->{try{nativeCall(AbstractDungeon.cardRewardScreen,"acquireCard",new Class<?>[]{AbstractCard.class},reward.cards.get(0));nativeCall(AbstractDungeon.cardRewardScreen,"takeReward",new Class<?>[]{});AbstractDungeon.closeCurrentScreen();}catch(Exception e){throw new RuntimeException(e);}});}
    JsonObject lastDecision(){JsonObject last=null;for(JsonElement e:StateMod.recorder.document().getAsJsonArray("records"))if("decision".equals(Recorder.string(e.getAsJsonObject(),"type")))last=e.getAsJsonObject();return last;}
    boolean lastConfirmed(){String id=Recorder.string(lastDecision(),"decision_id");for(JsonElement item:StateMod.recorder.document().getAsJsonArray("records")){JsonObject e=item.getAsJsonObject();if("result".equals(Recorder.string(e,"type"))&&id.equals(Recorder.string(e,"decision_id"))&&"confirmed".equals(Recorder.string(e,"status")))return true;}return false;}
    void anchor(String label)throws Exception {
        JsonObject expected=Snapshot.capture();pick();JsonObject before=lastDecision().getAsJsonObject("before");
        require(before.equals(expected),"direct confirm anchor "+label);require("known_automation".equals(Recorder.string(lastDecision(),"action_source")),"explicit automation remains automation");
        JsonArray cards=before.getAsJsonArray("deck");require(cards.size()==AbstractDungeon.player.masterDeck.size(),"deck count "+label);
        for(int i=0;i<cards.size();i++){AbstractCard c=AbstractDungeon.player.masterDeck.group.get(i);JsonObject o=cards.get(i).getAsJsonObject();require(c.cardID.equals(Recorder.string(o,"id"))&&c.timesUpgraded==o.get("upgrades").getAsInt()&&c.misc==o.get("misc").getAsInt()&&c.baseDamage==o.get("base_damage").getAsInt()&&c.baseBlock==o.get("base_block").getAsInt()&&c.baseMagicNumber==o.get("base_magic").getAsInt()&&c.cost==o.get("base_cost").getAsInt(),"live permanent values "+label);}
        require(before.get("hp").getAsInt()==AbstractDungeon.player.currentHealth&&before.get("max_hp").getAsInt()==AbstractDungeon.player.maxHealth&&before.get("gold").getAsInt()==AbstractDungeon.player.gold,"live resources "+label);
        for(int i=0;i<AbstractDungeon.player.relics.size();i++)require(before.getAsJsonArray("relics").get(i).getAsJsonObject().get("counter").getAsInt()==AbstractDungeon.player.relics.get(i).counter,"live relic counter "+label);
        anchorChecks.add(label);
    }
    int parasites(){return (int)AbstractDungeon.player.masterDeck.group.stream().filter(c->c.cardID.equals("Parasite")).count();}
    int upgrades(){return AbstractDungeon.player.masterDeck.group.stream().mapToInt(c->c.timesUpgraded).sum();}
    void mass(boolean inflict){AbstractDungeon.getCurrRoom().phase=AbstractRoom.RoomPhase.COMBAT;com.megacrit.cardcrawl.monsters.beyond.WrithingMass m=new com.megacrit.cardcrawl.monsters.beyond.WrithingMass();m.nextMove=(byte)(inflict?4:0);m.takeTurn();for(com.megacrit.cardcrawl.actions.AbstractGameAction a:new ArrayList<>(AbstractDungeon.actionManager.actions))if(a instanceof com.megacrit.cardcrawl.actions.unique.AddCardToDeckAction)a.update();AbstractDungeon.actionManager.clear();AbstractDungeon.getCurrRoom().phase=AbstractRoom.RoomPhase.COMPLETE;}
    void chest(boolean boss){new com.megacrit.cardcrawl.rewards.chests.SmallChest().open(boss);AbstractDungeon.actionManager.clear();}
    void uiPick()throws Exception {
        Settings.isTouchScreen=true;java.lang.reflect.Field f=CardRewardScreen.class.getDeclaredField("touchCard");f.setAccessible(true);f.set(AbstractDungeon.cardRewardScreen,reward.cards.get(0));
        Object button=StateMod.field(AbstractDungeon.cardRewardScreen,"confirmButton");com.megacrit.cardcrawl.helpers.Hitbox hb=(com.megacrit.cardcrawl.helpers.Hitbox)StateMod.field(button,"hb");hb.clicked=true;
        AbstractDungeon.cardRewardScreen.update();Settings.isTouchScreen=false;
        require(System.getProperty("sts1state.expectUiActor","unknown").equals(Recorder.string(lastDecision(),"action_source")),"actual native UI classification");anchorChecks.add("native_ui_"+Recorder.string(lastDecision(),"action_source"));
    }
    int decisions(){int n=0;for(JsonElement e:StateMod.recorder.document().getAsJsonArray("records"))if("decision".equals(Recorder.string(e.getAsJsonObject(),"type")))n++;return n;}
    static void require(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static void observe(){StateMod.nextObserve=0;StateMod.INSTANCE.receivePostUpdate();}
    public void receivePostUpdate(){
        if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+1200;
        try {
            if(stage!=previousStage){previousStage=stage;stageSince=System.currentTimeMillis();System.out.println("STATE_FIXTURE_STAGE "+stage);}
            if(System.currentTimeMillis()-stageSince>45000)throw new AssertionError("stage timeout; screen="+AbstractDungeon.screen+" effects="+AbstractDungeon.effectList+" top="+AbstractDungeon.topLevelEffects+" actions="+AbstractDungeon.actionManager.actions+" souls="+SoulGroup.isActive());
            if(stage==0){if(CardCrawlGame.mainMenuScreen==null)return;Settings.seed=1234567890123456789L;Settings.isDailyRun=false;Settings.isTrial=false;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage++;return;}
            if(!StateMod.ready()||CardCrawlGame.dungeon==null)return;
            if(AbstractDungeon.ftue!=null)AbstractDungeon.ftue=null;
            require(Snapshot.blockingEffects(Collections.singletonList(new PendingEffect())),"unknown pending effect must block");
            if(stage==1&&Boolean.getBoolean("sts1state.crossJvm")){
                fixture(41);CardCrawlGame.loadingSave=true;nativeCall(com.badlogic.gdx.Gdx.app.getApplicationListener(),"loadPlayerSave",new Class<?>[]{AbstractPlayer.class},AbstractDungeon.player);CardCrawlGame.loadingSave=false;StateMod.ensure();
                require(StateMod.recorder.runId.equals(System.getProperty("sts1state.expectedRun")),"cross JVM run restored");
                JsonObject point=null,load=null;for(JsonElement element:StateMod.recorder.document().getAsJsonArray("records")){JsonObject e=element.getAsJsonObject();if("checkpoint".equals(Recorder.string(e,"type")))point=e;if("load".equals(Recorder.string(e,"type")))load=e;}
                require(load!=null&&"local_checkpoint_found".equals(Recorder.string(load,"parent_status")),"cross JVM checkpoint parent");require(point!=null&&Snapshot.buildSignature(point.getAsJsonObject("state")).equals(Snapshot.buildSignature(Snapshot.capture())),"cross JVM actual loaded build");StateMod.end("cross-jvm-native-load-fixture");JsonObject report=new JsonObject();report.addProperty("status","pass");report.addProperty("run_id",StateMod.recorder.runId);report.addProperty("branch_id",StateMod.recorder.branch);report.addProperty("evidence","second JVM; original loadPlayerSave + BaseMod disk fields; not main-menu continue UI");Files.write(Paths.get("cross-jvm-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));stage=99;com.badlogic.gdx.Gdx.app.exit();return;
            }
            if(stage==1&&Boolean.getBoolean("sts1state.uiOnly")){fixture(1);TipTracker.disableAllFtues();StateMod.ensure();stage=41;return;}
            if(stage==1&&Boolean.getBoolean("sts1state.miscOnly")){fixture(1);TipTracker.disableAllFtues();StateMod.ensure();open("RitualDagger");reward.cards.add(CardLibrary.getCard("RitualDagger").makeCopy());anchor("native_nonzero_misc_candidates");require(lastDecision().getAsJsonArray("candidates").size()==3,"two same-name candidates plus actual skip");stage=50;return;}
            switch(stage){
            case 1: fixture(1);require("OTHER".equals(Snapshot.source(false)),"non-reward selection must not inherit monster-room reward source");AbstractDungeon.getCurrRoom().phase=AbstractRoom.RoomPhase.COMBAT;require("COMBAT_SELECTION".equals(Snapshot.source(false)),"Discovery in combat is not a permanent reward");require("COMBAT_SELECTION".equals(Snapshot.source(true)),"combat phase cannot be a postcombat reward");AbstractDungeon.getCurrRoom().phase=AbstractRoom.RoomPhase.COMPLETE;require("COMBAT_REWARD".equals(Snapshot.source(true)),"real reward classification");TipTracker.disableAllFtues();require(StateMod.reviewedMod("stslib","2.12.0")&&!StateMod.reviewedMod("RunHistoryPlus","1.8.3")&&!StateMod.reviewedMod("automation-mod","1.8.3"),"only BaseMod + StSLib reviewed");StateMod.ensure();deckSize=AbstractDungeon.player.masterDeck.size();open("Inflame");stage++;break;
            case 2: int n=decisions();AbstractDungeon.closeCurrentScreen();new FrozenEgg2().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);AbstractDungeon.cardRewardScreen.reopen();require(decisions()==n,"close/reopen must not commit");AbstractDungeon.player.gold=117;pick();require(lastDecision().getAsJsonObject("before").get("gold").getAsInt()==117,"current gold");require(lastDecision().getAsJsonObject("before").getAsJsonArray("relics").toString().contains("Frozen Egg 2"),"egg at confirm");stage++;break;
            case 3: if(!Snapshot.settled())return;observe();require(AbstractDungeon.player.masterDeck.size()==deckSize+1,"native obtain once");require(AbstractDungeon.player.masterDeck.group.stream().anyMatch(c->c.cardID.equals("Inflame")&&c.timesUpgraded==1),"native egg upgrade");fixture(2);open("Anger");pick();stage++;break;
            case 4: if(!Snapshot.settled())return;observe();require(AbstractDungeon.player.masterDeck.group.stream().anyMatch(c->c.cardID.equals("Anger")&&c.timesUpgraded==0),"unaffected type stays base");fixture(0);open("Apotheosis");pick();require("NEOW".equals(Recorder.string(lastDecision(),"source")),"floor zero excluded source");stage++;break;
            case 5: if(!Snapshot.settled())return;fixture(48);new SingingBowl().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);open("Anger");int hp=AbstractDungeon.player.maxHealth;PendingEffect bowlBlock=new PendingEffect();AbstractDungeon.effectList.add(bowlBlock);Object button=StateMod.field(AbstractDungeon.cardRewardScreen,"bowlButton");StateMod.withAutomation("native-integration-fixture",()->{try{nativeCall(button,"onClick",new Class<?>[]{});}catch(Exception e){throw new RuntimeException(e);}});require(AbstractDungeon.player.maxHealth==hp+2,"native bowl effect");require("bowl".equals(Recorder.string(lastDecision(),"candidate_id")),"bowl separate");observe();require(!lastConfirmed(),"bowl must wait for pending effects");anchorChecks.add("bowl_waits_for_effects");AbstractDungeon.effectList.remove(bowlBlock);AbstractDungeon.closeCurrentScreen();stage++;break;
            case 6: if(!Snapshot.settled())return;observe();require(lastConfirmed(),"bowl confirms after effects settle");fixture(49);open("Anger");PendingEffect skipBlock=new PendingEffect();AbstractDungeon.effectList.add(skipBlock);AbstractDungeon.closeCurrentScreen();int old=decisions();StateMod.withAutomation("native-integration-fixture",()->reward.recordCardSkipMetrics());reward.recordCardSkipMetrics();require(decisions()==old+1,"final skip once");require("skip".equals(Recorder.string(lastDecision(),"candidate_id")),"skip fact");observe();require(!lastConfirmed(),"skip must wait for pending effects");anchorChecks.add("skip_waits_for_effects");AbstractDungeon.effectList.remove(skipBlock);stage++;break;
            case 7: if(!Snapshot.settled())return;observe();require(lastConfirmed(),"skip confirms after effects settle");fixture(16);new Astrolabe().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);require(StateMod.mutationBefore!=null,"astrolabe prior anchor");require(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID,"astrolabe waits");AbstractDungeon.gridSelectScreen.selectedCards.addAll(AbstractDungeon.player.masterDeck.group.subList(0,3));AbstractDungeon.closeCurrentScreen();stage++;break;
            case 8: if(!Snapshot.settled())return;observe();require(StateMod.mutationBefore==null,"astrolabe effect settled");new EmptyCage().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);deckSize=AbstractDungeon.player.masterDeck.size();AbstractDungeon.gridSelectScreen.selectedCards.addAll(AbstractDungeon.player.masterDeck.group.subList(0,2));AbstractDungeon.closeCurrentScreen();stage++;break;
            case 9: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.size()==deckSize-2,"native cage removes");observe();new DollysMirror().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);deckSize=AbstractDungeon.player.masterDeck.size();AbstractDungeon.gridSelectScreen.selectedCards.add(AbstractDungeon.player.masterDeck.group.get(0));AbstractDungeon.closeCurrentScreen();stage++;break;
            case 10: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.size()==deckSize+1,"native mirror copy");observe();new PandorasBox().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);require(StateMod.mutationBefore!=null,"pandora pending anchor");stage++;break;
            case 11: if(AbstractDungeon.screen==AbstractDungeon.CurrentScreen.GRID){AbstractDungeon.gridSelectScreen.confirmButton.hb.clicked=true;AbstractDungeon.gridSelectScreen.update();return;}if(!Snapshot.settled())return;observe();stage=20;break;
            case 12: int rng=AbstractDungeon.cardRng.counter;String sig=Snapshot.capture().toString();StateMod.toggle(false);StateMod.toggle(true);observe();require(AbstractDungeon.cardRng.counter==rng,"recording must not consume card RNG");require(Snapshot.capture().toString().equals(sig),"toggle changes no player state");saveRef=StateMod.INSTANCE.onSave();require(saveRef!=null&&saveRef.has("checkpoint_id"),"savable reference");runId=StateMod.recorder.runId;parentBranch=StateMod.recorder.branch;AbstractDungeon.nextRoom=null;SaveAndContinue.save(new SaveFile(SaveFile.SaveType.ENTER_ROOM));stage++;break;
            case 13: CardCrawlGame.loadingSave=true;nativeCall(com.badlogic.gdx.Gdx.app.getApplicationListener(),"loadPlayerSave",new Class<?>[]{AbstractPlayer.class},AbstractDungeon.player);CardCrawlGame.loadingSave=false;StateMod.ensure();require(StateMod.recorder.runId.equals(runId),"native save/load run identity");require(!StateMod.recorder.branch.equals(parentBranch),"native save/load new branch");JsonObject lastLoad=null;for(JsonElement e:StateMod.recorder.document().getAsJsonArray("records"))if("load".equals(Recorder.string(e.getAsJsonObject(),"type")))lastLoad=e.getAsJsonObject();require(lastLoad!=null&&"local_checkpoint_found".equals(Recorder.string(lastLoad,"parent_status")),"native saved parent reference recovered");StateMod.end("native-fixture-complete");JsonObject report=new JsonObject();report.addProperty("status","pass");report.addProperty("evidence","original-game-jar; MTS patched hooks; fixture-driven native methods/effects and disk save/load; not complete played runs");report.add("anchor_checks",anchorChecks);report.addProperty("dataset_origin","test_fixture");report.addProperty("decisions",decisions());report.addProperty("run_id",StateMod.recorder.runId);report.addProperty("seed",String.valueOf(Settings.seed));Files.write(Paths.get("probe-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));System.out.println("STATE_NATIVE_PROBE_PASS "+report);stage++;com.badlogic.gdx.Gdx.app.exit();break;
            case 20: fixture(30);open("Anger");anchor("after_pandora_cage_astrolabe_mirror");stage++;break;
            case 21: if(!Snapshot.settled())return;observe();fixture(31);curseCount=parasites();mass(true);require(!Snapshot.settled(),"parasite effect pending");stage++;break;
            case 22: if(!Snapshot.settled())return;require(parasites()==curseCount+1,"native mass parasite");open("Anger");anchor("parasite_present");stage++;break;
            case 23: if(!Snapshot.settled())return;fixture(32);curseCount=parasites();mass(false);require(parasites()==curseCount,"mass non-inflict");open("Anger");anchor("parasite_absent_move");stage++;break;
            case 24: if(!Snapshot.settled())return;fixture(33);new Omamori().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);omamoriCount=AbstractDungeon.player.getRelic("Omamori").counter;curseCount=parasites();mass(true);stage++;break;
            case 25: if(!Snapshot.settled())return;require(parasites()==curseCount&&AbstractDungeon.player.getRelic("Omamori").counter==omamoriCount-1,"parasite intercepted count");open("Anger");anchor("parasite_omamori");stage++;break;
            case 26: if(!Snapshot.settled())return;fixture(34);new CursedKey().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);omamoriCount=AbstractDungeon.player.getRelic("Omamori").counter;deckSize=AbstractDungeon.player.masterDeck.size();chest(false);stage++;break;
            case 27: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.size()==deckSize&&AbstractDungeon.player.getRelic("Omamori").counter==omamoriCount-1,"cursed chest intercepted");open("Anger");anchor("key_omamori");stage++;break;
            case 28: if(!Snapshot.settled())return;fixture(35);deckSize=AbstractDungeon.player.masterDeck.size();chest(false);require(!Snapshot.settled(),"chest obtain pending");stage++;break;
            case 29: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.size()==deckSize+1,"cursed chest depleted omamori");open("Anger");anchor("key_curse");stage++;break;
            case 30: if(!Snapshot.settled())return;fixture(36);deckSize=AbstractDungeon.player.masterDeck.size();chest(true);require(AbstractDungeon.player.masterDeck.size()==deckSize,"boss chest no curse");open("Anger");anchor("key_boss_no_curse");stage++;break;
            case 31: if(!Snapshot.settled())return;fixture(37);upgradeCount=upgrades();new WarPaint().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);require(upgrades()>upgradeCount,"warpaint native upgrades");stage++;break;
            case 32: if(!Snapshot.settled())return;open("Anger");anchor("war_paint");stage++;break;
            case 33: if(!Snapshot.settled())return;fixture(38);upgradeCount=upgrades();new Whetstone().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);require(upgrades()>upgradeCount,"whetstone native upgrades");stage++;break;
            case 34: if(!Snapshot.settled())return;open("Anger");anchor("whetstone");stage++;break;
            case 35: if(!Snapshot.settled())return;fixture(39);upgradeCount=upgrades();new TinyHouse().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);require(upgrades()>upgradeCount,"tinyhouse native upgrades");stage++;break;
            case 36: if(!Snapshot.settled())return;AbstractDungeon.closeCurrentScreen();open("Anger");anchor("tiny_house_resources");stage++;break;
            case 37: if(!Snapshot.settled())return;fixture(40);mirrorCard=AbstractDungeon.player.masterDeck.group.stream().filter(c->c.timesUpgraded>0).findFirst().get();deckSize=AbstractDungeon.player.masterDeck.size();new DollysMirror().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);AbstractDungeon.gridSelectScreen.selectedCards.add(mirrorCard);AbstractDungeon.closeCurrentScreen();stage++;break;
            case 38: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.size()==deckSize+1,"upgraded copy acquired");require(AbstractDungeon.player.masterDeck.group.stream().filter(c->c.cardID.equals(mirrorCard.cardID)&&c.timesUpgraded==mirrorCard.timesUpgraded).count()>=2,"mirror preserves upgrade");open("Anger");anchor("mirror_upgraded_duplicates");stage++;break;
            case 39: if(!Snapshot.settled())return;fixture(0);open("Apotheosis");pick();stage++;break;
            case 40: if(!Snapshot.settled())return;observe();fixture(1);open("Anger");anchor("neow_first_combat");stage++;break;
            case 41: if(!Snapshot.settled())return;fixture(41);open("Anger");uiPick();stage++;break;
            case 42: if(!Snapshot.settled())return;observe();stage=12;break;
            case 50: if(!Snapshot.settled())return;observe();mirrorCard=AbstractDungeon.player.masterDeck.group.stream().filter(c->c.cardID.equals("RitualDagger")).findFirst().get();require(mirrorCard.misc==15,"native nonzero misc obtain");mirrorCard.upgrade();new DollysMirror().instantObtain(AbstractDungeon.player,AbstractDungeon.player.relics.size(),true);AbstractDungeon.gridSelectScreen.selectedCards.add(mirrorCard);AbstractDungeon.closeCurrentScreen();stage++;break;
            case 51: if(!Snapshot.settled())return;require(AbstractDungeon.player.masterDeck.group.stream().filter(c->c.cardID.equals("RitualDagger")&&c.misc==15&&c.timesUpgraded==1).count()==2,"native copy preserves nonzero misc and upgrades");fixture(2);open("Anger");anchor("nonzero_misc_upgraded_copy_live_before");stage++;break;
            case 52: {if(!Snapshot.settled())return;observe();StateMod.end("nonzero-misc-fixture");JsonObject miscReport=new JsonObject();miscReport.addProperty("status","pass");miscReport.add("anchor_checks",anchorChecks);miscReport.addProperty("run_id",StateMod.recorder.runId);miscReport.addProperty("dataset_origin","test_fixture");Files.write(Paths.get("probe-result.json"),miscReport.toString().getBytes(StandardCharsets.UTF_8));stage=99;com.badlogic.gdx.Gdx.app.exit();break;}
            default:return;
            }
        }catch(Throwable e){try{JsonObject fail=new JsonObject();fail.addProperty("status","failed");fail.addProperty("stage",stage);fail.addProperty("error",e.toString());Files.write(Paths.get("probe-result.json"),fail.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}e.printStackTrace();stage=99;com.badlogic.gdx.Gdx.app.exit();}
    }
}
