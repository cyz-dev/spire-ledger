package sts1state;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class TurnValidationCheck {
 interface Checked {void run()throws Exception;}
 static void reject(String name,Checked check)throws Exception{boolean rejected=false;try{check.run();}catch(Exception e){rejected=true;}if(!rejected)throw new AssertionError(name+" accepted");System.out.println(name+": REJECTED");}
 static JsonObject copy(JsonObject value){return Recorder.copy(value);}
 public static void main(String[] args)throws Exception{
  Path run=Paths.get(args[0]);JsonObject manifest=TurnFiles.read(run.resolve("replay/manifest.json")),ref=null;
  for(JsonElement e:manifest.getAsJsonArray("snapshots"))if(e.getAsJsonObject().get("turn").getAsInt()==2)ref=e.getAsJsonObject();
  if(ref==null)throw new AssertionError("turn two fixture required");JsonObject good=ReplayLauncher.readTarget(run,run.getFileName().toString(),ref);System.out.println("real_published_fixture: ACCEPTED");
  for(String field:Arrays.asList("manager","rng")){JsonObject bad=copy(good);bad.getAsJsonObject("supplement").remove(field);reject("missing_"+field,()->TurnAdapter.validateSnapshot(bad));}
  JsonObject bad=copy(good);JsonObject card=new JsonObject();card.addProperty("index",Integer.MAX_VALUE);bad.getAsJsonObject("supplement").getAsJsonArray("combat_history").add(card);final JsonObject cardBad=bad;reject("card_upper_bound",()->TurnAdapter.validateSnapshot(cardBad));
  bad=copy(good);card=new JsonObject();card.addProperty("active_index",Integer.MAX_VALUE);card.addProperty("combat_index",-1);bad.getAsJsonObject("supplement").getAsJsonObject("manager").getAsJsonArray("orb_combat_history").add(card);final JsonObject orbBad=bad;reject("orb_upper_bound",()->TurnAdapter.validateSnapshot(orbBad));
  bad=copy(good);bad.getAsJsonObject("supplement").getAsJsonObject("rng").getAsJsonObject("miscRng").addProperty("state0","broken");final JsonObject rngBad=bad;reject("invalid_rng_number",()->TurnAdapter.validateSnapshot(rngBad));
  bad=copy(good);bad.remove("environment");final JsonObject envBad=bad;reject("missing_environment",()->TurnAdapter.validateSnapshot(envBad));
  bad=copy(good);bad.getAsJsonObject("environment").addProperty("class","IRONCLAD");final JsonObject classBad=bad;reject("class_mismatch",()->TurnAdapter.validateSnapshot(classBad));
  bad=copy(good);bad.getAsJsonObject("environment").addProperty("dungeon_id","TheEnding");final JsonObject actBad=bad;reject("act_mismatch",()->TurnAdapter.validateSnapshot(actBad));
  JsonObject alteredRef=copy(ref);alteredRef.addProperty("branch_id","wrong");reject("manifest_ref_mismatch",()->ReplayLauncher.readTarget(run,run.getFileName().toString(),alteredRef));
  final JsonObject published=ref;reject("run_identity_mismatch",()->ReplayLauncher.readTarget(run,"00000000-0000-0000-0000-000000000000",published));
  reject("path_traversal",()->ReplayLauncher.resolveTurnPath(run,"../../desktop-1.0.jar"));
  Path copyRoot=Paths.get(args[1]),local=copyRoot.resolve(run.getFileName());Files.createDirectories(local.resolve("replay/turns"));Path turn=local.resolve("replay").resolve(Recorder.string(ref,"relative_path"));Files.copy(ReplayLauncher.resolveTurnPath(run,Recorder.string(ref,"relative_path")),turn,StandardCopyOption.REPLACE_EXISTING);Path localManifest=local.resolve("replay/manifest.json");
  for(String variant:Arrays.asList("missing_mod","changed_mod","extra_mod","changed_recorder_version")){JsonObject altered=copy(manifest);JsonArray mods=altered.getAsJsonArray("mods");if(variant.equals("changed_recorder_version"))altered.addProperty("mod_version","unknown");else if(variant.equals("missing_mod"))mods.remove(0);else if(variant.equals("changed_mod"))mods.get(0).getAsJsonObject().addProperty("version","unverified");else{JsonObject extra=new JsonObject();extra.addProperty("id","unknown");extra.addProperty("version","1");mods.add(extra);}Files.write(localManifest,altered.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));reject(variant,()->ReplayLauncher.readTarget(local,run.getFileName().toString(),published));}
  Files.write(localManifest,manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] changed=Files.readAllBytes(turn);Files.write(turn,(new String(changed,java.nio.charset.StandardCharsets.UTF_8)+" ").getBytes(java.nio.charset.StandardCharsets.UTF_8));reject("checksum_tamper",()->ReplayLauncher.readTarget(local,run.getFileName().toString(),published));
  System.out.println("TurnValidationCheck PASS");
 }
}
