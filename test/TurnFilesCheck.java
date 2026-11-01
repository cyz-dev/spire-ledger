package spireledger;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
public final class TurnFilesCheck {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception {TurnRecorder.battle();String first=TurnRecorder.combatId;TurnRecorder.battle();check(!first.equals(TurnRecorder.combatId),"same-floor battles get independent identities");JsonObject save=new JsonObject();save.addProperty("combat_id",first);TurnRecorder.restoreCombat(save);TurnRecorder.battle();check(first.equals(TurnRecorder.combatId),"saved combat identity survives load preparation");Path directory=Paths.get(args[0]);Files.createDirectories(directory);TurnFiles files=new TurnFiles(directory,"run",new JsonObject());JsonObject entry=new JsonObject();entry.addProperty("combat_id","first");entry.addProperty("turn",1);JsonObject snapshot=new JsonObject();snapshot.addProperty("seed","1234567890123456789");JsonObject ref=files.publish(entry,snapshot);check("recorded".equals(Recorder.string(ref,"availability")),"completed point");check(TurnFiles.read(files.directory.resolve(Recorder.string(ref,"relative_path"))).equals(snapshot),"point independent/readback");new TurnFiles(directory,"run",new JsonObject()).publish(entry,null);check(TurnFiles.read(files.directory.resolve("manifest.json")).getAsJsonArray("snapshots").size()==2,"restart retains completed points");ByteArrayOutputStream output=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(output)){TurnFiles.addToZip(directory,zip);}int count=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))){while(zip.getNextEntry()!=null)count++;}check(count==2,"only manifest and completed point");boolean invalid=false;ref.addProperty("relative_path","../outside.json");files.manifest.getAsJsonArray("snapshots").set(0,ref);TurnFiles.atomic(files.directory.resolve("manifest.json"),files.manifest.toString().getBytes("UTF-8"));try(ZipOutputStream zip=new ZipOutputStream(new ByteArrayOutputStream())){TurnFiles.addToZip(directory,zip);}catch(IOException ex){invalid=true;}check(invalid,"path traversal rejected");Path blocked=directory.resolve("blocked");Files.write(blocked,new byte[0]);boolean failed=false;try{new TurnFiles(blocked,"run",new JsonObject());}catch(IOException ex){failed=true;}check(failed,"IO failure explicit");
  boolean ptFailed = false;
  try { ReplayLauncher.resolveTurnPath(directory, "../evil.json"); } catch (IOException ex) { ptFailed = true; }
  check(ptFailed, "path traversal rejected by resolveTurnPath");
  boolean badFormat = false;
  try { ReplayLauncher.resolveTurnPath(directory, "turns/not-uuid.json"); } catch (IOException ex) { badFormat = true; }
  check(badFormat, "non-uuid relative path rejected by resolveTurnPath");
  boolean valFailed = false;
  try { TurnAdapter.validateSnapshot(new JsonObject()); } catch (IllegalArgumentException ex) { valFailed = true; }
  check(valFailed, "empty snapshot rejected by validateSnapshot");
JsonObject board=new JsonParser().parse("{\"hp\":30,\"deck\":[{\"uuid\":\"a\",\"id\":\"Strike\"}],\"monsters\":[{\"hb\":{\"x\":1,\"y\":2},\"draw_y\":3,\"intent_alpha\":0.5,\"health_hb\":{\"y\":4,\"hovered\":true}}]}").getAsJsonObject();
  JsonObject sameBoard=new JsonParser().parse("{\"hp\":30,\"deck\":[{\"uuid\":\"b\",\"id\":\"Strike\"}],\"monsters\":[{\"hb\":{\"x\":1,\"y\":9},\"draw_y\":7,\"intent_alpha\":0.9,\"health_hb\":{\"y\":8,\"hovered\":false}}]}").getAsJsonObject();
  JsonObject otherBoard=new JsonParser().parse("{\"hp\":29,\"deck\":[{\"uuid\":\"a\",\"id\":\"Strike\"}],\"monsters\":[]}").getAsJsonObject();
  check(TurnFiles.contentHash(board).equals(TurnFiles.contentHash(sameBoard)),"uuid and presentation values never split the same board");
  check(!TurnFiles.contentHash(board).equals(TurnFiles.contentHash(otherBoard)),"a different board hashes differently");
  Path dedupe=directory.resolve("dedupe");Files.createDirectories(dedupe);
  TurnFiles store=new TurnFiles(dedupe,"second-run",new JsonObject());JsonObject point=new JsonObject();point.addProperty("combat_id","c");point.addProperty("turn",1);
  JsonObject firstCopy=store.publish(point,new JsonParser().parse(board.toString()).getAsJsonObject());
  JsonObject secondCopy=store.publish(point,new JsonParser().parse(sameBoard.toString()).getAsJsonObject());
  check("recorded".equals(Recorder.string(firstCopy,"availability"))&&"duplicate".equals(Recorder.string(secondCopy,"availability")),"the same board is stored once and the repeat points at it: "+secondCopy);
  check(Recorder.string(secondCopy,"duplicate_of").equals(Recorder.string(firstCopy,"snapshot_id")),"the repeat keeps its own id and names the original");
  long stored;try(java.util.stream.Stream<Path> list=Files.list(dedupe.resolve("replay").resolve("turns"))){stored=list.count();}
  check(stored==1,"two identical points leave one file on disk: "+stored);
  check(TurnFiles.read(dedupe.resolve("replay/manifest.json"),TurnFiles.MAX_MANIFEST).getAsJsonArray("snapshots").size()==2,"both attempts stay in the manifest");
System.out.println("TurnFilesCheck PASS: durable independent points, restart, bounded ZIP and IO failures, one copy per board");}
}
