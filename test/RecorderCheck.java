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
    static JsonObject find(JsonArray records,String type,String key,String value){for(JsonElement e:records){JsonObject o=e.getAsJsonObject();if(type.equals(Recorder.string(o,"type"))&&(key==null||value.equals(Recorder.string(o,key))))return o;}throw new AssertionError("missing "+type+" "+key+"="+value);}
    static int count(JsonArray records,String type){int n=0;for(JsonElement e:records)if(type.equals(Recorder.string(e.getAsJsonObject(),"type")))n++;return n;}
    static void need(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    static void m1Checks(JsonArray recs){
        JsonObject neow=find(recs,"decision","source","NEOW_OPTION");
        need("mini_blessing".equals(Recorder.string(neow,"variant"))&&"neow:1".equals(Recorder.string(neow,"candidate_id")),"Neow variant and selection");
        JsonObject cost=neow.getAsJsonArray("candidates").get(1).getAsJsonObject();need("TEN_PERCENT_HP_BONUS".equals(Recorder.string(cost,"reward_type"))&&"NONE".equals(Recorder.string(cost,"drawback"))&&Recorder.string(cost,"label").contains("8"),"Neow reward, drawback and label");
        need(neow.getAsJsonObject("before").get("gold_gained").getAsInt()==0,"snapshot gold_gained");
        JsonObject neowResult=find(recs,"result","decision_id",Recorder.string(neow,"decision_id"));need("confirmed".equals(Recorder.string(neowResult,"status"))&&neowResult.getAsJsonObject("after").get("max_hp").getAsInt()==88,"Neow settled after");
        JsonObject enter=find(recs,"floor_enter","floor","1");need(enter.get("hp").getAsInt()==54&&enter.get("max_hp").getAsInt()==88&&enter.get("gold").getAsInt()==99&&enter.get("gold_gained").getAsInt()==0&&"RestRoom".equals(Recorder.string(enter,"room"))&&"before_room_entry_effects".equals(Recorder.string(enter,"timing")),"floor_enter fields");
        need(count(recs,"shop_offer")==1,"one offer per visit");JsonObject offer=find(recs,"shop_offer","shop_key","visit-1");
        JsonArray stock=offer.getAsJsonArray("candidates");need(stock.size()==5,"cards, relic, potion, purge, leave");
        JsonObject relic=stock.get(1).getAsJsonObject();need("shop_relic".equals(Recorder.string(relic,"type"))&&relic.get("price").getAsInt()==300&&!relic.get("purchasable").getAsBoolean(),"relic price and state");
        JsonObject purge=stock.get(3).getAsJsonObject();need(purge.get("available").getAsBoolean()&&purge.get("price").getAsInt()==75,"purge price and availability");
        need(offer.getAsJsonObject("state").get("gold_gained").getAsInt()==112,"offer state gold_gained");
        JsonObject buy=find(recs,"decision","candidate_id","card:shop-card");need("SHOP".equals(Recorder.string(buy,"source"))&&"visit-1".equals(Recorder.string(buy,"shop_key")),"purchase is a shop decision");
        JsonObject paid=find(recs,"result","decision_id",Recorder.string(buy,"decision_id")).getAsJsonObject("after");need(buy.getAsJsonObject("before").get("gold").getAsInt()-paid.get("gold").getAsInt()==buy.getAsJsonArray("candidates").get(0).getAsJsonObject().get("price").getAsInt(),"paid equals offered price");
        need(paid.get("gold_gained").getAsInt()==112,"spending never reduces gold_gained");
        JsonObject leave=find(recs,"decision","candidate_id","leave");JsonObject left=find(recs,"result","decision_id",Recorder.string(leave,"decision_id"));need("confirmed".equals(Recorder.string(left,"status"))&&left.getAsJsonObject("after").equals(leave.getAsJsonObject("before")),"leave settles at once");
        JsonObject end=find(recs,"ending",null,null);need(end.get("act3_cleared").getAsBoolean()&&end.get("act3_cleared_floor").getAsInt()==50&&!end.get("heart_kill").getAsBoolean(),"ending act-3 facts");
    }
    /** M1 additive fields: Neow options, entry HP, shop offer/purchase/leave, gold_gained, act-3 facts; journal restore and run.json both keep them. */
    static void m1Fields(Path samples,JsonObject meta)throws Exception {
        Recorder r=new Recorder(samples,meta,null);
        JsonObject s=state(1,0);s.addProperty("gold_gained",0);
        JsonArray neow=new JsonParser().parse("[{\"type\":\"neow_option\",\"candidate_id\":\"neow:0\",\"slot\":0,\"reward_type\":\"THREE_ENEMY_KILL\",\"drawback\":\"NONE\",\"label\":\"[ 敌人在接下来三场战斗中只有1点生命 ]\"},{\"type\":\"neow_option\",\"candidate_id\":\"neow:1\",\"slot\":1,\"reward_type\":\"TEN_PERCENT_HP_BONUS\",\"drawback\":\"NONE\",\"label\":\"[ 最大生命值 +8 ]\"}]").getAsJsonArray();
        // Extra keys never override the common decision contract.
        r.begin("neow-rewards","NEOW_OPTION",s,neow,"neow:1","human",parse("{\"variant\":\"mini_blessing\",\"source\":\"forged\",\"candidate_id\":\"forged\"}"));
        JsonObject a=state(1,0);a.addProperty("max_hp",88);a.addProperty("gold_gained",0);r.settle(a);
        JsonObject enter=Recorder.object("floor_enter");enter.addProperty("act",1);enter.addProperty("floor",1);enter.addProperty("room","RestRoom");enter.addProperty("hp",54);enter.addProperty("max_hp",88);enter.addProperty("gold",99);enter.addProperty("gold_gained",0);enter.addProperty("timing","before_room_entry_effects");r.append(enter);
        JsonObject shop=state(1,2);shop.addProperty("gold",123);shop.addProperty("gold_gained",112);
        JsonArray stock=new JsonParser().parse("[{\"type\":\"shop_card\",\"candidate_id\":\"card:shop-card\",\"price\":75,\"purchasable\":true,\"colorless\":false,\"card\":{\"id\":\"Inflame\",\"instance_id\":\"shop-card\",\"type\":\"POWER\",\"upgrades\":0}},{\"type\":\"shop_relic\",\"candidate_id\":\"relic:0\",\"price\":300,\"purchasable\":false,\"relic\":\"Anchor\"},{\"type\":\"shop_potion\",\"candidate_id\":\"potion:1\",\"price\":50,\"purchasable\":true,\"potion\":\"Fire Potion\"},{\"type\":\"shop_purge\",\"candidate_id\":\"purge\",\"price\":75,\"purchasable\":true,\"available\":true},{\"type\":\"leave\",\"candidate_id\":\"leave\"}]").getAsJsonArray();
        JsonObject offer=Recorder.object("shop_offer");offer.addProperty("shop_key","visit-1");offer.add("state",shop);offer.add("candidates",stock);r.append(offer);
        JsonObject visit=parse("{\"shop_key\":\"visit-1\"}");
        r.begin("card-object","SHOP",shop,stock,"card:shop-card","human",visit);JsonObject paid=Recorder.copy(shop);paid.addProperty("gold",48);r.settleKey("card-object",paid);
        JsonArray rest=new JsonParser().parse(stock.toString()).getAsJsonArray();rest.remove(0);
        need(r.begin("visit-1:leave","SHOP",paid,rest,"leave","human",visit)!=null,"leave begins");r.settleKey("visit-1:leave",paid);
        need(r.begin("visit-1:leave","SHOP",paid,rest,"leave","human",visit)==null,"leave recorded once");
        JsonObject end=Recorder.object("ending");end.addProperty("outcome","death");StateMod.act3Facts(end,3,50,false);r.append(end);
        String runId=r.runId;r.close();
        Recorder back=new Recorder(samples,meta,runId);m1Checks(back.document().getAsJsonArray("records"));
        m1Checks(parse(new String(Files.readAllBytes(back.directory.resolve("run.json")),StandardCharsets.UTF_8)).getAsJsonArray("records"));
        Files.write(samples.resolve("m1-run.json"),back.document().toString().getBytes(StandardCharsets.UTF_8));back.close();
        // Section 7 rule: no exit and still in act 1-3 is not cleared; act 4 is cleared without a known floor; a heart kill implies both.
        JsonObject f=new JsonObject();StateMod.act3Facts(f,3,-1,false);need(!f.get("act3_cleared").getAsBoolean()&&f.get("act3_cleared_floor").isJsonNull()&&!f.get("heart_kill").getAsBoolean(),"act 3 death not cleared");
        f=new JsonObject();StateMod.act3Facts(f,4,-1,false);need(f.get("act3_cleared").getAsBoolean()&&f.get("act3_cleared_floor").isJsonNull(),"act 4 implies cleared");
        f=new JsonObject();StateMod.act3Facts(f,4,51,true);need(f.get("act3_cleared").getAsBoolean()&&f.get("heart_kill").getAsBoolean()&&f.get("act3_cleared_floor").getAsInt()==51,"heart kill");
        System.out.println("RecorderCheck M1: PASS");
    }
    public static void main(String[] args)throws Exception {
        Path samples=Paths.get(args[0]);Files.createDirectories(samples);
        if(!StateMod.reviewedMod("basemod","5.56.0")||!StateMod.reviewedMod("stslib","2.12.0")||StateMod.reviewedMod("RunHistoryPlus","1.8.3"))throw new AssertionError("only BaseMod + StSLib keep the human source label");
        JsonObject meta=parse("{\"game_version\":\"test\",\"mod_version\":\"0.1.0\",\"seed\":\"-9223372036854775808\",\"play_id\":\"synthetic-contract-check\",\"coverage\":\"synthetic; not a real played run\"}");
        lockedRunJson(samples,meta);
        m1Fields(samples,meta);
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
