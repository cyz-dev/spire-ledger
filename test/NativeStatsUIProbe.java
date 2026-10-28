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
@SpireInitializer public class NativeStatsUIProbe implements PostUpdateSubscriber,PostRenderSubscriber {
    final boolean realLayout=Boolean.getBoolean("spireledger.realLayout");
    int stage;long next,started=System.currentTimeMillis();String shot;boolean captured;MainMenuScreen.CurScreen prior;AbstractDungeon.CurrentScreen rewardScreen;int deck,gold;long rng;JsonObject report=new JsonObject(),pauseState,pauseRng;
    public static void initialize(){BaseMod.subscribe(new NativeStatsUIProbe());}
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

    public void receivePostUpdate(){if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+700;try{
      if(System.currentTimeMillis()-started>150000)throw new AssertionError("stats native UI timeout stage "+stage);
      HistoryScreen h=HistoryScreen.instance;
      switch(stage){
       case 0:if(CardCrawlGame.mainMenuScreen==null||CardCrawlGame.mode!=CardCrawlGame.GameMode.CHAR_SELECT||CardCrawlGame.mainMenuScreen.screen!=MainMenuScreen.CurScreen.MAIN_MENU||((com.badlogic.gdx.graphics.Color)fade()).a>.01f)return;prior=CardCrawlGame.mainMenuScreen.screen;MenuButton entry=null;for(MenuButton b:CardCrawlGame.mainMenuScreen.buttons)if(b.result==HistoryHooks.STATE_NOTES)entry=b;check(entry!=null,"real native entry");clicked(entry.hb);CardCrawlGame.mainMenuScreen.update();check(HistoryScreen.active(),"native entry opens ledger");clicked(HistoryScreen.instance.home);HistoryScreen.instance.update();stage++;break;
       case 1:if(h.indexTask!=null||h.stats.task!=null||!h.stats.requested)return;check(!h.historyMode,"statistics reachable in one click");if(!h.stats.advanced){clicked(h.stats.advancedButton);h.update();}check(h.stats.index.nativeFiles==3&&h.stats.index.ledgerRecords==3&&h.stats.index.runs.size()==6,"three real native and three unlinked notes runs merged into six logical runs");check(h.stats.display.toString().contains("3"),"known result population");check(h.stats.display.toString().contains(HistoryScreen.tr("存档位","save slot")+" 3"),"three State Notes records without save_slot are in no slot");capture("stats-overview-zh");stage++;break;
       case 2:if(!captured)return;dropdown(h.stats.filters[0],3);check(h.stats.display.toString().contains(HistoryScreen.tr("暂无数据","No data")),"empty native class filter");clicked(h.stats.clearButton);h.update();check(h.stats.selected[0]==0,"clear unified filters");check(h.stats.display.toString().contains("33.3%"),"heart basis: one heart kill among three slot-0 native results");dropdown(h.stats.filters[3],1);check(h.stats.display.toString().contains(HistoryScreen.tr("胜利口径：通过第三幕","Wins: act 3 cleared"))&&h.stats.display.toString().contains("33.3%"),"act 3 basis: the heart win among three slot-0 native results");dropdown(h.stats.filters[8],1);check(h.stats.display.toString().contains("—"),"win rate is a dash under a result filter");dropdown(h.stats.filters[8],0);clicked(h.stats.themes[1]);h.update();check(h.stats.filters[2].rows.size()==2,"date uses absolute month range");check(h.stats.display.toString().contains(HistoryScreen.tr("操作来源未知","unknown action source")),"actor label is distinct from reward or act");for(StatsScreen.Line row:h.stats.displayRows)if(row.two){float w=h.cellWidth();check(h.measure(FontHelper.cardDescFont_N,row.lLabel,1.12f)<=w*.54f&&h.measure(FontHelper.cardDescFont_N,row.lValue,1.12f)<=w*.44f&&h.measure(FontHelper.cardDescFont_N,row.rLabel,1.12f)<=w*.54f&&h.measure(FontHelper.cardDescFont_N,row.rValue,1.12f)<=w*.44f,"metric cells fit without overlap");}capture("stats-build-zh");stage++;break;
       case 3:if(!captured)return;clicked(h.stats.themes[2]);h.update();check(h.stats.display.size()>3,"real encounter damage");InputHelper.scrolledDown=true;h.update();InputHelper.scrolledDown=false;check(h.stats.offset>0,"native statistics scroll");clicked(h.next);h.update();capture("stats-resources-zh");stage++;break;
       case 4:if(!captured)return;if(!h.stats.advanced){clicked(h.stats.advancedButton);h.update();}dropdown(h.stats.filters[6],2);check(h.stats.filter.source.equals(spireledger.stats.Fact.LEDGER),"runs with a Spire Ledger record");clicked(h.stats.themes[1]);h.update();capture("stats-independent-offers-zh");stage++;break;
       case 5:if(!captured)return;clicked(h.historyTab);h.update();check(h.historyMode&&!h.raw,"history navigation");check(h.filtered.isEmpty(),"legacy slot-unknown records excluded from current slot");dropdown(h.filters[6],1);check(h.filtered.size()==3,"legacy history remains available explicitly");clicked(h.keyword);h.update();String text=HistoryScreen.cardName("Feel No Pain");for(char c:text.toCharArray())Gdx.input.getInputProcessor().keyTyped(c);check(h.query.equals(text)&&!h.filtered.isEmpty(),"actual text input processor localized keyword");clicked(h.clear);h.update();check(h.filtered.isEmpty(),"clear restores current slot");dropdown(h.filters[6],1);capture("stats-history-list-zh");stage=50;break;
       case 50:if(!captured)return;HistoryIndex.Summary target=null;for(HistoryIndex.Summary run:h.filtered)if(run.id.equals("bce8e324-e786-41c4-b1db-a79b63e9f83b"))target=run;check(target!=null,"real long run");h.select(target);stage=6;break;
       case 6:if(h.detailTask!=null)return;h.review.sync();check(h.outcome("abandon").equals(HistoryScreen.tr("放弃","Abandoned"))&&h.outcome("interrupted").equals(HistoryScreen.tr("中断","Interrupted")),"history ending labels");check(!h.review.pinned,"overview by default");for(int i=0;i<h.review.model.floors.size();i++)if(h.review.model.floors.get(i).number==54){clicked(h.review.nodes.get(i));h.update();break;}check(h.review.pinned&&h.floorOrder.get(h.floorIndex)==54,"legacy floor selectable from route");capture("stats-real-floor-54-zh");stage++;break;
       case 7:if(!captured)return;clicked(h.review.deckTab);h.update();check(h.review.showDeck,"recorded floor deck");check(HistoryIndex.array(h.review.model.floors.get(h.review.focused()).last,"deck").size()==31,"legacy 31 card anchor");capture("stats-real-long-deck-zh");stage++;break;
       case 8:if(!captured)return;InputHelper.scrolledDown=true;h.update();InputHelper.scrolledDown=false;check(h.review.scroll==Math.min(1,h.review.maxScroll()),"native deck scrolling bounded");capture("stats-real-long-scroll-zh");stage++;break;
       case 9:if(!captured)return;clicked(h.next);h.update();check(h.floorOrder.get(h.floorIndex)==55,"next floor, not event number");clicked(h.diagnostics);h.update();check(h.raw&&h.branchMenu!=null,"original branch diagnostics available");clicked(h.cancel.hb);h.update();check(!h.raw&&h.selected!=null,"diagnostics back to review");clicked(h.historyTab);h.update();check(h.selected==null,"one click to history list");clicked(h.home);h.update();check(!h.historyMode&&HistoryScreen.active(),"one click to statistics");com.megacrit.cardcrawl.helpers.controller.CInputActionSet.cancel.justPressed=true;h.update();com.megacrit.cardcrawl.helpers.controller.CInputActionSet.cancel.unpress();check(!HistoryScreen.active()&&CardCrawlGame.mainMenuScreen.screen==prior,"native controller cancel return");report.addProperty("statistics_native_filters_themes_floor_detail_keyboard_return",true);report.addProperty("controller_cancel_synthetic",true);report.addProperty("physical_controller_verified",false);report.addProperty("width",Gdx.graphics.getWidth());report.addProperty("height",Gdx.graphics.getHeight());report.addProperty("dataset_origin","real_player_records_read_only");Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();CardCrawlGame.chosenCharacter=AbstractPlayer.PlayerClass.IRONCLAD;CardCrawlGame.mainMenuScreen.fadedOut=true;stage=10;break;
       case 10:if(!StateMod.ready()||CardCrawlGame.dungeon==null)return;StateMod.ensure();pauseState=Snapshot.capture();pauseRng=TurnAdapter.rng();rewardScreen=AbstractDungeon.screen;HistoryScreen.open();stage=11;break;
       case 11:if(h.indexTask!=null||h.detailTask!=null)return;check(h.selected!=null&&h.selected.current,"current run floor review opens");check(Snapshot.capture().equals(pauseState)&&TurnAdapter.rng().equals(pauseRng)&&AbstractDungeon.screen==rewardScreen,"open current view preserves all resources, deck and 13 RNGs");h.close();check(!HistoryScreen.active()&&Snapshot.capture().equals(pauseState)&&TurnAdapter.rng().equals(pauseRng)&&AbstractDungeon.screen==rewardScreen,"return leaves underlying game screen and inputs untouched");report.addProperty("current_run_open_close_13_rngs_unchanged",true);finish(null);stage=99;break;
      }
    }catch(Throwable e){finish(e);stage=99;}}
}
