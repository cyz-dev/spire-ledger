package spireledger.stats;

import java.util.*;

/** Presentation of observed pick rates and of standardized strength shares. Neither is an estimate of card strength on its own. */
public final class PickTiers {
    public static final String VERSION="pick-tiers-v1";
    // Percentage boundaries also used by TinyCat's observed pick-rate table; no model fitting.
    public static final int[] LOWER={90,75,60,45,30,15,0};
    /** Standardized view: five Jenks breaks on shares at or above 1%, then the <1% tail cut twice on log10 (TinyCat's assignStandardized). */
    public static final int STANDARD_TIERS=7;
    static final String[] STANDARD_NAMES={"S","A","B","C","D","R0","R00"};
    public static final class Entry {
        public final String card,status;public final int act,tier,samples,estimated;
        public final double picked,offered,standard;
        Entry(String card,int act,Metric metric){this(card,act,metric,metric.shown()?tier(metric.numerator,metric.denominator):8,-1);}
        Entry(String card,int act,Metric metric,int tier,double standard){this.card=card;this.act=act;status=metric.status;samples=metric.samples;estimated=metric.estimated;picked=metric.numerator;offered=metric.denominator;this.tier=tier;this.standard=standard;}
        public double rate(){return offered>0?picked/offered:0;}
        /** True for rows that carry a strength share instead of a picked/offered count. */
        public boolean standardized(){return standard>=0;}
    }
    public static int tier(double picked,double offered){        if(!Double.isFinite(picked)||!Double.isFinite(offered)||offered<=0||picked<0||picked>offered)return 8;
        if(picked==0)return 7;
        for(int i=0;i<LOWER.length;i++)if(picked*100>=offered*LOWER[i])return i;
        return 8;
    }
    public static List<Entry> entries(Map<String,Metric> metrics,int act){
        List<Entry> out=new ArrayList<>();String prefix="pick:"+act+":";
        for(Metric metric:metrics.values())if(metric.key.startsWith(prefix)&&(metric.shown()||metric.status.equals("below_min_sample")))out.add(new Entry(metric.key.substring(prefix.length()),act,metric));
        out.sort(Comparator.comparingInt((Entry e)->e.tier).thenComparingDouble(e->-e.rate()).thenComparingInt(e->-e.samples).thenComparing(e->e.card));return out;
    }
    /** A row outside the fitted universe (Skip, Bowl): its tier still comes from the observed rate. */
    public static Entry observed(String card,int act,Metric metric){return new Entry(card,act,metric);}
    /** The same tier/rate/sample order as {@link #entries}; used after appending rows the fit never reports. */
    public static void sort(List<Entry> entries){entries.sort(Comparator.comparingInt((Entry e)->e.tier).thenComparingDouble(e->-e.rate()).thenComparingInt(e->-e.samples).thenComparing(e->e.card));}
    /** Cards the standardized fit could report for this act, strongest share first; low samples stay out, as in the observed tiers. */
    public static List<Entry> standardized(Map<String,Metric> metrics,int act,Map<String,Double> share){
        List<Entry> out=new ArrayList<>();if(share==null)return out;
        String prefix="pick:"+act+":";List<Metric> shown=new ArrayList<>();
        for(Metric metric:metrics.values())if(metric.key.startsWith(prefix)&&metric.shown()&&share.containsKey(metric.key.substring(prefix.length())))shown.add(metric);
        final Map<String,Double> shares=share;
        shown.sort((a,b)->{int byShare=Double.compare(shares.get(b.key.substring(prefix.length())).doubleValue(),shares.get(a.key.substring(prefix.length())).doubleValue());if(byShare!=0)return byShare;
            int bySamples=Integer.compare(b.samples,a.samples);return bySamples!=0?bySamples:a.key.compareTo(b.key);});
        List<Double> values=new ArrayList<>(shown.size());
        for(Metric metric:shown)values.add(shares.get(metric.key.substring(prefix.length())));
        int[] tiers=standardTiers(values);
        for(int i=0;i<shown.size();i++){Metric metric=shown.get(i);out.add(new Entry(metric.key.substring(prefix.length()),act,metric,tiers[i],values.get(i).doubleValue()));}
        return out;
    }
    /** TinyCat's standardized breaks, as tier indices 0..6 for values already sorted strongest first; -1 input never occurs. */
    public static int[] standardTiers(List<Double> values){
        int n=values.size();int[] out=new int[n];Arrays.fill(out,5);
        List<Double> high=new ArrayList<>(),tail=new ArrayList<>();List<Integer> highAt=new ArrayList<>(),tailAt=new ArrayList<>();
        for(int i=0;i<n;i++){double value=values.get(i)==null?0:values.get(i).doubleValue();
            if(value>=1.0D){high.add(Double.valueOf(value));highAt.add(Integer.valueOf(i));}
            else {tail.add(Double.valueOf(Math.log10(Math.max(value,1.0e-9D))));tailAt.add(Integer.valueOf(i));}}
        if(!high.isEmpty()){int[] segment=segments(high,5);int count=Math.min(5,distinct(high));
            for(int i=0;i<high.size();i++)out[highAt.get(i).intValue()]=Math.max(0,Math.min(4,count-1-segment[i]));}
        if(!tail.isEmpty()){int[] segment=segments(tail,2);int count=Math.min(2,distinct(tail));
            for(int i=0;i<tail.size();i++)out[tailAt.get(i).intValue()]=count>1&&segment[i]==0?6:5;}
        return out;
    }
    public static String standardName(int tier){return tier>=0&&tier<STANDARD_NAMES.length?STANDARD_NAMES[tier]:"";}

    /** Fisher-Jenks optimal breaks on distinct values (equal values always share a tier); returns 0 for the lowest segment. */
    static int[] segments(List<Double> values,int k){
        int n=values.size();double[] asc=new double[n];
        for(int i=0;i<n;i++){Double value=values.get(i);asc[i]=value==null?0:value.doubleValue();}
        Arrays.sort(asc);
        int m=0;double[] unique=new double[n];int[] weight=new int[n];
        for(int i=0;i<n;i++){if(m>0&&Double.compare(unique[m-1],asc[i])==0)weight[m-1]++;else{unique[m]=asc[i];weight[m]=1;m++;}}
        int groups=Math.min(k,m);
        double[] sw=new double[m+1],s1=new double[m+1],s2=new double[m+1];
        for(int i=1;i<=m;i++){double v=unique[i-1];sw[i]=sw[i-1]+weight[i-1];s1[i]=s1[i-1]+v*weight[i-1];s2[i]=s2[i-1]+v*v*weight[i-1];}
        double infinity=Double.MAX_VALUE;
        double[][] best=new double[groups+1][m+1];int[][] back=new int[groups+1][m+1];
        for(int j=0;j<=groups;j++)for(int i=0;i<=m;i++)best[j][i]=infinity;
        best[0][0]=0;
        for(int j=1;j<=groups;j++)for(int i=j;i<=m;i++)for(int t=j-1;t<i;t++){
            if(best[j-1][t]>=infinity)continue;
            double cost=best[j-1][t]+sse(sw,s1,s2,t+1,i);
            if(cost<best[j][i]){best[j][i]=cost;back[j][i]=t;}
        }
        int[] start=new int[groups+1];int cursor=m;
        for(int j=groups;j>=1;j--){start[j]=back[j][cursor]+1;cursor=back[j][cursor];}
        int[] out=new int[n];
        for(int i=0;i<n;i++){
            Double value=values.get(i);double target=value==null?0:value.doubleValue();int pos=1;
            for(int t=1;t<=m;t++)if(Double.compare(unique[t-1],target)==0){pos=t;break;}
            int segment=1;for(int j=1;j<=groups;j++)if(start[j]<=pos)segment=j;
            out[i]=segment-1;
        }
        return out;
    }
    static double sse(double[] sw,double[] s1,double[] s2,int a,int b){
        double count=sw[b]-sw[a-1];if(count<=0)return 0;
        double sum=s1[b]-s1[a-1];return s2[b]-s2[a-1]-sum*sum/count;
    }
    static int distinct(List<Double> values){
        Set<Double> seen=new HashSet<>();
        for(Double value:values)seen.add(Double.valueOf(value==null?0:value.doubleValue()));
        return seen.size();
    }
    private PickTiers(){}
}
