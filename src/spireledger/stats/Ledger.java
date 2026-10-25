package spireledger.stats;

import com.google.gson.*;
import java.util.*;
import static spireledger.stats.Json.*;

/** One Ledger record (spire-ledger-v1, or State Notes sts1-state-v1) or its summary projection, read along its final path. */
public final class Ledger {
    static final String L=Fact.LEDGER;
    final String runId,digest;final boolean current;final Run run;
    /** Read at once so a large record is not held; current: the run still being played; conflict: the record index found disagreeing copies or signatures. */
    public Ledger(JsonObject doc,boolean current,boolean conflict){runId=str(obj(doc,"metadata"),"run_id");digest=Stats.digest(doc);this.current=current;run=read(doc,current,conflict);}

    public static final class FinalPath {public final List<JsonObject> records=new ArrayList<>();public boolean unresolved;public String lastBranch="";}
    /** Follow only a confirmed checkpoint ancestry. Unknown parents are not stitched together. */
    public static FinalPath finalPath(JsonArray records){
        FinalPath p=new FinalPath();List<JsonObject> all=new ArrayList<>();Map<String,JsonObject> points=new HashMap<>(),loads=new HashMap<>();
        for(JsonElement e:records)if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();all.add(o);String b=str(o,"branch_id"),type=str(o,"type");if(!b.isEmpty())p.lastBranch=b;
            if(type.equals("checkpoint"))points.put("save:"+str(o,"checkpoint_id"),o);if(type.equals("turn_snapshot"))points.put("turn:"+str(o,"snapshot_id"),o);if(type.equals("load")||type.equals("turn_rollback"))loads.put(b,o);}
        Map<String,Integer> ceilings=new HashMap<>();String branch=p.lastBranch;int ceiling=Integer.MAX_VALUE;Set<String> seen=new HashSet<>();
        while(!branch.isEmpty()&&seen.add(branch)){ceilings.put(branch,ceiling);JsonObject load=loads.get(branch);if(load==null)break;
            boolean turn=str(load,"type").equals("turn_rollback");JsonObject point=points.get(turn?"turn:"+str(load,"snapshot_id"):"save:"+str(load,"parent_checkpoint_id"));
            boolean known=turn?point!=null&&str(load,"parent_branch_id").equals(str(point,"branch_id")):"local_checkpoint_found".equals(str(load,"parent_status"));
            if(!known||point==null||num(point,"seq",-1)<0||num(point,"seq",-1)>=num(load,"seq",0)){p.unresolved=true;break;}
            branch=str(point,"branch_id");ceiling=num(point,"seq",-1);}
        for(JsonObject e:all){String b=str(e,"branch_id");if(ceilings.containsKey(b)&&num(e,"seq",0)<=ceilings.get(b))p.records.add(e);else if(p.lastBranch.isEmpty()&&b.isEmpty())p.records.add(e);}
        return p;
    }

    static Run read(JsonObject doc,boolean current,boolean conflict){
        JsonObject meta=obj(doc,"metadata");Run r=new Run();r.sources.add(L);r.runId=str(meta,"run_id");r.id="ledger:"+r.runId;r.playId=str(meta,"play_id");r.current=current;r.conflict=conflict;if(conflict)r.conflictReason="ledger_record";
        r.fixture="test_fixture".equals(str(meta,"dataset_origin"));r.practice="practice".equals(str(meta,"session_kind"));if(!str(meta,"seed").isEmpty())r.seed=Fact.of(str(meta,"seed"),L);
        JsonArray all=arr(doc,"records");Set<String> branches=new HashSet<>();long time=0;
        for(JsonElement e:all)if(e.isJsonObject()){JsonObject o=e.getAsJsonObject();if(!str(o,"branch_id").isEmpty())branches.add(str(o,"branch_id"));try{time=Math.max(time,o.has("time_ms")?o.get("time_ms").getAsLong():0);}catch(Exception ignored){}}
        r.sl=branches.size()>1;if(time>0)r.time=Fact.of(time,L);
        FinalPath path=finalPath(all);r.unresolvedPath=path.unresolved;
        Map<String,JsonObject> decisions=new LinkedHashMap<>(),results=new HashMap<>();Map<String,String> signatures=new HashMap<>();Set<String> conflicts=new HashSet<>(),changes=new HashSet<>();
        Map<String,Integer> observed=new LinkedHashMap<>();Map<Integer,Integer> enter=new HashMap<>();JsonObject ending=null,play=null;int maxAct=0;Integer gold=null;
        for(JsonObject e:path.records){String type=str(e,"type");
            for(JsonObject s:Arrays.asList(obj(e,"before"),obj(e,"state"),obj(e,"after"))){if(s.entrySet().isEmpty())continue;maxAct=Math.max(maxAct,num(s,"act",0));
                if(!str(s,"class").isEmpty())r.character=Fact.of(str(s,"class"),L);if(integer(s,"ascension")!=null)r.ascension=Fact.of(integer(s,"ascension"),L);if(integer(s,"gold_gained")!=null)gold=integer(s,"gold_gained");
                if(bool(s,"is_daily")!=null){r.daily=Fact.of(bool(s,"is_daily"),L);r.endless=Fact.of(bool(s,"is_endless"),L);r.trial=Fact.of(bool(s,"is_trial"),L);}}
            if(type.equals("floor_enter")){Integer floor=integer(e,"floor"),hp=integer(e,"hp");if(floor!=null&&hp!=null)enter.put(floor,hp);maxAct=Math.max(maxAct,num(e,"act",0));if(integer(e,"gold_gained")!=null)gold=integer(e,"gold_gained");}
            if(type.equals("ending"))ending=e;
            if(type.equals("game_play_id"))play=e;
            if(type.equals("decision")||type.equals("result")){String id=str(e,"decision_id"),sig=sig(e),old=signatures.putIfAbsent(type+":"+id,sig);if(old!=null&&!old.equals(sig))conflicts.add(id);if(old==null)(type.equals("decision")?decisions:results).put(id,e);}
            if(type.equals("build_change")&&"settled".equals(str(e,"status"))&&changes.add(sig(e)))observe(obj(e,"before"),obj(e,"after"),observed);
        }
        if(play!=null&&!str(play,"play_id").isEmpty())r.playId=str(play,"play_id");
        if(maxAct>0)r.maxAct=Fact.of(maxAct,L);r.floorEnter=Fact.of(enter,L);r.counts.put("observed",Fact.of(observed,L));
        // The last snapshot on the final path holds the run's total (metrics.md 5.2).
        if(gold!=null)r.gold=Fact.amount((double)gold,L);

        Set<String> actors=new TreeSet<>();List<Run.Screen> screens=new ArrayList<>();
        for(Map.Entry<String,JsonObject> item:decisions.entrySet()){String id=item.getKey();JsonObject d=item.getValue(),res=results.get(id),before=obj(d,"before");String source=str(d,"source");
            if(source.equals("COMBAT_REWARD"))r.ledgerRewardFloors.add(num(before,"floor",-1));
            if(conflicts.contains(id)||res==null||!"confirmed".equals(str(res,"status")))continue;actors.add(str(d,"action_source"));
            JsonObject chosen=null;boolean legal=true,bowl=false;Set<String> offered=new LinkedHashSet<>();
            for(JsonElement c:arr(d,"candidates"))if(c.isJsonObject()){JsonObject x=c.getAsJsonObject();if(str(x,"candidate_id").equals(str(d,"candidate_id"))){if(chosen!=null)legal=false;chosen=x;}
                if("card".equals(str(x,"type"))&&!str(obj(x,"card"),"id").isEmpty())offered.add(str(obj(x,"card"),"id"));bowl|="bowl".equals(str(x,"type"));}
            if(chosen==null||!legal)continue;String kind=str(chosen,"type");
            if(source.equals("COMBAT_REWARD")&&Arrays.asList("card","skip","bowl").contains(kind))screens.add(new Run.Screen(num(before,"act",0),num(before,"floor",-1),offered,kind,kind.equals("card")?str(obj(chosen,"card"),"id"):"",str(d,"action_source"),L,bowl));
            if(source.equals("NEOW_OPTION")){Integer hp=integer(obj(res,"after"),"hp");if(hp!=null)r.neowHp=Fact.of(hp,L);
                // Variant blessings are not rows of the standard Neow table (metrics.md 6.3).
                r.neow="blessing".equals(str(d,"variant"))?Fact.of(str(chosen,"reward_type")+" / "+str(chosen,"drawback"),L):Fact.notApplicable();}
        }
        r.screens=Fact.of(screens,L);if(!actors.isEmpty())r.actor=Fact.of(actors.size()==1?actors.iterator().next():"mixed",L);

        if(ending==null){r.outcome=Fact.of("incomplete",L);return r;}
        JsonObject state=obj(ending,"state");if(isArray(state,"deck")){Set<String> deck=new LinkedHashSet<>();for(JsonElement c:arr(state,"deck")){String id=c.isJsonObject()?str(c.getAsJsonObject(),"id"):"";if(!id.isEmpty())deck.add(id);}r.deck=Fact.of(deck,L);r.deckSize=Fact.amount(arr(state,"deck").size(),L);}
        Boolean victory=play==null?null:bool(play,"victory"),cleared=bool(ending,"act3_cleared");String end=str(ending,"outcome");
        r.heart=Fact.agree(Fact.of(bool(ending,"heart_kill"),L),Fact.of(play==null?null:bool(play,"heart_kill"),L));
        // Records before M1 have no act3_cleared; reaching act 4 clears act 3 and dying before act 3 cannot (the rule StateMod.act3Facts writes).
        r.act3=cleared!=null?Fact.of(cleared,L):maxAct>=4||Boolean.TRUE.equals(victory)?Fact.of(true,"ledger_act"):maxAct>0&&maxAct<=2?Fact.of(false,"ledger_act"):Fact.missing(L);
        // The game shows a DeathScreen for an act-3 win without keys (architecture M2) and for a menu abandon (Ledger records against .run), so "death" alone is not a death.
        boolean act3Win=Boolean.TRUE.equals(r.act3.value)&&maxAct>0&&maxAct<4;
        r.outcome=end.equals("victory")||end.equals("death")&&(act3Win||Boolean.TRUE.equals(victory))?Fact.of("victory",L):end.equals("abandon")?Fact.of("abandon",L):Fact.missing(L);
        return r;
    }

    /** Removed and upgraded card instances between two settled anchors; incomplete instance identities say nothing. */
    static void observe(JsonObject before,JsonObject after,Map<String,Integer> out){Map<String,JsonObject> b=instances(before),a=instances(after);if(b==null||a==null)return;
        for(Map.Entry<String,JsonObject> e:b.entrySet()){JsonObject now=a.get(e.getKey());if(now==null)out.merge("remove:observed:"+str(e.getValue(),"id"),1,Integer::sum);else if(num(now,"upgrades",0)>num(e.getValue(),"upgrades",0))out.merge("upgrade:observed:"+str(now,"id"),1,Integer::sum);}}
    static Map<String,JsonObject> instances(JsonObject state){if(!state.has("deck"))return null;Map<String,JsonObject> out=new LinkedHashMap<>();
        for(JsonElement e:arr(state,"deck")){if(!e.isJsonObject())return null;String id=str(e.getAsJsonObject(),"instance_id");if(id.isEmpty()||out.put(id,e.getAsJsonObject())!=null)return null;}return out;}
}
