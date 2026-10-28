package spireledger.stats;

import com.google.gson.*;
import java.util.*;
import static spireledger.stats.Json.*;

/**
 * Rest-site options rebuilt from one native .run (TinyCat's campfire opportunity model).
 *
 * <p>A native record only says which action was taken, never which options were on screen. The screen is
 * reconstructed by replaying the deck by floor: starting deck plus card rewards, shop buys, events, Neow,
 * removals and upgrades, with Pandora/Astrolabe-style boss relics reconciled against master_deck. Rest is
 * on screen unless Coffee Dripper is owned, smith needs an unupgraded card and no Fusion Hammer, Recall
 * needs act 1-3 and no earlier Recall, and the three relic actions need the relic at that floor.</p>
 *
 * <p>TinyCat's pool counts each unupgraded card kind as its own slot, never "smith" as one slot: the pool
 * is the cards on the smith screen plus available Recall/Purge/Lift/Dig, without Rest. The multiple divides
 * the observed action share by the random share of that pool. A Recall taken in act 3 to reach act 4 is the
 * forced red key, recorded so the multiple can leave it out.</p>
 */
final class Campfire {
    static final String[] KEYS={"REST","SMITH","RECALL","PURGE","LIFT","DIG"};
    static final Set<String> IMMUNE=new HashSet<>(Arrays.asList("AscendersBane","Ascender's Bane","Clumsy","CurseOfTheBell","Curse of the Bell","Decay","Doubt","Injury","Necronomicurse","Normality","Pain","Parasite","Pride","Regret","Shame","Void","Writhe","Burn","Dazed","Slimed","Wound"));
    static final Set<String> RELICS=new HashSet<>(Arrays.asList("Coffee Dripper","Fusion Hammer","Peace Pipe","Girya","Shovel"));
    // Empty Cage removes two cards .run never names; the surplus step takes them out at its boss floor (TinyCat leaves them in).
    static final Set<String> SUPPLEMENT=new HashSet<>(Arrays.asList("Pandora's Box","Astrolabe","Tiny House","Orrery","Calling Bell","Empty Cage"));
    static final int ADD=0,REMOVE=1,UPGRADE=2,PANDORA=3,DEFICIT=4,SURPLUS=5,CAMP=6;
    static final int DECK=0,SITE=1,SUPPLEMENT_ORDER=2,SURPLUS_ORDER=3;

    /** One deck change; SUPPLEMENT_ORDER deficit/surplus only run when a card-changing boss relic was taken. */
    static final class Event implements Comparable<Event> {
        final int floor,order,seq,kind;final String card,key;final boolean upgraded;final Map<String,Integer> cards;
        Event(int floor,int order,int seq,int kind,String card,String key,boolean upgraded,Map<String,Integer> cards){this.floor=floor;this.order=order;this.seq=seq;this.kind=kind;this.card=card;this.key=key;this.upgraded=upgraded;this.cards=cards;}
        public int compareTo(Event e){if(floor!=e.floor)return floor<e.floor?-1:1;if(order!=e.order)return order<e.order?-1:1;return seq<e.seq?-1:seq>e.seq?1:0;}
    }

    /** Unupgraded/upgraded counts per base card id; Searing Blow can always be upgraded again. */
    static final class Deck {
        final Map<String,int[]> cards=new LinkedHashMap<>();
        void add(String id,int count,boolean upgraded){if(id==null||id.isEmpty())return;int[] v=cards.get(id);if(v==null){v=new int[2];cards.put(id,v);}v[0]+=count;if(upgraded)v[1]+=count;}
        void remove(String id){int[] v=id==null?null:cards.get(id);if(v==null)return;if(--v[0]<=0)cards.remove(id);else if(v[1]>v[0])v[1]=v[0];}
        void removeAll(String id){if(id!=null)cards.remove(id);}
        void upgrade(String id){int[] v=id==null?null:cards.get(id);if(v!=null&&v[1]<v[0])v[1]++;}
        int copies(String id){int[] v=id==null?null:cards.get(id);return v==null?0:v[0];}
        int unupgraded(String id){int[] v=id==null?null:cards.get(id);if(v==null)return 0;return "Searing Blow".equals(id)?v[0]:v[0]-v[1];}
        /** The card kinds the smith screen offers: one row per name, however many unupgraded copies; immune curses never appear. */
        List<String> upgradableIds(){List<String> out=new ArrayList<>();for(String id:cards.keySet())if(!IMMUNE.contains(id)&&unupgraded(id)>0)out.add(id);return out;}
    }

    static Fact<List<Run.Campfire>> build(JsonObject o,String character,int[] bounds,boolean reachedAct4){
        Deck start=initial(character);if(start==null)return Fact.missing(Fact.NATIVE);
        if(!isArray(o,"campfire_choices"))return Fact.missing(Fact.NATIVE);
        Set<String> known=known(o);
        Map<String,Integer> relicFloors=relicFloors(o);
        List<Event> timeline=timeline(o,known,relicFloors);
        int seq=timeline.size();

        // Pass A: where the replay ends up, then reconcile the cards that .run never names card by card.
        Deck replay=start;
        for(Event e:timeline)apply(replay,e);
        Map<String,Integer> finalCounts=finalCounts(o);
        Map<String,Integer> deficit=new LinkedHashMap<>(),surplus=new LinkedHashMap<>();
        for(Map.Entry<String,Integer> e:finalCounts.entrySet()){int have=replay.copies(e.getKey());if(e.getValue()>have)deficit.put(e.getKey(),e.getValue()-have);}
        for(String id:replay.cards.keySet()){Integer actual=finalCounts.get(id);int kept=actual==null?0:actual;int have=replay.copies(id);if(have>kept)surplus.put(id,have-kept);}
        int supplement=supplementFloor(o);
        if(supplement>0&&!deficit.isEmpty())timeline.add(new Event(supplement,SUPPLEMENT_ORDER,seq++,DEFICIT,"","",astrolabe(o),deficit));
        if(supplement>0&&!surplus.isEmpty())timeline.add(new Event(supplement,SURPLUS_ORDER,seq++,SURPLUS,"","",false,surplus));
        Collections.sort(timeline);

        // Pass B: evaluate every rest site before applying its own choice (a fresh deck: pass A mutated its own).
        Deck deck=initial(character);List<Run.Campfire> out=new ArrayList<>();boolean recallUsed=false;int lifts=0;
        for(Event e:timeline){
            if(e.kind!=CAMP){apply(deck,e);continue;}
            int act=Native.act(bounds,e.floor);int option=index(e.key);
            List<String> candidates=deck.upgradableIds();
            boolean rest=!owns(relicFloors,"Coffee Dripper",e.floor);
            boolean smith=!owns(relicFloors,"Fusion Hammer",e.floor)&&!candidates.isEmpty();
            boolean recall=act<=3&&!recallUsed;
            boolean purge=owns(relicFloors,"Peace Pipe",e.floor);
            boolean lift=lifts<3&&owns(relicFloors,"Girya",e.floor);
            boolean dig=owns(relicFloors,"Shovel",e.floor);
            // A relic floor missed by the record must not make the chosen option look unavailable.
            if(option==0)rest=true;else if(option==1)smith=true;else if(option==2)recall=true;else if(option==3)purge=true;else if(option==4)lift=true;else if(option==5)dig=true;
            // The smith screen lists every unupgraded card kind, so it takes one pool slot per card; smith itself is not a slot.
            int pool=(smith?candidates.size():0)+(recall?1:0)+(purge?1:0)+(lift?1:0)+(dig?1:0);
            String target=option==1?cardId(e.card):"";
            boolean smithUsable=option==1&&!target.isEmpty()&&deck.unupgraded(target)>0;
            // Taking Recall in act 3 to reach act 4 buys the red key: it is a cost, not a preference, so the multiple leaves it out.
            boolean forcedRecall=option==2&&act==3&&reachedAct4;
            out.add(new Run.Campfire(act,e.floor,e.key,target,candidates,pool,rest,smith,recall,purge,lift,dig,smithUsable,forcedRecall));
            String id=cardId(e.card);
            if(option==1)deck.upgrade(id);else if(option==3)deck.remove(id);else if(option==4)lifts++;else if(option==2)recallUsed=true;
        }
        return Fact.of(out,Fact.NATIVE);
    }

    static void apply(Deck deck,Event e){
        switch(e.kind){case ADD:deck.add(e.card,1,e.upgraded);break;case REMOVE:deck.remove(e.card);break;case UPGRADE:deck.upgrade(e.card);break;
            case PANDORA:deck.removeAll(e.card);break;
            case DEFICIT:for(Map.Entry<String,Integer> c:e.cards.entrySet())deck.add(c.getKey(),c.getValue(),e.upgraded);break;
            case SURPLUS:for(Map.Entry<String,Integer> c:e.cards.entrySet())for(int i=0;i<c.getValue();i++)deck.remove(c.getKey());break;
            case CAMP:if("SMITH".equals(e.key))deck.upgrade(cardId(e.card));else if("PURGE".equals(e.key))deck.remove(cardId(e.card));break;default:break;}
    }

    static Deck initial(String character){
        Deck d=new Deck();
        if("IRONCLAD".equals(character)){d.add("Strike_R",5,false);d.add("Defend_R",4,false);d.add("Bash",1,false);}
        else if("THE_SILENT".equals(character)){d.add("Strike_G",5,false);d.add("Defend_G",5,false);d.add("Neutralize",1,false);d.add("Survivor",1,false);}
        else if("DEFECT".equals(character)){d.add("Strike_B",4,false);d.add("Defend_B",4,false);d.add("Zap",1,false);d.add("Dualcast",1,false);}
        else if("WATCHER".equals(character)){d.add("Strike_P",4,false);d.add("Defend_P",4,false);d.add("Eruption",1,false);d.add("Vigilance",1,false);}
        else return null;
        return d;
    }

    static List<Event> timeline(JsonObject o,Set<String> known,Map<String,Integer> relicFloors){
        List<Event> out=new ArrayList<>();int seq=0;
        for(JsonElement e:arr(o,"card_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");String picked=str(c,"picked");
            if(floor==null||picked.isEmpty()||picked.equals("SKIP")||picked.equals("Singing Bowl"))continue;
            out.add(new Event(floor,DECK,seq++,ADD,cardId(picked),"",picked.contains("+"),null));}
        List<String> bought=strings(arr(o,"items_purchased"));JsonArray boughtAt=arr(o,"item_purchase_floors");
        for(int i=0;i<bought.size();i++){String id=cardId(bought.get(i));if(!known.contains(id))continue;Integer at=i<boughtAt.size()?integer(boughtAt.get(i)):null;
            out.add(new Event(at==null?1:at,DECK,seq++,ADD,id,"",bought.get(i).contains("+"),null));}
        String[] purged=strings(arr(o,"items_purged")).toArray(new String[0]);JsonArray purgedAt=arr(o,"items_purged_floors");
        for(int i=0;i<purged.length;i++){Integer at=i<purgedAt.size()?integer(purgedAt.get(i)):null;out.add(new Event(at==null?1:at,DECK,seq++,REMOVE,cardId(purged[i]),"",false,null));}
        for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");
            if(floor==null)continue;
            for(String id:strings(arr(c,"cards_obtained")))out.add(new Event(floor,DECK,seq++,ADD,cardId(id),"",id.contains("+"),null));
            for(String id:strings(arr(c,"cards_removed")))out.add(new Event(floor,DECK,seq++,REMOVE,cardId(id),"",false,null));
            for(String id:strings(arr(c,"cards_transformed")))out.add(new Event(floor,DECK,seq++,REMOVE,cardId(id),"",false,null));
            for(String id:strings(arr(c,"cards_upgraded")))out.add(new Event(floor,DECK,seq++,UPGRADE,cardId(id),"",false,null));}
        if(o.get("neow_bonus_log")!=null&&o.get("neow_bonus_log").isJsonObject()){JsonObject n=obj(o,"neow_bonus_log");
            for(String id:strings(arr(n,"cardsObtained")))out.add(new Event(0,DECK,seq++,ADD,cardId(id),"",id.contains("+"),null));
            for(String id:strings(arr(n,"cardsRemoved")))out.add(new Event(0,DECK,seq++,REMOVE,cardId(id),"",false,null));
            for(String id:strings(arr(n,"cardsTransformed")))out.add(new Event(0,DECK,seq++,REMOVE,cardId(id),"",false,null));
            for(String id:strings(arr(n,"cardsUpgraded")))out.add(new Event(0,DECK,seq++,UPGRADE,cardId(id),"",false,null));}
        for(JsonElement e:arr(o,"campfire_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");String key=str(c,"key");
            if(floor!=null&&!key.isEmpty())out.add(new Event(floor,SITE,seq++,CAMP,cardId(str(c,"data")),key,false,null));}
        // Pandaora's Box removes every starter Strike/Defend; the new cards only exist in master_deck and are filled by the deficit.
        int[] boss=bossFloors(o);JsonArray picks=arr(o,"boss_relics");
        for(int i=0;i<Math.min(2,picks.size());i++){if(!picks.get(i).isJsonObject())continue;String picked=str(picks.get(i).getAsJsonObject(),"picked");
            if(picked.equals("Pandora's Box")){String strike="Strike_R",defend="Defend_R";String ch=str(o,"character_chosen");
                if(ch.equals("THE_SILENT")){strike="Strike_G";defend="Defend_G";}else if(ch.equals("DEFECT")){strike="Strike_B";defend="Defend_B";}else if(ch.equals("WATCHER")){strike="Strike_P";defend="Defend_P";}
                out.add(new Event(boss[i],DECK,seq++,PANDORA,strike,"",false,null));out.add(new Event(boss[i],DECK,seq++,PANDORA,defend,"",false,null));}}
        return out;
    }

    static Set<String> known(JsonObject o){
        Set<String> known=new HashSet<>();
        for(String id:strings(arr(o,"master_deck")))known.add(cardId(id));
        for(JsonElement e:arr(o,"card_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();known.add(cardId(str(c,"picked")));for(String id:strings(arr(c,"not_picked")))known.add(cardId(id));}
        for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();
            for(String id:strings(arr(c,"cards_obtained")))known.add(cardId(id));for(String id:strings(arr(c,"cards_removed")))known.add(cardId(id));for(String id:strings(arr(c,"cards_transformed")))known.add(cardId(id));}
        if(o.get("neow_bonus_log")!=null&&o.get("neow_bonus_log").isJsonObject()){JsonObject n=obj(o,"neow_bonus_log");
            for(String id:strings(arr(n,"cardsObtained")))known.add(cardId(id));for(String id:strings(arr(n,"cardsTransformed")))known.add(cardId(id));}
        for(JsonElement e:arr(o,"shop_contents"))if(e.isJsonObject())for(String id:strings(arr(e.getAsJsonObject(),"cards")))known.add(cardId(id));
        for(String id:strings(arr(o,"items_purged")))known.add(cardId(id));
        for(JsonElement e:arr(o,"campfire_choices"))if(e.isJsonObject()&&"SMITH".equals(str(e.getAsJsonObject(),"key")))known.add(cardId(str(e.getAsJsonObject(),"data")));
        known.remove("");
        return known;
    }

    static Map<String,Integer> finalCounts(JsonObject o){
        Map<String,Integer> out=new LinkedHashMap<>();for(String id:strings(arr(o,"master_deck"))){String base=cardId(id);if(!base.isEmpty())out.merge(base,1,Integer::sum);}return out;
    }

    /** First acquisition floor of the five rest-site relics: chests/elites (relics_obtained), shops, events, boss picks, Neow swap. */
    static Map<String,Integer> relicFloors(JsonObject o){
        Map<String,Integer> out=new LinkedHashMap<>();
        for(JsonElement e:arr(o,"relics_obtained"))if(e.isJsonObject()){JsonObject r=e.getAsJsonObject();String key=str(r,"key");Integer floor=integer(r,"floor");if(RELICS.contains(key))put(out,key,floor==null?1:floor);}
        List<String> bought=strings(arr(o,"items_purchased"));JsonArray boughtAt=arr(o,"item_purchase_floors");
        for(int i=0;i<bought.size();i++)if(RELICS.contains(bought.get(i))){Integer at=i<boughtAt.size()?integer(boughtAt.get(i)):null;put(out,bought.get(i),at==null?1:at);}
        for(JsonElement e:arr(o,"event_choices"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();Integer floor=integer(c,"floor");if(floor==null)continue;for(String id:strings(arr(c,"relics_obtained")))if(RELICS.contains(id))put(out,id,floor);}
        int[] boss=bossFloors(o);JsonArray picks=arr(o,"boss_relics");
        for(int i=0;i<Math.min(2,picks.size());i++)if(picks.get(i).isJsonObject()){String picked=str(picks.get(i).getAsJsonObject(),"picked");if(RELICS.contains(picked))put(out,picked,boss[i]);}
        // Neow's relic swap replaces the starter relic; .run keeps no id for the swap, so the first relic stands in.
        if("BOSS_RELIC".equals(str(o,"neow_bonus"))){JsonArray relics=arr(o,"relics");if(relics.size()>0&&relics.get(0).isJsonPrimitive()&&RELICS.contains(relics.get(0).getAsString()))put(out,relics.get(0).getAsString(),0);}
        return out;
    }

    static void put(Map<String,Integer> map,String key,int floor){Integer old=map.get(key);if(old==null||floor<old)map.put(key,floor);}
    static boolean owns(Map<String,Integer> floors,String relic,int floor){Integer f=floors.get(relic);return f!=null&&f<=floor;}

    static boolean astrolabe(JsonObject o){for(JsonElement e:arr(o,"boss_relics"))if(e.isJsonObject()&&"Astrolabe".equals(str(e.getAsJsonObject(),"picked")))return true;return false;}
    /** Deficit/surplus go in just after the first card-changing boss relic; earlier boss floors cannot have produced those cards. */
    static int supplementFloor(JsonObject o){
        int floor=Integer.MAX_VALUE;JsonArray picks=arr(o,"boss_relics");int[] boss=bossFloors(o);
        for(int i=0;i<Math.min(2,picks.size());i++)if(picks.get(i).isJsonObject()&&SUPPLEMENT.contains(str(picks.get(i).getAsJsonObject(),"picked")))floor=Math.min(floor,boss[i]);
        return floor==Integer.MAX_VALUE?0:floor;
    }

    static int[] bossFloors(JsonObject o){
        int a1=-1,a2=-1;
        for(JsonElement e:arr(o,"damage_taken"))if(e.isJsonObject()){JsonObject f=e.getAsJsonObject();String name=str(f,"enemies");Integer floor=integer(f,"floor");if(floor==null)continue;
            if(Native.ACT1_BOSS.contains(name))a1=a1<0||floor<a1?floor:a1;if(Native.ACT2_BOSS.contains(name))a2=a2<0||floor<a2?floor:a2;}
        return new int[]{a1>0?a1:16,a2>0?a2:33};
    }

    static int index(String key){for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key))return i;return -1;}
    private Campfire(){}
}
