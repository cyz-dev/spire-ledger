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
import com.megacrit.cardcrawl.relics.AbstractRelic;
import spireledger.stats.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** The same immutable metric rows drive the native preview and its PNG export, for both observed and standardized rates. */
final class PickRateChart {
    static final int COLUMNS=6,LIST_COLUMNS=3;
    static final float CARD_ROW=85,LIST_ROW=96,HEADER_ROW=48;
    static final Color[] COLORS={Color.valueOf("e5c07b"),Color.valueOf("98c379"),Color.valueOf("61afef"),Color.valueOf("c678dd"),Color.valueOf("e06c75"),Color.valueOf("d19a66"),Color.valueOf("7f848e"),Color.valueOf("626978"),Color.valueOf("8c929e")};
    static final String SKIP="#skip",BOWL="#bowl";
    final StatsScreen screen;final List<List<PickTiers.Entry>> rows=new ArrayList<>();
    List<PickTiers.Entry> entries=Collections.emptyList();List<Section> sections=Collections.emptyList();final List<ListLine> listLines=new ArrayList<>();
    int act=1,offset,mode,kind;boolean exportRequested;String message="";Path lastExport;
    PickRateChart(StatsScreen screen){this.screen=screen;}
    static String tr(String cn,String en){return HistoryScreen.tr(cn,en);}
    /** One tile of a non-card export: a card portrait, a relic image, or a blank card placeholder. */
    static final class Item{
        final String card,relic,name,detail;final int tone;final boolean estimated;
        Item(String card,String relic,String name,String detail,int tone,boolean estimated){this.card=card;this.relic=relic;this.name=name;this.detail=detail;this.tone=tone;this.estimated=estimated;}
    }
    static final class Section{
        final String title,note;final List<Item> items;
        Section(String title,String note,List<Item> items){this.title=title;this.note=note;this.items=items;}
    }
    static final class ListLine{
        final String title,note;final List<Item> items;final boolean header;
        private ListLine(String title,String note,List<Item> items,boolean header){this.title=title;this.note=note;this.items=items;this.header=header;}
        static ListLine header(String title,String note){return new ListLine(title,note,Collections.<Item>emptyList(),true);}
        static ListLine items(List<Item> items){return new ListLine("","",items,false);}
    }
    void rebuild(Map<String,Metric> metrics){entries=cards(metrics,mode);rows.clear();rows.addAll(group(entries));sections=buildSections(metrics,mode);layoutSections();offset=0;message="";}
    boolean exportable(){if(kind==0)return !cards(screen.metrics,0).isEmpty()||shares()!=null;return !buildSections(screen.metrics,0).isEmpty()||!buildSections(screen.metrics,1).isEmpty();}
    /** One tier per row, at most COLUMNS cards per row; entries arrive tier-ascending. */
    static List<List<PickTiers.Entry>> group(List<PickTiers.Entry> source){List<List<PickTiers.Entry>> out=new ArrayList<>();for(PickTiers.Entry entry:source){if(out.isEmpty()||out.get(out.size()-1).size()==COLUMNS||out.get(out.size()-1).get(0).tier!=entry.tier)out.add(new ArrayList<>());out.get(out.size()-1).add(entry);}return out;}
    /** Skip and Bowl are real screen outcomes, so they join the observed rows with a blank placeholder; the fit never reports them. */
    List<PickTiers.Entry> cards(Map<String,Metric> metrics,int view){
        if(view==1){Map<String,Double> shares=shares();return shares==null?Collections.<PickTiers.Entry>emptyList():PickTiers.standardized(metrics,act,shares);}
        List<PickTiers.Entry> out=PickTiers.entries(metrics,act);Metric skip=metrics.get("skip:"+act),bowl=metrics.get("bowl:"+act);
        if(listed(skip))out.add(PickTiers.observed(SKIP,act,skip));if(listed(bowl))out.add(PickTiers.observed(BOWL,act,bowl));
        PickTiers.sort(out);return out;
    }
    static boolean listed(Metric metric){return metric!=null&&(metric.shown()||metric.status.equals("below_min_sample"));}
    Map<String,Double> shares(){return screen.reportedShares(act);}
    boolean hasStandard(){switch(kind){case 1:return !buildSections(screen.metrics,1).isEmpty();case 2:return !screen.neowShares.isEmpty();case 3:return !screen.bossShares.isEmpty();default:return shares()!=null;}}
    int visible(float top){return Math.max(1,(int)((top-Settings.HEIGHT*.23f)/(76*Settings.scale)));}
    int visibleList(float top){int count=0;float y=top;for(int i=0;i<listLines.size();i++){float height=(listLines.get(i).header?HEADER_ROW:LIST_ROW)*Settings.scale;if(y-height<Settings.HEIGHT*.19f)break;y-=height;count++;}return Math.max(1,count);}
    void scroll(int amount){
        if(kind==0){offset=Math.max(0,Math.min(Math.max(0,rows.size()-visible(screen.bodyTop()-35*Settings.scale)),offset+amount));return;}
        offset=Math.max(0,Math.min(Math.max(0,listLines.size()-visibleList(screen.bodyTop()-35*Settings.scale)),offset+amount));
    }
    String tierName(int tier,int view){if(view==1)return PickTiers.standardName(tier);return tier<7?"T"+tier:tier==7?tr("未选","Zero"):tr("样本不足","Low sample");}
    String range(int tier,int view,List<PickTiers.Entry> source){
        if(view==1){double low=Double.MAX_VALUE,high=-1;for(PickTiers.Entry entry:source)if(entry.tier==tier){low=Math.min(low,entry.standard);high=Math.max(high,entry.standard);}return high<0?"":Double.compare(low,high)==0?share(low):share(low)+"–"+share(high);}
        return tier==0?"90–100%":tier<6?PickTiers.LOWER[tier]+"–<"+PickTiers.LOWER[tier-1]+"%":tier==6?">0–<15%":tier==7?"0%":"";
    }
    static String share(double value){return (value>=10?String.format(Locale.ROOT,"%.1f",value):value>=1?String.format(Locale.ROOT,"%.2f",value):String.format(Locale.ROOT,"%.3f",value))+"%";}
    static String tileName(String id){if(SKIP.equals(id))return tr("跳过","Skip");if(BOWL.equals(id))return tr("歌唱碗","Singing Bowl");return HistoryScreen.cardName(id);}
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
    void image(SpriteBatch sb,Item item,float x,float y,float size){if(item.card!=null&&!item.card.isEmpty())portrait(sb,item.card,x,y,size);else if(item.relic!=null&&!item.relic.isEmpty())relic(sb,item.relic,x,y,size);else blank(sb,x,y,size,size);}
    void drawRow(SpriteBatch sb,List<PickTiers.Entry> row,float x,float top,float width,float height,int view,List<PickTiers.Entry> source){
        int tier=row.get(0).tier;float tag=width*.11f,cell=(width-tag)/COLUMNS,pad=height*.10f;
        box(sb,x,top-height,width,height-3,new Color(.09f,.12f,.16f,1));box(sb,x,top-height,tag-5,height-3,COLORS[Math.min(tier,COLORS.length-1)]);
        text(sb,tierName(tier,view),x+pad,top-pad,tag-pad*2,height*.30f,Color.BLACK);text(sb,range(tier,view,source),x+pad,top-height*.56f,tag-pad*2,height*.20f,Color.BLACK);
        for(int i=0;i<row.size();i++){
            PickTiers.Entry e=row.get(i);float left=x+tag+i*cell,portrait=height*.45f;
            portrait(sb,e.card,left+pad,top-pad-portrait,portrait);
            text(sb,tileName(e.card),left+portrait+pad*2,top-pad,cell-portrait-pad*3,height*.28f,Settings.CREAM_COLOR);
            String rate=view==1?share(e.standard)+tr("  /  出现 ","  /  offered ")+screen.quantity(e.offered):e.tier==8?tr("出现 ","Offered ")+screen.quantity(e.offered):String.format(Locale.ROOT,"%.1f%%  %s/%s",100*e.rate(),screen.quantity(e.picked),screen.quantity(e.offered));
            if(e.estimated>0)rate+=tr(" 含估算"," est.");text(sb,rate,left+pad,top-height*.65f,cell-pad*2,height*.22f,HistoryScreen.MUTED);
        }
    }
    String heading(){if(kind==1)return mode==1?tr("火堆标准化倍：占比 ÷ 随机基线 · 每张牌单独一行","Campfire preference: share ÷ random baseline · one row per card"):tr("火堆行动次数：每次火堆实际做了什么，锻造按牌逐张计数","Campfire action counts: what each rest site did, smiths by upgraded card");
        if(kind==2)return mode==1?tr("涅奥标准率：同屏对齐后的强度份额 · 合计 100%","Neow standardized: opponent-adjusted shares · sums to 100%"):tr("涅奥选项：被选次数 / 出现次数","Neow options: picked / offered");
        if(kind==3)return mode==1?tr("Boss 遗物标准率：同屏对齐后的强度份额 · 跳过不计入","Boss relic standardized: opponent-adjusted shares · Skip not reported"):tr("Boss 遗物：被选次数 / 出现次数","Boss relics: picked / offered");
        return mode==1?tr("标准率：同屏对手对齐后的强度份额 · 合计 100%","Standardized rate: strength share after opponent adjustment · sums to 100%"):tr("实际抓率：被选次数 / 出现次数 · 只描述你的选择","Observed pick rate: picked / offered · Your choices");}
    String unavailable(){if(kind==1)return tr("标准化倍不可用：当前筛选没有可用的火堆偏好记录。","Campfire preference unavailable: no usable rest-site choices in this selection.");if(kind==2)return tr("涅奥标准率不可用：至少需要两屏有效选择。","Neow standardized unavailable: at least two usable screens are needed.");if(kind==3)return tr("Boss 遗物标准率不可用：至少需要两屏有效选择。","Boss relic standardized unavailable: at least two usable screens are needed.");return tr("标准率不可用：本幕至少需要 2 屏有效选牌，且筛选后仍保留 2 张不同卡。","Standardized rate unavailable: this act needs at least two usable reward screens and two distinct cards after filtering.");}
    void render(SpriteBatch sb){
        float top=screen.bodyTop();
        text(sb,heading(),Settings.WIDTH*.06f,top,Settings.WIDTH*.87f,23*Settings.scale,Settings.GOLD_COLOR);
        top-=35*Settings.scale;
        if(kind==0)renderCards(sb,top);else renderSections(sb,top);
        String footer=message.isEmpty()?tr("滚轮翻页 · ","Scroll · ")+(kind==0?(rows.isEmpty()?0:offset+1)+"–"+Math.min(rows.size(),offset+visible(top))+" / "+rows.size()+tr(" 行"," rows"):(listLines.isEmpty()?0:offset+1)+"–"+Math.min(listLines.size(),offset+visibleList(top))+" / "+listLines.size()+tr(" 行"," rows")):message;
        text(sb,footer,Settings.WIDTH*.22f,Settings.HEIGHT*.19f,Settings.WIDTH*.71f,21*Settings.scale,HistoryScreen.MUTED);
        if(exportRequested){exportRequested=false;try{export(sb);message=tr("图片已保存：","Saved: ")+lastExport;}catch(Exception e){message=tr("导出失败：","Export failed: ")+e.getClass().getSimpleName();DiagnosticReport.error("pick_rate_export",e);}}
    }
    void renderCards(SpriteBatch sb,float top){
        if(mode==1&&!hasStandard()){text(sb,unavailable(),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        if(rows.isEmpty()){text(sb,tr("当前筛选没有选牌记录。试试其他职业或放宽筛选。","No reward choices in this selection. Adjust the filters."),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        int count=visible(top);
        for(int i=0;i<count&&offset+i<rows.size();i++)drawRow(sb,rows.get(offset+i),Settings.WIDTH*.06f,top-i*76*Settings.scale,Settings.WIDTH*.88f,76*Settings.scale,mode,entries);
    }
    void renderSections(SpriteBatch sb,float top){
        if(sections.isEmpty()){text(sb,mode==1?unavailable():tr("当前筛选没有该类记录。试试其他职业或放宽筛选。","No rows of this kind in this selection. Adjust the filters."),Settings.WIDTH*.06f,top,Settings.WIDTH*.86f,23*Settings.scale,HistoryScreen.MUTED);return;}
        int count=visibleList(top);float y=top;
        for(int i=offset;i<listLines.size()&&i<offset+count;i++){
            ListLine line=listLines.get(i);
            if(line.header){drawHeader(sb,line.title,line.note,Settings.WIDTH*.06f,y,Settings.WIDTH*.88f,Settings.scale);y-=HEADER_ROW*Settings.scale;}
            else{drawItemRow(sb,line.items,Settings.WIDTH*.06f,y-6*Settings.scale,Settings.WIDTH*.88f,LIST_ROW*Settings.scale,Settings.scale);y-=LIST_ROW*Settings.scale;}
        }
    }
    void drawHeader(SpriteBatch sb,String title,String note,float x,float top,float width,float scale){
        box(sb,x,top-HEADER_ROW*scale+6*scale,width,HEADER_ROW*scale-8*scale,new Color(.11f,.14f,.19f,1));
        text(sb,title,x+10*scale,top-5*scale,width*.36f,22*scale,Settings.GOLD_COLOR);
        if(note!=null&&!note.isEmpty())text(sb,note,x+width*.34f,top-7*scale,width*.64f,18*scale,HistoryScreen.MUTED);
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
    void layoutSections(){listLines.clear();for(Section section:sections){listLines.add(ListLine.header(section.title,section.note));for(int i=0;i<section.items.size();i+=LIST_COLUMNS)listLines.add(ListLine.items(new ArrayList<>(section.items.subList(i,Math.min(section.items.size(),i+LIST_COLUMNS)))));}}
    List<Section> buildSections(Map<String,Metric> metrics,int view){switch(kind){case 1:return campfire(metrics,view);case 2:return neow(metrics,view);case 3:return boss(metrics,view);default:return Collections.emptyList();}}
    List<Section> campfire(Map<String,Metric> metrics,int view){
        List<Section> out=new ArrayList<>();
        for(int a:new int[]{1,2,3,4,0}){
            Metric visits=metrics.get("campfire_visits:"+a);if(visits==null||visits.numerator<=0)continue;
            String title=(a==0?tr("四幕合计","All acts"):tr("第 ","Act ")+a+tr(" 幕",""))+tr(" · 火堆 "," · campfires ")+screen.quantity(visits.numerator)+tr(" 次"," visits");
            List<Item> items=new ArrayList<>();String note;
            if(view==0){
                Metric sleep=metrics.get("campfire_sleep:"+a);if(sleep!=null&&sleep.denominator>0)items.add(new Item("",null,tr("睡觉","Sleep"),pct(sleep),-1,false));
                for(String key:screen.campfireRows("campfire_count:"+a+":")){Metric m=metrics.get(key);if(m==null||m.numerator<=0)continue;String id=key.substring(key.lastIndexOf(':')+1);items.add(campfireItem(id,screen.quantity(m.numerator)+tr(" 次",""),-1));}
                note=tr("行动次数 · 锻造按被升级的牌逐张计数","Action counts · smiths by upgraded card");
            }else{
                Metric sleep=metrics.get("campfire_sleep:"+a);if(sleep!=null&&sleep.denominator>0&&listed(sleep))items.add(new Item("",null,tr("睡觉","Sleep"),pref(sleep),-1,false));
                List<String> keys=new ArrayList<>();for(String key:metrics.keySet())if(key.startsWith("campfire_pref:"+a+":"))keys.add(key);
                keys.sort((x,y)->{Metric mx=metrics.get(x),my=metrics.get(y);int by=Double.compare(my.value(),mx.value());if(by!=0)return by;by=Integer.compare(my.samples,mx.samples);return by!=0?by:x.compareTo(y);});
                for(String key:keys){Metric m=metrics.get(key);if(!listed(m))continue;String id=key.substring(key.lastIndexOf(':')+1);items.add(campfireItem(id,pref(m),-1));}
                Metric smiths=metrics.get("campfire_smiths:"+a),pool=metrics.get("campfire_pool:"+a),rebuild=metrics.get("campfire_rebuild:"+a);
                StringBuilder builder=new StringBuilder();if(smiths!=null&&visits.numerator>0)builder.append(tr("锻造 ","smith ")).append(screen.percent(smiths.numerator/visits.numerator));
                if(pool!=null&&pool.value()>0)builder.append(tr(" · 平均候选 "," · mean pool ")).append(String.format(Locale.ROOT,"%.1f",pool.value())).append(tr(" · 随机基线 "," · baseline ")).append(String.format(Locale.ROOT,"%.1f%%",100.0/pool.value()));
                if(rebuild!=null&&rebuild.denominator>0)builder.append(tr(" · 重建 "," · rebuilt ")).append(screen.quantity(rebuild.numerator)).append("/").append(screen.quantity(rebuild.denominator));
                note=builder.length()==0?tr("占比 ÷ 随机基线","Share ÷ random baseline"):builder.toString();
            }
            out.add(new Section(title,note,items));
        }
        return out;
    }
    Item campfireItem(String id,String detail,int tone){boolean card=CardLibrary.getCard(id)!=null;return new Item(card?id:"",null,screen.campfireName(id,false),detail,tone,false);}
    List<Section> neow(Map<String,Metric> metrics,int view){
        List<Item> items=new ArrayList<>();String note;
        if(view==0){
            List<String> keys=new ArrayList<>();for(String key:metrics.keySet())if(key.startsWith("neow:"))keys.add(key);
            keys.sort((x,y)->{Metric mx=metrics.get(x),my=metrics.get(y);int by=Double.compare(my.value(),mx.value());if(by!=0)return by;by=Integer.compare(my.samples,mx.samples);return by!=0?by:x.compareTo(y);});
            for(String key:keys){Metric m=metrics.get(key);if(!listed(m))continue;items.add(new Item("",null,screen.neow(key.substring(5)),pct(m),-1,m.estimated>0));}
            note=tr("被选 / 出现 · 同屏每个选项都计入出现","Picked / offered · every option on the screen is an offer");
        }else{
            List<String> ids=new ArrayList<>(screen.neowShares.keySet());
            ids.sort((x,y)->{int by=Double.compare(screen.neowShares.get(y).doubleValue(),screen.neowShares.get(x).doubleValue());return by!=0?by:x.compareTo(y);});
            for(String id:ids){Metric m=metrics.get("neow:"+id);items.add(new Item("",null,screen.neow(id),share(screen.neowShares.get(id).doubleValue())+tr("  /  出现 ","  /  offered ")+screen.quantity(m==null?0:m.denominator),-1,false));}
            note=tr("强度份额 · 合计 100% · 缺少未选记录的局不参与拟合","Strength shares · sums to 100% · runs without skipped options are not fitted");
        }
        return items.isEmpty()?Collections.<Section>emptyList():Collections.singletonList(new Section(tr("涅奥选项","Neow options"),note,items));
    }
    List<Section> boss(Map<String,Metric> metrics,int view){
        List<Section> out=new ArrayList<>();
        for(int a=1;a<=3;a++){
            if(metrics.get("boss:"+a+":SKIP")==null)continue;
            List<Item> items=new ArrayList<>();
            if(view==0){
                List<String> keys=new ArrayList<>();for(String key:metrics.keySet())if(key.startsWith("boss:"+a+":"))keys.add(key);
                keys.sort((x,y)->{Metric mx=metrics.get(x),my=metrics.get(y);int by=Double.compare(my.value(),mx.value());if(by!=0)return by;by=Integer.compare(my.samples,mx.samples);return by!=0?by:x.compareTo(y);});
                for(String key:keys){Metric m=metrics.get(key);if(!listed(m))continue;String id=key.substring(("boss:"+a+":").length());items.add(id.equals(Standardized.SKIP)?new Item("",null,tr("跳过","Skip"),pct(m),-1,false):new Item("",id,HistoryScreen.relicName(id),pct(m),-1,m.estimated>0));}
            }else{
                Map<String,Double> shares=screen.bossShares.get(Integer.valueOf(a));if(shares==null||shares.isEmpty())continue;
                List<String> ids=new ArrayList<>(shares.keySet());
                ids.sort((x,y)->{int by=Double.compare(shares.get(y).doubleValue(),shares.get(x).doubleValue());return by!=0?by:x.compareTo(y);});
                for(String id:ids){Metric m=metrics.get("boss:"+a+":"+id);items.add(new Item("",id,HistoryScreen.relicName(id),share(shares.get(id).doubleValue())+tr("  /  出现 ","  /  offered ")+screen.quantity(m==null?0:m.denominator),-1,false));}
            }
            if(!items.isEmpty())out.add(new Section(tr("第 ","Act ")+a+tr(" 幕 · Boss 遗物"," · boss relics"),view==0?tr("被选 / 出现 · 跳过是同屏选项","Picked / offered · Skip is a screen option"):tr("强度份额 · 合计 100% · 跳过不计入","Strength shares · sums to 100% · Skip not reported"),items));
        }
        return out;
    }
    static String pct(Metric metric){return String.format(Locale.ROOT,"%.1f%%  %s/%s",100*metric.value(),String.valueOf((long)metric.numerator),String.valueOf((long)metric.denominator));}
    static String pref(Metric metric){return String.format(Locale.ROOT,"%.1f%% %.1f× (%s/%s)",metric.value()*100,metric.baseline>0?metric.value()*100/metric.baseline:0,String.valueOf((long)metric.numerator),String.valueOf((long)metric.denominator));}
    List<Item> actual(){List<Item> out=new ArrayList<>();Metric skip=screen.metrics.get("skip:"+act),bowl=screen.metrics.get("bowl:"+act);
        if(listed(skip))out.add(new Item(SKIP,null,tr("跳过","Skip"),pct(skip),7,false));if(listed(bowl))out.add(new Item(BOWL,"Singing Bowl",tr("歌唱碗","Singing Bowl"),pct(bowl),7,false));return out;}
    List<String> captions(int view){
        Stats.Filter f=screen.filter;List<String> text=new ArrayList<>();
        text.add(tr("职业：","Class: ")+(f.character.isEmpty()?tr("全部","all"):HistoryScreen.className(f.character))+tr(" · 进阶："," · Ascension: ")+(f.ascension<0?tr("全部","all"):"A"+f.ascension)+tr(" · 存档位 "," · Save slot ")+f.slot+tr(" · 最少出现 "," · Min offers ")+f.minSamples+tr(" 次",""));
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd");text.add(tr("日期：","Date: ")+(f.from==0?tr("不限","any"):date.format(new Date(f.from)))+" – "+(f.to==0?tr("不限","any"):date.format(new Date(f.to-1)))+tr(" · 标准模式 · 种子局包含"," · Standard mode · Seeded included"));
        text.add(tr("结果：","Results: ")+new String[]{tr("全部","all"),tr("仅胜","wins"),tr("仅败","losses")}[screen.selected[8]]+tr(" · 胜利口径："," · Wins: ")+(f.heart?tr("杀心","Heart"):tr("通过第三幕","act 3"))+(f.includeAbandon?tr(" · 包含放弃"," · Abandoned included"):tr(" · 不含放弃"," · Abandoned excluded")));
        text.add(tr("来源：","Sources: ")+new String[]{tr("原版战绩与账本合并","native + Ledger merged"),tr("有原版战绩","has native history"),tr("有账本记录","has Ledger record")}[screen.selected[6]]+(f.slOnly?tr(" · 仅已记录 SL"," · Recorded SL only"):tr(" · 最终尝试路径"," · Final attempt")));
        Metric unknown=screen.metrics.get("reward_source:unknown");text.add(tr("包含操作来源未知的选择","Unknown action sources included")+(unknown==null?"":" ("+screen.quantity(unknown.numerator)+"/"+screen.quantity(unknown.denominator)+")")+tr(" · 排除已知自动化"," · Known automation excluded"));
        text.add(tr("选中 ","Selected ")+screen.selection.runs.size()+tr(" 局"," runs"));
        if(kind==0){
            text.add(tr("口径：","Rate: ")+(view==1?tr("标准率 · Plackett-Luce 强度份额（a=1, b=0.01；合计 100%，不含置信区间）","standardized · Plackett-Luce strength share (a=1, b=0.01; sums to 100%, no confidence interval)"):tr("一般率 · 被选次数 / 出现次数","observed · picked / offered")));
            text.add("metrics-v1 · "+PickTiers.VERSION+(view==1?" · "+Standardized.VERSION:"")+" · "+new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(new Date()));return text;
        }
        if(kind==1)text.add(view==1?tr("口径：标准化倍 = 占比 ÷ 随机基线；候选池 = 每张可升级牌 + 可用抽烟/举重/挖掘/回忆，不含睡觉，锻造不作为一个选项","Multiple = share ÷ random baseline; pool = every ungraded card plus available Toke/Lift/Dig/Recall, never sleep and never smith as one slot"):tr("口径：行动次数 · 锻造按被升级的牌逐张计数","Rate: action counts · each smith counted under the card it upgraded"));
        else if(kind==2)text.add(view==1?tr("口径：涅奥标准率 · 同屏（所选与未选）对齐后的强度份额，合计 100%","Rate: Neow standardized · opponent-adjusted strength over the whole screen, sums to 100%"):tr("口径：涅奥选项 · 被选次数 / 出现次数","Rate: Neow options · picked / offered"));
        else text.add(view==1?tr("口径：Boss 遗物标准率 · 同屏（所选与未选）对齐后的强度份额；跳过不报告","Rate: boss relic standardized · opponent-adjusted strength over the whole screen; Skip not reported"):tr("口径：Boss 遗物 · 被选次数 / 出现次数","Rate: boss relics · picked / offered"));
        text.add((kind==1?"campfire-v1":PickTiers.VERSION)+(view==1&&kind!=1?" · "+Standardized.VERSION:"")+" · "+new SimpleDateFormat("yyyy-MM-dd HH:mm z").format(new Date()));return text;
    }
    static String kindName(int kind){switch(kind){case 1:return "campfire";case 2:return "neow";case 3:return "boss";default:return "cards";}}
    /** Writes the selected kind, observed and standardized when each exists, so the long image carries the whole category. */
    void export(SpriteBatch sb)throws Exception{
        List<Section> observedSections=kind==0?Collections.<Section>emptyList():buildSections(screen.metrics,0),standardSections=kind==0?Collections.<Section>emptyList():buildSections(screen.metrics,1);
        List<PickTiers.Entry> observed=kind==0?cards(screen.metrics,0):Collections.<PickTiers.Entry>emptyList(),standard=kind==0?cards(screen.metrics,1):Collections.<PickTiers.Entry>emptyList();
        if(observed.isEmpty()&&standard.isEmpty()&&observedSections.isEmpty()&&standardSections.isEmpty()){message=tr("没有可导出的记录。","Nothing to export.");return;}
        Path root=StateMod.ROOT.getParent().resolve("exports");Files.createDirectories(root);Path directory=Files.createDirectory(root.resolve("charts-"+new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)));
        List<Path> written=new ArrayList<>();Matrix4 projection=new Matrix4(sb.getProjectionMatrix()),transform=new Matrix4(sb.getTransformMatrix());
        try{
            if(kind==0){writeCards(sb,0,observed,directory,written,projection,transform);writeCards(sb,1,standard,directory,written,projection,transform);}
            else{writeSections(sb,0,observedSections,"",directory,written,projection,transform);writeSections(sb,1,standardSections,"standardized",directory,written,projection,transform);}
            JsonObject data=new JsonObject();data.addProperty("metrics_version","metrics-v1");data.addProperty("tier_version",PickTiers.VERSION);data.addProperty("standardized_version",Standardized.VERSION);data.addProperty("kind",kindName(kind));data.addProperty("act",act);data.addProperty("run_count",screen.selection.runs.size());
            List<String> views=new ArrayList<>();if(kind==0){if(!observed.isEmpty())views.add("observed");if(!standard.isEmpty())views.add("standardized");}else{if(!observedSections.isEmpty())views.add("observed");if(!standardSections.isEmpty())views.add("standardized");}
            data.add("views",new Gson().toJsonTree(views));data.add("captions",new Gson().toJsonTree(captions(mode)));data.add("observed_captions",new Gson().toJsonTree(captions(0)));data.add("standardized_captions",new Gson().toJsonTree(captions(1)));
            data.add("filters",new Gson().toJsonTree(screen.filter));data.add("excluded",new Gson().toJsonTree(screen.selection.excluded));
            if(kind==0){data.add("entries",new Gson().toJsonTree(observed));data.add("standardized_entries",new Gson().toJsonTree(standard));data.add("skip_bowl_observed",new Gson().toJsonTree(kind==0?actual():Collections.<Item>emptyList()));}
            else{data.add("observed_sections",new Gson().toJsonTree(observedSections));data.add("standardized_sections",new Gson().toJsonTree(standardSections));}
            Path metadata=directory.resolve("metadata.json");written.add(metadata);Files.write(metadata,new GsonBuilder().setPrettyPrinting().create().toJson(data).getBytes(StandardCharsets.UTF_8));lastExport=directory;
        }catch(Exception failure){for(Path file:written)try{Files.deleteIfExists(file);}catch(Exception cleanup){failure.addSuppressed(cleanup);}try{Files.deleteIfExists(directory);}catch(Exception cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    void writeCards(SpriteBatch sb,int view,List<PickTiers.Entry> source,Path directory,List<Path> written,Matrix4 projection,Matrix4 transform)throws Exception{
        if(source.isEmpty())return;
        final List<List<PickTiers.Entry>> rows=group(source);final List<Item> actual=view==1?actual():Collections.<Item>emptyList();final List<String> head=captions(view);final int width=1600,height=(int)(90+head.size()*44+rows.size()*CARD_ROW+(actual.isEmpty()?0:HEADER_ROW+LIST_ROW+12)+55);final int content=90+head.size()*44;final int mode=view;final List<PickTiers.Entry> all=source;
        Path file=directory.resolve("act-"+act+(view==1?"-standardized":"")+".png");written.add(file);
        writeTiled(sb,width,height,file,projection,transform,new Scene(){public void draw(SpriteBatch batch){
            text(batch,tr("尖塔账本 · ","Spire Ledger · ")+(mode==1?tr("标准率梯度图","Standardized rates"):tr("抓率梯度图","Pick rates"))+tr(" · 第 "," · Act ")+act+tr(" 幕",""),35,height-25,width-70,33,Settings.GOLD_COLOR);
            for(int i=0;i<head.size();i++)text(batch,head.get(i),35,height-80-i*44,width-70,22,Settings.CREAM_COLOR);
            for(int i=0;i<rows.size();i++)drawRow(batch,rows.get(i),35,(float)(height-content-i*CARD_ROW),width-70,CARD_ROW,mode,all);
            if(!actual.isEmpty()){float y=(float)(height-content-rows.size()*CARD_ROW-4);drawHeader(batch,tr("跳过与歌唱碗","Skip and Singing Bowl"),tr("实际占比，不参与标准率","Observed rates, not part of the standardized fit"),35,y,width-70,1);drawItemRow(batch,actual,35,y-HEADER_ROW-6,width-70,LIST_ROW,1);}
        }});
    }
    void writeSections(SpriteBatch sb,int view,List<Section> sections,String suffix,Path directory,List<Path> written,Matrix4 projection,Matrix4 transform)throws Exception{
        if(sections.isEmpty())return;
        final List<String> head=captions(view);final int width=1600;int height=90+head.size()*44+55;for(Section section:sections)height+=HEADER_ROW+((section.items.size()+LIST_COLUMNS-1)/LIST_COLUMNS)*LIST_ROW+8;final int total=height;final int contentTop=90+head.size()*44;final int mode=view;final List<Section> content=sections;
        String name=kind==1?(view==1?tr("火堆标准化倍","Campfire preference"):tr("火堆行动","Campfire actions")):kind==2?(view==1?tr("涅奥标准率","Neow standardized"):tr("涅奥选项","Neow options")):(view==1?tr("Boss 遗物标准率","Boss relic standardized"):tr("Boss 遗物","Boss relics"));
        Path file=directory.resolve(kindName(kind)+(suffix.isEmpty()?"":"-"+suffix)+".png");written.add(file);
        writeTiled(sb,width,total,file,projection,transform,new Scene(){public void draw(SpriteBatch batch){
            text(batch,tr("尖塔账本 · ","Spire Ledger · ")+name,35,total-25,width-70,33,Settings.GOLD_COLOR);
            for(int i=0;i<head.size();i++)text(batch,head.get(i),35,total-80-i*44,width-70,22,Settings.CREAM_COLOR);
            float y=total-contentTop;
            for(Section section:content){drawHeader(batch,section.title,section.note,35,y,width-70,1);y-=HEADER_ROW;
                for(int i=0;i<section.items.size();i+=LIST_COLUMNS){drawItemRow(batch,new ArrayList<>(section.items.subList(i,Math.min(section.items.size(),i+LIST_COLUMNS))),35,y-6,width-70,LIST_ROW,1);y-=LIST_ROW;}
                y-=8;}
        }});
    }
    interface Scene{void draw(SpriteBatch batch);}
    /** Tall categories exceed one GPU framebuffer, so bands are rendered bottom-up into one full-size pixmap and written as a single PNG. */
    void writeTiled(SpriteBatch sb,int width,int height,Path file,Matrix4 projection,Matrix4 transform,Scene scene)throws Exception{
        int band=4096;Pixmap full=new Pixmap(width,height,Pixmap.Format.RGBA8888);sb.end();
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
