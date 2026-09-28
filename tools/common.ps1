$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false

function Write-TextFile([string]$Path, [string]$Text) {
	New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
	[System.IO.File]::WriteAllText($Path, $Text.Replace("`r`n", "`n"), $Utf8NoBom)
}

function Get-ModId {
	$json = [System.IO.File]::ReadAllText((Join-Path $ProjectRoot 'src\main\resources\fabric.mod.json')) | ConvertFrom-Json
	return $json.id
}

function Assert-ContentName([string]$Name) {
	if ($Name -notmatch '^[a-z][a-z0-9_]*$') {
		throw "'$Name' is not a valid name. Use lowercase letters, numbers, and underscores, starting with a letter (e.g. ruby_sword)."
	}
}

function ConvertTo-DisplayName([string]$Name) {
	return (($Name -split '_') | ForEach-Object { if ($_) { $_.Substring(0, 1).ToUpper() + $_.Substring(1) } }) -join ' '
}

function Find-JavaFile([string]$FileName) {
	$file = Get-ChildItem -Path (Join-Path $ProjectRoot 'src\main\java') -Recurse -Filter $FileName | Select-Object -First 1
	if (-not $file) { throw "Could not find $FileName under src\main\java." }
	return $file.FullName
}

function Add-JavaLine([string]$FileName, [string]$Marker, [string]$Code, [string]$ConstantName) {
	$path = Find-JavaFile $FileName
	$text = [System.IO.File]::ReadAllText($path)
	if ($text -match "\b$ConstantName\b") { throw "$ConstantName already exists in $FileName." }

	$lines = [System.Collections.Generic.List[string]]($text -split "`r?`n")
	$index = -1
	for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i].Contains($Marker)) { $index = $i; break } }
	if ($index -lt 0) { throw "Could not find the '$Marker' line in $FileName. Add it back inside the class so the script knows where to put new code." }

	$lines.Insert($index, '')
	$lines.Insert($index, $Code)
	Write-TextFile $path ($lines -join "`n")
}

function Add-LangEntry([string]$ModId, [string]$Key, [string]$Value) {
	$path = Join-Path $ProjectRoot "src\main\resources\assets\$ModId\lang\en_us.json"
	$entries = [ordered]@{}
	if (Test-Path $path) {
		$existing = [System.IO.File]::ReadAllText($path) | ConvertFrom-Json
		foreach ($p in $existing.PSObject.Properties) { $entries[$p.Name] = [string]$p.Value }
	}
	$entries[$Key] = $Value

	$body = ($entries.Keys | ForEach-Object {
		$k = $_.Replace('\', '\\').Replace('"', '\"')
		$v = $entries[$_].Replace('\', '\\').Replace('"', '\"')
		"`t`"$k`": `"$v`""
	}) -join ",`n"
	Write-TextFile $path "{`n$body`n}`n"
}

function New-PlaceholderTexture([string]$Path, [string]$Name, [ValidateSet('item', 'block')][string]$Kind) {
	if (Test-Path $Path) { return }
	Add-Type -AssemblyName System.Drawing
	New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null

	# A color derived from the name, so every new texture is easy to tell apart.
	$hash = [Math]::Abs($Name.GetHashCode())
	$hue = $hash % 360
	function Get-Color([double]$Lightness) {
		$c = (1 - [Math]::Abs(2 * $Lightness - 1)) * 0.6
		$x = $c * (1 - [Math]::Abs((($hue / 60) % 2) - 1))
		$m = $Lightness - $c / 2
		switch ([int][Math]::Floor($hue / 60)) {
			0 { $r = $c; $g = $x; $b = 0 } 1 { $r = $x; $g = $c; $b = 0 } 2 { $r = 0; $g = $c; $b = $x }
			3 { $r = 0; $g = $x; $b = $c } 4 { $r = $x; $g = 0; $b = $c } default { $r = $c; $g = 0; $b = $x }
		}
		return [System.Drawing.Color]::FromArgb(255, [int](($r + $m) * 255), [int](($g + $m) * 255), [int](($b + $m) * 255))
	}
	$dark = Get-Color 0.25; $mid = Get-Color 0.5; $light = Get-Color 0.72

	$bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			if ($Kind -eq 'block') {
				$edge = $x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15
				$speck = (($x * 7 + $y * 13) % 5) -eq 0
				$bmp.SetPixel($x, $y, $(if ($edge) { $dark } elseif ($speck) { $light } else { $mid }))
			} else {
				$d = [Math]::Abs($x - 7.5) + [Math]::Abs($y - 7.5)
				if ($d -le 6) { $bmp.SetPixel($x, $y, $(if ($d -gt 5) { $dark } elseif ($x -lt 8 -and $y -lt 8) { $light } else { $mid })) }
			}
		}
	}
	$bmp.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}
