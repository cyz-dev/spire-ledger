package spireledger.stats;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Statistics kernel entry: logical runs from native .run files and Ledger records, the sample scope, and the win bases (metrics.md 2-4). Reads only. */
public final class Stats {
    public static final long MAX_BYTES=32L*1024*1024;
    public static final class NativeFile {final JsonObject run;final String dir,file;public NativeFile(JsonObject run,String dir,String file){this.run=run;this.dir=dir;this.file=file;}}
    public static final class Index {
        public final List<Run> runs=new ArrayList<>();public final List<String> errors=new ArrayList<>();
        public int nativeFiles,ledgerRecords,nativeDuplicates,ledgerDuplicates,conflicts,linked,unlinked;
    }
    public static final class Filter {
        /** slot: the current save slot; slots are never merged (metrics.md 2). */
        public String character="",result="all",source="all",seed="all";public int ascension=-1,days,minSamples=1,slot;public long from,to;
        public boolean heart=true,includeAbandon,slOnly;
    }
    public static final class Selection {public final List<Run> runs=new ArrayList<>();public final Map<String,Integer> excluded=new TreeMap<>();}

    static String digest(JsonObject o){try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(o.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}

    /** Logical runs from a set of sources; the result does not depend on the order they are given in (metrics.md 4.5). */
    public static Index build(Collection<NativeFile> natives,Collection<Ledger> ledgers){
        Index index=new Index();Map<String,TreeMap<String,Run>> nativeCopies=new TreeMap<>();
        for(NativeFile f:natives){Run r=Native.read(f.run,f.dir,f.file);index.nativeFiles++;if(nativeCopies.containsKey(r.id))index.nativeDuplicates++;nativeCopies.computeIfAbsent(r.id,k->new TreeMap<>()).putIfAbsent(digest(f.run),r);}
        Map<String,Run> nativeRuns=new TreeMap<>();
        // Any two differing copies of one play_id conflict, whichever was read first.
        for(Map.Entry<String,TreeMap<String,Run>> e:nativeCopies.entrySet()){Run r=e.getValue().firstEntry().getValue();if(e.getValue().size()>1){r.conflict=true;r.conflictReason="native_copies";index.conflicts++;}nativeRuns.put(e.getKey(),r);}
        Map<String,List<Ledger>> byRunId=new TreeMap<>();for(Ledger l:ledgers){index.ledgerRecords++;byRunId.computeIfAbsent(l.runId,k->new ArrayList<>()).add(l);}
        Map<String,List<Run>> byPlay=new TreeMap<>();List<Run> alone=new ArrayList<>();
        for(List<Ledger> copies:byRunId.values()){TreeMap<String,Ledger> contents=new TreeMap<>();for(Ledger l:copies)contents.put(l.digest,l);index.ledgerDuplicates+=copies.size()-1;
            Ledger first=contents.firstEntry().getValue();for(Ledger l:copies)if(l.current)first=l;Run r=first.run.copy();if(contents.size()>1){r.conflict=true;r.conflictReason="ledger_copies";}
            if(nativeRuns.containsKey(r.playId))byPlay.computeIfAbsent(r.playId,k->new ArrayList<>()).add(r);else{alone.add(r);index.unlinked++;}}
        for(Map.Entry<String,Run> e:nativeRuns.entrySet()){Run n=e.getValue();List<Run> led=byPlay.getOrDefault(e.getKey(),Collections.emptyList());
            if(led.isEmpty()){index.runs.add(n);continue;}
            // Several Ledger records on one play_id are same-rank sources that cannot be ordered: the logical run is a conflict.
            if(led.size()>1){n.conflict=true;n.conflictReason="ledger_records_per_play_id";index.conflicts++;index.runs.add(n);continue;}
            Run l=led.get(0);if(l.conflict){index.conflicts++;index.runs.add(n);continue;}
            index.linked++;index.runs.add(Run.merge(n,l));}
        for(Run r:alone){if(r.conflict)index.conflicts++;index.runs.add(r);}
        index.runs.sort(Comparator.comparingLong((Run r)->-r.time.or(0L)).thenComparing(r->r.id));Collections.sort(index.errors);return index;
    }
    static JsonObject read(Path file)throws IOException{if(Files.isSymbolicLink(file)||Files.size(file)>MAX_BYTES)throw new IOException("file bounds");try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){return new JsonParser().parse(reader).getAsJsonObject();}}
    /** Native history is runs/&lt;dir&gt;/*.run; unreadable or unrecognized files are reported, not counted. */
    public static Index scan(Path nativeRoot,Collection<Ledger> ledgers,List<String> ledgerErrors)throws IOException{
        List<NativeFile> natives=new ArrayList<>();List<String> errors=new ArrayList<>(ledgerErrors);
        if(Files.isDirectory(nativeRoot)&&!Files.isSymbolicLink(nativeRoot))try(DirectoryStream<Path> dirs=Files.newDirectoryStream(nativeRoot)){for(Path dir:dirs)if(Files.isDirectory(dir)&&!Files.isSymbolicLink(dir))try(DirectoryStream<Path> files=Files.newDirectoryStream(dir,"*.run")){
            for(Path file:files)try{JsonObject data=read(file);if(Json.str(data,"character_chosen").isEmpty())throw new IOException("Unrecognized native history format");natives.add(new NativeFile(data,dir.getFileName().toString(),file.getFileName().toString()));}catch(Exception e){errors.add(file.getFileName()+": "+e.getClass().getSimpleName());}}}
        Index index=build(natives,ledgers);index.errors.addAll(errors);Collections.sort(index.errors);return index;
    }

    /** Daily, endless or custom (is_trial; metrics.md 2 leaves custom detection unverified); null when no source says. */
    static Boolean special(Run r){Boolean d=r.daily.or(null),e=r.endless.or(null),t=r.trial.or(null);if(Boolean.TRUE.equals(d)||Boolean.TRUE.equals(e)||Boolean.TRUE.equals(t))return true;return d==null||e==null||t==null?null:false;}
    /** The formal sample (metrics.md 2) narrowed by the filter; each exclusion is counted by reason, narrowing is not. */
    public static Selection select(Index index,Filter f,long now){Selection s=new Selection();for(Run r:index.runs){String why=reason(r,f,now);if(why==null)s.runs.add(r);else if(!why.isEmpty())s.excluded.merge(why,1,Integer::sum);}return s;}
    static String reason(Run r,Filter f,long now){
        String outcome=r.outcome.or(null);Boolean special=special(r);long time=r.time.or(0L);
        if(r.conflict)return "conflict";if(r.fixture)return "fixture";if(r.practice)return "practice";if(r.current)return "current";if("incomplete".equals(outcome))return "incomplete";
        // Daily, custom and endless runs are never counted (metrics.md 2); an unstated mode stays in.
        if(Boolean.TRUE.equals(special))return "mode";
        // Only the current save slot; a run whose slot no source gives is in no slot's statistics.
        if(!Integer.valueOf(f.slot).equals(r.slot.or(null)))return "slot";
        if("abandon".equals(outcome)&&!f.includeAbandon)return "abandon";
        boolean shown=(f.character.isEmpty()||f.character.equals(r.character.or(null)))&&(f.ascension<0||f.ascension==r.ascension.or(-1))&&(f.days==0||time>0&&time>=now-f.days*86400000L)&&(f.from==0||time>=f.from)&&(f.to==0||time<f.to)
            &&(!f.slOnly||r.sl)&&(f.source.equals("all")||r.has(f.source))&&(f.seed.equals("all")||Boolean.valueOf(f.seed.equals("only")).equals(r.seeded.or(null)));
        if(!shown)return "";
        if(f.result.equals("all"))return null;Boolean w=win(r,f.heart);if(w==null)return "result_unknown";return w==f.result.equals("win")?null:"";
    }
    /** metrics.md 3.2. Heart: a heart kill wins. Act 3: clearing act 3 wins even with a death in act 4. Abandons lose; a missing fact is null, never a loss. */
    public static Boolean win(Run r,boolean heart){String o=r.outcome.or(null);if("abandon".equals(o))return false;
        if(heart){Boolean h=r.heart.or(null);if(Boolean.TRUE.equals(h))return true;if(o==null)return null;return o.equals("death")?Boolean.FALSE:h==null?null:Boolean.FALSE;}
        Boolean a=r.act3.or(null);if(o==null||a==null)return null;if(a)return true;return o.equals("death")?Boolean.FALSE:null;}
}
