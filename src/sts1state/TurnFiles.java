package sts1state;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Independent completed files, followed by an atomic manifest publication. */
final class TurnFiles {
    static final String SCHEMA="sts1-turn-replay-v1";
    static final int MAX_BYTES=2*1024*1024, MAX_POINTS=10000;
    final Path directory;
    final JsonObject manifest;
    TurnFiles(Path runDirectory,String runId,JsonObject environment)throws IOException {
        directory=runDirectory.resolve("replay");Files.createDirectories(directory.resolve("turns"));
        Path path=directory.resolve("manifest.json");
        if(Files.exists(path)){manifest=read(path);if(!SCHEMA.equals(Recorder.string(manifest,"schema"))||!runId.equals(Recorder.string(manifest,"run_id")))throw new IOException("Replay identity mismatch");}
        else {manifest=Recorder.copy(environment);manifest.addProperty("schema",SCHEMA);manifest.addProperty("run_id",runId);manifest.add("snapshots",new JsonArray());}
    }
    static JsonObject read(Path path)throws IOException {if(Files.isSymbolicLink(path)||Files.size(path)>MAX_BYTES)throw new IOException("Replay file bounds");try{return new JsonParser().parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException ex){throw new IOException("Invalid replay JSON",ex);}}
    static void atomic(Path path,byte[] bytes)throws IOException {Path tmp=path.resolveSibling(path.getFileName()+".tmp");try(FileOutputStream out=new FileOutputStream(tmp.toFile())){out.write(bytes);out.flush();out.getFD().sync();}try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}
    JsonObject publish(JsonObject entry,JsonObject snapshot)throws IOException {
        JsonArray entries=manifest.getAsJsonArray("snapshots");if(entries.size()>=MAX_POINTS)throw new IOException("Replay point limit");
        JsonObject ref=Recorder.copy(entry);String id=UUID.randomUUID().toString();ref.addProperty("snapshot_id",id);
        if(snapshot!=null){JsonObject identity=Recorder.copy(ref);identity.addProperty("run_id",Recorder.string(manifest,"run_id"));snapshot.add("identity",identity);byte[] data=snapshot.toString().getBytes(StandardCharsets.UTF_8);if(data.length>MAX_BYTES)throw new IOException("Replay point size limit");String name="turns/"+id+".json";atomic(directory.resolve(name),data);ref.addProperty("relative_path",name);ref.addProperty("availability","recorded");ref.addProperty("bytes",data.length);ref.addProperty("sha256",sha256(data));}
        else {ref.addProperty("relative_path","");if(!ref.has("availability"))ref.addProperty("availability","missing");}
        entries.add(ref);try{atomic(directory.resolve("manifest.json"),manifest.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ex){entries.remove(entries.size()-1);throw ex;}return Recorder.copy(ref);
    }
    static String sha256(byte[] bytes)throws IOException{try{StringBuilder out=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format("%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException ex){throw new IOException(ex);}}
    static void addToZip(Path runDirectory,ZipOutputStream zip)throws IOException {
        Path directory=runDirectory.resolve("replay"),path=directory.resolve("manifest.json");if(!Files.exists(path))return;JsonObject manifest=read(path);if(!SCHEMA.equals(Recorder.string(manifest,"schema")))throw new IOException("Replay schema mismatch");
        JsonArray entries=manifest.getAsJsonArray("snapshots");if(entries==null||entries.size()>MAX_POINTS)throw new IOException("Replay point bounds");Set<String> names=new HashSet<>();
        for(JsonElement item:entries){JsonObject ref=item.getAsJsonObject();if(!"recorded".equals(Recorder.string(ref,"availability")))continue;String name=Recorder.string(ref,"relative_path");if(!name.matches("turns/[a-fA-F0-9-]{36}\\.json")||!names.add(name))throw new IOException("Replay path or duplicate");Path point=directory.resolve(name);if(Files.isSymbolicLink(directory)||Files.isSymbolicLink(directory.resolve("turns"))||Files.isSymbolicLink(point)||Files.size(point)>MAX_BYTES)throw new IOException("Replay point bounds");zip.putNextEntry(new ZipEntry("replay/"+name));Files.copy(point,zip);zip.closeEntry();}
        zip.putNextEntry(new ZipEntry("replay/manifest.json"));Files.copy(path,zip);zip.closeEntry();
    }
}
