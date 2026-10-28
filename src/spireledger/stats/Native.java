package spireledger.stats;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import static spireledger.stats.Json.*;

/** One vanilla .run (and the RunHistoryPlus fields an old one may carry) as Run facts. */
final class Native {
    static final String N=Fact.NATIVE;
    static final Set<String> ACT1_BOSS=set("The Guardian","Hexaghost","Slime Boss"),ACT2_BOSS=set("Champ","Automaton","Collector"),ACT3_BOSS=set("Awakened One","Time Eater","Donu and Deca"),ACT4=set("Shield and Spear","The Heart");
    static final Pattern SLOT=Pattern.compile("^(\\d+)_");
    /** The Neow constant of TinyCat 4.5.6's gold estimate (Run.getNeowBonusDeduction, fitted on exact goldGained): by neow_bonus, 45 for any other bonus, 0 with the NO_GOLD cost. */
    static final Map<String,Integer> GOLD_DEDUCTION=new HashMap<>();
    static{String[] k={"TWO_FIFTY_GOLD","HUNDRED_GOLD","RANDOM_COMMON_RELIC","REMOVE_CARD","ONE_RARE_RELIC","REMOVE_TWO","TRANSFORM_TWO_CARDS","TRANSFORM_CARD","UPGRADE_CARD","THREE_SMALL_POTIONS","RANDOM_COLORLESS_2","THREE_RARE_CARDS","THREE_ENEMY_KILL"};
        int[] v={55,40,35,40,45,40,55,60,60,45,60,45,40};for(int i=0;i<k.length;i++)GOLD_DEDUCTION.put(k[i],v[i]);}
    static int goldDeduction(JsonObject o){return "NO_GOLD".equals(str(o,"neow_cost"))?0:GOLD_DEDUCTION.getOrDefault(str(o,"neow_bonus"),45);}
    static Set<String> set(String... s){return new HashSet<>(Arrays.asList(s));}
    static Fact<String> text(JsonObject o,String k){String v=str(o,k);return v.isEmpty()?Fact.missing(N):Fact.of(v,N);}

    /** Act boundaries from boss floors (metrics.md 6.4): first act-1 and act-2 boss, last act-3 boss; 17/34/52 without them. */
    static int[] bounds(JsonArray fights){int b1=Integer.MAX_VALUE,b2=Integer.MAX_VALUE,b3=-1;
        for(JsonElement e:fights)if(e.isJsonObject()){String name=str(e.getAsJsonObject(),"enemies");Integer floor=integer(e.getAsJsonObject(),"floor");if(floor==null)continue;
            if(ACT1_BOSS.contains(name))b1=Math.min(b1,floor);if(ACT2_BOSS.contains(name))b2=Math.min(b2,floor);if(ACT3_BOSS.contains(name))b3=Math.max(b3,floor);}
        // The boss chest floor right after the act-1 and act-2 boss still belongs to that act (user decision, metrics.md 6.4).
        return new int[]{b1==Integer.MAX_VALUE?17:b1+1,b2==Integer.MAX_VALUE?34:b2+1,b3<0?52:b3};}
    static int act(int[] b,int floor){return floor<=b[0]?1:floor<=b[1]?2:floor<=b[2]?3:4;}

    static Run read(JsonObject o,String dir,String file){
        Run r=new Run();r.sources.add(N);r.playId=str(o,"play_id");r.nativeFile=dir+"/"+file;r.id=r.playId.isEmpty()?"native-file:"+r.nativeFile:r.playId;
        r.character=text(o,"character_chosen");r.seed=text(o,"seed_played");r.ascension=Fact.of(integer(o,"ascension_level"),N);r.floorReached=Fact.of(integer(o,"floor_reached"),N);
        Double stamp=number(o,"timestamp");r.time=stamp==null?Fact.missing(N):Fact.of((long)(stamp*1000),N);r.playtime=Fact.amount(number(o,"playtime"),N);
        r.fixture="test_fixture".equals(str(o,"dataset_origin"));r.practice="practice".equals(str(o,"session_kind"));
        r.daily=Fact.of(bool(o,"is_daily"),N);r.endless=Fact.of(bool(o,"is_endless"),N);r.trial=Fact.of(bool(o,"is_trial"),N);r.seeded=Fact.of(bool(o,"chose_seed"),N);
        // The save slot is only in the directory name (2_IRONCLAD); slot 0 has no prefix.
        Matcher slot=SLOT.matcher(dir);r.slot=Fact.of(slot.find()?Integer.parseInt(slot.group(1)):0,"native_directory");

        Boolean victory=bool(o,"victory");String killed=str(o,"killed_by");
        boolean abandoned=Boolean.TRUE.equals(bool(o,"abandoned"))||Boolean.TRUE.equals(bool(o,"is_abandoned"))||killed.equalsIgnoreCase("Abandoned");
        // proposed default (metrics.md 9.1): vanilla writes .run only when a run ends, so a loss without killed_by is an abandon.
        r.outcome=Fact.of(abandoned?"abandon":victory==null?null:victory?"victory":killed.isEmpty()?"abandon":"death",N);if(!killed.isEmpty()&&!abandoned)r.killedBy=Fact.of(killed,N);
        String outcome=r.outcome.value;Integer last=r.floorReached.value;

        JsonArray fights=arr(o,"damage_taken"),path=arr(o,"path_per_floor");int[] b=bounds(fights);boolean hasFights=isArray(o,"damage_taken"),act4=false;Set<Integer> act3Wins=new HashSet<>();
        List<Run.Combat> combats=new ArrayList<>();
        for(JsonElement e:fights)if(e.isJsonObject()){JsonObject f=e.getAsJsonObject();String name=str(f,"enemies");Integer floor=integer(f,"floor");if(name.isEmpty()||floor==null||floor<1)continue;
            JsonElement room=floor-1<path.size()?path.get(floor-1):null;if(room!=null&&room.isJsonNull())continue; // boss chest and reward floors hold no fight
            Double damage=number(f,"damage");if(damage!=null&&damage>=99999)damage-=99999; // proposed (metrics.md 6.4): TinyCat's death-fight adjustment
            combats.add(new Run.Combat(floor,act(b,floor),name,room!=null&&room.isJsonPrimitive()?room.getAsString():"",damage));
            act4|=ACT4.contains(name);boolean died="death".equals(outcome)&&floor.equals(last)&&name.equals(killed);if(ACT3_BOSS.contains(name)&&!died)act3Wins.add(floor);}
        r.combats=hasFights?Fact.of(combats,N):Fact.missing(N);
        // A victory without act-4 fights ended in act 3 (no keys), though floor_reached counts the victory room.
        if(last!=null)r.maxAct=Fact.of(Boolean.TRUE.equals(victory)&&!act4?Math.min(3,act(b,last)):Math.max(1,act(b,last)),N);
        // m1-field-gaps 7: victory, an act-4 fight, or enough distinct act-3 boss wins (two at A20); any one clears act 3.
        if(Boolean.TRUE.equals(victory)||hasFights&&(act4||act3Wins.size()>=(r.ascension.or(0)>=20?2:1)))r.act3=Fact.of(true,N);else if(hasFights&&victory!=null)r.act3=Fact.of(false,N);
        if(abandoned||Boolean.FALSE.equals(victory))r.heart=Fact.of(false,N);else if(Boolean.TRUE.equals(victory)&&hasFights){boolean heart=false;for(Run.Combat c:combats)heart|=c.encounter.equals("The Heart");r.heart=Fact.of(heart,N);}

        if(isArray(o,"current_hp_per_floor")){List<Integer> hp=new ArrayList<>();for(JsonElement e:arr(o,"current_hp_per_floor"))hp.add(integer(e));r.hpPerFloor=Fact.of(hp,N);}
        if(isArray(o,"master_deck")){Set<String> deck=new LinkedHashSet<>();JsonArray cards=arr(o,"master_deck");for(JsonElement e:cards)if(e.isJsonPrimitive())deck.add(cardId(e.getAsString()));r.deck=Fact.of(deck,N);r.deckSize=Fact.amount(cards.size(),N);}
        if(!str(o,"neow_bonus").isEmpty()){String picked=str(o,"neow_bonus")+" / "+str(o,"neow_cost");r.neow=Fact.of(picked,N);
            // Every option on the Neow screen: the picked one plus the skipped log (TinyCat's choice block).
            List<String> bonuses=strings(arr(o,"neow_bonuses_skipped_log")),costs=strings(arr(o,"neow_costs_skipped_log"));
            if(!bonuses.isEmpty()){List<String> offers=new ArrayList<>();offers.add(picked);Set<String> seen=new HashSet<>();seen.add(picked);
                for(int i=0;i<bonuses.size();i++){String key=bonuses.get(i)+" / "+(i<costs.size()?costs.get(i):"NONE");if(seen.add(key))offers.add(key);}
                r.neowOffers=Fact.of(offers,N);}}
        r.gold=goldEstimate(o);r.score=Fact.of(integer(o,"score"),N);
        // TinyCat's purge families; a field the file does not carry stays out, so an all-absent record is missing, not zero.
        if(isArray(o,"items_purged")||isArray(o,"event_choices")||isArray(o,"campfire_choices")||isArray(o,"relics")||o.get("neow_bonus_log")!=null){
            int shop=arr(o,"items_purged").size(),event=0,neow=0,campfire=0,cage=strings(arr(o,"relics")).contains("Empty Cage")?2:0;
            for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject())event+=strings(arr(e.getAsJsonObject(),"cards_removed")).size();
            if(o.get("neow_bonus_log")!=null&&o.get("neow_bonus_log").isJsonObject())neow=strings(arr(obj(o,"neow_bonus_log"),"cards_removed")).size();
            for(JsonElement e:arr(o,"campfire_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();if("PURGE".equals(str(c,"key"))&&!str(c,"data").isEmpty())campfire++;}
            Map<String,Integer> purges=new LinkedHashMap<>();purges.put("shop",shop);purges.put("event",event);purges.put("neow",neow);purges.put("campfire",campfire);purges.put("empty_cage",cage);purges.put("total",shop+event+neow+campfire+cage);
            r.purges=Fact.of(purges,N);
        }

        // Card rewards: only floors with a fight are post-combat screens; Neow and event card choices share the field.
        if(isArray(o,"card_choices")&&hasFights){Set<Integer> fightFloors=new HashSet<>();for(Run.Combat c:combats)fightFloors.add(c.floor);List<Run.Screen> screens=new ArrayList<>();
            for(JsonElement e:arr(o,"card_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");String picked=str(c,"picked");if(floor==null||!fightFloors.contains(floor)||picked.isEmpty())continue;
                String kind=picked.equals("SKIP")?"skip":picked.equals("Singing Bowl")?"bowl":"card";Set<String> offered=new LinkedHashSet<>();for(String id:strings(arr(c,"not_picked")))offered.add(cardId(id));if(kind.equals("card"))offered.add(cardId(picked));
                // .run never says whether the bowl was on offer, so these screens stay out of the bowl rate.
                screens.add(new Run.Screen(act(b,floor),floor,offered,kind,kind.equals("card")?cardId(picked):"","unknown",N,null));}
            r.screens=Fact.of(screens,N);}
        if(isArray(o,"boss_relics")){List<Run.BossOffer> offers=new ArrayList<>();for(JsonElement e:arr(o,"boss_relics")){if(!e.isJsonObject()){offers.add(null);continue;}JsonObject c=e.getAsJsonObject();String picked=str(c,"picked");
                Set<String> offered=new LinkedHashSet<>(strings(arr(c,"not_picked")));if(!picked.isEmpty())offered.add(picked);offered.add("SKIP");offers.add(new Run.BossOffer(offered,picked.isEmpty()?"SKIP":picked));}
            r.boss=Fact.of(offers,N);}

        if(isArray(o,"event_choices")){Map<String,Integer> events=new LinkedHashMap<>();Map<Integer,Double> hurt=new TreeMap<>();
            for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();events.merge("event:"+str(c,"event_name")+" / "+str(c,"player_choice"),1,Integer::sum);
                for(String id:strings(arr(c,"cards_removed")))events.merge("remove:event:"+cardId(id),1,Integer::sum);for(String id:strings(arr(c,"cards_upgraded")))events.merge("upgrade:event:"+cardId(id),1,Integer::sum);
                Double d=number(c,"damage_taken");Integer floor=integer(c,"floor");if(d!=null&&d>0&&floor!=null)hurt.merge(act(b,floor),d,Double::sum);}
            r.counts.put("events",Fact.of(events,N));r.eventDamage=Fact.of(hurt,N);}
        if(isArray(o,"items_purchased")){Map<String,Integer> m=new LinkedHashMap<>();List<String> items=strings(arr(o,"items_purchased"));m.put("purchases",arr(o,"items_purchased").size());for(String id:items)m.merge("purchase:"+item(id),1,Integer::sum);r.counts.put("purchases",Fact.of(m,N));}
        if(isArray(o,"items_purged")){Map<String,Integer> m=new LinkedHashMap<>();m.put("purges",arr(o,"items_purged").size());for(String id:strings(arr(o,"items_purged")))m.merge("remove:shop:"+cardId(id),1,Integer::sum);r.counts.put("purges",Fact.of(m,N));}
        if(isArray(o,"campfire_choices")){Map<String,Integer> m=new LinkedHashMap<>();for(JsonElement e:arr(o,"campfire_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();String key=str(c,"key"),id=cardId(str(c,"data"));m.merge("campfire:"+key,1,Integer::sum);
                if(key.equals("SMITH")&&!id.isEmpty())m.merge("upgrade:campfire:"+id,1,Integer::sum);if(key.equals("PURGE")&&!id.isEmpty())m.merge("remove:campfire:"+id,1,Integer::sum);}
            r.counts.put("campfire",Fact.of(m,N));r.campfires=Campfire.build(o,r.character.or(""),b,r.maxAct.known()&&r.maxAct.value>=4);}
        // Explicit use records only, never inferred from slot changes; each entry of potions_floor_usage is one use on that floor.
        if(isArray(o,"potions_floor_usage")){Map<String,Integer> m=new LinkedHashMap<>();Map<Integer,Integer> byFloor=new HashMap<>();JsonArray uses=arr(o,"potions_floor_usage");m.put("potions",uses.size());for(JsonElement e:uses){Integer floor=integer(e);if(floor!=null)byFloor.merge(floor,1,Integer::sum);}
            r.counts.put("potions",Fact.of(m,N));r.potionsByFloor=Fact.of(byFloor,N);}
        else if(isArray(o,"potion_use_per_floor")){Map<Integer,Integer> byFloor=new HashMap<>();JsonArray uses=arr(o,"potion_use_per_floor");for(int i=0;i<uses.size();i++)if(uses.get(i).isJsonArray())byFloor.put(i+1,uses.get(i).getAsJsonArray().size());r.potionsByFloor=Fact.of(byFloor,Fact.RHP);}
        r.counts.put(ACTS,Fact.of(acts(o,b),N));return r;
    }

    /** Count family of act-split occurrences: "&lt;act&gt;|&lt;key&gt;", act 0 when the floor is unknown; "has|&lt;field&gt;" marks each field present, so an absent one stays missing. */
    static final String ACTS="acts";
    static Map<String,Integer> acts(JsonObject o,int[] b){Map<String,Integer> m=new TreeMap<>();
        for(String field:Arrays.asList("event_choices","campfire_choices","items_purchased","items_purged","relics","path_per_floor"))if(isArray(o,field))m.put("has|"+field,1);
        for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");at(m,b,floor,"event:"+str(c,"event_name"),1);at(m,b,floor,"purge:EVENT",strings(arr(c,"cards_removed")).size());}
        for(JsonElement e:arr(o,"campfire_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");String key=str(c,"key"),id=cardId(str(c,"data"));if(key.isEmpty())continue;at(m,b,floor,"campfire:"+key,1);
            if(key.equals("SMITH")&&!id.isEmpty())at(m,b,floor,"upgrade:campfire:"+id,1);if(key.equals("PURGE")&&!id.isEmpty())at(m,b,floor,"purge:CAMPFIRE",1);}
        List<String> bought=strings(arr(o,"items_purchased"));JsonArray boughtAt=arr(o,"item_purchase_floors");for(int i=0;i<bought.size();i++)at(m,b,i<boughtAt.size()?integer(boughtAt.get(i)):null,"purchase:"+item(bought.get(i)),1);
        int purged=arr(o,"items_purged").size();JsonArray purgedAt=arr(o,"items_purged_floors");for(int i=0;i<purged;i++)at(m,b,i<purgedAt.size()?integer(purgedAt.get(i)):null,"purge:SHOP",1);
        // RunHistoryPlus only; vanilla has no Neow removal record (metrics.md 6.5).
        if(o.get("neow_bonus_log")!=null&&o.get("neow_bonus_log").isJsonObject()){JsonObject neow=obj(o,"neow_bonus_log");m.put("has|neow_bonus_log",1);at(m,b,null,"purge:NEOW",strings(arr(neow,"cards_removed")).size());}
        // Empty Cage removes two cards that .run never lists: +2, estimated, in the act whose boss relic it was.
        if(strings(arr(o,"relics")).contains("Empty Cage")){int act=0;JsonArray boss=arr(o,"boss_relics");for(int i=0;i<boss.size();i++)if(boss.get(i).isJsonObject()&&"Empty Cage".equals(str(boss.get(i).getAsJsonObject(),"picked")))act=i+1;m.merge(act+"|purge:EMPTY_CAGE",2,Integer::sum);}
        JsonArray path=arr(o,"path_per_floor");for(int i=0;i<path.size();i++)if(path.get(i).isJsonPrimitive()&&"$".equals(path.get(i).getAsString()))at(m,b,i+1,"shop_visit",1);
        return m;}
    static void at(Map<String,Integer> m,int[] b,Integer floor,String key,int n){if(n>0)m.merge((floor==null?0:act(b,floor))+"|"+key,n,Integer::sum);}
    /** Shop item ID: potions are bought under the ID or the display name, so "Swift Potion" becomes SwiftPotion (metrics.md 6.5); cards lose +N. */
    static String item(String id){return id.endsWith(" Potion")?id.replace(" ",""):cardId(id);}

    /** metrics.md 5.2 estimate, TinyCat's formula: first floor's gold less the Neow constant (clamped at 0, as TinyCat), plus later gains, plus 12 with Maw Bank. */
    static Fact<Double> goldEstimate(JsonObject o){JsonArray g=arr(o,"gold_per_floor");if(g.size()==0)return Fact.missing(N);double total=0;Integer prev=null;int start=goldDeduction(o);
        for(JsonElement e:g){Integer v=integer(e);if(v==null)return Fact.missing(N);
            total+=prev==null?Math.max(0,v-start):Math.max(0,v-prev);prev=v;}
        if(strings(arr(o,"relics")).contains("MawBank"))total+=12;return Fact.estimated(total,N);}
}
