# Builds the offline driver against a TinyCat jar and exports the TinyCat baseline.
# The game root holding runs/ is used read-only as the working directory (TinyCat resolves runs/ via Gdx.files.local).
param(
  [Parameter(Mandatory)][string]$TinyCatJar,
  [Parameter(Mandatory)][string]$GameRoot,
  [Parameter(Mandatory)][string]$OutDir,
  [string]$Version = '4.5.6',
  [string]$Commit = 'unknown',
  [int[]]$Slots = @(0),
  [string]$Game = 'D:\SteamLibrary\steamapps\common\SlayTheSpire'
)
$ErrorActionPreference = 'Stop'
$workshop = Join-Path (Split-Path (Split-Path $Game -Parent) -Parent) 'workshop/content/646570'
$cp = "$TinyCatJar;$Game/desktop-1.0.jar;$workshop/1605833019/BaseMod.jar;$workshop/1605060445/ModTheSpire.jar"
$classes = Join-Path ([IO.Path]::GetTempPath()) "tinycat-compare-driver"
New-Item -ItemType Directory -Force $classes | Out-Null
& javac '-J-Dfile.encoding=UTF-8' -encoding UTF-8 --release 8 -proc:none -nowarn -cp $cp -d $classes "$PSScriptRoot/TinyCatBaselineExport.java"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
New-Item -ItemType Directory -Force $OutDir | Out-Null
Push-Location $GameRoot
try {
  foreach ($s in $Slots) {
    & java '-Dfile.encoding=UTF-8' -cp "$classes;$cp" TinyCatBaselineExport $s (Join-Path $OutDir "baseline-slot$s.json") $Version $Commit
    if ($LASTEXITCODE -ne 0) { throw "export failed for slot $s" }
  }
} finally { Pop-Location }
