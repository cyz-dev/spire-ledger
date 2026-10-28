package spireledger.stats;

import java.util.*;
import java.util.function.BiPredicate;
import static spireledger.stats.Metric.*;

/** The M2 metrics of metrics.md section 6 as pure functions of a selection; nothing here is stored. */
public final class Metrics {
    static final List<String> OUTCOMES=Arrays.asList("victory","death","abandon","unknown");
    static final List<String> FAMILIES=Arrays.asList("events","purchases","purges","campfire","potions","observed");
    static final Set<String> FIGHT_ROOMS=new HashSet<>(Arrays.asList("M","E","B"));
    final List<Run> runs;final Stats.Filter f;final Map<String,Metric> out=new LinkedHashMap<>();final Map<String,double[]> cover=new LinkedHashMap<>();final Map<String,String> coverUnits=new HashMap<>();
    Metrics(List<Run> runs,Stats.Filter f){this.runs=runs;this.f=f;}

    public static Map<String,Metric> compute(Stats.Selection s,Stats.Filter f){Metrics m=new Metrics(s.runs,f);m.runLevel();m.neow();m.screens();m.combats();m.counts();m.campfires();return m.finish();}

    Metric get(String key,String kind,String unit,String measure,String cover,boolean winRate){return out.computeIfAbsent(key,k->new Metric(k,kind,unit,measure,cover,winRate));}
    Metric get(String key,String kind,String unit,String measure,String cover){return get(key,kind,unit,measure,cover,false);}
    Metric rate(String key,String unit,String cover){return get(key,RATE,unit,"",cover,false);}
    /** Win rate among the runs that share one choice; association only, once per run. */
    void related(String key,Boolean win){Metric m=get("related:"+key,RATE,"run","","result",true);if(win==null)m.excluded++;else m.add(win?1:0,1);}
    void cover(String family,String unit,double have,double of){double[] c=cover.computeIfAbsent(family,k->new double[2]);c[0]+=have;c[1]+=of;coverUnits.put(family,unit);}

    static boolean died(Run r,Run.Combat c){return "death".equals(r.outcome.or(null))&&Integer.valueOf(c.floor).equals(r.floorReached.or(null))&&c.encounter.equals(r.killedBy.or(null));}
    /** Acts 1-3 normal, elite and boss fights plus every act-4 fight (metrics.md 6.4). */
    static boolean hpEligible(Run.Combat c){return c.act==4||FIGHT_ROOMS.contains(c.room);}
    /** Surviving act n: reaching act n+1, or ending it with a victory (an act-3 win without keys survives act 3); dying or abandoning in it does not. */
    static Fact<Boolean> survived(Run r,int n){Integer last=r.maxAct.or(null);if(last==null)return n==1?Fact.missing(""):Fact.notApplicable();if(n>last)return Fact.notApplicable();if(n<last)return Fact.of(true,r.maxAct.source);
        if(n==3&&Boolean.TRUE.equals(r.act3.or(null)))return Fact.of(true,r.act3.source);String o=r.outcome.or(null);return o==null?Fact.missing(r.outcome.source):Fact.of(o.equals("victory"),r.outcome.source);}

    void runLevel(){
        get("runs",COUNT,"run","",null).count(runs.size());for(String o:OUTCOMES)get("outcome:"+o,COUNT,"run","",null);
        Metric win=get("win",RATE,"run","","result",true),time=get("time",MEAN,"run","seconds","playtime"),deck=get("deck_size",MEAN,"run","cards","deck");
        Metric gold=get("gold_gained",MEAN,"run","gold","gold"),exact=get("gold_gained:exact",MEAN,"run","gold","gold_exact"),guess=get("gold_gained:estimated",MEAN,"run","gold","gold_estimated");
        Metric score=get("score",MEAN,"run","score","score"),purgeTotal=get("purge_total",MEAN,"run","cards","purges"),purgeShop=get("purge_shop",MEAN,"run","cards","purges"),purgeEvent=get("purge_event",MEAN,"run","cards","purges");
        for(Run r:runs){Boolean w=Stats.win(r,f.heart);String o=r.outcome.or("unknown");out.get("outcome:"+(OUTCOMES.contains(o)?o:"unknown")).count(1);
            if(w==null)win.excluded++;else win.add(w?1:0,1);cover("result","run",w==null?0:1,1);
            if(r.playtime.known())time.add(r.playtime.value,1);cover("playtime","run",r.playtime.known()?1:0,1);
            if(r.deckSize.known())deck.add(r.deckSize.value,1);cover("deck","run",r.deckSize.known()?1:0,1);
            boolean estimated=r.gold.status==Fact.Status.ESTIMATED;if(r.gold.known()){gold.add(r.gold.value,1);(estimated?guess:exact).add(r.gold.value,1);if(estimated){gold.estimated++;guess.estimated++;}}
            cover("gold","run",r.gold.known()?1:0,1);cover("gold_exact","run",r.gold.known()&&!estimated?1:0,1);cover("gold_estimated","run",estimated?1:0,1);
            if(r.score.known()){score.add(r.score.value,1);cover("score","run",1,1);}else cover("score","run",0,1);
            if(r.purges.known()){Map<String,Integer> p=r.purges.value;purgeTotal.add(p.getOrDefault("total",0),1);purgeShop.add(p.getOrDefault("shop",0),1);purgeEvent.add(p.getOrDefault("event",0),1);cover("purges","run",1,1);}else cover("purges","run",0,1);
            if(r.deck.known())for(String id:r.deck.value){Metric m=get("holding:"+id,RATE,"run","","deck",true);if(w==null)m.excluded++;else m.add(w?1:0,1);}
            if(r.neow.known())related("neow:"+r.neow.value,w);// Coverage is the rate rows' denominator (metrics.md 6.3): runs with the whole option set, not just the pick.
            if(r.neow.status!=Fact.Status.NOT_APPLICABLE)cover("neow","run",r.neowOffers.known()?1:0,1);
            if(r.boss.known())for(int i=0;i<r.boss.value.size();i++){Run.BossOffer b=r.boss.value.get(i);cover("boss_relics","run_act",b==null?0:1,1);if(b==null)continue;
                for(String id:b.offered)rate("boss:"+(i+1)+":"+id,"choice","boss_relics").add(id.equals(b.picked)?1:0,1);related("boss:"+(i+1)+":"+b.picked,w);}
            else if(r.boss.status==Fact.Status.MISSING&&r.maxAct.or(0)>=2)cover("boss_relics","run_act",0,Math.min(2,r.maxAct.value-1));
            for(int n=1;n<=4;n++){Fact<Boolean> s=survived(r,n);if(s.status==Fact.Status.NOT_APPLICABLE)continue;cover("act","run_act",s.known()?1:0,1);if(s.known())rate("act:"+n,"run_act","act").add(s.value?1:0,1);}
            Fact<Map<String,Integer>> events=r.counts.get("events");if(events!=null&&events.known())for(String k:events.value.keySet())if(k.startsWith("event:"))related(k,w);
            if("death".equals(o))get(r.killedBy.known()?"death_count:"+r.killedBy.value:"death_count_unknown",COUNT,"run","",null).count(1);
            if(r.unresolvedPath)get("unresolved_path",COUNT,"run","",null).count(1);
        }
    }

    /** Neow offerings: every option on the screen is an offer, so the rate is picked / offered, not a raw count. */
    void neow(){for(Run r:runs){List<String> offers=r.neowOffers.or(null);String picked=r.neow.or(null);if(offers==null||picked==null)continue;
        Set<String> seen=new HashSet<>();for(String key:offers){if(key==null||key.isEmpty()||!seen.add(key))continue;rate("neow:"+key,"run",null).add(key.equals(picked)?1:0,1);}}}

    /** Card reward screens from the merged source (metrics.md 6.2); known automation is left out of every choice ranking (3.4, plan A). */
    void screens(){boolean byScreen=true;for(Run r:runs)byScreen&=r.combats.known();
        for(Run r:runs){Set<Integer> floors=new HashSet<>();
            if(r.screens.known())for(Run.Screen s:r.screens.value){floors.add(s.floor);
                if("known_automation".equals(s.actor)){get("reward_automation_excluded",COUNT,"choice_set","",null).count(1);continue;}
                get("reward:"+s.act,COUNT,"choice_set","","reward").count(1);rate("skip:"+s.act,"choice_set","reward").add(s.kind.equals("skip")?1:0,1);
                if(Boolean.TRUE.equals(s.bowlOffered))rate("bowl:"+s.act,"choice_set","reward").add(s.kind.equals("bowl")?1:0,1);
                for(String c:s.offered)rate("pick:"+s.act+":"+c,"choice","reward").add(s.kind.equals("card")&&s.picked.equals(c)?1:0,1);
                rate("reward_source:unknown","choice_set","reward").add("unknown".equals(s.actor)?1:0,1);}
            // Coverage per expected screen: won normal, elite and act-1/2 boss fights (metrics.md 6.2, proposed); by run when a run's fights are unknown.
            if(byScreen){int expected=0,have=0;for(Run.Combat c:r.combats.value)if((c.room.equals("M")||c.room.equals("E")||c.room.equals("B")&&c.act<=2)&&!died(r,c)){expected++;if(floors.contains(c.floor))have++;}cover("reward","choice_set",have,expected);}
            else cover("reward","run",floors.isEmpty()?0:1,1);
        }
    }

    void combats(){
        for(Run r:runs){Map<Integer,double[]> acts=new HashMap<>();boolean death="death".equals(r.outcome.or(null));int threshold="IRONCLAD".equals(r.character.or(null))?6:0; // Burning Blood, as TinyCat
            if(r.combats.known())for(Run.Combat c:r.combats.value){
                if(c.damage!=null)get("damage:"+c.encounter,MEAN,"combat","hp","damage").add(c.damage,1);
                if(!death||r.killedBy.known())rate("death:"+c.encounter,"combat","death").add(died(r,c)?1:0,1);
                if(r.potionsByFloor.known())get("encounter_potions:"+c.encounter,MEAN,"combat","potions","potions_by_encounter").add(r.potionsByFloor.value.getOrDefault(c.floor,0),1);
                if(!hpEligible(c))continue;Fact<Double> loss=r.hpLoss(c);double[] a=acts.computeIfAbsent(c.act,k->new double[3]);if(!loss.known()){a[1]=1;continue;}
                boolean estimated=loss.status==Fact.Status.ESTIMATED;a[0]+=loss.value;if(estimated)a[2]=1;
                // Healed in the fight: damage taken exceeds the HP actually lost by more than the threshold (metrics.md 6.4).
                String split=c.damage==null?null:(c.damage-loss.value>threshold?"hp_loss_heal:":"hp_loss_noheal:")+c.act+":"+c.encounter;
                for(String key:split==null?Collections.singletonList("hp_loss:"+c.act+":"+c.encounter):Arrays.asList("hp_loss:"+c.act+":"+c.encounter,split)){Metric m=get(key,MEAN,"combat","hp","hp_loss");m.add(loss.value,1);if(estimated)m.estimated++;}
            }
            // Per run and act: fights plus positive event damage; an act without fights is a known 0, one unknown fight leaves the act missing.
            Integer last=r.maxAct.or(null);if(last!=null)for(int act=1;act<=Math.min(4,last);act++){double[] a=acts.getOrDefault(act,new double[3]);boolean have=r.combats.known()&&a[1]==0&&r.eventDamage.known();cover("act_hp_loss","run_act",have?1:0,1);
                if(have){Metric m=get("act_hp_loss:"+act,MEAN,"run_act","hp","act_hp_loss");m.add(a[0]+r.eventDamage.value.getOrDefault(act,0.0),1);if(a[2]>0)m.estimated++;}}
        }
        combatCover("damage",(r,c)->true,(r,c)->c.damage!=null);combatCover("hp_loss",(r,c)->hpEligible(c),(r,c)->r.hpLoss(c).known());
        combatCover("potions_by_encounter",(r,c)->true,(r,c)->r.potionsByFloor.known());combatCover("death",(r,c)->true,(r,c)->!"death".equals(r.outcome.or(null))||r.killedBy.known());
    }
    /** Coverage in fights; by run when some run's fights are unknown, since their number is then unknown too (metrics.md 6.4). */
    void combatCover(String family,BiPredicate<Run,Run.Combat> eligible,BiPredicate<Run,Run.Combat> has){boolean byFight=true;for(Run r:runs)byFight&=r.combats.known();
        for(Run r:runs){int of=0,have=0;if(r.combats.known())for(Run.Combat c:r.combats.value)if(eligible.test(r,c)){of++;if(has.test(r,c))have++;}
            if(byFight)cover(family,"combat",have,of);else cover(family,"run",r.combats.known()&&have==of?1:0,1);}}

    void counts(){for(Run r:runs)for(String family:FAMILIES){if(family.equals("observed")&&!r.has(Fact.LEDGER))continue;Fact<Map<String,Integer>> c=r.counts.get(family);boolean known=c!=null&&c.known();cover(family,"run",known?1:0,1);
        if(known)for(Map.Entry<String,Integer> e:c.value.entrySet()){if(family.equals("campfire")&&e.getKey().startsWith("campfire:"))continue; // the campfire page builds its own per-card rows
            get(e.getKey(),COUNT,family.equals("potions")?"run":"event","",family).count(e.getValue());}}}

    /** Per-act campfire tallies; act 0 is the four-act total. */
    static final class CampfireAgg{
        final int[] visits=new int[5],smiths=new int[5],rebuildOk=new int[5],sleepChosen=new int[5],sleepAvail=new int[5];final double[] poolSum=new double[5];
        final List<Map<String,Integer>> actions=maps(),exposure=maps(),cardChosen=maps(),available=maps(),actionChosen=maps();
        static List<Map<String,Integer>> maps(){List<Map<String,Integer>> out=new ArrayList<Map<String,Integer>>();for(int i=0;i<5;i++)out.add(new LinkedHashMap<String,Integer>());return out;}
        void add(Run.Campfire c,int act){
            visits[act]++;poolSum[act]+=c.pool-(c.forcedRecall?1:0);
            if("SMITH".equals(c.key)){smiths[act]++;if(c.smithUsable)rebuildOk[act]++;}
            merge(actions.get(act),"SMITH".equals(c.key)&&!c.target.isEmpty()?c.target:c.key);
            if(c.rest)sleepAvail[act]++;if("REST".equals(c.key))sleepChosen[act]++;
            for(String key:Campfire.KEYS)if(!"REST".equals(key)&&!"SMITH".equals(key)&&c.had(key)&&!(c.forcedRecall&&"RECALL".equals(key)))merge(available.get(act),key);
            if(!"REST".equals(c.key)&&!"SMITH".equals(c.key)&&!(c.forcedRecall&&"RECALL".equals(c.key)))merge(actionChosen.get(act),c.key);
            if("SMITH".equals(c.key)&&c.smithUsable){for(String card:c.candidates)merge(exposure.get(act),card);if(c.candidates.contains(c.target))merge(cardChosen.get(act),c.target);}
        }
        void fold(int from,int to){
            visits[to]+=visits[from];smiths[to]+=smiths[from];rebuildOk[to]+=rebuildOk[from];sleepChosen[to]+=sleepChosen[from];sleepAvail[to]+=sleepAvail[from];poolSum[to]+=poolSum[from];
            fold(actions,from,to);fold(exposure,from,to);fold(cardChosen,from,to);fold(available,from,to);fold(actionChosen,from,to);
        }
        static void fold(List<Map<String,Integer>> maps,int from,int to){for(Map.Entry<String,Integer> e:maps.get(from).entrySet())maps.get(to).merge(e.getKey(),e.getValue(),Integer::sum);}
        static void merge(Map<String,Integer> map,String key){map.merge(key,Integer.valueOf(1),Integer::sum);}
    }

    /**
     * Rest sites, TinyCat's two views over the same visits.
     *
     * <p>Counts: one row per card smithed and per other action, per act, plus the act's visit total.
     *
     * <p>Multiple: the pool is every unupgraded card kind plus available Purge/Lift/Dig/Recall, never Rest and
     * never "smith" as one slot. Each card kind is one row (smithed / times it was in the pool); each relic
     * action and Recall is one row (chosen / times available). All rows divide by the shared baseline
     * 100 / average pool, so cards and actions share one ranking. A Recall taken in act 3 to reach act 4 is
     * the forced red key: it leaves both the pool and the Recall row.
     */
    void campfires(){
        CampfireAgg agg=new CampfireAgg();
        for(Run r:runs){if(!r.campfires.known())continue;for(Run.Campfire c:r.campfires.value)agg.add(c,c.act>=1&&c.act<5?c.act:0);}
        for(int act=1;act<5;act++)agg.fold(act,0);
        for(int act=0;act<5;act++){if(agg.visits[act]<=0)continue;
            double poolAvg=agg.poolSum[act]/agg.visits[act],baseline=poolAvg>0?100.0/poolAvg:0;
            get("campfire_visits:"+act,COUNT,"event","",null).count(agg.visits[act]);
            get("campfire_smiths:"+act,COUNT,"event","",null).count(agg.smiths[act]);
            Metric pool=get("campfire_pool:"+act,MEAN,"event","candidates",null);pool.numerator=poolAvg;pool.denominator=1;pool.samples=agg.visits[act];
            if(agg.smiths[act]>0){Metric rebuild=get("campfire_rebuild:"+act,MEAN,"event","rebuild",null);rebuild.numerator=agg.rebuildOk[act];rebuild.denominator=agg.smiths[act];rebuild.samples=agg.smiths[act];}
            Metric sleep=get("campfire_sleep:"+act,RATE,"event","",null);int sleepSites=Math.max(agg.sleepAvail[act],agg.sleepChosen[act]);sleep.numerator=agg.sleepChosen[act];sleep.denominator=sleepSites;sleep.samples=sleepSites;
            for(Map.Entry<String,Integer> e:agg.actions.get(act).entrySet())get("campfire_count:"+act+":"+e.getKey(),COUNT,"event","",null).count(e.getValue());
            Set<String> keys=new LinkedHashSet<>();keys.addAll(agg.exposure.get(act).keySet());keys.addAll(agg.available.get(act).keySet());
            for(String key:keys){Integer exposure=agg.exposure.get(act).get(key),available=agg.available.get(act).get(key);
                int visits=exposure!=null?exposure.intValue():available==null?0:available.intValue();
                int chosen=exposure!=null?agg.cardChosen.get(act).getOrDefault(key,Integer.valueOf(0)).intValue():agg.actionChosen.get(act).getOrDefault(key,Integer.valueOf(0)).intValue();
                if(visits<=0)continue;
                Metric m=get("campfire_pref:"+act+":"+key,MEAN,"event","share",null);m.numerator=chosen;m.denominator=visits;m.samples=visits;m.baseline=baseline;}
        }
    }

    /** Status per metrics.md 1.2, 1.5 and 3.3; coverage rows follow, outside the minimum sample. */
    Map<String,Metric> finish(){
        for(Metric m:out.values()){double[] c=m.cover==null?null:cover.get(m.cover);if(c!=null){m.coverNumerator=c[0];m.coverDenominator=c[1];m.coverUnit=coverUnits.get(m.cover);}
            m.status=m.winRate&&!f.result.equals("all")?"hidden_by_result_filter":m.kind.equals(COUNT)?(m.numerator==0?"zero":"ok"):m.denominator==0?"missing":m.samples<f.minSamples?"below_min_sample":m.numerator==0?"zero":"ok";}
        for(Map.Entry<String,double[]> e:cover.entrySet()){Metric m=get("coverage:"+e.getKey(),RATE,coverUnits.get(e.getKey()),"",null);m.numerator=e.getValue()[0];m.denominator=e.getValue()[1];m.samples=(int)m.denominator;m.status=m.denominator==0?"missing":m.numerator==0?"zero":"ok";}
        return out;
    }
}
