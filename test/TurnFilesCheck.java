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
String[] allowed={"basemod|5.56.0","RunHistoryPlus|1.8.3","superfastmode|1.0.8","stslib|2.12.0","spire-ledger|0.6.0-beta.1","HideInfo|0.0.1","lolifontmod|0.2","CardImageReplacer|2.0"};
  JsonArray combination=new JsonArray();
  for(String pair:allowed){String[] parts=pair.split("\\|");JsonObject mod=new JsonObject();mod.addProperty("id",parts[0]);mod.addProperty("version",parts[1]);combination.add(mod);}
  check(ReplayEnvironment.accepts(combination),"the installed combination with CardImageReplacer stays inside the whitelist");
  combination.get(combination.size()-1).getAsJsonObject().addProperty("version","2.1");
  check(!ReplayEnvironment.accepts(combination),"an unreviewed build of the same mod is still rejected");
// Every presentation field must be dropped by the hash: the list used to live in two places and drifted (reticle_rendered).
  for(String field:SnapshotShape.VISUAL){
    JsonObject plain=new JsonParser().parse("{\"state\":{\"hp\":30,\"card\":{\"id\":\"Strike\"}}}").getAsJsonObject();
    JsonObject noisy=new JsonParser().parse("{\"state\":{\"hp\":30,\"card\":{\"id\":\"Strike\"}}}").getAsJsonObject();
    plain.getAsJsonObject("state").add(field,new JsonPrimitive(1));
    noisy.getAsJsonObject("state").add(field,new JsonPrimitive("different"));
    check(TurnFiles.contentHash(plain).equals(TurnFiles.contentHash(noisy)),field+" is presentation and must not split the same board");
  }
  JsonObject entered=new JsonParser().parse("{\"state\":{\"hp\":30,\"cur_map_node_state\":{\"taken\":false}}}").getAsJsonObject();
  JsonObject reloaded=new JsonParser().parse("{\"state\":{\"hp\":30,\"cur_map_node_state\":{\"taken\":true}}}").getAsJsonObject();
  check(!TurnFiles.contentHash(entered).equals(TurnFiles.contentHash(reloaded)),"the map node's taken flag is state and still splits the board");
  Path tree=directory.resolve("tree");Files.createDirectories(tree);JsonObject meta=new JsonObject();
  Recorder session1=new Recorder(tree,meta,"11111111-1111-1111-1111-111111111111");
  check(session1.branchParent.isEmpty()&&"run_start".equals(session1.branchReason),"the first session of a run has no parent");
  TurnFiles treeFiles=new TurnFiles(tree.resolve(session1.runId),session1.runId,new JsonObject());
  JsonObject treeEntry=new JsonObject();treeEntry.addProperty("branch_id",session1.branch);treeEntry.addProperty("turn",1);treeEntry.addProperty("captured_at",1L);
  JsonObject treeSnap=new JsonParser().parse("{\"state\":{\"hp\":30,\"deck\":[]}}").getAsJsonObject();
  JsonObject treeRef=treeFiles.publish(treeEntry,treeSnap);session1.close();
  Recorder session2=new Recorder(tree,meta,session1.runId);
  check(!session2.branch.equals(session1.branch),"a new process opens a new branch");
  check(session2.branchParent.equals(session1.branch),"the new branch names the session it continues: "+session2.branchParent);
  check(session2.branchParentSnapshot.equals(Recorder.string(treeRef,"snapshot_id")),"the parent pointer names the last recorded checkpoint");
  check("process_restart".equals(session2.branchReason),"a restart records why it branched");
  TurnFiles resumed=new TurnFiles(tree.resolve(session2.runId),session2.runId,new JsonObject());
  JsonObject again=new JsonObject();again.addProperty("branch_id",session2.branch);again.addProperty("turn",1);again.addProperty("captured_at",2L);
  JsonObject repeat=resumed.publish(again,new JsonParser().parse(treeSnap.toString()).getAsJsonObject());
  check("duplicate".equals(Recorder.string(repeat,"availability")),"a re-entered turn costs a pointer, not a second file");
  check(Recorder.string(repeat,"duplicate_of").equals(Recorder.string(treeRef,"snapshot_id")),"the pointer names the original checkpoint");
  check(resumed.liveBytes()==treeFiles.liveBytes(),"the second session adds no bytes for the same board: "+resumed.liveBytes());
  String parentBranch=session2.branch;JsonObject rollback=new JsonObject();rollback.addProperty("snapshot_id",Recorder.string(treeRef,"snapshot_id"));
  session2.forkBranch(parentBranch,"in_combat_turn_rollback",rollback);
  check(!session2.branch.equals(parentBranch)&&session2.branchParent.equals(parentBranch),"a turn rollback forks from the branch it was in");
  check(session2.branchParentSnapshot.equals(Recorder.string(treeRef,"snapshot_id")),"the rollback names the checkpoint it restarted from");
  boolean rollbackLogged=false;for(JsonElement e:session2.document().getAsJsonArray("records")){JsonObject r=e.getAsJsonObject();if("turn_rollback".equals(Recorder.string(r,"type"))&&"in_combat_turn_rollback".equals(Recorder.string(r,"reason"))&&parentBranch.equals(Recorder.string(r,"parent_branch_id")))rollbackLogged=true;}
  check(rollbackLogged,"the rollback stays in events.jsonl for the training side");
  session2.close();
  // A restart must resume the fight, not invent a second one; a new fight on the same floor must not inherit it.
  JsonObject persisted=new JsonObject();persisted.addProperty("combat_id","stored");persisted.addProperty("floor",20);
  check(TurnRecorder.nextCombat(true,20,"",-1,persisted).equals("stored"),"a restarted process resumes the fight it was in");
  check(TurnRecorder.nextCombat(true,20,"current",20,persisted).equals("current"),"an in-process load keeps the live identity");
  check(!TurnRecorder.nextCombat(true,21,"",-1,persisted).equals("stored"),"a load that moved a floor starts a new fight");
  check(!TurnRecorder.nextCombat(true,20,"",-1,null).startsWith("stored"),"a run with no stored fight starts a new one");
  check(!TurnRecorder.nextCombat(false,20,"current",20,persisted).equals("current"),"walking into a new fight never reuses an id");
  JsonObject manifestAfter=TurnFiles.read(dedupe.resolve("replay/manifest.json"),TurnFiles.MAX_MANIFEST);
  JsonObject lastCombat=manifestAfter.getAsJsonObject("last_combat");
  check(lastCombat!=null&&"c".equals(Recorder.string(lastCombat,"combat_id")),"the manifest remembers which fight the run is in: "+lastCombat);
  // Absolute positions only mean something in the window they were captured in (practice window used to be 1024x768).
  check(TurnAdapter.windowScale(1920,1080,1920,1080)==null,"a snapshot restored at the same size is left alone");
  float[] shrink=TurnAdapter.windowScale(1920,1080,1024,768);
  check(shrink!=null&&Math.abs(shrink[0]-0.533333f)<0.001f&&Math.abs(shrink[1]-0.711111f)<0.001f,"a smaller window scales positions by the size ratio: "+java.util.Arrays.toString(shrink));
  check(TurnAdapter.windowScale(0,0,1024,768)==null,"an unknown capture size is never guessed");
  JsonObject legacy=new JsonParser().parse("{\"state\":{\"player_state\":{\"creature\":{\"draw_x\":480.0}}}}").getAsJsonObject();
  check(TurnAdapter.capturedWidth(legacy)==1920,"a snapshot without screen_w is read back from the player's x: "+TurnAdapter.capturedWidth(legacy));
  JsonObject modern=new JsonParser().parse("{\"environment\":{\"screen_w\":1600,\"screen_h\":900},\"state\":{\"player_state\":{\"creature\":{\"draw_x\":400.0}}}}").getAsJsonObject();
  check(TurnAdapter.capturedWidth(modern)==1600&&TurnAdapter.capturedHeight(modern,1600)==900,"a snapshot records its own window size");
System.out.println("TurnFilesCheck PASS: durable independent points, restart, bounded ZIP and IO failures, one copy per board");}
}
