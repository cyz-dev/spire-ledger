package spireledger;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.helpers.*;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.screens.options.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Future;
import spireledger.stats.*;
/** Native personal statistics surface; no replacement of the game's or another mod's StatsScreen. Every number comes from spireledger.stats. */
final class StatsScreen implements DropdownMenuListener {
 final PickRateChart chart=new PickRateChart(this);Map<String,Metric> metrics=new LinkedHashMap<>();Map<Integer,Standardized.Act> standard=new TreeMap<>();Map<Integer,Map<String,Double>> reportShares=new TreeMap<>();Standardized.Act neowStandard;Map<Integer,Standardized.Act> bossStandard=new TreeMap<>();Map<String,Double> neowShares=new LinkedHashMap<>();Map<Integer,Map<String,Double>> bossShares=new TreeMap<>();DropdownMenu chartKind,chartAct,chartBossAct,rateMenu,choiceRateMenu,chartCampfireRate,campfireMenu;int rateMode,choiceMode,campfireMode;final Hitbox exportButton=new Hitbox(220*Settings.scale,36*Settings.scale);
 final HistoryScreen host;Stats.Index index=new Stats.Index();Stats.Selection selection=new Stats.Selection();Future<Stats.Index> task;boolean requested,advanced;int theme,offset;
 final Hitbox[] themes=new Hitbox[4];final Hitbox advancedButton=new Hitbox(180*Settings.scale,36*Settings.scale);
 /** actFilter narrows every page whose rows are grouped by act; hpSplit is the actual-HP-loss view (all, healed, not healed). */
 DropdownMenu actMenu,bossActMenu,hpSplitMenu;int actFilter,hpSplit;
 final DropdownMenu[] filters=new DropdownMenu[9];final int[] selected=new int[9];DropdownMenu category,fromMonth,toMonth;int categoryIndex,fromIndex,toIndex;List<String> months=new ArrayList<>();
 final Hitbox clearButton=new Hitbox(100*Settings.scale,36*Settings.scale),refreshButton=new Hitbox(100*Settings.scale,36*Settings.scale);List<String> display=new ArrayList<>();List<Line> displayRows=new ArrayList<>();final Map<Integer,Cell> metricRows=new HashMap<>();String status="";Stats.Filter filter=new Stats.Filter();
 static final class Cell{final String label,value;final HistoryScreen.Tone tone,valueTone;Cell(String label,String value,HistoryScreen.Tone tone,HistoryScreen.Tone valueTone){this.label=label;this.value=value;this.tone=tone;this.valueTone=valueTone;}}
 /** One rendered line: either full-width text, or two label/value cells side by side in reading order. */
 static final class Line{final String text,lLabel,lValue,rLabel,rValue;final HistoryScreen.Tone tone,lTone,lValueTone,rTone,rValueTone;final boolean two;
  Line(String text,HistoryScreen.Tone tone){this.text=text;this.tone=tone;lLabel=lValue=rLabel=rValue="";lTone=rTone=lValueTone=rValueTone=HistoryScreen.Tone.NORMAL;two=false;}
  Line(String lLabel,String lValue,HistoryScreen.Tone lTone,HistoryScreen.Tone lValueTone,String rLabel,String rValue,HistoryScreen.Tone rTone,HistoryScreen.Tone rValueTone){this.text="";this.tone=lTone;this.lLabel=lLabel;this.lValue=lValue;this.rLabel=rLabel;this.rValue=rValue;this.lTone=lTone;this.lValueTone=lValueTone;this.rTone=rTone;this.rValueTone=rValueTone;two=true;}}
 String tr(String a,String b){return HistoryScreen.tr(a,b);}DropdownMenu menu(String[] labels){return new DropdownMenu(this,labels,FontHelper.cardDescFont_N,Settings.CREAM_COLOR,8);}
 StatsScreen(HistoryScreen host){this.host=host;clearButton.move(Settings.WIDTH*.76f,Settings.HEIGHT*.825f);refreshButton.move(Settings.WIDTH*.90f,Settings.HEIGHT*.825f);for(int i=0;i<themes.length;i++){themes[i]=new Hitbox(Settings.WIDTH*.19f,36*Settings.scale);themes[i].move(Settings.WIDTH*(.15f+i*.23f),Settings.HEIGHT*.885f);}advancedButton.move(Settings.WIDTH*.83f,Settings.HEIGHT*.745f);
  filters[0]=menu(new String[]{tr("职业：全部","Class: all"),tr("战士","Ironclad"),tr("猎人","Silent"),tr("机器人","Defect"),tr("观者","Watcher")});String[] asc=new String[22];asc[0]=tr("进阶：全部","Ascension: all");for(int i=1;i<asc.length;i++)asc[i]="A"+(i-1);filters[1]=menu(asc);
  filters[2]=menu(new String[]{tr("时间：全部","Date: all"),tr("起止月份","Month range")});filters[3]=menu(new String[]{tr("口径：杀心","Wins: Heart"),tr("口径：通过第三幕","Wins: act 3 cleared")});filters[4]=menu(new String[]{tr("不含放弃","Exclude abandoned"),tr("包含放弃","Include abandoned")});filters[5]=menu(new String[]{tr("最少样本：1","Min samples: 1"),tr("最少样本：5","Min samples: 5"),tr("最少样本：10","Min samples: 10"),tr("最少样本：20","Min samples: 20")});
  filters[6]=menu(new String[]{tr("来源：全部","Sources: all"),tr("有原版战绩","Has native .run"),tr("有构筑记录","Has Spire Ledger record")});filters[7]=menu(new String[]{tr("全部尝试路径","Final attempts"),tr("仅已记录SL","Recorded SL only")});
  filters[8]=menu(new String[]{tr("结果：全部","Result: all"),tr("仅胜","Wins only"),tr("仅败","Losses only")});resetCategory();setMonths();chartAct=menu(new String[]{tr("第一幕","Act 1"),tr("第二幕","Act 2"),tr("第三幕","Act 3"),tr("第四幕","Act 4"),tr("全部","All")});chartBossAct=menu(new String[]{tr("第一幕","Act 1"),tr("第二幕","Act 2"),tr("全部","All")});chartKind=menu(new String[]{tr("卡牌抓率","Card picks"),tr("火堆行动","Campfire actions"),tr("涅奥选项","Neow options"),tr("Boss 遗物","Boss relics"),tr("实际战损","Actual HP loss")});rateMenu=menu(new String[]{tr("口径：一般率","Rate: observed"),tr("口径：标准率","Rate: standardized")});choiceRateMenu=menu(new String[]{tr("口径：一般率","Rate: observed"),tr("口径：标准率","Rate: standardized"),tr("口径：胜率","Rate: win rate")});chartCampfireRate=menu(new String[]{tr("口径：行动次数","Rate: action counts"),tr("口径：标准化倍","Rate: standardized multiple")});campfireMenu=menu(new String[]{tr("口径：行动次数","Rate: action counts"),tr("口径：标准化倍","Rate: standardized multiple")});
  actMenu=menu(new String[]{tr("幕：全部","Act: all"),tr("第1幕","Act 1"),tr("第2幕","Act 2"),tr("第3幕","Act 3"),tr("第4幕","Act 4")});
  bossActMenu=menu(new String[]{tr("幕：全部","Act: all"),tr("第1幕","Act 1"),tr("第2幕","Act 2")});
  hpSplitMenu=menu(new String[]{tr("回复：全部","Healing: all"),tr("有回复","Healed"),tr("无回复","Not healed")});
 }
 void setMonths(){String[] list=new String[months.size()+1];list[0]=tr("不限月份","Any month");for(int i=0;i<months.size();i++)list[i+1]=months.get(i);fromMonth=menu(list);toMonth=menu(list);fromIndex=toIndex=0;}
 void resetCategory(){String[][] cn={{"胜负总览","各幕通过率","常见死亡遭遇","时间、牌组与金币"},{"候选出现与选取","终局持牌胜率","升级与删除","涅奥选择与结果","Boss遗物选择与结果"},{"遭遇平均承伤","遭遇死亡率","药水使用","商店购买与删除","火堆行动","事件选择","实际战损"}};String[][] en={{"Results","Act completion","Death encounters","Time, deck and gold"},{"Offers and picks","Final deck win rate","Upgrades and removals","Neow and results","Boss relics and results"},{"Encounter damage","Encounter deaths","Potion use","Shops","Campfires","Events","Actual HP loss"}};category=menu(HistoryScreen.zh()?cn[Math.min(theme,2)]:en[Math.min(theme,2)]);categoryIndex=0;offset=0;resetAct();}
 void load(){if(task!=null||requested||host.indexTask!=null)return;requested=true;JsonObject live=host.live;task=host.io.submit(()->StatsIndex.scan(Paths.get("runs"),host.index,live));status=tr("正在读取本地战绩…","Reading local run history…");}
 long month(int index,boolean end){return month(months,index,end);}
 static long month(List<String> months,int index,boolean end){if(index<=0||index>months.size())return 0;String[] parts=months.get(index-1).split("-");Calendar c=Calendar.getInstance();c.clear();c.set(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])-1,1);if(end)c.add(Calendar.MONTH,1);return c.getTimeInMillis();}
 static void dateRange(Stats.Filter filter,int mode,List<String> months,int from,int to){filter.days=0;filter.from=mode==1?month(months,from,false):0;filter.to=mode==1?month(months,to,true):0;}
 @Override public void changedSelectionTo(DropdownMenu menu,int row,String option){for(int i=0;i<filters.length;i++)if(menu==filters[i])selected[i]=row;if(menu==actMenu||menu==bossActMenu){actFilter=row;offset=0;rebuild();return;}if(menu==hpSplitMenu){hpSplit=row;offset=0;rebuild();return;}if(menu==chartKind){chart.kind=row;chart.offset=0;chart.message="";chart.act=row==3?Math.min(chart.act,2):chart.act;chart.mode=chartMode(row);DropdownMenu rate=row==0?rateMenu:row==1?chartCampfireRate:row==2||row==3?choiceRateMenu:null;if(rate!=null)rate.setSelectedIndex(chart.mode);chart.rebuild(metrics);return;}if(menu==chartAct){chart.act=row==4?0:row+1;chart.rebuild(metrics);return;}if(menu==chartBossAct){chart.act=row==2?0:row+1;chart.rebuild(metrics);return;}if(menu==chartCampfireRate){rateMode=row;rateMenu.setSelectedIndex(row);offset=0;chart.mode=rateMode;chart.rebuild(metrics);return;}if(menu==rateMenu){rateMode=row;chartCampfireRate.setSelectedIndex(row);offset=0;chart.mode=rateMode;rebuild();return;}if(menu==choiceRateMenu){choiceMode=row;offset=0;chart.mode=choiceMode;rebuild();return;}if(menu==campfireMenu){campfireMode=row;offset=0;rebuild();return;}if(menu==category){categoryIndex=row;resetAct();}if(menu==fromMonth)fromIndex=row;if(menu==toMonth)toIndex=row;offset=0;rebuild();}
 int chartMode(int kind){return kind==4?0:kind==2||kind==3?choiceMode:rateMode;}
 // Rows are spaced by the dropdown's scaled height so boxes never overlap at any resolution.
 float row(int i){return Settings.HEIGHT*.825f-i*58*Settings.scale;}int monthRow(){return 2;}int advancedRow(){return selected[2]==1?3:2;}
 float bodyTop(){return row(advancedRow())-(advanced?2.4f*58*Settings.scale:0)-20*Settings.scale;}int visible(){return Math.max(1,(int)((bodyTop()-Settings.HEIGHT*.22f)/host.rowHeight()));}
 void layout(){displayRows.clear();Cell pending=null;for(int i=0;i<display.size();i++){Cell metric=metricRows.get(i);if(metric==null){if(pending!=null){pair(pending,null);pending=null;}for(String text:HistoryLayout.wrap(display.get(i),host.contentWidth(),t->host.measure(FontHelper.cardDescFont_N,t,1.12f)))displayRows.add(new Line(text,HistoryScreen.Tone.NORMAL));}else if(pending==null)pending=metric;else{pair(pending,metric);pending=null;}}if(pending!=null)pair(pending,null);}
 /** Consecutive metrics pair left then right (reading order); headings and notes always interrupt the pairing and span the full width. */
 void pair(Cell a,Cell b){float cell=host.cellWidth(),labelWidth=cell*.54f,valueWidth=cell*.44f;List<String> al=wrap(a.label,labelWidth),av=wrap(a.value,valueWidth),bl=b==null?Collections.<String>emptyList():wrap(b.label,labelWidth),bv=b==null?Collections.<String>emptyList():wrap(b.value,valueWidth);int rows=Math.max(Math.max(al.size(),av.size()),Math.max(bl.size(),bv.size()));for(int i=0;i<rows;i++)displayRows.add(new Line(at(al,i),at(av,i),a.tone,a.valueTone,at(bl,i),at(bv,i),b==null?HistoryScreen.Tone.NORMAL:b.tone,b==null?HistoryScreen.Tone.NORMAL:b.valueTone));}
 List<String> wrap(String text,float width){return HistoryLayout.wrap(text,width,t->host.measure(FontHelper.cardDescFont_N,t,1.12f));}
 static String at(List<String> lines,int i){return i<lines.size()?lines.get(i):"";}
 static String metricGroup(String key){String[] parts=key.split(":",4);
  if(key.startsWith("pick:"))return "pick:"+parts[1];if(key.startsWith("skip:")||key.startsWith("bowl:"))return "pick:"+parts[1];
  if(key.startsWith("hp_loss:")||key.startsWith("hp_loss_heal:")||key.startsWith("hp_loss_noheal:")||key.startsWith("act_hp_loss:"))return "hp_loss:"+parts[1];
  if(key.startsWith("damage_act:"))return "damage_act:"+parts[1];if(key.startsWith("encounter_potions_act:"))return "encounter_potions_act:"+parts[1];if(key.startsWith("potions_act:"))return "potions_act:"+parts[1];if(key.startsWith("death_act:"))return "death_act:"+parts[1];
  if(key.startsWith("boss:"))return "boss:"+parts[1];if(key.startsWith("related:boss:"))return "related:boss:"+(parts.length>2?parts[2].split(":",2)[0]:"");
  return parts[0];}
 static Comparator<String> metricOrder(Map<String,Metric> metrics){return Comparator.comparing((String key)->metricGroup(key)).thenComparingInt(key->metrics.get(key).shown()?0:1).thenComparingDouble(key->metrics.get(key).shown()?-metrics.get(key).value():0).thenComparingInt(key->-metrics.get(key).samples).thenComparing(key->key);}
 /** The rate mode of the current page: observed, standardized or the selected-run win rate. */
 int mode(){return theme==1&&(categoryIndex==3||categoryIndex==4)?choiceMode:rateMode;}
 /** The act a row belongs to, for the per-act headings; null when the row is not per act. */
 static String actOf(String key){String[] parts=key.split(":",4);
  if(key.startsWith("pick:")||key.startsWith("skip:")||key.startsWith("bowl:")||key.startsWith("boss:"))return parts[1];
  if(key.startsWith("hp_loss:")||key.startsWith("hp_loss_heal:")||key.startsWith("hp_loss_noheal:")||key.startsWith("act_hp_loss:"))return parts[1];
  if(key.startsWith("damage_act:")||key.startsWith("encounter_potions_act:")||key.startsWith("potions_act:")||key.startsWith("death_act:"))return parts[1];
  if(key.startsWith("related:boss:"))return parts.length>2?parts[2]:null;return null;}
 /** Pages whose rows are grouped under per-act headings get the act menu; the cards page, boss relics, encounter damage, potions, campfires and actual HP loss. */
 boolean actShown(){return theme==1?(categoryIndex==0||categoryIndex==4):theme==2&&(categoryIndex==0||categoryIndex==1||categoryIndex==2||categoryIndex==4||categoryIndex==6);}
 /** Boss relics only exist after the act-1 and act-2 bosses, so their menu stops at act 2. */
 DropdownMenu actControl(){return theme==1&&categoryIndex==4?bossActMenu:actMenu;}
 void resetAct(){actFilter=0;hpSplit=0;if(actMenu!=null)actMenu.setSelectedIndex(0);if(bossActMenu!=null)bossActMenu.setSelectedIndex(0);if(hpSplitMenu!=null)hpSplitMenu.setSelectedIndex(0);}
 /** The actual-HP-loss page shows one healing view at a time; the act totals stay in every view. */
 boolean hiddenRow(String key){if(theme!=2||categoryIndex!=6)return false;if(key.startsWith("act_hp_loss:"))return false;
  if(hpSplit==0)return key.startsWith("hp_loss_heal:")||key.startsWith("hp_loss_noheal:");
  return !key.startsWith(hpSplit==1?"hp_loss_heal:":"hp_loss_noheal:");}
 static boolean actualRow(String key){return key.startsWith("bowl:");}
 /** The HP-loss page ranks each act by mean loss, with the act's per-run total above it; other pages keep their own order. */
 Comparator<String> ordered(){Comparator<String> base=metricOrder(metrics);
  if(theme==2&&categoryIndex==6)return (a,b)->{
   String aa=actOf(a),bb=actOf(b);int byAct=aa==null?(bb==null?0:-1):bb==null?1:aa.compareTo(bb);if(byAct!=0)return byAct;
   boolean at=a.startsWith("act_hp_loss:"),bt=b.startsWith("act_hp_loss:");if(at!=bt)return at?-1:1;
   Metric ma=metrics.get(a),mb=metrics.get(b);boolean sa=ma.shown(),sb=mb.shown();if(sa!=sb)return sa?-1:1;
   int byValue=Double.compare(sb?mb.value():0,sa?ma.value():0);if(byValue!=0)return byValue;
   int bySamples=Integer.compare(mb.samples,ma.samples);if(bySamples!=0)return bySamples;
   return a.compareTo(b);};
  if(theme==2&&categoryIndex==2)return (a,b)->{
   boolean pa=a.equals("potions"),pb=b.equals("potions");if(pa!=pb)return pa?-1:1;
   String aa=actOf(a),bb=actOf(b);int byAct=aa==null?(bb==null?0:-1):bb==null?1:aa.compareTo(bb);if(byAct!=0)return byAct;
   boolean at=a.startsWith("potions_act:"),bt=b.startsWith("potions_act:");if(at!=bt)return at?-1:1;
   Metric ma=metrics.get(a),mb=metrics.get(b);boolean sa=ma.shown(),sb=mb.shown();if(sa!=sb)return sa?-1:1;
   int byValue=Double.compare(sb?mb.value():0,sa?ma.value():0);if(byValue!=0)return byValue;
   int bySamples=Integer.compare(mb.samples,ma.samples);if(bySamples!=0)return bySamples;
   return a.compareTo(b);};
  if(mode()!=1)return base;
  return (a,b)->{int byGroup=metricGroup(a).compareTo(metricGroup(b));if(byGroup!=0)return byGroup;
   boolean aa=actualRow(a),ab=actualRow(b);if(aa!=ab)return aa?1:-1;
   if(a.startsWith("pick:")||a.startsWith("skip:")||a.startsWith("neow:")||a.startsWith("boss:")){Double va=standardShare(a),vb=standardShare(b);
    if(va!=null&&vb!=null){int byShare=Double.compare(vb.doubleValue(),va.doubleValue());if(byShare!=0)return byShare;}
    else if(va!=null)return -1;else if(vb!=null)return 1;}
   return base.compare(a,b);};}
 Map<Integer,Map<String,Double>> reportedShares(){return reportShares;}
 Map<String,Double> reportedShares(int act){return reportShares.get(Integer.valueOf(act));}
 /** Shares over the rows this filter actually shows, renormalized to 100; a screen type left with fewer than two rows is unavailable. */
 void reported(){reportShares.clear();neowShares.clear();bossShares.clear();
  for(Map.Entry<Integer,Standardized.Act> e:standard.entrySet()){Map<String,Double> out=normalize(e.getValue(),"pick:"+e.getKey()+":","skip:"+e.getKey());if(out!=null)reportShares.put(e.getKey(),out);}
  Map<String,Double> neow=normalize(neowStandard,"neow:",null);if(neow!=null)neowShares.putAll(neow);
  for(Map.Entry<Integer,Standardized.Act> e:bossStandard.entrySet()){Map<String,Double> out=normalize(e.getValue(),"boss:"+e.getKey()+":",null);if(out!=null)bossShares.put(e.getKey(),out);}}
 /** Fitted share per visible row of one screen type; null when fewer than two fitted rows remain. skipKey maps that row onto the fit's SKIP. */
 Map<String,Double> normalize(Standardized.Act fitted,String prefix,String skipKey){if(fitted==null||!fitted.available)return null;double total=0;Map<String,Double> raw=new LinkedHashMap<>();
  for(Metric m:metrics.values())if(m.shown()){String id=m.key.startsWith(prefix)?m.key.substring(prefix.length()):skipKey!=null&&m.key.equals(skipKey)?Standardized.SKIP:null;
   if(id==null)continue;Double share=fitted.share.get(id);if(share!=null&&share.doubleValue()>0){raw.put(id,share);total+=share.doubleValue();}}
  if(raw.size()<2||total<=0)return null;Map<String,Double> out=new LinkedHashMap<>();for(Map.Entry<String,Double> r:raw.entrySet())out.put(r.getKey(),Double.valueOf(r.getValue().doubleValue()/total*100));return out;}
 /** Share in percent for a fitted row (card per act, Skip, Neow option, boss relic per act), or null when it cannot be fitted or is filtered out. */
 Double standardShare(String key){if(key.startsWith("pick:")){String[] parts=key.split(":",3);if(parts.length<3)return null;Map<String,Double> shares=reportedShares(Integer.parseInt(parts[1]));return shares==null?null:shares.get(parts[2]);}
  if(key.startsWith("skip:")){String[] parts=key.split(":",3);if(parts.length<2)return null;Map<String,Double> shares=reportedShares(Integer.parseInt(parts[1]));return shares==null?null:shares.get(Standardized.SKIP);}
  if(key.startsWith("neow:"))return neowShares.get(key.substring(5));
  if(key.startsWith("boss:")){String[] parts=key.split(":",3);if(parts.length<3)return null;Map<String,Double> shares=bossShares.get(Integer.valueOf(Integer.parseInt(parts[1])));return shares==null?null:shares.get(parts[2]);}
  return null;}
 boolean rateShown(){return theme==3||theme==1&&(categoryIndex==0||categoryIndex==3||categoryIndex==4);}
 boolean campfireShown(){return theme==2&&categoryIndex==4;}
 String reasonName(String r){switch(r){case "abandon":return tr("放弃","abandoned");case "incomplete":return tr("未完成","incomplete");case "mode":return tr("每日/自定义/无尽","daily/custom/endless");case "conflict":return tr("冲突","conflicts");case "practice":return tr("练习","practice");case "fixture":return tr("夹具","fixtures");case "current":return tr("进行中","in progress");case "result_unknown":return tr("胜负未知","result unknown");case "slot":return tr("存档位","save slot");default:return r;}}
 void rebuild(){filter.character=host.characters[selected[0]];filter.ascension=selected[1]-1;dateRange(filter,selected[2],months,fromIndex,toIndex);filter.heart=selected[3]==0;filter.includeAbandon=selected[4]==1;filter.minSamples=new int[]{1,5,10,20}[selected[5]];
  filter.source=new String[]{"all",Fact.NATIVE,Fact.LEDGER}[selected[6]];filter.slOnly=selected[7]==1;filter.result=new String[]{"all","win","loss"}[selected[8]];filter.slot=com.megacrit.cardcrawl.core.CardCrawlGame.saveSlot;
  selection=Stats.select(index,filter,System.currentTimeMillis());metrics=Metrics.compute(selection,filter);standard=Standardized.compute(selection.runs);neowStandard=Standardized.computeNeow(selection.runs);bossStandard=Standardized.computeBoss(selection.runs);reported();chart.mode=chartMode(chart.kind);chart.rebuild(metrics);List<Run> runs=selection.runs;display.clear();metricRows.clear();
  display.add(tr("合并对局 · 当前 ","Merged runs · Selected ")+runs.size()+tr(" 局 · 胜利口径："," runs · Wins: ")+(filter.heart?tr("杀心","Heart"):tr("通过第三幕","act 3 cleared"))+tr(" · 结果："," · Result: ")+new String[]{tr("全部","all"),tr("仅胜","wins"),tr("仅败","losses")}[selected[8]]);
  if(advanced)display.add(tr("当前存档位 ","Current save slot ")+filter.slot+tr(" · 已读取：原版 "," · Read: native ")+index.nativeFiles+tr(" 个文件，构筑记录 "," files, Ledger records ")+index.ledgerRecords+tr(" 局 · 读取错误 "," runs · Read errors ")+index.errors.size());
  if(requested&&status.isEmpty()&&index.nativeFiles==0&&!Files.isDirectory(Paths.get("runs")))display.add(tr("未找到原版历史目录。","Native run history directory was not found."));
  if(advanced&&!selection.excluded.isEmpty()){StringBuilder b=new StringBuilder(tr("已排除：","Excluded: "));for(Map.Entry<String,Integer> e:selection.excluded.entrySet())b.append(reasonName(e.getKey())).append(' ').append(e.getValue()).append("  ");display.add(b.toString().trim());}
  if(filter.from>0&&filter.to>0&&filter.from>=filter.to){display.add(tr("起始月份晚于结束月份，请调整筛选。","Start month follows end month; adjust the filter."));layout();return;}
  if(theme==3){layout();return;}
  if(runs.isEmpty()){display.add(tr("暂无数据。","No data."));layout();return;}
  if(theme==0&&categoryIndex==0){for(String k:Arrays.asList("outcome:victory","outcome:death","outcome:abandon","outcome:unknown","win"))line(metrics.get(k));for(String k:Arrays.asList("deck_size","score","purge_shop","purge_event","purge_total"))line(metrics.get(k));display.add(tr("平均删牌按 TinyCat 口径：商店购买、事件移除、火堆抽烟、涅奥移除与空鸟笼；95% 区间为均值 ±1.96 标准误，胜率为 Wilson 区间。","Removal means follow TinyCat: shop, events, campfire toke, Neow and Empty Cage; 95% intervals are mean ± 1.96 SE, the win rate a Wilson interval."));display.add(tr("胜负未知不算失败；放弃不会归为死于最后遭遇。","Unknown results are excluded; abandonment is not an encounter death."));}
  else if(theme==0&&categoryIndex==3){for(String k:Arrays.asList("time","deck_size","gold_gained","gold_gained:exact","gold_gained:estimated"))line(metrics.get(k));}
  else {List<String> prefixes=new ArrayList<>();if(theme==0)prefixes.addAll(categoryIndex==1?Arrays.asList("act:"):Arrays.asList("death_count:","death_count_unknown"));else if(theme==1){if(categoryIndex==0)prefixes.addAll(Arrays.asList("pick:","skip:","bowl:","reward_source:","reward_automation"));if(categoryIndex==1)prefixes.add("holding:");if(categoryIndex==2){prefixes.add("upgrade:");prefixes.add("remove:");}if(categoryIndex==3)prefixes.add(mode()==2?"related:neow:":"neow:");if(categoryIndex==4)prefixes.add(mode()==2?"related:boss:":"boss:");}
   else {if(categoryIndex==0)prefixes.add("damage_act:");if(categoryIndex==1){prefixes.add("death:");prefixes.add("death_act:");}if(categoryIndex==2){prefixes.add("potions");prefixes.add("encounter_potions_act:");}if(categoryIndex==3){prefixes.add("purchase:");prefixes.add("remove:shop:");}if(categoryIndex==5){prefixes.add("event:");prefixes.add("related:event:");}if(categoryIndex==6){prefixes.add("act_hp_loss:");prefixes.add("hp_loss:");prefixes.add("hp_loss_heal:");prefixes.add("hp_loss_noheal:");}}
   int before=display.size();if(theme==2&&categoryIndex==4)campfirePage();else{List<String> keys=new ArrayList<>(metrics.keySet());keys.sort(ordered());String act="";for(String key:keys){boolean match=false;for(String prefix:prefixes)if(key.startsWith(prefix)){match=true;break;}
     if(!match||hiddenRow(key))continue;String next=actOf(key);
     if(actFilter>0&&actShown()&&(next==null||!next.equals(String.valueOf(actFilter))))continue;
     if(next!=null&&!next.equals(act)){display.add(actHeader(key,next));act=next;}line(metrics.get(key));}if(before==display.size())display.add(tr("该项未记录。","Not recorded."));}
  }
  if(theme==1&&categoryIndex==1)display.add(tr("这是持牌对局的相关胜率，不是选这张牌带来的收益。","Association among final decks, not the benefit of choosing a card."));
  if(theme==2&&categoryIndex==0)display.add(tr("仅使用原版承伤字段；楼层净血量不会代替承伤。","Explicit damage records only; floor HP deltas are not damage."));
  if(theme==2&&categoryIndex==6)display.add(tr("实际战损 = 入层生命 − 本层结束生命；第一层缺入层记录时为估算。","Actual loss = entry HP − floor end HP; floor 1 is estimated without an entry record."));
  if(theme==1&&categoryIndex==0)display.add(tr("只计已确认战后奖励；同次同名候选最多出现一次；已知自动化的选择不计入。","Confirmed combat rewards only; duplicate offers count once per choice; known automation is left out."));
  if(theme==1&&categoryIndex==0&&mode()==1)display.add(tr("标准率是同屏对手对齐后的强度份额，合计 100%（含跳过）；无法拟合的幕显示 —，两份口径不能相减；跳过按拟合份额参与排序，歌唱碗并入跳过的份额，另列的实际占比仅供参考。","Standardized shares are opponent-adjusted strength summing to 100% including Skip; acts that cannot be fitted show —. The two views are not subtractable; Skip is fitted and ranked like any option, Singing Bowl is folded into its share, and the extra observed Bowl row is for reference only."));
  if(theme==1&&categoryIndex==0)display.add(tr("原版战绩不记录真人/自动化操作来源。","Native history does not record human/automated action sources."));
  if(theme==1&&(categoryIndex==3||categoryIndex==4)&&mode()==1)display.add(categoryIndex==3?tr("涅奥标准率按同屏（所选与未选）对齐后的强度份额，合计 100%；缺少未选记录的局不参与拟合，无法拟合时显示 —。","Neow shares are opponent-adjusted strength over the whole screen, summing to 100%; runs without skipped options are not fitted, and an unfittable screen shows —."):tr("Boss 遗物标准率按同屏（所选与未选）对齐后的强度份额，合计 100%（含跳过）；跳过与遗物一样参与拟合并显示份额。","Boss relic shares are opponent-adjusted strength over the whole screen, summing to 100% including Skip; Skip is fitted and reported like any relic."));
  if(theme==1&&(categoryIndex==3||categoryIndex==4)&&mode()==2)display.add(tr("胜率口径 = 选了该选项的局里赢了多少（选后相关，不是该选项带来的收益）；每局只计一次，胜负未知的局不计入，仅胜/仅败筛选下显示 —；暂不提供区间。","Win rate = wins among runs that picked this option (selection-conditional, not its causal benefit); once per run, unknown results excluded, and — under Win/Loss only. No interval yet."));
  if(campfireShown())display.add(campfireMode==1?tr("标准化倍 = 该条目被选概率 ÷ 随机基线（100 ÷ 平均候选池）；候选池 = 可升级的每张牌 + 可用的抽烟/举重/挖掘/回忆，不含睡觉，也不把锻造算作一个选项；战锤禁锻造、咖啡杯禁休息时对应选项不参与；为进入第四幕在第三幕拿的回忆是固定成本，不计入。每张牌单独一行。","Multiple = chosen ÷ the random baseline (100 ÷ mean pool); the pool is every unupgraded card plus available Toke/Lift/Dig/Recall, never sleep and never smith as one slot. Fusion Hammer and Coffee Dripper remove their option. The act-3 Recall taken for the red key is a fixed cost and is left out. Each card is its own row."):tr("行动次数按幕统计每次火堆实际做了什么：锻造按被升级的卡牌逐张计数，抽烟/举重/挖掘/回忆/休息按行动类型计数；“火堆总数”是该幕的火堆访问次数。","Action counts per act: every smith is counted under the card it upgraded; Toke/Lift/Dig/Recall/Rest count by action type; the rest-site total is that act's visits."));
  String coverage=theme==0?new String[]{"result","act","death","gold"}[categoryIndex]:theme==1?new String[]{"reward","deck","observed","neow","boss_relics"}[categoryIndex]:new String[]{"damage","death","potions_by_encounter","purchases","campfire","events","hp_loss"}[categoryIndex];
  Metric c=metrics.get("coverage:"+coverage);if(c!=null)display.add(tr("字段覆盖：","Field coverage: ")+quantity(c.numerator)+" / "+quantity(c.denominator)+" "+unitName(c.unit)+tr("（缺失不作为0）"," (missing is not zero)"));
  if(metrics.containsKey("unresolved_path"))display.add(tr("部分SL父点未关联，仅统计最终分支可确认片段。","Some SL parents are unknown; only confirmed final-branch fragments count."));layout();
 }
 String unitName(String unit){switch(unit){case "run_act":return tr("局·幕","run-acts");case "combat":return tr("场","fights");case "choice_set":return tr("屏","screens");case "choice":return tr("次","offers");case "event":return tr("次","events");default:return tr("局","runs");}}
 /** Campfire page: per-act action counts (smith split by card), or per-act preference ranking on the shared pool baseline. */
 void campfirePage(){boolean multiple=campfireMode==1;int[] order=multiple?new int[]{1,2,3,4,0}:new int[]{1,2,3,4};int before=display.size();
  for(int act:order){if(actFilter>0&&(act==0||act!=actFilter))continue;Metric visits=metrics.get("campfire_visits:"+act);if(visits==null)continue;
   display.add((act==0?tr("四幕合计","All acts"):tr("第 ","Act ")+act+tr(" 幕",""))+(multiple?tr(" · 火堆偏好"," · campfire preference"):tr(" · 行动次数"," · action counts")));
   if(!multiple){line(visits);for(String key:campfireRows("campfire_count:"+act+":"))line(metrics.get(key));continue;}
   Metric smiths=metrics.get("campfire_smiths:"+act),pool=metrics.get("campfire_pool:"+act),rebuild=metrics.get("campfire_rebuild:"+act);
   StringBuilder head=new StringBuilder(tr("火堆 ","Campfires ")).append(quantity(visits.numerator)).append(tr(" 次"," visits"));
   if(smiths!=null&&visits.numerator>0)head.append(tr(" · 锻造 "," · smith ")).append(percent(smiths.numerator/visits.numerator));
   double poolAvg=pool==null?0:pool.value();
   if(poolAvg>0)head.append(tr(" / 平均候选 "," / mean pool ")).append(String.format(Locale.ROOT,"%.1f",poolAvg)).append(tr(" 个"," cards")).append(tr(" · 随机基线 "," · random baseline ")).append(String.format(Locale.ROOT,"%.1f%%",100.0/poolAvg)).append(tr("（倍 = 占比÷基线）"," (multiple = share ÷ baseline)"));
   if(rebuild!=null&&rebuild.denominator>0)head.append(" · ").append(tr("重建覆盖 ","rebuild coverage ")).append(quantity(rebuild.numerator)).append("/").append(quantity(rebuild.denominator)).append(" (").append(percent(rebuild.numerator/rebuild.denominator)).append(")");
   display.add(head.toString());
   Metric sleep=metrics.get("campfire_sleep:"+act);if(sleep!=null)line(sleep);
   for(String key:campfireRows("campfire_pref:"+act+":"))line(metrics.get(key));}
  if(before==display.size())display.add(tr("该项未记录。","Not recorded."));}
 /** Rows under one campfire prefix, highest share (or count) first; ties keep the sample-heavy key first. */
 List<String> campfireRows(String prefix){List<String> keys=new ArrayList<>();for(String key:metrics.keySet())if(key.startsWith(prefix))keys.add(key);keys.sort(metricOrder(metrics));return keys;}
 static String percent(double fraction){return String.format(Locale.ROOT,"%.1f%%",fraction*100);}
 String campfireName(String id,boolean count){String action=id.equals("REST")?tr("休息","Rest"):id.equals("SMITH")?tr("锻造","Smith"):id.equals("RECALL")?tr("回忆","Recall"):id.equals("PURGE")?tr("抽烟","Toke"):id.equals("LIFT")?tr("举重","Lift"):id.equals("DIG")?tr("挖掘","Dig"):"";
  if(!action.isEmpty())return action;return count?tr("锻造 · ","Upgrade · ")+HistoryScreen.cardName(id):HistoryScreen.cardName(id);}
 /** Per-act heading for rows that are grouped by act: cards and Skip/Bowl, and boss relics in every rate mode. */
 String actHeader(String key,String act){if(key.startsWith("related:boss:"))return tr("第 ","Act ")+act+tr(" 幕 · Boss 遗物 · 胜率"," · Boss relics · win rate");
  if(key.startsWith("hp_loss:")||key.startsWith("hp_loss_heal:")||key.startsWith("hp_loss_noheal:")||key.startsWith("act_hp_loss:"))return tr("第 ","Act ")+act+tr(" 幕 · 实际战损"," · actual HP loss");
  if(key.startsWith("damage_act:"))return tr("第 ","Act ")+act+tr(" 幕 · 遭遇平均承伤"," · encounter damage");
  if(key.startsWith("death_act:"))return tr("第 ","Act ")+act+tr(" 幕 · 遭遇死亡率"," · encounter deaths");
  if(key.startsWith("encounter_potions_act:")||key.startsWith("potions_act:"))return tr("第 ","Act ")+act+tr(" 幕 · 药水使用"," · potion use");
  if(key.startsWith("boss:"))return tr("第 ","Act ")+act+tr(" 幕 · Boss 遗物 · "," · Boss relics · ")+(mode()==1?tr("标准率","standardized"):tr("选取率","pick rate"));
  return tr("第 ","Act ")+act+tr(" 幕 · "," · ")+(mode()==1?tr("标准率 · 末行为实际占比","standardized · trailing rows observed"):tr("选取率","pick rate"));}
 String label(String key){if(key.startsWith("related:")){String base=key.substring(8);
   if(mode()==2&&base.startsWith("neow:"))return neow(base.substring(5));
   if(mode()==2&&base.startsWith("boss:")){String[] q=base.split(":",3);return q.length>2?(q[2].equals("SKIP")?tr("跳过","Skip"):HistoryScreen.relicName(q[2])):base;}
   return label(base)+tr(" · 所选对局胜率（相关）"," · Selected-run win rate (association)");}
  if(key.equals("outcome:victory"))return tr("胜利（杀心另计）","Victories (Heart counted separately)");if(key.equals("outcome:abandon"))return tr("放弃","Abandoned");if(key.equals("outcome:unknown"))return tr("结局未知","Result unknown");
  if(key.startsWith("death_count:")||key.startsWith("death:")||key.startsWith("damage:")||key.startsWith("encounter_potions:"))return encounter(key.substring(key.indexOf(':')+1));if(key.equals("death_count_unknown"))return tr("死因未知","Cause of death unknown");
  if(key.startsWith("damage_act:")||key.startsWith("encounter_potions_act:")||key.startsWith("death_act:")){String[] q=key.split(":",3);return encounter(q[2]);}
  if(key.startsWith("potions_act:"))return tr("本幕药水使用","Potion uses this act");
  if(key.startsWith("event:")){String[] pair=key.substring(6).split(" / ",2);return com.megacrit.cardcrawl.helpers.EventHelper.getEventName(pair[0])+(pair.length>1?" · "+pair[1]:"");}if(key.startsWith("neow:"))return neow(key.substring(5));
  if(key.equals("win"))return tr("胜率","Win rate");if(key.equals("time"))return tr("平均游戏时间","Mean playtime");if(key.equals("deck_size"))return tr("平均终局牌组大小","Mean final deck size");
  if(key.equals("score"))return tr("平均得分","Mean score");if(key.equals("purge_total"))return tr("平均每局删牌","Mean removals per run");if(key.equals("purge_shop"))return tr("商店平均删牌","Mean shop removals");if(key.equals("purge_event"))return tr("事件平均删牌","Mean event removals");
  if(key.equals("gold_gained"))return tr("平均累计获得金币","Mean gold gained");if(key.equals("gold_gained:exact"))return tr("  其中精确","  of which exact");if(key.equals("gold_gained:estimated"))return tr("  其中估算","  of which estimated");
  if(key.equals("reward_source:unknown"))return tr("操作来源未知的选牌占比","Share of picks with unknown action source");if(key.equals("reward_automation_excluded"))return tr("已知自动化选择（不计入）","Known automation choices (left out)");
  if(key.startsWith("outcome:"))return host.outcome(key.substring(8));if(key.startsWith("act:"))return tr("第 ","Act ")+key.substring(4)+tr(" 幕通过率"," completion");if(key.startsWith("act_hp_loss:"))return tr("第 ","Act ")+key.substring(12)+tr(" 幕总战损（每局）"," total HP loss per run");String[] parts=key.split(":",3);String tail=parts.length==3?parts[2]:parts.length==2?parts[1]:key;
  if(key.startsWith("hp_loss"))return tr("幕 ","Act ")+parts[1]+" · "+encounter(tail)+(key.startsWith("hp_loss_heal:")?tr(" · 有回复"," · healed"):key.startsWith("hp_loss_noheal:")?tr(" · 无回复"," · not healed"):"");
  if(key.startsWith("pick:"))return tr("幕 ","Act ")+parts[1]+" · "+HistoryScreen.cardName(tail);if(key.startsWith("skip:")||key.startsWith("bowl:"))return tr("幕 ","Act ")+parts[1]+" · "+(key.startsWith("skip:")?tr("跳过","Skip"):tr("歌唱碗","Bowl"));if(key.startsWith("holding:"))return HistoryScreen.cardName(tail);if(key.startsWith("boss:"))return tr("幕 ","Act ")+parts[1]+" · "+(tail.equals("SKIP")?tr("跳过","Skip"):HistoryScreen.relicName(tail));
  if(key.startsWith("upgrade:")||key.startsWith("remove:"))return (key.startsWith("upgrade:")?tr("升级","Upgrade"):tr("删除","Remove"))+" · "+(parts[1].equals("observed")?tr("观察到（不归因）","Observed, no attribution"):parts[1].equals("shop")?tr("商店","Shop"):parts[1].equals("campfire")?tr("火堆","Campfire"):tr("事件","Event"))+" · "+HistoryScreen.cardName(tail);if(key.equals("potions"))return tr("已记录药水使用","Recorded potion uses");if(key.startsWith("purchase:"))return tr("购买 · ","Bought · ")+itemName(tail);if(key.startsWith("campfire_visits:"))return tr("火堆总数","Rest sites");if(key.startsWith("campfire_count:"))return campfireName(tail,true);if(key.startsWith("campfire_pref:"))return campfireName(tail,false);if(key.startsWith("campfire_sleep:"))return tr("睡觉","Sleep");return tail;
 }
 /** metrics.md 1.2, 1.5, 3.3: hidden win rates read "—", missing reads "not recorded", below the minimum reads "too few samples"; numbers otherwise come with their denominators. */
 void line(Metric m){if(m==null){display.add(tr("该项未记录","Not recorded"));return;}String value;
  Double share=mode()==1&&m.shown()?standardShare(m.key):null;
  if(share!=null)value=String.format(Locale.ROOT,"%.2f%%",share.doubleValue());
  else if(actualRow(m.key)&&mode()==1&&m.shown())value=tr("实际 ","Observed ")+String.format(Locale.ROOT,"%.1f%%  (%s / %s)",m.value()*100,quantity(m.numerator),quantity(m.denominator));
  else if(mode()==1&&(m.key.startsWith("pick:")||m.key.startsWith("skip:")||m.key.startsWith("neow:")||m.key.startsWith("boss:"))&&m.shown())value="—";
  else if(m.key.startsWith("campfire_visits:")||m.key.startsWith("campfire_count:"))value=quantity(m.numerator)+tr(" 次","");
  else if(m.key.startsWith("campfire_pref:"))value=!m.shown()?m.status.equals("below_min_sample")?tr("样本不足","Too few samples")+" ("+quantity(m.samples)+")":tr("未记录","Not recorded"):String.format(Locale.ROOT,"%.1f%% %.1f× (%s/%s)",m.value()*100,m.baseline>0?m.value()*100/m.baseline:0,quantity(m.numerator),quantity(m.denominator));
  else switch(m.status){case "hidden_by_result_filter":value="—";break;case "missing":value=tr("未记录","Not recorded");break;case "below_min_sample":value=tr("样本不足","Too few samples")+" ("+quantity(m.samples)+")";break;
   default:value=m.kind.equals(Metric.RATE)?String.format(Locale.ROOT,"%.1f%%  (%s / %s)",m.value()*100,quantity(m.numerator),quantity(m.denominator)):m.kind.equals(Metric.COUNT)?quantity(m.numerator):String.format(Locale.ROOT,"%.1f %s  (%s / %s)",m.value(),m.measure.equals("seconds")?tr("秒","s"):m.measure.equals("cards")?tr("张","cards"):m.measure.equals("hp")?tr("生命","HP"):m.measure.equals("potions")?tr("瓶","potions"):m.measure.equals("gold")?tr("金币","gold"):m.measure.equals("score")?tr("分","pts"):m.measure,quantity(m.numerator),quantity(m.denominator));}
  value+=(m.estimated>0&&m.shown()?tr(" · 含估算 "," · estimated ")+m.estimated:"")+(m.excluded>0&&m.shown()?tr(" · 未知不计 "," · Unknown excluded ")+m.excluded:"")+interval(m);metricRows.put(display.size(),new Cell(label(m.key),value,HistoryScreen.Tone.NORMAL,HistoryScreen.Tone.CHANGED));display.add(label(m.key)+"   "+value);}
 /** Two-sided 95% interval: Wilson for the win rate, mean ± 1.96 standard errors for the overview means; empty when it cannot be formed. */
 String interval(Metric m){if(!INTERVALS.contains(m.key)||!m.shown())return "";double[] ci=m.interval();if(ci==null)return "";boolean rate=m.kind.equals(Metric.RATE);
  return tr("  95%区间 ","  95% CI ")+intervalText(ci[0]*(rate?100:1),ci[1]*(rate?100:1),rate?"%":"");}
 static final List<String> INTERVALS=Arrays.asList("win","deck_size","score","purge_total","purge_shop","purge_event");
 static String intervalText(double low,double high,String suffix){return String.format(Locale.ROOT,"%.1f–%.1f%s",low,high,suffix);}
 String itemName(String id){if(com.megacrit.cardcrawl.helpers.CardLibrary.getCard(id)!=null)return HistoryScreen.cardName(id);try{String name=HistoryScreen.relicName(id);if(!name.equals(id))return name;}catch(Exception ignored){}try{return CardCrawlGame.languagePack.getPotionString(id).NAME;}catch(Exception ignored){return id;}}
 String encounter(String id){try{if("Blue Slaver".equals(id))return tr("蓝奴隶贩子","Blue Slaver");if("Red Slaver".equals(id))return tr("红奴隶贩子","Red Slaver");return MonsterHelper.getEncounterName(id);}catch(Exception e){return id;}}
/** Native history stores a Neow key as "BONUS / COST"; a free option carries NONE or an empty cost, which is left off instead of repeated. */
String neow(String id){String[] parts=id.split(" / ",2);String bonus=neowPart(parts[0],false);String cost=parts.length>1&&!parts[1].isEmpty()&&!parts[1].equals("NONE")?neowPart(parts[1],true):"";return cost.isEmpty()?bonus:bonus+" / "+cost;}
String neowPart(String id,boolean cost){if(cost)switch(id){case "TEN_PERCENT_HP_LOSS":return tr("失去最大生命","Lose max HP");case "NO_GOLD":return tr("失去金币","Lose gold");case "CURSE":return tr("获得诅咒","Receive curse");case "PERCENT_DAMAGE":return tr("受到伤害","Take damage");default:return id;}
 switch(id){case "RANDOM_COLORLESS_2":return tr("稀有无色牌","Rare colorless card");case "RANDOM_COLORLESS":return tr("无色牌","Colorless card");case "THREE_CARDS":return tr("三张卡中选一张","Choose one of three cards");case "ONE_RANDOM_RARE_CARD":return tr("随机稀有牌","Random rare card");case "REMOVE_TWO":return tr("删两张牌","Remove two cards");case "REMOVE_CARD":return tr("删除1张牌","Remove 1 card");case "UPGRADE_CARD":return tr("升级牌","Upgrade a card");case "TRANSFORM_TWO_CARDS":return tr("变两张牌","Transform two cards");case "TRANSFORM_CARD":return tr("变一张牌","Transform a card");case "THREE_SMALL_POTIONS":return tr("三瓶药水","Three potions");case "RANDOM_COMMON_RELIC":return tr("普通遗物","Common relic");case "TEN_PERCENT_HP_BONUS":return tr("最大生命 +10%","Max HP +10%");case "HUNDRED_GOLD":return tr("100 金币","100 gold");case "THREE_ENEMY_KILL":return tr("前三场敌人 1 HP","First three encounters: 1 HP");case "ONE_RARE_RELIC":return tr("稀有遗物","Rare relic");case "THREE_RARE_CARDS":return tr("稀有牌三选一","Choose 1 of 3 rare cards");case "TWO_FIFTY_GOLD":return tr("250 金币","250 gold");case "TWENTY_PERCENT_HP_BONUS":return tr("最大生命 +20%","Max HP +20%");case "BOSS_RELIC":return tr("替换初始遗物","Swap starting relic");default:return id;}}
String quantity(double n){return n==(long)n?String.valueOf((long)n):String.format(Locale.ROOT,"%.1f",n);}
 void dismissMenus(){for(DropdownMenu d:filters)d.isOpen=false;for(DropdownMenu d:Arrays.asList(category,fromMonth,toMonth,chartKind,chartAct,chartBossAct,rateMenu,choiceRateMenu,chartCampfireRate,campfireMenu,actMenu,bossActMenu,hpSplitMenu))if(d!=null)d.isOpen=false;}
 boolean openDropdown(){for(DropdownMenu d:visibleMenus())if(d.isOpen)return true;return false;}
 List<DropdownMenu> visibleMenus(){List<DropdownMenu> out=new ArrayList<>();for(int i:new int[]{0,1,2})out.add(filters[i]);if(theme==3){out.add(chartKind);if(chart.kind!=2)out.add(chart.kind==3?chartBossAct:chartAct);if(chart.kind<4)out.add(chart.kind==1?chartCampfireRate:chart.kind==0?rateMenu:choiceRateMenu);}else out.add(category);if(theme==1&&rateShown())out.add(categoryIndex==0?rateMenu:choiceRateMenu);if(campfireShown())out.add(campfireMenu);if(actShown())out.add(actControl());if(theme==2&&categoryIndex==6)out.add(hpSplitMenu);if(selected[2]==1){out.add(fromMonth);out.add(toMonth);}if(advanced)for(int i:new int[]{3,4,5,6,7,8})out.add(filters[i]);return out;}
 void update(){load();if(task!=null&&task.isDone()){try{index=task.get();Set<String> availableMonths=new TreeSet<>(Comparator.reverseOrder());SimpleDateFormat format=new SimpleDateFormat("yyyy-MM",Locale.ROOT);for(Run r:index.runs)if(r.time.or(0L)>0)availableMonths.add(format.format(new Date(r.time.value)));months=new ArrayList<>(availableMonths);setMonths();status="";rebuild();}catch(Exception e){status=tr("战绩读取失败：","Read failed: ")+e.getClass().getSimpleName();}task=null;}
  host.cancel.update();if(host.cancel.hb.clicked||InputHelper.pressedEscape){host.cancel.hb.clicked=false;chart.dispose();host.close();return;}
  for(DropdownMenu d:visibleMenus())d.update();if(openDropdown())return;
  List<Hitbox> controls=new ArrayList<>(Arrays.asList(host.latestTab,host.historyTab,host.home));controls.addAll(Arrays.asList(themes));for(DropdownMenu d:visibleMenus())controls.add(d.getHitbox());controls.addAll(Arrays.asList(clearButton,refreshButton,advancedButton,host.prev,host.next,host.cancel.hb));if(theme==3){controls.add(exportButton);}host.controller(controls);
  for(int i=0;i<themes.length;i++)if(host.click(themes[i])){theme=i;if(theme==3){rateMenu.setSelectedIndex(rateMode);choiceRateMenu.setSelectedIndex(choiceMode);chartCampfireRate.setSelectedIndex(rateMode);campfireMenu.setSelectedIndex(campfireMode);}resetCategory();rebuild();}
  if(host.click(advancedButton)){advanced=!advanced;dismissMenus();offset=0;rebuild();}
  if(host.click(clearButton)){Arrays.fill(selected,0);for(DropdownMenu d:filters)d.setSelectedIndex(0);categoryIndex=0;category.setSelectedIndex(0);fromIndex=toIndex=0;fromMonth.setSelectedIndex(0);toMonth.setSelectedIndex(0);resetAct();offset=0;rebuild();}
  if(host.click(refreshButton)){if(theme==3&&chart.lastExport!=null){try{java.awt.Desktop.getDesktop().open(chart.lastExport.toFile());}catch(Exception e){chart.message=tr("无法打开文件夹：","Cannot open folder: ")+chart.lastExport;}}else{requested=false;host.refresh();}}
  if(theme==3&&host.click(exportButton)){if(!chart.exportable())chart.message=tr("当前筛选没有可导出的记录。","No records to export in this selection.");else chart.exportRequested=true;}
  int move=InputHelper.scrolledDown?1:InputHelper.scrolledUp?-1:0;if(host.click(host.prev))move-=3;if(host.click(host.next))move+=3;
  if(theme==3)chart.scroll(move);else offset=Math.max(0,Math.min(Math.max(0,displayRows.size()-visible()),offset+move*3));
 }
 void cell(SpriteBatch sb,String label,String value,HistoryScreen.Tone tone,HistoryScreen.Tone valueTone,float x,float top,float width){if(!label.isEmpty())host.styledText(sb,label,x,top,tone);if(!value.isEmpty())host.styledText(sb,value,x+width-host.measure(FontHelper.cardDescFont_N,value,1.12f),top,valueTone);}
 void render(SpriteBatch sb){sb.setColor(new Color(.045f,.06f,.085f,1));sb.draw(ImageMaster.WHITE_SQUARE_IMG,0,0,Settings.WIDTH,Settings.HEIGHT);sb.setColor(Color.WHITE);host.styledText(sb,tr("尖塔账本","Spire Ledger"),Settings.WIDTH*.06f,Settings.HEIGHT*.95f,HistoryScreen.Tone.HEADER);
  String[] labels={tr("总览","Overview"),tr("卡牌与构筑","Cards and build"),tr("战斗与资源","Combat and resources"),tr("导出","Export")};for(int i=0;i<4;i++)host.tab(sb,themes[i],labels[i],theme==i);float sc=Settings.scale;
  clearButton.move(Settings.WIDTH*.90f,row(0));advancedButton.move(Settings.WIDTH*.82f,row(1));refreshButton.move(Settings.WIDTH*.86f,Settings.HEIGHT*.13f);exportButton.move(Settings.WIDTH*.655f,Settings.HEIGHT*.13f);
  if(!status.isEmpty())host.text(sb,status,Settings.WIDTH*.06f,bodyTop()+30*sc,Settings.GOLD_COLOR);
  if(theme==3)chart.render(sb);else{float y=bodyTop(),x=Settings.WIDTH*.06f,cellW=host.cellWidth(),gap=Settings.WIDTH*.025f;for(int i=0;i<visible()&&offset+i<displayRows.size();i++){Line line=displayRows.get(offset+i);float top=y-i*host.rowHeight();if(!line.two){host.styledText(sb,line.text,x,top,offset+i==0?HistoryScreen.Tone.CHANGED:line.tone);continue;}cell(sb,line.lLabel,line.lValue,offset+i==0?HistoryScreen.Tone.CHANGED:line.lTone,line.lValueTone,x,top,cellW);cell(sb,line.rLabel,line.rValue,line.rTone,line.rValueTone,x+cellW+gap,top,cellW);}}
  host.button(sb,clearButton,tr("重置","Reset"));host.button(sb,refreshButton,theme==3&&chart.lastExport!=null?tr("打开文件夹","Open folder"):tr("刷新","Refresh"));host.button(sb,advancedButton,advanced?tr("收起筛选","Fewer filters"):tr("更多筛选","More filters"));host.button(sb,host.prev,tr("上一页","Previous"));host.button(sb,host.next,tr("下一页","Next"));host.cancel.render(sb);if(theme==3){host.button(sb,exportButton,tr("导出 PNG+CSV","Export PNG+CSV"));}
  List<Object[]> menus=new ArrayList<>();float[] x={.06f,.32f,.58f};for(int i=0;i<3;i++)menus.add(new Object[]{filters[i],Settings.WIDTH*x[i],row(0)});
  if(theme==3){menus.add(new Object[]{chartKind,Settings.WIDTH*.06f,row(1)});if(chart.kind!=2)menus.add(new Object[]{chart.kind==3?chartBossAct:chartAct,Settings.WIDTH*.32f,row(1)});if(chart.kind<4)menus.add(new Object[]{chart.kind==1?chartCampfireRate:chart.kind==0?rateMenu:choiceRateMenu,Settings.WIDTH*.58f,row(1)});}
  else{menus.add(new Object[]{category,Settings.WIDTH*.06f,row(1)});boolean middle=false;
   if(theme==1&&rateShown()){menus.add(new Object[]{categoryIndex==0?rateMenu:choiceRateMenu,Settings.WIDTH*.32f,row(1)});middle=true;}
   else if(campfireShown()){menus.add(new Object[]{campfireMenu,Settings.WIDTH*.32f,row(1)});middle=true;}
   if(actShown()){menus.add(new Object[]{actControl(),Settings.WIDTH*(middle?.58f:.32f),row(1)});middle=true;}
   if(theme==2&&categoryIndex==6)menus.add(new Object[]{hpSplitMenu,Settings.WIDTH*(middle?.58f:.32f),row(1)});}
  if(selected[2]==1){menus.add(new Object[]{fromMonth,Settings.WIDTH*.06f,row(monthRow())});menus.add(new Object[]{toMonth,Settings.WIDTH*.32f,row(monthRow())});}
  if(advanced){int[] extra={3,4,5,6,7,8};for(int i=0;i<extra.length;i++)menus.add(new Object[]{filters[extra[i]],Settings.WIDTH*x[i%3],row(advancedRow()+i/3)});}
  for(Object[] m:menus)if(!((DropdownMenu)m[0]).isOpen)((DropdownMenu)m[0]).render(sb,(float)m[1],(float)m[2]);for(Object[] m:menus)if(((DropdownMenu)m[0]).isOpen)((DropdownMenu)m[0]).render(sb,(float)m[1],(float)m[2]);
 }
}
