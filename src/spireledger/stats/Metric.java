package spireledger.stats;

/** One metric row with its explicit numerator, denominator, sample unit and coverage (metrics.md 1, 8). */
public final class Metric {
    public static final String COUNT="count",RATE="rate",MEAN="mean";
    /** kind: count, rate or mean; unit: run, run_act, combat, choice, choice_set or event; measure: what a mean is in (hp, gold, seconds, cards, potions). */
    public final String key,kind,unit,measure,cover;public final boolean winRate;
    public double numerator,denominator,coverNumerator,coverDenominator;public int samples,excluded,estimated;public String coverUnit="",status="ok";
    /** Sum of squared per-sample values, kept for the mean's 95% interval; rates never read it. */
    public double squares;
    /** Campfire preference rows: the random baseline in percent (100 / average pool) their share is divided by. */
    public double baseline;
    Metric(String key,String kind,String unit,String measure,String cover,boolean winRate){this.key=key;this.kind=kind;this.unit=unit;this.measure=measure;this.cover=cover;this.winRate=winRate;}
    void add(double n,double d){numerator+=n;denominator+=d;samples++;squares+=n*n;}
    void count(double n){numerator+=n;samples++;}
    public double value(){return kind.equals(COUNT)?numerator:denominator==0?0:numerator/denominator;}
    /** Sample variance of one value per row; NaN under two rows, including rows set directly with denominator 1. */
    public double variance(){if(samples<2||denominator<2)return Double.NaN;double v=(squares-numerator*numerator/denominator)/(denominator-1);return v<0?0:v;}
    /** Two-sided 95% interval {low, high} (metrics.md 6.1): Wilson for a rate, mean ± 1.96 SE for a mean; null when it cannot be formed. */
    public double[] interval(){double n=denominator,z=1.96;
        if(kind.equals(RATE)){if(n<=0)return null;double p=numerator/n,z2=z*z,center=(p+z2/(2*n))/(1+z2/n),half=z*Math.sqrt(p*(1-p)/n+z2/(4*n*n))/(1+z2/n);return new double[]{Math.max(0,center-half),Math.min(1,center+half)};}
        double v=kind.equals(MEAN)?variance():Double.NaN;if(Double.isNaN(v))return null;double half=z*Math.sqrt(v/n);return new double[]{Math.max(0,value()-half),value()+half};}
    /** A number the views may show: not missing, not under the minimum sample, not hidden by a result filter. */
    public boolean shown(){return status.equals("ok")||status.equals("zero");}
}
