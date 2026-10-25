package spireledger;
import com.google.gson.*;
import java.util.*;
public final class RelicStatsCheck {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JsonObject parse(String json){return new JsonParser().parse(json).getAsJsonObject();}
 public static void main(String[] args){
  Map<String,RelicStatsTable.Relic> r=RelicStatsTable.parse(parse("{\"Burning Blood\":177,\"Orichalcum\":[18],\"Gremlin Horn\":[3,2],\"Cables\":[86,26,21,39,0],\"Frozen Eye\":26.5,"
   +"\"Bottled Flame\":\"Core Surge\",\"Bottled Tornado\":\"\",\"Whetstone\":[],\"War Paint\":[\"Defend_R\",\"Armaments\"],\"Snecko Eye\":[146,119,143,147,-136],"
   +"\"White Beast Statue\":[0,0,0,0.4,0,0.6,0,0,0,0,0,10.5],\"counters\":[18,85],\"obtain_stats\":[{},{},{}]}"));
  check(r.size()==11&&!r.containsKey("counters")&&!r.containsKey("obtain_stats"),"counters and obtain_stats are not relics");
  RelicStatsTable.Relic bb=r.get("Burning Blood");check(!bb.missing()&&bb.parts.size()==1&&bb.parts.get(0).key.equals("hp_healed")&&bb.parts.get(0).unit.equals("hp")&&bb.parts.get(0).number==177&&!bb.parts.get(0).perCombat&&!bb.parts.get(0).perTurn,"scalar");
  RelicStatsTable.Part o=r.get("Orichalcum").parts.get(0);check(o.key.equals("block_gained")&&o.number==18&&o.perCombat&&o.perTurn,"list1 combat relic");
  List<RelicStatsTable.Part> horn=r.get("Gremlin Horn").parts;check(horn.size()==2&&horn.get(1).key.equals("energy_gained")&&horn.get(1).perCombat&&!horn.get(1).perTurn,"per-combat only");
  List<RelicStatsTable.Part> cab=r.get("Cables").parts;check(cab.size()==5&&cab.get(0).key.equals("orbs_triggered")&&cab.get(0).perTurn&&cab.get(1).key.equals("lightning_orbs")&&cab.get(1).number==26&&!cab.get(1).perCombat&&cab.get(4).number==0,"list5 orbs");
  check(r.get("Frozen Eye").parts.get(0).number==26.5&&r.get("Frozen Eye").parts.get(0).unit.equals("seconds"),"float");
  check(r.get("Bottled Flame").parts.get(0).items.equals(Collections.singletonList("Core Surge"))&&r.get("Bottled Flame").parts.get(0).number==null,"string");
  check(!r.get("Bottled Tornado").missing()&&r.get("Bottled Tornado").parts.get(0).items.isEmpty(),"empty bottle is a value, not missing");
  check(!r.get("Whetstone").missing()&&r.get("Whetstone").parts.get(0).items.isEmpty(),"list0");
  check(r.get("War Paint").parts.get(0).items.equals(Arrays.asList("Defend_R","Armaments")),"card list");
  List<RelicStatsTable.Part> sn=r.get("Snecko Eye").parts;check(sn.size()==5&&sn.get(4).key.equals("discount_sum")&&sn.get(4).number==-136,"negative discount kept");
  List<RelicStatsTable.Part> wb=r.get("White Beast Statue").parts;check(wb.size()==12&&wb.get(3).unit.equals("probability")&&wb.get(11).key.equals("expected_potions")&&wb.get(11).number==10.5,"list12");

  Map<String,RelicStatsTable.Relic> bad=RelicStatsTable.parse(parse("{\"Modded Relic\":3,\"Burning Blood\":[177],\"Orichalcum\":18,\"Gremlin Horn\":[1],\"Cables\":[1,2,3,4,\"x\"],\"Bottled Flame\":5,\"War Paint\":[\"a\",1],"
   +"\"Boot\":\"8\",\"Pen Nib\":null,\"Shuriken\":[[1]],\"Kunai\":[true]}"));
  check(bad.get("Modded Relic").missing()&&bad.get("Modded Relic").reason.equals("unknown_relic")&&bad.get("Modded Relic").parts.isEmpty(),"unknown relic is missing");
  for(String k:new String[]{"Burning Blood","Orichalcum","Gremlin Horn","Cables","Bottled Flame","War Paint","Boot","Pen Nib","Shuriken","Kunai"})check(bad.get(k).missing()&&bad.get(k).reason.equals("bad_shape")&&bad.get(k).parts.isEmpty(),"bad shape "+k);
  check(RelicStatsTable.parse(null).isEmpty()&&RelicStatsTable.parse(new JsonObject()).isEmpty(),"absent relic_stats");
  check(RelicStatsTable.known("Cables")&&RelicStatsTable.known("The Courier")&&!RelicStatsTable.known("Modded Relic"),"known");

  JsonObject rs=parse("{\"counters\":[18,85],\"obtain_stats\":[{\"Pear\":13,\"Boot\":0},{\"Pear\":4,\"Boot\":0},{\"Pear\":30,\"Boot\":0}]}");
  check(Arrays.equals(RelicStatsTable.counters(rs),new int[]{18,85}),"counters are combats then turns");
  Map<String,int[]> ob=RelicStatsTable.obtain(rs);check(ob.size()==2&&Arrays.equals(ob.get("Pear"),new int[]{13,4,30})&&Arrays.equals(ob.get("Boot"),new int[3]),"obtain floor, combats, turns");
  check(RelicStatsTable.counters(parse("{}"))==null&&RelicStatsTable.counters(parse("{\"counters\":[1]}"))==null&&RelicStatsTable.counters(parse("{\"counters\":[1,\"x\"]}"))==null&&RelicStatsTable.counters(parse("{\"counters\":[1.5,2]}"))==null&&RelicStatsTable.counters(parse("{\"counters\":[-1,2]}"))==null,"bad counters");
  check(RelicStatsTable.obtain(parse("{}"))==null&&RelicStatsTable.obtain(parse("{\"obtain_stats\":[{},{}]}"))==null&&RelicStatsTable.obtain(parse("{\"obtain_stats\":[{},{},3]}"))==null,"bad obtain");
  Map<String,int[]> part=RelicStatsTable.obtain(parse("{\"obtain_stats\":[{\"A\":1,\"B\":2,\"C\":\"x\"},{\"A\":1,\"C\":1},{\"A\":1,\"B\":2,\"C\":1}]}"));
  check(part.size()==1&&part.containsKey("A"),"relic without all three entries is dropped");
  System.out.println("RelicStatsCheck PASS: value shapes, unknown and malformed as missing, counters and obtain_stats");
 }
}
