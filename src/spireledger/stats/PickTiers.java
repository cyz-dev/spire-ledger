package spireledger.stats;

import java.util.*;

/** Presentation of observed pick rates and of standardized strength shares. Neither is an estimate of card strength on its own. */
public final class PickTiers {
    public static final String VERSION="pick-tiers-v1";
    /** Neow/Boss/campfire export sections: Jenks breaks over the values in one section, S..R00. */
    public static final String SECTION_VERSION="section-tiers-v1";
    // Percentage boundaries also used by TinyCat's observed pick-rate table; no model fitting.
    public static final int[] LOWER={90,75,60,45,30,15,0};
    /** Standardized view (metrics.md 6.7): Jenks natural breaks put shares of at least 1% into S..D, and the <1% tail into R0/R00 on log10. */
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
    /** A row outside the fitted universe (Skip, Bowl): its tier still comes from the observed rate. */
    public static Entry observed(String card,int act,Metric metric){return new Entry(card,act,metric);}
    /** Skip/Bowl inside the standardized view: after the S..R00 groups, showing the actual observed rate. */
    public static Entry actual(String card,int act,Metric metric){return new Entry(card,act,metric,9,-1);}
    /** Fisher-Jenks tiers (0 = highest) over the values shown in one export section; equal values always share a tier. */
    public static int[] naturalTiers(List<Double> values,int k){int n=values.size();int[] out=new int[n];if(n==0)return out;
        double[] v=new double[n];List<Integer> all=new ArrayList<>();for(int i=0;i<n;i++){double x=values.get(i)==null?0:values.get(i);v[i]=Double.isFinite(x)?x:0;all.add(i);}
        Map<Double,Integer> classes=jenks(v,all,Math.max(1,k));for(int i=0;i<n;i++)out[i]=classes.get(v[i]);return out;}
    /** The same tier/rate/sample order as {@link #entries}; used after appending rows the fit never reports. */
    public static void sort(List<Entry> entries){entries.sort(Comparator.comparingInt((Entry e)->e.tier).thenComparingDouble(e->-e.rate()).thenComparingInt(e->-e.samples).thenComparing(e->e.card));}
    /** Cards plus Skip that the standardized fit reports for this act, strongest share first; low samples stay out, as in the observed tiers. */
    public static List<Entry> standardized(Map<String,Metric> metrics,int act,Map<String,Double> share){
        List<Entry> out=new ArrayList<>();if(share==null)return out;
        String prefix="pick:"+act+":";List<Metric> shown=new ArrayList<>();List<String> ids=new ArrayList<>();
        for(Metric metric:metrics.values())if(metric.key.startsWith(prefix)&&metric.shown()&&share.containsKey(metric.key.substring(prefix.length()))){shown.add(metric);ids.add(metric.key.substring(prefix.length()));}
        Metric skip=metrics.get("skip:"+act);
        if(skip!=null&&skip.shown()&&share.containsKey(Standardized.SKIP)){shown.add(skip);ids.add(Standardized.SKIP);}
        final Map<String,Double> shares=share;
        List<Integer> order=new ArrayList<>();for(int i=0;i<shown.size();i++)order.add(i);
        order.sort((a,b)->{int byShare=Double.compare(shares.get(ids.get(b)).doubleValue(),shares.get(ids.get(a)).doubleValue());if(byShare!=0)return byShare;
            int bySamples=Integer.compare(shown.get(b).samples,shown.get(a).samples);return bySamples!=0?bySamples:ids.get(a).compareTo(ids.get(b));});
        List<Double> values=new ArrayList<>(order.size());for(int i:order)values.add(shares.get(ids.get(i)));
        int[] tiers=standardTiers(values);
        for(int i=0;i<order.size();i++){int at=order.get(i);out.add(new Entry(ids.get(at),act,shown.get(at),tiers[i],values.get(i).doubleValue()));}
        return out;
    }
    /** Standardized tier per share, in any input order: 0..4 (S..D) for shares of at least 1%, 5..6 (R0, R00) for the tail. */
    public static int[] standardTiers(List<Double> values){int n=values.size();int[] out=new int[n];double[] v=new double[n];List<Integer> high=new ArrayList<>(),tail=new ArrayList<>();
        for(int i=0;i<n;i++){double x=values.get(i)==null?0:values.get(i);if(x>=1){v[i]=x;high.add(i);}else{v[i]=Math.log10(Math.max(x,1e-9));tail.add(i);}}
        Map<Double,Integer> top=jenks(v,high,5),low=jenks(v,tail,2);
        for(int i:high)out[i]=top.get(v[i]);for(int i:tail)out[i]=5+low.get(v[i]);return out;}
    public static String standardName(int tier){return tier>=0&&tier<STANDARD_NAMES.length?STANDARD_NAMES[tier]:"";}
    /**
     * Fisher-Jenks natural breaks into at most k classes, minimizing the summed within-class squared deviation.
     * Classes are cut between distinct values, each weighted by its copies, so equal values always share a class.
     * Returns value to class rank, 0 for the highest class.
     */
    static Map<Double,Integer> jenks(double[] v,List<Integer> at,int k){
        TreeMap<Double,Integer> copies=new TreeMap<>();for(int i:at)copies.merge(v[i],1,Integer::sum);
        int m=copies.size(),groups=Math.min(k,m),c=0;double[] u=new double[m];int[] w=new int[m];for(Map.Entry<Double,Integer> e:copies.entrySet()){u[c]=e.getKey();w[c++]=e.getValue();}
        // Prefix sums of weight, weighted value and weighted square give any run's squared deviation in O(1).
        double[] sw=new double[m+1],sx=new double[m+1],sxx=new double[m+1];for(int i=0;i<m;i++){sw[i+1]=sw[i]+w[i];sx[i+1]=sx[i]+w[i]*u[i];sxx[i+1]=sxx[i]+w[i]*u[i]*u[i];}
        double[][] best=new double[groups+1][m+1];int[][] cut=new int[groups+1][m+1];for(double[] row:best)Arrays.fill(row,Double.POSITIVE_INFINITY);best[0][0]=0;
        for(int g=1;g<=groups;g++)for(int i=g;i<=m;i++)for(int t=g-1;t<i;t++){double sum=sx[i]-sx[t],cost=best[g-1][t]+sxx[i]-sxx[t]-sum*sum/(sw[i]-sw[t]);if(cost<best[g][i]){best[g][i]=cost;cut[g][i]=t;}}
        Map<Double,Integer> out=new HashMap<>();for(int g=groups,end=m;g>=1;end=cut[g][end],g--)for(int i=cut[g][end];i<end;i++)out.put(u[i],groups-g);
        return out;}
    private PickTiers(){}
}
