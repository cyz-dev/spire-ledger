package spireledger;

import com.google.gson.*;
import java.util.*;

public final class RunReviewCheck {
    static JsonObject json(String text){return new JsonParser().parse(text).getAsJsonObject();}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static JsonObject mod(String id,String version){JsonObject m=new JsonObject();m.addProperty("id",id);m.addProperty("version",version);return m;}
    public static void main(String[] args){
        List<JsonObject> records=new ArrayList<>();
        records.add(json("{type:'floor',state:{floor:1,act:1,room:'MonsterRoom',hp:70,gold:99}}"));
        JsonObject choice=json("{type:'decision',decision_id:'one',floor:1,candidate_id:'a',before:{floor:1,act:1,hp:68,gold:99},candidates:[{candidate_id:'a',card:{id:'Bash',upgrades:0}}]}");
        records.add(choice);records.add(Recorder.copy(choice));
        records.add(json("{type:'result',decision_id:'one',status:'confirmed',after:{floor:1,act:1,hp:68,gold:115}}"));
        records.add(json("{type:'floor',state:{floor:18,act:2,room:'RestRoom',hp:68,gold:115}}"));
        records.add(json("{type:'build_change',status:'pending',floor:18}"));
        records.add(json("{type:'build_change',status:'settled',floor:18,before:{floor:18,hp:68},after:{floor:18,hp:75}}"));
        records.add(json("{type:'build_change',status:'settled',floor:18,before:{floor:18,act:2,room:'RestRoom',hp:75},after:{floor:18,act:3,room:'EmptyRoom',hp:75}}"));
        String original=records.toString();RunReviewModel model=new RunReviewModel(records);
        check(model.floors.size()==2&&model.floors.get(1).act==2,"recorded floors and acts only");
        check(model.floors.get(0).decisions.size()==1&&model.confirmed(choice),"duplicate decision is one confirmed choice");
        check(model.floors.get(0).first.get("hp").getAsInt()==70&&model.floors.get(0).last.get("gold").getAsInt()==115,"first and last observations");
        check(model.floors.get(1).changes.size()==2&&model.finalState.get("hp").getAsInt()==75,"settled changes and final anchor");
        check(model.floors.get(1).act==2&&model.floors.get(1).room.equals("RestRoom"),"act transition stays with originating floor and retains recorded room");
        check(original.equals(records.toString()),"projection never edits source");
        records.add(json("{type:'result',decision_id:'one',status:'cancelled'}"));
        check(!new RunReviewModel(records).confirmed(choice),"conflicting results remain unconfirmed");
        JsonObject state=json("{deck:[{id:'Strike_R',instance_id:'a',upgrades:0,base_damage:6},{id:'Strike_R',instance_id:'b',upgrades:0,base_damage:6},{id:'Strike_R',upgrades:1,base_damage:9},{id:'Strike_R',upgrades:0,base_damage:8}]}");
        List<RunReviewModel.CardCount> deck=RunReviewModel.deck(state);
        check(deck.size()==3&&deck.get(0).count==2,"combine equal cards while retaining upgrades and permanent values");
        JsonArray mods=new JsonArray();mods.add(mod("basemod","5.56.0"));mods.add(mod("stslib","2.12.0"));mods.add(mod("spire-ledger","0.5.0-beta.1"));
        check(ReplayEnvironment.accepts(mods),"base supported");
        for(Map.Entry<String,String> e:ReplayEnvironment.OPTIONAL.entrySet())mods.add(mod(e.getKey(),e.getValue().replace("+","")));
        check(ReplayEnvironment.accepts(mods),"player eight-mod combination supported");
        mods.add(mod("sts1solver","0.9.6"));check(!ReplayEnvironment.accepts(mods),"duplicate rejected");mods.remove(mods.size()-1);
        JsonObject solver=mods.get(4).getAsJsonObject();check("sts1solver".equals(Recorder.string(solver,"id")),"fixture order: solver at 4");
        for(String v:new String[]{"0.9.6","0.9.7","0.10.0","1.0"}){solver.addProperty("version",v);check(ReplayEnvironment.accepts(mods),"solver "+v+" accepted as 0.9.6 or later");}
        for(String v:new String[]{"0.9.5","0.9.4-test","0.9.7-rc1",""}){solver.addProperty("version",v);check(!ReplayEnvironment.accepts(mods),"solver "+v+" rejected");}
        solver.addProperty("version","0.9.6");
        mods.get(4).getAsJsonObject().addProperty("version","unknown");check(!ReplayEnvironment.accepts(mods),"unverified version rejected");
        check(!ReplayEnvironment.accepts(new JsonArray()),"missing requirements rejected");
        System.out.println("RunReviewCheck PASS: read-only floor projection, choice conflicts, deck grouping and version boundary");
    }
}
