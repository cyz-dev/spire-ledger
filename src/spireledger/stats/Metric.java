package spireledger.stats;

/** One metric row with its explicit numerator, denominator, sample unit and coverage (metrics.md 1, 8). */
public final class Metric {
    public static final String COUNT="count",RATE="rate",MEAN="mean";
    /** kind: count, rate or mean; unit: run, run_act, combat, choice, choice_set or event; measure: what a mean is in (hp, gold, seconds, cards, potions). */
    public final String key,kind,unit,measure,cover;public final boolean winRate;
    public double numerator,denominator,coverNumerator,coverDenominator;public int samples,excluded,estimated;public String coverUnit="",status="ok";
    Metric(String key,String kind,String unit,String measure,String cover,boolean winRate){this.key=key;this.kind=kind;this.unit=unit;this.measure=measure;this.cover=cover;this.winRate=winRate;}
    void add(double n,double d){numerator+=n;denominator+=d;samples++;}
    void count(double n){numerator+=n;samples++;}
    public double value(){return kind.equals(COUNT)?numerator:denominator==0?0:numerator/denominator;}
    /** A number the views may show: not missing, not under the minimum sample, not hidden by a result filter. */
    public boolean shown(){return status.equals("ok")||status.equals("zero");}
}
