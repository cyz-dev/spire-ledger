param([string]$Game='D:\SteamLibrary\steamapps\common\SlayTheSpire',[string]$Instance='spire-ledger-dev/native-stats-01',[switch]$PrepareOnly,[switch]$WaitForExit,[switch]$Headless)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$utf8=[Text.UTF8Encoding]::new($false)
. "$PSScriptRoot/tools/paths.ps1"
$instance=Resolve-RunPath $Instance
$workshop=Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop\content\646570'
New-Item -ItemType Directory -Force "$instance\mods","$instance\home","$instance\preferences","$instance\temp","$instance\probe-classes" | Out-Null
Set-MutedPreferences $instance
if(-not(Test-Path "$instance\desktop-1.0.jar")){Copy-Item "$Game\desktop-1.0.jar" $instance}
Copy-Item "$workshop\1605060445\ModTheSpire.jar" $instance
Copy-Item "$workshop\1605833019\BaseMod.jar" "$instance\mods"
Copy-Item "$workshop\1609158507\StSLib.jar" "$instance\mods"
Copy-Item "$PSScriptRoot\build\SpireLedger.jar" "$instance\mods"
$cp="$instance\desktop-1.0.jar;$instance\ModTheSpire.jar;$instance\mods\*"
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d "$instance\probe-classes" "$PSScriptRoot\test\NativeStatsProbe.java"
if($LASTEXITCODE -ne 0){throw 'NativeStatsProbe compile failed'}
[IO.File]::WriteAllText("$instance\probe-classes\ModTheSpire.json",'{"modid":"spire-ledger-stats-probe","name":"Isolated Native State Probe","version":"0.1.0","dependencies":["spire-ledger"]}',$utf8)
& jar cf "$instance\mods\NativeStatsProbe.jar" -C "$instance\probe-classes" .
if($LASTEXITCODE -ne 0){throw 'NativeStatsProbe package failed'}
[IO.File]::WriteAllText("$instance\info.displayconfig","1024`n768`n60`nfalse`nfalse`ntrue`n",$utf8)
New-Item -ItemType Directory -Force "$instance\betaPreferences" | Out-Null
[IO.File]::WriteAllText("$instance\betaPreferences\STSSaveSlots",'{"DEFAULT_SLOT":"0","PROFILE_NAME":"UI fixture"}',$utf8)
if($PrepareOnly){Write-Output "Prepared isolated instance: $instance";return}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try {
 $env:APPDATA="$instance\home";$env:LOCALAPPDATA="$instance\home";$env:TEMP="$instance\temp";$env:TMP="$instance\temp"
 $proc=Start-Probe $Game $instance @('-Dfile.encoding=UTF-8','-Dspireledger.fixture=true') 'basemod,stslib,spire-ledger,spire-ledger-stats-probe' -Headless:$Headless
 [IO.File]::WriteAllText("$instance\pid.txt",[string]$proc.Id,$utf8)
 Write-Output "Isolated Spire Ledger native probe PID $($proc.Id); result: $instance\stats-probe-result.json"
 if($WaitForExit){
  $proc.WaitForExit()
  [IO.File]::WriteAllText("$instance\process-result.json",('{"exit_code":'+$proc.ExitCode+'}'),$utf8)
  if($proc.ExitCode -ne 0){throw "Native process exit $($proc.ExitCode)"}
  $report=Get-Content -Raw -Encoding UTF8 "$instance\stats-probe-result.json" | ConvertFrom-Json
  if($report.status -ne 'pass'){throw "Native fixture failed: $($report.error)"}
  Write-Output "Native process exit $($proc.ExitCode); fixture PASS"
 }
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
