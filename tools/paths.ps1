# Native evidence and third-party inputs live in the sts1 data root, never in this repository.
$StateNotesData=if($env:STS1_DATA){$env:STS1_DATA}else{Join-Path $PSScriptRoot '..\..\data'}
function Resolve-RunPath([string]$Path){if([IO.Path]::IsPathRooted($Path)){$Path}else{Join-Path (Join-Path $StateNotesData 'runs') $Path}}
function Get-CachePath([string]$Name){Join-Path (Join-Path $StateNotesData 'cache\state-notes') $Name}
# Probe instances read preferences from their own folder; zero volume keeps test runs silent without touching the player's game.
# The 12-18-2022 build reads betaPreferences; preferences is kept for other builds.
function Set-MutedPreferences([string]$Instance){foreach($dir in 'preferences','betaPreferences'){New-Item -ItemType Directory -Force "$Instance\$dir" | Out-Null;[IO.File]::WriteAllText("$Instance\$dir\STSSound",'{"Master Volume":"0.0","Music Volume":"0.0","Sound Volume":"0.0","Ambience On":"false","Mute in Bg":"true"}',[Text.UTF8Encoding]::new($false))}}
$HeadlessRunner=Join-Path $PSScriptRoot 'headless.sh'
# shortcut: assumes WSL's default /mnt/<drive> automount; switch to wslpath if a machine changes it.
function ConvertTo-WslPath([string]$Path){$full=[IO.Path]::GetFullPath($Path);'/mnt/'+$full.Substring(0,1).ToLowerInvariant()+$full.Substring(2).Replace('\','/')}
# One launch for every probe: Windows runs the game's JRE hidden; -Headless runs the same properties in WSL under Xvfb with no audio, so nothing reaches the desktop or speakers.
function Start-Probe([string]$Game,[string]$Instance,[string[]]$Properties,[string]$Mods,[switch]$Headless){
 $launch=@{WorkingDirectory=$Instance;WindowStyle='Hidden';RedirectStandardOutput="$Instance\stdout.log";RedirectStandardError="$Instance\stderr.log";PassThru=$true}
 if(-not $Headless){return Start-Process -FilePath "$Game\jre\bin\java.exe" -ArgumentList ($Properties+@(('"-Duser.home='+$Instance+'/home"'),('"-Djava.io.tmpdir='+$Instance+'/temp"'),'-jar','ModTheSpire.jar','--skip-launcher','--skip-intro','--mods',$Mods)) @launch}
 if(-not(Get-Command wsl.exe -ErrorAction SilentlyContinue)){throw 'Headless needs WSL with an Ubuntu distro: wsl --install -d Ubuntu-24.04'}
 $runner=@('--exec','env',('STS1_DATA='+(ConvertTo-WslPath $StateNotesData)),'bash',(ConvertTo-WslPath $HeadlessRunner))
 $check=& wsl.exe @runner --check 2>&1
 if($LASTEXITCODE -ne 0){throw "Headless launch unavailable (exit $LASTEXITCODE):`n$($check -join "`n")"}
 $linux=ConvertTo-WslPath $Instance
 # Windows paths inside -D values (replaySource) must become WSL paths for the Linux JVM.
 $props=@($Properties|ForEach-Object{$_.Trim('"') -replace '^(-D[^=]+=)([A-Za-z]:[\\/].*)$',{$_.Groups[1].Value+(ConvertTo-WslPath $_.Groups[2].Value)}})+@("-Duser.home=$linux/home","-Djava.io.tmpdir=$linux/temp")
 Start-Process -FilePath 'wsl.exe' -ArgumentList ($runner+@($linux,$Mods)+@($props|ForEach-Object{if($_ -match '\s'){'"'+$_+'"'}else{$_}})) @launch
}
