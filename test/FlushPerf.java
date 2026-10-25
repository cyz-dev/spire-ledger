package spireledger;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** M1 measurement, not part of -Check: replay a real run.json record by record through Recorder and time each append and flush. Public Recorder API only, so it also runs against older builds. */
public final class FlushPerf {
    public static void main(String[] args)throws Exception{
        JsonObject run=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)).getAsJsonObject();Path out=Paths.get(args[1]);int reps=Integer.parseInt(args[2]);
        JsonObject meta=run.getAsJsonObject("metadata");meta.remove("run_id");
        for(int rep=0;rep<reps;rep++){Recorder r=new Recorder(out,meta,null);
            // Each record is followed by a flush, as begin/settle/checkpoint do in the game; the line is the run.json size the flush wrote.
            for(JsonElement e:run.getAsJsonArray("records")){JsonObject o=e.getAsJsonObject();for(String k:Arrays.asList("seq","branch_id","time_ms"))o.remove(k);
                long a=System.nanoTime();r.append(o);long b=System.nanoTime();r.flush();long c=System.nanoTime();
                System.out.println(rep+" "+Files.size(r.directory.resolve("run.json"))+" "+(b-a)/1000+" "+(c-b)/1000);}
            r.close();}
    }
}
