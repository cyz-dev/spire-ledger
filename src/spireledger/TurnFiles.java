package spireledger;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Independent completed files, followed by an atomic manifest publication. */
final class TurnFiles {
    static final String SCHEMA="spire-ledger-turn-v1";
    static final int MAX_BYTES=2*1024*1024, MAX_POINTS=10000;
    static final long MAX_MANIFEST=8L*1024*1024;
    /** Per-run budget. Repeated S/L sessions stop adding bytes once the same state is already on disk. */
    static final long BUDGET_BYTES=budget();
    /**
     * Distinct boards kept per (floor, combat, turn). One BOSS turn can be S/L'd dozens of times; the first board and
     * the newest ones are the useful samples, the twenty boards in between mostly differ by one card (2026-11-03).
     */
    static final int TURN_ATTEMPTS=turnAttempts();
    static long budget(){long mb=512;String v=System.getProperty("spireledger.turnBudgetMb");if(v!=null)try{mb=Long.parseLong(v.trim());}catch(RuntimeException ignored){}return Math.max(1,mb)*1024L*1024L;}
    static int turnAttempts(){int n=8;String v=System.getProperty("spireledger.turnAttemptCap");if(v!=null)try{n=Integer.parseInt(v.trim());}catch(RuntimeException ignored){}return Math.max(2,n);}
    final Path directory;
    final JsonObject manifest;
    TurnFiles(Path runDirectory,String runId,JsonObject environment)throws IOException {
        directory=runDirectory.resolve("replay");Files.createDirectories(directory.resolve("turns"));
        Path path=directory.resolve("manifest.json");
        if(Files.exists(path)){manifest=read(path,MAX_MANIFEST);if(!SCHEMA.equals(Recorder.string(manifest,"schema"))||!runId.equals(Recorder.string(manifest,"run_id")))throw new IOException("Replay identity mismatch");}
        else {manifest=Recorder.copy(environment);manifest.addProperty("schema",SCHEMA);manifest.addProperty("run_id",runId);manifest.add("snapshots",new JsonArray());}
        if(!manifest.has("blobs")||!manifest.get("blobs").isJsonObject())manifest.add("blobs",new JsonObject());
    }
    static JsonObject read(Path path)throws IOException {return read(path,MAX_BYTES);}
    static JsonObject read(Path path,long max)throws IOException {if(Files.isSymbolicLink(path)||Files.size(path)>max)throw new IOException("Replay file bounds");try{return new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){throw new IOException("Invalid replay JSON",ex);}}
    static void atomic(Path path,byte[] bytes)throws IOException {Path tmp=path.resolveSibling(path.getFileName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes);out.flush();out.getFD().sync();}Recorder.replace(tmp,path);}
    JsonObject blobs(){return manifest.getAsJsonObject("blobs");}
    /**
     * Stores a point once per distinct state. A repeated S/L session reaches the same turn with the same board, so the
     * snapshot body is identical: the existing point only gains an attempt counter, no second blob and no second
     * manifest entry. The session itself stays in the branch tree through events.jsonl (architecture, 2026-11-01,
     * tightened 2026-11-03: a duplicate used to add a pointer row, which still ate MAX_POINTS).
     */
    JsonObject publish(JsonObject entry,JsonObject snapshot)throws IOException {
        JsonArray entries=manifest.getAsJsonArray("snapshots");
        JsonObject ref=Recorder.copy(entry);String id=UUID.randomUUID().toString();
        if(snapshot!=null){
            JsonObject identity=Recorder.copy(entry);identity.addProperty("snapshot_id",id);identity.addProperty("run_id",Recorder.string(manifest,"run_id"));snapshot.add("identity",identity);
            String content=contentHash(snapshot);
            JsonObject known=blobs().has(content)?blobs().getAsJsonObject(content):null;
            if(known!=null){
                Path existing=directory.resolve(Recorder.string(known,"relative_path"));
                if(Files.exists(existing)&&sha256(Files.readAllBytes(existing)).equals(Recorder.string(known,"sha256"))){
                    JsonObject original=recordedFor(entries,content);
                    ref.addProperty("snapshot_id",id);ref.addProperty("availability","duplicate");ref.addProperty("relative_path","");
                    ref.addProperty("content_sha256",content);ref.addProperty("duplicate_of",Recorder.string(known,"snapshot_id"));
                    int attempts=1;
                    if(original!=null){
                        attempts=original.has("attempts")?original.get("attempts").getAsInt():1;
                        original.addProperty("attempts",attempts+1);
                        original.addProperty("last_seen_at",entry.has("captured_at")?entry.get("captured_at").getAsLong():System.currentTimeMillis());
                        if(entry.has("branch_id"))original.addProperty("last_branch_id",Recorder.string(entry,"branch_id"));
                    }
                    ref.addProperty("attempts",attempts+1);
                    noteLastCombat(entry);
                    try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){if(original!=null){original.addProperty("attempts",attempts);original.remove("last_seen_at");original.remove("last_branch_id");}throw ex;}
                    return Recorder.copy(ref);
                }
                blobs().remove(content);
            }
            byte[] data=snapshot.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Replay point size limit");
            if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");
            String name="turns/"+id+".json";atomic(directory.resolve(name),data);
            JsonObject blob=new JsonObject();blob.addProperty("relative_path",name);blob.addProperty("bytes",data.length);blob.addProperty("sha256",sha256(data));blob.addProperty("snapshot_id",id);blobs().add(content,blob);
            ref.addProperty("snapshot_id",id);ref.addProperty("relative_path",name);ref.addProperty("availability","recorded");ref.addProperty("bytes",data.length);ref.addProperty("sha256",sha256(data));ref.addProperty("content_sha256",content);ref.addProperty("attempts",1);
        }
        else {if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");ref.addProperty("snapshot_id",id);ref.addProperty("relative_path","");if(!ref.has("availability"))ref.addProperty("availability","missing");}
        entries.add(ref);noteLastCombat(entry);
        // Trim while the manifest is still in memory, so one atomic write publishes the new point and the reclaim.
        evictTurn(entry);
        evict(Recorder.string(ref,"branch_id"));
        try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){entries.remove(entries.size()-1);throw ex;}
        return Recorder.copy(ref);
    }
    private static JsonObject recordedFor(JsonArray entries,String content){
        for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();if("recorded".equals(Recorder.string(e,"availability"))&&content.equals(Recorder.string(e,"content_sha256")))return e;}
        return null;
    }
    /**
     * Keeps the first board of a turn (the turn-start baseline) plus the newest TURN_ATTEMPTS-1 boards. Anything
     * older in between is marked evicted and its blob freed, but the counter on the surviving points stays, so the
     * sample set still says how often a board was reached.
     */
    private void evictTurn(JsonObject entry){
        String combat=Recorder.string(entry,"combat_id");int floor=Recorder.intOf(entry,"floor",-1),turn=Recorder.intOf(entry,"turn",-1);
        List<JsonObject> same=new ArrayList<>();
        for(JsonElement item:manifest.getAsJsonArray("snapshots")){
            JsonObject e=item.getAsJsonObject();
            if(!"recorded".equals(Recorder.string(e,"availability"))||!combat.equals(Recorder.string(e,"combat_id")))continue;
            if(Recorder.intOf(e,"floor",-1)!=floor||Recorder.intOf(e,"turn",-1)!=turn)continue;
            same.add(e);
        }
        if(same.size()<=TURN_ATTEMPTS)return;
        same.sort((a,b)->Long.compare(capturedAt(a),capturedAt(b)));
        for(int i=1;i<same.size()-(TURN_ATTEMPTS-1);i++){
            JsonObject drop=same.get(i);
            releaseBlob(drop);
            drop.addProperty("availability","evicted");drop.addProperty("relative_path","");drop.remove("sha256");
        }
    }
    private void releaseBlob(JsonObject entry){
        String content=Recorder.string(entry,"content_sha256");if(content.isEmpty())return;
        if(referenced(manifest.getAsJsonArray("snapshots"),content,Recorder.string(entry,"snapshot_id")))return;
        JsonObject known=blobs().has(content)?blobs().getAsJsonObject(content):null;if(known==null)return;
        try{Files.deleteIfExists(directory.resolve(Recorder.string(known,"relative_path")));}catch(IOException ignored){}
        blobs().remove(content);
    }
    private static long capturedAt(JsonObject e){return e.has("captured_at")&&!e.get("captured_at").isJsonNull()?e.get("captured_at").getAsLong():0L;}
    /**
     * Remembers which fight the run is in, so a restarted process can keep the same identity instead of counting one
     * fight twice. It rides in the manifest because that file is already written atomically with every point.
     */
    private void noteLastCombat(JsonObject entry){
        String combat=Recorder.string(entry,"combat_id");if(combat.isEmpty())return;
        JsonObject last=new JsonObject();last.addProperty("combat_id",combat);
        last.addProperty("floor",entry.has("floor")?entry.get("floor").getAsInt():-1);
        manifest.add("last_combat",last);
    }
    /** State-only hash: metadata and transient presentation values never split the same turn into two copies. */
    static String contentHash(JsonObject snapshot){
        try{return sha256(SnapshotShape.body(snapshot).toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){return "";}
    }
    /** Oldest branches are reclaimed first; the branch being written is never touched. */
    private void evict(String currentBranch)throws IOException {
        JsonArray entries=manifest.getAsJsonArray("snapshots");
        long live=liveBytes();if(live<=BUDGET_BYTES)return;
        Map<String,Long> start=new HashMap<>();
        for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();String b=Recorder.string(e,"branch_id");if(b.isEmpty())continue;
            long at=e.has("captured_at")?e.get("captured_at").getAsLong():0L;Long old=start.get(b);if(old==null||at<old)start.put(b,at);}
        List<String> order=new ArrayList<>(start.keySet());order.sort((a,b)->Long.compare(start.get(a),start.get(b)));
        for(String branch:order){
            if(live<=BUDGET_BYTES)return;
            if(branch.equals(currentBranch))continue;
            for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();if(!branch.equals(Recorder.string(e,"branch_id"))||!"recorded".equals(Recorder.string(e,"availability")))continue;
                String content=Recorder.string(e,"content_sha256");
                if(!content.isEmpty()&&!referenced(entries,content,Recorder.string(e,"snapshot_id"))){
                    JsonObject known=blobs().has(content)?blobs().getAsJsonObject(content):null;
                    if(known!=null){Files.deleteIfExists(directory.resolve(Recorder.string(known,"relative_path")));blobs().remove(content);}
                }
                e.addProperty("availability","evicted");e.addProperty("relative_path","");e.remove("sha256");
            }
            live=liveBytes();
        }
    }
    static boolean referenced(JsonArray entries,String content,String except){for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();if(!"recorded".equals(Recorder.string(e,"availability"))||except.equals(Recorder.string(e,"snapshot_id")))continue;if(content.equals(Recorder.string(e,"content_sha256")))return true;}return false;}
    long liveBytes(){long total=0;for(Map.Entry<String,JsonElement> e:blobs().entrySet()){JsonObject blob=e.getValue().getAsJsonObject();if(blob.has("bytes"))total+=blob.get("bytes").getAsLong();}return total;}
    static String sha256(byte[] bytes)throws IOException{try{StringBuilder out=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException ex){throw new IOException(ex);}}
    static void addToZip(Path runDirectory,ZipOutputStream zip)throws IOException {
        Path directory=runDirectory.resolve("replay"),path=directory.resolve("manifest.json");if(!Files.exists(path))return;JsonObject manifest=read(path,MAX_MANIFEST);if(!SCHEMA.equals(Recorder.string(manifest,"schema")))throw new IOException("Replay schema mismatch");
        JsonArray entries=manifest.getAsJsonArray("snapshots");if(entries==null||entries.size()>MAX_POINTS)throw new IOException("Replay point bounds");Set<String> names=new HashSet<>();
        for(JsonElement item:entries){JsonObject ref=item.getAsJsonObject();if(!"recorded".equals(Recorder.string(ref,"availability")))continue;String name=Recorder.string(ref,"relative_path");if(!name.matches("turns/[a-fA-F0-9-]{36}\\.json"))throw new IOException("Replay path");if(!names.add(name))continue;Path point=directory.resolve(name);if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(directory.resolve("turns"))||Files.isSymbolicLink(point)||Files.size(point)>MAX_BYTES)throw new IOException("Replay point bounds");zip.putNextEntry(new ZipEntry("replay/"+name));Files.copy(point,zip);zip.closeEntry();}
        zip.putNextEntry(new ZipEntry("replay/manifest.json"));Files.copy(path,zip);zip.closeEntry();
    }
}
