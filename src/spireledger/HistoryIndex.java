package spireledger;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read-only, streaming directory index. No game objects, no retained deck anchors. */
final class HistoryIndex {
    static final long MAX_BYTES=32L*1024*1024;
    static final class Summary {
        String playId="",sessionKind="",fileSignature="",lastBranch="";boolean conflict;String metadataSignature="";final Map<String,String> eventSignatures=new HashMap<>();Path file;String id,seed="",character="unknown",outcome="incomplete",search="";
        int ascension=-1,floor,picks,skips,bowls,changes,pending,unknown;
        long time;boolean fixture,current;
        final Set<String> branches=new LinkedHashSet<>(),cardIds=new HashSet<>(),relicIds=new HashSet<>(),names=new HashSet<>();
        String localizedSearch="";
    }
    static final class Index {final List<Summary> runs=new ArrayList<>();final List<String> errors=new ArrayList<>();int duplicates;}
    static String str(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsString():"";}
    static int num(JsonObject o,String k,int fallback){try{return o.has(k)?o.get(k).getAsInt():fallback;}catch(Exception e){return fallback;}}
    static JsonObject obj(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonObject()?v.getAsJsonObject():new JsonObject();}
    static JsonArray array(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonArray()?v.getAsJsonArray():new JsonArray();}
    static void anchor(Summary s,JsonObject state){
        if(state.has("class"))s.character=str(state,"class");s.ascension=num(state,"ascension",s.ascension);s.floor=Math.max(s.floor,num(state,"floor",0));
        for(JsonElement e:array(state,"deck"))if(e.isJsonObject()){JsonObject c=e.getAsJsonObject();s.cardIds.add(str(c,"id"));s.names.add(str(c,"name"));}
        for(JsonElement e:array(state,"relics"))if(e.isJsonObject())s.relicIds.add(str(e.getAsJsonObject(),"id"));
    }
    static final class Builder {
        final Summary s=new Summary();final Map<String,JsonObject> decisions=new LinkedHashMap<>(),results=new HashMap<>();final Map<String,String> signatures=new HashMap<>();final Set<String> conflicts=new HashSet<>();int count;String lastBranch="";final Map<String,String> endings=new HashMap<>();
        void metadata(JsonObject m){s.metadataSignature=signature(m);s.id=str(m,"run_id");s.seed=str(m,"seed");s.fixture="test_fixture".equals(str(m,"dataset_origin"));s.playId=str(m,"play_id");s.sessionKind=str(m,"session_kind");}
        void event(JsonObject e){
            if(++count>50000)throw new IllegalArgumentException("too many records");String eventKey=e.has("seq")?str(e,"seq"):String.valueOf(count);String previous=s.eventSignatures.putIfAbsent(eventKey,signature(e));if(previous!=null&&!previous.equals(signature(e)))s.conflict=true;
            try{s.time=Math.max(s.time,e.has("time_ms")?e.get("time_ms").getAsLong():0);}catch(Exception ignored){}
            String b=str(e,"branch_id");if(!b.isEmpty()){s.branches.add(b);lastBranch=b;}
            anchor(s,obj(e,"before"));anchor(s,obj(e,"after"));anchor(s,obj(e,"state"));
            for(JsonElement c:array(e,"candidates"))if(c.isJsonObject()){JsonObject card=obj(c.getAsJsonObject(),"card");s.cardIds.add(str(card,"id"));s.names.add(str(card,"name"));}
            String type=str(e,"type"),id=str(e,"decision_id");if(type.equals("game_play_id")&&!str(e,"play_id").isEmpty())s.playId=str(e,"play_id");
            if(type.equals("decision")||type.equals("result")){Map<String,JsonObject> map=type.equals("decision")?decisions:results;String signature=signature(e),key=type+":"+id;String prior=signatures.putIfAbsent(key,signature);if(prior!=null&&!prior.equals(signature))conflicts.add(id);if(prior==null){JsonObject small=new JsonObject();for(String k:Arrays.asList("action_source","candidate_id","status"))if(e.has(k))small.add(k,e.get(k));if(type.equals("decision")){JsonArray candidates=new JsonArray();for(JsonElement c:array(e,"candidates")){JsonObject candidate=new JsonObject();for(String k:Arrays.asList("candidate_id","type"))if(c.isJsonObject()&&c.getAsJsonObject().has(k))candidate.add(k,c.getAsJsonObject().get(k));candidates.add(candidate);}small.add("candidates",candidates);}map.put(id,small);}}
            if(type.equals("build_change")&&"settled".equals(str(e,"status")))s.changes++;
            if(type.equals("ending")){String outcome=str(e,"outcome");endings.put(b,outcome.equals("victory")?"victory":outcome.equals("death")?"death":"incomplete");}
        }
        Summary finish(boolean current){
            if(s.id==null||s.id.isEmpty())throw new IllegalArgumentException("missing run_id");
            s.lastBranch=lastBranch;
            s.conflict|=!conflicts.isEmpty();s.current=current;s.outcome=endings.containsKey(lastBranch)?endings.get(lastBranch):current?"current":"incomplete";
            for(Map.Entry<String,JsonObject> item:decisions.entrySet()){
                JsonObject d=item.getValue(),r=results.get(item.getKey());
                if(!"human".equals(str(d,"action_source")))s.unknown++;
                if(conflicts.contains(item.getKey())||r==null||!"confirmed".equals(str(r,"status"))){s.pending++;continue;}
                String selected=str(d,"candidate_id");for(JsonElement c:array(d,"candidates"))if(c.isJsonObject()&&selected.equals(str(c.getAsJsonObject(),"candidate_id"))){switch(str(c.getAsJsonObject(),"type")){case "card":s.picks++;break;case "skip":s.skips++;break;case "bowl":s.bowls++;break;}break;}
            }
            // Search retains a set of names/IDs, not one token per frame/anchor.
            s.search=String.join(" ",s.names)+" "+s.seed+" "+String.join(" ",s.cardIds)+" "+String.join(" ",s.relicIds);s.search=s.search.toLowerCase(Locale.ROOT);return s;
        }
        boolean ended;
    }
    static String signature(JsonObject e){try{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(normalized(e).toString().getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(digest);}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
    static JsonObject normalized(JsonObject e){JsonObject n=Recorder.copy(e);for(String k:Arrays.asList("seq","time_ms"))n.remove(k);return n;}
    static Summary summarize(JsonObject run,boolean current){if(!Recorder.knownSchema(str(run,"schema")))throw new IllegalArgumentException("unsupported schema");Builder b=new Builder();b.metadata(obj(run,"metadata"));for(JsonElement e:array(run,"records")){if(!e.isJsonObject())throw new IllegalArgumentException("invalid record");JsonObject r=e.getAsJsonObject();b.ended|="ending".equals(str(r,"type"));b.event(r);}return b.finish(current);}
    static Summary readSummary(Path file)throws IOException {
        if(Files.isSymbolicLink(file)||Files.size(file)>MAX_BYTES)throw new IOException("file exceeds bounds or is linked");Builder b=new Builder();String schema="";
        try(JsonReader r=new JsonReader(Files.newBufferedReader(file,StandardCharsets.UTF_8))){r.beginObject();while(r.hasNext()){String key=r.nextName();if(key.equals("schema"))schema=r.nextString();else if(key.equals("metadata"))b.metadata(new JsonParser().parse(r).getAsJsonObject());else if(key.equals("records")){r.beginArray();while(r.hasNext()){JsonObject e=new JsonParser().parse(r).getAsJsonObject();b.ended|="ending".equals(str(e,"type"));b.event(e);}r.endArray();}else r.skipValue();}r.endObject();if(r.peek()!=com.google.gson.stream.JsonToken.END_DOCUMENT)throw new IOException("trailing JSON");}
        if(!Recorder.knownSchema(schema))throw new IOException("unsupported schema");Summary s=b.finish(false);s.file=file;return s;
    }
    static Index scan(Path root,JsonObject live)throws IOException {return scan(Collections.singletonList(root),live);}
    static Index scan(List<Path> roots,JsonObject live)throws IOException {
        Index index=new Index();Map<String,Summary> byId=new HashMap<>();
        for(Path root:roots)if(Files.isDirectory(root))try(DirectoryStream<Path> dirs=Files.newDirectoryStream(root)){for(Path dir:dirs){if(!Files.isDirectory(dir)||Files.isSymbolicLink(dir))continue;Path file=dir.resolve("run.json");if(!Files.exists(file))continue;try{Summary s=readSummary(file);Summary old=byId.get(s.id);if(old!=null){index.duplicates++;boolean bad=old.conflict||s.conflict||!old.metadataSignature.equals(s.metadataSignature);for(String key:s.eventSignatures.keySet())if(old.eventSignatures.containsKey(key)&&!old.eventSignatures.get(key).equals(s.eventSignatures.get(key)))bad=true;old.conflict=s.conflict=bad;if(bad)index.errors.add("Conflicting exports: "+s.id);if(old.time>s.time)continue;}byId.put(s.id,s);}catch(Exception e){index.errors.add(dir.getFileName()+": "+e.getClass().getSimpleName());}}}
        if(live!=null){Summary s=summarize(live,true);Summary old=byId.put(s.id,s);if(old!=null){s.file=old.file;s.conflict|=old.conflict;}}
        index.runs.addAll(byId.values());index.runs.sort(Comparator.comparingLong((Summary s)->s.time).reversed().thenComparing(s->s.id));return index;
    }
    static List<Summary> filter(List<Summary> runs,String character,int ascension,String outcome,int days,String query,boolean sl,long now){
        List<Summary> out=new ArrayList<>();String q=query.trim().toLowerCase(Locale.ROOT);
        for(Summary s:runs)if((character.isEmpty()||s.character.equals(character))&&(ascension<0||s.ascension==ascension)&&(outcome.isEmpty()||s.outcome.equals(outcome))&&(days==0||(s.time>0&&s.time>=now-days*86400000L))&&(!sl||s.branches.size()>1)&&(q.isEmpty()||s.search.contains(q)||s.localizedSearch.contains(q)))out.add(s);return out;
    }
    static JsonObject readDetail(Path file)throws IOException {if(Files.isSymbolicLink(file)||Files.size(file)>MAX_BYTES)throw new IOException("file exceeds bounds");try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){JsonObject o=new JsonParser().parse(r).getAsJsonObject();summarize(o,false);return o;}}
}
