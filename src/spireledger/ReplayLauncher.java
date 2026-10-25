package spireledger;

import com.google.gson.*;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.ModInfo;
import com.evacipated.cardcrawl.modthespire.steam.SteamSearch;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.dungeons.Exordium;
import com.megacrit.cardcrawl.helpers.TipTracker;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.MonsterGroup;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.rooms.MonsterRoom;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Launcher and runtime manager for in-combat replay and isolated historical practice. */
final class ReplayLauncher {
    // Attachments are keyed by snapshot schema, TurnAdapter.ID and save_state identity, not by our jar version.
    private static final String ANY_VERSION="*";
    static String practiceTargetRunId = System.getProperty("spireledger.practiceRunId", "");
    static String practiceTargetSnapshotId = System.getProperty("spireledger.practiceSnapshotId", "");
    static boolean practiceInitialized = false;
    static int practiceStep = 0;
    static Process launchedProcess;
    static JsonObject readTarget(Path runDir,String runId,JsonObject ref)throws Exception{
        if(!runId.matches("[a-fA-F0-9-]{36}"))throw new IOException("Invalid run identity");
        JsonObject manifest=TurnFiles.read(runDir.resolve("replay/manifest.json"));
        if(!TurnFiles.SCHEMA.equals(Recorder.string(manifest,"schema"))||!runId.equals(Recorder.string(manifest,"run_id"))||!TurnAdapter.ID.equals(Recorder.string(manifest,"adapter"))||!manifest.has("restore_available")||!manifest.get("restore_available").getAsBoolean()||!"12-18-2022".equals(Recorder.string(manifest,"game_version")))throw new IOException("Unsupported or mismatched replay manifest");
        Map<String,String> expectedMods=new HashMap<>();expectedMods.put("basemod","5.56.0");expectedMods.put("stslib","2.12.0");expectedMods.put("spire-ledger",ANY_VERSION);if(!"embedded-capture-only-1".equals(Recorder.string(manifest,"save_state")))throw new IOException("Missing embedded adapter identity");
        JsonArray recordedMods=manifest.getAsJsonArray("mods");if(recordedMods==null)throw new IOException("Missing recording environment");
        for(JsonElement item:recordedMods){JsonObject mod=item.getAsJsonObject();String id=Recorder.string(mod,"id");if(Boolean.getBoolean("spireledger.fixture")&&"spire-ledger-replay-probe".equals(id))continue;String version=expectedMods.remove(id);if(version==null||!(version==ANY_VERSION||version.equals(Recorder.string(mod,"version"))))throw new IOException("Unsupported recording mod: "+id);}
        if(!expectedMods.isEmpty())throw new IOException("Incomplete recording dependencies");
        JsonObject selected=null;
        for(JsonElement item:manifest.getAsJsonArray("snapshots"))if(Recorder.string(ref,"snapshot_id").equals(Recorder.string(item.getAsJsonObject(),"snapshot_id"))){if(selected!=null)throw new IOException("Duplicate snapshot identity");selected=item.getAsJsonObject();}
        if(selected==null||!selected.equals(ref)||!"recorded".equals(Recorder.string(ref,"availability")))throw new IOException("Snapshot reference is not the published manifest entry");
        Path path=resolveTurnPath(runDir,Recorder.string(ref,"relative_path"));
        if(!path.getFileName().toString().equals(Recorder.string(ref,"snapshot_id")+".json"))throw new IOException("Snapshot filename/identity mismatch");
        if(Files.isSymbolicLink(runDir.resolve("replay"))||Files.isSymbolicLink(runDir.resolve("replay/turns"))||!path.toRealPath().startsWith(runDir.toRealPath()))throw new IOException("Untrusted replay link");
        JsonObject snapshot=TurnFiles.read(path),identity=snapshot.getAsJsonObject("identity");
        if(!TurnFiles.sha256(Files.readAllBytes(path)).equals(Recorder.string(ref,"sha256")))throw new IOException("Snapshot checksum mismatch");
        if(identity==null||!runId.equals(Recorder.string(identity,"run_id")))throw new IOException("Snapshot source run mismatch");
        for(String key:Arrays.asList("snapshot_id","branch_id","combat_id","floor","turn","phase"))if(!Objects.equals(identity.get(key),ref.get(key)))throw new IOException("Snapshot reference mismatch: "+key);
        TurnAdapter.validateSnapshot(snapshot);
        if(ref.get("floor").getAsInt()!=snapshot.getAsJsonObject("state").get("floor_num").getAsInt()||ref.get("turn").getAsInt()!=snapshot.getAsJsonObject("state").get("turn").getAsInt())throw new IOException("Snapshot state/header mismatch");
        return snapshot;
    }

    static Path resolveTurnPath(Path runDir, String rel) throws IOException {
        if (rel == null || !rel.matches("^turns/[a-fA-F0-9-]{36}\\.json$")) {
            throw new IOException("Invalid or untrusted turn relative path: " + rel);
        }
        Path runReplay = runDir.resolve("replay").toAbsolutePath().normalize();
        Path target = runReplay.resolve(rel).toAbsolutePath().normalize();
        if (!target.startsWith(runReplay)) {
            throw new IOException("Path traversal detected in replay relative path: " + rel);
        }
        return target;
    }

    static boolean canReplayCurrent(JsonObject ref) {
        if (ref == null || !TurnAdapter.restoreReady() || AbstractDungeon.getCurrRoom() == null) return false;
        if (AbstractDungeon.getCurrRoom().phase != AbstractRoom.RoomPhase.COMBAT) return false;
        String curCombat = TurnRecorder.combatId;
        String refCombat = Recorder.string(ref, "combat_id");
        return !curCombat.isEmpty() && curCombat.equals(refCombat);
    }

    static boolean replayCurrent(JsonObject ref, Path snapshotPath) throws Exception {
        if (!canReplayCurrent(ref)) {
            throw new IllegalStateException("Not currently in the same combat");
        }
        if (!TurnRecorder.compatible()) {
            throw new IllegalStateException("Environment not compatible for turn restore");
        }
        if (!Files.exists(snapshotPath)) {
            throw new FileNotFoundException("Snapshot file missing: " + snapshotPath);
        }
        Path runDir=snapshotPath.getParent().getParent().getParent();
        JsonObject snapshot = readTarget(runDir,runDir.getFileName().toString(),ref);
        TurnAdapter.restore(snapshot);
        if (StateMod.recorder != null) {
            String parentBranch = Recorder.string(ref, "branch_id");
            JsonObject details = new JsonObject();
            details.addProperty("snapshot_id", Recorder.string(ref, "snapshot_id"));
            details.addProperty("combat_id", Recorder.string(ref, "combat_id"));
            details.addProperty("target_turn", ref.has("turn") ? ref.get("turn").getAsInt() : -1);
            details.addProperty("floor", ref.has("floor") ? ref.get("floor").getAsInt() : -1);
            StateMod.recorder.forkBranch(parentBranch, "in_combat_turn_rollback", details);
        }
        HistoryScreen.instance = null;
        return true;
    }

    static Path findDesktopJar() throws FileNotFoundException {
        if (Loader.STS_JAR != null && !Loader.STS_JAR.isEmpty()) {
            Path p = Paths.get(Loader.STS_JAR);
            if (Files.exists(p)) return p;
        }
        try {
            String steamDesktop = SteamSearch.findDesktopJar();
            if (steamDesktop != null && !steamDesktop.isEmpty()) {
                Path p = Paths.get(steamDesktop);
                if (Files.exists(p)) return p;
            }
        } catch (Throwable ignored) {}
        Path userDir = Paths.get(System.getProperty("user.dir", ".")).resolve("desktop-1.0.jar");
        if (Files.exists(userDir)) return userDir;
        Path parentDir = Paths.get(System.getProperty("user.dir", ".")).getParent();
        if (parentDir != null && Files.exists(parentDir.resolve("desktop-1.0.jar"))) return parentDir.resolve("desktop-1.0.jar");
        throw new FileNotFoundException("Cannot locate desktop-1.0.jar");
    }

    static Path findJreExecutable() throws FileNotFoundException {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path exe = javaHome.resolve(System.getProperty("os.name", "").toLowerCase().contains("win") ? "bin/java.exe" : "bin/java");
        if (Files.exists(exe)) return exe;
        try {
            String steamJre = SteamSearch.findJRE();
            if (steamJre != null && !steamJre.isEmpty()) {
                Path p = Paths.get(steamJre);
                if (Files.exists(p)) return p;
            }
        } catch (Throwable ignored) {}
        Path userJre = Paths.get(System.getProperty("user.dir", ".")).resolve("jre/bin/java.exe");
        if (Files.exists(userJre)) return userJre;
        throw new FileNotFoundException("Cannot locate java runtime executable");
    }

    static Path findModJar(String modId) throws FileNotFoundException {
        if (Loader.MODINFOS != null) {
            for (ModInfo info : Loader.MODINFOS) {
                if (modId.equalsIgnoreCase(info.ID)) {
                    URL url = info.jarURL;
                    if (url != null) {
                        try {
                            Path p = Paths.get(url.toURI());
                            if (Files.exists(p)) return p;
                        } catch (Exception ignored) {
                            try {
                                Path p = Paths.get(url.getPath());
                                if (Files.exists(p)) return p;
                            } catch (Exception ignored2) {}
                        }
                    }
                }
            }
        }
        throw new FileNotFoundException("Required mod jar not loaded in ModTheSpire: " + modId);
    }

    static Path findModTheSpireJar() throws FileNotFoundException {
        try {
            URL url = Loader.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                Path p = Paths.get(url.toURI());
                if (Files.exists(p)) return p;
            }
        } catch (Throwable ignored) {}
        Path localMts = Paths.get("ModTheSpire.jar");
        if (Files.exists(localMts)) return localMts;
        throw new FileNotFoundException("Cannot locate ModTheSpire.jar");
    }

    static Path findStateNotesJar() throws FileNotFoundException {
        try {
            URL url = StateMod.class.getProtectionDomain().getCodeSource().getLocation();
            if (url != null) {
                Path p = Paths.get(url.toURI());
                if (Files.exists(p)) return p;
            }
        } catch (Throwable ignored) {}
        Path local = Paths.get("mods/SpireLedger.jar");
        if (Files.exists(local)) return local;
        Path buildJar = Paths.get("build/SpireLedger.jar");
        if (Files.exists(buildJar)) return buildJar;
        throw new FileNotFoundException("Cannot locate SpireLedger.jar");
    }

    static long getProcessPid(Process process) {
        try {
            Method m = process.getClass().getMethod("pid");
            return (Long) m.invoke(process);
        } catch (Throwable ignored) {}
        try {
            Field f = process.getClass().getDeclaredField("handle");
            f.setAccessible(true);
            long handle = f.getLong(process);
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getInt(process);
        } catch (Throwable ignored) {}
        try {
            Field f = process.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(process);
        } catch (Throwable ignored) {}
        return -1;
    }

    static Path launchPracticeInstance(HistoryIndex.Summary summary, JsonObject ref, Path runDir) throws Exception {
        if (!TurnRecorder.compatible()) {
            throw new IllegalStateException("Environment not compatible for practice instance");
        }
        String snapshotId = Recorder.string(ref, "snapshot_id");
        if (snapshotId.isEmpty()) {
            throw new IllegalArgumentException("No snapshot ID");
        }

        Path desktopJar = findDesktopJar();
        Path jreJava = findJreExecutable();
        Path mtsJar = findModTheSpireJar();
        Path baseModJar = findModJar("basemod");
        Path stslibJar = findModJar("stslib");
        Path stateNotesJar = findStateNotesJar();

        Path turnFile = resolveTurnPath(runDir, Recorder.string(ref, "relative_path"));
        readTarget(runDir,summary.id,ref);
        if (!Files.exists(turnFile)) {
            throw new FileNotFoundException("Snapshot turn file missing: " + turnFile);
        }

        Path instanceDir = StateMod.ROOT.getParent().resolve("practice-instances").resolve("inst-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(instanceDir.resolve("mods"));
        Files.createDirectories(instanceDir.resolve("home"));
        Files.createDirectories(instanceDir.resolve("preferences"));
        Files.createDirectories(instanceDir.resolve("betaPreferences"));
        Files.createDirectories(instanceDir.resolve("temp"));

        // Copy display config and dummy slot prefs to ensure isolated launch
        Files.write(instanceDir.resolve("info.displayconfig"), "1024\n768\n60\nfalse\nfalse\ntrue\n".getBytes(StandardCharsets.UTF_8));
        Files.write(instanceDir.resolve("betaPreferences/STSSaveSlots"), "{\"DEFAULT_SLOT\":\"0\",\"PROFILE_NAME\":\"Practice\"}".getBytes(StandardCharsets.UTF_8));

        // Mute SaveStateMod UI and keybindings in isolated practice instance
        Path saveStateOptionsDir = instanceDir.resolve("home/ModTheSpire/SaveStateMod");
        Files.createDirectories(saveStateOptionsDir);
        Files.write(saveStateOptionsDir.resolve("options.properties"), "num_save_states=0\n".getBytes(StandardCharsets.UTF_8));

        // Copy target run directory into isolated practice instance home
        Path targetRunHome = instanceDir.resolve("home/.spire-ledger/runs").resolve(summary.id);
        Files.createDirectories(targetRunHome.resolve("replay/turns"));
        Path manifestSrc = runDir.resolve("replay/manifest.json");
        if (Files.exists(manifestSrc)) {
            Files.copy(manifestSrc, targetRunHome.resolve("replay/manifest.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        Path targetTurn = resolveTurnPath(targetRunHome, Recorder.string(ref, "relative_path"));
        Files.createDirectories(targetTurn.getParent());
        Files.copy(turnFile, targetTurn, StandardCopyOption.REPLACE_EXISTING);

        Path runJsonSrc = runDir.resolve("run.json");
        if (Files.exists(runJsonSrc)) {
            Files.copy(runJsonSrc, targetRunHome.resolve("run.json"), StandardCopyOption.REPLACE_EXISTING);
        }

        // Copy verified jars to instance
        Files.copy(mtsJar, instanceDir.resolve("ModTheSpire.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(desktopJar, instanceDir.resolve("desktop-1.0.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(baseModJar, instanceDir.resolve("mods/BaseMod.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(stslibJar, instanceDir.resolve("mods/StSLib.jar"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(stateNotesJar, instanceDir.resolve("mods/SpireLedger.jar"), StandardCopyOption.REPLACE_EXISTING);

        List<String> cmd = new ArrayList<>();
        cmd.add(jreJava.toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dspireledger.experimentalTurns=true");
        cmd.add("-Dspireledger.practiceMode=true");
        cmd.add("-Dspireledger.practiceRunId=" + summary.id);
        cmd.add("-Dspireledger.practiceSnapshotId=" + snapshotId);
        cmd.add("-Duser.home=" + instanceDir.resolve("home").toAbsolutePath().toString());
        cmd.add("-Djava.io.tmpdir=" + instanceDir.resolve("temp").toAbsolutePath().toString());
        cmd.add("-jar");
        cmd.add(instanceDir.resolve("ModTheSpire.jar").toAbsolutePath().toString());
        cmd.add("--skip-launcher");
        cmd.add("--skip-intro");
        cmd.add("--mods");
        cmd.add("basemod,stslib,spire-ledger");
        // A fixture observes the actual child launch; it never prepares the child's battle.
        if(Boolean.getBoolean("spireledger.fixture")&&Boolean.getBoolean("spireledger.launchPracticeProbe")){
            Files.copy(findModJar("spire-ledger-replay-probe"),instanceDir.resolve("mods/NativeReplayProbe.jar"));
            cmd.set(cmd.size()-1,cmd.get(cmd.size()-1)+",spire-ledger-replay-probe");
            cmd.add(1,"-Dspireledger.fixture=true");cmd.add(1,"-Dspireledger.productionPractice=true");cmd.add(1,"-Dspireledger.replayMode=restore");
            cmd.add(1,"-Dspireledger.replaySource="+System.getProperty("user.dir"));cmd.add(1,"-Dspireledger.replayCharacter="+System.getProperty("spireledger.replayCharacter","IRONCLAD"));cmd.add(1,"-Dspireledger.replayScenario="+System.getProperty("spireledger.replayScenario","ordinary"));
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(instanceDir.toFile());
        pb.redirectOutput(instanceDir.resolve("stdout.log").toFile());
        pb.redirectError(instanceDir.resolve("stderr.log").toFile());
        Map<String, String> env = pb.environment();
        env.put("APPDATA", instanceDir.resolve("home").toAbsolutePath().toString());
        env.put("LOCALAPPDATA", instanceDir.resolve("home").toAbsolutePath().toString());
        env.put("TEMP", instanceDir.resolve("temp").toAbsolutePath().toString());
        env.put("TMP", instanceDir.resolve("temp").toAbsolutePath().toString());
        Process p = pb.start();launchedProcess=p;
        long pid = getProcessPid(p);
        Files.write(instanceDir.resolve("pid.txt"), String.valueOf(pid >= 0 ? pid : -1).getBytes(StandardCharsets.UTF_8));
        return instanceDir;
    }

    static void checkPracticeStartup() {
        if (practiceInitialized || practiceTargetRunId.isEmpty() || practiceTargetSnapshotId.isEmpty()) return;
        try {
            Path runHome = StateMod.ROOT.resolve(practiceTargetRunId);
            Path manifestPath = runHome.resolve("replay/manifest.json");
            if (!Files.exists(manifestPath)) throw new FileNotFoundException("Practice manifest missing");
            JsonObject manifest = TurnFiles.read(manifestPath);
            JsonObject targetSnapshot = null;
            for (JsonElement item : manifest.getAsJsonArray("snapshots")) {
                JsonObject point = item.getAsJsonObject();
                if (practiceTargetSnapshotId.equals(Recorder.string(point, "snapshot_id"))) {
                    Path pointPath = resolveTurnPath(runHome, Recorder.string(point, "relative_path"));
                    targetSnapshot = readTarget(runHome,practiceTargetRunId,point);
                    break;
                }
            }
            if (targetSnapshot == null) {
                throw new FileNotFoundException("Target snapshot not found in manifest: " + practiceTargetSnapshotId);
            }

            if (practiceStep == 0) {
                if (CardCrawlGame.mainMenuScreen == null) return;
                if(!TurnRecorder.compatible())throw new IllegalStateException("Unsupported practice dependencies");
                JsonObject env=targetSnapshot.getAsJsonObject("environment");
                JsonObject state = targetSnapshot.getAsJsonObject("state");
                JsonObject rngState = state.getAsJsonObject("rng_state");
                if (!rngState.has("seed") || !rngState.get("seed").isJsonPrimitive()) {
                    throw new IllegalStateException("Snapshot missing required seed for environment initialization");
                }
                long seed = rngState.get("seed").getAsLong();
                Settings.seed = seed;
                AbstractDungeon.ascensionLevel=env.get("ascension").getAsInt();AbstractDungeon.isAscensionMode=env.get("ascension_mode").getAsBoolean();
                AbstractDungeon.generateSeeds();

                JsonObject pState = state.getAsJsonObject("player_state");
                if (!pState.has("chosen_class_name") || !pState.get("chosen_class_name").isJsonPrimitive()) {
                    throw new IllegalStateException("Snapshot missing required player class name");
                }
                String charName = pState.get("chosen_class_name").getAsString();
                CardCrawlGame.chosenCharacter = AbstractPlayer.PlayerClass.valueOf(charName);
                CardCrawlGame.mainMenuScreen.fadedOut = true;
                practiceStep = 1;
                return;
            }

            if (practiceStep == 1) {
                if (CardCrawlGame.dungeonTransitionScreen != null) {
                    CardCrawlGame.dungeonTransitionScreen.isComplete = true;
                }
                String expectedDungeon=targetSnapshot.getAsJsonObject("environment").get("dungeon_id").getAsString();
                CardCrawlGame.nextDungeon=expectedDungeon;
                if (CardCrawlGame.dungeon == null || !expectedDungeon.equals(CardCrawlGame.dungeon.getClass().getSimpleName())) {
                    TipTracker.disableAllFtues();
                    AbstractDungeon.ftue = null;
                    if (CardCrawlGame.characterManager != null) {
                        AbstractPlayer p = CardCrawlGame.characterManager.recreateCharacter(CardCrawlGame.chosenCharacter);
                        JsonObject state = targetSnapshot.getAsJsonObject("state");
                        int act = targetSnapshot.getAsJsonObject("environment").get("act").getAsInt();
                        if (act == 4) {
                            CardCrawlGame.dungeon = new com.megacrit.cardcrawl.dungeons.TheEnding(p, new ArrayList<>());
                        } else if (act == 3) {
                            CardCrawlGame.dungeon = new com.megacrit.cardcrawl.dungeons.TheBeyond(p, new ArrayList<>());
                        } else if (act == 2) {
                            CardCrawlGame.dungeon = new com.megacrit.cardcrawl.dungeons.TheCity(p, new ArrayList<>());
                        } else {
                            CardCrawlGame.dungeon = new Exordium(p, new ArrayList<>());
                        }
                    }
                }
                if (CardCrawlGame.dungeon != null) {
                    if (AbstractDungeon.currMapNode == null) {
                        MapRoomNode node = new MapRoomNode(0, 0);
                        node.room = new MonsterRoom();
                        AbstractDungeon.setCurrMapNode(node);
                    }
                    if (StateMod.ready()) {
                        practiceStep = 2;
                    }
                }
                return;
            }

            if (practiceStep == 2) {
                if(CardCrawlGame.dungeon==null||!targetSnapshot.getAsJsonObject("environment").get("dungeon_id").getAsString().equals(CardCrawlGame.dungeon.getClass().getSimpleName())){practiceStep=1;return;}
                TipTracker.disableAllFtues();
                AbstractDungeon.ftue = null;
                TurnAdapter.restore(targetSnapshot);
                practiceInitialized = true;
                System.out.println("[Spire Ledger] Practice snapshot successfully restored: " + practiceTargetSnapshotId);
                JsonObject receipt=new JsonObject();receipt.addProperty("status","ready");receipt.addProperty("run_id",practiceTargetRunId);receipt.addProperty("snapshot_id",practiceTargetSnapshotId);receipt.addProperty("interactive",TurnAdapter.stable());
                Files.write(Paths.get("practice-startup.json"),receipt.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            System.err.println("[Spire Ledger] Failed to restore practice snapshot: " + ex.getMessage());
            ex.printStackTrace();
            ReplaySafety.quarantine(ex);
            try{JsonObject receipt=new JsonObject();receipt.addProperty("status","failed");receipt.addProperty("error",ex.toString());Files.write(Paths.get("practice-startup.json"),receipt.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException receiptFailure){receiptFailure.printStackTrace();}
            practiceInitialized = true;
        }
    }
}
