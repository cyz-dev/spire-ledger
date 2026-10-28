package spireledger.stats;

import java.util.*;

/**
 * Standardized rate (metrics.md 6.7, standardized-v1): a Plackett-Luce point estimate over whole choice screens.
 *
 * <p>Each screen is a choice set S with P(pick i | S) = s_i / sum(S), so a pick against strong offers counts for
 * more than one against weak offers; the observed picked/offered rate cannot tell the two apart, which is why both
 * views exist and must not be subtracted. Strengths follow Hunter's MM update s_i = (W_i + a) / (sum over screens
 * holding i of 1 / sum(S) + b) with a = 1 imaginary win and b = 0.01 imaginary appearance, rescaled to geometric
 * mean 1 each round, until every relative change is under 1e-9 or after 2000 rounds. {@link #SKIP} stands for
 * passing the screen (Skip and Singing Bowl alike); it competes but is never reported. No bootstrap interval:
 * the stats screen refits on the render thread whenever a filter changes.</p>
 */
public final class Standardized {
    public static final String VERSION="standardized-v1",SKIP="SKIP";
    static final double PRIOR_WINS=1,PRIOR_APPEARANCES=.01,TOLERANCE=1e-9,FLOOR=1e-12;static final int ROUNDS=2000;

    /** One fitted universe: reported id to strength share in percent (sums to 100), with the screens and ids behind it. */
    public static final class Act {
        public final boolean available;public final Map<String,Double> share;public final int screens,cards;
        Act(boolean available,Map<String,Double> share,int screens,int cards){this.available=available;this.share=share;this.screens=screens;this.cards=cards;}
    }

    /** Card reward screens per act: confirmed screens only, known automation left out, Skip on every screen. */
    public static Map<Integer,Act> compute(List<Run> runs){Map<Integer,List<String[]>> byAct=new TreeMap<>();
        for(Run r:runs)if(r.screens.known())for(Run.Screen s:r.screens.value){if("known_automation".equals(s.actor))continue;
            boolean card=s.kind.equals("card");if(card&&!s.offered.contains(s.picked))continue;
            byAct.computeIfAbsent(s.act,k->new ArrayList<>()).add(screen(card?s.picked:SKIP,s.offered,true));}
        return fitEach(byAct);}

    /** One Neow screen per run with the whole option set; a run that kept only its pick carries no evidence. */
    public static Act computeNeow(List<Run> runs){List<String[]> screens=new ArrayList<>();
        for(Run r:runs){List<String> offers=r.neowOffers.or(null);String picked=r.neow.or(null);if(offers!=null&&picked!=null)screens.add(screen(picked,offers,false));}
        return fit(screens);}

    /** One boss relic screen per act and run; Skip is the screen's pass option. */
    public static Map<Integer,Act> computeBoss(List<Run> runs){Map<Integer,List<String[]>> byAct=new TreeMap<>();
        for(Run r:runs)if(r.boss.known())for(int i=0;i<r.boss.value.size();i++){Run.BossOffer b=r.boss.value.get(i);
            if(b!=null&&b.picked!=null&&!b.picked.isEmpty())byAct.computeIfAbsent(i+1,k->new ArrayList<>()).add(screen(b.picked,b.offered,true));}
        return fitEach(byAct);}

    /** The chosen id first, then every other distinct offer, then SKIP when the screen always had a pass option. */
    static String[] screen(String chosen,Collection<String> offered,boolean skip){LinkedHashSet<String> ids=new LinkedHashSet<>();ids.add(chosen);
        for(String id:offered)if(id!=null&&!id.isEmpty())ids.add(id);if(skip)ids.add(SKIP);return ids.toArray(new String[0]);}
    static Map<Integer,Act> fitEach(Map<Integer,List<String[]>> byAct){Map<Integer,Act> out=new TreeMap<>();for(Map.Entry<Integer,List<String[]>> e:byAct.entrySet())out.put(e.getKey(),fit(e.getValue()));return out;}

    /** Fit one universe. Each screen lists the chosen id first; screens with fewer than two distinct ids say nothing. Public for the offline checks. */
    public static Act fit(List<String[]> screens){
        Map<String,Integer> index=new LinkedHashMap<>();List<int[]> sets=new ArrayList<>();
        for(String[] screen:screens==null?Collections.<String[]>emptyList():screens){if(screen==null)continue;LinkedHashSet<Integer> set=new LinkedHashSet<>();
            for(String id:screen)if(id!=null&&!id.isEmpty()){Integer at=index.get(id);if(at==null)index.put(id,at=index.size());set.add(at);}
            if(set.size()>=2){int[] ids=new int[set.size()];int i=0;for(Integer id:set)ids[i++]=id;sets.add(ids);}}
        int n=index.size();if(n<2||sets.size()<2)return new Act(false,Collections.<String,Double>emptyMap(),sets.size(),n);
        double[] wins=new double[n],s=new double[n];boolean[] present=new boolean[n];
        for(int[] set:sets){wins[set[0]]++;for(int id:set)present[id]=true;}
        Arrays.fill(s,1);
        for(int round=0;round<ROUNDS;round++){double[] exposure=new double[n];
            for(int[] set:sets){double sum=0;for(int id:set)sum+=s[id];if(sum>0)for(int id:set)exposure[id]+=1/sum;}
            double[] next=new double[n];double logs=0;int active=0;
            for(int i=0;i<n;i++)if(present[i]){next[i]=Math.max((wins[i]+PRIOR_WINS)/(exposure[i]+PRIOR_APPEARANCES),FLOOR);logs+=Math.log(next[i]);active++;}
            double scale=Math.exp(logs/active),change=0;
            for(int i=0;i<n;i++)if(present[i]){double v=Math.max(next[i]/scale,FLOOR);change=Math.max(change,Math.abs(v/s[i]-1));s[i]=v;}
            if(change<TOLERANCE)break;}
        double total=0;for(Map.Entry<String,Integer> e:index.entrySet())if(reported(e,present))total+=s[e.getValue()];
        Map<String,Double> share=new LinkedHashMap<>();if(total>0)for(Map.Entry<String,Integer> e:index.entrySet())if(reported(e,present))share.put(e.getKey(),100*s[e.getValue()]/total);
        boolean available=share.size()>=2;return new Act(available,available?Collections.unmodifiableMap(share):Collections.<String,Double>emptyMap(),sets.size(),share.size());}
    static boolean reported(Map.Entry<String,Integer> e,boolean[] present){return present[e.getValue()]&&!e.getKey().equals(SKIP);}
    private Standardized(){}
}
