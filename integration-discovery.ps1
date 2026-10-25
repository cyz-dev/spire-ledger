param([ValidateSet('Vanilla','Adapted','Original')][string]$Dependency='Adapted',[string]$Instance='state-notes-dev/codex-discovery-adapted-01',[string]$Game='D:/SteamLibrary/steamapps/common/SlayTheSpire')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$utf8=[Text.UTF8Encoding]::new($false)
& "$PSScriptRoot/integration-replay.ps1" -Game $Game -Instance $Instance -PrepareOnly
. "$PSScriptRoot/tools/paths.ps1"
$dir=(Resolve-Path (Resolve-RunPath $Instance)).Path
Remove-Item -LiteralPath "$dir/mods/NativeReplayProbe.jar"
if($Dependency -eq 'Vanilla'){Copy-Item (Get-CachePath "STS1StateNotes-dualmod-control.jar") "$dir/mods/STS1StateNotes.jar" -Force}
if($Dependency -eq 'Original'){Copy-Item (Get-CachePath "SaveStateMod-1.3.0.jar") "$dir/mods/SaveStateMod.jar" -Force}
$classes=Join-Path $dir 'discovery-classes'
New-Item -ItemType Directory -Force $classes | Out-Null
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp "$dir/desktop-1.0.jar;$dir/ModTheSpire.jar;$dir/mods/*" -d $classes "$PSScriptRoot/test/NativeDiscoveryProbe.java"
if($LASTEXITCODE -ne 0){throw 'Discovery probe compile failed'}
[IO.File]::WriteAllText("$classes/ModTheSpire.json",'{"modid":"state-notes-discovery-probe","name":"Native Discovery comparison","version":"0.1.0","dependencies":["sts1-state-notes"]}',$utf8)
& jar cf "$dir/mods/NativeDiscoveryProbe.jar" -C $classes .
if($LASTEXITCODE -ne 0){throw 'Discovery probe package failed'}
$mods='basemod,stslib,sts1-state-notes,state-notes-discovery-probe'
if($Dependency -eq 'Original'){$mods='basemod,stslib,SaveStateMod,sts1-state-notes,state-notes-discovery-probe'}
$oldApp=$env:APPDATA;$oldLocal=$env:LOCALAPPDATA;$oldTemp=$env:TEMP;$oldTmp=$env:TMP
try{
 $env:APPDATA="$dir/home";$env:LOCALAPPDATA="$dir/home";$env:TEMP="$dir/temp";$env:TMP="$dir/temp"
 $arguments=@('-Dfile.encoding=UTF-8',('"-Duser.home='+$dir+'/home"'),('"-Djava.io.tmpdir='+$dir+'/temp"'),'-jar','ModTheSpire.jar','--skip-launcher','--skip-intro','--mods',$mods)
 $proc=Start-Process -FilePath "$Game/jre/bin/java.exe" -ArgumentList $arguments -WorkingDirectory $dir -WindowStyle Hidden -RedirectStandardOutput "$dir/stdout.log" -RedirectStandardError "$dir/stderr.log" -PassThru
 $proc.WaitForExit();[IO.File]::WriteAllText("$dir/process-result.json",('{"exit_code":'+$proc.ExitCode+'}'),$utf8)
 if($proc.ExitCode -ne 0){throw "Discovery process exit $($proc.ExitCode)"}
 $report=Get-Content -Raw -Encoding UTF8 "$dir/discovery-result.json" | ConvertFrom-Json
 if($report.status -ne 'pass'){throw $report.error}
 Write-Output "$Dependency Discovery native PASS; exit $($proc.ExitCode)"
}finally{$env:APPDATA=$oldApp;$env:LOCALAPPDATA=$oldLocal;$env:TEMP=$oldTemp;$env:TMP=$oldTmp}
