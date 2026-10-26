package spireledger.stats;

import java.util.*;

/** Presentation of observed pick rates. Does not estimate card strength or standardized preference. */
public final class PickTiers {
    public static final String VERSION="pick-tiers-v1";
    // Percentage boundaries also used by TinyCat's observed pick-rate table; no model fitting.
    public static final int[] LOWER={90,75,60,45,30,15,0};
    public static final class Entry {
        public final String card,status;public final int act,tier,samples,estimated;
        public final double picked,offered;
        Entry(String card,int act,Metric metric){this.card=card;this.act=act;status=metric.status;samples=metric.samples;estimated=metric.estimated;picked=metric.numerator;offered=metric.denominator;tier=metric.shown()?tier(picked,offered):8;}
        public double rate(){return offered>0?picked/offered:0;}
    }
    public static int tier(double picked,double offered){
        if(!Double.isFinite(picked)||!Double.isFinite(offered)||offered<=0||picked<0||picked>offered)return 8;
        if(picked==0)return 7;
        for(int i=0;i<LOWER.length;i++)if(picked*100>=offered*LOWER[i])return i;
        return 8;
    }
    public static List<Entry> entries(Map<String,Metric> metrics,int act){
        List<Entry> out=new ArrayList<>();String prefix="pick:"+act+":";
        for(Metric metric:metrics.values())if(metric.key.startsWith(prefix)&&(metric.shown()||metric.status.equals("below_min_sample")))out.add(new Entry(metric.key.substring(prefix.length()),act,metric));
        out.sort(Comparator.comparingInt((Entry e)->e.tier).thenComparingDouble(e->-e.rate()).thenComparingInt(e->-e.samples).thenComparing(e->e.card));return out;
    }
    private PickTiers(){}
}
