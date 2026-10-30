package spireledger;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.google.gson.*;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import spireledger.stats.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The same immutable metric rows drive the native preview and its PNG/CSV export, for observed, standardized and selected-run win rates. */
final class PickRateChart {
    static final int COLUMNS=6,LIST_COLUMNS=3,TIERS=7;
    static final float CARD_ROW=85,LIST_ROW=96,HEADER_ROW=48,RULE_ROW=34;
    static final int ACTUAL=9,LOW=-2,UNTIERED=-3;
    static final Color[] COLORS={Color.valueOf("e5c07b"),Color.valueOf("98c379"),Color.valueOf("61afef"),Color.valueOf("c678dd"),Color.valueOf("e06c75"),Color.valueOf("d19a66"),Color.valueOf("7f848e"),Color.valueOf("626978"),Color.valueOf("8c929e"),Color.valueOf("56b6c2")};
    static final String SKIP="#skip",BOWL="#bowl";
    final StatsScreen screen;final List<List<PickTiers.Entry>> rows=new ArrayList<>();
    List<PickTiers.Entry> entries=Collections.emptyList();List<Section> sections=Collections.emptyList();final List<ListLine> listLines=new ArrayList<>();
    int act=1,offset,mode,kind;boolean exportRequested;String message="";Path lastExport;final Map<String,Texture> monsterTextures=new HashMap<>();Map<String,Path> monsterIndex;
    static final Set<String> BOSSES=new HashSet<>(Arrays.asList("Hexaghost","Slime Boss","The Guardian","Collector","Automaton","Champ","Time Eater","Awakened One","Donu and Deca"));
    static final String[] HP_TIERS={"T0 BOSS（单独排序）","T1 极危","T2 高危","T3 中危","T4 低危"};
    PickRateChart(StatsScreen screen){this.screen=screen;}
    static String tr(String cn,String en){return HistoryScreen.tr(cn,en);}
    /** One tile of a non-card export: a relic image, a blank card placeholder, and its value in the section's tiers. */
    static final class Item{
        final String card,relic,monster,name,detail,measure;int tone;final boolean estimated;final double value;int tier;Metric source;
        Item(String card,String relic,String name,String detail,int tone,boolean estimated){this(card,relic,null,name,detail,tone,estimated,0,"");}
        Item(String card,String relic,String name,String detail,int tone,boolean estimated,double value,String measure){this(card,relic,null,name,detail,tone,estimated,value,measure);}
        Item(String card,String relic,String monster,String name,String detail,int tone,boolean estimated,double value,String measure){this.card=card;this.relic=relic;this.monster=monster;this.name=name;this.detail=detail;this.tone=tone;this.estimated=estimated;this.value=value;this.measure=measure;this.tier=UNTIERED;}
        Item with(Metric metric){source=metric;return this;}
    }
    static final class Section{
        final int act;final String title,note;final List<Item> items;final boolean tiered;final String[] tierNames;
        Section(int act,String title,String note,List<Item> items){this(act,title,note,items,true,null);}
        Section(int act,String title,String note,List<Item> items,boolean tiered){this(act,title,note,items,tiered,null);}
        Section(int act,String title,String note,List<Item> items,boolean tiered,String[] tierNames){this.act=act;this.title=title;this.note=note;this.items=items;this.tiered=tiered;this.tierNames=tierNames;}
    }
    /** A preview/export row: a section heading, a tier rule with its value range, or up to LIST_COLUMNS tiles. */
    static final class ListLine{
        static final int HEADER=0,RULE=1,ITEMS=2;
        final int type;final String title,note;final List<Item> items;
        private ListLine(int type,String title,String note,List<Item> items){this.type=type;this.title=title;this.note=note;this.items=items;}
        static ListLine header(String title,String note){return new ListLine(HEADER,title,note,Collections.<Item>emptyList());}
        static ListLine rule(String name,String range){return new ListLine(RULE,name,range,Collections.<Item>emptyList());}
        static ListLine items(List<Item> items){return new ListLine(ITEMS,"","",items);}
    }
    void rebuild(Map<String,Metric> metrics){entries=kind==0?cards(metrics,mode):Collections.<PickTiers.Entry>emptyList();rows.clear();rows.addAll(group(entries));sections=buildSections(metrics,mode,kind==2?0:act);layoutSections();offset=0;message="";}
    boolean exportable(){return !screen.selection.runs.isEmpty();}
    /** Cards are tiered per act, so the all-acts preview stays on act 1 while the export writes every act separately. */
    int previewAct(){return act>0?act:1;}
    /** One tier per row, at most COLUMNS cards per row; entries arrive tier-ascending. */
    static List<List<PickTiers.Entry>> group(List<PickTiers.Entry> source){List<List<PickTiers.Entry>> out=new ArrayList<>();for(PickTiers.Entry entry:source){if(out.isEmpty()||out.get(out.size()-1).size()==COLUMNS||out.get(out.size()-1).get(0).tier!=entry.tier)out.add(new ArrayList<>());out.get(out.size()-1).add(entry);}return out;}
    /** Skip is fitted and ranked with the cards; Bowl is folded into that Skip share and stays as an extra observed row. */
    List<PickTiers.Entry> cards(Map<String,Metric> metrics,int view){return cards(metrics,view,previewAct());}
    List<PickTiers.Entry> cards(Map<String,Metric> metrics,int view,int a){
        if(view==1){Map<String,Double> shares=screen.reportedShares(a);if(shares==null)return Collections.emptyList();
            List<PickTiers.Entry> out=new ArrayList<>(PickTiers.standardized(metrics,a,shares));
            Metric bowl=metrics.get("bowl:"+a);if(listed(bowl))out.add(PickTiers.actual(BOWL,a,bowl));return out;}
        List<PickTiers.Entry> out=PickTiers.entries(metrics,a);Metric skip=metrics.get("skip:"+a),bowl=metrics.get("bowl:"+a);
        if(listed(skip))out.add(PickTiers.observed(SKIP,a,skip));if(listed(bowl))out.add(PickTiers.observed(BOWL,a,bowl));
        PickTiers.sort(out);return out;
    }
    static boolean listed(Metric metric){return metric!=null&&(metric.shown()||metric.status.equals("below_min_sample"));}
    Map<String,Double> shares(){return screen.reportedShares(previewAct());}
    boolean hasStandard(){switch(kind){case 1:return !buildSections(screen.metrics,1).isEmpty();case 2:return !screen.neowShares.isEmpty();case 3:return !screen.bossShares.isEmpty();default:return shares()!=null;}}
    int visible(float top){return Math.max(1,(int)((top-Settings.HEIGHT*.23f)/(76*Settings.scale)));}
    int visibleList(float top){int count=0;float y=top;for(int i=0;i<listLines.size();i++){float height=listHeight(listLines.get(i))*Settings.scale;if(y-height<Settings.HEIGHT*.19f)break;y-=height;count++;}return Math.max(1,count);}
    static float listHeight(ListLine line){return line.type==ListLine.HEADER?HEADER_ROW:line.type==ListLine.RULE?RULE_ROW:LIST_ROW;}
    void scroll(int amount){
        if(kind==0){offset=Math.max(0,Math.min(Math.max(0,rows.size()-visible(screen.bodyTop()-35*Settings.scale)),offset+amount));return;}
        offset=Math.max(0,Math.min(Math.max(0,listLines.size()-visibleList(screen.bodyTop()-35*Settings.scale)),offset+amount));
    }
    String tierName(int tier,int view){if(tier==ACTUAL)return tr("实际占比","Observed");if(view==1)return PickTiers.standardName(tier);return tier<7?"T"+tier:tier==7?tr("未选","Zero"):tr("样本不足","Low sample");}
    String range(int tier,int view,List<PickTiers.Entry> source){
        if(tier==ACTUAL)return "";
        if(view==1){double low=Double.MAX_VALUE,high=-1;for(PickTiers.Entry entry:source)if(entry.tier==tier){low=Math.min(low,entry.standard);high=Math.max(high,entry.standard);}return high<0?"":Double.compare(low,high)==0?share(low):share(low)+"–"+share(high);}
        return tier==0?"90–100%":tier<6?PickTiers.LOWER[tier]+"–<"+PickTiers.LOWER[tier-1]+"%":tier==6?">0–<15%":tier==7?"0%":"";
    }
    static String decimal(double value){return value>=10?String.format(Locale.ROOT,"%.1f",value):value>=1?String.format(Locale.ROOT,"%.2f",value):String.format(Locale.ROOT,"%.3f",value);}
    static String share(double value){return decimal(value)+"%";}
    static String tierLabel(int tier){if(tier>=0&&tier<TIERS)return PickTiers.standardName(tier);if(tier==LOW)return tr("样本不足","Low sample");return "";}
    /** Range of the values the tier actually holds, so the image states where its S..R00 cuts fell. */
    static String tierRange(int tier,List<Item> group){
        if(tier<0)return "";double low=Double.MAX_VALUE,high=-Double.MAX_VALUE;String unit="";
        for(Item item:group)if(item.tier==tier){low=Math.min(low,item.value);high=Math.max(high,item.value);unit=item.measure;}
        if(high<low)return "";return Double.compare(low,high)==0?decimal(low)+unit:decimal(low)+unit+" – "+decimal(high)+unit;
    }
    /** Fisher-Jenks S..R00 over one section's displayed values; below-minimum rows stay separate at the end. */
    static void tier(List<Item> items){
        List<Double> values=new ArrayList<>();for(Item item:items)if(item.tier!=LOW)values.add(item.value);
        int[] tiers=PickTiers.naturalTiers(values,TIERS);int k=0;
        for(Item item:items)if(item.tier!=LOW)item.tier=tiers[k++];
        items.sort((a,b)->{int by=Integer.compare(a.tier<0?99:a.tier,b.tier<0?99:b.tier);if(by!=0)return by;by=Double.compare(b.value,a.value);return by!=0?by:a.name.compareTo(b.name);});
    }
    static void low(Item item,Metric metric){if(metric==null||!metric.shown())item.tier=LOW;}
    static String tileName(String id){if(SKIP.equals(id)||Standardized.SKIP.equals(id))return tr("跳过","Skip");if(BOWL.equals(id))return tr("歌唱碗","Singing Bowl");return HistoryScreen.cardName(id);}
    void text(SpriteBatch sb,String value,float x,float y,float width,float size,Color color){BitmapFont font=FontHelper.cardDescFont_N;float sx=font.getData().scaleX,sy=font.getData().scaleY;Color old=font.getColor().cpy();try{float factor=size/Math.max(1,font.getCapHeight());font.getData().setScale(sx*factor,sy*factor);String fit=HistoryLayout.fit(value,width,t->new GlyphLayout(font,t).width);FontHelper.renderFontLeftTopAligned(sb,font,fit,x,y,color);}finally{font.getData().setScale(sx,sy);font.setColor(old);}}
    void box(SpriteBatch sb,float x,float y,float width,float height,Color color){sb.setColor(color);sb.draw(ImageMaster.WHITE_SQUARE_IMG,x,y,width,height);sb.setColor(Color.WHITE);}
    /** Card-shaped empty frame used for Skip, campfire actions and Neow options, which have no portrait of their own. */
    void blank(SpriteBatch sb,float x,float y,float width,float height){box(sb,x,y,width,height,new Color(.24f,.28f,.34f,1));box(sb,x+2,y+2,width-4,height-4,new Color(.08f,.10f,.14f,1));}
    void portrait(SpriteBatch sb,String id,float x,float y,float size){
        if(SKIP.equals(id)){blank(sb,x,y,size,size);return;}
        if(BOWL.equals(id)){relic(sb,"Singing Bowl",x,y,size);return;}
        AbstractCard card=CardLibrary.getCard(id);if(card!=null&&card.portrait!=null){sb.setColor(Color.WHITE);sb.draw(card.portrait,x,y,size,size);return;}
        blank(sb,x,y,size,size);
    }
    void relic(SpriteBatch sb,String id,float x,float y,float size){AbstractRelic relic=RelicLibrary.getRelic(id);if(relic!=null&&relic.img!=null){sb.setColor(Color.WHITE);sb.draw(relic.img,x,y,size,size);}else blank(sb,x,y,size,size);}
    /** Encounter art supplied by the player, like TinyCat: the game's own monster files are Spine atlases, so no portrait is read from the jar. */
    void monster(SpriteBatch sb,String id,float x,float y,float size){Texture texture=monsterTexture(id);if(texture==null){blank(sb,x,y,size,size);return;}
        float scale=Math.min(size/texture.getWidth(),size/texture.getHeight());float w=texture.getWidth()*scale,h=texture.getHeight()*scale;
        sb.setColor(Color.WHITE);sb.draw(texture,x+(size-w)/2,y+(size-h)/2,w,h);}
    Texture monsterTexture(String id){
        String key=normalizeMonster(id);if(key.isEmpty())return null;if(monsterTextures.containsKey(key))return monsterTextures.get(key);
        Texture texture=null;Path file=monsterFile(id);
        if(file!=null)try{texture=new Texture(new FileHandle(file.toFile()));texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);}catch(Exception error){texture=null;}
        if(texture==null)texture=bundledMonster(key);
        monsterTextures.put(key,texture);return texture;}
    /** Images packed into a private test build at build time; the release jar has none, so local files and the blank tile still apply. */
    static Texture bundledMonster(String key){
        if(key==null||key.isEmpty())return null;
        try(InputStream stream=PickRateChart.class.getResourceAsStream(monsterResource(key))){
            if(stream==null)return null;
            ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int read;
            while((read=stream.read(chunk))>0)data.write(chunk,0,read);
            byte[] png=data.toByteArray();Pixmap pixmap=new Pixmap(png,0,png.length);
            try{Texture texture=new Texture(pixmap);texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return texture;}finally{pixmap.dispose();}
        }catch(Exception error){return null;}}
    static String monsterResource(String id){return "/monster-images/"+normalizeMonster(id)+".png";}
    static boolean bundledMonsterImages(){return PickRateChart.class.getResource("/monster-images/bundled.txt")!=null;}
    Path monsterFile(String id){
        Map<String,Path> index=monsterIndex();if(index.isEmpty())return null;
        Path path=index.get(normalizeMonster(id));if(path==null)path=index.get(normalizeMonster(screen.encounter(id)));return path;}
    /** Images are read from the local game folder or the mod's data folder; nothing is downloaded or bundled. */
    Map<String,Path> monsterIndex(){
        if(monsterIndex!=null)return monsterIndex;
        monsterIndex=new HashMap<>();List<Path> roots=new ArrayList<>();
        for(String path:new String[]{"mods/SpireLedger/monster-images","mods/spire-ledger/monster-images","monster-images"})try{roots.add(Gdx.files.local(path).file().toPath());}catch(Exception ignored){}
        roots.add(StateMod.ROOT.getParent().resolve("monster-images"));
        for(Path root:roots){if(!Files.isDirectory(root))continue;
            try(java.util.stream.Stream<Path> walk=Files.walk(root)){
                walk.filter(Files::isRegularFile).sorted().forEach(path->{String name=path.getFileName().toString();int dot=name.lastIndexOf('.');if(dot<=0)return;
                    String extension=name.substring(dot+1).toLowerCase(Locale.ROOT);if(!extension.equals("png")&&!extension.equals("jpg")&&!extension.equals("jpeg"))return;
                    String key=normalizeMonster(name.substring(0,dot));if(!key.isEmpty()&&!monsterIndex.containsKey(key))monsterIndex.put(key,path);});
            }catch(Exception ignored){}}
        return monsterIndex;}
    static String normalizeMonster(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    void dispose(){for(Texture texture:monsterTextures.values())if(texture!=null)texture.dispose();monsterTextures.clear();monsterIndex=null;}
    void image(SpriteBatch sb,Item item,float x,float y,float size){if(item.card!=null&&!item.card.isEmpty())portrait(sb,item.card,x,y,size);else if(item.relic!=null&&!item.relic.isEmpty())relic(sb,item.relic,x,y,size);else if(item.monster!=null&&!item.monster.isEmpty())monster(sb,item.monster,x,y,size);else blank(sb,x,y,size,size);}
    void drawRow(SpriteBatch sb,List<PickTiers.Entry> row,float x,float top,float width,float height,int view,List<PickTiers.Entry> source){
        int tier=row.get(0).tier;float tag=width*.11f,cell=(width-tag)/COLUMNS,pad=height*.10f;
        box(sb,x,top-height,width,height-3,new Color(.09f,.12f,.16f,1));box(sb,x,top-height,tag-5,height-3,COLORS[Math.min(tier,COLORS.length-1)]);
        text(sb,tierName(tier,view),x+pad,top-pad,tag-pad*2,height*.30f,Color.BLACK);text(sb,range(tier,view,source),x+pad,top-height*.56f,tag-pad*2,height*.20f,Color.BLACK);
        for(int i=0;i<row.size();i++){
            PickTiers.Entry e=row.get(i);float left=x+tag+i*cell,portrait=height*.45f;
            portrait(sb,e.card,left+pad,top-pad-portrait,portrait);
            text(sb,tileName(e.card),left+portrait+pad*2,top-pad,cell-portrait-pad*3,height*.28f,Settings.CREAM_COLOR);
            String rate;
            if(view==1&&tier!=ACTUAL)rate=share(e.standard)+tr("  /  出现 ","  /  offered ")+screen.quantity(e.offered);
            else if(e.status.equals("below_min_sample"))rate=tr("样本不足","Too few samples")+" ("+screen.quantity(e.offered)+")";
            else if(e.tier==8)rate=tr("出现 ","Offered ")+screen.quantity(e.offered);
            else rate=String.format(Locale.ROOT,"%.1f%%  %s/%s",100*e.rate(),screen.quantity(e.picked),screen.quantity(e.offered));
            if(e.estimated>0)rate+=tr(" 含估算"," est.");text(sb,rate,left+pad,top-height*.65f,cell-pad*2,height*.22f,HistoryScreen.MUTED);
        }
    }
    String heading(){if(kind==1)return mode==1?tr("火堆标准化倍：占比 ÷ 随机基线 · 每张牌单独一行","Campfire preference: share ÷ random baseline · one row per card"):tr("火堆行动次数：每次火堆实际做了什么，锻造按牌逐张计数","Campfire action counts: what each rest site did, smiths by upgraded card");
        if(kind==2)return mode==2?tr("涅奥选项 · 胜率：选了该选项的局里赢了多少（选后相关）","Neow options · win rate: wins among runs that picked the option (association)"):mode==1?tr("涅奥标准率：同屏对齐后的强度份额 · 合计 100%","Neow standardized: opponent-adjusted shares · sums to 100%"):tr("涅奥选项：被选次数 / 出现次数","Neow options: picked / offered");
        if(kind==3)return mode==2?tr("Boss 遗物 · 胜率（按幕）：选了该遗物的局里赢了多少","Boss relics · win rate by act: wins among runs that picked the relic"):mode==1?tr("Boss 遗物标准率（按幕）：同屏对齐后的强度份额 · 含跳过","Boss relic standardized by act: opponent-adjusted shares · Skip included"):tr("Boss 遗物（按幕）：被选次数 / 出现次数","Boss relics by act: picked / offered");
        if(kind==4)return tr("实际战损：入层生命 − 本层结束生命 · 第 1–3 幕 Boss 为 T0、其余按场均战损分 T1–T4","Actual HP loss: entry HP − floor end HP · acts 1–3 keep bosses in T0 and split the rest into T1–T4");
        return (mode==1?tr("标准率：同屏对手对齐后的强度份额 · 合计 100%","Standardized rate: strength share after opponent adjustment · sums to 100%"):tr("实际抓率：被选次数 / 出现次数 · 只描述你的选择","Observed pick rate: picked / offered · Your choices"))+(kind==0&&act==0?tr(" · 全部幕（导出按幕分图，预览第 1 幕）"," · All acts (one image per act on export; preview shows act 1)"):"");}
    String unavailable(){if(kind==1)return tr("标准化倍不可用：当前筛选没有可用的火堆偏好记录。","Campfire preference unavailable: no usable rest-site choices in this selection.");
        if(kind==2||kind==3){if(mode==2)return tr("胜率不可用：仅胜/仅败筛选下不显示所选对局胜率；切回“全部”即可。","Win rates are hidden under a result filter; switch back to All.");
            return kind==2?tr("涅奥标准率不可用：至少需要两屏有效选择。","Neow standardized unavailable: at least two usable screens are needed."):tr("Boss 遗物标准率不可用：至少需要两屏有效选择。","Boss relic standardized unavailable: at least two usable screens are needed.");}
        if(kind==4)return tr("实际战损不可用：当前筛选没有可用的战斗战损记录。","Actual HP loss unavailable: no usable combat loss records in this selection.");
        return tr("标准率不可用：本幕至少需要 2 屏有效选牌，且筛选后仍保留 2 张不同卡。","Standardized rate unavailable: this act needs at least two usable reward screens and two distinct cards after filtering.");}
    void render(SpriteBatch sb){
        float top=screen.bodyTop();
        text(sb,heading(),Settings.WIDTH*.06f,top,Settings.WIDTH*.87f,23*Settings.scale,Settings.GOLD_COLOR);
        top-=35*Settings.scale;
        if(kind==0)renderCards(sb,top);else renderSections(sb,top);
        String footer=message.isEmpty()?tr("滚轮翻页 · ","Scroll · ")+(kind==0?(rows.isEmpty()?0:offset+1)+"–"+Math.min(rows.size(),offset+visible(top))+" / "+rows.size()+tr(" 行"," rows"):(listLines.isEmpty()?0:offset+1)+"–"+Math.min(listLines.size(),offset+visibleList(top))+" / "+listLines.size()+tr(" 行"," rows")):message;
        text(sb,footer,Settings.WIDTH*.22f,Settings.HEIGHT*.19f,Settings.WIDTH*.71f,21*Settings.scale,HistoryScreen.MUTED);
        if(exportRequested){exportRequested=false;try{export(sb);message=(message.isEmpty()?tr("图片与 CSV 已保存：","Saved images and CSV: "):message)+lastExport;}catch(Exception e){message=tr("导出失败：","Export failed: ")+e.getClass().getSimpleName();DiagnosticReport.error("pick_rate_export",e);}}
    }
    void renderCards(SpriteBatch sb,float top){
        if(mode==1&&!hasStandard()){text(sb,unavailable(),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        if(rows.isEmpty()){text(sb,tr("当前筛选没有选牌记录。试试其他职业或放宽筛选。","No reward choices in this selection. Adjust the filters."),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        int count=visible(top);
        for(int i=0;i<count&&offset+i<rows.size();i++)drawRow(sb,rows.get(offset+i),Settings.WIDTH*.06f,top-i*76*Settings.scale,Settings.WIDTH*.88f,76*Settings.scale,mode,entries);
    }
    void renderSections(SpriteBatch sb,float top){
        if(sections.isEmpty()){text(sb,mode==1||mode==2?unavailable():tr("当前筛选没有该类记录。试试其他职业或放宽筛选。","No rows of this kind in this selection. Adjust the filters."),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        int count=visibleList(top);float y=top;
        for(int i=offset;i<listLines.size()&&i<offset+count;i++){
            ListLine line=listLines.get(i);
            if(line.type==ListLine.HEADER){drawHeader(sb,line.title,line.note,Settings.WIDTH*.06f,y,Settings.WIDTH*.88f,Settings.scale);y-=HEADER_ROW*Settings.scale;}
            else if(line.type==ListLine.RULE){drawRule(sb,line.title,line.note,Settings.WIDTH*.06f,y,Settings.WIDTH*.88f,RULE_ROW*Settings.scale,Settings.scale);y-=RULE_ROW*Settings.scale;}
            else{drawItemRow(sb,line.items,Settings.WIDTH*.06f,y-6*Settings.scale,Settings.WIDTH*.88f,LIST_ROW*Settings.scale,Settings.scale);y-=LIST_ROW*Settings.scale;}
        }
    }
    void drawHeader(SpriteBatch sb,String title,String note,float x,float top,float width,float scale){
        box(sb,x,top-HEADER_ROW*scale+6*scale,width,HEADER_ROW*scale-8*scale,new Color(.11f,.14f,.19f,1));
        text(sb,title,x+10*scale,top-5*scale,width*.36f,22*scale,Settings.GOLD_COLOR);
        if(note!=null&&!note.isEmpty())text(sb,note,x+width*.34f,top-7*scale,width*.64f,18*scale,HistoryScreen.MUTED);
    }
    void drawRule(SpriteBatch sb,String name,String range,float x,float top,float width,float height,float scale){
        box(sb,x,top-height+3*scale,width,height-4*scale,new Color(.10f,.13f,.18f,1));
        text(sb,name,x+10*scale,top-6*scale,width*.22f,height*.55f,Settings.GOLD_COLOR);
        if(range!=null&&!range.isEmpty())text(sb,range,x+width*.20f,top-8*scale,width*.78f,height*.42f,HistoryScreen.MUTED);
    }
    void drawItemRow(SpriteBatch sb,List<Item> items,float x,float top,float width,float height,float scale){
        float cell=width/LIST_COLUMNS,pad=height*.08f,size=height*.62f;
        for(int i=0;i<items.size();i++){
            Item item=items.get(i);float left=x+i*cell;
            image(sb,item,left+pad,top-pad-size,size);
            text(sb,item.name,left+size+pad*2,top-pad,cell-size-pad*3,height*.26f,item.tone>=0?COLORS[Math.min(item.tone,COLORS.length-1)]:Settings.CREAM_COLOR);
            String detail=item.detail+(item.estimated?tr(" 含估算"," est."):"");
            text(sb,detail,left+pad,top-height*.72f,cell-pad*2,height*.21f,HistoryScreen.MUTED);
        }
    }
    void layoutSections(){listLines.clear();for(Section section:sections){listLines.add(ListLine.header(section.title,section.note));listLines.addAll(sectionLines(section));}}
    /** Heading, then one rule row per tier and its tiles; untiered sections (action counts) list tiles only. */
    static List<ListLine> sectionLines(Section section){
        List<ListLine> out=new ArrayList<>();
        if(!section.tiered){for(int i=0;i<section.items.size();i+=LIST_COLUMNS)out.add(ListLine.items(new ArrayList<>(section.items.subList(i,Math.min(section.items.size(),i+LIST_COLUMNS)))));return out;}
        int i=0;
        while(i<section.items.size()){
            int tier=section.items.get(i).tier,j=i;while(j<section.items.size()&&section.items.get(j).tier==tier)j++;
            List<Item> group=section.items.subList(i,j);
            out.add(ListLine.rule(section.tierNames!=null&&tier>=0&&tier<section.tierNames.length?section.tierNames[tier]:tierLabel(tier),tierRange(tier,group)));
            for(int k=0;k<group.size();k+=LIST_COLUMNS)out.add(ListLine.items(new ArrayList<>(group.subList(k,Math.min(group.size(),k+LIST_COLUMNS)))));
            i=j;
        }
        return out;
    }
    List<Section> buildSections(Map<String,Metric> metrics,int view){return buildSections(metrics,view,kind==2?0:act<=0?0:Math.max(1,Math.min(act,kind==3?2:4)));}
    /** one==0 builds every act (the all-acts export); otherwise only that act is built. */
    List<Section> buildSections(Map<String,Metric> metrics,int view,int one){switch(kind){case 1:return campfire(metrics,view,one);case 2:return neow(metrics,view);case 3:return boss(metrics,view,one);case 4:return view==0?hpLoss(metrics,one):Collections.<Section>emptyList();default:return Collections.emptyList();}}
    static boolean hasPrefix(Map<String,Metric> metrics,String prefix){for(String key:metrics.keySet())if(key.startsWith(prefix))return true;return false;}
    List<Section> campfire(Map<String,Metric> metrics,int view,int one){
        List<Section> out=new ArrayList<>();
        for(int a:new int[]{1,2,3,4,0}){
            if(one>0&&a!=one)continue;
            Metric visits=metrics.get("campfire_visits:"+a);if(visits==null||visits.numerator<=0)continue;
            String title=(a==0?tr("四幕合计","All acts"):tr("第 ","Act ")+a+tr(" 幕",""))+tr(" · 火堆 "," · campfires ")+screen.quantity(visits.numerator)+tr(" 次"," visits");
            List<Item> items=new ArrayList<>();String note;
            if(view==0){
                Metric sleep=metrics.get("campfire_sleep:"+a);if(sleep!=null&&sleep.denominator>0)items.add(new Item("",null,tr("睡觉","Sleep"),pct(sleep),-1,false).with(sleep));
                for(String key:screen.campfireRows("campfire_count:"+a+":")){Metric m=metrics.get(key);if(m==null||m.numerator<=0)continue;String id=key.substring(key.lastIndexOf(':')+1);items.add(campfireItem(id,screen.quantity(m.numerator)+tr(" 次",""),-1,0,"").with(m));}
                out.add(new Section(a,title,tr("行动次数 · 锻造按被升级的牌逐张计数 · 计数不分档","Action counts · smiths by upgraded card · counts are not tiered"),items,false));
                continue;
            }
            Metric sleep=metrics.get("campfire_sleep:"+a);if(sleep!=null&&sleep.denominator>0&&listed(sleep)){Item item=new Item("",null,tr("睡觉","Sleep"),detail(sleep,pref(sleep)),-1,false,sleep.value()*100,"%");low(item,sleep);items.add(item.with(sleep));}
            for(String key:screen.campfireRows("campfire_pref:"+a+":")){Metric m=metrics.get(key);if(!listed(m))continue;String id=key.substring(key.lastIndexOf(':')+1);Item item=campfireItem(id,detail(m,pref(m)),-1,m.baseline>0?m.value()*100/m.baseline:0,"×");low(item,m);items.add(item.with(m));}
            tier(items);
            Metric smiths=metrics.get("campfire_smiths:"+a),pool=metrics.get("campfire_pool:"+a),rebuild=metrics.get("campfire_rebuild:"+a);
            StringBuilder builder=new StringBuilder();if(smiths!=null&&visits.numerator>0)builder.append(tr("锻造 ","smith ")).append(screen.percent(smiths.numerator/visits.numerator));
            if(pool!=null&&pool.value()>0)builder.append(tr(" · 平均候选 "," · mean pool ")).append(String.format(Locale.ROOT,"%.1f",pool.value())).append(tr(" · 随机基线 "," · baseline ")).append(String.format(Locale.ROOT,"%.1f%%",100.0/pool.value()));
            if(rebuild!=null&&rebuild.denominator>0)builder.append(tr(" · 重建 "," · rebuilt ")).append(screen.quantity(rebuild.numerator)).append("/").append(screen.quantity(rebuild.denominator));
            note=(builder.length()==0?tr("占比 ÷ 随机基线","Share ÷ random baseline"):builder.toString())+tr(" · S–R00 分档 · 同值同档"," · S–R00 tiers · equal values share a tier");
            out.add(new Section(a,title,note,items));
        }
        return out;
    }
    Item campfireItem(String id,String detail,int tone,double value,String measure){boolean card=CardLibrary.getCard(id)!=null;return new Item(card?id:"",null,screen.campfireName(id,false),detail,tone,false,value,measure);}
    List<Section> neow(Map<String,Metric> metrics,int view){
        List<Item> items=new ArrayList<>();String note,title=tr("涅奥选项","Neow options");
        if(view==0){
            for(String key:screen.campfireRows("neow:")){Metric m=metrics.get(key);if(!listed(m))continue;Item item=new Item("",null,screen.neow(key.substring(5)),detail(m,pct(m)),-1,m.estimated>0,m.value()*100,"%");low(item,m);items.add(item.with(m));}
            note=tr("被选 / 出现 · 同屏每个选项都计入出现 · S–R00 分档","Picked / offered · every option on the screen is an offer · S–R00 tiers");
        }else if(view==1){
            List<String> ids=new ArrayList<>(screen.neowShares.keySet());
            ids.sort((x,y)->{int by=Double.compare(screen.neowShares.get(y).doubleValue(),screen.neowShares.get(x).doubleValue());return by!=0?by:x.compareTo(y);});
            for(String id:ids){Metric m=metrics.get("neow:"+id);Item item=new Item("",null,screen.neow(id),standardDetail(m,share(screen.neowShares.get(id).doubleValue())+tr("  /  出现 ","  /  offered ")+screen.quantity(m==null?0:m.denominator)),-1,false,screen.neowShares.get(id).doubleValue(),"%");low(item,m);items.add(item.with(m));}
            note=tr("强度份额 · 合计 100% · 缺少未选记录的局不参与拟合 · S–R00 分档","Strength shares · sums to 100% · runs without skipped options are not fitted · S–R00 tiers");
        }else{
            title=tr("涅奥选项 · 胜率","Neow options · win rate");
            for(String key:screen.campfireRows("related:neow:")){Metric m=metrics.get(key);if(!listed(m))continue;Item item=new Item("",null,screen.neow(key.substring(13)),detail(m,pct(m)),-1,m.excluded>0,m.value()*100,"%");low(item,m);items.add(item.with(m));}
            note=tr("胜局 / 选了该选项的局 · 每局只计一次 · S–R00 分档","Wins / runs that picked it · once per run · S–R00 tiers");
        }
        tier(items);
        return items.isEmpty()?Collections.<Section>emptyList():Collections.singletonList(new Section(0,title,note,items));
    }
    List<Section> boss(Map<String,Metric> metrics,int view,int one){
        List<Section> out=new ArrayList<>();
        for(int a=1;a<=3;a++){
            if(one>0&&a!=one)continue;
            String prefix=view==2?"related:boss:"+a+":":"boss:"+a+":";
            if(view==2){if(!hasPrefix(metrics,prefix))continue;}else if(metrics.get("boss:"+a+":SKIP")==null)continue;
            List<Item> items=new ArrayList<>();
            if(view==0){
                for(String key:screen.campfireRows(prefix)){Metric m=metrics.get(key);if(!listed(m))continue;String id=key.substring(prefix.length());Item item=id.equals(Standardized.SKIP)?new Item("",null,tr("跳过","Skip"),detail(m,pct(m)),-1,false,m.value()*100,"%"):new Item("",id,HistoryScreen.relicName(id),detail(m,pct(m)),-1,m.estimated>0,m.value()*100,"%");low(item,m);items.add(item.with(m));}
            }else if(view==1){
                Map<String,Double> shares=screen.bossShares.get(Integer.valueOf(a));if(shares==null||shares.isEmpty())continue;
                List<String> ids=new ArrayList<>(shares.keySet());
                ids.sort((x,y)->{int by=Double.compare(shares.get(y).doubleValue(),shares.get(x).doubleValue());return by!=0?by:x.compareTo(y);});
                for(String id:ids){Metric m=metrics.get("boss:"+a+":"+id);boolean skip=id.equals(Standardized.SKIP);Item item=new Item("",skip?null:id,skip?tr("跳过","Skip"):HistoryScreen.relicName(id),standardDetail(m,share(shares.get(id).doubleValue())+tr("  /  出现 ","  /  offered ")+screen.quantity(m==null?0:m.denominator)),-1,false,shares.get(id).doubleValue(),"%");low(item,m);items.add(item.with(m));}
            }else{
                for(String key:screen.campfireRows(prefix)){Metric m=metrics.get(key);if(!listed(m))continue;String id=key.substring(prefix.length());Item item=id.equals(Standardized.SKIP)?new Item("",null,tr("跳过","Skip"),detail(m,pct(m)),-1,false,m.value()*100,"%"):new Item("",id,HistoryScreen.relicName(id),detail(m,pct(m)),-1,m.excluded>0,m.value()*100,"%");low(item,m);items.add(item.with(m));}
            }
            if(items.isEmpty())continue;
            tier(items);
            String title=tr("第 ","Act ")+a+tr(" 幕 · Boss 遗物"," · boss relics")+(view==2?tr(" · 胜率"," · win rate"):"");
            String note=view==0?tr("被选 / 出现 · 跳过是同屏选项 · S–R00 分档","Picked / offered · Skip is a screen option · S–R00 tiers"):view==1?tr("强度份额 · 合计 100% · 含跳过 · S–R00 分档","Strength shares · sums to 100% · Skip included · S–R00 tiers"):tr("胜局 / 选了该遗物的局 · 每局只计一次 · S–R00 分档","Wins / runs that picked it · once per run · S–R00 tiers");
            out.add(new Section(a,title,note,items));
        }
        return out;
    }
    /** One section per act: acts 1-3 put the per-run total alone, then TinyCat's monster tiers; act 4 stays untiered. */
    List<Section> hpLoss(Map<String,Metric> metrics,int one){
        List<Section> out=new ArrayList<>();
        for(int a=1;a<=4;a++){
            if(one>0&&a!=one)continue;
            Metric total=metrics.get("act_hp_loss:"+a);List<String> keys=hpLossKeys(metrics,a);
            if(total==null&&keys.isEmpty())continue;
            String title=tr("第 ","Act ")+a+tr(" 幕 · 实际战损"," · actual HP loss");
            if(a<=3){
                if(total!=null){Item item=new Item("",null,tr("本幕总战损（每局）","Act total per run"),detail(total,hp(total,tr("局","runs"))),-1,total.estimated>0,total.value()," HP");low(item,total);
                    out.add(new Section(a,title,tr("入层生命 − 本层结束生命 · 每局合计","Entry HP − floor end HP · per run"),Collections.singletonList(item.with(total)),false));}
                List<Item> bosses=new ArrayList<>(),rest=new ArrayList<>();
                for(String key:keys){Metric m=metrics.get(key);if(!listed(m))continue;Item item=new Item("",null,encounter(key),screen.encounter(encounter(key)),detail(m,hp(m,tr("场","fights"))),-1,m.estimated>0,m.value()," HP").with(m);low(item,m);
                    if(BOSSES.contains(encounter(key))){item.tier=0;bosses.add(item);}else rest.add(item);}
                Comparator<Item> byLoss=(x,y)->{int by=Double.compare(y.value,x.value);if(by!=0)return by;by=Integer.compare(y.source==null?0:y.source.samples,x.source==null?0:x.source.samples);return by!=0?by:x.name.compareTo(y.name);};
                bosses.sort(byLoss);rest.sort(byLoss);
                int base=rest.size()/4,extra=rest.size()%4,index=0;for(int t=1;t<=4;t++){int count=base+(t<=extra?1:0);for(int k=0;k<count;k++)rest.get(index++).tier=t;}
                List<Item> fights=new ArrayList<>(bosses);fights.addAll(rest);for(Item item:fights)if(item.tier>=0)item.tone=item.tier;
                if(!fights.isEmpty())out.add(new Section(a,title+" · "+tr("遭遇危险度","danger"),tr("Boss 单列 T0，其余等人数分 T1–T4；事件战与第四幕不计入","T0 keeps bosses apart, others split into T1–T4 by equal counts; events and act 4 are left out"),fights,true,HP_TIERS));
            }else{
                List<Item> items=new ArrayList<>();
                if(total!=null){Item item=new Item("",null,tr("本幕总战损（每局）","Act total per run"),detail(total,hp(total,tr("局","runs"))),-1,total.estimated>0,total.value()," HP");low(item,total);items.add(item.with(total));}
                for(String key:keys){Metric m=metrics.get(key);if(!listed(m))continue;Item item=new Item("",null,encounter(key),screen.encounter(encounter(key)),detail(m,hp(m,tr("场","fights"))),-1,m.estimated>0,m.value()," HP");low(item,m);items.add(item.with(m));}
                if(items.isEmpty())continue;
                out.add(new Section(a,title,tr("入层生命 − 本层结束生命 · 场均战损降序 · 总行为每局 · 第四幕不分档","Entry HP − floor end HP · mean loss descending · the total row is per run · act 4 is not tiered"),items,false));
            }
        }
        return out;
    }
    List<String> hpLossKeys(Map<String,Metric> metrics,int act){
        List<String> keys=new ArrayList<>();
        for(String key:metrics.keySet())if(key.startsWith("hp_loss:"+act+":"))keys.add(key);
        keys.sort((x,y)->{Metric a=metrics.get(x),b=metrics.get(y);boolean sa=a.shown(),sb=b.shown();if(sa!=sb)return sa?-1:1;
            int by=Double.compare(sb?b.value():0,sa?a.value():0);if(by!=0)return by;by=Integer.compare(b.samples,a.samples);return by!=0?by:x.compareTo(y);});
        return keys;
    }
    static String encounter(String key){String[] parts=key.split(":",3);return parts.length>2?parts[2]:key;}
    String hp(Metric metric,String unit){return String.format(Locale.ROOT,"%.1f HP  (%s %s)",metric.value(),screen.quantity(metric.samples),unit);}
    String detail(Metric metric,String shown){return metric.shown()?shown:tr("样本不足","Too few samples")+" ("+screen.quantity(metric.samples)+")";}
    /** Standard shares count the entry's offers as its sample, so a below-minimum relic stays out of the tiers. */
    String standardDetail(Metric metric,String shown){return metric!=null&&metric.shown()?shown:tr("样本不足","Too few samples")+" ("+screen.quantity(metric==null?0:metric.samples)+")";}
    static String pct(Metric metric){return String.format(Locale.ROOT,"%.1f%%  %s/%s",100*metric.value(),String.valueOf((long)metric.numerator),String.valueOf((long)metric.denominator));}
    static String pref(Metric metric){return String.format(Locale.ROOT,"%.1f%% %.1f× (%s/%s)",metric.value()*100,metric.baseline>0?metric.value()*100/metric.baseline:0,String.valueOf((long)metric.numerator),String.valueOf((long)metric.denominator));}
    static String kindName(int kind){switch(kind){case 1:return "campfire";case 2:return "neow";case 3:return "boss";case 4:return "hp-loss";default:return "cards";}}
    /** Selected runs whose Ledger record has at least one known-automation decision; native-only runs stay unknown. */
    boolean solverUsed(){for(Run r:screen.selection.runs)if(r.automation.known()&&Boolean.TRUE.equals(r.automation.value))return true;return false;}
    String solverMark(){return solverUsed()?tr(" · 含求解器"," · Solver used"):"";}
    /** Filter "all" still says which ascensions the selection actually contains, so an A20-only sample is not hidden. */
    String ascensionLabel(Stats.Filter f){
        if(f.ascension>=0)return "A"+f.ascension;
        Integer min=null,max=null;
        for(Run r:screen.selection.runs){Integer a=r.ascension.or(null);if(a==null)continue;if(min==null||a<min)min=a;if(max==null||a>max)max=a;}
        if(min==null)return tr("全部","all");
        return min.equals(max)?tr("全部（实际 A","all (actual A")+min+tr("）",")"):tr("全部（A","all (A")+min+tr("–A","–A")+max+tr("）",")");
    }
    String winRate(){Metric m=screen.metrics.get("win");return m==null||m.samples<=0?"—":String.format(Locale.ROOT,"%.1f%%（%s/%s）",100*m.value(),screen.quantity(m.numerator),screen.quantity(m.denominator));}
    /** Only the fields worth reading in the image; source, date, slot and version details move to metadata remarks. */
    List<String> captions(int view){
        Stats.Filter f=screen.filter;List<String> text=new ArrayList<>();
        text.add(tr("职业：","Class: ")+(f.character.isEmpty()?tr("全部","all"):HistoryScreen.className(f.character))
            +tr(" · 进阶："," · Ascension: ")+ascensionLabel(f)
            +tr(" · 结果："," · Results: ")+new String[]{tr("全部","all"),tr("仅胜","wins"),tr("仅败","losses")}[screen.selected[8]]
            +tr(" · 口径："," · Wins: ")+(f.heart?tr("杀心","Heart"):tr("通过第三幕","act 3"))
            +(f.includeAbandon?tr(" · 包含放弃"," · Abandoned included"):tr(" · 不含放弃"," · Abandoned excluded")));
        text.add(tr("选中 ","Selected ")+screen.quantity(screen.selection.runs.size())+tr(" 局 · 胜率 "," runs · Win rate ")+winRate()+tr(" · 最少出现 "," · Min offers ")+f.minSamples+tr(" 次",""));
        if(kind==4){
            text.add(tr("口径：实际战损 = 入层生命 − 本层结束生命（每场平均）；第一层缺入层记录时为估算","Rate: actual loss = entry HP − floor end HP, mean per fight; floor 1 is estimated without an entry record"));
            text.add(tr("分档：参考 TinyCat，Boss 单列 T0，其余按场均战损等人数分 T1–T4；事件战与第四幕不分档。","Tiers follow TinyCat: bosses stay in T0, the rest split into T1–T4 by equal counts on mean loss; event fights and act 4 are not tiered."));
            if(bundledMonsterImages())text.add(tr("本构建已内置遭遇测试图片，仅限私人测试，不可分发。","This build bundles encounter test images for private testing only and must not be distributed."));
            return text;
        }
        if(kind==0){
            text.add(tr("口径：","Rate: ")+(view==1?tr("标准率 · Plackett-Luce 强度份额（a=1, b=0.01；合计 100%，含跳过，不含置信区间）","standardized · Plackett-Luce strength share (a=1, b=0.01; sums to 100% with Skip, no CI)"):tr("一般率 · 被选次数 / 出现次数","observed · picked / offered")));
            text.add(view==1?tr("分档：S–R00 · ≥1% 用 5 档 Fisher-Jenks；<1% 按 log10 分 R0/R00；同值同档，跳过/歌唱碗并入。","Tiers: S–R00 · ≥1% uses 5 Fisher-Jenks classes; <1% splits into R0/R00 on log10; ties share a tier, Skip/Bowl folded in."):tr("分档：T0–T6 固定阈值 90/75/60/45/30/15%；未选（0%）与样本不足单列；跳过与歌唱碗并入各幕候选。","Tiers: T0–T6 at fixed 90/75/60/45/30/15%; zero picks and low samples are separate; Skip and Bowl join each act's candidates."));
            return text;
        }
        if(kind==1){
            text.add(view==1?tr("口径：标准化倍 = 占比 ÷ 随机基线；候选池 = 可升级牌 + 抽烟/举重/挖掘/回忆；不含睡觉/锻造。","Multiple = share ÷ random baseline; pool = ungraded cards plus Toke/Lift/Dig/Recall; sleep and smith are not options"):tr("口径：行动次数 · 锻造按被升级的牌逐张计数","Rate: action counts · each smith counted under the card it upgraded"));
            text.add(view==1?tr("分档：S–R00 · 对本段显示的倍数做 Fisher-Jenks（最多 7 档），同值同档；样本不足单列。","Tiers: S–R00 · Fisher-Jenks over the section multiples (up to 7 classes); ties share a tier; low samples separate."):tr("分档：行动次数是计数，不分档。","Tiers: action counts are counts and are not tiered."));
            return text;
        }
        if(kind==2)text.add(view==0?tr("口径：涅奥选项 · 被选次数 / 出现次数","Rate: Neow options · picked / offered"):view==1?tr("口径：涅奥标准率 · 同屏（所选与未选）对齐后的强度份额，合计 100%","Rate: Neow standardized · opponent-adjusted strength, sums to 100%"):tr("口径：胜率 = 选了该选项的局里赢了多少（选后相关，非该选项收益）；每局计一次，胜负未知不计。","Rate: wins among runs that picked the option (association, not causal benefit); once per run, unknown results excluded"));
        else text.add(view==0?tr("口径：Boss 遗物 · 被选次数 / 出现次数，按幕分组","Rate: boss relics · picked / offered, grouped by act"):view==1?tr("口径：Boss 遗物标准率 · 同屏（所选与未选）对齐后的强度份额，按幕分组；含跳过","Rate: boss relic standardized · opponent-adjusted strength by act; Skip included"):tr("口径：胜率 = 选了该遗物的局里赢了多少（选后相关）；每局计一次，按幕分组","Rate: wins among runs that picked the relic (association); once per run, by act"));
        text.add(tr("分档：S–R00 · 对本段显示的百分比做 Fisher-Jenks（最多 7 档），同值同档；样本不足单列。","Tiers: S–R00 · Fisher-Jenks over the section percentages (up to 7 classes); ties share a tier; low samples separate."));
        return text;
    }
    /** Readable source, date, slot, automation and version details that no longer fit in the image caption. */
    JsonObject remarks(){
        Stats.Filter f=screen.filter;JsonObject out=new JsonObject();
        out.addProperty("source",new String[]{tr("原版战绩与账本合并","native + Ledger merged"),tr("有原版战绩","has native history"),tr("有账本记录","has Ledger record")}[screen.selected[6]]+(f.slOnly?tr(" · 仅已记录 SL"," · Recorded SL only"):tr(" · 最终尝试路径"," · Final attempt")));
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd");
        out.addProperty("date_from",f.from==0?tr("不限","any"):date.format(new Date(f.from)));out.addProperty("date_to",f.to==0?tr("不限","any"):date.format(new Date(f.to-1)));
        out.addProperty("save_slot",f.slot);out.addProperty("standard_mode",true);out.addProperty("seeded",true);out.addProperty("min_offers",f.minSamples);
        Metric unknown=screen.metrics.get("reward_source:unknown");if(unknown!=null)out.addProperty("unknown_action_sources",screen.quantity(unknown.numerator)+"/"+screen.quantity(unknown.denominator));
        Metric excluded=screen.metrics.get("reward_automation_excluded");out.addProperty("known_automation_excluded",excluded==null?0:(long)excluded.numerator);out.addProperty("solver_used",solverUsed());
        List<String> versions=new ArrayList<>();versions.add("metrics-v1");if(kind==0||kind==2||kind==3)versions.add(Standardized.VERSION);versions.add(PickTiers.VERSION);if(kind!=0&&kind!=4)versions.add(PickTiers.SECTION_VERSION);
        out.add("versions",new Gson().toJsonTree(versions));out.addProperty("generated_at",new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(new Date()));
        if(kind==4){out.addProperty("export_scope",tr("图片与 CSV 只含当前选择的幕；幕选“全部”时每幕单独成图，CSV 一表一幕。","Images and CSV cover the selected act; with All, each act gets its own image and CSV table."));
            out.addProperty("image_source",tr("本地 monster-images 优先，其次本构建内置的测试图片，缺失留白，不下载。","Local monster-images first, then bundled test images, blank when absent; nothing is downloaded."));
            out.addProperty("bundled_images",bundledMonsterImages());}
        if(kind==2||kind==3)out.addProperty("interval_note",tr("界面不显示区间；CSV 的 95% 下限/上限给出 Wilson 区间作提示。","No interval in the image; the CSV's 95% bounds hold the Wilson interval as a hint."));
        return out;
    }
    /** Acts one export writes: every act of the kind when the act menu is "All", otherwise the selected one. Neow has no act. */
    int[] exportActs(){
        if(kind==2)return new int[]{0};
        if(act<=0)return kind==3?new int[]{1,2}:new int[]{1,2,3,4};
        return new int[]{kind==3?Math.max(1,Math.min(act,2)):Math.max(1,Math.min(act,4))};
    }
    String tableTitle(int a){return a<=0?kindNameCn():tr("第 ","Act ")+a+tr(" 幕 · "," · ")+kindNameCn();}
    /** CSV "口径" wording follows the exported kind instead of borrowing the card-pick labels. */
    String viewName(int view){
        if(view==2)return tr("胜率","Win rate");
        if(kind==4)return tr("场均战损","Mean HP loss");
        if(kind==1)return view==1?tr("标准化倍","Standardized multiple"):tr("行动次数","Action counts");
        if(kind==2||kind==3)return view==1?tr("标准率","Standardized"):tr("被选率","Pick rate");
        return view==1?tr("标准率","Standardized"):tr("一般率","Observed");
    }
    String kindNameCn(){switch(kind){case 1:return tr("火堆行动","Campfire actions");case 2:return tr("涅奥选项","Neow options");case 3:return tr("Boss 遗物","Boss relics");case 4:return tr("实际战损","Actual HP loss");default:return tr("卡牌抓率","Card picks");}}
    /** One CSV table per exported act, rows only from what that act's images contain. */
    void export(SpriteBatch sb)throws Exception{
        message="";
        List<Csv.Table> tables=new ArrayList<>();List<PickTiers.Entry> observedEntries=new ArrayList<>(),standardEntries=new ArrayList<>();
        List<Section> observedSections=new ArrayList<>(),standardSections=new ArrayList<>(),winSections=new ArrayList<>();List<Integer> keptActs=new ArrayList<>();
        Path root=StateMod.ROOT.getParent().resolve("exports");Files.createDirectories(root);Path directory=Files.createDirectory(root.resolve("charts-"+new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)));
        List<Path> written=new ArrayList<>();Matrix4 projection=new Matrix4(sb.getProjectionMatrix()),transform=new Matrix4(sb.getTransformMatrix());
        try{
            for(int a:exportActs()){
                if(kind==0){
                    List<PickTiers.Entry> observed=cards(screen.metrics,0,a),standard=cards(screen.metrics,1,a);
                    if(observed.isEmpty()&&standard.isEmpty())continue;
                    writeCards(sb,0,observed,a,directory,written,projection,transform);writeCards(sb,1,standard,a,directory,written,projection,transform);
                    Csv.Table table=new Csv.Table(tableTitle(a));
                    for(PickTiers.Entry e:observed)table.addRate(String.valueOf(a),"一般率",cardTier(e,0),tileName(e.card),e.picked,e.offered,e.samples,e.status,e.estimated);
                    for(PickTiers.Entry e:standard)table.addRate(String.valueOf(a),"标准率",cardTier(e,1),tileName(e.card),e.picked,e.offered,e.samples,e.status,e.estimated);
                    if(!table.empty()){tables.add(table);keptActs.add(a);}observedEntries.addAll(observed);standardEntries.addAll(standard);
                }else{
                    List<Section> observed=buildSections(screen.metrics,0,a),standard=kind==4?Collections.<Section>emptyList():buildSections(screen.metrics,1,a),win=kind==2||kind==3?buildSections(screen.metrics,2,a):Collections.<Section>emptyList();
                    if(observed.isEmpty()&&standard.isEmpty()&&win.isEmpty())continue;
                    writeSections(sb,0,observed,"",a,directory,written,projection,transform);writeSections(sb,1,standard,"standardized",a,directory,written,projection,transform);writeSections(sb,2,win,"win",a,directory,written,projection,transform);
                    Csv.Table table=new Csv.Table(tableTitle(a));
                    addRows(table,observed,a,viewName(0));addRows(table,standard,a,viewName(1));addRows(table,win,a,viewName(2));
                    if(!table.empty()){tables.add(table);keptActs.add(a);}observedSections.addAll(observed);standardSections.addAll(standard);winSections.addAll(win);
                }
            }
            if(tables.isEmpty())message=tr("本类当前筛选无图，已保存 CSV：","No chart rows for this selection; saved CSV: ");
            JsonObject data=new JsonObject();data.addProperty("metrics_version","metrics-v1");data.addProperty("tier_version",PickTiers.VERSION);data.addProperty("standardized_version",Standardized.VERSION);data.addProperty("csv_version",Csv.VERSION);
            if(kind!=0&&kind!=4)data.addProperty("section_tier_version",PickTiers.SECTION_VERSION);
            data.addProperty("kind",kindName(kind));data.addProperty("act",act);data.addProperty("all_acts",act<=0);data.add("acts",new Gson().toJsonTree(keptActs));data.addProperty("run_count",screen.selection.runs.size());
            List<String> views=new ArrayList<>();if(kind==0){if(!observedEntries.isEmpty())views.add("observed");if(!standardEntries.isEmpty())views.add("standardized");}
            else{if(!observedSections.isEmpty())views.add("observed");if(!standardSections.isEmpty())views.add("standardized");if(!winSections.isEmpty())views.add("win");}
            data.add("views",new Gson().toJsonTree(views));data.add("captions",new Gson().toJsonTree(captions(mode)));data.add("observed_captions",new Gson().toJsonTree(captions(0)));if(kind!=4)data.add("standardized_captions",new Gson().toJsonTree(captions(1)));
            if(kind==2||kind==3)data.add("win_captions",new Gson().toJsonTree(captions(2)));
            JsonObject csv=new JsonObject();csv.addProperty("file","metrics.csv");csv.addProperty("version",Csv.VERSION);
            csv.addProperty("scope",tr("只含本次导出的幕与项目；每个幕一张表，中文列名。","Only the acts and rows of this export; one table per act, Chinese column names."));
            data.add("csv",csv);
            data.add("filters",new Gson().toJsonTree(screen.filter));data.add("excluded",new Gson().toJsonTree(screen.selection.excluded));data.add("remarks",remarks());
            if(kind==0){data.add("entries",new Gson().toJsonTree(observedEntries));data.add("standardized_entries",new Gson().toJsonTree(standardEntries));data.add("skip_bowl_observed",new Gson().toJsonTree(actualItems(observedEntries)));}
            else{data.add("observed_sections",new Gson().toJsonTree(observedSections));data.add("standardized_sections",new Gson().toJsonTree(standardSections));if(kind==2||kind==3)data.add("win_sections",new Gson().toJsonTree(winSections));}
            Path csvFile=directory.resolve("metrics.csv");written.add(csvFile);Files.write(csvFile,("\uFEFF"+Csv.body(tables)).getBytes(StandardCharsets.UTF_8));
            Path metadata=directory.resolve("metadata.json");written.add(metadata);Files.write(metadata,new GsonBuilder().setPrettyPrinting().create().toJson(data).getBytes(StandardCharsets.UTF_8));lastExport=directory;
        }catch(Exception failure){for(Path file:written)try{Files.deleteIfExists(file);}catch(Exception cleanup){failure.addSuppressed(cleanup);}try{Files.deleteIfExists(directory);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    static String cardTier(PickTiers.Entry e,int view){
        if(view==1)return e.tier==ACTUAL?tr("实际占比","Observed"):PickTiers.standardName(e.tier);
        return e.tier==7?tr("未选","Zero"):e.tier<7?"T"+e.tier:tr("样本不足","Low sample");
    }
    void addRows(Csv.Table table,List<Section> sections,int act,String view){
        for(Section section:sections)for(Item item:section.items){
            String tier="";
            if(section.tiered&&item.tier!=UNTIERED){tier=item.tier==LOW?tr("样本不足","Low sample"):section.tierNames!=null&&item.tier>=0&&item.tier<section.tierNames.length?section.tierNames[item.tier]:PickTiers.standardName(item.tier);}
            table.add(act<=0?"":String.valueOf(act),view,tier,item.name,item.source);
        }
    }
    static List<Item> actualItems(List<PickTiers.Entry> entries){List<Item> out=new ArrayList<>();
        for(PickTiers.Entry e:entries)if(SKIP.equals(e.card)||BOWL.equals(e.card))out.add(new Item(e.card,BOWL.equals(e.card)?"Singing Bowl":null,tileName(e.card),String.format(Locale.ROOT,"%.1f%%  %s/%s",100*e.rate(),String.valueOf((long)e.picked),String.valueOf((long)e.offered)),7,false,100*e.rate(),"%"));return out;}
    void writeCards(SpriteBatch sb,int view,List<PickTiers.Entry> source,int a,Path directory,List<Path> written,Matrix4 projection,Matrix4 transform)throws Exception{
        if(source.isEmpty())return;
        final List<List<PickTiers.Entry>> rows=group(source);final List<String> head=captions(view);final int width=1600,height=(int)(90+head.size()*44+rows.size()*CARD_ROW+55);final int content=90+head.size()*44;final int mode=view;final List<PickTiers.Entry> all=source;
        Path file=directory.resolve("act-"+a+(view==1?"-standardized":"")+".png");written.add(file);
        writeTiled(sb,width,height,file,projection,transform,new Scene(){public void draw(SpriteBatch batch){
            text(batch,tr("尖塔账本 · ","Spire Ledger · ")+(mode==1?tr("标准率梯度图","Standardized rates"):tr("抓率梯度图","Pick rates"))+tr(" · 第 "," · Act ")+a+tr(" 幕","")+solverMark(),35,height-25,width-70,33,Settings.GOLD_COLOR);
            for(int i=0;i<head.size();i++)text(batch,head.get(i),35,height-80-i*44,width-70,22,Settings.CREAM_COLOR);
            for(int i=0;i<rows.size();i++)drawRow(batch,rows.get(i),35,(float)(height-content-i*CARD_ROW),width-70,CARD_ROW,mode,all);
        }});
    }
    void writeSections(SpriteBatch sb,int view,List<Section> sections,String suffix,int a,Path directory,List<Path> written,Matrix4 projection,Matrix4 transform)throws Exception{
        if(sections.isEmpty())return;
        final List<String> head=captions(view);final int width=1600;int height=90+head.size()*44+55;
        for(Section section:sections){height+=HEADER_ROW+8;for(ListLine line:sectionLines(section))height+=line.type==ListLine.RULE?RULE_ROW:LIST_ROW;}
        final int total=height;final int contentTop=90+head.size()*44;
        final String name=(kind==4?tr("实际战损","Actual HP loss"):kind==1?(view==1?tr("火堆标准化倍","Campfire preference"):tr("火堆行动","Campfire actions")):kind==2?(view==1?tr("涅奥标准率","Neow standardized"):view==2?tr("涅奥胜率","Neow win rate"):tr("涅奥选项","Neow options")):(view==1?tr("Boss 遗物标准率","Boss relic standardized"):view==2?tr("Boss 遗物胜率","Boss relic win rate"):tr("Boss 遗物","Boss relics")))+(a>0?tr(" · 第 "," · Act ")+a+tr(" 幕",""):"");
        Path file=directory.resolve(kindName(kind)+(a>0?"-act"+a:"")+(suffix.isEmpty()?"":"-"+suffix)+".png");written.add(file);
        writeTiled(sb,width,total,file,projection,transform,new Scene(){public void draw(SpriteBatch batch){
            text(batch,tr("尖塔账本 · ","Spire Ledger · ")+name+solverMark(),35,total-25,width-70,33,Settings.GOLD_COLOR);
            for(int i=0;i<head.size();i++)text(batch,head.get(i),35,total-80-i*44,width-70,22,Settings.CREAM_COLOR);
            float y=total-contentTop;
            for(Section section:sections){drawHeader(batch,section.title,section.note,35,y,width-70,1);y-=HEADER_ROW;
                for(ListLine line:sectionLines(section)){
                    if(line.type==ListLine.RULE){drawRule(batch,line.title,line.note,35,y,width-70,RULE_ROW,1);y-=RULE_ROW;}
                    else{drawItemRow(batch,line.items,35,y-6,width-70,LIST_ROW,1);y-=LIST_ROW;}
                }
                y-=8;}
        }});
    }
    interface Scene{void draw(SpriteBatch batch);}
    /** Band height the driver can attach: 4096 at most, lower when the driver's texture or renderbuffer limit is. */
    static int band(){java.nio.IntBuffer limit=BufferUtils.newIntBuffer(16);int band=4096;
        for(int name:new int[]{GL20.GL_MAX_TEXTURE_SIZE,GL20.GL_MAX_RENDERBUFFER_SIZE}){limit.clear();Gdx.gl.glGetIntegerv(name,limit);if(limit.get(0)>0)band=Math.min(band,limit.get(0));}
        return band;}
    /** Tall categories exceed one GPU framebuffer, so bands are rendered bottom-up into one full-size pixmap and written as a single PNG. */
    void writeTiled(SpriteBatch sb,int width,int height,Path file,Matrix4 projection,Matrix4 transform,Scene scene)throws Exception{
        int band=band();Pixmap full=new Pixmap(width,height,Pixmap.Format.RGBA8888);sb.end();
        try{
            for(int bottom=0;bottom<height;bottom+=band){
                int slice=Math.min(band,height-bottom);FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,width,slice,false);Pixmap part=null;
                try{
                    buffer.begin();Gdx.gl.glClearColor(.045f,.06f,.085f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    sb.setProjectionMatrix(new Matrix4().setToOrtho2D(0,bottom,width,slice));sb.setTransformMatrix(new Matrix4());sb.begin();
                    scene.draw(sb);sb.flush();part=ScreenUtils.getFrameBufferPixmap(0,0,width,slice);sb.end();
                    full.drawPixmap(part,0,0,width,slice,0,bottom,width,slice);
                }finally{if(sb.isDrawing())sb.end();if(part!=null)part.dispose();buffer.end();buffer.dispose();}
            }
            PixmapIO.PNG png=new PixmapIO.PNG();try{png.setFlipY(true);png.write(new FileHandle(file.toFile()),full);}finally{png.dispose();}
        }finally{full.dispose();sb.setProjectionMatrix(projection);sb.setTransformMatrix(transform);sb.begin();}
    }
}
