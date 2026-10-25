# Native evidence and third-party inputs live in the sts1 data root, never in this repository.
$StateNotesData=if($env:STS1_DATA){$env:STS1_DATA}else{Join-Path $PSScriptRoot '..\..\data'}
function Resolve-RunPath([string]$Path){if([IO.Path]::IsPathRooted($Path)){$Path}else{Join-Path (Join-Path $StateNotesData 'runs') $Path}}
function Get-CachePath([string]$Name){Join-Path (Join-Path $StateNotesData 'cache\state-notes') $Name}
# Probe instances read preferences from their own folder; zero volume keeps test runs silent without touching the player's game.
function Set-MutedPreferences([string]$Instance){New-Item -ItemType Directory -Force "$Instance\preferences" | Out-Null;[IO.File]::WriteAllText("$Instance\preferences\STSSound",'{"Master Volume":"0.0","Music Volume":"0.0","Sound Volume":"0.0","Ambience On":"false","Mute in Bg":"true"}',[Text.UTF8Encoding]::new($false))}
