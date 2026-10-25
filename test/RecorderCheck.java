package spireledger;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Contract/storage check with synthetic observations. Does NOT prove live hook integration. */
public class RecorderCheck {
    static JsonObject parse(String s){return new JsonParser().parse(s).getAsJsonObject();}
    static JsonObject state(int act,int floor){return parse("{\"act\":"+act+",\"floor\":"+floor+",\"class\":\"IRONCLAD\",\"ascension\":20,\"hp\":60,\"max_hp\":80,\"gold\":123,\"resource_timing\":\"direct_game_thread_at_record\",\"deck\":[{\"id\":\"Strike_R\",\"name\":\"打击\",\"instance_id\":\"one\",\"upgrades\":0,\"misc\":0},{\"id\":\"Strike_R\",\"name\":\"打击\",\"instance_id\":\"two\",\"upgrades\":0,\"misc\":0}],\"relics\":[],\"potions\":[]}");}
    static JsonArray choices(){return new JsonParser().parse("[{\"candidate_id\":\"card:0\",\"type\":\"card\",\"card\":{\"id\":\"Inflame\",\"type\":\"POWER\",\"upgrades\":0}},{\"candidate_id\":\"skip\",\"type\":\"skip\"},{\"candidate_id\":\"bowl\",\"type\":\"bowl\"}]").getAsJsonArray();}
    /** Windows: a reader holding run.json must neither stop recording nor lose the latest write. */
    static void lockedRunJson(Path samples,JsonObject meta)throws Exception {
        Recorder r=new Recorder(samples,meta,null);r.flush();Path run=r.directory.resolve("run.json"),tmp=r.directory.resolve("run.json.tmp");
        try(java.io.FileInputStream held=new java.io.FileInputStream(run.toFile())){r.append(Recorder.object("lock_probe"));r.flush();}
        if(Files.exists(tmp)&&!new String(Files.readAllBytes(tmp),StandardCharsets.UTF_8).contains("lock_probe"))throw new AssertionError("latest run.json lost while held");
        r.flush();if(!new String(Files.readAllBytes(run),StandardCharsets.UTF_8).contains("lock_probe"))throw new AssertionError("run.json not refreshed after release");
        java.io.FileInputStream brief=new java.io.FileInputStream(run.toFile());Files.write(tmp,"{}".getBytes(StandardCharsets.UTF_8));
        Thread release=new Thread(()->{try{Thread.sleep(60);brief.close();}catch(Exception ignored){}});release.start();
        Recorder.replace(tmp,run);release.join();if(!"{}".equals(new String(Files.readAllBytes(run),StandardCharsets.UTF_8)))throw new AssertionError("replace did not wait for a brief reader");
        r.close();System.out.println("RecorderCheck lock: PASS");
    }
    public static void main(String[] args)throws Exception {
        Path samples=Paths.get(args[0]);Files.createDirectories(samples);
        if(!StateMod.reviewedMod("basemod","5.56.0")||!StateMod.reviewedMod("stslib","2.12.0")||StateMod.reviewedMod("RunHistoryPlus","1.8.3"))throw new AssertionError("only BaseMod + StSLib keep the human source label");
        JsonObject meta=parse("{\"game_version\":\"test\",\"mod_version\":\"0.1.0\",\"seed\":\"-9223372036854775808\",\"play_id\":\"synthetic-contract-check\",\"coverage\":\"synthetic; not a real played run\"}");
        lockedRunJson(samples,meta);
        Recorder r=new Recorder(samples,meta,null);JsonObject before=state(1,1);
        assert !r.hasMap(1);JsonObject map=Recorder.object("map");map.addProperty("act",1);map.addProperty("dictionary_version",1);map.add("nodes",new JsonArray());r.append(map);assert r.hasMap(1)&&!r.hasMap(3);
        JsonObject egg=parse("{\"id\":\"Frozen Egg 2\",\"counter\":-1}");before.getAsJsonArray("relics").add(egg);
        String id=r.begin("reward-1","COMBAT_REWARD",before,choices(),"card:0","human");
        assert id!=null;r.begin("reward-1","COMBAT_REWARD",state(1,1),choices(),"skip","human");
        before.addProperty("gold",999); // immutable decision anchor; current resources never backfill it
        JsonObject after=state(1,1);after.addProperty("gold",123);after.getAsJsonArray("deck").add(parse("{\"id\":\"Inflame\",\"instance_id\":\"three\",\"upgrades\":1,\"misc\":0}"));r.settle(after);
        assert r.begin("reward-1","COMBAT_REWARD",after,choices(),"skip","human")==null;
        JsonObject first=r.document().getAsJsonArray("records").get(1).getAsJsonObject();assert first.getAsJsonObject("before").get("gold").getAsInt()==123;
        assert first.getAsJsonObject("before").getAsJsonArray("deck").size()==2;
        // Neow appears once and has no invented skip.
        JsonArray neow=new JsonArray();neow.add(choices().get(0));r.begin("neow","NEOW",state(1,0),neow,"card:0","human");r.settle(after);
        for(String relic:new String[]{"Astrolabe","Pandora's Box","Empty Cage","DollysMirror"}){JsonObject b=state(1,16),a=state(2,17);a.getAsJsonArray("relics").add(parse("{\"id\":\""+relic+"\",\"counter\":-1}"));a.getAsJsonArray("deck").remove(0);JsonObject e=Recorder.object("build_change");e.addProperty("reason",relic);e.addProperty("status","settled");e.add("before",b);e.add("after",a);r.append(e);}
        // Closing and reopening never calls begin; no decision is emitted by an observation.
        int n=r.document().getAsJsonArray("records").size();r.flush();assert r.document().getAsJsonArray("records").size()==n;
        JsonObject cancel=Recorder.object("build_change");cancel.addProperty("reason","multiselect_cancel");cancel.addProperty("status","no_change_or_cancelled");cancel.add("before",after);cancel.add("after",after);r.append(cancel);
        for(String choice:new String[]{"skip","bowl"}){String key="act3-"+choice;r.begin(key,"COMBAT_REWARD",state(3,48),choices(),choice,"human");r.settleKey(key,state(3,48));}
        JsonObject checkpoint=r.checkpoint(after);String runId=r.runId;String parentBranch=r.branch;r.export();r.close();
        Recorder resumed=new Recorder(samples,meta,runId);resumed.resume(checkpoint,after);assert !resumed.branch.equals(parentBranch);
        resumed.begin("pending-on-crash","COMBAT_REWARD",state(3,49),choices(),"skip","unknown");resumed.close();
        resumed=new Recorder(samples,meta,runId);resumed.resume(parse("{\"checkpoint_id\":\"missing\"}"),after);
        resumed.resume(new JsonObject(),after);
        JsonArray checked=resumed.document().getAsJsonArray("records");JsonObject unknownLoad=checked.get(checked.size()-1).getAsJsonObject();
        assert "unknown".equals(Recorder.string(unknownLoad,"parent_status"))&&"unknown".equals(Recorder.string(unknownLoad,"parent_checkpoint_id")):"An absent saved checkpoint must never match records without checkpoint IDs";
        JsonObject end=Recorder.object("ending");end.addProperty("outcome","synthetic-demo");end.add("state",state(3,50));resumed.append(end);resumed.export();
        JsonObject doc=resumed.document();Files.write(samples.resolve("run.json"),doc.toString().getBytes(StandardCharsets.UTF_8));Files.copy(resumed.directory.resolve("review.html"),samples.resolve("review.html"),StandardCopyOption.REPLACE_EXISTING);Files.copy(resumed.directory.resolve("run.zip"),samples.resolve("synthetic-review.zip"),StandardCopyOption.REPLACE_EXISTING);
        JsonObject malicious=Recorder.copy(doc);for(JsonElement e:malicious.getAsJsonArray("records"))if("decision".equals(Recorder.string(e.getAsJsonObject(),"type"))){e.getAsJsonObject().getAsJsonObject("before").getAsJsonArray("deck").get(0).getAsJsonObject().addProperty("name","<img src=x onerror=window.XSS_EXECUTED=true>");break;}Files.write(samples.resolve("malicious.json"),malicious.toString().getBytes(StandardCharsets.UTF_8));
        resumed.close();
        Recorder other=new Recorder(samples,meta,null);assert !other.runId.equals(runId);other.close();
        Path blocked=samples.resolve("blocked-file");Files.write(blocked,new byte[0]);boolean failed=false;try{new Recorder(blocked,meta,null);}catch(java.io.IOException expected){failed=true;}assert failed;
        System.out.println("RecorderCheck OK: independent anchors, current resources, dedup, same-name instances, Neow, relic-change anchors, cancellation, skip/bowl, SL branches, interrupted pending, UTF-8/64-bit seed, IO failure. Synthetic test only.");
    }
}
