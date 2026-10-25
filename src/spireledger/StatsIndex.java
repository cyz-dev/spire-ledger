package spireledger;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import spireledger.stats.*;
/** Hands the record index's runs to the statistics kernel, which cannot see HistoryIndex (its summaries and recovery live beside game code). */
final class StatsIndex {
 static Stats.Index scan(Path nativeRoot,HistoryIndex.Index notes,JsonObject live)throws IOException{List<Ledger> ledgers=new ArrayList<>();List<String> errors=new ArrayList<>();
  for(HistoryIndex.Summary s:notes.runs)try{ledgers.add(new Ledger(s.current&&live!=null?live:HistoryIndex.facts(s),s.current,s.conflict));}catch(Exception e){errors.add(s.id+": "+e.getClass().getSimpleName());}
  return Stats.scan(nativeRoot,ledgers,errors);}
}
