param([string]$Game='C:\Program Files (x86)\Steam\steamapps\common\SlayTheSpire',[string]$Instance='spire-ledger-dev/native-options-ui-01',[string]$Language="ZHS",[int]$Width=1024,[int]$Height=768,[switch]$PrepareOnly,[switch]$WaitForExit,[switch]$Headless)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$utf8=[Text.UTF8Encoding]::new($false)
. "$PSScriptRoot/tools/paths.ps1"
$instance=Resolve-RunPath $Instance
$workshop=Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop\content\646570'
New-Item -ItemType Directory -Force "$instance\mods","$instance\home","$instance\preferences","$instance\temp","$instance\probe-classes","$instance\runs" | Out-Null
Set-MutedPreferences $instance
if(-not(Test-Path "$instance\desktop-1.0.jar")){Copy-Item "$Game\desktop-1.0.jar" $instance}
Copy-Item "$workshop\1605060445\ModTheSpire.jar" $instance
Copy-Item "$workshop\1605833019\BaseMod.jar" "$instance\mods"
Copy-Item "$workshop\1609158507\StSLib.jar" "$instance\mods"
Copy-Item "$PSScriptRoot\build\SpireLedger.jar" "$instance\mods"
$cp="$instance\desktop-1.0.jar;$instance\ModTheSpire.jar;$instance\mods\*"
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d "$instance\probe-classes" "$PSScriptRoot\test\StatsOptionsProbe.java"
if($LASTEXITCODE -ne 0){throw 'StatsOptionsProbe compile failed'}
[IO.File]::WriteAllText("$instance\probe-classes\ModTheSpire.json",'{"modid":"spire-ledger-stats-options-probe","name":"Native Rate Options Probe","version":"0.1.0","dependencies":["spire-ledger"]}',$utf8)
& jar cf "$instance\mods\NativeStatsOptionsProbe.jar" -C "$instance\probe-classes" .
if($LASTEXITCODE -ne 0){throw 'StatsOptionsProbe package failed'}
# Real slot-0 native records only: the three base classes, read-only copies, never the player's install.
foreach($character in @('IRONCLAD','DEFECT','WATCHER')){
 New-Item -ItemType Directory -Force "$instance\runs\$character" | Out-Null
 Copy-Item "$Game\runs\$character\*.run" "$instance\runs\$character"
}
[IO.File]::WriteAllText("$instance\info.displayconfig","$Width`n$Height`n60`nfalse`nfalse`ntrue`n",$utf8)
New-Item -ItemType Directory -Force "$instance\betaPreferences" | Out-Null
[IO.File]::WriteAllText("$instance\betaPreferences\STSSaveSlots",'{"DEFAULT_SLOT":"0","PROFILE_NAME":"Rate options"}',$utf8)
if($PrepareOnly){Write-Output "Prepared isolated instance: $instance";return}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try {
 $env:APPDATA="$instance\home";$env:LOCALAPPDATA="$instance\home";$env:TEMP="$instance\temp";$env:TMP="$instance\temp"
 $args=@(('-Dspireledger.uiLanguage='+$Language),'-Dfile.encoding=UTF-8','-Dspireledger.fixture=true')
 $proc=Start-Probe $Game $instance $args 'basemod,stslib,spire-ledger,spire-ledger-stats-options-probe' -Headless:$Headless
 [IO.File]::WriteAllText("$instance\pid.txt",[string]$proc.Id,$utf8)
 Write-Output "Isolated rate options probe PID $($proc.Id); result: $instance\ui-options-result.json"
 if($WaitForExit){
  $proc.WaitForExit()
  $code=try{$proc.ExitCode}catch{$null}
  [IO.File]::WriteAllText("$instance\process-result.json",('{"exit_code":'+$(if($null -eq $code){'null'}else{$code})+'}'),$utf8)
  if($null -ne $code -and $code -ne 0){throw "Native process exit $code"}
  $report=Get-Content -Raw -Encoding UTF8 "$instance\ui-options-result.json" | ConvertFrom-Json
  if($report.status -ne 'pass'){throw "Native rate options failed: $($report.error)"}
  Write-Output "Native process exit $(if($null -eq $code){'unknown'}else{$code}); rate options PASS"
 }
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
