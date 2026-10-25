package sts1state;
import com.google.gson.*;
import java.util.*;
import java.util.function.ToDoubleFunction;
public final class HistoryLayoutCheck {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JsonObject card(String uuid,int upgrade){JsonObject c=new JsonObject();c.addProperty("instance_id",uuid);c.addProperty("id","Strike_R");c.addProperty("upgrades",upgrade);c.addProperty("misc",0);return c;}
 static JsonObject deck(JsonObject... cards){JsonObject s=new JsonObject();JsonArray d=new JsonArray();for(JsonObject c:cards)d.add(c);s.add("deck",d);return s;}
 public static void main(String[] args){
  ToDoubleFunction<String> width=s->s.codePoints().map(c->c>127?10:c=='W'?9:4).sum();
  for(String text:Arrays.asList("中文长牌组武装升级永久值", "WWiiiiWWiii", "😀中文😀永久值", "averylongwordwithoutspaces")){
   List<String> lines=HistoryLayout.wrap(text,24,width);check(String.join("",lines).equals(text),"codepoints retained");for(String line:lines)check(width.applyAsDouble(line)<=24,"pixel bound");
  }
  check(HistoryLayout.wrap("hello world",24,width).equals(Arrays.asList("hello","world")),"word boundary");
  check(width.applyAsDouble(HistoryLayout.fit("中文😀WWWW",25,width))<=25,"ellipsis measured");
  check(HistoryLayout.fit("短",9,width).isEmpty(),"no room for ellipsis");
  JsonObject a=deck(card("a",0),card("b",0)),b=deck(card("a",1),card("c",0));String original=a.toString();HistoryLayout.Delta d=HistoryLayout.delta(a,b);
  check(d.identitiesComplete&&d.cards.size()==3,"same-name instances add remove upgrade");check(d.cards.get(0).kind.equals("removed"),"remove independently observed");
  JsonObject modified=card("a",0);modified.addProperty("misc",9);modified.addProperty("base_cost",0);modified.addProperty("bottle_flame",true);check(HistoryLayout.delta(deck(card("a",0)),deck(modified)).cards.size()==1,"permanent values");
  JsonObject missing=card("",0);check(!HistoryLayout.delta(a,deck(missing)).identitiesComplete,"missing identity explicit");check(!HistoryLayout.delta(a,deck(card("a",0),card("a",1))).identitiesComplete,"duplicate identity explicit");check(a.toString().equals(original),"read only");
  for(String room:new String[]{"MonsterRoom","MonsterRoomElite","MonsterRoomBoss","EventRoom","RestRoom","ShopRoom","TreasureRoom","TreasureRoomBoss","NeowRoom","VictoryRoom","TrueVictoryRoom","EmptyRoom"})for(boolean zh:new boolean[]{true,false}){String name=HistoryScreen.roomName(room,zh);if(name==null||name.equals(room))throw new AssertionError("vanilla room shown as class name: "+room);}if(HistoryScreen.roomName("SomeModRoom",true)!=null)throw new AssertionError("unknown room ids stay raw");
  System.out.println("HistoryLayoutCheck PASS: measured widths, Unicode, instance changes, missing identity, vanilla room names and read-only");
 }
}
