package spireledger.stats;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline metrics export for the TinyCat parity check (tools/tinycat-compare/README.md 2): native .run only, TinyCat's sample scope, one row per metric, scope and dims. Needs Gson and the JDK only. */
public final class Export {
    static final List<String> SCOPES=Arrays.asList("ALL","IRONCLAD","THE_SILENT","DEFECT","WATCHER");
    static final List<String> PURGES=Arrays.asList("SHOP","EVENT","NEOW","CAMPFIRE","EMPTY_CAGE");
    static final Map<String,String> PURGE_FIELD=new HashMap<>();
    static{PURGE_FIELD.put("SHOP","items_purged");PURGE_FIELD.put("EVENT","event_choices");PURGE_FIELD.put("NEOW","neow_bonus_log");PURGE_FIELD.put("CAMPFIRE","campfire_choices");PURGE_FIELD.put("EMPTY_CAGE","relics");}

    /** Export &lt;runs dir&gt; &lt;out.json&gt; &lt;save slot&gt; [run list]: the run list is a baseline or export (its meta.run_ids.ALL) or a JSON array of dir/file.run, and replaces the scope filters. */
    public static void main(String[] a)throws IOException{
        if(a.length<3||a.length>4){System.err.println("usage: Export <runs dir> <out.json> <save slot> [run-list.json]");System.exit(2);}
        Set<String> only=a.length==4?runList(Paths.get(a[3])):null;
        JsonObject doc=export(Stats.scan(Paths.get(a[0]),Collections.<Ledger>emptyList(),Collections.<String>emptyList()),Integer.parseInt(a[2]),only,version());
        Path out=Paths.get(a[1]).toAbsolutePath(),tmp=out.resolveSibling(out.getFileName()+".tmp");Files.createDirectories(out.getParent());
        Files.write(tmp,new GsonBuilder().serializeNulls().create().toJson(doc).getBytes(StandardCharsets.UTF_8));Files.move(tmp,out,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("exported "+doc.getAsJsonArray("rows").size()+" rows, "+doc.getAsJsonObject("meta").getAsJsonObject("run_ids").getAsJsonArray("ALL").size()+" runs to "+out);
    }
    static Set<String> runList(Path file)throws IOException{JsonElement e;try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){e=new JsonParser().parse(r);}
        JsonArray ids=e.isJsonArray()?e.getAsJsonArray():Json.arr(Json.obj(Json.obj(e.getAsJsonObject(),"meta"),"run_ids"),"ALL");return new TreeSet<>(Json.strings(ids));}
    static String version(){try(InputStream in=Export.class.getResourceAsStream("/ModTheSpire.json")){if(in!=null){String v=Json.str(new JsonParser().parse(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject(),"version");if(!v.isEmpty())return "Spire Ledger "+v;}}catch(Exception e){}return "Spire Ledger unknown";}

    /** The TinyCat alignment scope (README 2): A20, no daily, custom or endless, the given save slot, abandons in and lost, heart-kill wins, native .run only. */
    static Stats.Filter filter(int slot){Stats.Filter f=new Stats.Filter();f.ascension=20;f.slot=slot;f.includeAbandon=true;f.heart=true;f.source=Fact.NATIVE;f.seed="all";return f;}

    public static JsonObject export(Stats.Index index,int slot,Set<String> only,String source){
        Stats.Filter f=filter(slot);List<Run> runs=new ArrayList<>();Map<String,Integer> excluded=new TreeMap<>();
        if(only==null){Stats.Selection s=Stats.select(index,f,0);runs.addAll(s.runs);excluded.putAll(s.excluded);}else for(Run r:index.runs)if(only.contains(r.nativeFile))runs.add(r);
        runs.sort(Comparator.comparing(r->r.nativeFile));
        JsonObject meta=new JsonObject();meta.addProperty("format","spire-ledger-metrics");meta.addProperty("format_version",1);meta.addProperty("metrics_version","metrics-v1");meta.addProperty("source",source);
        JsonObject filters=new JsonParser().parse("{\"tinycat_alignment\":true,\"save_slot\":0,\"ascension\":[20],\"modes\":\"exclude_daily_special_endless\",\"include_abandons\":true,\"seed_filter\":\"all\",\"win_definition\":\"heart_kill\",\"sources\":[\"native\"]}").getAsJsonObject();
        filters.addProperty("save_slot",slot);meta.add("filters",filters);meta.addProperty("runs_scanned",index.nativeFiles);meta.addProperty("run_list",only!=null);
        JsonObject why=new JsonObject();for(Map.Entry<String,Integer> e:excluded.entrySet())why.addProperty(e.getKey(),e.getValue());meta.add("excluded_by_reason",why);
        JsonObject ids=new JsonObject();JsonArray rows=new JsonArray();
        for(String scope:SCOPES){List<Run> sub=new ArrayList<>();for(Run r:runs)if(scope.equals("ALL")||scope.equals(r.character.or(null)))sub.add(r);
            JsonArray list=new JsonArray();for(Run r:sub)list.add(new JsonPrimitive(r.nativeFile));ids.add(scope,list);new Rows(scope,sub,f,rows).all();}
        meta.add("run_ids",ids);JsonObject doc=new JsonObject();doc.add("meta",meta);doc.add("rows",rows);return doc;
    }

    /** Rows of one scope: kernel metrics renamed to the export's dims, plus the act splits the screen does not show. */
    static final class Rows {
        final String scope;final List<Run> runs;final Stats.Filter f;final JsonArray out;
        Rows(String scope,List<Run> runs,Stats.Filter f,JsonArray out){this.scope=scope;this.runs=runs;this.f=f;this.out=out;}
        static Map<String,String> dims(String... kv){Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<kv.length;i+=2)m.put(kv[i],kv[i+1]);return m;}
        JsonObject row(String metric,Map<String,String> dims,Double value,Double numerator,Double denominator,int samples,String status){
            JsonObject r=new JsonObject();r.addProperty("metric",metric);r.addProperty("scope",scope);JsonObject d=new JsonObject();for(Map.Entry<String,String> e:dims.entrySet())d.addProperty(e.getKey(),e.getValue());r.add("dims",d);
            r.addProperty("value",value);r.addProperty("numerator",numerator);r.addProperty("denominator",denominator);r.addProperty("sample_size",samples);r.addProperty("status",status);out.add(r);return r;}
        /** A rate or mean; nothing to divide by is missing, never 0. */
        JsonObject ratio(String metric,Map<String,String> dims,double numerator,double denominator,int samples,String status){boolean none=denominator==0;
            return row(metric,dims,none?null:numerator/denominator,none?null:numerator,denominator,samples,none?"missing":status!=null?status:numerator==0?"zero":"ok");}
        JsonObject from(String metric,Map<String,String> dims,Metric m){return ratio(metric,dims,m.numerator,m.denominator,m.samples,m.denominator==0?null:m.status);}
        /** A count over the runs that carry its field; no such run is missing. */
        void count(String metric,Map<String,String> dims,Integer have,double n){if(have==null)row(metric,dims,null,null,null,0,"missing");else row(metric,dims,n,n,null,have,n==0?"zero":"ok");}

        void all(){Map<String,Metric> m=Metrics.compute(selection(),f);kernel(m);splits(m);combats();counts();}
        Stats.Selection selection(){Stats.Selection s=new Stats.Selection();s.runs.addAll(runs);return s;}

        void kernel(Map<String,Metric> all){
            for(Metric m:all.values()){String k=m.key;String[] p=k.split(":",3);
                if(k.equals("win"))from("win",dims(),m);
                else if(k.equals("time"))from("time",dims("result","all"),m);
                else if(k.equals("deck_size"))from("deck_size",dims(),m);
                else if(k.equals("gold_gained")){JsonObject r=from("gold_gained",dims(),m);r.addProperty("estimated",m.estimated);r.addProperty("exact",m.samples-m.estimated);}
                else if(p[0].equals("pick")&&p.length==3)from("pick",dims("act",p[1],"card",p[2]),m);
                else if(p[0].equals("skip")||p[0].equals("bowl"))from(p[0],dims("act",p[1]),m);
                else if(p[0].equals("holding"))from("holding",dims("card",k.substring(8)),m);
                else if(k.startsWith("related:neow:"))from("neow_related",dims("neow",k.substring(13)),m);
                else if(p[0].equals("boss")&&p.length==3)from("boss_pick",dims("act",p[1],"relic",p[2]),m);
                else if(k.startsWith("related:boss:")){String[] q=k.substring(13).split(":",2);from("boss_related",dims("act",q[0],"relic",q[1]),m);}
                else if((p[0].equals("hp_loss")||p[0].equals("hp_loss_heal")||p[0].equals("hp_loss_noheal"))&&p.length==3)from(p[0],dims("act",p[1],"encounter",p[2]),m);
                else if(p[0].equals("act_hp_loss"))from("act_hp_loss",dims("act",p[1]),m);
                else if(p[0].equals("act")&&p.length==2)from("act_survival",dims("act",p[1]),m);}
        }
        /** Winning time; the bowl rate per act stays missing where .run cannot tell whether the bowl was offered (metrics.md 6.2). */
        void splits(Map<String,Metric> all){double sum=0;int n=0;for(Run r:runs)if(Boolean.TRUE.equals(Stats.win(r,true))&&r.playtime.known()){sum+=r.playtime.value;n++;}ratio("time",dims("result","win"),sum,n,n,null);
            for(int act=1;act<=4;act++)if(all.containsKey("reward:"+act)&&!all.containsKey("bowl:"+act))row("bowl",dims("act",""+act),null,null,null,0,"missing");}

        /** Damage, deaths and potions per act and encounter, with the kernel's per-encounter conditions. */
        void combats(){Map<String,double[]> agg=new TreeMap<>();
            for(Run r:runs){if(!r.combats.known())continue;boolean death="death".equals(r.outcome.or(null));
                for(Run.Combat c:r.combats.value){String at=c.act+"\u0000"+c.encounter;
                    if(c.damage!=null)add(agg,"damage\u0000"+at,c.damage);
                    if(!death||r.killedBy.known())add(agg,"death\u0000"+at,Metrics.died(r,c)?1:0);
                    if(r.potionsByFloor.known())add(agg,"encounter_potions\u0000"+at,r.potionsByFloor.value.getOrDefault(c.floor,0));}}
            for(Map.Entry<String,double[]> e:agg.entrySet()){String[] k=e.getKey().split("\u0000");ratio(k[0],dims("act",k[1],"encounter",k[2]),e.getValue()[0],e.getValue()[1],(int)e.getValue()[1],null);}
        }
        static void add(Map<String,double[]> agg,String key,double v){double[] a=agg.computeIfAbsent(key,k->new double[2]);a[0]+=v;a[1]++;}

        /** Event win rate per act and event name, once per run (metrics.md 6.5), and the act-split counts; act "0" sums all four acts. */
        void counts(){Map<String,Integer> have=new HashMap<>();Map<String,double[]> events=new TreeMap<>();Map<String,Double> sums=new TreeMap<>();
            for(Run r:runs){Fact<Map<String,Integer>> fam=r.counts.get(Native.ACTS);if(fam==null||!fam.known())continue;Boolean w=Stats.win(r,true);Set<String> seen=new HashSet<>();
                for(Map.Entry<String,Integer> e:fam.value.entrySet()){String[] k=e.getKey().split("\\|",2);if(k[0].equals("has")){have.merge(k[1],1,Integer::sum);continue;}
                    if(k[1].startsWith("event:")){if(!k[0].equals("0")&&!k[0].equals("4")&&w!=null&&seen.add(k[0]+"\u0000"+k[1].substring(6))){double[] a=events.computeIfAbsent(k[0]+"\u0000"+k[1].substring(6),x->new double[2]);a[0]+=w?1:0;a[1]++;}continue;}
                    sums.merge(e.getKey(),(double)e.getValue(),Double::sum);if(!k[0].equals("0"))sums.merge("0|"+k[1],(double)e.getValue(),Double::sum);}}
            for(Map.Entry<String,double[]> e:events.entrySet()){String[] k=e.getKey().split("\u0000",2);ratio("event_related",dims("act",k[0],"event",k[1]),e.getValue()[0],e.getValue()[1],(int)e.getValue()[1],null);}
            // Purges by source over all acts, act 0 included; ALL adds the known sources and names the ones some run lacks.
            Map<String,Double> bySource=new LinkedHashMap<>();for(String s:PURGES)bySource.put(s,sums.getOrDefault("0|purge:"+s,0.0));
            double total=0;JsonArray partial=new JsonArray();for(String s:PURGES){Integer h=have.get(PURGE_FIELD.get(s));count("purges",dims("source",s),h,bySource.get(s));if(h!=null)total+=bySource.get(s);if(h==null||h<runs.size())partial.add(new JsonPrimitive(s));
                if(s.equals("EMPTY_CAGE")&&h!=null)out.get(out.size()-1).getAsJsonObject().addProperty("estimated",(int)(bySource.get(s)/2));}
            count("purges",dims("source","ALL"),runs.isEmpty()?null:runs.size(),total);out.get(out.size()-1).getAsJsonObject().add("partial_sources",partial);
            for(Map.Entry<String,Double> e:sums.entrySet()){String[] k=e.getKey().split("\\|",2);String act=k[0],key=k[1];double v=e.getValue();
                if(key.equals("purge:SHOP")&&!act.equals("0"))count("purges",dims("source","SHOP","act",act),have.get("items_purged"),v);
                else if(key.startsWith("campfire:"))count("campfire",dims("act",act,"action",key.substring(9)),have.get("campfire_choices"),v);
                else if(key.startsWith("upgrade:campfire:"))count("upgrade",dims("source","campfire","act",act,"card",key.substring(17)),have.get("campfire_choices"),v);
                else if(key.startsWith("purchase:"))count("purchase",dims("act",act,"item",key.substring(9)),have.get("items_purchased"),v);
                else if(key.equals("shop_visit"))count("shop_visits",dims("act",act),have.get("path_per_floor"),v);}
            for(int act=0;act<=4;act++){double n=0;for(Map.Entry<String,Double> e:sums.entrySet())if(e.getKey().startsWith(act+"|campfire:"))n+=e.getValue();if(act==0||n>0)count("campfire",dims("act",""+act,"action","TOTAL"),have.get("campfire_choices"),n);}
        }
    }
}
