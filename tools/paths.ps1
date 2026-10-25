# Native evidence and third-party inputs live in the sts1 data root, never in this repository.
$StateNotesData=if($env:STS1_DATA){$env:STS1_DATA}else{Join-Path $PSScriptRoot '..\..\data'}
function Resolve-RunPath([string]$Path){if([IO.Path]::IsPathRooted($Path)){$Path}else{Join-Path (Join-Path $StateNotesData 'runs') $Path}}
function Get-CachePath([string]$Name){Join-Path (Join-Path $StateNotesData 'cache\state-notes') $Name}
