param([string]$Game='D:\SteamLibrary\steamapps\common\SlayTheSpire',[switch]$Check,[string]$SaveStateSource=$env:STS1_SAVESTATE_JAR)
. "$PSScriptRoot/tools/paths.ps1"
if(-not $SaveStateSource){$SaveStateSource=Get-CachePath 'SaveStateMod-1.3.0.jar'}
if(-not (Test-Path $SaveStateSource)){throw "SaveStateMod 1.3.0 not found at $SaveStateSource; pass -SaveStateSource or set STS1_SAVESTATE_JAR"}
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$utf8=[Text.UTF8Encoding]::new($false)
$workshop=Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop\content\646570'
$cp="$Game\desktop-1.0.jar;$workshop\1605060445\ModTheSpire.jar;$workshop\1605833019\BaseMod.jar"
$out=Join-Path $PSScriptRoot 'build'
$classes=Join-Path $out ('classes-'+[Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $classes | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot\src" -Recurse -Filter *.java)
$list=Join-Path $out 'sources.txt'
[IO.File]::WriteAllLines($list,@($sources | ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'}),$utf8)
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp $cp -d $classes "@$list"
if($LASTEXITCODE -ne 0){throw 'javac failed'}
# spireledger.stats reads only the record schema: compile it alone against the game's own Gson, so any game or mod class reference fails the build.
$gson=Join-Path $out 'gson'
New-Item -ItemType Directory -Force $gson | Out-Null;Push-Location $gson;try{& jar xf "$Game\desktop-1.0.jar" com/google/gson}finally{Pop-Location}
if(-not(Test-Path "$gson\com\google\gson\JsonObject.class")){throw 'Gson not found in desktop-1.0.jar'}
# The same classes run the offline TinyCat export: java -cp "build\gson;build\stats-classes" spireledger.stats.Export <runs> <out.json> <slot> [run-list.json]
$statsClasses=Join-Path $out 'stats-classes';if(Test-Path $statsClasses){Remove-Item -Recurse -Force $statsClasses};New-Item -ItemType Directory -Force $statsClasses | Out-Null;Copy-Item "$PSScriptRoot\ModTheSpire.json" $statsClasses
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -implicit:none -cp $gson -d $statsClasses @(Get-ChildItem "$PSScriptRoot\src\spireledger\stats" -Filter *.java | ForEach-Object FullName)
if($LASTEXITCODE -ne 0){throw 'spireledger.stats must compile with Gson alone'}
& "$PSScriptRoot/adapt-savestate.ps1" -Source $(if([IO.Path]::IsPathRooted($SaveStateSource)){$SaveStateSource}else{Join-Path $PSScriptRoot $SaveStateSource}) -Output 'build/SaveStateMod.jar' -Game $Game
& javac -encoding UTF-8 --release 8 -cp "$workshop/1605060445/ModTheSpire.jar" -d "$out/adapter-tool" "$PSScriptRoot/tools/BundleSaveState.java"
if($LASTEXITCODE -ne 0){throw 'Bundle tool compile failed'}
& java '-Dfile.encoding=UTF-8' -cp "$out/adapter-tool;$workshop/1605060445/ModTheSpire.jar" BundleSaveState "$out/SaveStateMod.jar" $classes "$Game/desktop-1.0.jar" "$workshop/1605060445/ModTheSpire.jar" "$workshop/1605833019/BaseMod.jar"
if($LASTEXITCODE -ne 0){throw 'Bundle failed'}
Copy-Item "$PSScriptRoot/NOTICE.txt" $classes
Copy-Item "$PSScriptRoot/LICENSE" $classes
Copy-Item "$PSScriptRoot\ModTheSpire.json" $classes
Copy-Item "$PSScriptRoot\viewer\review.html" $classes
& jar cf "$out\SpireLedger.jar" -C $classes .
if($LASTEXITCODE -ne 0){throw 'jar failed'}
if($Check){
  $tests=@(Get-ChildItem "$PSScriptRoot\test" -Filter *.java | Where-Object Name -ne "NativeReplayProbe.java")
  [IO.File]::WriteAllLines($list,@($tests | ForEach-Object {'"'+$_.FullName.Replace('\','/')+'"'}),$utf8)
  & javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -cp "$classes;$cp" -d $classes "@$list"
  if($LASTEXITCODE -ne 0){throw 'test compile failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.RecorderCheck (Join-Path $out ('recorder-checks-'+[Guid]::NewGuid().ToString('N')))
  if($LASTEXITCODE -ne 0){throw 'RecorderCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.HistoryIndexCheck (Join-Path $out ('history-checks-'+[Guid]::NewGuid().ToString('N')))
  if($LASTEXITCODE -ne 0){throw 'HistoryIndexCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.HistoryLayoutCheck
  if($LASTEXITCODE -ne 0){throw 'HistoryLayoutCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.StatsCheck (Join-Path $out ('stats-checks-'+[Guid]::NewGuid().ToString('N'))) "$PSScriptRoot\src\spireledger\stats"
  if($LASTEXITCODE -ne 0){throw 'StatsCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.SummaryCheck (Join-Path $out ('summary-checks-'+[Guid]::NewGuid().ToString('N'))) "$PSScriptRoot\samples"
  if($LASTEXITCODE -ne 0){throw 'SummaryCheck failed'}
  & java '-Dfile.encoding=UTF-8' ("-Duser.home="+(Join-Path $out ('diagnostic-check-'+[Guid]::NewGuid().ToString('N')))) -ea -cp "$classes;$cp" spireledger.DiagnosticReportCheck
  if($LASTEXITCODE -ne 0){throw 'DiagnosticReportCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.TurnFilesCheck (Join-Path $out ('turn-checks-'+[Guid]::NewGuid().ToString('N')))
  if($LASTEXITCODE -ne 0){throw 'TurnFilesCheck failed'}
  & java '-Dfile.encoding=UTF-8' -ea -cp "$classes;$cp" spireledger.RelicStatsCheck
  if($LASTEXITCODE -ne 0){throw 'RelicStatsCheck failed'}
}
Get-Item "$out\SpireLedger.jar" | Select-Object FullName,Length
