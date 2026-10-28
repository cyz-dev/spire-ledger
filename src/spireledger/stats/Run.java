package spireledger.stats;

import java.util.*;

/** One logical run (metrics.md 4): native .run and Ledger facts merged by play_id, each fact with its source and status. Derived numbers live in Metrics only. */
public final class Run implements Cloneable {
    /** One confirmed card reward screen; bowlOffered is null when the source cannot tell. */
    public static final class Screen {
        public final int act,floor;public final Set<String> offered;public final String kind,picked,actor,source;public final Boolean bowlOffered;
        Screen(int act,int floor,Set<String> offered,String kind,String picked,String actor,String source,Boolean bowlOffered){this.act=act;this.floor=floor;this.offered=offered;this.kind=kind;this.picked=picked;this.actor=actor;this.source=source;this.bowlOffered=bowlOffered;}
    }
    /** One encounter from damage_taken; damage is null when the entry has no number. */
    public static final class Combat {
        public final int floor,act;public final String encounter,room;public final Double damage;
        Combat(int floor,int act,String encounter,String room,Double damage){this.floor=floor;this.act=act;this.encounter=encounter;this.room=room;this.damage=damage;}
    }
    /** One boss relic screen; SKIP is a candidate like any relic (metrics.md 6.3). */
    public static final class BossOffer {
        public final Set<String> offered;public final String picked;
        public BossOffer(Set<String> offered,String picked){this.offered=offered;this.picked=picked;}
    }
    /** One rest site: the chosen action, every unupgraded card kind that was on the smith screen, and the competitor pool the multiple divides by. */
    public static final class Campfire {
        public final int act,floor,pool,available;public final String key,target;public final boolean rest,smith,recall,purge,lift,dig,smithUsable,forcedRecall;
        /** Card kinds with an unupgraded copy when the site opened; empty when Fusion Hammer or no upgradable card disabled smith. */
        public final List<String> candidates;
        public Campfire(int act,int floor,String key,String target,List<String> candidates,int pool,boolean rest,boolean smith,boolean recall,boolean purge,boolean lift,boolean dig,boolean smithUsable,boolean forcedRecall){
            this.act=act;this.floor=floor;this.key=key;this.target=target;this.candidates=candidates;this.pool=pool;this.rest=rest;this.smith=smith;this.recall=recall;this.purge=purge;this.lift=lift;this.dig=dig;
            this.smithUsable=smithUsable;this.forcedRecall=forcedRecall;
            available=(rest?1:0)+(smith?1:0)+(recall?1:0)+(purge?1:0)+(lift?1:0)+(dig?1:0);}
        public boolean had(String option){switch(option){case "REST":return rest;case "SMITH":return smith;case "RECALL":return recall;case "PURGE":return purge;case "LIFT":return lift;case "DIG":return dig;default:return false;}}
    }

    public String id="",playId="",runId="",conflictReason="",nativeFile="";public boolean fixture,practice,current,conflict,sl,unresolvedPath;
    public final Set<String> sources=new TreeSet<>();
    public Fact<String> character=Fact.missing(""),seed=Fact.missing(""),outcome=Fact.missing(""),killedBy=Fact.missing(""),neow=Fact.missing(""),actor=Fact.missing("");
    public Fact<Integer> ascension=Fact.missing(""),slot=Fact.missing(""),floorReached=Fact.missing(""),maxAct=Fact.missing(""),neowHp=Fact.missing(""),deckSize=Fact.missing("");
    public Fact<Long> time=Fact.missing("");public Fact<Double> playtime=Fact.missing(""),gold=Fact.missing("");public Fact<Integer> score=Fact.missing("");
    /** Cards removed per source (shop, event, neow, campfire, empty_cage) plus their total; TinyCat's purge families. */
    public Fact<Map<String,Integer>> purges=Fact.missing("");
    public Fact<Boolean> act3=Fact.missing(""),heart=Fact.missing(""),daily=Fact.missing(""),endless=Fact.missing(""),trial=Fact.missing(""),seeded=Fact.missing("");
    public Fact<Set<String>> deck=Fact.missing("");
    public Fact<List<Combat>> combats=Fact.missing("");public Fact<List<Integer>> hpPerFloor=Fact.missing("");public Fact<Map<Integer,Integer>> floorEnter=Fact.missing(""),potionsByFloor=Fact.missing("");
    /** Positive event damage summed per act. */
    public Fact<Map<Integer,Double>> eventDamage=Fact.missing("");
    public Fact<List<Screen>> screens=Fact.missing("");final Set<Integer> ledgerRewardFloors=new HashSet<>();
    public Fact<List<BossOffer>> boss=Fact.missing("");
    /** The whole Neow choice set (picked included); absent when the source kept only the picked option. */
    public Fact<List<String>> neowOffers=Fact.missing("");
    public Fact<List<Campfire>> campfires=Fact.missing("");
    /** Count families (events, purchases, purges, campfire, potions, observed): metric key to occurrences. */
    public final Map<String,Fact<Map<String,Integer>>> counts=new TreeMap<>();

    public boolean has(String source){return sources.contains(source);}
    /** A Ledger record is read once and reused by every index build; a build marks flags on its own copy. Facts are immutable, so a shallow copy is enough. */
    Run copy(){try{return (Run)super.clone();}catch(CloneNotSupportedException e){throw new IllegalStateException(e);}}

    /** Entry HP (metrics.md 5.3): floor_enter, then Neow's settled HP for floor 1, then the previous floor's end HP; floor 1 falls back to the TinyCat estimate. */
    Fact<Integer> enterHp(Combat c){
        if(floorEnter.known()&&floorEnter.value.containsKey(c.floor))return Fact.of(floorEnter.value.get(c.floor),Fact.LEDGER);
        if(c.floor==1&&neowHp.known())return neowHp;
        List<Integer> hp=hpPerFloor.or(null);if(hp==null)return Fact.missing(Fact.NATIVE);
        if(c.floor>1)return c.floor-2<hp.size()&&hp.get(c.floor-2)!=null?Fact.of(hp.get(c.floor-2),Fact.NATIVE):Fact.missing(Fact.NATIVE);
        // Burning Blood heals 6 after the first fight, so its end HP overstates the entry by that much.
        return c.floor==1&&!hp.isEmpty()&&hp.get(0)!=null&&c.damage!=null?Fact.estimated((int)(hp.get(0)+c.damage)-("IRONCLAD".equals(character.value)?6:0),Fact.NATIVE):Fact.missing(Fact.NATIVE);
    }
    Fact<Integer> endHp(Combat c){List<Integer> hp=hpPerFloor.or(null);return hp!=null&&c.floor>=1&&c.floor-1<hp.size()&&hp.get(c.floor-1)!=null?Fact.of(hp.get(c.floor-1),Fact.NATIVE):Fact.missing(Fact.NATIVE);}
    /** Actual HP lost in one fight; negative when it healed. */
    Fact<Double> hpLoss(Combat c){Fact<Integer> in=enterHp(c),out=endHp(c);if(!in.known()||!out.known())return Fact.missing(in.known()?out.source:in.source);double v=in.value-out.value;return in.status==Fact.Status.ESTIMATED?Fact.estimated(v,in.source):Fact.amount(v,in.source);}

    /** Field-level merge (metrics.md 1.6, 4): native first for results and identity, Ledger first for in-run facts; same-rank disagreement is a conflict. */
    static Run merge(Run n,Run l){
        Run r=new Run();r.id=n.id;r.playId=n.playId;r.runId=l.runId;r.nativeFile=n.nativeFile;r.sources.addAll(n.sources);r.sources.addAll(l.sources);
        r.conflict=n.conflict;r.conflictReason=n.conflictReason;r.fixture=n.fixture||l.fixture;r.practice=n.practice||l.practice;r.current=l.current;r.sl=l.sl;r.unresolvedPath=l.unresolvedPath;
        r.character=Fact.agree(n.character,l.character);r.ascension=Fact.agree(n.ascension,l.ascension);r.seed=Fact.agree(n.seed,l.seed);
        for(Fact<?> f:Arrays.asList(r.character,r.ascension,r.seed))if(f.status==Fact.Status.CONFLICT){r.conflict=true;r.conflictReason="identity";}
        r.time=Fact.prefer(n.time,l.time);r.playtime=Fact.prefer(n.playtime,l.playtime);r.outcome=Fact.prefer(n.outcome,l.outcome);r.killedBy=Fact.prefer(n.killedBy,l.killedBy);
        r.score=Fact.prefer(n.score,l.score);r.purges=n.purges;
        r.act3=Fact.agree(l.act3,n.act3);r.heart=Fact.prefer(n.heart,l.heart);r.neow=Fact.agree(n.neow,l.neow);r.gold=Fact.prefer(l.gold,n.gold);
        r.deck=Fact.prefer(n.deck,l.deck);r.deckSize=Fact.prefer(n.deckSize,l.deckSize);r.maxAct=Fact.prefer(l.maxAct,n.maxAct);r.floorReached=Fact.prefer(n.floorReached,l.floorReached);
        r.daily=Fact.prefer(n.daily,l.daily);r.endless=Fact.prefer(n.endless,l.endless);r.trial=Fact.prefer(n.trial,l.trial);r.seeded=Fact.prefer(n.seeded,l.seeded);r.slot=Fact.prefer(l.slot,n.slot);
        r.combats=n.combats;r.eventDamage=n.eventDamage;r.hpPerFloor=n.hpPerFloor;r.potionsByFloor=n.potionsByFloor;r.boss=n.boss;r.neowOffers=Fact.prefer(n.neowOffers,l.neowOffers);r.campfires=Fact.prefer(n.campfires,l.campfires);r.floorEnter=l.floorEnter;r.neowHp=l.neowHp;r.actor=Fact.prefer(l.actor,n.actor);
        // proposed (metrics.md 6.2): a floor the Ledger recorded is taken from the Ledger only; .run fills the floors it lacks.
        if(l.screens.known()&&n.screens.known()){List<Screen> all=new ArrayList<>(l.screens.value);for(Screen s:n.screens.value)if(!l.ledgerRewardFloors.contains(s.floor))all.add(s);r.screens=Fact.of(all,Fact.NATIVE+"+"+Fact.LEDGER);}
        else r.screens=Fact.prefer(l.screens,n.screens);
        r.counts.putAll(n.counts);r.counts.putAll(l.counts);return r;
    }
}
