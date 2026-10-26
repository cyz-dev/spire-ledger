package spireledger;

import com.google.gson.Gson;
import java.util.*;
import spireledger.stats.*;

/** Display ordering and absolute month boundaries without starting the game. */
public final class StatsDisplayCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Metric rate(String key,double n,double d,int samples,String status){Metric m=new Gson().fromJson("{\"key\":\""+key+"\",\"kind\":\"rate\",\"status\":\""+status+"\"}",Metric.class);m.numerator=n;m.denominator=d;m.samples=samples;return m;}
 public static void main(String[] args){
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
