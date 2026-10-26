package spireledger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import spireledger.stats.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The same immutable metric rows drive the native preview and its PNG export. */
final class PickRateChart {
    static final int COLUMNS=6,PAGE_ROWS=18;
    static final Color[] COLORS={Color.valueOf("e5c07b"),Color.valueOf("98c379"),Color.valueOf("61afef"),Color.valueOf("c678dd"),Color.valueOf("e06c75"),Color.valueOf("d19a66"),Color.valueOf("7f848e"),Color.valueOf("626978"),Color.valueOf("8c929e")};
    final StatsScreen screen;final List<List<PickTiers.Entry>> rows=new ArrayList<>();
    List<PickTiers.Entry> entries=Collections.emptyList();int act=1,offset;boolean exportRequested;String message="";Path lastExport;
    PickRateChart(StatsScreen screen){this.screen=screen;}
    static String tr(String cn,String en){return HistoryScreen.tr(cn,en);}
    void rebuild(Map<String,Metric> metrics){entries=PickTiers.entries(metrics,act);rows.clear();for(PickTiers.Entry entry:entries){if(rows.isEmpty()||rows.get(rows.size()-1).size()==COLUMNS||rows.get(rows.size()-1).get(0).tier!=entry.tier)rows.add(new ArrayList<>());rows.get(rows.size()-1).add(entry);}offset=0;message="";}
    int visible(float top){return Math.max(1,(int)((top-Settings.HEIGHT*.23f)/(76*Settings.scale)));}
    void scroll(int amount){offset=Math.max(0,Math.min(Math.max(0,rows.size()-visible(screen.bodyTop()-35*Settings.scale)),offset+amount));}
    String tierName(int tier){return tier<7?"T"+tier:tier==7?tr("未选","Zero"):tr("样本不足","Low sample");}
    String range(int tier){return tier==0?"90–100%":tier<6?PickTiers.LOWER[tier]+"–<"+PickTiers.LOWER[tier-1]+"%":tier==6?">0–<15%":tier==7?"0%":"";}
    void text(SpriteBatch sb,String value,float x,float y,float width,float size,Color color){BitmapFont font=FontHelper.cardDescFont_N;float sx=font.getData().scaleX,sy=font.getData().scaleY;Color old=font.getColor().cpy();try{float factor=size/Math.max(1,font.getCapHeight());font.getData().setScale(sx*factor,sy*factor);String fit=HistoryLayout.fit(value,width,t->new GlyphLayout(font,t).width);FontHelper.renderFontLeftTopAligned(sb,font,fit,x,y,color);}finally{font.getData().setScale(sx,sy);font.setColor(old);}}
    void box(SpriteBatch sb,float x,float y,float width,float height,Color color){sb.setColor(color);sb.draw(ImageMaster.WHITE_SQUARE_IMG,x,y,width,height);sb.setColor(Color.WHITE);}
    void drawRow(SpriteBatch sb,List<PickTiers.Entry> row,float x,float top,float width,float height){
        int tier=row.get(0).tier;float tag=width*.11f,cell=(width-tag)/COLUMNS,pad=height*.10f;
        box(sb,x,top-height,width,height-3,new Color(.09f,.12f,.16f,1));box(sb,x,top-height,tag-5,height-3,COLORS[tier]);
        text(sb,tierName(tier),x+pad,top-pad,tag-pad*2,height*.30f,Color.BLACK);text(sb,range(tier),x+pad,top-height*.56f,tag-pad*2,height*.20f,Color.BLACK);
        for(int i=0;i<row.size();i++){
            PickTiers.Entry e=row.get(i);float left=x+tag+i*cell,portrait=height*.45f;AbstractCard card=CardLibrary.getCard(e.card);
            if(card!=null&&card.portrait!=null){sb.setColor(Color.WHITE);sb.draw(card.portrait,left+pad,top-pad-portrait,portrait,portrait);}
            text(sb,HistoryScreen.cardName(e.card),left+portrait+pad*2,top-pad,cell-portrait-pad*3,height*.28f,Settings.CREAM_COLOR);
            String rate=e.tier==8?tr("出现 ","Offered ")+screen.quantity(e.offered):String.format(Locale.ROOT,"%.1f%%  %s/%s",100*e.rate(),screen.quantity(e.picked),screen.quantity(e.offered));
            if(e.estimated>0)rate+=tr(" 含估算"," est.");text(sb,rate,left+pad,top-height*.65f,cell-pad*2,height*.22f,HistoryScreen.MUTED);
        }
    }
    void render(SpriteBatch sb){
        float top=screen.bodyTop();text(sb,tr("实际抓率：被选次数 / 出现次数 · 只描述你的选择","Observed pick rate: picked / offered · Your choices"),Settings.WIDTH*.06f,top,Settings.WIDTH*.87f,23*Settings.scale,Settings.GOLD_COLOR);
        top-=35*Settings.scale;int count=visible(top);
        if(rows.isEmpty())text(sb,tr("当前筛选没有选牌记录。试试其他职业或放宽筛选。","No reward choices in this selection. Adjust the filters."),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);
        for(int i=0;i<count&&offset+i<rows.size();i++)drawRow(sb,rows.get(offset+i),Settings.WIDTH*.06f,top-i*76*Settings.scale,Settings.WIDTH*.88f,76*Settings.scale);
        String footer=message.isEmpty()?tr("滚轮翻页 · ","Scroll · ")+(rows.isEmpty()?0:offset+1)+"–"+Math.min(rows.size(),offset+count)+" / "+rows.size()+tr(" 行"," rows"):message;
        text(sb,footer,Settings.WIDTH*.22f,Settings.HEIGHT*.19f,Settings.WIDTH*.71f,21*Settings.scale,HistoryScreen.MUTED);
        if(exportRequested){exportRequested=false;try{export(sb);message=tr("图片已保存：","Saved: ")+lastExport;}catch(Exception e){message=tr("导出失败：","Export failed: ")+e.getClass().getSimpleName();DiagnosticReport.error("pick_rate_export",e);}}
    }
    List<String> captions(){
        Stats.Filter f=screen.filter;List<String> text=new ArrayList<>();
        text.add(tr("职业：","Class: ")+(f.character.isEmpty()?tr("全部","all"):HistoryScreen.className(f.character))+tr(" · 进阶："," · Ascension: ")+(f.ascension<0?tr("全部","all"):"A"+f.ascension)+tr(" · 存档位 "," · Save slot ")+f.slot+tr(" · 最少出现 "," · Min offers ")+f.minSamples+tr(" 次",""));
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd");text.add(tr("日期：","Date: ")+(f.from==0?tr("不限","any"):date.format(new Date(f.from)))+" – "+(f.to==0?tr("不限","any"):date.format(new Date(f.to-1)))+tr(" · 标准模式 · 种子局包含"," · Standard mode · Seeded included"));
        text.add(tr("结果：","Results: ")+new String[]{tr("全部","all"),tr("仅胜","wins"),tr("仅败","losses")}[screen.selected[8]]+tr(" · 胜利口径："," · Wins: ")+(f.heart?tr("杀心","Heart"):tr("通过第三幕","act 3"))+(f.includeAbandon?tr(" · 包含放弃"," · Abandoned included"):tr(" · 不含放弃"," · Abandoned excluded")));
        text.add(tr("来源：","Sources: ")+new String[]{tr("原版战绩与账本合并","native + Ledger merged"),tr("有原版战绩","has native history"),tr("有账本记录","has Ledger record")}[screen.selected[6]]+(f.slOnly?tr(" · 仅已记录 SL"," · Recorded SL only"):tr(" · 最终尝试路径"," · Final attempt")));
        Metric unknown=screen.metrics.get("reward_source:unknown");text.add(tr("包含操作来源未知的选择","Unknown action sources included")+(unknown==null?"":" ("+screen.quantity(unknown.numerator)+"/"+screen.quantity(unknown.denominator)+")")+tr(" · 排除已知自动化"," · Known automation excluded"));
        text.add(tr("选中 ","Selected ")+screen.selection.runs.size()+tr(" 局 · 每张牌标出抓取/出现次数 · 分档不代表强度"," runs · Each card shows picked/offered · Tiers describe pick rates"));
        text.add("metrics-v1 · "+PickTiers.VERSION+" · "+new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(new Date()));return text;
    }
    void export(SpriteBatch sb)throws Exception{
        if(rows.isEmpty()){message=tr("没有可导出的抓率记录。","No pick rates to export.");return;}
        Path root=StateMod.ROOT.getParent().resolve("exports");Files.createDirectories(root);Path directory=Files.createDirectory(root.resolve("pick-rate-"+new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)));
        List<Path> written=new ArrayList<>();Matrix4 projection=new Matrix4(sb.getProjectionMatrix()),transform=new Matrix4(sb.getTransformMatrix());List<String> captions=captions();
        try{
            int pages=(rows.size()+PAGE_ROWS-1)/PAGE_ROWS;
            for(int page=0;page<pages;page++){
                int count=Math.min(PAGE_ROWS,rows.size()-page*PAGE_ROWS),width=1600,height=365+count*85;
                FrameBuffer buffer=null;Pixmap pixmap=null;sb.end();
                try{
                    buffer=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false);buffer.begin();Gdx.gl.glClearColor(.045f,.06f,.085f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    sb.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));sb.setTransformMatrix(new Matrix4());sb.begin();
                    text(sb,tr("尖塔账本 · 抓率梯度图 · 第 ","Spire Ledger · Pick rates · Act ")+act+tr(" 幕","")+"  ("+(page+1)+"/"+pages+")",35,height-25,width-70,33,Settings.GOLD_COLOR);
                    for(int i=0;i<captions.size();i++)text(sb,captions.get(i),35,height-76-i*30,width-70,22,Settings.CREAM_COLOR);
                    for(int i=0;i<count;i++)drawRow(sb,rows.get(page*PAGE_ROWS+i),35,height-310-i*85,width-70,85);
                    sb.flush();pixmap=ScreenUtils.getFrameBufferPixmap(0,0,width,height);sb.end();
                    Path file=directory.resolve("act-"+act+"-"+(page+1)+".png");written.add(file);PixmapIO.PNG png=new PixmapIO.PNG();try{png.setFlipY(true);png.write(new FileHandle(file.toFile()),pixmap);}finally{png.dispose();}
                }finally{if(sb.isDrawing())sb.end();if(pixmap!=null)pixmap.dispose();if(buffer!=null){buffer.end();buffer.dispose();}sb.setProjectionMatrix(projection);sb.setTransformMatrix(transform);sb.begin();}
            }
            JsonObject data=new JsonObject();data.addProperty("metrics_version","metrics-v1");data.addProperty("tier_version",PickTiers.VERSION);data.addProperty("act",act);data.addProperty("run_count",screen.selection.runs.size());data.add("captions",new Gson().toJsonTree(captions));data.add("filters",new Gson().toJsonTree(screen.filter));data.add("excluded",new Gson().toJsonTree(screen.selection.excluded));data.add("entries",new Gson().toJsonTree(entries));
            Path metadata=directory.resolve("metadata.json");written.add(metadata);Files.write(metadata,new GsonBuilder().setPrettyPrinting().create().toJson(data).getBytes(StandardCharsets.UTF_8));lastExport=directory;
        }catch(Exception failure){for(Path file:written)try{Files.deleteIfExists(file);}catch(Exception cleanup){failure.addSuppressed(cleanup);}try{Files.deleteIfExists(directory);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
}
