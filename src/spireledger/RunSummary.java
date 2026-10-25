package spireledger;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fact projection of one run for the indexes: every record reduced to the keys the indexes read, no metrics. Never trusted over its source. */
final class RunSummary {
    static final String SCHEMA="spire-ledger-summary-v1",FILE="summary.json";
    static final List<String> ANCHOR=Arrays.asList("act","floor","class","ascension","hp","max_hp","gold","gold_gained","room","event","is_daily","is_endless","is_trial");
    // HistoryLayout.delta compares these keys, so a kept card keeps all of them.
    static final List<String> CARD=Arrays.asList("id","name","instance_id","upgrades","misc","base_damage","base_block","base_magic","base_cost","bottle_flame","bottle_lightning","bottle_tornado");
    private final JsonArray records=new JsonArray();private final JsonObject relics=new JsonObject();private final HistoryIndex.Summary sets=new HistoryIndex.Summary();
    static boolean plain(JsonElement v){return v.isJsonPrimitive()||v.isJsonNull();}
    static JsonObject primitives(JsonObject o){JsonObject out=new JsonObject();for(Map.Entry<String,JsonElement> e:o.entrySet())if(plain(e.getValue()))out.add(e.getKey(),e.getValue());return out;}
    static JsonObject pick(JsonObject o,List<String> keys){JsonObject out=new JsonObject();for(String k:keys)if(o.has(k))out.add(k,o.get(k));return out;}
    static JsonArray cards(JsonArray deck){JsonArray out=new JsonArray();for(JsonElement c:deck)out.add(c.isJsonObject()?pick(c.getAsJsonObject(),CARD):c);return out;}
    void add(JsonObject e){
        JsonObject s=primitives(e);s.addProperty("_sig",HistoryIndex.sig(e));String type=HistoryIndex.str(e,"type");
        for(String k:Arrays.asList("before","after","state")){JsonElement v=e.get(k);if(v==null||!v.isJsonObject())continue;JsonObject a=v.getAsJsonObject(),p=pick(a,ANCHOR);
            // The final deck and relics feed deck size, holding and relic metrics; other snapshots only feed the search sets below.
            if(type.equals("ending")){if(a.has("deck"))p.add("deck",a.get("deck").isJsonArray()?cards(a.getAsJsonArray("deck")):a.get("deck"));if(a.has("relics")&&a.get("relics").isJsonArray()){JsonArray r=new JsonArray();for(JsonElement x:a.getAsJsonArray("relics"))r.add(x.isJsonObject()?pick(x.getAsJsonObject(),Arrays.asList("id","counter")):x);p.add("relics",r);}}
            s.add(k,p);int floor=HistoryIndex.num(a,"floor",0);for(JsonElement x:HistoryIndex.array(a,"relics"))if(x.isJsonObject()&&!relics.has(HistoryIndex.str(x.getAsJsonObject(),"id")))relics.addProperty(HistoryIndex.str(x.getAsJsonObject(),"id"),floor);
            HistoryIndex.anchor(sets,a);}
        if(type.equals("build_change")){
            // Only changed instances survive, in source order, so delta() over the projection yields the same changes.
            HistoryLayout.Delta d=HistoryLayout.delta(HistoryIndex.obj(e,"before"),HistoryIndex.obj(e,"after"));
            if(d.identitiesComplete&&s.has("before")&&s.has("after")){Set<String> was=new HashSet<>(),now=new HashSet<>();for(HistoryLayout.Change c:d.cards){if(c.before!=null)was.add(HistoryIndex.str(c.before,"instance_id"));if(c.after!=null)now.add(HistoryIndex.str(c.after,"instance_id"));}
                JsonArray b=new JsonArray(),a=new JsonArray();for(JsonElement c:HistoryIndex.array(HistoryIndex.obj(e,"before"),"deck"))if(was.contains(HistoryIndex.str(c.getAsJsonObject(),"instance_id")))b.add(pick(c.getAsJsonObject(),CARD));for(JsonElement c:HistoryIndex.array(HistoryIndex.obj(e,"after"),"deck"))if(now.contains(HistoryIndex.str(c.getAsJsonObject(),"instance_id")))a.add(pick(c.getAsJsonObject(),CARD));
                s.getAsJsonObject("before").add("deck",b);s.getAsJsonObject("after").add("deck",a);}
        }
        JsonElement candidates=e.get("candidates");if(candidates!=null&&candidates.isJsonArray()){JsonArray out=new JsonArray();for(JsonElement c:candidates.getAsJsonArray()){if(!c.isJsonObject()){out.add(c);continue;}JsonObject p=primitives(c.getAsJsonObject());JsonElement card=c.getAsJsonObject().get("card");if(card!=null&&card.isJsonObject())p.add("card",pick(card.getAsJsonObject(),CARD));out.add(p);}s.add("candidates",out);}
        records.add(s);
    }
    JsonObject document(String sourceSchema,JsonElement metadata,long eventsBytes,int lastSeq){
        JsonObject o=new JsonObject();o.addProperty("schema",SCHEMA);o.addProperty("source_schema",sourceSchema);o.addProperty("events_bytes",eventsBytes);o.addProperty("last_seq",lastSeq);o.addProperty("record_count",records.size());o.add("metadata",metadata);
        JsonArray ids=new JsonArray(),names=new JsonArray();for(String x:sets.cardIds)ids.add(x);for(String x:sets.names)names.add(x);o.add("card_ids",ids);o.add("card_names",names);o.add("relics",relics);o.add("records",records);return o;
    }
    static JsonObject project(JsonObject run,long eventsBytes,int lastSeq){RunSummary s=new RunSummary();for(JsonElement e:HistoryIndex.array(run,"records"))if(e.isJsonObject())s.add(e.getAsJsonObject());return s.document(HistoryIndex.str(run,"schema"),HistoryIndex.obj(run,"metadata"),eventsBytes,lastSeq);}
    /** The summary only when its marker matches the journal now; null sends the reader to the full record. Never writes. */
    static JsonObject fresh(Path dir){
        Path file=dir.resolve(FILE),log=dir.resolve("events.jsonl");
        // events.jsonl is append-only and fsynced per line, so its length names exactly one prefix of the run; mtime does not survive copies.
        try{if(!Files.isRegularFile(file)||Files.isSymbolicLink(file)||Files.size(file)>HistoryIndex.MAX_BYTES||!Files.isRegularFile(log))return null;
            JsonObject o;try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){o=new JsonParser().parse(r).getAsJsonObject();}
            if(!SCHEMA.equals(HistoryIndex.str(o,"schema"))||!Recorder.knownSchema(HistoryIndex.str(o,"source_schema"))||!o.has("records")||!o.get("records").isJsonArray())return null;
            return o.get("events_bytes").getAsLong()==Files.size(log)?o:null;}catch(Exception e){return null;}
    }
}
