param([string]$Game='D:\SteamLibrary\steamapps\common\SlayTheSpire',[string]$Instance='state-notes-dev/native-history-ui-01',[string]$Language="ZHS",[int]$Width=1024,[int]$Height=768,[switch]$RealLayout,[switch]$PrepareOnly,[switch]$WaitForExit)
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
Copy-Item "$PSScriptRoot\build\STS1StateNotes.jar" "$instance\mods"
$cp="$instance\desktop-1.0.jar;$instance\ModTheSpire.jar;$instance\mods\*"
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d "$instance\probe-classes" "$PSScriptRoot\test\NativeStatsUIProbe.java"
if($LASTEXITCODE -ne 0){throw 'NativeStatsUIProbe compile failed'}
[IO.File]::WriteAllText("$instance\probe-classes\ModTheSpire.json",'{"modid":"state-notes-stats-ui-probe","name":"Isolated Native State Probe","version":"0.1.0","dependencies":["sts1-state-notes"]}',$utf8)
& jar cf "$instance\mods\NativeStatsUIProbe.jar" -C "$instance\probe-classes" .
if($LASTEXITCODE -ne 0){throw 'NativeStatsUIProbe package failed'}
$latestClasses=(Get-ChildItem "$PSScriptRoot\build" -Directory -Filter 'classes-*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
if($RealLayout){
 foreach($id in @('4de578be-7284-4ec2-91a7-f74560884b40','9461ba2f-6745-4a92-96eb-64df4d940359','bce8e324-e786-41c4-b1db-a79b63e9f83b')){
  New-Item -ItemType Directory -Force "$instance/home/.sts1-state-notes/runs/$id" | Out-Null
  Copy-Item (Join-Path (Resolve-RunPath "state-notes-player-review-20261009") "$id/run.json") "$instance/home/.sts1-state-notes/runs/$id/run.json"
 }
}else{
& java '-Dfile.encoding=UTF-8' -ea -cp "$latestClasses;$cp" sts1state.HistoryIndexCheck "$instance/home/.sts1-state-notes/runs"
if($LASTEXITCODE -ne 0){throw 'UI fixture preparation failed'}
}
[IO.File]::WriteAllText("$instance\info.displayconfig","$Width`n$Height`n60`nfalse`nfalse`ntrue`n",$utf8)
New-Item -ItemType Directory -Force "$instance\betaPreferences" | Out-Null
[IO.File]::WriteAllText("$instance\betaPreferences\STSSaveSlots",'{"DEFAULT_SLOT":"0","PROFILE_NAME":"UI fixture"}',$utf8)
New-Item -ItemType Directory -Force "$instance/runs/IRONCLAD" | Out-Null
foreach($name in @('1791581509.run','1791581539.run','1791582447.run')){Copy-Item "$Game/runs/2_IRONCLAD/$name" "$instance/runs/IRONCLAD/$name"}
if($PrepareOnly){Write-Output "Prepared isolated instance: $instance";return}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try {
 $env:APPDATA="$instance\home";$env:LOCALAPPDATA="$instance\home";$env:TEMP="$instance\temp";$env:TMP="$instance\temp"
 $args=@(('-Dsts1state.realLayout='+$RealLayout.ToString().ToLowerInvariant()),('-Dsts1state.uiLanguage='+$Language),'-Dfile.encoding=UTF-8','-Dsts1state.fixture=true',('"-Duser.home='+$instance+'/home"'),('"-Djava.io.tmpdir='+$instance+'/temp"'),'-jar','ModTheSpire.jar','--skip-launcher','--skip-intro','--mods','basemod,stslib,sts1-state-notes,state-notes-stats-ui-probe')
 $proc=Start-Process -FilePath "$Game\jre\bin\java.exe" -ArgumentList $args -WorkingDirectory $instance -WindowStyle Hidden -RedirectStandardOutput "$instance\stdout.log" -RedirectStandardError "$instance\stderr.log" -PassThru
 [IO.File]::WriteAllText("$instance\pid.txt",[string]$proc.Id,$utf8)
 Write-Output "Isolated State Notes native probe PID $($proc.Id); result: $instance\ui-probe-result.json"
 if($WaitForExit){
  $proc.WaitForExit()
  [IO.File]::WriteAllText("$instance\process-result.json",('{"exit_code":'+$proc.ExitCode+'}'),$utf8)
  if($proc.ExitCode -ne 0){throw "Native process exit $($proc.ExitCode)"}
  $report=Get-Content -Raw -Encoding UTF8 "$instance\ui-probe-result.json" | ConvertFrom-Json
  if($report.status -ne 'pass'){throw "Native fixture failed: $($report.error)"}
  Write-Output "Native process exit $($proc.ExitCode); fixture PASS"
 }
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
