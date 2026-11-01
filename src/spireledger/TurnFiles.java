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
    static long budget(){long mb=512;String v=System.getProperty("spireledger.turnBudgetMb");if(v!=null)try{mb=Long.parseLong(v.trim());}catch(RuntimeException ignored){}return Math.max(1,mb)*1024L*1024L;}
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
     * snapshot body is identical and only a pointer is published: the session is still in the branch tree, the bytes
     * are not written twice (architecture, 2026-11-01).
     */
    JsonObject publish(JsonObject entry,JsonObject snapshot)throws IOException {
        JsonArray entries=manifest.getAsJsonArray("snapshots");if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");
        JsonObject ref=Recorder.copy(entry);String id=UUID.randomUUID().toString();
        if(snapshot!=null){
            JsonObject identity=Recorder.copy(entry);identity.addProperty("snapshot_id",id);identity.addProperty("run_id",Recorder.string(manifest,"run_id"));snapshot.add("identity",identity);
            String content=contentHash(snapshot);
            JsonObject known=blobs().has(content)?blobs().getAsJsonObject(content):null;
            if(known!=null){
                Path existing=directory.resolve(Recorder.string(known,"relative_path"));
                if(Files.exists(existing)&&sha256(Files.readAllBytes(existing)).equals(Recorder.string(known,"sha256"))){
                    ref.addProperty("snapshot_id",id);ref.addProperty("availability","duplicate");ref.addProperty("relative_path","");
                    ref.addProperty("content_sha256",content);ref.addProperty("duplicate_of",Recorder.string(known,"snapshot_id"));
                    entries.add(ref);try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){entries.remove(entries.size()-1);throw ex;}return Recorder.copy(ref);
                }
                blobs().remove(content);
            }
            byte[] data=snapshot.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Replay point size limit");
            String name="turns/"+id+".json";atomic(directory.resolve(name),data);
            JsonObject blob=new JsonObject();blob.addProperty("relative_path",name);blob.addProperty("bytes",data.length);blob.addProperty("sha256",sha256(data));blob.addProperty("snapshot_id",id);blobs().add(content,blob);
            ref.addProperty("snapshot_id",id);ref.addProperty("relative_path",name);ref.addProperty("availability","recorded");ref.addProperty("bytes",data.length);ref.addProperty("sha256",sha256(data));ref.addProperty("content_sha256",content);
        }
        else {ref.addProperty("snapshot_id",id);ref.addProperty("relative_path","");if(!ref.has("availability"))ref.addProperty("availability","missing");}
        entries.add(ref);try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){entries.remove(entries.size()-1);throw ex;}
        evict(Recorder.string(ref,"branch_id"));
        return Recorder.copy(ref);
    }
    /** State-only hash: metadata and transient presentation values never split the same turn into two copies. */
    static String contentHash(JsonObject snapshot){
        JsonObject body=new JsonParser().parse(snapshot.toString()).getAsJsonObject();body.remove("identity");normalize(body);
        try{return sha256(body.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){return "";}
    }
    static final String[] TRANSIENT={"uuid","draw_y","intent_alpha","reticle_alpha","wait_timer"},HITBOX={"hb","health_hb","intent_hb"},HITBOX_TRANSIENT={"y","c_y","hovered","just_hovered"};
    static void normalize(JsonElement element){
        if(element.isJsonObject()){
            JsonObject o=element.getAsJsonObject();
            for(String k:TRANSIENT)o.remove(k);
            for(String k:HITBOX)if(o.has(k)&&o.get(k).isJsonObject()){JsonObject box=o.getAsJsonObject(k);for(String t:HITBOX_TRANSIENT)box.remove(t);}
            for(Map.Entry<String,JsonElement> e:new ArrayList<>(o.entrySet()))normalize(e.getValue());
        }else if(element.isJsonArray()){for(JsonElement x:element.getAsJsonArray())normalize(x);}
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
