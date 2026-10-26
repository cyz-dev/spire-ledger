package spireledger;

import basemod.*;
import basemod.abstracts.CustomSavable;
import basemod.interfaces.*;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.badlogic.gdx.graphics.Color;
import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.screens.CardRewardScreen;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.neow.*;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.shop.ShopScreen;
import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;

@SpireInitializer
public final class StateMod implements PostInitializeSubscriber,PostUpdateSubscriber,StartGameSubscriber,CustomSavable<JsonObject> {
    static final StateMod INSTANCE=new StateMod();
    static final Path ROOT=Paths.get(System.getProperty("user.home"),".spire-ledger","runs");
    // State Notes 0.4.0 records stay where the player left them; read-only.
    static final Path LEGACY_ROOT=Paths.get(System.getProperty("user.home"),".sts1-state-notes","runs");
    static Recorder recorder;
    static ModLabeledButton historyEntry,reportEntry;
    static HistoryTopPanelItem historyTopPanel;
    static boolean enabled=true,failed=false;
    static String status="Ready — local recording",automation=null;
    static JsonObject loading,last,mutationBefore;
    static String mutationReason;
    static final Map<Object,Recorder> metricsOwners=Collections.synchronizedMap(new IdentityHashMap<Object,Recorder>());
    static int lastFloor=-1,lastAct=-1,act3Floor=-1;
    static boolean heartExit,abandoning;
    static long nextObserve;
    static Object player;
    static boolean startRequested=true,loadingExpected=false;
    static final IdentityHashMap<Object,String> rewardKeys=new IdentityHashMap<>();
    static final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    public static void initialize(){EmbeddedSaveState.initialize();BaseMod.subscribe(INSTANCE);BaseMod.addSaveField("spire-ledger:checkpoint",INSTANCE);}
    static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception ex){throw new IllegalStateException("Missing game field: "+name,ex);}}
    interface Action {void run()throws Exception;}
    static void safely(Action a){if(failed)return;try{a.run();}catch(Exception ex){DiagnosticReport.error("recording",ex);failed=true;status="Recording stopped: "+ex.getClass().getSimpleName()+" (see log)";System.err.println("[Spire Ledger] "+status);ex.printStackTrace();if(recorder!=null)try{recorder.close();}catch(Exception ignored){System.err.println("[Spire Ledger] Last export failed; recover events.jsonl");}recorder=null;}}
    static boolean ready(){return enabled&&!failed&&!ReplaySafety.quarantined&&!CardCrawlGame.loadingSave&&CardCrawlGame.mode==CardCrawlGame.GameMode.GAMEPLAY&&AbstractDungeon.player!=null&&AbstractDungeon.player.masterDeck!=null&&AbstractDungeon.getCurrMapNode()!=null&&AbstractDungeon.getCurrMapNode().getRoom()!=null;}
    static final String SPEED_MOD="superfastmode",SPEED_VERSION="1.0.8";
    static boolean reviewedMod(String id,String version){return "basemod".equals(id)||"stslib".equals(id)||"spire-ledger".equals(id)||SPEED_MOD.equals(id)&&SPEED_VERSION.equals(version);}
    static String actor(){
        if(automation!=null)return "known_automation";
        // Unreviewed mods leave the action source unknown, even on a native UI path.
        if(Loader.MODINFOS!=null)for(ModInfo info:Loader.MODINFOS)if(!reviewedMod(info.ID,String.valueOf(info.ModVersion)))return "unknown";
        // Direct mod calls cannot be identified as human actions. Only native UI commit call chains qualify.
        for(StackTraceElement f:Thread.currentThread().getStackTrace()){
            String c=f.getClassName(),m=f.getMethodName();
            if(c.startsWith("com.megacrit.cardcrawl.")&&(m.equals("cardSelectUpdate")||m.equals("update")))return "human";
            if(!c.startsWith("spireledger.")&&!c.startsWith("java.")&&!c.startsWith("com.megacrit.cardcrawl.")&&!c.startsWith("com.evacipated.")&&!c.startsWith("basemod."))return "unknown";
        }return "unknown";
    }
    /** Automation must wrap its native commit call. Label does not change gameplay. */
    public static void withAutomation(String label,Runnable action){String old=automation;automation=label;try{action.run();}finally{automation=old;}}
    static String key(Object reward){return rewardKeys.computeIfAbsent(reward,r->UUID.randomUUID().toString());}
    static void ensure()throws Exception{
        if(!ready())return;
        if(recorder!=null&&player==AbstractDungeon.player&&!startRequested)return;
        if(recorder!=null){recorder.close();recorder=null;}
        rewardKeys.clear();seen.clear();Snapshot.identities.clear();last=null;mutationBefore=null;lastFloor=-1;lastAct=-1;abandoning=false;
        JsonObject meta=new JsonObject();meta.addProperty("mod_version",Recorder.VERSION);meta.addProperty("game_version",CardCrawlGame.VERSION_NUM);meta.addProperty("seed",String.valueOf(Settings.seed));meta.addProperty("save_slot",CardCrawlGame.saveSlot);meta.add("play_id",JsonNull.INSTANCE);meta.addProperty("play_id_status","unknown_until_game_metrics");meta.addProperty("coverage",loadingExpected?"loaded_or_partial":"from_enable_or_start");meta.addProperty("privacy","local; no account, paths, audio, images or upload");meta.addProperty("checkpoint_attachments",false);
        JsonArray mods=new JsonArray();if(Loader.MODINFOS!=null)for(ModInfo info:Loader.MODINFOS){JsonObject mod=new JsonObject();mod.addProperty("id",info.ID);mod.addProperty("version",String.valueOf(info.ModVersion));mods.add(mod);}meta.add("mods",mods);
        meta.addProperty("human_path_review","native UI; BaseMod + StSLib, optionally SuperFastMode 1.0.8; other mods unknown");
        if(Boolean.getBoolean("spireledger.practiceMode")){meta.addProperty("session_kind","practice");meta.addProperty("parent_run_id",ReplayLauncher.practiceTargetRunId);meta.addProperty("parent_snapshot_id",ReplayLauncher.practiceTargetSnapshotId);}
        if(Boolean.getBoolean("spireledger.fixture"))meta.addProperty("dataset_origin","test_fixture");
        String restore=loading==null?null:Recorder.string(loading,"run_id");
        if(restore!=null&&!restore.matches("[a-fA-F0-9-]{36}"))restore=null;
        recorder=new Recorder(ROOT,meta,restore);
        if(loading!=null&&loading.has("deck"))Snapshot.restoreIdentities(loading.getAsJsonArray("deck"));
        JsonObject state=Snapshot.capture();JsonObject e=Recorder.object("coverage");e.addProperty("status",loadingExpected?"loaded":"started");e.addProperty("floor_start",AbstractDungeon.floorNum);e.add("state",state);recorder.append(e);
        if(loadingExpected)recorder.resume(loading==null?new JsonObject():loading,state);
        // The game save is the lineage truth: an SL to before the act-3 exit must forget it.
        act3Floor=loading!=null&&loading.has("act3_cleared_floor")?loading.get("act3_cleared_floor").getAsInt():-1;heartExit=false;
        loading=null;loadingExpected=false;player=AbstractDungeon.player;startRequested=false;last=state;recorder.flush();status="Recording "+recorder.runId.substring(0,8);
    }
    static JsonArray candidates(List<AbstractCard> cards,boolean skip,boolean bowl){JsonArray all=new JsonArray();for(int i=0;i<cards.size();i++){JsonObject c=Recorder.object("card");c.addProperty("candidate_id","card:"+i);c.add("card",Snapshot.card(cards.get(i)));all.add(c);}if(skip){JsonObject c=Recorder.object("skip");c.addProperty("candidate_id","skip");all.add(c);}if(bowl){JsonObject c=Recorder.object("bowl");c.addProperty("candidate_id","bowl");all.add(c);}return all;}
    static void choose(CardRewardScreen screen,AbstractCard card){if(!ready())return;safely(()->{
        ensure();Object reward=screen.rItem!=null?screen.rItem:screen.rewardGroup;if(seen.contains(reward))return;
        int index=screen.rewardGroup.indexOf(card);if(index<0)throw new IllegalStateException("Committed card missing from candidates");
        // Buttons are hidden by the native click before acquireCard; skippable is the actual screen contract.
        boolean skip=(Boolean)field(screen,"skippable");boolean bowl=screen.rItem!=null&&AbstractDungeon.player.hasRelic("Singing Bowl");
        recorder.begin(key(reward),Snapshot.source(screen.rItem!=null),Snapshot.capture(),candidates(screen.rewardGroup,skip,bowl),"card:"+index,actor());seen.add(reward);
    });}
    static void bowl(Object button){if(!ready())return;safely(()->{ensure();RewardItem r=(RewardItem)field(button,"rItem");if(r==null||seen.contains(r))return;recorder.begin(key(r),Snapshot.source(true),Snapshot.capture(),candidates(r.cards,true,true),"bowl",actor());seen.add(r);});}
    static void skip(RewardItem r){if(!ready()||r.cards==null||r.cards.isEmpty())return;safely(()->{ensure();if(seen.contains(r))return;recorder.begin(key(r),Snapshot.source(true),Snapshot.capture(),candidates(r.cards,true,AbstractDungeon.player.hasRelic("Singing Bowl")),"skip",actor());seen.add(r);recorder.flush();});}
    static void abandon(){if(!ready()||AbstractDungeon.screen!=AbstractDungeon.CurrentScreen.COMBAT_REWARD)return;for(RewardItem r:new ArrayList<>(AbstractDungeon.combatRewardScreen.rewards))if(r.type==RewardItem.RewardType.CARD)skip(r);}
    static void screenSkip(CardRewardScreen s){if(!ready()||s.rItem!=null)return;safely(()->{ensure();Object r=s.rewardGroup;if(seen.contains(r))return;boolean legal=(Boolean)field(s,"skippable");if(!legal)return;recorder.begin(key(r),Snapshot.source(false),Snapshot.capture(),candidates(s.rewardGroup,true,false),"skip",actor());seen.add(r);recorder.flush();});}
    // blessing() (4 rewards) and miniBlessing() (2) both end at screenNum 3, where buttonEffect activates rewards[button].
    @SuppressWarnings("unchecked") static void neow(NeowEvent event,int button){if(!ready())return;safely(()->{
        if((Integer)field(event,"screenNum")!=3)return;List<NeowReward> rewards=(List<NeowReward>)field(event,"rewards");if(button<0||button>=rewards.size())return;
        ensure();JsonObject extra=new JsonObject();
        // shortcut: variant is inferred from the vanilla option counts; hook blessing()/miniBlessing() if a mod changes them.
        extra.addProperty("variant",rewards.size()==4?"blessing":rewards.size()==2?"mini_blessing":"unknown");
        recorder.begin(key(rewards),"NEOW_OPTION",Snapshot.capture(),Snapshot.neow(rewards),"neow:"+button,actor(),extra);
    });}
    static JsonObject shopVisit(AbstractRoom room){JsonObject o=new JsonObject();o.addProperty("shop_key",key(room));return o;}
    // First open only: closing and reopening the shop screen is not a new offer.
    static void shopOffer(ShopScreen s){if(!ready())return;safely(()->{ensure();AbstractRoom room=AbstractDungeon.getCurrRoom();if(!(room instanceof ShopRoom)||seen.contains(room))return;seen.add(room);
        JsonObject e=Recorder.object("shop_offer");e.addProperty("shop_key",key(room));e.add("state",Snapshot.capture());e.add("candidates",Snapshot.shop(s));recorder.append(e);recorder.flush();});}
    /** item is the live stock object (a Courier restock is a new object); null is the removal service. */
    static void shopBuy(Object item,String candidate){if(!ready())return;safely(()->{ensure();AbstractRoom room=AbstractDungeon.getCurrRoom();if(!(room instanceof ShopRoom))return;
        recorder.begin(item==null?key(room)+":purge":key(item),"SHOP",Snapshot.capture(),Snapshot.shop(AbstractDungeon.shopScreen),candidate,actor(),shopVisit(room));});}
    static void leaveRoom(){if(!ready())return;safely(()->{AbstractRoom room=AbstractDungeon.getCurrRoom();if(!(room instanceof ShopRoom)||!seen.contains(room))return;ensure();
        String k=key(room)+":leave";JsonObject before=Snapshot.capture();
        // Leaving has no effect to wait for: its anchor is also its settled result.
        if(recorder.begin(k,"SHOP",before,Snapshot.shop(AbstractDungeon.shopScreen),"leave",actor(),shopVisit(room))!=null)recorder.settleKey(k,before);});}
    static void enterRoom(){if(!ready())return;safely(()->{ensure();MapRoomNode n=AbstractDungeon.nextRoom;JsonObject e=Recorder.object("floor_enter");
        e.addProperty("act",AbstractDungeon.actNum);e.addProperty("floor",AbstractDungeon.floorNum);
        // A "?" node is still EventRoom here; the rolled room appears in the later floor record.
        e.addProperty("room",n==null||n.room==null?"unknown":n.room.getClass().getSimpleName());if(n!=null){e.addProperty("x",n.x);e.addProperty("y",n.y);}
        e.addProperty("hp",AbstractDungeon.player.currentHealth);e.addProperty("max_hp",AbstractDungeon.player.maxHealth);e.addProperty("gold",AbstractDungeon.player.gold);e.addProperty("gold_gained",CardCrawlGame.goldGained);
        e.addProperty("timing","before_room_entry_effects");recorder.append(e);});}
    static void act3Exit(){act3Floor=AbstractDungeon.floorNum;}
    static void heartExit(){heartExit=true;}
    // m1-field-gaps section 7: the final act-3 boss exit, being in act 4, or a heart kill each mean act 3 was cleared.
    static void act3Facts(JsonObject e,int act,int exitFloor,boolean heart){e.addProperty("act3_cleared",exitFloor>=0||act>=4||heart);if(exitFloor>=0)e.addProperty("act3_cleared_floor",exitFloor);else e.add("act3_cleared_floor",JsonNull.INSTANCE);e.addProperty("heart_kill",heart);}
    static void mutation(String reason){if(!ready())return;safely(()->{ensure();if(mutationBefore==null){mutationBefore=Snapshot.capture();mutationReason=reason;JsonObject e=Recorder.object("mutation_pending");e.addProperty("reason",reason);e.add("before",mutationBefore);recorder.append(e);recorder.flush();}});}
    // Bind the synchronous on-disk Metrics object before its UUID is generated. Upload
    // Metrics objects can generate a different UUID later and are deliberately ignored.
    static void metricsStarted(Object metrics){if(!enabled||failed)return;safely(()->{if(ready())ensure();if(recorder!=null)metricsOwners.put(metrics,recorder);});}
    static void metricsFinished(Object metrics,boolean death,boolean heart){Recorder target=metricsOwners.remove(metrics);if(target==null)return;safely(()->{Object params=field(metrics,"params");Object value=((Map<?,?>)params).get("play_id");if(!(value instanceof String)||!((String)value).matches("[a-fA-F0-9-]{36}"))throw new IOException("Missing native metrics play_id");JsonObject e=Recorder.object("game_play_id");e.addProperty("play_id",(String)value);e.addProperty("source","original_metrics_disk_save");e.addProperty("victory",!death);e.addProperty("heart_kill",!death&&heart);target.append(e);target.export();});}
    static void end(String outcome){if(!ready())return;safely(()->{ensure();if(Snapshot.settled()&&recorder.hasPending())recorder.settle(Snapshot.capture());JsonObject e=Recorder.object("ending");e.addProperty("outcome",outcome);e.addProperty("outcome_basis","death_screen_cause");act3Facts(e,AbstractDungeon.actNum,act3Floor,heartExit||AbstractDungeon.getCurrRoom() instanceof TrueVictoryRoom);e.addProperty("effects_settled",Snapshot.settled());e.add("state",Snapshot.capture());recorder.append(e);recorder.interrupt("run_ended_before_settlement");recorder.export();status="Archived "+outcome;});}
    /** Main-menu abandon uses only the checkpoint already read by vanilla, never a seed or most-recent guess. */
    static boolean abandonSaved(Path root,JsonObject ref,int slot)throws IOException {
        String id=HistoryIndex.str(ref,"run_id"),point=HistoryIndex.str(ref,"checkpoint_id"),branch=HistoryIndex.str(ref,"branch_id");
        if(!id.matches("[a-fA-F0-9-]{36}")||point.isEmpty()||branch.isEmpty())return false;
        Path dir=root.resolve(id);if(!Files.isDirectory(dir)||Files.isSymbolicLink(dir))return false;
        if(!Files.isRegularFile(dir.resolve("events.jsonl")))return false;
        JsonObject doc=HistoryIndex.journal(dir),meta=HistoryIndex.obj(doc,"metadata");
        if(!id.equals(HistoryIndex.str(meta,"run_id"))||HistoryIndex.num(meta,"save_slot",-1)!=slot)return false;
        JsonObject checkpoint=null;for(JsonElement x:HistoryIndex.array(doc,"records")){JsonObject e=x.getAsJsonObject();if("checkpoint".equals(HistoryIndex.str(e,"type"))&&point.equals(HistoryIndex.str(e,"checkpoint_id"))&&branch.equals(HistoryIndex.str(e,"branch_id")))checkpoint=e;}
        if(checkpoint==null)return false;
        try(Recorder target=new Recorder(root,meta,id)){
            JsonObject state=HistoryIndex.obj(checkpoint,"state");target.resume(ref,state);
            JsonObject end=Recorder.object("ending");end.addProperty("outcome","abandon");end.addProperty("outcome_basis","main_menu_saved_checkpoint");end.addProperty("effects_settled",false);end.add("state",state);target.append(end);target.export();
        }return true;
    }
    static void abandonSaved(com.megacrit.cardcrawl.saveAndContinue.SaveFile save){
        if(!enabled||failed||ReplaySafety.quarantined||save==null)return;
        safely(()->{
            Map<String,JsonElement> fields=basemod.patches.com.megacrit.cardcrawl.saveAndContinue.SaveFile.ModSaves.modSaves.get(save);
            JsonElement value=fields==null?null:fields.get("spire-ledger:checkpoint");if(value==null||!value.isJsonObject())return;
            JsonObject ref=value.getAsJsonObject();
            if(recorder!=null&&recorder.runId.equals(Recorder.string(ref,"run_id"))){recorder.close();recorder=null;}
            if(abandonSaved(ROOT,ref,CardCrawlGame.saveSlot))status="Archived abandon";
        });
    }
    @Override public void receiveStartGame(){startRequested=true;loadingExpected=loadingExpected||CardCrawlGame.loadingSave;}
    @Override public void onLoad(JsonObject data){loading=data;loadingExpected=true;startRequested=true;TurnRecorder.restoreCombat(data==null?new JsonObject():data);}
    @Override public JsonObject onSave(){
        if(!ready())return null;
        final JsonObject[] result={null};safely(()->{ensure();result[0]=recorder.checkpoint(Snapshot.capture());result[0].add("deck",Snapshot.capture().get("deck"));result[0].addProperty("combat_id",TurnRecorder.combatId);if(act3Floor>=0)result[0].addProperty("act3_cleared_floor",act3Floor);});return result[0];
    }
    @Override public void receivePostUpdate(){
        ReplayLauncher.checkPracticeStartup();
        if(!ready())return;
        if(System.nanoTime()<nextObserve)return;nextObserve=System.nanoTime()+250000000L;
        safely(()->{
            ensure();if(!Snapshot.settled())return;
            JsonObject now=Snapshot.capture();
            if(recorder.hasPending())recorder.settle(now);
            if(mutationBefore!=null){JsonObject e=Recorder.object("build_change");e.addProperty("reason",mutationReason);e.addProperty("status",Snapshot.buildSignature(mutationBefore).equals(Snapshot.buildSignature(now))?"no_change_or_cancelled":"settled");e.add("before",mutationBefore);e.add("after",now);recorder.append(e);mutationBefore=null;}
            else if(last!=null&&!Snapshot.buildSignature(last).equals(Snapshot.buildSignature(now))){JsonObject e=Recorder.object("build_change");e.addProperty("reason","observed_build_change; no inferred action");e.addProperty("status","settled");e.addProperty("before_timing","previous_settled_anchor");e.add("before",last);e.add("after",now);recorder.append(e);}
            if(lastAct!=AbstractDungeon.actNum){if(!recorder.hasMap(AbstractDungeon.actNum)){JsonObject e=Recorder.object("map");e.addProperty("act",AbstractDungeon.actNum);e.addProperty("dictionary_version",1);e.add("nodes",Snapshot.map());recorder.append(e);}lastAct=AbstractDungeon.actNum;}
            if(lastFloor!=AbstractDungeon.floorNum){JsonObject e=Recorder.object("floor");e.add("state",now);recorder.append(e);recorder.flush();lastFloor=AbstractDungeon.floorNum;}
            if(last!=null&&!Snapshot.buildSignature(last).equals(Snapshot.buildSignature(now)))recorder.flush();
            last=now;
        });
    }
    static void toggle(boolean value){if(value==enabled)return;if(failed&&value)failed=false;safely(()->{if(recorder!=null){JsonObject e=Recorder.object("coverage");e.addProperty("status",value?"enabled":"disabled");recorder.append(e);recorder.interrupt("recording_disabled");recorder.export();}enabled=value;if(value){last=null;mutationBefore=null;}status=value?"Ready — recording enabled":"Recording disabled";savePreference();});}
    static void savePreference()throws IOException{Path p=ROOT.getParent().resolve("enabled.txt");Files.createDirectories(p.getParent());Files.write(p,String.valueOf(enabled).getBytes(StandardCharsets.UTF_8));}
    @Override public void receivePostInitialize(){
        try{Path p=ROOT.getParent().resolve("enabled.txt");if(Files.exists(p))enabled=Boolean.parseBoolean(new String(Files.readAllBytes(p),StandardCharsets.UTF_8).trim());}catch(IOException ex){status="Preference read failed; recording disabled";enabled=false;}
        historyTopPanel=new HistoryTopPanelItem();BaseMod.addTopPanelItem(historyTopPanel);
        ModPanel panel=new ModPanel();
        panel.addUIElement(new ModLabeledToggleButton("Local Spire Ledger recording",350,700,Color.WHITE,FontHelper.charDescFont,enabled,panel,l->{},b->toggle(b.enabled)));
        panel.addUIElement(new ModLabeledToggleButton("Turn checkpoint recording (practice/replay)",350,650,Color.WHITE,FontHelper.charDescFont,TurnRecorder.enabled,panel,l->{},b->TurnRecorder.toggle(b.enabled)));
        panel.addUIElement(new ModLabel(status,350,600,Color.WHITE,panel,l->l.text=status));
        panel.addUIElement(new ModLabeledButton("Export current run ZIP",350,540,panel,b->safely(()->{if(recorder!=null){recorder.export();status="Exported current run.zip";}})));
        panel.addUIElement(new ModLabeledButton("Open records folder",350,470,panel,b->safely(()->{Files.createDirectories(ROOT);if(Desktop.isDesktopSupported())Desktop.getDesktop().open(ROOT.toFile());else status="Folder: ~/.spire-ledger/runs";})));
        historyEntry=new ModLabeledButton(HistoryScreen.tr("尖塔账本 / 当前局统计","Spire Ledger / current run"),350,400,panel,b->HistoryScreen.open());panel.addUIElement(historyEntry);
        reportEntry=new ModLabeledButton(HistoryScreen.tr("导出错误报告 · Ctrl+Shift+R","Error report · Ctrl+Shift+R"),350,330,panel,b->DiagnosticReport.request());panel.addUIElement(reportEntry);
        panel.addUIElement(new ModLabeledButton(HistoryScreen.tr("打开错误报告目录","Open error report folder"),350,230,panel,b->DiagnosticReport.openFolder()));
        panel.addUIElement(new ModLabel("",350,285,Color.WHITE,panel,l->l.text=DiagnosticReport.status));
        BaseMod.registerModBadge(ImageMaster.loadImage("images/relics/blueCandle.png"),"Spire Ledger","CYZ","Local build recording and offline review",panel);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{if(recorder!=null)try{recorder.close();}catch(IOException ex){System.err.println("[Spire Ledger] Recover events.jsonl after shutdown IO failure");}},"spire-ledger-flush"));
    }
}
