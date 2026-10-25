package sts1state;

import com.google.gson.*;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Only the nontrivial presentation rules: measured text and observed instance changes. */
final class HistoryLayout {
    static List<String> wrap(String input,double width,ToDoubleFunction<String> measure){
        List<String> lines=new ArrayList<>();String text=input.replace('\r',' ').replace('\n',' ');int start=0;
        while(start<text.length()){
            int end=start,best=start,wordBreak=-1;
            while(end<text.length()){
                int next=end+Character.charCount(text.codePointAt(end));
                if(measure.applyAsDouble(text.substring(start,next))>width&&best>start)break;
                best=next;if(Character.isWhitespace(text.codePointAt(end)))wordBreak=end;end=next;
                if(measure.applyAsDouble(text.substring(start,next))>width)break;
            }
            if(best<text.length()&&wordBreak>start)best=wordBreak;
            lines.add(text.substring(start,best).trim());start=best;
            while(start<text.length()&&Character.isWhitespace(text.codePointAt(start)))start+=Character.charCount(text.codePointAt(start));
        }
        if(lines.isEmpty())lines.add("");return lines;
    }
    static String fit(String text,double width,ToDoubleFunction<String> measure){
        if(measure.applyAsDouble(text)<=width)return text;
        if(measure.applyAsDouble("…")>width)return "";
        int end=0,best=0;while(end<text.length()){end+=Character.charCount(text.codePointAt(end));if(measure.applyAsDouble(text.substring(0,end)+"…")>width)break;best=end;}return text.substring(0,best)+"…";
    }
    static final class Change {
        final String kind;final JsonObject before,after;
        Change(String kind,JsonObject before,JsonObject after){this.kind=kind;this.before=before;this.after=after;}
    }
    static final class Delta {boolean identitiesComplete=true;final List<Change> cards=new ArrayList<>();}
    static Map<String,JsonObject> instances(JsonArray deck){
        Map<String,JsonObject> out=new LinkedHashMap<>();
        for(JsonElement e:deck){if(!e.isJsonObject())return null;JsonObject c=e.getAsJsonObject();String id=HistoryIndex.str(c,"instance_id");if(id.isEmpty()||out.put(id,c)!=null)return null;}return out;
    }
    static Delta delta(JsonObject before,JsonObject after){
        Delta d=new Delta();if(!before.has("deck")||!after.has("deck")){d.identitiesComplete=false;return d;}
        Map<String,JsonObject> b=instances(HistoryIndex.array(before,"deck")),a=instances(HistoryIndex.array(after,"deck"));
        if(b==null||a==null){d.identitiesComplete=false;return d;}
        for(Map.Entry<String,JsonObject> e:b.entrySet())if(!a.containsKey(e.getKey()))d.cards.add(new Change("removed",e.getValue(),null));
        for(Map.Entry<String,JsonObject> e:a.entrySet()){
            JsonObject old=b.get(e.getKey()),now=e.getValue();if(old==null)d.cards.add(new Change("added",null,now));
            else {boolean changed=false;for(String k:Arrays.asList("id","upgrades","misc","base_damage","base_block","base_magic","base_cost","bottle_flame","bottle_lightning","bottle_tornado"))if(!Objects.equals(old.get(k),now.get(k)))changed=true;if(changed)d.cards.add(new Change("changed",old,now));}
        }return d;
    }
}
