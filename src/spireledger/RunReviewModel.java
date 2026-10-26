package spireledger;

import com.google.gson.*;
import java.util.*;

/** Read-only projection of the selected attempt; never manufactures missing observations. */
final class RunReviewModel {
    static final class Floor {
        final int number;int act=1;String room="";
        JsonObject first=new JsonObject(),last=new JsonObject();
        final List<JsonObject> decisions=new ArrayList<>(),changes=new ArrayList<>();
        Floor(int number){this.number=number;}
        void observe(JsonObject state){if(!state.has("floor")||HistoryIndex.num(state,"floor",-1)!=number)return;if(first.entrySet().isEmpty()){first=state;act=HistoryIndex.num(state,"act",act);}last=state;String location=HistoryIndex.str(state,"room");if(!location.isEmpty()&&!location.equals("EmptyRoom")&&!location.equals("unknown"))room=location;}
    }
    final List<Floor> floors=new ArrayList<>();
    final Map<String,JsonObject> results=new HashMap<>();
    final Set<String> conflicts=new HashSet<>();
    JsonObject finalState=new JsonObject();
    RunReviewModel(List<JsonObject> records){
        Map<Integer,Floor> byFloor=new TreeMap<>();Map<String,String> decisions=new HashMap<>();
        for(JsonObject event:records){
            String type=HistoryIndex.str(event,"type"),id=HistoryIndex.str(event,"decision_id");
            JsonObject before=HistoryIndex.obj(event,"before"),state=HistoryIndex.obj(event,"state"),after=HistoryIndex.obj(event,"after");
            int number=HistoryIndex.num(event,"floor",HistoryIndex.num(before,"floor",HistoryIndex.num(state,"floor",HistoryIndex.num(after,"floor",-1))));
            if(type.equals("result")){
                JsonObject old=results.put(id,event);if(old!=null&&!HistoryIndex.signature(old).equals(HistoryIndex.signature(event)))conflicts.add(id);
            }
            if(number<0)continue;
            Floor floor=byFloor.computeIfAbsent(number,Floor::new);
            for(JsonObject observation:Arrays.asList(before,state,after))if(observation.has("floor")){floor.observe(observation);finalState=observation;}
            if(type.equals("decision")){
                String old=decisions.put(id,HistoryIndex.signature(event));
                if(old==null)floor.decisions.add(event);else if(!old.equals(HistoryIndex.signature(event)))conflicts.add(id);
            }
            if(type.equals("build_change")&&"settled".equals(HistoryIndex.str(event,"status")))floor.changes.add(event);
        }
        floors.addAll(byFloor.values());
    }
    boolean confirmed(JsonObject decision){String id=HistoryIndex.str(decision,"decision_id");return !conflicts.contains(id)&&results.containsKey(id)&&"confirmed".equals(HistoryIndex.str(results.get(id),"status"));}
    static final class CardCount {final JsonObject card;int count=1;CardCount(JsonObject card){this.card=card;}}
    static List<CardCount> deck(JsonObject state){
        Map<String,CardCount> cards=new LinkedHashMap<>();
        for(JsonElement item:HistoryIndex.array(state,"deck"))if(item.isJsonObject()){
            JsonObject card=item.getAsJsonObject();StringBuilder key=new StringBuilder();
            for(String field:Arrays.asList("id","upgrades","misc","base_damage","base_block","base_magic","base_cost","bottle_flame","bottle_lightning","bottle_tornado"))key.append(field).append('=').append(card.get(field)).append(';');
            CardCount old=cards.get(key.toString());if(old==null)cards.put(key.toString(),new CardCount(card));else old.count++;
        }
        return new ArrayList<>(cards.values());
    }
}
