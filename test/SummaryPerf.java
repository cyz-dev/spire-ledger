package spireledger;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** M1 performance measurement, not part of -Check: make a fixture from real records, then time one cold scan plus repeated filters per JVM. */
public final class SummaryPerf {
    static JsonObject parse(String s){return new JsonParser().parse(s).getAsJsonObject();}
    static String pct(List<Double> v){List<Double> s=new ArrayList<>(v);Collections.sort(s);return String.format(Locale.ROOT,"{\"n\":%d,\"p50\":%.3f,\"p95\":%.3f,\"max\":%.3f}",s.size(),s.get((s.size()-1)/2),s.get((int)Math.ceil(s.size()*.95)-1),s.get(s.size()-1));}
    public static void main(String[] args)throws Exception{
        if(args[0].equals("make")){Path src=Paths.get(args[1]),out=Paths.get(args[2]);int n=Integer.parseInt(args[3]);List<Path> runs=new ArrayList<>();
            // Real records only read; copies get fresh run and play IDs so no two fixture runs merge as duplicates.
            try(DirectoryStream<Path> d=Files.newDirectoryStream(src)){for(Path p:d)if(Files.isRegularFile(p.resolve("run.json"))&&Files.isRegularFile(p.resolve("events.jsonl")))runs.add(p);}Collections.sort(runs);
            for(int i=0;i<n;i++){Path from=runs.get(i%runs.size());String id=String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",i);Path dir=out.resolve(id);Files.createDirectories(dir);
                JsonObject run=parse(new String(Files.readAllBytes(from.resolve("run.json")),StandardCharsets.UTF_8));run.getAsJsonObject("metadata").addProperty("run_id",id);run.getAsJsonObject("metadata").addProperty("play_id","perf-"+i);Files.write(dir.resolve("run.json"),run.toString().getBytes(StandardCharsets.UTF_8));
                StringBuilder log=new StringBuilder();int seq=0;for(String line:new String(Files.readAllBytes(from.resolve("events.jsonl")),StandardCharsets.UTF_8).split("\n")){if(line.isEmpty())continue;JsonObject e=parse(line);if(e.has("header")){e.getAsJsonObject("header").addProperty("run_id",id);e.getAsJsonObject("header").addProperty("play_id","perf-"+i);}else seq=Math.max(seq,HistoryIndex.num(e,"seq",0));log.append(e).append('\n');}
                Files.write(dir.resolve("events.jsonl"),log.toString().getBytes(StandardCharsets.UTF_8));
                Files.write(dir.resolve(RunSummary.FILE),RunSummary.project(run,Files.size(dir.resolve("events.jsonl")),seq).toString().getBytes(StandardCharsets.UTF_8));}
            System.out.println("made "+n+" runs from "+runs.size()+" real records");return;}
        Path root=Paths.get(args[1]);
        if(args[0].equals("toggle")){boolean on=args[2].equals("on");int moved=0;try(DirectoryStream<Path> d=Files.newDirectoryStream(root)){for(Path p:d){Path a=p.resolve(RunSummary.FILE),b=p.resolve(RunSummary.FILE+".off");if(on&&Files.exists(b)){Files.move(b,a);moved++;}if(!on&&Files.exists(a)){Files.move(a,b);moved++;}}}System.out.println("toggled "+moved);return;}
        if(args[0].equals("verify")){int n=0,unique=0;Set<String> seen=new HashSet<>();try(DirectoryStream<Path> d=Files.newDirectoryStream(root)){for(Path p:d){HistoryIndex.Summary s=HistoryIndex.load(p),f=HistoryIndex.loadFull(p);if(!s.summarized)throw new AssertionError("summary not used "+p);SummaryCheck.same(s,f,p+" Summary");SummaryCheck.same(StatsIndex.notesRun(HistoryIndex.facts(s),s),StatsIndex.notesRun(HistoryIndex.document(f.file),f),p+" Run");n++;}}System.out.println("verify PASS: "+n+" runs, summary path equals full path for every Summary and Run field");return;}
        // scan: one cold measurement in this JVM, then 100 repeated filters on the loaded index.
        long t0=System.nanoTime();HistoryIndex.Index index=HistoryIndex.scan(root,null);long t1=System.nanoTime();StatsIndex.Index stats=StatsIndex.scan(root.resolve("no-native"),index,null);long t2=System.nanoTime();
        int summarized=0;for(HistoryIndex.Summary s:index.runs)if(s.summarized)summarized++;
        List<Double> hf=new ArrayList<>(),sf=new ArrayList<>();long now=System.currentTimeMillis();StatsIndex.Filter f=new StatsIndex.Filter();f.scope="notes";f.includeAbandon=true;int hits=0;
        for(int i=0;i<100;i++){long a=System.nanoTime();hits+=HistoryIndex.filter(index.runs,i%2==0?"WATCHER":"",-1,"",0,i%3==0?"bash":"",false,now).size();long b=System.nanoTime();f.character=i%2==0?"WATCHER":"";hits+=StatsIndex.metrics(StatsIndex.filter(stats,f,now),f).size();long c=System.nanoTime();hf.add((b-a)/1e6);sf.add((c-b)/1e6);}
        Runtime rt=Runtime.getRuntime();
        System.out.println(String.format(Locale.ROOT,"{\"runs\":%d,\"summarized\":%d,\"errors\":%d,\"history_scan_ms\":%.1f,\"stats_scan_ms\":%.1f,\"history_filter\":%s,\"stats_filter_metrics\":%s,\"used_heap_mb\":%d,\"hits\":%d}",index.runs.size(),summarized,index.errors.size()+stats.errors.size(),(t1-t0)/1e6,(t2-t1)/1e6,pct(hf),pct(sf),(rt.totalMemory()-rt.freeMemory())>>20,hits));
    }
}
