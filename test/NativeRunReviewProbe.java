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
@SpireInitializer public class NativeRunReviewProbe implements PostUpdateSubscriber,PostRenderSubscriber {
    final boolean realLayout=Boolean.getBoolean("spireledger.realLayout");
    int stage;long next,started=System.currentTimeMillis();String shot;boolean captured;MainMenuScreen.CurScreen prior;AbstractDungeon.CurrentScreen rewardScreen;int deck,gold;long rng;JsonObject report=new JsonObject(),pauseState,pauseRng;
    public static void initialize(){BaseMod.subscribe(new NativeRunReviewProbe());}
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


    JsonObject recorded,initialRng;
    void hover(Hitbox node){InputHelper.mX=(int)node.cX;InputHelper.mY=(int)node.cY;}
    int node(HistoryScreen h,int floor){for(int i=0;i<h.review.model.floors.size();i++)if(h.review.model.floors.get(i).number==floor)return i;throw new AssertionError("floor missing "+floor);}
    public void receivePostUpdate(){if(System.currentTimeMillis()<next||stage==99)return;next=System.currentTimeMillis()+700;try{
      if(System.currentTimeMillis()-started>150000)throw new AssertionError("review timeout stage "+stage);
      HistoryScreen h=HistoryScreen.instance;
      switch(stage){
      case 0:
        if(CardCrawlGame.mainMenuScreen==null||CardCrawlGame.mode!=CardCrawlGame.GameMode.CHAR_SELECT||((com.badlogic.gdx.graphics.Color)fade()).a>.01f)return;
        Settings.seed=1234567890123456789L;AbstractDungeon.generateSeeds();initialRng=TurnAdapter.rng();
        CardCrawlGame.saveSlot=2;HistoryScreen.open();stage++;break;
      case 1:
        if(h.indexTask!=null||h.detailTask!=null)return;
        HistoryIndex.Summary target=null;for(HistoryIndex.Summary run:h.index.runs)if(run.id.equals("70b4245f-a0f9-4dd5-9a9a-842629f65886"))target=run;
        check(target!=null&&h.selected!=null&&h.selected.id.equals(target.id),"entry directly selects latest run in current slot");stage++;break;
      case 2:
        if(h.detailTask!=null)return;h.review.sync();recorded=Recorder.copy(h.detail);
        check(h.review.model.floors.size()>=55,"whole route retained");check(!h.review.pinned&&h.review.focused()<0,"overview is default");
        check(HistoryIndex.num(h.review.model.finalState,"hp",-1)==23&&HistoryIndex.num(h.review.model.finalState,"gold",-1)==34,"actual ending resources");
        for(Hitbox hb:h.review.nodes)check(hb.x>=0&&hb.x+hb.width<=Settings.WIDTH&&hb.y>Settings.HEIGHT*.43f,"route within screen");
        check(h.review.model.floors.get(node(h,17)).act==1&&h.review.model.floors.get(node(h,17)).room.equals("TreasureRoomBoss"),"boss chest belongs to completed act");
        capture("review-overview-zh");stage++;break;
      case 3:
        if(!captured)return;hover(h.review.nodes.get(node(h,2)));h.update();check(h.review.focused()==node(h,2)&&!h.review.pinned,"hover shows floor without click");
        capture("review-hover-floor-2-zh");stage++;break;
      case 4:
        if(!captured)return;hover(h.home);h.update();check(h.review.focused()<0,"leaving hover restores overview");
        clicked(h.review.nodes.get(node(h,47)));h.update();check(h.review.pinned&&h.floorOrder.get(h.floorIndex)==47,"click pins chosen floor");capture("review-pinned-floor-47-zh");stage++;break;
      case 5:
        if(!captured)return;hover(h.home);h.update();check(h.review.focused()==node(h,47),"pinned detail persists");clicked(h.review.deckTab);h.update();check(h.review.showDeck,"recorded deck tab");capture("review-floor-deck-zh");stage++;break;
      case 6:
        if(!captured)return;clicked(h.next);h.update();check(h.floorOrder.get(h.floorIndex)==48,"next floor control");clicked(h.review.overview);h.update();check(!h.review.pinned,"return to overview");
        check(h.detail.equals(recorded)&&TurnAdapter.rng().equals(initialRng),"hover pin deck navigation and renders preserve record and all 13 RNGs");
        clicked(h.diagnostics);h.update();check(h.raw&&h.branchMenu!=null,"raw details available");clicked(h.diagnostics);h.update();check(!h.raw,"return from details");
        clicked(h.historyTab);h.update();check(h.selected==null&&h.historyMode,"global history navigation");clicked(h.latestTab);h.update();stage=7;break;
      case 7:
        if(h.detailTask!=null)return;check(h.selected!=null&&h.selected.id.equals("70b4245f-a0f9-4dd5-9a9a-842629f65886"),"latest tab returns to newest run");clicked(h.home);h.update();check(!h.historyMode,"global statistics navigation");stage=8;break;
      case 8:
        if(h.stats.task!=null||!h.stats.requested)return;check(!h.stats.advanced,"simple filters by default");clicked(h.stats.themes[3]);h.update();check(h.stats.theme==3&&!h.stats.chart.entries.isEmpty(),"pick tiers use actual run choices");capture("navigation-pick-tiers-zh");stage=9;break;
      case 9:
        if(!captured)return;
        Path blocked=StateMod.ROOT.getParent().resolve("exports");
        if(!Files.exists(blocked)){Files.write(blocked,"probe-blocker".getBytes(StandardCharsets.UTF_8));clicked(h.stats.exportButton);h.update();stage=90;break;}
        clicked(h.stats.exportButton);h.update();check(h.stats.chart.exportRequested,"native export button");stage=10;break;
      case 90:
        if(h.stats.chart.exportRequested)return;
        Path blocker=StateMod.ROOT.getParent().resolve("exports");check(h.stats.chart.lastExport==null&&h.stats.chart.message.startsWith(HistoryScreen.tr("导出失败：","Export failed:")),"unwritable export path gives visible failure");check(new String(Files.readAllBytes(blocker),StandardCharsets.UTF_8).equals("probe-blocker"),"existing blocking file preserved");Files.delete(blocker);
        report.addProperty("export_failure_recovery",true);clicked(h.stats.exportButton);h.update();stage=10;break;
      case 10:
        if(h.stats.chart.exportRequested)return;check(h.stats.chart.lastExport!=null,"PNG export completed");
        Path output=h.stats.chart.lastExport;check(output.getParent().equals(StateMod.ROOT.getParent().resolve("exports")),"exports outside run data");JsonObject meta=TurnFiles.read(output.resolve("metadata.json"));check(meta.get("act").getAsInt()==1&&meta.getAsJsonArray("entries").size()==h.stats.chart.entries.size(),"export scope and entry count match preview");
        for(JsonElement item:meta.getAsJsonArray("entries")){JsonObject e=item.getAsJsonObject();spireledger.stats.Metric metric=h.stats.metrics.get("pick:1:"+e.get("card").getAsString());check(metric!=null&&metric.numerator==e.get("picked").getAsDouble()&&metric.denominator==e.get("offered").getAsDouble(),"export counts match screen kernel");}
        Pixmap png=new Pixmap(new FileHandle(output.resolve("act-1-1.png").toFile()));check(png.getWidth()==1600&&png.getHeight()>365,"readable PNG dimensions");png.dispose();
        report.addProperty("export_directory",output.toString());report.addProperty("native_latest_navigation_and_png_export",true);
        dropdown(h.stats.chartAct,1);check(h.stats.chart.act==2,"chart act selection");dropdown(h.stats.chartAct,0);check(h.stats.chart.act==1,"chart act return");
        check(TurnAdapter.rng().equals(initialRng),"stats and image export preserve all RNGs");clicked(h.stats.advancedButton);h.update();stage=12;break;
      case 12:
        dropdown(h.stats.filters[5],1);check(h.stats.filter.minSamples==5&&h.stats.chart.entries.stream().anyMatch(e->e.tier==8),"low samples are separate from zero picks");capture("navigation-low-samples-zh");stage=13;break;
      case 13:
        if(!captured)return;clicked(h.latestTab);h.update();stage=11;break;
      case 11:
        if(h.detailTask!=null)return;check(h.stats.filter.minSamples==5,"stats filters survive navigation");check(h.detail.equals(recorded),"switching tabs preserves source record");clicked(h.review.back);h.update();check(!HistoryScreen.active(),"one click closes ledger from review");check(TurnAdapter.rng().equals(initialRng),"close preserves RNG");
        report.addProperty("hover_pin_deck_navigation",true);report.addProperty("all_13_rngs_unchanged",true);report.addProperty("source_json_unchanged",true);report.addProperty("width",Settings.WIDTH);report.addProperty("height",Settings.HEIGHT);finish(null);stage=99;break;
      }
    }catch(Throwable e){finish(e);stage=99;}}
}
