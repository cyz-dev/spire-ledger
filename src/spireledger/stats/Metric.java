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
    /** Sample variance of one value per row; NaN when fewer than two rows or when the rows were set directly. */
    public double variance(){if(samples<2||denominator<=0)return Double.NaN;double v=(squares-numerator*numerator/denominator)/(denominator-1);return v<0?0:v;}
    /** A number the views may show: not missing, not under the minimum sample, not hidden by a result filter. */
    public boolean shown(){return status.equals("ok")||status.equals("zero");}
}
