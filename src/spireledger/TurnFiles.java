package spireledger;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Independent completed files, followed by an atomic manifest publication; one file per distinct board. */
final class TurnFiles {
    static final String SCHEMA="spire-ledger-turn-v1";
    static final int MAX_BYTES=2*1024*1024, MAX_POINTS=10000;
    /** The manifest grows with the point list, so it needs more room than a single point. */
    static final long MAX_MANIFEST=8L*1024*1024;
    final Path directory;
    final JsonObject manifest;
    TurnFiles(Path runDirectory,String runId,JsonObject environment)throws IOException {
        directory=runDirectory.resolve("replay");Files.createDirectories(directory.resolve("turns"));
        Path path=directory.resolve("manifest.json");
        if(Files.exists(path)){manifest=read(path,MAX_MANIFEST);if(!SCHEMA.equals(Recorder.string(manifest,"schema"))||!runId.equals(Recorder.string(manifest,"run_id")))throw new IOException("Replay identity mismatch");}
        else {manifest=Recorder.copy(environment);manifest.addProperty("schema",SCHEMA);manifest.addProperty("run_id",runId);manifest.add("snapshots",new JsonArray());}
        // Runs recorded before boards were deduplicated carry no blob table; they gain one on the next point.
        if(!manifest.has("blobs")||!manifest.get("blobs").isJsonObject())manifest.add("blobs",new JsonObject());
    }
    static JsonObject read(Path path)throws IOException {return read(path,MAX_BYTES);}
    static JsonObject read(Path path,long max)throws IOException {if(Files.isSymbolicLink(path)||Files.size(path)>max)throw new IOException("Replay file bounds");try{return new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){throw new IOException("Invalid replay JSON",ex);}}
    /** The fight the last point belongs to, so a restarted process can resume the id instead of starting a second fight. */
    static JsonObject lastCombat(Path manifest)throws IOException {JsonObject doc=read(manifest,MAX_MANIFEST);JsonElement last=doc.get("last_combat");return last!=null&&last.isJsonObject()?last.getAsJsonObject():null;}
    static void atomic(Path path,byte[] bytes)throws IOException {Path tmp=path.resolveSibling(path.getFileName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes);out.flush();out.getFD().sync();}Recorder.replace(tmp,path);}
    JsonObject blobs(){return manifest.getAsJsonObject("blobs");}
    /**
     * Stores a point once per distinct board. Re-entering the same turn with the same board (S/L, practice, a rolled
     * back turn) hashes to the state already on disk: the existing point only gains an attempt counter, no second
     * blob and no second manifest row. The session itself stays in events.jsonl, so nothing about it is lost.
     */
    JsonObject publish(JsonObject entry,JsonObject snapshot)throws IOException {
        JsonArray entries=manifest.getAsJsonArray("snapshots");
        JsonObject ref=Recorder.copy(entry);String id=UUID.randomUUID().toString();ref.addProperty("snapshot_id",id);
        if(snapshot!=null){
            JsonObject identity=Recorder.copy(ref);identity.addProperty("run_id",Recorder.string(manifest,"run_id"));snapshot.add("identity",identity);
            String content=contentHash(snapshot);
            JsonObject known=blobs().has(content)?blobs().getAsJsonObject(content):null;
            if(known!=null&&intact(known)){
                JsonObject original=recordedFor(entries,content);
                ref.addProperty("relative_path","");ref.addProperty("availability","duplicate");
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
            // A blob row whose file no longer matches is stale; drop it and store this board again.
            if(known!=null)blobs().remove(content);
            if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");
            byte[] data=snapshot.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Replay point size limit");
            String name="turns/"+id+".json";atomic(directory.resolve(name),data);
            String sha=sha256(data);
            JsonObject blob=new JsonObject();blob.addProperty("relative_path",name);blob.addProperty("bytes",data.length);blob.addProperty("sha256",sha);blob.addProperty("snapshot_id",id);
            blobs().add(content,blob);
            ref.addProperty("relative_path",name);ref.addProperty("availability","recorded");ref.addProperty("bytes",data.length);ref.addProperty("sha256",sha);ref.addProperty("content_sha256",content);ref.addProperty("attempts",1);
        }
        else {if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");ref.addProperty("relative_path","");if(!ref.has("availability"))ref.addProperty("availability","missing");}
        entries.add(ref);
        // The manifest remembers the fight, not just the points: after a full restart the next battle prep reads it back.
        noteLastCombat(entry);
        try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){entries.remove(entries.size()-1);throw ex;}return Recorder.copy(ref);
    }
    private static JsonObject recordedFor(JsonArray entries,String content){
        for(JsonElement item:entries){JsonObject e=item.getAsJsonObject();if("recorded".equals(Recorder.string(e,"availability"))&&content.equals(Recorder.string(e,"content_sha256")))return e;}
        return null;
    }
    private boolean intact(JsonObject blob){
        try{Path point=directory.resolve(Recorder.string(blob,"relative_path"));return Files.isRegularFile(point)&&sha256(Files.readAllBytes(point)).equals(Recorder.string(blob,"sha256"));}catch(IOException|RuntimeException ex){return false;}
    }
    private void noteLastCombat(JsonObject entry){
        if(!entry.has("combat_id"))return;
        JsonObject last=new JsonObject();last.addProperty("combat_id",Recorder.string(entry,"combat_id"));last.addProperty("floor",entry.has("floor")?entry.get("floor").getAsInt():-1);
        manifest.add("last_combat",last);
    }
    /** State-only hash: metadata and transient presentation values never split the same turn into two copies. */
    static String contentHash(JsonObject snapshot){
        try{return sha256(SnapshotShape.body(snapshot).toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){return "";}
    }
    static String sha256(byte[] bytes)throws IOException{try{StringBuilder out=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException ex){throw new IOException(ex);}}
    static void addToZip(Path runDirectory,ZipOutputStream zip)throws IOException {
        Path directory=runDirectory.resolve("replay"),path=directory.resolve("manifest.json");if(!Files.exists(path))return;JsonObject manifest=read(path,MAX_MANIFEST);if(!SCHEMA.equals(Recorder.string(manifest,"schema")))throw new IOException("Replay schema mismatch");
        JsonArray entries=manifest.getAsJsonArray("snapshots");if(entries==null||entries.size()>MAX_POINTS)throw new IOException("Replay point bounds");Set<String> names=new HashSet<>();
        for(JsonElement item:entries){JsonObject ref=item.getAsJsonObject();if(!"recorded".equals(Recorder.string(ref,"availability")))continue;String name=Recorder.string(ref,"relative_path");if(!name.matches("turns/[a-fA-F0-9-]{36}\\.json")||!names.add(name))throw new IOException("Replay path or duplicate");Path point=directory.resolve(name);if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(directory.resolve("turns"))||Files.isSymbolicLink(point)||Files.size(point)>MAX_BYTES)throw new IOException("Replay point bounds");zip.putNextEntry(new ZipEntry("replay/"+name));Files.copy(point,zip);zip.closeEntry();}
        zip.putNextEntry(new ZipEntry("replay/manifest.json"));Files.copy(path,zip);zip.closeEntry();
    }
}
