package spireledger;
import com.google.gson.*;
import java.util.*;
/** Relic Stats (ForgottenArbiter, MIT) relic_stats semantics, derived from its source; pure, no game classes. */
final class RelicStatsTable {
 static final class Part {
  final String key,unit;final Double number;final List<String> items;final boolean perCombat,perTurn;
  Part(String key,String unit,Double number,List<String> items,boolean perCombat,boolean perTurn){this.key=key;this.unit=unit;this.number=number;this.items=items;this.perCombat=perCombat;this.perTurn=perTurn;}
 }
 /** Unknown or malformed relics are missing, never zero. */
 static final class Relic {
  final String id,reason;final List<Part> parts;
  Relic(String id,String reason,List<Part> parts){this.id=id;this.reason=reason;this.parts=parts;}
  boolean missing(){return reason!=null;}
 }
 // Embedded: ~120 short rows, one file, packaged by the existing jar step. id|layout|key:unit[:c][t];...
 // layout S scalar number, L fixed-length number list, T scalar card id, V card id list of any length.
 // c/t: upstream's extended text divides this component by combats/turns since the relic was obtained.
 // unit probability (White Beast Statue buckets) is end-of-run state, not additive across runs.
 private static final String[] ROWS={
  "Ancient Tea Set|L|energy_gained:energy:ct",
  "Art of War|L|energy_gained:energy:ct",
  "Astrolabe|V|cards_obtained:card",
  "Bag of Preparation|L|cards_drawn:cards:ct",
  "Bird Faced Urn|L|hp_healed:hp:ct",
  "Black Blood|S|hp_healed:hp",
  "Black Star|S|extra_relics:relics",
  "Blood Vial|S|hp_healed:hp",
  "Bloody Idol|S|hp_healed:hp",
  "Blue Candle|L|curses_exhausted:cards:ct",
  "Boot|L|damage_added:damage:ct",
  "Bottled Flame|T|card:card",
  "Bottled Lightning|T|card:card",
  "Bottled Tornado|T|card:card",
  "Bronze Scales|L|damage_dealt:damage:ct",
  "Burning Blood|S|hp_healed:hp",
  "Busted Crown|S|card_options_lost:cards",
  "Cables|L|orbs_triggered:orbs:ct;lightning_orbs:orbs;frost_orbs:orbs;dark_orbs:orbs;plasma_orbs:orbs",
  "Calipers|L|block_retained:block:ct",
  "Cauldron|S|potions_picked_up:potions",
  "Centennial Puzzle|L|cards_drawn:cards:ct",
  "CeramicFish|S|gold_earned:gold",
  "Champion Belt|L|weak_applied:stacks:ct",
  "Charon's Ashes|L|damage_dealt:damage:ct",
  "Chemical X|L|x_cost_cards_played:cards:ct",
  "CloakClasp|L|block_gained:block:ct",
  "Cursed Key|S|curses_created:cards",
  "Darkstone Periapt|S|max_hp_gained:hp",
  "Dead Branch|L|cards_generated:cards:ct",
  "Dream Catcher|S|extra_card_rewards:cards",
  "Ectoplasm|S|gold_lost:gold",
  "Emotion Chip|L|orbs_triggered:orbs:ct;lightning_orbs:orbs;frost_orbs:orbs;dark_orbs:orbs;plasma_orbs:orbs",
  "Eternal Feather|S|hp_healed:hp",
  "FaceOfCleric|S|max_hp_gained:hp",
  "FossilizedHelix|L|damage_prevented:damage:ct",
  "Frozen Eye|S|time_spent_viewing_draw_order:seconds",
  "FrozenCore|L|frost_orbs_channeled:orbs:ct",
  "Gambling Chip|L|cards_drawn:cards:ct",
  "Ginger|L|weak_prevented:stacks:ct",
  "Girya|S|times_lifted:times",
  "Golden Idol|S|extra_gold_dropped:gold",
  "GoldenEye|L|extra_cards_scried:cards:ct",
  "Gremlin Horn|L|cards_drawn:cards:c;energy_gained:energy:c",
  "HandDrill|L|vulnerable_applied:stacks:ct",
  "Happy Flower|L|energy_gained:energy:ct",
  "HolyWater|L|energy_gained:energy:ct",
  "HoveringKite|L|energy_gained:energy:ct",
  "Ice Cream|L|energy_retained:energy:ct",
  "InkBottle|L|cards_drawn:cards:ct",
  "Inserter|L|orb_slots_created:orbs:ct",
  "Juzu Bracelet|S|combats_avoided:combats",
  "Kunai|L|dexterity_gained:dexterity:ct",
  "Lee's Waffle|S|hp_healed:hp",
  "Letter Opener|L|damage_dealt:damage:ct",
  "Lizard Tail|S|hp_healed:hp",
  "Magic Flower|L|hp_healed:hp:ct",
  "Mark of the Bloom|S|healing_prevented:hp",
  "Matryoshka|S|charges_used:charges",
  "MawBank|S|gold_earned:gold",
  "MealTicket|S|hp_healed:hp",
  "Meat on the Bone|S|hp_healed:hp",
  "Medical Kit|L|status_cards_exhausted:cards:ct",
  "Melange|L|cards_scried:cards:ct",
  "Membership Card|S|gold_saved:gold",
  "Mercury Hourglass|L|damage_dealt:damage:ct",
  "Mummified Hand|L|energy_discounted:energy:ct",
  "Necronomicon|L|attacks_played_twice:cards:ct",
  "Nilry's Codex|L|common_picked:cards;uncommon_picked:cards;rare_picked:cards;skipped:cards",
  "Nloth's Gift|S|extra_rare_cards:cards",
  "Nunchaku|L|energy_gained:energy:ct",
  "Omamori|S|charges_used:charges",
  "OrangePellets|L|debuffs_cleared:debuffs:ct",
  "Orichalcum|L|block_gained:block:ct",
  "Ornamental Fan|L|block_gained:block:ct",
  "Pandora's Box|V|cards_obtained:card",
  "Pantograph|S|hp_healed:hp",
  "Peace Pipe|S|cards_toked:cards",
  "Pen Nib|L|attacks_doubled:cards:ct",
  "Pocketwatch|L|cards_drawn:cards:ct",
  "Prayer Wheel|S|extra_card_rewards:cards",
  "PreservedInsect|S|hp_removed:hp",
  "PureWater|L|energy_gained:energy:ct",
  "Question Card|S|extra_card_options:cards",
  "Regal Pillow|S|hp_healed:hp",
  "Ring of the Snake|L|cards_drawn:cards:ct",
  "Runic Cube|L|cards_drawn:cards:ct",
  "Self Forming Clay|L|block_gained:block:ct",
  "Shovel|S|relics_dug:relics",
  "Shuriken|L|strength_gained:strength:ct",
  "Singing Bowl|S|max_hp_gained:hp",
  "Smiling Mask|S|gold_saved:gold",
  "Snake Skull|L|poison_applied:poison:ct",
  "Snecko Eye|L|cost0_cards:cards;cost1_cards:cards;cost2_cards:cards;cost3_cards:cards;discount_sum:energy",
  "Sozu|S|potions_lost:potions",
  "SsserpentHead|S|gold_earned:gold",
  "StoneCalendar|L|damage_dealt:damage:ct",
  "Strange Spoon|L|cards_recycled:cards:ct",
  "Sundial|L|energy_gained:energy:ct",
  "The Courier|S|gold_saved:gold",
  "The Specimen|L|relevant_poison_applied:poison:ct",
  "TheAbacus|L|block_gained:block:ct",
  "Tingsha|L|damage_dealt:damage:ct",
  "Tiny Chest|S|chests_created:chests",
  "Torii|S|damage_prevented:damage",
  "Tough Bandages|L|block_gained:block:ct",
  "Toy Ornithopter|S|hp_healed:hp",
  "TungstenRod|S|damage_prevented:damage",
  "Turnip|L|frail_prevented:stacks:ct",
  "Unceasing Top|L|cards_drawn:cards:ct",
  "VioletLotus|L|energy_gained:energy:ct",
  "War Paint|V|cards_upgraded:card",
  "WarpedTongs|L|cards_upgraded:cards:ct",
  "Whetstone|V|cards_upgraded:card",
  "White Beast Statue|L|chance_bucket_0:probability;chance_bucket_1:probability;chance_bucket_2:probability;chance_bucket_3:probability;chance_bucket_4:probability;chance_bucket_5:probability;chance_bucket_6:probability;chance_bucket_7:probability;chance_bucket_8:probability;chance_bucket_9:probability;chance_bucket_10:probability;expected_potions:potions",
  "WingedGreaves|S|charges_used:charges",
  "Yang|L|temp_dexterity_gained:dexterity:ct"
 };
 private static final class Spec{String layout;String[] key,unit;boolean[] c,t;}
 private static final Map<String,Spec> SPECS=new HashMap<>();
 static{
  for(String row:ROWS){
   String[] f=row.split("[|]");String[] cs=f[2].split(";");Spec s=new Spec();s.layout=f[1];int n=cs.length;s.key=new String[n];s.unit=new String[n];s.c=new boolean[n];s.t=new boolean[n];
   for(int i=0;i<n;i++){String[] p=cs[i].split(":");s.key[i]=p[0];s.unit[i]=p[1];String fl=p.length>2?p[2]:"";s.c[i]=fl.indexOf('c')>=0;s.t[i]=fl.indexOf('t')>=0;}
   SPECS.put(f[0],s);
  }
 }
 static boolean known(String relicId){return SPECS.containsKey(relicId);}
 /** Every relic key in relic_stats except counters and obtain_stats. */
 static Map<String,Relic> parse(JsonObject relicStats){
  Map<String,Relic> out=new LinkedHashMap<>();
  if(relicStats==null)return out;
  for(Map.Entry<String,JsonElement> e:relicStats.entrySet()){
   String id=e.getKey();if(id.equals("counters")||id.equals("obtain_stats"))continue;
   Spec s=SPECS.get(id);
   if(s==null){out.put(id,new Relic(id,"unknown_relic",Collections.<Part>emptyList()));continue;}
   List<Part> parts=parts(s,e.getValue());
   out.put(id,parts==null?new Relic(id,"bad_shape",Collections.<Part>emptyList()):new Relic(id,null,parts));
  }
  return out;
 }
 private static List<Part> parts(Spec s,JsonElement v){
  List<Part> out=new ArrayList<>();
  if(v==null)return null;
  switch(s.layout){
   case "S":{Double d=number(v);if(d==null)return null;out.add(num(s,0,d));return out;}
   case "T":{if(!isString(v))return null;String c=v.getAsString();out.add(new Part(s.key[0],s.unit[0],null,c.isEmpty()?Collections.<String>emptyList():Collections.singletonList(c),false,false));return out;}
   case "V":{
    if(!v.isJsonArray())return null;List<String> cards=new ArrayList<>();
    for(JsonElement x:v.getAsJsonArray()){if(!isString(x))return null;cards.add(x.getAsString());}
    out.add(new Part(s.key[0],s.unit[0],null,cards,false,false));return out;
   }
   case "L":{
    if(!v.isJsonArray()||v.getAsJsonArray().size()!=s.key.length)return null;
    for(int i=0;i<s.key.length;i++){Double d=number(v.getAsJsonArray().get(i));if(d==null)return null;out.add(num(s,i,d));}
    return out;
   }
   default:return null;
  }
 }
 private static Part num(Spec s,int i,double d){return new Part(s.key[i],s.unit[i],d,null,s.c[i],s.t[i]);}
 private static boolean isString(JsonElement e){return e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString();}
 private static Double number(JsonElement e){
  if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())return null;
  double d=e.getAsDouble();return Double.isNaN(d)||Double.isInfinite(d)?null:d;
 }
 private static Integer whole(JsonElement e){Double d=e==null?null:number(e);return d==null||d!=Math.rint(d)||d<0||d>Integer.MAX_VALUE?null:(Integer)(int)(double)d;}
 /** {combats, turns} run totals (RelicStats.mod), or null if absent or malformed. */
 static int[] counters(JsonObject relicStats){
  JsonElement c=relicStats==null?null:relicStats.get("counters");
  if(c==null||!c.isJsonArray()||c.getAsJsonArray().size()!=2)return null;
  Integer a=whole(c.getAsJsonArray().get(0)),b=whole(c.getAsJsonArray().get(1));
  return a==null||b==null?null:new int[]{a,b};
 }
 /** relic id to {floor, combats, turns} totals at first pickup (RelicObtainStats), or null if absent or malformed. */
 static Map<String,int[]> obtain(JsonObject relicStats){
  JsonElement o=relicStats==null?null:relicStats.get("obtain_stats");
  if(o==null||!o.isJsonArray()||o.getAsJsonArray().size()!=3)return null;
  JsonArray a=o.getAsJsonArray();
  for(int i=0;i<3;i++)if(!a.get(i).isJsonObject())return null;
  Map<String,int[]> out=new LinkedHashMap<>();
  for(Map.Entry<String,JsonElement> e:a.get(0).getAsJsonObject().entrySet()){
   Integer f=whole(e.getValue()),b=whole(a.get(1).getAsJsonObject().get(e.getKey())),t=whole(a.get(2).getAsJsonObject().get(e.getKey()));
   if(f!=null&&b!=null&&t!=null)out.put(e.getKey(),new int[]{f,b,t});
  }
  return out;
 }
}
