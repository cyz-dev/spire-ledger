package spireledger;

import com.evacipated.cardcrawl.modthespire.*;
import com.google.gson.*;
import java.util.*;

/** One version contract for recording and reading turn attachments; "*" is any version, "x.y.z+" a numeric minimum. */
final class ReplayEnvironment {
    static final Map<String,String> OPTIONAL;
    static {
        Map<String,String> mods=new LinkedHashMap<>();
        mods.put("superfastmode","1.0.8");
        // The solver is ours: any release from 0.9.6 on, as long as it keeps the members SolverReplay drives (checked when loaded).
        mods.put("sts1solver","0.9.6+");
        mods.put("coloredmap","2.4.1");
        mods.put("coloredpowertips","2.3.0");
        mods.put("HighlightPath","0.0.3");
        mods.put("RunHistoryPlus","1.8.3");
        mods.put("HideInfo","0.0.1");
        mods.put("justclick","0.1.1");
        mods.put("lolifontmod","0.2");
        OPTIONAL=Collections.unmodifiableMap(mods);
    }
    static boolean accepts(JsonArray mods) {
        if(mods==null)return false;
        Map<String,String> remaining=new HashMap<>(OPTIONAL);
        remaining.put("basemod","5.56.0");remaining.put("stslib","2.12.0");remaining.put("spire-ledger","*");
        for(JsonElement item:mods){
            if(!item.isJsonObject())return false;
            JsonObject mod=item.getAsJsonObject();String id=Recorder.string(mod,"id");
            if(Boolean.getBoolean("spireledger.fixture")&&"spire-ledger-replay-probe".equals(id))continue;
            String version=remaining.remove(id);
            if(version==null||!matches(version,Recorder.string(mod,"version")))return false;
        }
        for(String id:OPTIONAL.keySet())remaining.remove(id);
        return remaining.isEmpty();
    }
    static boolean matches(String expected,String actual){
        if(expected.equals("*")||expected.equals(actual))return true;
        // Numeric releases only: a "-test" or other suffixed build is never assumed newer.
        if(!expected.endsWith("+")||actual==null||!actual.matches("\\d{1,9}(\\.\\d{1,9})*"))return false;
        String[] min=expected.substring(0,expected.length()-1).split("\\."),have=actual.split("\\.");
        for(int i=0;i<Math.max(min.length,have.length);i++){long a=i<have.length?Long.parseLong(have[i]):0,b=i<min.length?Long.parseLong(min[i]):0;if(a!=b)return a>b;}
        return true;
    }
    static boolean loaded() {
        if(Loader.MODINFOS==null||!"12-18-2022".equals(Loader.STS_VERSION))return false;
        JsonArray mods=new JsonArray();
        for(ModInfo info:Loader.MODINFOS){JsonObject mod=new JsonObject();mod.addProperty("id",info.ID);mod.addProperty("version",String.valueOf(info.ModVersion));mods.add(mod);}
        return accepts(mods)&&(!Loader.isModLoaded("sts1solver")||SolverReplay.contract());
    }
}
