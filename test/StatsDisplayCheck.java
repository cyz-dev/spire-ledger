package spireledger;

import com.google.gson.Gson;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import spireledger.stats.*;

/** Display ordering and absolute month boundaries without starting the game. */
public final class StatsDisplayCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Metric rate(String key,double n,double d,int samples,String status){Metric m=new Gson().fromJson("{\"key\":\""+key+"\",\"kind\":\"rate\",\"status\":\""+status+"\"}",Metric.class);m.numerator=n;m.denominator=d;m.samples=samples;return m;}
 public static void main(String[] args)throws Exception{
  Map<String,Metric> metrics=new LinkedHashMap<>();
  for(Metric m:Arrays.asList(rate("pick:1:Low",1,100,100,"ok"),rate("pick:1:High",8,10,10,"ok"),rate("pick:2:NextAct",10,10,10,"ok"),rate("pick:1:Missing",100,100,100,"missing"),rate("pick:1:MoreSamples",16,20,20,"ok"),rate("pick:1:Zero",0,10,10,"zero"),rate("pick:1:A",8,10,10,"ok")))metrics.put(m.key,m);
  List<String> keys=new ArrayList<>(metrics.keySet());keys.sort(StatsScreen.metricOrder(metrics));
  check(keys.equals(Arrays.asList("pick:1:MoreSamples","pick:1:A","pick:1:High","pick:1:Low","pick:1:Zero","pick:1:Missing","pick:2:NextAct")),"Act groups, displayed value descending, sample/name ties, missing last: "+keys);
  List<String> months=Arrays.asList("2026-10","2026-09");Stats.Filter filter=new Stats.Filter();filter.days=30;
  StatsScreen.dateRange(filter,1,months,2,1);Calendar c=Calendar.getInstance();c.clear();c.set(2026,Calendar.SEPTEMBER,1);
  check(filter.days==0&&filter.from==c.getTimeInMillis(),"Absolute inclusive start replaces rolling days");c.set(2026,Calendar.NOVEMBER,1);
  check(filter.to==c.getTimeInMillis(),"End month includes its entire final day");
  StatsScreen.dateRange(filter,0,months,2,1);check(filter.days==0&&filter.from==0&&filter.to==0,"All dates clears prior range");
  StatsScreen.dateRange(filter,1,months,0,0);check(filter.from==0&&filter.to==0,"Unbounded month endpoints");
  StatsScreen.dateRange(filter,1,months,1,2);check(filter.from>=filter.to,"Reversed months remain detectable");
  check(PickTiers.tier(90,100)==0&&PickTiers.tier(89,100)==1&&PickTiers.tier(75,100)==1&&PickTiers.tier(60,100)==2&&PickTiers.tier(45,100)==3&&PickTiers.tier(30,100)==4&&PickTiers.tier(15,100)==5&&PickTiers.tier(1,100)==6&&PickTiers.tier(0,100)==7,"observed tier boundary counts");
  check(PickTiers.tier(0,0)==8&&PickTiers.tier(101,100)==8&&PickTiers.tier(Double.NaN,1)==8,"invalid denominator or numerator is not a zero rate");
  metrics.put("pick:1:Small",rate("pick:1:Small",1,1,1,"below_min_sample"));
  List<PickTiers.Entry> tiers=PickTiers.entries(metrics,1);check(tiers.size()==6&&tiers.get(tiers.size()-1).tier==8&&tiers.stream().noneMatch(e->e.card.equals("Missing")||e.card.equals("NextAct")),"act scope, missing exclusion and low-sample separation");
  Standardized.Act fit=Standardized.fit(Arrays.asList(new String[]{"A","B","C",Standardized.SKIP},new String[]{"A","B",Standardized.SKIP},new String[]{"B","A",Standardized.SKIP}));
  double total=0;for(double share:fit.share.values())total+=share;
  check(fit.available&&fit.share.size()==4&&Math.abs(total-100)<1e-6,"standardized shares include Skip and sum to 100: "+total);
  check(fit.share.containsKey(Standardized.SKIP)&&fit.share.get(Standardized.SKIP)>0&&fit.share.get("A")>fit.share.get("B"),"Skip is reported with the other options; more wins rank higher: "+fit.share);
  check(!Standardized.fit(Arrays.<String[]>asList(new String[]{"A","B","C",Standardized.SKIP})).available,"one choice screen cannot separate cards");
  check(!Standardized.fit(Arrays.<String[]>asList(new String[]{"A",Standardized.SKIP},new String[]{"SKIP","A"})).available,"one card is not a ranking");
  int[] breaks=PickTiers.standardTiers(new ArrayList<>(Arrays.asList(30.0,20.0,8.0,2.0,0.5,0.1)));
  check(breaks[0]<=breaks[1]&&breaks[1]<=breaks[2]&&breaks[2]<=breaks[3]&&breaks[3]<=4,"top shares fill the Jenks tiers: "+Arrays.toString(breaks));
  check(breaks[4]>=5&&breaks[5]==6,"below 1% splits into R0 and R00 in log order: "+Arrays.toString(breaks));
  int[] equal=PickTiers.standardTiers(new ArrayList<>(Arrays.asList(10.0,10.0,4.0)));check(equal[0]==equal[1]&&equal[0]<equal[2],"equal shares never split tiers: "+Arrays.toString(equal));
  check(StatsScreen.metricGroup("skip:2").equals("pick:2")&&StatsScreen.metricGroup("related:boss:2:Snecko Eye").equals("related:boss:2")&&StatsScreen.actOf("related:boss:2:Snecko Eye").equals("2")&&StatsScreen.actOf("skip:2").equals("2"),"skip/bowl group with their act and boss win rows keep their act");
  check(StatsScreen.actualRow("bowl:1")&&!StatsScreen.actualRow("skip:1")&&!StatsScreen.actualRow("pick:1:Bash"),"only Bowl stays a trailing observed row; Skip is fitted");
  check(StatsScreen.metricGroup("hp_loss:1:Jaw Worm").equals("hp_loss:1")&&StatsScreen.metricGroup("hp_loss_heal:1:Jaw Worm").equals("hp_loss:1")&&StatsScreen.metricGroup("act_hp_loss:1").equals("hp_loss:1")&&StatsScreen.actOf("hp_loss:2:Cultist").equals("2")&&StatsScreen.actOf("act_hp_loss:3").equals("3"),"actual HP loss groups its total and encounter rows under one act");
  check(StatsScreen.metricGroup("damage_act:1:Jaw Worm").equals("damage_act:1")&&StatsScreen.actOf("damage_act:2:Jaw Worm").equals("2")&&StatsScreen.metricGroup("encounter_potions_act:3:Cultist").equals("encounter_potions_act:3")&&StatsScreen.actOf("encounter_potions_act:3:Cultist").equals("3")&&StatsScreen.metricGroup("potions_act:4").equals("potions_act:4")&&StatsScreen.actOf("potions_act:4").equals("4"),"per-act damage and potion rows keep their act");
  check(PickRateChart.kindName(4).equals("hp-loss")&&PickRateChart.normalizeMonster("Jaw Worm").equals("jawworm")&&PickRateChart.encounter("hp_loss_heal:2:Jaw Worm").equals("Jaw Worm"),"HP-loss export kind and local image keys");
  check(PickRateChart.monsterResource("2 Fungi Beasts").equals("/monster-images/2fungibeasts.png")&&PickRateChart.bundledMonster("The Heart")==null&&!PickRateChart.bundledMonsterImages(),"bundled monster images stay optional and fall back cleanly");
  int[] section=PickTiers.naturalTiers(new ArrayList<>(Arrays.asList(40.0,30.0,20.0,10.0,5.0,1.0,0.5)),7);
  check(section[0]==0&&section[6]==6&&section[0]<section[1]&&section[1]<section[2],"section tiers rank high to low: "+Arrays.toString(section));
  int[] tied=PickTiers.naturalTiers(new ArrayList<>(Arrays.asList(10.0,10.0,4.0)),7);check(tied[0]==tied[1]&&tied[0]<tied[2],"section tiers never split equal values: "+Arrays.toString(tied));
  List<PickRateChart.Item> sectionItems=new ArrayList<>();
  PickRateChart.Item high=new PickRateChart.Item("",null,"High","",-1,false,50,"%"),mid=new PickRateChart.Item("",null,"Mid","",-1,false,20,"%"),lowSample=new PickRateChart.Item("",null,"Low","",-1,false,99,"%");
  lowSample.tier=PickRateChart.LOW;sectionItems.add(lowSample);sectionItems.add(high);sectionItems.add(mid);
  PickRateChart.tier(sectionItems);
  check(sectionItems.get(2)==lowSample&&lowSample.tier==PickRateChart.LOW&&high.tier>=0&&high.tier<mid.tier,"below-minimum rows stay out of the tiers and sort last: "+high.tier+"/"+mid.tier+"/"+lowSample.tier);
  Map<String,Metric> csvMetrics=new LinkedHashMap<>();
  csvMetrics.put("related:neow:HUNDRED_GOLD / NONE",rate("related:neow:HUNDRED_GOLD / NONE",1,2,2,"ok"));
  csvMetrics.put("related:boss:1:Sozu",rate("related:boss:1:Sozu",1,2,2,"hidden_by_result_filter"));
  csvMetrics.put("pick:1:Small",rate("pick:1:Small",1,5,1,"below_min_sample"));
  Csv.Table csvTable=new Csv.Table("第1幕 · 测试");
  csvTable.add("1","胜率","T0","100 金币",csvMetrics.get("related:neow:HUNDRED_GOLD / NONE"));
  csvTable.add("1","胜率","","Sozu",csvMetrics.get("related:boss:1:Sozu"));
  csvTable.add("1","一般率","样本不足","Small",csvMetrics.get("pick:1:Small"));
  Csv.Table second=new Csv.Table("第2幕 · 测试");second.addRate("2","一般率","T1","Bash",3,4,4,"ok",0);
  String csv=Csv.body(Arrays.asList(csvTable,second));String[] lines=csv.split("\r\n");
  check(lines[0].equals(String.join(",",Csv.COLUMNS))&&lines[0].startsWith("表,幕,口径,分档,条目,数值,单位,分子,分母,样本,状态"),"csv header is the fixed Chinese column list: "+(lines.length==0?"":lines[0]));
  String[] winCells=lines[1].split(",",-1);check(winCells[4].equals("100 金币")&&winCells[5].equals("50")&&winCells[7].equals("1")&&winCells[8].equals("2")&&!winCells[11].isEmpty()&&!winCells[12].isEmpty(),"csv rate row is Chinese with its Wilson interval: "+lines[1]);
  String[] hiddenCells=lines[2].split(",",-1);check(hiddenCells[7].isEmpty()&&hiddenCells[5].isEmpty()&&hiddenCells[11].isEmpty()&&hiddenCells[10].equals("结果筛选隐藏"),"result-filtered rows leave numbers blank: "+lines[2]);
  String[] lowCells=lines[3].split(",",-1);check(lowCells[7].equals("1")&&lowCells[8].equals("5")&&lowCells[5].isEmpty()&&lowCells[11].isEmpty()&&lowCells[10].equals("样本不足"),"below-min rows keep counts but no value or interval: "+lines[3]);
  check(lines.length==6&&lines[4].isEmpty()&&lines[5].contains("第2幕")&&lines[5].contains("75"),"tables are separated by a blank line and keep only their own rows: "+Arrays.toString(lines));
  Path root=Files.createTempDirectory("sl-native-charset");Path dir=Files.createDirectory(root.resolve("1_IRONCLAD"));
  String gbk="{\"character_chosen\":\"IRONCLAD\",\"play_id\":\"gbk-run\",\"timestamp\":1.0,\"floor_reached\":2,\"victory\":false,\"killed_by\":\"Jaw Worm\",\"score_breakdown\":[\"爬塔的楼层 (2): 10\"]}";
  Files.write(dir.resolve("1.run"),gbk.getBytes(Charset.forName("GBK")));
  String bom="\uFEFF{\"character_chosen\":\"IRONCLAD\",\"play_id\":\"bom-run\",\"timestamp\":2.0,\"floor_reached\":2,\"victory\":false,\"killed_by\":\"Jaw Worm\"}";
  Files.write(dir.resolve("2.run"),bom.getBytes(StandardCharsets.UTF_8));
  Stats.Index nativeIndex=Stats.scan(root,new ArrayList<Ledger>(),new ArrayList<String>());
  check(nativeIndex.nativeFiles==2&&nativeIndex.errors.isEmpty()&&nativeIndex.runs.size()==2,"GBK and UTF-8 BOM native runs parse: "+nativeIndex.errors);
  HistoryIndex.Summary first=new HistoryIndex.Summary();first.id="first";first.saveSlot=2;first.time=10;first.outcome="victory";
  HistoryIndex.Summary other=new HistoryIndex.Summary();other.id="other-slot";other.saveSlot=1;other.time=50;
  HistoryIndex.Summary next=new HistoryIndex.Summary();next.id="next";next.saveSlot=2;next.time=20;next.outcome="death";
  List<HistoryIndex.Summary> summaries=Arrays.asList(other,first,next);
  check(HistoryIndex.latest(summaries,2,false)==next,"latest by time within slot, independent of list order");
  first.current=true;first.outcome="current";check(HistoryIndex.latest(summaries,2,false)==first,"ongoing run takes precedence");
  first.fixture=true;check(HistoryIndex.latest(summaries,2,false)==next,"test data not chosen automatically");
  next.sessionKind="practice";check(HistoryIndex.latest(summaries,2,false)==null,"no normal run has no fallback to another slot or practice");
    System.out.println("StatsDisplayCheck passed");
 }
}
