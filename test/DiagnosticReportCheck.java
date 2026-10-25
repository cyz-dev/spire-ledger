package sts1state;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
public final class DiagnosticReportCheck {
 interface Checked{void run()throws Exception;}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void reject(Checked c)throws Exception{boolean caught=false;try{c.run();}catch(IOException e){caught=true;}check(caught,"expected bounded report refusal");}
 public static void main(String[] args)throws Exception{
  Path dir=StateMod.ROOT.getParent().resolve("reports-check");JsonObject input=new JsonObject();input.addProperty("username","secret-account");input.addProperty("steam_id","secret-steam");input.addProperty("access_token","secret-token");input.addProperty("unc","\\\\private-server\\share\\secret-file");input.addProperty("message","C:\\Users\\private\\game\\save.autosave\n/home/private/game/state.json");JsonArray nested=new JsonArray();JsonObject n=new JsonObject();n.addProperty("account_id","secret-id");n.addProperty("card_id","Anger");nested.add(n);input.add("nested",nested);String original=input.toString();Map<String,JsonElement> files=new LinkedHashMap<>();files.put("context",input);Path report=DiagnosticReport.write(dir,files);check(report.getFileName().toString().endsWith(".json"),"single JSON report");String data=new String(Files.readAllBytes(report),StandardCharsets.UTF_8);JsonObject parsed=new JsonParser().parse(data).getAsJsonObject();check(parsed.entrySet().size()==1&&parsed.has("context"),"only explicit report sections");
  check(!data.contains("private")&&!data.contains("secret-")&&!data.contains("autosave")&&data.contains("Anger"),"recursive accounts/paths removed, gameplay retained");check(original.equals(input.toString()),"source JSON unchanged");
  Map<String,JsonElement> invalid=new LinkedHashMap<>();invalid.put("../context",input);reject(()->DiagnosticReport.write(dir,invalid));reject(()->DiagnosticReport.write(dir.getParent().getParent().resolve("outside"),files));
  char[] big=new char[DiagnosticReport.MAX_FILE+1];Arrays.fill(big,'x');invalid.clear();invalid.put("context",new JsonPrimitive(new String(big)));reject(()->DiagnosticReport.write(dir,invalid));invalid.clear();for(String key:Arrays.asList("part_a","part_b","part_c","part_d","part_e"))invalid.put(key,new JsonPrimitive(new String(big,0,DiagnosticReport.MAX_FILE-32)));reject(()->DiagnosticReport.write(dir,invalid));try(java.util.stream.Stream<Path> stream=Files.list(dir)){check(!stream.anyMatch(p->p.getFileName().toString().endsWith(".tmp")),"failed exports leave no partial file");}
  Path blocked=dir.resolve("not-a-directory");Files.write(blocked,new byte[]{1});check(DiagnosticReport.request(blocked),"async failure starts");long limit=System.currentTimeMillis()+5000;while(DiagnosticReport.busy.get()&&System.currentTimeMillis()<limit)Thread.sleep(10);check(!DiagnosticReport.busy.get()&&DiagnosticReport.status.contains("failed"),"background failure visible and busy reset");
  com.evacipated.cardcrawl.modthespire.ModInfo[] old=com.evacipated.cardcrawl.modthespire.Loader.MODINFOS;try{com.evacipated.cardcrawl.modthespire.Loader.MODINFOS=new com.evacipated.cardcrawl.modthespire.ModInfo[]{null};check(!DiagnosticReport.request()&&!DiagnosticReport.busy.get()&&DiagnosticReport.status.contains("failed"),"synchronous preparation failure releases busy and displays failure");}finally{com.evacipated.cardcrawl.modthespire.Loader.MODINFOS=old;}
  System.out.println("DiagnosticReportCheck PASS: single JSON file, nested privacy, unchanged source, size/path bounds, no partial file, visible async failure");
 }
}
