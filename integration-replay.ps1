param([string]$Game='D:\SteamLibrary\steamapps\common\SlayTheSpire',[string]$Instance='state-notes-dev/native-replay-save',[ValidateSet("save","restore")][string]$Mode="save",[string]$Source="",[switch]$Repair,[switch]$ProductionRestore,[switch]$ProductionPractice,[string]$PracticeRunId="",[string]$PracticeSnapshotId="",[switch]$ReplayCurrent,[switch]$Diagnostic,[string]$Failure="",[switch]$LaunchPractice,[switch]$NoCapture,[switch]$ExperimentalTurns,[string]$Character="IRONCLAD",[string]$Scenario="ordinary",[switch]$PrepareOnly,[switch]$WaitForExit)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$utf8=[Text.UTF8Encoding]::new($false)
. "$PSScriptRoot/tools/paths.ps1"
$instance=Resolve-RunPath $Instance
$workshop=Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop\content\646570'
New-Item -ItemType Directory -Force "$instance\mods","$instance\home","$instance\preferences","$instance\temp","$instance\probe-classes" | Out-Null
if(-not(Test-Path "$instance\desktop-1.0.jar")){Copy-Item "$Game\desktop-1.0.jar" $instance}
Copy-Item "$workshop\1605060445\ModTheSpire.jar" $instance
Copy-Item "$workshop\1605833019\BaseMod.jar" "$instance\mods"
Copy-Item "$workshop\1609158507\StSLib.jar" "$instance\mods"
if($Mode -eq "restore"){
    if(-not $Source){throw "Restore source required"}
    $Source=(Resolve-Path (Resolve-RunPath $Source)).Path
}
Copy-Item "$PSScriptRoot\build\STS1StateNotes.jar" "$instance\mods"
$cp="$instance\desktop-1.0.jar;$instance\ModTheSpire.jar;$instance\mods\*"
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d "$instance\probe-classes" "$PSScriptRoot\test\NativeReplayProbe.java"
if($LASTEXITCODE -ne 0){throw 'NativeReplayProbe compile failed'}
[IO.File]::WriteAllText("$instance\probe-classes\ModTheSpire.json",'{"modid":"state-notes-replay-probe","name":"Isolated Native Replay Probe","version":"0.1.0","dependencies":["sts1-state-notes"]}',$utf8)
& jar cf "$instance\mods\NativeReplayProbe.jar" -C "$instance\probe-classes" .
if($LASTEXITCODE -ne 0){throw 'NativeReplayProbe package failed'}
[IO.File]::WriteAllText("$instance\info.displayconfig","1024`n768`n60`nfalse`nfalse`ntrue`n",$utf8)
New-Item -ItemType Directory -Force "$instance\betaPreferences" | Out-Null
[IO.File]::WriteAllText("$instance\betaPreferences\STSSaveSlots",'{"DEFAULT_SLOT":"0","PROFILE_NAME":"UI fixture"}',$utf8)
if($PrepareOnly){Write-Output "Prepared isolated instance: $instance";return}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try {
 $env:APPDATA="$instance\home";$env:LOCALAPPDATA="$instance\home";$env:TEMP="$instance\temp";$env:TMP="$instance\temp"
 $args=@(("-Dsts1state.diagnosticProbe="+$Diagnostic.ToString().ToLowerInvariant()),("-Dsts1state.replayCurrentProbe="+$ReplayCurrent.ToString().ToLowerInvariant()),("-Dsts1state.replayFailure="+$Failure),("-Dsts1state.launchPracticeProbe="+$LaunchPractice.ToString().ToLowerInvariant()),("-Dsts1state.productionRestore="+$ProductionRestore.ToString().ToLowerInvariant()),("-Dsts1state.productionPractice="+$ProductionPractice.ToString().ToLowerInvariant()),("-Dsts1state.practiceMode="+$ProductionPractice.ToString().ToLowerInvariant()),("-Dsts1state.practiceRunId="+$PracticeRunId),("-Dsts1state.practiceSnapshotId="+$PracticeSnapshotId),("-Dsts1state.experimentalTurns="+$ExperimentalTurns.ToString().ToLowerInvariant()),("-Dsts1state.replayNoCapture="+$NoCapture.ToString().ToLowerInvariant()),("-Dsts1state.replayCharacter="+$Character),("-Dsts1state.replayScenario="+$Scenario),('-Dsts1state.replayRepair='+$Repair.ToString().ToLowerInvariant()),('-Dsts1state.replayMode='+$Mode),('"-Dsts1state.replaySource='+$Source+'"'),'-Dfile.encoding=UTF-8','-Dsts1state.fixture=true',('"-Duser.home='+$instance+'/home"'),('"-Djava.io.tmpdir='+$instance+'/temp"'),'-jar','ModTheSpire.jar','--skip-launcher','--skip-intro','--mods','basemod,stslib,sts1-state-notes,state-notes-replay-probe')
 $proc=Start-Process -FilePath "$Game\jre\bin\java.exe" -ArgumentList $args -WorkingDirectory $instance -WindowStyle Hidden -RedirectStandardOutput "$instance\stdout.log" -RedirectStandardError "$instance\stderr.log" -PassThru
 [IO.File]::WriteAllText("$instance\pid.txt",[string]$proc.Id,$utf8)
 Write-Output "Isolated State Notes native probe PID $($proc.Id); result: $instance\replay-probe-result.json"
 if($WaitForExit){
  $proc.WaitForExit()
  [IO.File]::WriteAllText("$instance\process-result.json",('{"exit_code":'+$proc.ExitCode+'}'),$utf8)
  if($proc.ExitCode -ne 0){throw "Native process exit $($proc.ExitCode)"}
  $report=Get-Content -Raw -Encoding UTF8 "$instance\replay-probe-result.json" | ConvertFrom-Json
  if($report.status -ne 'pass'){throw "Native fixture failed: $($report.error)"}
  Write-Output "Native process exit $($proc.ExitCode); fixture PASS"
 }
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
