package spireledger.stats;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Writes the Java side of test/check_overview.py and test/check_boss_standardized.py from native .run files only.
 * After build.ps1: javac -encoding UTF-8 --release 8 -cp "build\gson;build\stats-classes" -d build\dump test\StatsDump.java, then
 * java -cp "build\gson;build\stats-classes;build\dump" spireledger.stats.StatsDump <runs dir> <slot> build\dump-out
 * Out: overview.tsv, overview-ids.tsv and boss-shares.tsv, with the stats screen's default filter on that slot.
 */
public final class StatsDump {
    public static void main(String[] args)throws Exception{
        Stats.Index index=Stats.scan(Paths.get(args[0]),new ArrayList<Ledger>(),new ArrayList<String>());
        Stats.Filter f=new Stats.Filter();f.slot=Integer.parseInt(args[1]);Path out=Files.createDirectories(Paths.get(args[2]));
        Stats.Selection s=Stats.select(index,f,System.currentTimeMillis());Map<String,Metric> m=Metrics.compute(s,f);
        StringBuilder overview=new StringBuilder("key\tnumerator\tdenominator\tsamples\tvalue\tvariance\tlow\thigh\n");
        for(String key:Arrays.asList("win","deck_size","score","purge_total","purge_shop","purge_event")){Metric x=m.get(key);double[] ci=x.interval();double scale=x.kind.equals(Metric.RATE)?100:1;
            overview.append(key).append('\t').append(x.numerator).append('\t').append(x.denominator).append('\t').append(x.samples).append('\t').append(x.value()).append('\t').append(x.variance())
                .append('\t').append(ci==null?Double.NaN:ci[0]*scale).append('\t').append(ci==null?Double.NaN:ci[1]*scale).append('\n');}
        StringBuilder ids=new StringBuilder();for(Run r:s.runs)ids.append(r.id).append('\n');
        StringBuilder boss=new StringBuilder("act\trelic\tshare\n");
        for(Map.Entry<Integer,Standardized.Act> e:Standardized.computeBoss(s.runs).entrySet())for(Map.Entry<String,Double> share:e.getValue().share.entrySet())boss.append(e.getKey()).append('\t').append(share.getKey()).append('\t').append(share.getValue()).append('\n');
        Files.write(out.resolve("overview.tsv"),overview.toString().getBytes(StandardCharsets.UTF_8));Files.write(out.resolve("overview-ids.tsv"),ids.toString().getBytes(StandardCharsets.UTF_8));Files.write(out.resolve("boss-shares.tsv"),boss.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("StatsDump: "+s.runs.size()+" runs on slot "+f.slot+" -> "+out);
    }
    private StatsDump(){}
}
