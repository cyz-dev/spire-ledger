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
System.out.println("TurnFilesCheck PASS: durable independent points, restart, bounded ZIP and IO failures");}
}
