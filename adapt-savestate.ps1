param([Parameter(Mandatory=$true)][string]$Source,[string]$Output='build/SaveStateMod.jar',[string]$Game='D:/SteamLibrary/steamapps/common/SlayTheSpire')
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$workshop=Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop/content/646570'
$mts=Join-Path $workshop '1605060445/ModTheSpire.jar'
$base=Join-Path $workshop '1605833019/BaseMod.jar'
$classes=Join-Path $PSScriptRoot 'build/adapter-tool'
New-Item -ItemType Directory -Force $classes | Out-Null
# JDK 8 predates --release, so the flag list follows the compiler that is actually on PATH.
$javacRelease=@('--release','8')
$javacVersion=''
$strict=$ErrorActionPreference;$ErrorActionPreference='Continue'
try{$javacVersion=(& javac -version 2>&1 | Out-String)}catch{}finally{$ErrorActionPreference=$strict}
if($javacVersion -match 'javac 1\.8'){$javacRelease=@('-source','1.8','-target','1.8')}
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 @javacRelease -cp $mts -d $classes "$PSScriptRoot/tools/AdaptSaveState.java"
if($LASTEXITCODE -ne 0){throw 'Adapter tool compile failed'}
$outputPath=[IO.Path]::GetFullPath($(if([IO.Path]::IsPathRooted($Output)){$Output}else{Join-Path $PSScriptRoot $Output}))
if(-not $outputPath.StartsWith($PSScriptRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Output must stay in state-mod'}
New-Item -ItemType Directory -Force (Split-Path $outputPath -Parent) | Out-Null
& java '-Dfile.encoding=UTF-8' -cp "$classes;$mts" AdaptSaveState $Source $outputPath "$Game/desktop-1.0.jar" $mts $base
if($LASTEXITCODE -ne 0){throw 'SaveState adaptation failed'}
Get-FileHash $outputPath
