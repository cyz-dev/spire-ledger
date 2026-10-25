package spireledger.stats;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Defensive reads of the record schema: a wrong shape reads as absent, never as zero. */
final class Json {
    static String str(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()?v.getAsString():"";}
    static int num(JsonObject o,String k,int fallback){try{return o.has(k)?o.get(k).getAsInt():fallback;}catch(Exception e){return fallback;}}
    static JsonObject obj(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonObject()?v.getAsJsonObject():new JsonObject();}
    static JsonArray arr(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonArray()?v.getAsJsonArray():new JsonArray();}
    static boolean isArray(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonArray();}
    static Boolean bool(JsonObject o,String k){JsonElement v=o.get(k);return v!=null&&v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean()?v.getAsBoolean():null;}
    static Double number(JsonObject o,String k){JsonElement v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())return null;double n=v.getAsDouble();return Double.isFinite(n)?n:null;}
    static Integer integer(JsonObject o,String k){Double n=number(o,k);return n==null?null:n.intValue();}
    static Integer integer(JsonElement e){return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()&&Double.isFinite(e.getAsDouble())?(int)e.getAsDouble():null;}
    static String cardId(String id){return id.replaceFirst("\\+\\d*$","");}
    static List<String> strings(JsonArray a){List<String> out=new ArrayList<>();for(JsonElement e:a)if(e.isJsonPrimitive())out.add(e.getAsString());return out;}
    // Same digest as the record index (HistoryIndex.sig), so a summary projection and its full record compare equal.
    static String sig(JsonObject e){JsonElement v=e.get("_sig");if(v!=null&&v.isJsonPrimitive())return v.getAsString();
        JsonObject n=new JsonParser().parse(e.toString()).getAsJsonObject();n.remove("seq");n.remove("time_ms");
        try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(n.toString().getBytes(StandardCharsets.UTF_8))).substring(0,16);}catch(Exception x){throw new IllegalStateException(x);}}
}
