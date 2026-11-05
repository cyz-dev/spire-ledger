package spireledger;
import com.google.gson.*;
import java.nio.file.*;
import java.util.zip.*;
import java.io.*;
import java.nio.file.DirectoryStream;
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
  // A save load returns to the same fight: the identity survives an in-process load and a restart on the same floor, and is dropped when the load moved a floor.
  JsonObject persisted=new JsonObject();persisted.addProperty("combat_id","stored");persisted.addProperty("floor",20);
  check(TurnRecorder.nextCombat(true,20,"",-1,persisted).equals("stored"),"a restarted process resumes the fight it was in");
  check(TurnRecorder.nextCombat(true,20,"current",20,persisted).equals("current"),"an in-process load keeps the live identity");
  check(!TurnRecorder.nextCombat(true,21,"",-1,persisted).equals("stored"),"a load that moved a floor starts a new fight");
  check(!TurnRecorder.nextCombat(false,20,"",-1,persisted).equals("stored"),"a new fight never inherits the stored identity");
  check("first".equals(Recorder.string(TurnFiles.lastCombat(files.directory.resolve("manifest.json")),"combat_id")),"the manifest remembers the fight of the last point");
  // The same board reached again costs no second file and no second row: it only gains an attempt.
  Path manifest=files.directory.resolve("manifest.json");
  int rows=TurnFiles.read(manifest).getAsJsonArray("snapshots").size();
  int blobs=TurnFiles.read(manifest).getAsJsonObject("blobs").entrySet().size();
  JsonObject same=Recorder.copy(snapshot);same.addProperty("reticle_alpha",0.42);same.addProperty("uuid","card-uuid-of-this-session");
  JsonObject dup=new TurnFiles(directory,"run",new JsonObject()).publish(entry,same);
  check("duplicate".equals(Recorder.string(dup,"availability")),"an identical board is a duplicate");
  check(Recorder.string(dup,"relative_path").isEmpty(),"a duplicate writes no file");
  check(Recorder.string(ref,"snapshot_id").equals(Recorder.string(dup,"duplicate_of")),"a duplicate points at the stored board");
  check(dup.get("attempts").getAsInt()==2,"a duplicate counts as another attempt");
  JsonObject after=TurnFiles.read(manifest);
  check(after.getAsJsonArray("snapshots").size()==rows,"a duplicate adds no manifest row");
  check(after.getAsJsonObject("blobs").entrySet().size()==blobs,"a duplicate adds no blob");
  int stored=0;for(JsonElement item:after.getAsJsonArray("snapshots")){JsonObject e=item.getAsJsonObject();if(Recorder.string(dup,"content_sha256").equals(Recorder.string(e,"content_sha256")))stored=e.has("attempts")?e.get("attempts").getAsInt():0;}
  check(stored==2,"the stored point keeps the attempt count");
  // After S/L the final path drops the first session's events, so the history must reach the stored point through the duplicate.
  JsonObject seenAgain=Recorder.copy(dup);seenAgain.addProperty("type","turn_snapshot");
  check(HistoryScreen.pathSnapshots(java.util.Collections.singletonList(seenAgain)).contains(Recorder.string(ref,"snapshot_id")),"a duplicate on the final path keeps the stored point selectable");
  int turnFiles=0;try(DirectoryStream<Path> stream=Files.newDirectoryStream(files.directory.resolve("turns"))){for(Path ignored:stream)turnFiles++;}
  check(turnFiles==1,"a duplicate leaves one file on disk");
  // A different board on the same turn is a point of its own, so merging never hides a real state.
  JsonObject other=new JsonObject();other.addProperty("seed","9876543210987654321");
  int before=TurnFiles.read(manifest).getAsJsonArray("snapshots").size();
  JsonObject second=new TurnFiles(directory,"run",new JsonObject()).publish(entry,other);
  check("recorded".equals(Recorder.string(second,"availability")),"a different board is a new point");
  check(TurnFiles.read(manifest).getAsJsonArray("snapshots").size()==before+1,"the different board adds a row");
  // Same floor and same turn number, different board: the merge key is the state, not the turn.
  JsonObject t3a=new JsonObject();t3a.addProperty("seed","777");JsonObject pl=new JsonObject();pl.addProperty("hp",50);t3a.add("player",pl);
  JsonObject t3b=new JsonObject();t3b.addProperty("seed","777");JsonObject pl2=new JsonObject();pl2.addProperty("hp",40);t3b.add("player",pl2);
  JsonObject e3=new JsonObject();e3.addProperty("combat_id","c3");e3.addProperty("turn",3);e3.addProperty("floor",2);e3.addProperty("captured_at",2000L);
  TurnFiles sameTurn=new TurnFiles(directory,"run",new JsonObject());
  int rowsBefore=TurnFiles.read(manifest).getAsJsonArray("snapshots").size();
  check("recorded".equals(Recorder.string(sameTurn.publish(e3,t3a),"availability")),"the first board of a turn is stored");
  check("recorded".equals(Recorder.string(sameTurn.publish(e3,t3b),"availability")),"a different board on the same turn is stored separately");
  check(TurnFiles.read(manifest).getAsJsonArray("snapshots").size()==rowsBefore+2,"same turn with different boards keeps two points");
  check(!TurnFiles.contentHash(t3a).equals(TurnFiles.contentHash(t3b)),"the hash follows the state, not the turn number");
System.out.println("TurnFilesCheck PASS: durable independent points, restart, identical boards merged with attempts, same turn with different boards kept, bounded ZIP and IO failures, one fight across S/L");}
}
