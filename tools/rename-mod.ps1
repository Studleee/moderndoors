param(
	[Parameter(Mandatory = $true, Position = 0)][string]$DisplayName,
	[Parameter(Position = 1)][string]$Id
)

trap { Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
. (Join-Path $PSScriptRoot 'common.ps1')

$oldId = Get-ModId
$modJson = [System.IO.File]::ReadAllText((Join-Path $ProjectRoot 'src\main\resources\fabric.mod.json')) | ConvertFrom-Json
$oldDisplayName = $modJson.name
$oldClass = ($modJson.entrypoints.main[0] -split '\.')[-1]

if (-not $Id) { $Id = ($DisplayName.ToLower() -replace '[^a-z0-9]', '') }
if ($Id -notmatch '^[a-z][a-z0-9_]{1,63}$') {
	throw "Mod id '$Id' is not valid. Use 2-64 lowercase letters, numbers, or underscores, starting with a letter."
}
if ($Id -in @('minecraft', 'fabric', 'java')) { throw "'$Id' is reserved. Pick a different id." }

$newClass = (($DisplayName -split '[^A-Za-z0-9]') | Where-Object { $_ } | ForEach-Object { $_.Substring(0, 1).ToUpper() + $_.Substring(1) }) -join ''
if ($newClass -notmatch '^[A-Za-z]') { $newClass = 'Mod' + $newClass }

Write-Host "Renaming '$oldDisplayName' ($oldId) to '$DisplayName' ($Id), main class $newClass"

$textFiles = Get-ChildItem -Path $ProjectRoot -Recurse -File -Include *.java, *.json, *.gradle, *.properties, *.md |
	Where-Object { $_.FullName -notmatch '\\(build|\.gradle|run|gradle)\\' }

$oldNameField = [regex]::Escape("`"name`": `"$oldDisplayName`"")
$newNameField = "`"name`": `"$DisplayName`"".Replace('$', '$$')

foreach ($file in $textFiles) {
	$text = [System.IO.File]::ReadAllText($file.FullName)
	$updated = $text -creplace "\b$oldClass\b", $newClass
	$updated = $updated -creplace "\b$($oldClass)Client\b", "$($newClass)Client"
	$updated = $updated -creplace $oldNameField, $newNameField
	$updated = $updated -creplace "\b$oldId\b", $Id
	if ($file.Extension -eq '.md') {
		$updated = $updated -creplace [regex]::Escape($oldDisplayName), $DisplayName.Replace('$', '$$')
	}
	if ($updated -ne $text) { Write-TextFile $file.FullName $updated }
}

foreach ($rel in @('src\main\java\com', 'src\client\java\com', 'src\main\resources\assets', 'src\main\resources\data')) {
	$old = Join-Path $ProjectRoot "$rel\$oldId"
	if (Test-Path $old) { Rename-Item $old $Id }
}

$mainFile = Get-ChildItem -Path (Join-Path $ProjectRoot 'src\main\java') -Recurse -Filter "$oldClass.java" | Select-Object -First 1
if ($mainFile) { Rename-Item $mainFile.FullName "$newClass.java" }
$clientFile = Get-ChildItem -Path (Join-Path $ProjectRoot 'src\client\java') -Recurse -Filter "$($oldClass)Client.java" | Select-Object -First 1
if ($clientFile) { Rename-Item $clientFile.FullName "$($newClass)Client.java" }

Add-LangEntry $Id "creativeTab.$Id" $DisplayName

Write-Host "Done. Your mod is now '$DisplayName' with id '$Id'." -ForegroundColor Green
