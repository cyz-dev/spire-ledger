package spireledger;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import spireledger.stats.*;

/** Statistics kernel against docs/metrics.md: the StatsIndex assertions that still hold, the ten M2 gaps (section 10), every result filter x abandon x win basis, read order, and the Gson-only boundary. */
public final class StatsCheck {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JsonObject parse(String json){return new JsonParser().parse(json).getAsJsonObject();}
 static final String SEED="same-seed";

 // --- native .run fixtures ---
 /** fights: "floor:room:encounter:damage"; HP is 60 at the end of every floor. */
 static JsonObject nativeRun(String id,int asc,int floorReached,Boolean victory,String killed,String... fights){
  JsonObject o=parse("{\"character_chosen\":\"IRONCLAD\",\"timestamp\":100,\"playtime\":120,\"master_deck\":[\"Bash\",\"Bash+1\",\"Strike_R\"]}");o.addProperty("play_id",id);o.addProperty("seed_played",SEED);o.addProperty("ascension_level",asc);o.addProperty("floor_reached",floorReached);
  if(victory!=null)o.addProperty("victory",victory);if(killed!=null)o.addProperty("killed_by",killed);
  JsonArray path=new JsonArray(),hp=new JsonArray(),damage=new JsonArray();for(int i=0;i<floorReached;i++){path.add(new JsonPrimitive("?"));hp.add(new JsonPrimitive(60));}
  for(String f:fights){String[] p=f.split(":");int floor=Integer.parseInt(p[0]);path.set(floor-1,new JsonPrimitive(p[1]));JsonObject d=new JsonObject();d.addProperty("floor",floor);d.addProperty("enemies",p[2]);d.addProperty("damage",Integer.parseInt(p[3]));damage.add(d);}
  o.add("path_per_floor",path);o.add("current_hp_per_floor",hp);if(fights.length>0)o.add("damage_taken",damage);o.add("event_choices",new JsonArray());return o;}
 static String[] act123(){return new String[]{"1:M:Jaw Worm:10","16:B:Hexaghost:20","33:B:Champ:15","50:B:Time Eater:10","51:B:Awakened One:12"};}
 static String[] plus(String[] a,String... b){List<String> l=new ArrayList<>(Arrays.asList(a));l.addAll(Arrays.asList(b));return l.toArray(new String[0]);}
 static Stats.NativeFile nf(JsonObject run,String dir,String file){return new Stats.NativeFile(run,dir,file);}

 // --- Ledger fixtures ---
 static JsonObject doc(String schema,String runId,String playId){JsonObject d=parse("{\"records\":[]}");d.addProperty("schema",schema);JsonObject m=new JsonObject();m.addProperty("run_id",runId);m.addProperty("seed",SEED);if(playId!=null)m.addProperty("play_id",playId);m.addProperty("save_slot",0);d.add("metadata",m);return d;}
 static JsonObject state(int act,int floor){JsonObject s=parse("{\"class\":\"IRONCLAD\",\"ascension\":20,\"deck\":[{\"id\":\"Bash\",\"instance_id\":\"a\",\"upgrades\":0}],\"hp\":20,\"max_hp\":80,\"gold\":1,\"potions\":[],\"relics\":[],\"is_daily\":false,\"is_endless\":false,\"is_trial\":false}");s.addProperty("act",act);s.addProperty("floor",floor);return s;}
 static void add(JsonArray records,JsonObject e,String branch,int seq){e.addProperty("branch_id",branch);e.addProperty("seq",seq);e.addProperty("time_ms",100000);records.add(e);}
 static JsonArray records(JsonObject doc){return doc.getAsJsonArray("records");}
 static void decision(JsonArray records,String id,String branch,int seq,int act,int floor,String kind){JsonObject d=Recorder.object("decision");d.addProperty("decision_id",id);d.addProperty("source","COMBAT_REWARD");d.addProperty("action_source","unknown");d.addProperty("candidate_id",kind.equals("card")?"card:0":kind);d.add("before",state(act,floor));d.add("candidates",parse("{\"items\":[{\"candidate_id\":\"card:0\",\"type\":\"card\",\"card\":{\"id\":\"Bash\",\"upgrades\":0}},{\"candidate_id\":\"card:1\",\"type\":\"card\",\"card\":{\"id\":\"Bash\",\"upgrades\":1}},{\"candidate_id\":\"skip\",\"type\":\"skip\"},{\"candidate_id\":\"bowl\",\"type\":\"bowl\"}]}").get("items"));add(records,d,branch,seq);JsonObject result=Recorder.object("result");result.addProperty("decision_id",id);result.addProperty("status","confirmed");result.add("after",state(act,floor));add(records,result,branch,seq+1);}
 static JsonObject ending(String outcome,int act,int floor,Boolean act3,Boolean heart){JsonObject e=Recorder.object("ending");e.addProperty("outcome",outcome);if(act3!=null)e.addProperty("act3_cleared",act3);if(heart!=null)e.addProperty("heart_kill",heart);e.add("state",state(act,floor));return e;}
 static Ledger ledger(JsonObject doc){return new Ledger(doc,false,false);}

 static Map<String,Metric> metrics(Stats.Index index,Stats.Filter f){return Metrics.compute(Stats.select(index,f,100000),f);}
 static Stats.Filter filter(boolean heart,String result,boolean abandon){Stats.Filter f=new Stats.Filter();f.heart=heart;f.result=result;f.includeAbandon=abandon;return f;}
 static Set<String> ids(Stats.Selection s){Set<String> out=new TreeSet<>();for(Run r:s.runs)out.add(r.id);return out;}
 static Run run(Stats.Index index,String id){for(Run r:index.runs)if(r.id.equals(id))return r;throw new AssertionError("no run "+id);}
 static Metric m(Map<String,Metric> all,String key){Metric x=all.get(key);check(x!=null,"metric "+key+" exists in "+all.keySet());return x;}
 static boolean is(Metric m,double num,double den){return m.numerator==num&&m.denominator==den;}
 static String dump(Stats.Index index,Stats.Filter f){Stats.Selection s=Stats.select(index,f,100000);return new Gson().toJson(new TreeMap<>(Metrics.compute(s,f)))+ids(s)+s.excluded;}

 public static void main(String[] args)throws Exception{
  Path root=Paths.get(args[0]),src=Paths.get(args[1]);
  oldAssertions(root);gapsAndCombinations();export();boundary(src);
  System.out.println("StatsCheck PASS: StatsIndex assertions migrated, metrics.md section 10 gaps, 3 result filters x abandon x 2 win bases, shuffled read order, save slot sources, TinyCat export, Gson-only stats package");
 }

 /** The StatsIndexCheck assertions that still hold under metrics-v1; changed ones say what changed. */
 static void oldAssertions(Path root)throws Exception{
  Files.createDirectories(root.resolve("IRONCLAD"));JsonObject first=nativeRun("one",0,57,true,null,"56:B:The Heart:12"),second=nativeRun("two",0,57,false,"The Heart","56:B:The Heart:12");byte[] original=first.toString().getBytes(StandardCharsets.UTF_8);
  Files.write(root.resolve("IRONCLAD/first.run"),original);Files.write(root.resolve("IRONCLAD/copy.run"),original);Files.write(root.resolve("IRONCLAD/second.run"),second.toString().getBytes(StandardCharsets.UTF_8));Files.write(root.resolve("IRONCLAD/unrecognized.run"),"{}".getBytes(StandardCharsets.UTF_8));
  Stats.Index index=StatsIndex.scan(root,new HistoryIndex.Index(),null);check(index.errors.size()==1,"unrecognized native format excluded");check(index.runs.size()==2&&index.nativeDuplicates==1,"play ID dedup, same seed separate");
  Stats.Filter f=new Stats.Filter();Map<String,Metric> m=metrics(index,f);check(is(m(m,"win"),1,2),"heart rate denominator");for(Run r:index.runs)check(!r.actor.known(),"unlinked native runs carry no actor");check(Stats.select(index,f,100000).runs.size()==2,"default filter counts unlinked native runs");
  // was: an actor filter would hide every native run; the actor filter is gone, the source filter keeps runs by where their facts came from.
  Stats.Filter ledgerOnly=new Stats.Filter();ledgerOnly.source=Fact.LEDGER;check(Stats.select(index,ledgerOnly,100000).runs.isEmpty(),"Ledger source filter hides native-only runs");
  check(m(m,"holding:Bash").denominator==2,"same-name held once per run");check(m(m,"damage:The Heart").value()==12,"explicit damage");check(m(m,"death_count:The Heart").numerator==1,"explicit native death frequency");
  check(m(m,"coverage:potions").numerator==0&&m(m,"coverage:potions").denominator==2,"missing potions not zero use");check(m(m,"time").denominator==2,"time eligible only");
  JsonObject nulls=nativeRun("null-fields",0,57,true,null,"56:B:The Heart:12");nulls.add("potions_floor_usage",JsonNull.INSTANCE);nulls.add("items_purchased",JsonNull.INSTANCE);nulls.add("neow_bonus",JsonNull.INSTANCE);
  Run nullRun=Stats.build(Arrays.asList(nf(nulls,"IRONCLAD","n.run")),Collections.emptyList()).runs.get(0);check(!nullRun.counts.containsKey("potions")&&!nullRun.counts.containsKey("purchases")&&!nullRun.neow.known(),"null fields are missing, not zero");
  // was: incomplete. metrics.md 3.1 keeps "incomplete" for Ledger records without an ending; a .run without a result is unknown, still never a death.
  JsonObject missing=parse("{\"character_chosen\":\"DEFECT\",\"seed_played\":\"same-seed\"}");Run unknown=Stats.build(Arrays.asList(nf(missing,"DEFECT","m.run")),Collections.emptyList()).runs.get(0);check(!unknown.outcome.known()&&Stats.win(unknown,true)==null&&Stats.win(unknown,false)==null,"missing result is unknown, not death");
  JsonObject ambiguous=nativeRun("ambiguous",0,57,true,null);check(Stats.win(Stats.build(Arrays.asList(nf(ambiguous,"IRONCLAD","a.run")),Collections.emptyList()).runs.get(0),true)==null,"heart detail unavailable not failure");
  JsonObject abandon=nativeRun("abandon",0,57,false,"The Heart","56:B:The Heart:12");abandon.addProperty("abandoned",true);JsonObject quit=nativeRun("quit",0,20,false,null,"1:M:Cultist:3");
  List<Stats.NativeFile> natives=new ArrayList<>(Arrays.asList(nf(first,"IRONCLAD","1.run"),nf(second,"IRONCLAD","2.run"),nf(abandon,"IRONCLAD","3.run"),nf(quit,"IRONCLAD","4.run"),nf(missing,"DEFECT","5.run")));
  Stats.Index more=Stats.build(natives,Collections.emptyList());check("abandon".equals(run(more,"quit").outcome.value)&&Boolean.FALSE.equals(Stats.win(run(more,"quit"),true)),"vanilla run without killed_by is abandoned, not incomplete");
  // was 2 and 4: the run without a result is now in "all" as unknown (metrics.md 3.3), so each count is one higher.
  check(Stats.select(more,f,100000).runs.size()==3,"abandon off: both abandons excluded, unknown result kept");f.includeAbandon=true;check(Stats.select(more,f,100000).runs.size()==5,"abandon on: both abandoned runs counted");f.includeAbandon=false;

  JsonObject doc=doc(Recorder.SCHEMA,"notes-one",null);JsonArray records=records(doc);decision(records,"first","a",1,1,10,"card");JsonObject point=Recorder.object("checkpoint");point.addProperty("checkpoint_id","cp");add(records,point,"a",3);decision(records,"discarded-attempt","a",4,3,30,"bowl");
  JsonObject load=Recorder.object("load");load.addProperty("parent_checkpoint_id","cp");load.addProperty("parent_status","local_checkpoint_found");add(records,load,"b",6);decision(records,"final","b",7,3,30,"skip");add(records,ending("death",3,50,false,false),"b",9);
  Map<String,Metric> notes=metrics(Stats.build(Collections.emptyList(),Arrays.asList(ledger(doc))),new Stats.Filter());check(m(notes,"pick:1:Bash").denominator==1,"duplicate candidates one appearance");check(m(notes,"pick:1:Bash").numerator==1,"picked once");
  check(is(m(notes,"skip:3"),1,1)&&is(m(notes,"bowl:3"),0,1),"third-act skip and ancestry");check(!notes.containsKey("unresolved_path"),"known checkpoint ancestry");
  load.addProperty("parent_status","unknown");Map<String,Metric> orphan=metrics(Stats.build(Collections.emptyList(),Arrays.asList(ledger(doc))),new Stats.Filter());check(!orphan.containsKey("pick:1:Bash")&&orphan.get("unresolved_path").numerator==1,"unknown ancestor not stitched");
  JsonArray turns=new JsonArray();JsonObject early=Recorder.object("floor");add(turns,early,"old",1);JsonObject turnPoint=Recorder.object("turn_snapshot");turnPoint.addProperty("snapshot_id","turn-two");add(turns,turnPoint,"old",3);JsonObject discarded=Recorder.object("floor");add(turns,discarded,"old",4);
  JsonObject rollback=Recorder.object("turn_rollback");rollback.addProperty("snapshot_id","turn-two");rollback.addProperty("parent_branch_id","old");add(turns,rollback,"new",6);JsonObject child=Recorder.object("floor");add(turns,child,"new",7);
  Ledger.FinalPath path=Ledger.finalPath(turns);check(path.records.contains(early)&&path.records.contains(turnPoint)&&path.records.contains(child)&&!path.records.contains(discarded)&&!path.unresolved,"turn rollback preserves earlier floors and excludes abandoned attempt");
  rollback.addProperty("parent_branch_id","wrong");path=Ledger.finalPath(turns);check(!path.records.contains(early)&&path.unresolved,"mismatched turn ancestry excluded");rollback.addProperty("parent_branch_id","old");rollback.addProperty("snapshot_id","missing");path=Ledger.finalPath(turns);check(!path.records.contains(early)&&path.unresolved,"missing turn ancestry excluded");
  load.addProperty("parent_status","local_checkpoint_found");
  JsonObject practice=Recorder.copy(doc);practice.getAsJsonObject("metadata").addProperty("session_kind","practice");JsonObject fixture=Recorder.copy(doc);fixture.getAsJsonObject("metadata").addProperty("dataset_origin","test_fixture");
  for(Object[] c:new Object[][]{{practice,false,"practice"},{fixture,false,"fixture"},{doc,true,"conflict"}}){Stats.Selection s=Stats.select(Stats.build(Collections.emptyList(),Arrays.asList(new Ledger((JsonObject)c[0],false,(Boolean)c[1]))),new Stats.Filter(),100000);check(s.runs.isEmpty()&&s.excluded.get((String)c[2])==1,c[2]+" excluded and counted");}
  // was "ended notes run counted": a Ledger "death" is also how a menu abandon is recorded, so an unlinked one now counts with an unknown result.
  Stats.Selection ended=Stats.select(Stats.build(Collections.emptyList(),Arrays.asList(ledger(doc))),new Stats.Filter(),100000);check(ended.runs.size()==1&&!ended.runs.get(0).outcome.known(),"ended notes run counted, its death unconfirmed");
  JsonObject open=Recorder.copy(doc);JsonArray kept=new JsonArray();for(JsonElement e:records(open))if(!"ending".equals(HistoryIndex.str(e.getAsJsonObject(),"type")))kept.add(e);open.add("records",kept);
  Stats.Selection inc=Stats.select(Stats.build(Collections.emptyList(),Arrays.asList(ledger(open))),new Stats.Filter(),100000);check(inc.runs.isEmpty()&&inc.excluded.get("incomplete")==1,"notes run without ending excluded (metrics.md 10.1)");
  check(Arrays.equals(original,Files.readAllBytes(root.resolve("IRONCLAD/first.run"))),"read only native files");
 }

 static void gapsAndCombinations(){
  // Heart win; act-3 win without keys; act-4 death; abandon; act-1 death; a .run without a result.
  JsonObject heart=nativeRun("H",20,57,true,null,plus(act123(),"54:E:Shield and Spear:8","56:B:The Heart:30"));
  heart.add("card_choices",new JsonParser().parse("[{\"floor\":0,\"picked\":\"Neow Card\",\"not_picked\":[]},{\"floor\":1,\"picked\":\"Shrug It Off\",\"not_picked\":[\"Anger\"]},{\"floor\":16,\"picked\":\"SKIP\",\"not_picked\":[\"Carnage\"]}]"));
  heart.add("boss_relics",new JsonParser().parse("[{\"picked\":\"Runic Cube\",\"not_picked\":[\"Sozu\",\"Calling Bell\"]},{\"not_picked\":[\"Astrolabe\",\"Ectoplasm\",\"Snecko Eye\"]}]"));
  JsonObject act3=nativeRun("V3",20,52,true,null,act123());act3.add("gold_per_floor",new JsonParser().parse("[120,110,150]"));act3.add("potions_floor_usage",new JsonArray());
  JsonObject act4=nativeRun("D4",20,56,false,"The Heart",plus(act123(),"54:E:Shield and Spear:8","56:B:The Heart:70"));act4.add("potions_floor_usage",new JsonParser().parse("[1,1,16]"));
  JsonObject quit=nativeRun("AB",20,5,false,null,"1:M:Cultist:3");
  JsonObject early=nativeRun("D1",20,3,false,"Jaw Worm","1:M:Cultist:5","3:M:Jaw Worm:40");early.add("card_choices",new JsonParser().parse("[{\"floor\":1,\"picked\":\"Inflame\",\"not_picked\":[\"Anger+1\"]}]"));
  JsonObject noResult=nativeRun("NU",20,10,null,null);
  // Ledger: one record linked to H; an M1 act-3 win written as "death"; a State Notes victory and death with no heart or act-3 facts; one without an ending.
  JsonObject link=doc(Recorder.SCHEMA,"L-link",null);JsonArray lr=records(link);JsonObject enter=parse("{\"type\":\"floor_enter\",\"act\":1,\"floor\":1,\"hp\":70,\"gold_gained\":0}");add(lr,enter,"x",1);decision(lr,"r1","x",2,1,1,"card");
  JsonObject s=state(4,57);s.addProperty("gold_gained",300);JsonObject play=Recorder.object("game_play_id");play.addProperty("play_id","H");play.addProperty("victory",true);play.addProperty("heart_kill",true);add(lr,play,"x",4);add(lr,ending("victory",4,57,true,true),"x",5);lr.get(lr.size()-1).getAsJsonObject().add("state",s);
  JsonObject lv3=doc(Recorder.SCHEMA,"L-act3",null);decision(records(lv3),"r2","y",1,1,1,"skip");add(records(lv3),ending("death",3,51,true,false),"y",3);
  JsonObject leg=doc("sts1-state-v1","L-legacy-win",null);add(records(leg),ending("victory",4,57,null,null),"z",1);
  JsonObject legDeath=doc("sts1-state-v1","L-legacy-death",null);add(records(legDeath),ending("death",1,2,null,null),"w",1);
  JsonObject inc=doc(Recorder.SCHEMA,"L-open",null);decision(records(inc),"r3","v",1,1,1,"card");
  List<Stats.NativeFile> natives=Arrays.asList(nf(heart,"IRONCLAD","h.run"),nf(act3,"IRONCLAD","v.run"),nf(act4,"IRONCLAD","d4.run"),nf(quit,"IRONCLAD","ab.run"),nf(early,"IRONCLAD","d1.run"),nf(noResult,"IRONCLAD","nu.run"));
  List<Ledger> ledgers=Arrays.asList(ledger(link),ledger(lv3),ledger(leg),ledger(legDeath),ledger(inc));
  Stats.Index index=Stats.build(natives,ledgers);
  check(index.runs.size()==10&&index.linked==1&&index.unlinked==4,"10.9 one merged population: linked record joins its .run, unlinked records stand alone ("+index.runs.size()+")");
  Run h=run(index,"H");check(h.has(Fact.NATIVE)&&h.has(Fact.LEDGER)&&h.gold.status==Fact.Status.EXACT&&h.gold.value==300&&h.gold.source.equals(Fact.LEDGER),"merged run: Ledger gold_gained is exact");
  check(Boolean.TRUE.equals(run(index,"ledger:L-act3").act3.value)&&"victory".equals(run(index,"ledger:L-act3").outcome.value),"M2 fix: an act-3 clear the Ledger wrote as death is a victory by act3_cleared");
  check(!run(index,"ledger:L-legacy-death").outcome.known()&&Boolean.FALSE.equals(run(index,"ledger:L-legacy-death").act3.value),"an unlinked Ledger death is unconfirmed (menu abandons write one too)");

  // M2 acceptance: 3 result filters x abandon x 2 bases; expected wins and losses per run (heart / act 3).
  Map<String,String> heartWins=new TreeMap<>(),act3Wins=new TreeMap<>();
  for(String[] e:new String[][]{{"H","win","win"},{"V3","loss","win"},{"D4","loss","win"},{"AB","loss","loss"},{"D1","loss","loss"},{"ledger:L-act3","loss","win"},{"ledger:L-legacy-win","unknown","win"},{"NU","unknown","unknown"},{"ledger:L-legacy-death","unknown","unknown"}}){heartWins.put(e[0],e[1]);act3Wins.put(e[0],e[2]);}
  for(boolean basis:new boolean[]{true,false})for(boolean abandon:new boolean[]{false,true})for(String result:Arrays.asList("all","win","loss")){Stats.Filter f=filter(basis,result,abandon);Stats.Selection sel=Stats.select(index,f,100000);Map<String,Metric> m=Metrics.compute(sel,f);
   Set<String> want=new TreeSet<>();int wins=0,losses=0,unknowns=0;for(Map.Entry<String,String> e:(basis?heartWins:act3Wins).entrySet()){if(e.getKey().equals("AB")&&!abandon)continue;String v=e.getValue();if(v.equals("win"))wins++;if(v.equals("loss"))losses++;if(v.equals("unknown"))unknowns++;if(result.equals("all")||result.equals(v))want.add(e.getKey());}
   String what=(basis?"heart":"act3")+"/"+result+"/abandon="+abandon;check(ids(sel).equals(want),what+": runs "+ids(sel)+" want "+want);check(sel.excluded.get("incomplete")==1,what+": incomplete counted out");check(abandon||sel.excluded.get("abandon")==1,what+": abandon counted out");
   if(result.equals("all")){check(is(m(m,"win"),wins,wins+losses)&&m(m,"win").excluded==unknowns&&m(m,"win").status.equals("ok"),what+": win "+m(m,"win").numerator+"/"+m(m,"win").denominator);}
   else{check(sel.excluded.get("result_unknown")==unknowns,what+": unknown results stay out of win/loss");for(Metric x:m.values())check(x.winRate==x.status.equals("hidden_by_result_filter"),what+": 10.10 only win-rate metrics read — ("+x.key+")");check(m(m,"runs").status.equals("ok")&&m(m,"damage:Jaw Worm").shown(),what+": other metrics still shown");}
  }
  Stats.Filter all=new Stats.Filter();Map<String,Metric> m=metrics(index,all);
  check(Stats.win(run(index,"D4"),false)&&!Stats.win(run(index,"D4"),true),"10.2 an act-4 death wins on the act-3 basis");
  // 10.3 survival by death facts: H all four acts, V3 ends in act 3 with a win, D4 dies in act 4, L-act3 cleared act 3, legacy win reached act 4.
  check(is(m(m,"act:3"),5,5)&&is(m(m,"act:4"),2,3),"10.3 act survival from death facts: act3 "+m(m,"act:3").numerator+"/"+m(m,"act:3").denominator+" act4 "+m(m,"act:4").numerator+"/"+m(m,"act:4").denominator);
  // 10.5 native card_choices: fight floors only (Neow's floor 0 is not a reward), a Ledger floor replaces the .run floor.
  check(is(m(m,"pick:1:Inflame"),1,1)&&!m.containsKey("pick:1:Neow Card")&&!m.containsKey("pick:1:Shrug It Off")&&is(m(m,"pick:1:Carnage"),0,1)&&is(m(m,"pick:1:Bash"),1,2),"10.5 .run card_choices in pick rates, Ledger floors first");
  check(is(m(m,"skip:1"),2,4)&&is(m(m,"bowl:1"),0,2),"skip over all screens; bowl only where the bowl was offered");
  check(is(m(m,"boss:1:SKIP"),0,1)&&is(m(m,"boss:2:SKIP"),1,1)&&is(m(m,"boss:1:Runic Cube"),1,1)&&is(m(m,"boss:2:Astrolabe"),0,1),"10.6 boss relic skip is a candidate");
  check(is(m(m,"encounter_potions:Jaw Worm"),2,2)&&m(m,"encounter_potions:Jaw Worm").value()==1,"10.7 per-fight potions from potions_floor_usage");
  // TinyCat core: hp loss with an exact entry (H via floor_enter) and the floor-1 estimate (V3, D4: 60+10-6; D1 floor 3 loses 0), heal split, gold exact and estimated.
  Metric loss=m(m,"hp_loss:1:Jaw Worm");check(is(loss,18,4)&&loss.estimated==2,"hp loss exact and estimated: "+loss.numerator+"/"+loss.denominator+" est "+loss.estimated);
  check(m(m,"hp_loss_heal:1:Hexaghost").denominator==3&&m(m,"hp_loss_heal:1:Hexaghost").numerator==0&&m(m,"hp_loss_noheal:1:Jaw Worm").denominator==3,"heal split at Burning Blood's 6");
  check(is(m(m,"gold_gained:exact"),300,1)&&is(m(m,"gold_gained:estimated"),115,1)&&m(m,"gold_gained").estimated==1&&is(m(m,"gold_gained"),415,2),"gold exact from gold_gained, estimate from gold_per_floor (no neow_bonus: TinyCat deducts 45)");
  // GoldCheck pairs a Ledger exact gold_gained with its linked .run estimate by play_id.
  JsonObject gn=nativeRun("GX",20,3,false,"Jaw Worm","3:M:Jaw Worm:40");gn.add("gold_per_floor",new JsonParser().parse("[120,110,150]"));
  JsonObject gl=doc(Recorder.SCHEMA,"L-gold",null);JsonObject gp=Recorder.object("game_play_id");gp.addProperty("play_id","GX");add(records(gl),gp,"x",1);JsonObject ge=ending("death",1,3,false,false);ge.getAsJsonObject("state").addProperty("gold_gained",100);add(records(gl),ge,"x",2);
  List<GoldCheck.Row> gr=GoldCheck.rows(Stats.build(Arrays.asList(nf(gn,"IRONCLAD","g.run")),Collections.emptyList()),Arrays.asList(ledger(gl),ledger(doc(Recorder.SCHEMA,"L-nogold",null))));
  check(gr.size()==1&&gr.get(0).exact==100&&gr.get(0).estimate==115,"GoldCheck pairs exact and estimate by play_id");
  // metrics.md 5.2 TinyCat formula: the Neow constant depends on neow_bonus (HUNDRED_GOLD 40) and is 0 with the NO_GOLD cost; was a flat 99.
  JsonObject g1=nativeRun("G1",20,3,false,"Jaw Worm","3:M:Jaw Worm:40"),g2=nativeRun("G2",20,3,false,"Jaw Worm","3:M:Jaw Worm:40");g1.addProperty("neow_bonus","HUNDRED_GOLD");g1.addProperty("neow_cost","NONE");g1.add("gold_per_floor",new JsonParser().parse("[150,140,200]"));
  g2.addProperty("neow_bonus","TWO_FIFTY_GOLD");g2.addProperty("neow_cost","NO_GOLD");g2.add("gold_per_floor",new JsonParser().parse("[50,60]"));
  check(is(m(metrics(Stats.build(Arrays.asList(nf(g1,"IRONCLAD","g1.run"),nf(g2,"IRONCLAD","g2.run")),Collections.emptyList()),all),"gold_gained:estimated"),230,2),"gold estimate: Neow constant by bonus, none with NO_GOLD (170 + 60)");
  // metrics.md 6.4: without boss fights the act bounds fall back to 17/34/52, so floor 17 is still act 1.
  check(metrics(Stats.build(Arrays.asList(nf(nativeRun("NB",20,17,false,"Cultist","17:M:Cultist:5"),"IRONCLAD","nb.run")),Collections.emptyList()),all).containsKey("hp_loss:1:Cultist"),"act fallback 17/34/52 without boss fights");
  // metrics.md 6.4: a run that ends on the act-1 boss chest floor never entered act 2.
  check(!metrics(Stats.build(Arrays.asList(nf(nativeRun("CH",20,17,false,null,"1:M:Jaw Worm:10","16:B:Hexaghost:20"),"IRONCLAD","ch.run")),Collections.emptyList()),all).containsKey("act_hp_loss:2"),"boss chest floor stays in the boss act");
  check(m(m,"act_hp_loss:1").denominator>0&&m(m,"coverage:act_hp_loss").unit.equals("run_act"),"act HP loss per run and act");
  // 10.8 coverage in each metric's own unit; fights fall back to runs when some run's fights are unknown.
  check(m(m,"coverage:act").unit.equals("run_act")&&m(m,"coverage:boss_relics").unit.equals("run_act")&&m(m,"coverage:damage").unit.equals("run")&&m(m,"coverage:reward").unit.equals("run")&&m(m,"win").coverUnit.equals("run"),"10.8 coverage units with unknown fights");
  Stats.Index fights=Stats.build(Arrays.asList(nf(heart,"IRONCLAD","h.run"),nf(act4,"IRONCLAD","d4.run"),nf(early,"IRONCLAD","d1.run")),Collections.emptyList());Map<String,Metric> fm=metrics(fights,all);
  check(fm.get("coverage:damage").unit.equals("combat")&&is(fm.get("coverage:damage"),16,16)&&fm.get("coverage:reward").unit.equals("choice_set")&&fm.get("pick:1:Inflame").coverUnit.equals("choice_set")&&fm.get("coverage:potions_by_encounter").unit.equals("combat")&&is(fm.get("coverage:potions_by_encounter"),7,16),"10.8 coverage by fight and by screen");
  // 10.9 sources: native filter keeps linked runs, Ledger filter keeps linked and unlinked records, together they are the one population.
  Stats.Filter nat=new Stats.Filter();nat.source=Fact.NATIVE;Stats.Filter led=new Stats.Filter();led.source=Fact.LEDGER;check(Stats.select(index,nat,100000).runs.size()==5&&Stats.select(index,led,100000).runs.size()==4,"10.9 sources are filters on one population");
  Stats.Filter slot=new Stats.Filter();slot.slot=2;check(Stats.select(index,slot,100000).runs.isEmpty(),"slot-0 runs are not in slot 2");
  JsonObject daily=nativeRun("DAILY",20,3,false,"Jaw Worm","3:M:Jaw Worm:40");daily.addProperty("is_daily",true);Stats.Index withDaily=Stats.build(Arrays.asList(nf(daily,"DAILY","x.run")),Collections.emptyList());check(Stats.select(withDaily,all,100000).excluded.get("mode")==1,"daily never counted");
  // Ledger endings with outcome_basis are trusted for death; older "death" endings stay missing unless facts prove a win.
  JsonObject dead=doc(Recorder.SCHEMA,"L-dead",null);JsonObject de=ending("death",2,20,false,false);de.addProperty("outcome_basis","death_screen_cause");add(records(dead),de,"x",1);
  JsonObject old=doc(Recorder.SCHEMA,"L-old-death",null);add(records(old),ending("death",2,20,false,false),"x",1);
  Stats.Index basis=Stats.build(Collections.emptyList(),Arrays.asList(ledger(dead),ledger(old)));Map<String,String> outs=new HashMap<>();for(Run r:basis.runs)outs.put(r.id,String.valueOf(r.outcome.value));
  check(outs.containsValue("death")&&outs.containsValue("null")&&outs.size()==2,"outcome_basis death trusted, older death missing: "+outs);
  // Save slot (metrics.md 2): Ledger save_slot, else the linked .run's directory, else missing and in no slot.
  JsonObject bare=doc(Recorder.SCHEMA,"L-noslot",null);bare.getAsJsonObject("metadata").remove("save_slot");add(records(bare),ending("victory",4,57,true,true),"x",1);
  Stats.Index noSlot=Stats.build(Collections.emptyList(),Arrays.asList(ledger(bare)));check(Stats.select(noSlot,all,100000).excluded.get("slot")==1&&Stats.select(noSlot,slot,100000).excluded.get("slot")==1,"unlinked record without save_slot is in no slot");
  JsonObject bareLinked=doc(Recorder.SCHEMA,"L-noslot-2","S2");bareLinked.getAsJsonObject("metadata").remove("save_slot");add(records(bareLinked),ending("victory",4,57,true,true),"x",1);
  Stats.Index fromDir=Stats.build(Arrays.asList(nf(nativeRun("S2",20,57,true,null,"56:B:The Heart:3"),"2_IRONCLAD","s.run")),Arrays.asList(ledger(bareLinked)));check(fromDir.runs.get(0).slot.value==2&&Stats.select(fromDir,slot,100000).runs.size()==1,"older record takes the linked .run's slot");
  JsonObject slotted=doc(Recorder.SCHEMA,"L-slot-1","S1");slotted.getAsJsonObject("metadata").addProperty("save_slot",1);add(records(slotted),ending("victory",4,57,true,true),"x",1);
  Stats.Index fromLedger=Stats.build(Arrays.asList(nf(nativeRun("S1",20,57,true,null,"56:B:The Heart:3"),"IRONCLAD","s.run")),Arrays.asList(ledger(slotted)));Stats.Filter one=new Stats.Filter();one.slot=1;check(fromLedger.runs.get(0).slot.value==1&&Stats.select(fromLedger,one,100000).runs.size()==1&&Stats.select(fromLedger,all,100000).runs.isEmpty(),"Ledger save_slot beats the directory; slots never merge");

  // 10.4 read order: shuffled sources give the same metrics, including duplicate and conflicting copies.
  JsonObject changed=Recorder.copy(early);changed.addProperty("playtime",999);JsonObject twin=doc(Recorder.SCHEMA,"L-twin",null);JsonObject p2=Recorder.object("game_play_id");p2.addProperty("play_id","V3");add(records(twin),p2,"t",1);JsonObject twin2=Recorder.copy(twin);twin2.getAsJsonObject("metadata").addProperty("run_id","L-twin-2");
  List<Stats.NativeFile> n2=new ArrayList<>(natives);n2.add(nf(Recorder.copy(heart),"IRONCLAD","h-copy.run"));n2.add(nf(changed,"IRONCLAD","d1-changed.run"));List<Ledger> l2=new ArrayList<>(ledgers);l2.add(ledger(twin));l2.add(ledger(twin2));
  Stats.Index base=Stats.build(n2,l2);check(base.nativeDuplicates==2&&run(base,"D1").conflict&&run(base,"V3").conflict&&base.conflicts==2,"differing copies and two records on one play_id conflict");
  Random random=new Random(7);List<Stats.Filter> filters=new ArrayList<>();for(boolean b:new boolean[]{true,false})for(String r:Arrays.asList("all","win","loss"))filters.add(filter(b,r,true));
  Map<Stats.Filter,String> expected=new HashMap<>();for(Stats.Filter f:filters)expected.put(f,dump(base,f));
  for(int i=0;i<30;i++){List<Stats.NativeFile> a=new ArrayList<>(n2);List<Ledger> b=new ArrayList<>(l2);Collections.shuffle(a,random);Collections.shuffle(b,random);Stats.Index shuffled=Stats.build(a,b);for(Stats.Filter f:filters)check(dump(shuffled,f).equals(expected.get(f)),"10.4 shuffled read order changes nothing (round "+i+")");}
 }

 /** architecture.md: stats reads only the record schema; the build also compiles it alone against Gson. */
 static JsonObject row(JsonObject doc,String metric,String scope,String dims){for(JsonElement e:doc.getAsJsonArray("rows")){JsonObject r=e.getAsJsonObject();if(r.get("metric").getAsString().equals(metric)&&r.get("scope").getAsString().equals(scope)&&r.get("dims").toString().equals(dims))return r;}throw new AssertionError("no row "+metric+" "+scope+" "+dims);}
 static boolean nul(JsonObject r,String k){return r.get(k)==null||r.get(k).isJsonNull();}
 /** TinyCat parity export (tools/tinycat-compare README 2): exact filters, slot by directory, per-act counts, missing as null. */
 static void export(){
  JsonObject a=nativeRun("E1",20,20,true,null,"3:M:Jaw Worm:7","16:B:Hexaghost:20","18:M:Cultist:4");a.add("path_per_floor",new JsonParser().parse("[\"M\",\"$\",\"M\",\"R\",\"$\",\"?\",\"?\",\"?\",\"?\",\"?\",\"?\",\"?\",\"?\",\"?\",\"?\",\"B\",null,\"M\",\"$\",\"R\"]"));
  a.add("items_purged",new JsonParser().parse("[\"Strike_R\",\"Defend_R\"]"));a.add("items_purged_floors",new JsonParser().parse("[2,19]"));a.add("items_purchased",new JsonParser().parse("[\"Swift Potion\",\"SwiftPotion\",\"Inflame+1\"]"));a.add("item_purchase_floors",new JsonParser().parse("[2,5,19]"));
  a.add("campfire_choices",new JsonParser().parse("[{\"floor\":4,\"key\":\"SMITH\",\"data\":\"Bash+1\"},{\"floor\":20,\"key\":\"REST\"}]"));a.add("event_choices",new JsonParser().parse("[{\"floor\":6,\"event_name\":\"Big Fish\",\"player_choice\":\"Banana\"},{\"floor\":7,\"event_name\":\"Big Fish\",\"player_choice\":\"Box\",\"cards_removed\":[\"Strike_R\"]}]"));
  a.add("relics",new JsonParser().parse("[\"Burning Blood\",\"Empty Cage\"]"));a.add("boss_relics",new JsonParser().parse("[{\"picked\":\"Empty Cage\",\"not_picked\":[\"Sozu\"]}]"));
  JsonObject other=nativeRun("E2",20,3,false,"Jaw Worm","3:M:Jaw Worm:40"),low=nativeRun("E3",10,3,false,"Jaw Worm","3:M:Jaw Worm:40");
  Stats.Index index=Stats.build(Arrays.asList(nf(a,"IRONCLAD","a.run"),nf(other,"2_IRONCLAD","b.run"),nf(low,"IRONCLAD","c.run")),Collections.emptyList());
  JsonObject doc=Export.export(index,0,null,"Spire Ledger test"),meta=doc.getAsJsonObject("meta");
  check(meta.get("filters").toString().equals("{\"tinycat_alignment\":true,\"save_slot\":0,\"ascension\":[20],\"modes\":\"exclude_daily_special_endless\",\"include_abandons\":true,\"seed_filter\":\"all\",\"win_definition\":\"heart_kill\",\"sources\":[\"native\"]}"),"export filters are the alignment values "+meta.get("filters"));
  check(meta.getAsJsonObject("run_ids").get("ALL").toString().equals("[\"IRONCLAD/a.run\"]")&&meta.getAsJsonObject("run_ids").get("WATCHER").getAsJsonArray().size()==0,"slot 0 reads unprefixed directories, A20 only");
  check(Export.export(index,2,null,"t").getAsJsonObject("meta").getAsJsonObject("run_ids").get("ALL").toString().equals("[\"2_IRONCLAD/b.run\"]"),"slot 2 reads 2_ directories");
  check(Export.export(index,0,new HashSet<>(Arrays.asList("IRONCLAD/c.run")),"t").getAsJsonObject("meta").getAsJsonObject("run_ids").get("ALL").toString().equals("[\"IRONCLAD/c.run\"]"),"run list replaces the scope");
  check(row(doc,"win","ALL","{}").get("numerator").getAsDouble()==0&&row(doc,"win","ALL","{}").get("denominator").getAsDouble()==1,"heart-kill win basis");
  JsonObject gold=row(doc,"gold_gained","IRONCLAD","{}");check(nul(gold,"value")&&gold.get("status").getAsString().equals("missing")&&gold.get("exact").getAsInt()==0,"missing gold is null, never 0");
  check(nul(row(doc,"time","ALL","{\"result\":\"win\"}"),"value"),"no heart win: winning time is null");
  check(row(doc,"damage","ALL","{\"act\":\"2\",\"encounter\":\"Cultist\"}").get("value").getAsDouble()==4,"damage per act and encounter");
  check(row(doc,"purges","ALL","{\"source\":\"SHOP\"}").get("value").getAsDouble()==2&&row(doc,"purges","ALL","{\"source\":\"SHOP\",\"act\":\"2\"}").get("value").getAsDouble()==1,"shop purges overall and per act");
  check(row(doc,"purges","ALL","{\"source\":\"EVENT\"}").get("value").getAsDouble()==1&&nul(row(doc,"purges","ALL","{\"source\":\"NEOW\"}"),"value"),"event purges; Neow purges missing without neow_bonus_log");
  JsonObject cage=row(doc,"purges","ALL","{\"source\":\"EMPTY_CAGE\"}");check(cage.get("value").getAsDouble()==2&&cage.get("estimated").getAsInt()==1,"Empty Cage +2, estimated");
  JsonObject total=row(doc,"purges","ALL","{\"source\":\"ALL\"}");check(total.get("value").getAsDouble()==5&&total.get("partial_sources").toString().equals("[\"NEOW\"]"),"all purges name the missing source");
  check(row(doc,"purchase","ALL","{\"act\":\"0\",\"item\":\"SwiftPotion\"}").get("value").getAsDouble()==2&&row(doc,"purchase","ALL","{\"act\":\"2\",\"item\":\"Inflame\"}").get("value").getAsDouble()==1,"potion IDs normalized, cards lose +N, act 0 sums");
  check(row(doc,"shop_visits","ALL","{\"act\":\"1\"}").get("value").getAsDouble()==2&&row(doc,"shop_visits","ALL","{\"act\":\"0\"}").get("value").getAsDouble()==3,"shop visits from $ floors");
  check(row(doc,"campfire","ALL","{\"act\":\"0\",\"action\":\"TOTAL\"}").get("value").getAsDouble()==2&&row(doc,"upgrade","ALL","{\"source\":\"campfire\",\"act\":\"1\",\"card\":\"Bash\"}").get("value").getAsDouble()==1,"campfire actions and upgrades per act");
  // metrics.md 1.2: a scope with no runs (WATCHER here) counts known zeros; its rates and means stay missing.
  JsonObject none=row(doc,"win","WATCHER","{}"),allPurges=row(doc,"purges","WATCHER","{\"source\":\"ALL\"}");
  check(none.get("numerator").getAsDouble()==0&&nul(none,"value")&&row(doc,"purges","WATCHER","{\"source\":\"SHOP\"}").get("value").getAsDouble()==0&&allPurges.get("value").getAsDouble()==0&&allPurges.get("partial_sources").getAsJsonArray().size()==0
   &&row(doc,"campfire","WATCHER","{\"act\":\"0\",\"action\":\"TOTAL\"}").get("value").getAsDouble()==0&&row(doc,"time","WATCHER","{\"result\":\"all\"}").get("numerator").getAsDouble()==0,"empty scope: counts and sums are 0, not null");
  JsonObject fish=row(doc,"event_related","ALL","{\"act\":\"1\",\"event\":\"Big Fish\"}");check(fish.get("denominator").getAsDouble()==1&&fish.get("numerator").getAsDouble()==0,"event win rate once per run per act");
 }
 static void boundary(Path src)throws Exception{int files=0;try(DirectoryStream<Path> d=Files.newDirectoryStream(src,"*.java")){for(Path p:d){files++;for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)){String t=line.trim();
   check(!t.contains("com.megacrit")&&!t.contains("com.badlogic")&&!t.contains("basemod")&&!t.contains("com.evacipated"),"no game or mod reference in "+p.getFileName()+": "+t);
   if(t.startsWith("import "))check(t.matches("import (static )?(java\\.|com\\.google\\.gson\\.|spireledger\\.stats\\.).*"),"stats imports only JDK, Gson and itself: "+p.getFileName()+": "+t);}}}check(files>0,"stats sources found at "+src);}
}
