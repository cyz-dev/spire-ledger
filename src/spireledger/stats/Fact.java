package spireledger.stats;

import java.util.*;

/** One value with where it came from and how much it can be trusted (metrics.md 1.2): missing is never zero. */
public final class Fact<T> {
    public enum Status {EXACT,ESTIMATED,ZERO,MISSING,NOT_APPLICABLE,CONFLICT}
    public static final String NATIVE="native",LEDGER="ledger",RHP="rhp";
    public final T value;public final Status status;public final String source;
    private Fact(T value,Status status,String source){this.value=value;this.status=status;this.source=source;}
    /** A present value; an empty collection is a known zero. */
    public static <T> Fact<T> of(T value,String source){return value==null?missing(source):new Fact<>(value,value instanceof Collection&&((Collection<?>)value).isEmpty()||value instanceof Map&&((Map<?,?>)value).isEmpty()?Status.ZERO:Status.EXACT,source);}
    /** A measured amount, where 0 is a known zero. */
    public static <T extends Number> Fact<T> amount(T value,String source){return value==null?missing(source):new Fact<>(value,value.doubleValue()==0?Status.ZERO:Status.EXACT,source);}
    public static <T> Fact<T> estimated(T value,String source){return value==null?missing(source):new Fact<>(value,Status.ESTIMATED,source);}
    public static <T> Fact<T> missing(String source){return new Fact<>(null,Status.MISSING,source);}
    public static <T> Fact<T> notApplicable(){return new Fact<>(null,Status.NOT_APPLICABLE,"");}
    public static <T> Fact<T> conflict(String source){return new Fact<>(null,Status.CONFLICT,source);}
    public boolean known(){return status==Status.EXACT||status==Status.ESTIMATED||status==Status.ZERO;}
    public T or(T fallback){return known()?value:fallback;}
    /** Priority merge: the lower source fills only a missing higher one; a conflict stays a conflict. */
    static <T> Fact<T> prefer(Fact<T> high,Fact<T> low){return high.status==Status.MISSING?low:high;}
    /** Same-rank facts (metrics.md 4.3): two known values that differ conflict, whichever was read first. */
    static <T> Fact<T> agree(Fact<T> high,Fact<T> low){return high.known()&&low.known()&&!Objects.equals(high.value,low.value)?conflict(high.source+"+"+low.source):prefer(high,low);}
    @Override public String toString(){return status+(known()?"("+value+")":"")+"@"+source;}
}
