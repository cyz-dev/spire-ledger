package sts1state;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gson.*;
import com.evacipated.cardcrawl.modthespire.*;
import com.megacrit.cardcrawl.core.*;
import com.megacrit.cardcrawl.helpers.FontHelper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** Opt-in local diagnosis: game-thread snapshots written as one bounded JSON file in the background. */
final class DiagnosticReport {
 static final int MAX_FILE=2*1024*1024,MAX_TOTAL=8*1024*1024;
 static final AtomicBoolean busy=new AtomicBoolean();
 static final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"state-notes-diagnostic");t.setDaemon(true);return t;});
 static volatile String status="";static volatile Path lastOutput;static volatile JsonObject checkpoint,replayTarget;
 static final Deque<JsonObject> errors=new ArrayDeque<>();static boolean held;
 static void rememberCheckpoint(JsonObject snapshot){checkpoint=Recorder.copy(snapshot);}
 static void rememberReplay(JsonObject snapshot){replayTarget=Recorder.copy(snapshot);}
 static synchronized void error(String where,Throwable failure){JsonObject e=new JsonObject();e.addProperty("where",where);e.addProperty("time_ms",System.currentTimeMillis());e.addProperty("type",failure.getClass().getName());e.addProperty("message",String.valueOf(failure.getMessage()));StringWriter text=new StringWriter();failure.printStackTrace(new PrintWriter(text));String stack=text.toString();e.addProperty("stack",stack.substring(0,Math.min(stack.length(),16000)));errors.addLast(e);while(errors.size()>8)errors.removeFirst();}
 static void pollShortcut(){if(Gdx.input==null)return;boolean down=Gdx.input.isKeyPressed(Input.Keys.R)&&(Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)||Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))&&(Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)||Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));if(down&&!held)request();held=down;}
 static boolean request(){return request(StateMod.ROOT.getParent().resolve("bug-reports"));}
 static boolean request(Path output){
  if(!busy.compareAndSet(false,true))return false;
  try{
  status=HistoryScreen.tr("正在导出本地错误报告…","Exporting local error report…");
  JsonObject context=new JsonObject();context.addProperty("schema","sts1-state-diagnostic-v1");context.addProperty("mod_version",Recorder.VERSION);context.addProperty("game_version",Loader.STS_VERSION);context.addProperty("mts_version",String.valueOf(Loader.MTS_VERSION));context.addProperty("embedded_save_state","1.3.0/capture-only-1/embedded-1");context.addProperty("session_kind",ReplaySafety.practice()?"practice":"normal");context.addProperty("quarantined",ReplaySafety.quarantined);context.addProperty("privacy","local only; no raw save, account, audio, screenshot, full log, database or upload; diagnostics are not training permission");
  JsonArray mods=new JsonArray();if(Loader.MODINFOS!=null)for(ModInfo info:Loader.MODINFOS){JsonObject mod=new JsonObject();mod.addProperty("id",info.ID);mod.addProperty("version",String.valueOf(info.ModVersion));mods.add(mod);}context.add("mods",mods);
  JsonArray recent=new JsonArray();synchronized(DiagnosticReport.class){for(JsonObject e:errors)recent.add(Recorder.copy(e));}context.add("recent_errors",recent);
  if(StateMod.recorder!=null){context.addProperty("run_id",StateMod.recorder.runId);context.addProperty("branch_id",StateMod.recorder.branch);context.add("recent_events",StateMod.recorder.recent(24));}
  context.addProperty("combat_id",TurnRecorder.combatId);context.addProperty("recording_status",StateMod.status);context.addProperty("turn_recording_status",TurnRecorder.status);
  Map<String,JsonElement> files=new LinkedHashMap<>();files.put("context",context);
  if(checkpoint!=null)files.put("last_checkpoint",checkpoint);if(replayTarget!=null)files.put("replay_target",replayTarget);
  if(!ReplaySafety.quarantined&&TurnAdapter.restoreReady())try{files.put("current_battle",TurnAdapter.capture());}catch(Exception e){error("diagnostic_capture",e);context.addProperty("capture_error",e.toString());}
  writer.submit(()->{try{lastOutput=write(output,files);status=HistoryScreen.tr("错误报告已保存：","Report saved: ")+lastOutput.getFileName();}catch(Exception e){error("diagnostic_write",e);status=HistoryScreen.tr("错误报告导出失败：","Report export failed: ")+e.getClass().getSimpleName();}finally{busy.set(false);}});
  return true;
  }catch(Exception e){error("diagnostic_prepare",e);status=HistoryScreen.tr("错误报告准备失败：","Report preparation failed: ")+e.getClass().getSimpleName();busy.set(false);return false;}
 }
 static JsonElement sanitize(JsonElement value){
  if(value==null||value.isJsonNull())return JsonNull.INSTANCE;
  if(value.isJsonArray()){JsonArray out=new JsonArray();for(JsonElement e:value.getAsJsonArray())out.add(sanitize(e));return out;}
  if(value.isJsonObject()){JsonObject out=new JsonObject();for(Map.Entry<String,JsonElement> e:value.getAsJsonObject().entrySet()){String key=e.getKey().toLowerCase(Locale.ROOT).replace("_","");if(Arrays.asList("username","playername","profilename","account","accountid","steamid","userid","email","accesstoken","token","password","filepath","absolutepath").contains(key))continue;out.add(e.getKey(),sanitize(e.getValue()));}return out;}
  if(value.getAsJsonPrimitive().isString()){String text=value.getAsString();for(String name:Arrays.asList("user.home","user.dir","java.io.tmpdir")){String path=System.getProperty(name,"");if(!path.isEmpty()){text=text.replace(path,"<local>").replace(path.replace('\\','/'),"<local>");}}text=text.replaceAll("\\\\\\\\[^\\r\\n\\\"<>]*","<local-path>");text=text.replaceAll("(?i)[a-z]:[\\\\/][^\\r\\n\\\"<>]*","<local-path>").replaceAll("/(?:home|Users|tmp|mnt)/[^\\r\\n\\\"<>]*","<local-path>");return new JsonPrimitive(text);}
  return value;
 }
 static Path write(Path output,Map<String,JsonElement> input)throws IOException{
  Path allowed=StateMod.ROOT.getParent().toAbsolutePath().normalize(),dir=output.toAbsolutePath().normalize();if(!dir.startsWith(allowed))throw new IOException("Diagnostic output outside State Notes directory");
  Path walk=dir;while(walk!=null&&walk.startsWith(allowed)){if(Files.isSymbolicLink(walk))throw new IOException("Diagnostic link rejected");walk=walk.getParent();}
  Files.createDirectories(dir);JsonObject report=new JsonObject();int total=0;
  for(Map.Entry<String,JsonElement> entry:input.entrySet()){if(!entry.getKey().matches("[a-z_]+"))throw new IOException("Invalid report section");JsonElement clean=sanitize(entry.getValue());int size=clean.toString().getBytes(StandardCharsets.UTF_8).length;if(size>MAX_FILE||total+size>MAX_TOTAL)throw new IOException("Diagnostic size limit");total+=size;report.add(entry.getKey(),clean);}
  String name="state-notes-report-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)+".json";Path target=dir.resolve(name),tmp=dir.resolve(name+".tmp");
  try{Files.write(tmp,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report).getBytes(StandardCharsets.UTF_8));try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target);}return target;
  }catch(IOException e){Files.deleteIfExists(tmp);throw e;}
 }
 static void openFolder(){try{Path dir=StateMod.ROOT.getParent().resolve("bug-reports");java.nio.file.Files.createDirectories(dir);if(java.awt.Desktop.isDesktopSupported())java.awt.Desktop.getDesktop().open(dir.toFile());else status=HistoryScreen.tr("报告目录：","Report folder: ")+"~/.sts1-state-notes/bug-reports";}catch(Exception e){error("diagnostic_open_folder",e);status=HistoryScreen.tr("无法打开报告目录：","Cannot open report folder: ")+e.getClass().getSimpleName();}}
 static void draw(SpriteBatch sb){if(status.isEmpty())return;FontHelper.renderFontLeftTopAligned(sb,FontHelper.cardDescFont_N,status,20,35,Color.WHITE);}
}
