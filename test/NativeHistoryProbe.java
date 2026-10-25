package spireledger;
import basemod.BaseMod;
import basemod.interfaces.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.google.gson.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.integrations.*;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;
import com.megacrit.cardcrawl.screens.mainMenu.*;
import com.megacrit.cardcrawl.screens.options.DropdownMenu;
import javassist.expr.*;
import javassist.CannotCompileException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Native controls exercised by synthetic input. Shipping JAR never contains this driver. */
@SpireInitializer public class NativeHistoryProbe implements PostUpdateSubscriber,PostRenderSubscriber {
    final boolean realLayout=Boolean.getBoolean("spireledger.realLayout");
    int stage;long next,started=System.currentTimeMillis();String shot;boolean captured;MainMenuScreen.CurScreen prior;AbstractDungeon.CurrentScreen rewardScreen;int deck,gold;long rng;JsonObject report=new JsonObject();
    public static void initialize(){BaseMod.subscribe(new NativeHistoryProbe());}
    @SpirePatch(clz=DistributorFactory.class,method="getEnabledDistributor") public static class Offline {@SpirePrefixPatch public static SpireReturn<PublisherIntegration> before(String distributor){return SpireReturn.Return(new GogIntegration());}}
    @SpirePatch(clz=CardCrawlGame.class,method="create") public static class NoSteam {@SpireInstrumentPatch public static ExprEditor edit(){return new ExprEditor(){public void edit(NewExpr e)throws CannotCompileException{if(e.getClassName().equals("com.codedisaster.steamworks.SteamUtils")||e.getClassName().equals("com.megacrit.cardcrawl.helpers.steamInput.SteamInputHelper"))e.replace("{ $_ = null; }");}};}}
    @SpirePatch(clz=Settings.class,method="initializeGamePref") public static class Chinese {@SpirePostfixPatch public static void lang(){Settings.setLanguage(Settings.GameLanguage.valueOf(System.getProperty("spireledger.uiLanguage","ZHS")),true);}}
    static Object fade()throws Exception{java.lang.reflect.Field f=CardCrawlGame.class.getDeclaredField("screenColor");f.setAccessible(true);return f.get(null);}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void clicked(Hitbox h){h.clicked=true;InputHelper.mX=(int)h.cX;InputHelper.mY=(int)h.cY;}
    void dropdown(DropdownMenu d,int row)throws Exception{d.getHitbox().clicked=true;d.update();check(d.isOpen,"native dropdown opened");Object r=d.rows.get(row);Hitbox h=(Hitbox)StateMod.field(r,"hb");clicked(h);d.update();}
    void capture(String name){shot=HistoryScreen.zh()?name:name.replace("-zh","-en");captured=false;}
    public void receivePostRender(SpriteBatch sb){if(shot==null||captured)return;try{sb.flush();Pixmap pixmap=ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);png.write(new FileHandle(shot+".png"),pixmap);png.dispose();pixmap.dispose();captured=true;shot=null;}catch(Throwable e){finish(e);}}
    void finish(Throwable error){try{report.addProperty("status",error==null?"pass":"fail");report.addProperty("dataset_origin",realLayout?"real_player_records_read_only":"test_fixture");report.addProperty("test_environment",true);report.addProperty("input","synthetic native hitbox/button controls; not a human demonstration");report.addProperty("stage",stage);report.addProperty("error",error==null?"":error.toString());Files.write(Paths.get("ui-probe-result.json"),report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}if(error!=null)error.printStackTrace();Gdx.app.exit();}
    public void receivePostUpdate(){if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+750;try{
        if(System.currentTimeMillis()-started>150000)throw new AssertionError("native UI fixture timed out at "+stage);
        HistoryScreen h=HistoryScreen.instance;
        if(realLayout&&(stage<6||stage>=11&&stage<=17)){realSteps(h);return;}
        switch(stage){
            case 0:if(CardCrawlGame.mainMenuScreen==null||CardCrawlGame.mode!=CardCrawlGame.GameMode.CHAR_SELECT||CardCrawlGame.mainMenuScreen.screen!=MainMenuScreen.CurScreen.MAIN_MENU||((com.badlogic.gdx.graphics.Color)fade()).a>.01f)return;capture("native-main-menu-zh");stage=10;break;
            case 10:if(!captured)return;prior=CardCrawlGame.mainMenuScreen.screen;MenuButton entry=null;for(MenuButton b:CardCrawlGame.mainMenuScreen.buttons)if(b.result==HistoryHooks.STATE_NOTES)entry=b;check(entry!=null,"main menu native entry");clicked(entry.hb);CardCrawlGame.mainMenuScreen.update();check(HistoryScreen.active(),"native MenuButton.update -> buttonEffect -> history");stage=1;break;
            case 1:if(h.indexTask!=null)return;check(h.index.runs.size()==1200,"1200 indexed runs");check(h.index.errors.size()==1,"unreadable visible");check(HistoryScreen.cardName("Bash").equals(HistoryScreen.zh()?"痛击":"Bash"),"native localized card name");check(HistoryScreen.relicName("unknown-relic").equals("unknown-relic"),"unknown relic not fabricated Circlet");capture("native-history-list-zh");stage++;break;
            case 2:if(!captured)return;dropdown(h.filters[0],1);check(h.filtered.size()==300,"native class filter");dropdown(h.filters[2],1);check(h.filtered.isEmpty(),"native empty filter");capture("native-history-filter-zh");stage++;break;
            case 3:if(!captured)return;clicked(h.clear);h.update();check(h.filtered.size()==1200&&h.selections[0]==0,"native clear");clicked(h.next);h.update();check(h.offset==6,"native paging");InputHelper.scrolledDown=true;h.update();InputHelper.scrolledDown=false;check(h.offset==9,"native scrolling");h.setText(HistoryScreen.cardName("Bash"));check(h.filtered.size()==1200,"localized keyword");dropdown(h.filters[2],2);check(h.filtered.size()==300,"victory subset");clicked(h.rows[0]);h.update();stage++;break;
            case 4:if(h.detailTask!=null)return;check(h.detail!=null&&!h.lines.isEmpty(),"selected detail only");check(h.lines.toString().contains(HistoryScreen.cardName("Bash"))&&h.lines.toString().contains("67/80"),"localized before/resource details");presentationChecks(h);capture("native-history-detail-zh");stage++;break;
            case 5:if(!captured)return;clicked(h.diagnostics);h.update();check(h.raw,"native diagnostics toggle");clicked(h.cancel.hb);h.update();check(h.selected==null,"return to list");clicked(h.cancel.hb);h.update();check(!HistoryScreen.active()&&CardCrawlGame.mainMenuScreen.screen==prior,"exact main menu return");report.addProperty("native_menu_filters_clear_scroll_detail_return",true);Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage++;break;
            case 6:if(!StateMod.ready())return;AbstractDungeon.effectList.clear();AbstractDungeon.effectsQueue.clear();AbstractDungeon.topLevelEffects.clear();AbstractDungeon.topLevelEffectsQueue.clear();AbstractDungeon.actionManager.clear();AbstractDungeon.ftue=null;AbstractDungeon.screen=AbstractDungeon.CurrentScreen.COMBAT_REWARD;AbstractDungeon.isScreenUp=true;rewardScreen=AbstractDungeon.screen;deck=AbstractDungeon.player.masterDeck.size();gold=AbstractDungeon.player.gold;rng=AbstractDungeon.cardRng.counter;clicked((Hitbox)StateMod.field(StateMod.historyEntry,"hb"));StateMod.historyEntry.update();check(HistoryScreen.active(),"actual ModLabeledButton.update -> callback");HistoryScreen.instance.close();AbstractDungeon.topPanel.update();Hitbox top=StateMod.historyTopPanel.getHitbox();InputHelper.mX=(int)(top.x+top.width/2);InputHelper.mY=(int)(top.y+top.height/2);InputHelper.justClickedLeft=true;AbstractDungeon.topPanel.update();check(HistoryScreen.active(),"actual TopPanel.update -> BaseMod TopPanelGroup -> item callback");report.addProperty("native_ingame_top_panel_entry",true);stage++;break;
            case 7:if(h.indexTask!=null)return;check(h.index.runs.stream().anyMatch(s->s.current),"live recorder shown");check(AbstractDungeon.screen==rewardScreen&&AbstractDungeon.player.masterDeck.size()==deck&&AbstractDungeon.player.gold==gold&&AbstractDungeon.cardRng.counter==rng,"pause preserves screen/deck/resources/RNG");capture("native-history-current-zh");stage++;break;
            case 8:if(!captured)return;clicked(h.cancel.hb);h.update();check(!HistoryScreen.active()&&AbstractDungeon.screen==rewardScreen,"same reward screen restored");check(!InputHelper.justClickedLeft&&!InputHelper.justReleasedClickLeft,"return click consumed");report.addProperty("mods_entry_current_snapshot_reward_pause_return",true);report.addProperty("fixture_count",realLayout?0:1200);report.addProperty("screenshots",realLayout?8:5);finish(null);stage++;break;
        }
    }catch(Throwable e){finish(e);stage=99;}}
    void presentationChecks(HistoryScreen h){
     JsonObject original=h.detail,event=h.events.get(h.eventIndex);try{
      h.detail=new JsonParser().parse(original.toString()).getAsJsonObject();JsonObject chosen=new JsonParser().parse(event.toString()).getAsJsonObject();h.events.set(h.eventIndex,chosen);chosen.addProperty("candidate_id","bowl");h.makeLines();
      JsonObject result=null;for(JsonElement e:HistoryIndex.array(h.detail,"records"))if(e.isJsonObject()&&"result".equals(HistoryIndex.str(e.getAsJsonObject(),"type")))result=e.getAsJsonObject();check(result!=null,"synthetic result");
      // Change the synthetic document's decision too; otherwise it is correctly a conflicting decision.
      for(JsonElement e:HistoryIndex.array(h.detail,"records"))if(e.isJsonObject()&&"decision".equals(HistoryIndex.str(e.getAsJsonObject(),"type")))e.getAsJsonObject().addProperty("candidate_id","bowl");h.makeLines();
      check(h.lines.stream().anyMatch(r->r.left.contains("[Bowl used]")&&r.tone==HistoryScreen.Tone.CHANGED),"completed bowl");result.addProperty("status","pending");h.makeLines();check(h.lines.toString().contains("[Submitted · incomplete]")&&!h.lines.toString().contains("[Bowl used]"),"pending not completed");
      result.addProperty("status","confirmed");JsonObject conflict=new JsonParser().parse(result.toString()).getAsJsonObject();conflict.addProperty("status","interrupted");HistoryIndex.array(h.detail,"records").add(conflict);h.makeLines();check(!h.lines.toString().contains("[Bowl used]")&&h.lines.stream().anyMatch(r->r.tone==HistoryScreen.Tone.REMOVED),"conflict does not choose result");
      report.addProperty("bowl_pending_conflict_presentation",true);
     }finally{h.detail=original;h.events.set(h.eventIndex,event);h.makeLines();}
    }
    void event(HistoryScreen h,int seq){for(int i=0;i<h.events.size();i++)if(HistoryIndex.num(h.events.get(i),"seq",-1)==seq){h.eventIndex=i;h.makeLines();checkWidths(h);return;}throw new AssertionError("missing real event "+seq);}
    void checkWidths(HistoryScreen h){for(HistoryScreen.Row r:h.lines){com.badlogic.gdx.graphics.g2d.BitmapFont f=r.tone==HistoryScreen.Tone.HEADER?FontHelper.buttonLabelFont:FontHelper.cardDescFont_N;float scale=r.tone==HistoryScreen.Tone.HEADER?1:1.12f;check(h.measure(f,r.left,scale)<=(r.columns?h.cellWidth():h.contentWidth())+.1f,"left native pixel bound "+r.left);if(r.columns)check(h.measure(FontHelper.cardDescFont_N,r.right,1.12f)<=h.cellWidth()+.1f,"right native pixel bound");}}
    void realSteps(HistoryScreen h)throws Exception{
      switch(stage){
       case 0:if(CardCrawlGame.mainMenuScreen==null||CardCrawlGame.mode!=CardCrawlGame.GameMode.CHAR_SELECT||CardCrawlGame.mainMenuScreen.screen!=MainMenuScreen.CurScreen.MAIN_MENU||((com.badlogic.gdx.graphics.Color)fade()).a>.01f)return;prior=CardCrawlGame.mainMenuScreen.screen;MenuButton entry=null;for(MenuButton b:CardCrawlGame.mainMenuScreen.buttons)if(b.result==HistoryHooks.STATE_NOTES)entry=b;check(entry!=null,"real menu entry");clicked(entry.hb);CardCrawlGame.mainMenuScreen.update();check(HistoryScreen.active(),"menu opened");stage=1;break;
       case 1:if(h.indexTask!=null)return;check(h.index.runs.size()==3,"three real runs");capture("real-history-list-zh");stage=11;break;
       case 11:if(!captured)return;dropdown(h.filters[2],2);check(h.filtered.size()==1,"real victory filter");clicked(h.clear);h.update();check(h.filtered.size()==3,"real clear");h.setText(HistoryScreen.cardName("Feel No Pain"));check(!h.filtered.isEmpty(),"localized real keyword");clicked(h.clear);h.update();HistoryIndex.Summary target=null;for(HistoryIndex.Summary r:h.filtered)if(r.id.equals("bce8e324-e786-41c4-b1db-a79b63e9f83b"))target=r;check(target!=null,"long real run");h.select(target);stage=2;break;
       case 2:if(h.detailTask!=null)return;event(h,287);check(h.lines.toString().contains("【已选择】")&&h.lines.toString().contains("＋ 新增"),"selected and added real card highlighted");check(h.lines.toString().contains("来源未核实"),"real unknown preserved");capture("real-selected-addition-zh");stage=12;break;
       case 12:if(!captured)return;clicked(h.sections[3]);h.update();check(h.lines.toString().contains("31 张"),"31 card result");capture("real-long-deck-result-zh");stage=13;break;
       case 13:if(!captured)return;InputHelper.scrolledDown=true;h.update();InputHelper.scrolledDown=false;h.scrolledUsingBar(.88f);check(h.lineOffset>h.sectionRows[3],"long deck native scroll");capture("real-long-deck-scroll-zh");stage=14;break;
       case 14:if(!captured)return;event(h,301);check(h.lines.toString().contains("【已跳过】"),"real skip highlight");capture("real-skip-zh");stage=15;break;
       case 15:if(!captured)return;event(h,280);clicked(h.sections[2]);h.update();check(h.lines.toString().contains("－ 移除"),"real removal");capture("real-removal-zh");stage=16;break;
       case 16:if(!captured)return;event(h,275);clicked(h.sections[2]);h.update();check(h.lines.toString().contains("↑ 升级变化"),"real upgrade");capture("real-upgrade-zh");stage=17;break;
       case 17:if(!captured)return;clicked(h.diagnostics);h.update();check(h.raw,"real diagnostics");clicked(h.cancel.hb);h.update();clicked(h.cancel.hb);h.update();check(!HistoryScreen.active()&&CardCrawlGame.mainMenuScreen.screen==prior,"real safe return");report.addProperty("real_native_font_widths",true);report.addProperty("real_selected_skip_add_remove_upgrade_long_deck",true);report.addProperty("real_run_id","bce8e324-e786-41c4-b1db-a79b63e9f83b");report.addProperty("width",Gdx.graphics.getWidth());report.addProperty("height",Gdx.graphics.getHeight());Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage=6;break;
      }
    }

}
