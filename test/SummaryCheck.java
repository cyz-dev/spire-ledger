package spireledger;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import spireledger.stats.*;

/** Summary path equals the full path field by field; stale, damaged or missing summaries fall back; scans write nothing. Synthetic records only. */
public final class SummaryCheck {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static JsonObject parse(String s){return new JsonParser().parse(s).getAsJsonObject();}
    static JsonObject card(String id,String instance,int upgrades){JsonObject c=new JsonObject();c.addProperty("id",id);c.addProperty("name",id+"-名");c.addProperty("instance_id",instance);c.addProperty("upgrades",upgrades);c.addProperty("misc",0);return c;}
    static JsonObject state(int act,int floor,JsonObject... deck){JsonObject s=parse("{\"class\":\"IRONCLAD\",\"ascension\":20,\"hp\":60,\"max_hp\":80,\"gold\":99,\"gold_gained\":7,\"room\":\"MonsterRoom\",\"x\":1,\"y\":2,\"resource_timing\":\"direct\",\"relics\":[{\"id\":\"Burning Blood\",\"counter\":-1}],\"potions\":[]}");s.addProperty("act",act);s.addProperty("floor",floor);JsonArray d=new JsonArray();for(JsonObject c:deck)d.add(c);s.add("deck",d);return s;}
    static JsonArray reward(String id){return new JsonParser().parse("[{\"candidate_id\":\"card:0\",\"type\":\"card\",\"card\":{\"id\":\""+id+"\",\"name\":\""+id+"-名\",\"upgrades\":0}},{\"candidate_id\":\"card:1\",\"type\":\"card\",\"card\":{\"id\":\"Anger\",\"upgrades\":1}},{\"candidate_id\":\"skip\",\"type\":\"skip\"},{\"candidate_id\":\"bowl\",\"type\":\"bowl\"}]").getAsJsonArray();}
    static JsonObject won(JsonObject state){JsonObject e=Recorder.object("ending");e.addProperty("outcome","victory");e.add("state",state);return e;}
    // Every field of the index objects; only how the summary was obtained may differ.
    static void same(Object a,Object b,String what)throws Exception{
        for(Field f:a.getClass().getDeclaredFields()){if(Modifier.isStatic(f.getModifiers())||f.getName().equals("summarized"))continue;f.setAccessible(true);Object x=f.get(a),y=f.get(b);
            if(f.getName().equals("search")){List<String> p=new ArrayList<>(Arrays.asList(((String)x).split(" "))),q=new ArrayList<>(Arrays.asList(((String)y).split(" ")));Collections.sort(p);Collections.sort(q);check(p.equals(q),what+": search");continue;}
            if(f.getName().equals("values")){Map<?,?> m=(Map<?,?>)x,n=(Map<?,?>)y;check(m.keySet().equals(n.keySet()),what+": values keys");for(Object k:m.keySet())check(Arrays.equals((double[])m.get(k),(double[])n.get(k)),what+": values "+k);continue;}
            check(Objects.equals(x,y),what+": "+f.getName()+" "+x+" vs "+y);}
    }
    // The kernel's logical run from one record, as JSON: the summary and the full record must give the same one.
    static Run run(JsonObject doc,HistoryIndex.Summary s){return Stats.build(Collections.<Stats.NativeFile>emptyList(),Collections.singletonList(new Ledger(doc,s.current,s.conflict))).runs.get(0);}
    static void same(Run a,Run b,String what){String x=new Gson().toJson(a),y=new Gson().toJson(b);check(x.equals(y),what+": "+x+" vs "+y);}
    static void equal(Path dir,String what)throws Exception{
        HistoryIndex.Summary s=HistoryIndex.load(dir),full=HistoryIndex.loadFull(dir);check(s.summarized&&!full.summarized,what+": summary used");same(s,full,what+" Summary");
        same(run(HistoryIndex.facts(s),s),run(HistoryIndex.document(full.file),full),what+" Run");
    }
    static void equal(JsonObject doc,Path dir,String what)throws Exception{
        HistoryIndex.Summary full=HistoryIndex.summarize(doc,false),s=HistoryIndex.fromSummary(RunSummary.project(doc,0,0),dir);full.file=s.file;same(s,full,what+" Summary");
        same(run(RunSummary.project(doc,0,0),s),run(doc,full),what+" Run");
    }
    static Map<Path,byte[]> files(Path root)throws Exception{Map<Path,byte[]> out=new TreeMap<>();try(java.util.stream.Stream<Path> w=Files.walk(root)){for(Path p:(Iterable<Path>)w.filter(Files::isRegularFile)::iterator)out.put(p,Files.readAllBytes(p));}return out;}
    static void copy(Path from,Path to)throws Exception{Files.createDirectories(to);try(DirectoryStream<Path> s=Files.newDirectoryStream(from)){for(Path p:s)Files.copy(p,to.resolve(p.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]).resolve("runs");Files.createDirectories(root);
        JsonObject meta=parse("{\"game_version\":\"test\",\"mod_version\":\"0.5.0\",\"seed\":\"-9223372036854775808\",\"play_id\":\"\",\"dataset_origin\":\"synthetic\"}");
        // A run touching every projected shape: Neow, reward picks, instance changes, shop, save-load branch, unknown turn parent, a conflicting duplicate and the ending.
        Recorder r=new Recorder(root,meta,null);String id=r.runId;
        JsonObject s0=state(1,0,card("Strike_R","one",0),card("Strike_R","two",0));
        r.begin("neow","NEOW_OPTION",s0,new JsonParser().parse("[{\"type\":\"neow_option\",\"candidate_id\":\"neow:0\",\"slot\":0,\"reward_type\":\"REMOVE_CARD\",\"drawback\":\"NONE\",\"label\":\"[ 移除 ]\"},{\"type\":\"neow_option\",\"candidate_id\":\"neow:1\",\"slot\":1,\"reward_type\":\"TEN_PERCENT_HP_BONUS\",\"drawback\":\"NONE\",\"label\":\"[ +8 ]\"}]").getAsJsonArray(),"neow:0","human",parse("{\"variant\":\"blessing\"}"));r.settle(state(1,0,card("Strike_R","one",0)));
        JsonObject enter=parse("{\"type\":\"floor_enter\",\"act\":1,\"floor\":1,\"room\":\"MonsterRoom\",\"hp\":54,\"max_hp\":80,\"gold\":99,\"gold_gained\":0}");r.append(enter);
        r.begin("reward-1","COMBAT_REWARD",state(1,1,card("Strike_R","one",0)),reward("Inflame"),"card:0","human");r.settle(state(1,1,card("Strike_R","one",0),card("Inflame","three",0)));
        r.begin("reward-2","COMBAT_REWARD",state(1,2,card("Strike_R","one",0)),reward("Bash"),"skip","known_automation");r.settle(state(1,2,card("Strike_R","one",0)));
        r.begin("reward-3","COMBAT_REWARD",state(1,3,card("Strike_R","one",0)),reward("Bash"),"card:0","human");r.interrupt("closed");
        JsonObject change=Recorder.object("build_change");change.addProperty("status","settled");change.addProperty("reason","campfire");change.add("before",state(1,4,card("Strike_R","one",0),card("Inflame","three",0),card("Defend_R","four",0)));change.add("after",state(1,4,card("Strike_R","one",1),card("Defend_R","four",0),card("Shrug It Off","five",0)));r.append(change);
        JsonObject loose=Recorder.copy(change);loose.add("before",state(1,4,card("Strike_R","",0)));r.append(loose);
        JsonObject offer=Recorder.object("shop_offer");offer.addProperty("shop_key","visit-1");offer.add("state",state(1,5,card("Strike_R","one",1)));offer.add("candidates",new JsonParser().parse("[{\"type\":\"shop_card\",\"candidate_id\":\"card:s\",\"price\":75,\"purchasable\":true,\"card\":{\"id\":\"Pommel Strike\",\"instance_id\":\"s\",\"upgrades\":0}},{\"type\":\"shop_relic\",\"candidate_id\":\"relic:0\",\"price\":300,\"purchasable\":false,\"relic\":\"Anchor\"},{\"type\":\"leave\",\"candidate_id\":\"leave\"}]"));r.append(offer);
        r.begin("visit-1:leave","SHOP",state(1,5),offer.getAsJsonArray("candidates"),"leave","human",parse("{\"shop_key\":\"visit-1\"}"));r.settle(state(1,5));
        JsonObject map=Recorder.object("map");map.addProperty("act",1);map.add("nodes",new JsonParser().parse("[[1,2],[3]]"));r.append(map);
        JsonObject ref=r.checkpoint(state(1,6,card("Strike_R","one",1)));String parent=r.branch;r.close();
        Recorder b=new Recorder(root,meta,id);b.resume(ref,state(1,6,card("Strike_R","one",1)));
        b.begin("reward-4","COMBAT_REWARD",state(2,18,card("Strike_R","one",1)),reward("Carnage"),"card:0","human");b.settle(state(2,18,card("Strike_R","one",1),card("Carnage","six",0)));
        JsonObject dup=null;for(JsonElement e:b.document().getAsJsonArray("records"))if(dup==null&&"decision".equals(HistoryIndex.str(e.getAsJsonObject(),"type")))dup=Recorder.copy(e.getAsJsonObject());dup.addProperty("candidate_id","skip");b.append(dup);
        JsonObject play=Recorder.object("game_play_id");play.addProperty("play_id","play-1");play.addProperty("heart_kill",true);b.append(play);
        JsonObject end=Recorder.object("ending");end.addProperty("outcome","victory");end.addProperty("act3_cleared",true);end.addProperty("heart_kill",true);JsonObject last=state(4,57,card("Strike_R","one",1),card("Carnage","six",0));last.getAsJsonArray("relics").add(parse("{\"id\":\"Anchor\",\"counter\":-1}"));end.add("state",last);b.append(end);b.flush();b.awaitWrites();
        Path dir=root.resolve(id);check(Files.exists(dir.resolve(RunSummary.FILE)),"recorder writes summary.json");
        JsonObject sum=parse(new String(Files.readAllBytes(dir.resolve(RunSummary.FILE)),StandardCharsets.UTF_8));check(sum.get("schema").getAsString().equals(RunSummary.SCHEMA)&&sum.get("events_bytes").getAsLong()==Files.size(dir.resolve("events.jsonl")),"summary marker names the journal");
        for(Map.Entry<String,JsonElement> k:sum.entrySet())check(Arrays.asList("schema","source_schema","events_bytes","last_seq","record_count","metadata","card_ids","card_names","relics","records").contains(k.getKey()),"summary holds facts only: "+k.getKey());
        check(sum.getAsJsonObject("relics").get("Anchor").getAsInt()==57,"relic first floor");check(Files.size(dir.resolve(RunSummary.FILE))<Files.size(dir.resolve("run.json")),"summary is a projection");
        equal(dir,"recorder run");HistoryIndex.Summary full=HistoryIndex.loadFull(dir);check(full.outcome.equals("victory")&&full.conflict&&full.branches.size()==2&&full.picks==2&&full.pending==2,"fixture exercises outcome, conflict, branches, picks, pending");
        Run led=run(HistoryIndex.document(full.file),full);Map<String,Integer> seen=led.counts.get("observed").value;boolean carnage=false;for(Run.Screen x:led.screens.value)carnage|=x.act==2&&"Carnage".equals(x.picked);
        check(!led.unresolvedPath&&carnage&&seen.containsKey("remove:observed:Inflame")&&seen.containsKey("upgrade:observed:Strike_R")&&led.deckSize.value==2,"fixture exercises path, observed changes, final deck");
        Recorder u=new Recorder(root,meta,null);u.begin("r","COMBAT_REWARD",state(1,1),reward("Bash"),"card:0","human");u.settle(state(1,1));JsonObject turn=new JsonObject();turn.addProperty("snapshot_id","missing");u.forkBranch(u.branch,"turn_restore",turn);u.append(won(state(3,51)));u.flush();u.awaitWrites();
        equal(u.directory,"unknown turn parent");check(run(HistoryIndex.facts(HistoryIndex.load(u.directory)),HistoryIndex.load(u.directory)).unresolvedPath,"fixture exercises an unresolved path");u.close();
        Path samples=Paths.get(args.length>1?args[1]:"samples");for(String name:Arrays.asList("run.json","m1-run.json"))equal(parse(new String(Files.readAllBytes(samples.resolve(name)),StandardCharsets.UTF_8)),dir,"samples/"+name);
        // Stale: the summary from an earlier flush is not used, even though it parses.
        Recorder st=new Recorder(root,meta,null);st.begin("r","COMBAT_REWARD",state(1,1),reward("Bash"),"card:0","human");st.settle(state(1,1));st.awaitWrites();Path sd=st.directory;byte[] oldSummary=Files.readAllBytes(sd.resolve(RunSummary.FILE)),oldRun=Files.readAllBytes(sd.resolve("run.json"));
        st.append(won(state(3,51)));st.flush();st.awaitWrites();
        Path stale=root.resolve("stale");copy(sd,stale);Files.write(stale.resolve(RunSummary.FILE),oldSummary);
        Path broken=root.resolve("broken");copy(sd,broken);Files.write(broken.resolve(RunSummary.FILE),"{\"schema\":\"spire-ledger-summary-v1\",\"recor".getBytes(StandardCharsets.UTF_8));
        Path missing=root.resolve("missing");copy(sd,missing);Files.delete(missing.resolve(RunSummary.FILE));
        Path alien=root.resolve("alien");copy(sd,alien);JsonObject other=parse(new String(Files.readAllBytes(sd.resolve(RunSummary.FILE)),StandardCharsets.UTF_8));other.addProperty("schema","spire-ledger-summary-v9");Files.write(alien.resolve(RunSummary.FILE),other.toString().getBytes(StandardCharsets.UTF_8));
        Path nojournal=root.resolve("nojournal");copy(sd,nojournal);Files.delete(nojournal.resolve("events.jsonl"));
        for(Path p:Arrays.asList(stale,broken,missing,alien,nojournal)){HistoryIndex.Summary x=HistoryIndex.load(p);check(!x.summarized&&x.outcome.equals("victory"),"falls back to the full record: "+p.getFileName());same(run(HistoryIndex.facts(x),x),run(HistoryIndex.document(x.file),x),"fallback Run "+p.getFileName());}
        // run.json older than run.json.tmp (a reader blocked the last replace): the newer copy wins, by record count.
        Path held=root.resolve("held");copy(sd,held);Files.copy(held.resolve("run.json"),held.resolve("run.json.tmp"));Files.write(held.resolve("run.json"),oldRun);
        check(HistoryIndex.load(held).summarized&&HistoryIndex.load(held).outcome.equals("victory"),"fresh summary already reflects the newer copy");Files.delete(held.resolve(RunSummary.FILE));
        HistoryIndex.Summary h=HistoryIndex.load(held);check(!h.summarized&&h.outcome.equals("victory")&&h.file.equals(held.resolve("run.json")),"run.json.tmp newer than run.json is chosen");check(HistoryIndex.document(h.file).getAsJsonArray("records").size()==h.records,"detail reads the newer copy");
        Path older=root.resolve("older-tmp");copy(sd,older);Files.write(older.resolve("run.json.tmp"),oldRun);Files.delete(older.resolve(RunSummary.FILE));check(HistoryIndex.load(older).outcome.equals("victory"),"an older run.json.tmp is ignored");
        // Scans only read: no summary is rebuilt on disk, nothing is touched.
        Map<Path,byte[]> before=files(root);HistoryIndex.Index index=HistoryIndex.scan(root,null);Stats.Index stats=StatsIndex.scan(root.resolve("no-native"),index,null);Map<Path,byte[]> after=files(root);
        check(before.keySet().equals(after.keySet()),"scan creates or deletes no file");for(Path p:before.keySet())check(Arrays.equals(before.get(p),after.get(p)),"scan changes no file: "+p);check(stats.errors.isEmpty()&&index.errors.isEmpty(),"scan errors "+index.errors+stats.errors);
        st.close();System.out.println("SummaryCheck PASS: summary equals full path for Summary and Run (recorder run, samples), stale/damaged/missing/foreign/no-journal fall back, newer run.json.tmp chosen, scans write nothing");
    }
}
