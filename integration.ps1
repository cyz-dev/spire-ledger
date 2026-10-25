param([string]$Game='D:\SteamLibrary\steamapps\common\SlayTheSpire',[string]$Instance='state-notes-dev/integration-instance',[switch]$PrepareOnly,[switch]$WithHistory,[switch]$UiOnly,[switch]$MiscOnly,[switch]$WaitForExit)
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
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d "$instance\probe-classes" "$PSScriptRoot\test\NativeProbe.java"
if($LASTEXITCODE -ne 0){throw 'NativeProbe compile failed'}
[IO.File]::WriteAllText("$instance\probe-classes\ModTheSpire.json",'{"modid":"state-notes-probe","name":"Isolated Native State Probe","version":"0.1.0","dependencies":["sts1-state-notes"]}',$utf8)
& jar cf "$instance\mods\NativeProbe.jar" -C "$instance\probe-classes" .
if($LASTEXITCODE -ne 0){throw 'NativeProbe package failed'}
if($WithHistory){
 # Test-only driver is inside this isolated recorder copy; shipping JAR stays unchanged.
 Remove-Item -LiteralPath "$instance\probe-classes\ModTheSpire.json"
 & jar uf "$instance\mods\STS1StateNotes.jar" -C "$instance\probe-classes" sts1state
 if($LASTEXITCODE -ne 0){throw 'Isolated test driver injection failed'}
 Remove-Item -LiteralPath "$instance\mods\NativeProbe.jar"
}
[IO.File]::WriteAllText("$instance\info.displayconfig","1024`n768`n60`nfalse`nfalse`ntrue`n",$utf8)
if($PrepareOnly){Write-Output "Prepared isolated instance: $instance";return}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try {
 $env:APPDATA="$instance\home";$env:LOCALAPPDATA="$instance\home";$env:TEMP="$instance\temp";$env:TMP="$instance\temp"
 $mods=if($WithHistory){'basemod,stslib,sts1-state-notes'}else{'basemod,stslib,sts1-state-notes,state-notes-probe'}
 $uiActor=if($WithHistory){'human'}else{'unknown'}
 $args=@('-Dfile.encoding=UTF-8','-Dsts1state.fixture=true',('-Dsts1state.expectUiActor='+$uiActor),('"-Duser.home='+$instance+'/home"'),('"-Djava.io.tmpdir='+$instance+'/temp"'),'-jar','ModTheSpire.jar','--skip-launcher','--skip-intro','--mods',$mods)
 if($UiOnly){$args=@('-Dsts1state.uiOnly=true')+$args}
 if($MiscOnly){$args=@('-Dsts1state.miscOnly=true')+$args}
 $proc=Start-Process -FilePath "$Game\jre\bin\java.exe" -ArgumentList $args -WorkingDirectory $instance -WindowStyle Hidden -RedirectStandardOutput "$instance\stdout.log" -RedirectStandardError "$instance\stderr.log" -PassThru
 [IO.File]::WriteAllText("$instance\pid.txt",[string]$proc.Id,$utf8)
 Write-Output "Isolated State Notes native probe PID $($proc.Id); result: $instance\probe-result.json"
 if($WaitForExit){
  $proc.WaitForExit()
  [IO.File]::WriteAllText("$instance\process-result.json",('{"exit_code":'+$proc.ExitCode+'}'),$utf8)
  if($proc.ExitCode -ne 0){throw "Native process exit $($proc.ExitCode)"}
  $report=Get-Content -Raw -Encoding UTF8 "$instance\probe-result.json" | ConvertFrom-Json
  if($report.status -ne 'pass'){throw "Native fixture failed: $($report.error)"}
  Write-Output "Native process exit $($proc.ExitCode); fixture PASS"
 }
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
