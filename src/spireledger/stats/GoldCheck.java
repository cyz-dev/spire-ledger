package spireledger.stats;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Gold truth check (metrics.md 5.2, 6.6): each Ledger record's exact gold_gained against the estimate from its linked .run. Reads only. */
public final class GoldCheck {
    public static final class Row {public final String playId,character;public final double exact,estimate;Row(String p,String c,double x,double e){playId=p;character=c;exact=x;estimate=e;}}

    /** Pairs by play_id; runs missing either value are left out. */
    public static List<Row> rows(Stats.Index natives,Collection<Ledger> ledgers){
        Map<String,Run> byPlay=new HashMap<>();for(Run r:natives.runs)if(!r.playId.isEmpty())byPlay.put(r.playId,r);
        List<Row> rows=new ArrayList<>();
        for(Ledger l:ledgers){Run n=byPlay.get(l.run.playId);if(n==null||!l.run.gold.known()||!n.gold.known())continue;rows.add(new Row(l.run.playId,n.character.or(""),l.run.gold.value,n.gold.value));}
        rows.sort(Comparator.comparing(r->r.playId));return rows;
    }

    /** GoldCheck &lt;runs dir&gt; &lt;~/.spire-ledger/runs&gt;: one line per linked run, then the mean and largest absolute error. */
    public static void main(String[] a)throws IOException{
        if(a.length!=2){System.err.println("usage: GoldCheck <runs dir> <spire-ledger runs dir>");System.exit(2);}
        List<Ledger> ledgers=new ArrayList<>();
        try(DirectoryStream<Path> dirs=Files.newDirectoryStream(Paths.get(a[1]))){for(Path d:dirs){Path f=d.resolve("run.json");if(Files.isRegularFile(f))try{ledgers.add(new Ledger(Stats.read(f),false,false));}catch(IOException|RuntimeException e){System.err.println("skip "+d.getFileName()+": "+e.getMessage());}}}
        List<Row> rows=rows(Stats.scan(Paths.get(a[0]),Collections.<Ledger>emptyList(),Collections.<String>emptyList()),ledgers);
        double sum=0,max=0;for(Row r:rows){double d=r.estimate-r.exact;sum+=Math.abs(d);max=Math.max(max,Math.abs(d));System.out.printf(Locale.ROOT,"%s %s exact=%.0f estimate=%.0f diff=%+.0f%n",r.playId,r.character,r.exact,r.estimate,d);}
        System.out.printf(Locale.ROOT,"%d linked runs; mean abs error %.1f, max %.0f (TinyCat reports 38 and 160 on its author's data)%n",rows.size(),rows.isEmpty()?0:sum/rows.size(),max);
    }
}
