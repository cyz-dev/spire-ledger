package sts1state;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** JSON values only: never retain game objects or RNGs. Called on the game thread. */
public final class Recorder implements AutoCloseable {
    public static final String SCHEMA="sts1-state-v1", VERSION="0.4.0";
    public final Path directory;
    public final String runId;
    public String branch;
    private final JsonObject root;
    private final JsonArray records;
    private final FileOutputStream journal;
    private int sequence, segmentStart, segment;
    private final Map<String,JsonObject> pending=new LinkedHashMap<>();
    private final Set<String> completed=new HashSet<>();
    public Recorder(Path parent, JsonObject metadata, String restoreId) throws IOException {
        runId=restoreId==null?UUID.randomUUID().toString():restoreId;
        if(!runId.matches("[a-fA-F0-9-]{36}")) throw new IOException("Invalid run identity");
        directory=parent.resolve(runId); Files.createDirectories(directory);
        Path log=directory.resolve("events.jsonl");
        root=new JsonObject(); records=new JsonArray();
        root.addProperty("schema",SCHEMA); root.add("metadata",copy(metadata));
        root.getAsJsonObject("metadata").addProperty("run_id",runId);
        root.add("records",records);
        if(Files.exists(log)) {
            // A crash may leave only the last line incomplete. Never skip a corrupt middle line.
            byte[] bytes=Files.readAllBytes(log); int end=bytes.length;
            while(end>0&&bytes[end-1]!='\n') end--;
            String[] lines=new String(bytes,0,end,StandardCharsets.UTF_8).split("\n");
            for(String line:lines) if(!line.isEmpty()) {
                JsonObject e=new JsonParser().parse(line).getAsJsonObject();
                if(e.has("header")) root.add("metadata",e.get("header"));
                else { records.add(e); sequence=Math.max(sequence,e.get("seq").getAsInt());
                    if(e.has("decision_id")&&"result".equals(string(e,"type"))) completed.add(string(e,"decision_id")); }
            }
            try(RandomAccessFile f=new RandomAccessFile(log.toFile(),"rw")){f.setLength(end);}
        }
        journal=new FileOutputStream(log.toFile(),true);
        if(sequence==0) {JsonObject h=new JsonObject();h.add("header",root.get("metadata"));line(h);}
        branch=UUID.randomUUID().toString();
        int existing=0;
        try(DirectoryStream<Path> paths=Files.newDirectoryStream(directory,"segment-*.json")){for(Path p:paths)existing++;}
        segment=existing;segmentStart=records.size();
        // Pending decisions from a crashed process remain visible and are explicitly interrupted.
        Set<String> begun=new LinkedHashSet<>();
        for(JsonElement e:records)if("decision".equals(string(e.getAsJsonObject(),"type")))begun.add(string(e.getAsJsonObject(),"decision_id"));
        begun.removeAll(completed);
        for(String id:begun){JsonObject e=object("result");e.addProperty("decision_id",id);e.addProperty("status","interrupted");e.addProperty("reason","process_restart");append(e);completed.add(id);}
    }
    public static JsonObject copy(JsonObject value){return new JsonParser().parse(value.toString()).getAsJsonObject();}
    public static String string(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    public static JsonObject object(String type){JsonObject o=new JsonObject();o.addProperty("type",type);return o;}
    private void line(JsonObject value)throws IOException{journal.write((value+"\n").getBytes(StandardCharsets.UTF_8));journal.flush();journal.getFD().sync();}
    public void append(JsonObject record)throws IOException {
        JsonObject e=copy(record); e.addProperty("seq",++sequence);e.addProperty("branch_id",branch);e.addProperty("time_ms",System.currentTimeMillis());
        if(e.toString().length()>2*1024*1024)throw new IOException("Record exceeds 2 MiB");
        line(e);records.add(e);
        if(records.size()-segmentStart>=16)flush();
    }
    public String begin(String key,String source,JsonObject before,JsonArray candidates,String selected,String actor)throws IOException {
        // Key represents the live reward, not floor/name. Reopening is not a new choice.
        if(pending.containsKey(key)||completed.contains(key))return null;
        String id=UUID.randomUUID().toString();JsonObject d=object("decision");
        d.addProperty("decision_id",id);d.addProperty("reward_key",key);d.addProperty("source",source);
        d.addProperty("candidate_id",selected);d.addProperty("action_source",actor);d.addProperty("status","pending");
        d.add("before",copy(before));d.add("candidates",new JsonParser().parse(candidates.toString()));
        append(d);pending.put(key,d);flush();return id;
    }
    public boolean hasPending(){return !pending.isEmpty();}
    public boolean hasMap(int act){for(JsonElement e:records){JsonObject r=e.getAsJsonObject();if("map".equals(string(r,"type"))&&r.has("act")&&r.get("act").getAsInt()==act)return true;}return false;}
    public void settle(JsonObject after)throws IOException {
        for(String key:new ArrayList<>(pending.keySet()))settleKey(key,after);
        flush();
    }
    public void settleKey(String key,JsonObject after)throws IOException {
        JsonObject d=pending.remove(key);if(d!=null){
            JsonObject result=object("result");result.addProperty("decision_id",string(d,"decision_id"));
            result.addProperty("status","confirmed");result.addProperty("confirmation","native_commit_hook_and_effects_settled");result.add("after",copy(after));append(result);
            completed.add(key);completed.add(string(d,"decision_id"));
        }
    }
    public void interrupt(String reason)throws IOException {
        for(JsonObject d:pending.values()){JsonObject e=object("result");e.addProperty("decision_id",string(d,"decision_id"));e.addProperty("status","interrupted");e.addProperty("reason",reason);append(e);}
        pending.clear();flush();
    }
    public JsonObject checkpoint(JsonObject state)throws IOException {
        JsonObject e=object("checkpoint");e.addProperty("checkpoint_id",UUID.randomUUID().toString());e.addProperty("availability","save_requested; no resumable attachment");e.add("state",copy(state));append(e);flush();
        JsonObject ref=new JsonObject();ref.addProperty("run_id",runId);ref.addProperty("checkpoint_id",string(e,"checkpoint_id"));ref.addProperty("branch_id",branch);return ref;
    }
    public void resume(JsonObject ref,JsonObject state)throws IOException {
        JsonObject e=object("load");boolean known=false;
        String point=string(ref,"checkpoint_id");
        for(JsonElement r:records)if(!point.isEmpty()&&"checkpoint".equals(string(r.getAsJsonObject(),"type"))&&point.equals(string(r.getAsJsonObject(),"checkpoint_id")))known=true;
        e.addProperty("parent_checkpoint_id",known?point:"unknown");e.addProperty("parent_branch_id",known?string(ref,"branch_id"):"unknown");e.addProperty("parent_status",known?"local_checkpoint_found":"unknown");e.add("state",copy(state));append(e);flush();
    }
    public void forkBranch(String parentBranch,String reason,JsonObject details)throws IOException {
        branch=UUID.randomUUID().toString();
        JsonObject e=object("turn_rollback");
        e.addProperty("parent_branch_id",parentBranch);
        e.addProperty("reason",reason);
        if(details!=null)for(Map.Entry<String,JsonElement> entry:details.entrySet())e.add(entry.getKey(),entry.getValue());
        append(e);flush();
    }
    JsonArray recent(int count){JsonArray out=new JsonArray();for(int i=Math.max(0,records.size()-count);i<records.size();i++)out.add(copy(records.get(i).getAsJsonObject()));return out;}
    public JsonObject document(){return copy(root);}
    private static void atomic(Path path,byte[] data)throws IOException {
        Path tmp=path.resolveSibling(path.getFileName()+".tmp");Files.write(tmp,data);
        try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    public void flush()throws IOException {
        if(records.size()>segmentStart){JsonObject part=new JsonObject();part.addProperty("schema",SCHEMA);part.add("metadata",root.get("metadata"));JsonArray slice=new JsonArray();for(int i=segmentStart;i<records.size();i++)slice.add(records.get(i));part.add("records",slice);
            atomic(directory.resolve(String.format(Locale.ROOT,"segment-%05d.json",++segment)),part.toString().getBytes(StandardCharsets.UTF_8));segmentStart=records.size();}
        atomic(directory.resolve("run.json"),root.toString().getBytes(StandardCharsets.UTF_8));
        Path html=directory.resolve("review.html");if(!Files.exists(html))try(InputStream in=Recorder.class.getResourceAsStream("/review.html")){if(in!=null)Files.copy(in,html);}
    }
    public Path export()throws IOException {
        flush();Path target=directory.resolve("run.zip"),tmp=directory.resolve("run.zip.tmp");
        try{writeZip(tmp,true);}catch(IOException ex){
            // A damaged optional attachment must not stop build recording. Keep an
            // explicit export diagnostic and produce the independently readable base.
            JsonObject error=object("replay_export_unavailable");error.addProperty("reason",ex.getClass().getSimpleName());append(error);flush();writeZip(tmp,false);
            System.err.println("[State Notes] Optional replay export failed; base package exported: "+ex.getClass().getSimpleName());
        }
        Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);return target;
    }
    private void writeZip(Path tmp,boolean replay)throws IOException {
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(tmp))){for(String name:Arrays.asList("run.json","review.html")){Path p=directory.resolve(name);if(Files.exists(p)){zip.putNextEntry(new ZipEntry(name));Files.copy(p,zip);zip.closeEntry();}}if(replay)TurnFiles.addToZip(directory,zip);}
    }
    public void close()throws IOException{try{interrupt("session_closed");export();}finally{journal.close();}}
}
