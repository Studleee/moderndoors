# Generates every Modern Doors texture and JSON file: the door panel textures (for the moving 3D doors), handles,
# item icons, block states, loot tables, recipes, recipe unlocks, tool tags, and names.
# Usage: powershell -ExecutionPolicy Bypass -File tools\gen-doors.ps1
$ErrorActionPreference = 'Stop'
trap { Write-Host "Error: $_"; exit 1 }
Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot '..\src\main\resources'
$assets = Join-Path $root 'assets\moderndoors'
$data = Join-Path $root 'data\moderndoors'
$tex = Join-Path $assets 'textures'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Everything under these is generated, so start clean and leave nothing behind from older versions.
foreach ($dir in @('blockstates', 'items', 'lang', 'models', 'textures')) { Remove-Item -Recurse -Force (Join-Path $assets $dir) -ErrorAction SilentlyContinue }
Remove-Item -Recurse -Force $data, (Join-Path $root 'data\minecraft') -ErrorAction SilentlyContinue

function Write-Json($path, $text) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	[System.IO.File]::WriteAllText($path, ($text.Trim() -replace "`r`n", "`n") + "`n", $utf8)
}

function Save-Bitmap($bmp, $path) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}

# 'rrggbb' or 'aarrggbb'
function Color($hex) {
	if ($hex.Length -eq 6) { $hex = 'ff' + $hex }
	return [System.Drawing.Color]::FromArgb([Convert]::ToInt32($hex, 16))
}

# Halfway between two 'rrggbb' colors.
function Mix($a, $b) {
	$out = ''
	for ($i = 0; $i -lt 6; $i += 2) {
		$v = ([Convert]::ToInt32($a.Substring($i, 2), 16) + [Convert]::ToInt32($b.Substring($i, 2), 16)) / 2
		$out += ([int]$v).ToString('x2')
	}
	return $out
}

function Rows($map) { return , [string[]]($map.Trim("`r", "`n") -split "`r?`n") }

# Draws a text map: one character per pixel, '.' is see-through, $palette maps characters to colors.
function Draw($rows, $palette, $path, $scale = 1) {
	$h = $rows.Count; $w = $rows[0].Length
	$bmp = New-Object System.Drawing.Bitmap ($w * $scale), ($h * $scale), ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	for ($y = 0; $y -lt $h; $y++) {
		for ($x = 0; $x -lt $w; $x++) {
			$ch = [string]$rows[$y][$x]
			if ($ch -eq '.' -or -not $palette.ContainsKey($ch)) { continue }
			$c = Color $palette[$ch]
			for ($dy = 0; $dy -lt $scale; $dy++) { for ($dx = 0; $dx -lt $scale; $dx++) { $bmp.SetPixel($x * $scale + $dx, $y * $scale + $dy, $c) } }
		}
	}
	Save-Bitmap $bmp $path
}

# A rectangle of speckled color, for handles and break particles.
function Noise-Texture($dark, $mid, $light, $w, $h, $path, $seed) {
	$rand = New-Object System.Random $seed
	$bmp = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	for ($y = 0; $y -lt $h; $y++) {
		for ($x = 0; $x -lt $w; $x++) {
			$roll = $rand.NextDouble()
			$hex = if ($roll -lt 0.15) { $light } elseif ($roll -lt 0.3) { $dark } else { $mid }
			$bmp.SetPixel($x, $y, (Color $hex))
		}
	}
	Save-Bitmap $bmp $path
}

# ---- Materials ----
# id, name, style (wood / metal / concrete), dark, mid, light, crafting ingredient
$materials = @(
	@('oak', 'Oak', 'wood', '8b6d3f', 'a2824e', 'b8945f', 'minecraft:oak_planks'),
	@('spruce', 'Spruce', 'wood', '5a4024', '72522f', '86613a', 'minecraft:spruce_planks'),
	@('birch', 'Birch', 'wood', 'a8955f', 'c4b07b', 'd7c98f', 'minecraft:birch_planks'),
	@('dark_oak', 'Dark Oak', 'wood', '3a2610', '4a3218', '5e4224', 'minecraft:dark_oak_planks'),
	@('cherry', 'Cherry', 'wood', 'c4907f', 'e2b3a3', 'f0c7ba', 'minecraft:cherry_planks'),
	@('pale_oak', 'Pale Oak', 'wood', 'cfc6bb', 'e8e1d9', 'f5f1ec', 'minecraft:pale_oak_planks'),
	@('black_steel', 'Black Steel', 'metal', '161616', '262626', '3a3a3a', 'moderndoors:black_steel_door_frame'),
	@('white_aluminum', 'White Aluminum', 'metal', 'c4c4c4', 'e6e6e6', 'ffffff', 'moderndoors:white_aluminum_door_frame'),
	@('concrete', 'Concrete', 'concrete', '8f8f88', 'a9a9a2', 'bdbdb6', 'minecraft:light_gray_concrete'),
	@('dark_concrete', 'Dark Concrete', 'concrete', '444447', '555558', '66666a', 'minecraft:gray_concrete')
)
# id, name
$kinds = @(
	@('pivot', 'Pivot Door'),
	@('folding', 'Folding Glass Door'),
	@('sliding', 'Sliding Glass Door')
)
$glassHex = '66cfe8f2'
$streakHex = '99ffffff'

# ---- Door textures ----
# Doors can be any size, so they're drawn one block-sized tile at a time with plain frame bars around each panel.
# A tile texture is laid out the way the game wraps a texture around a box: a strip of edges along the top, then
# the side, front, other side, and back faces below it. The face art is flipped upside down because the tiles are
# drawn with their texture's top at the bottom.
function Panel-Texture($w, $h, $d, $texW, $texH, $face, $edge, $path) {
	$bmp = New-Object System.Drawing.Bitmap $texW, $texH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	$edgeColor = Color $edge
	for ($y = 0; $y -lt $d + $h; $y++) {
		for ($x = 0; $x -lt 2 * $d + 2 * $w; $x++) {
			if ($y -lt $d -and ($x -lt $d -or $x -ge $d + 2 * $w)) { continue }
			$bmp.SetPixel($x, $y, $edgeColor)
		}
	}
	for ($y = 0; $y -lt $h; $y++) {
		for ($x = 0; $x -lt $w; $x++) {
			$c = Color $face[$h - 1 - $y][$x]
			$bmp.SetPixel($d + $x, $d + $y, $c)
			$bmp.SetPixel(2 * $d + $w + $x, $d + $y, $c)
		}
	}
	Save-Bitmap $bmp $path
}

# One block of door front, 16x16, made to repeat seamlessly: [row][column].
function Tile-Face($style, $glass, $dark, $mid, $light, $seed) {
	$rand = New-Object System.Random $seed
	$shade = Mix $dark $mid
	$face = @()
	for ($y = 0; $y -lt 16; $y++) {
		$row = New-Object string[] 16
		for ($x = 0; $x -lt 16; $x++) {
			$roll = $rand.NextDouble()
			if ($glass) {
				# A short diagonal glint in each pane.
				$glint = $y -ge 3 -and $y -le 7 -and ($x + $y -eq 12 -or $x + $y -eq 14)
				$hex = if ($glint) { $streakHex } else { $glassHex }
			} elseif ($style -eq 'wood') {
				# Vertical boards with dark seams, two to a block.
				if ($x % 8 -eq 0) { $hex = $dark }
				elseif ($roll -lt 0.12) { $hex = $light }
				elseif ($roll -lt 0.24) { $hex = $shade }
				else { $hex = $mid }
			} else {
				# Smooth concrete with a faint joint between blocks.
				if ($y -eq 0) { $hex = $shade }
				elseif ($roll -lt 0.1) { $hex = $light }
				elseif ($roll -lt 0.2) { $hex = $shade }
				else { $hex = $mid }
			}
			$row[$x] = $hex
		}
		$face += , $row
	}
	return , $face
}

function Solid-Texture($hex, $path) {
	$bmp = New-Object System.Drawing.Bitmap 64, 32, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	$c = Color $hex
	for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 64; $x++) { $bmp.SetPixel($x, $y, $c) } }
	Save-Bitmap $bmp $path
}

# Glass doors: glass tiles 1 pixel thick with frame bars in the material's color. Solid doors (wood and concrete
# pivot doors): 3 pixel thick tiles with darker trim.
$seed = 1
foreach ($m in $materials) {
	$mid, $mname, $style, $dark, $midHex, $light, $ingredient = $m
	if ($style -ne 'concrete') {
		Panel-Texture 16 16 1 64 32 (Tile-Face $style $true $dark $midHex $light ($seed++)) $midHex (Join-Path $tex "entity\door\tile\${mid}_glass.png")
		Solid-Texture $midHex (Join-Path $tex "entity\door\frame\${mid}_glass.png")
	}
	if ($style -ne 'metal') {
		Panel-Texture 16 16 3 64 32 (Tile-Face $style $false $dark $midHex $light ($seed++)) $dark (Join-Path $tex "entity\door\tile\${mid}_solid.png")
		Solid-Texture $dark (Join-Path $tex "entity\door\frame\${mid}_solid.png")
	}
}

Noise-Texture '111111' '1c1c1c' '2e2e2e' 64 32 (Join-Path $tex 'entity\door\handle_black.png') 201
Noise-Texture 'a8acb0' 'c8ccd0' 'e8ecf0' 64 32 (Join-Path $tex 'entity\door\handle_silver.png') 202
Noise-Texture 'a8842f' 'c9a24a' 'e6c46a' 64 32 (Join-Path $tex 'entity\door\handle_brass.png') 203
foreach ($m in $materials) { Noise-Texture $m[3] $m[4] $m[5] 16 16 (Join-Path $tex "block\$($m[0])_door.png") ($seed++) }

# ---- Item icons ----
# F frame, M fill, L lighter fill, H handle.
function Icon($kindId) {
	$rows = @()
	for ($y = 0; $y -lt 16; $y++) { $rows += '................' }
	switch ($kindId) {
		'pivot' {
			for ($y = 0; $y -lt 16; $y++) {
				for ($x = 3; $x -le 12; $x++) {
					$ch = if ($y -eq 0 -or $y -eq 15 -or $x -eq 3 -or $x -eq 12) { 'F' } elseif ($x + $y -eq 7 -or $x + $y -eq 8) { 'L' } else { 'M' }
					if ($x -eq 10 -and $y -ge 5 -and $y -le 9) { $ch = 'H' }
					Set-Pixel $rows $x $y $ch
				}
			}
		}
		'folding' {
			for ($y = 0; $y -lt 16; $y++) {
				for ($x = 0; $x -lt 16; $x++) {
					$ch = if ($y -eq 0 -or $y -eq 15 -or $x % 5 -eq 0) { 'F' } elseif ($x -gt 5 -and $x -lt 10) { 'L' } else { 'M' }
					if ($x -eq 13 -and $y -ge 6 -and $y -le 9) { $ch = 'H' }
					Set-Pixel $rows $x $y $ch
				}
			}
		}
		'sliding' {
			foreach ($panel in @(@(0, 1, 'M'), @(7, 0, 'L'))) {
				$left, $top, $fill = $panel
				for ($y = $top; $y -lt $top + 15; $y++) {
					for ($x = $left; $x -lt $left + 9; $x++) {
						$ch = if ($y -eq $top -or $y -eq $top + 14 -or $x -eq $left -or $x -eq $left + 8) { 'F' } else { $fill }
						Set-Pixel $rows $x $y $ch
					}
				}
			}
			for ($y = 5; $y -le 9; $y++) { Set-Pixel $rows 13 $y 'H' }
		}
	}
	return , $rows
}

function Set-Pixel($rows, $x, $y, $ch) {
	$row = $rows[$y].ToCharArray(); $row[$x] = $ch; $rows[$y] = -join $row
}

# ---- Every door: icon, models, drops, recipe, name ----
$lang = [ordered]@{ 'creativeTab.moderndoors' = 'Modern Doors' }
$axe = @(); $pickaxe = @()
foreach ($kind in $kinds) {
	$kindId, $kindName = $kind
	foreach ($m in $materials) {
		$mid, $mname, $style, $dark, $midHex, $light, $ingredient = $m
		if ($style -eq 'concrete' -and $kindId -ne 'pivot') { continue }
		$id = "${mid}_${kindId}_door"
		$glass = $style -eq 'metal' -or ($style -eq 'wood' -and $kindId -ne 'pivot')
		$lang["block.moderndoors.$id"] = "$mname $kindName"
		if ($style -eq 'wood') { $axe += "moderndoors:$id" } else { $pickaxe += "moderndoors:$id" }

		$handle = switch ($style) { 'concrete' { 'c9a24a' } 'metal' { if ($mid -eq 'white_aluminum') { 'b8bcc0' } else { '101010' } } default { '1c1c1c' } }
		$palette = @{ 'F' = $dark; 'H' = $handle }
		if ($glass) {
			$palette['M'] = 'a0cfe8f2'; $palette['L'] = 'd0f0fbff'
			if ($style -eq 'metal' -and $mid -eq 'black_steel') { $palette['F'] = '262626' }
			if ($style -eq 'wood') { $palette['F'] = $midHex }
		} else {
			$palette['M'] = $midHex; $palette['L'] = $light
		}
		Draw (Icon $kindId) $palette (Join-Path $tex "item\$id.png")
		Write-Json (Join-Path $assets "models\item\$id.json") "{ `"parent`": `"minecraft:item/generated`", `"textures`": { `"layer0`": `"moderndoors:item/$id`" } }"
		Write-Json (Join-Path $assets "items\$id.json") "{ `"model`": { `"type`": `"minecraft:model`", `"model`": `"moderndoors:item/$id`" } }"
		# The blocks are invisible (the block entity draws the door); the model only gives break particles.
		Write-Json (Join-Path $assets "models\block\$id.json") "{ `"textures`": { `"particle`": `"moderndoors:block/${mid}_door`" } }"
		Write-Json (Join-Path $assets "blockstates\$id.json") "{ `"variants`": { `"`": { `"model`": `"moderndoors:block/$id`" } } }"

		# Only the main part (hinge side, bottom) drops the door.
		Write-Json (Join-Path $data "loot_table\blocks\$id.json") @"
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1,
			"conditions": [
				{ "condition": "minecraft:survives_explosion" },
				{ "condition": "minecraft:block_state_property", "block": "moderndoors:$id", "properties": { "column": "0", "row": "0" } }
			],
			"entries": [ { "type": "minecraft:item", "name": "moderndoors:$id" } ]
		}
	]
}
"@
		# M the material, F its frame, G glass, I an iron ingot (for the handle).
		$recipe = switch ($kindId) {
			'pivot' { if ($glass) { @('[ "FG", "FI", "FG" ]', "`"F`": `"$ingredient`", `"G`": `"minecraft:glass`", `"I`": `"minecraft:iron_ingot`"") } else { @('[ "MM", "MI", "MM" ]', "`"M`": `"$ingredient`", `"I`": `"minecraft:iron_ingot`"") } }
			'folding' { @('[ "FGF", "FGF", "FGF" ]', "`"F`": `"$ingredient`", `"G`": `"minecraft:glass`"") }
			'sliding' { @('[ "FG", "FG", "FG" ]', "`"F`": `"$ingredient`", `"G`": `"minecraft:glass`"") }
		}
		Write-Json (Join-Path $data "recipe\$id.json") @"
{
	"type": "minecraft:crafting_shaped",
	"category": "redstone",
	"group": "modern_$($kindId)_door",
	"pattern": $($recipe[0]),
	"key": { $($recipe[1]) },
	"result": { "id": "moderndoors:$id", "count": 1 }
}
"@
		Write-Json (Join-Path $data "advancement\recipes\redstone\$id.json") @"
{
	"parent": "minecraft:recipes/root",
	"criteria": {
		"has_material": { "conditions": { "items": [ { "items": "$ingredient" } ] }, "trigger": "minecraft:inventory_changed" },
		"has_the_recipe": { "conditions": { "recipes": "moderndoors:$id" }, "trigger": "minecraft:recipe_unlocked" }
	},
	"requirements": [ [ "has_the_recipe", "has_material" ] ],
	"rewards": { "recipes": [ "moderndoors:$id" ] }
}
"@
	}
}

# ---- Door frames: blocks built around an opening that a door then fills ----
# A beveled block: a dark outline, a light top-left edge, a shaded bottom-right edge, and the material inside.
function Frame-Texture($style, $dark, $mid, $light, $path, $seed) {
	$rand = New-Object System.Random $seed
	$shade = Mix $dark $mid
	$bmp = New-Object System.Drawing.Bitmap 16, 16, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$roll = $rand.NextDouble()
			if ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15) { $hex = $dark }
			elseif ($x -eq 1 -or $y -eq 1) { $hex = $light }
			elseif ($x -eq 14 -or $y -eq 14) { $hex = $shade }
			elseif ($style -eq 'wood' -and ($x -eq 5 -or $x -eq 10)) { $hex = $shade }
			elseif ($style -eq 'metal') { $hex = if ($x + $y -eq 9 -or $x + $y -eq 10) { $light } else { $mid } }
			elseif ($roll -lt 0.1) { $hex = $light }
			elseif ($roll -lt 0.2) { $hex = $shade }
			else { $hex = $mid }
			$bmp.SetPixel($x, $y, (Color $hex))
		}
	}
	Save-Bitmap $bmp $path
}

foreach ($m in $materials) {
	$mid, $mname, $style, $dark, $midHex, $light, $ingredient = $m
	$id = "${mid}_door_frame"
	$lang["block.moderndoors.$id"] = "$mname Door Frame"
	if ($style -eq 'wood') { $axe += "moderndoors:$id" } else { $pickaxe += "moderndoors:$id" }
	Frame-Texture $style $dark $midHex $light (Join-Path $tex "block\$id.png") ($seed++)
	Write-Json (Join-Path $assets "models\block\$id.json") "{ `"parent`": `"minecraft:block/cube_all`", `"textures`": { `"all`": `"moderndoors:block/$id`" } }"
	Write-Json (Join-Path $assets "blockstates\$id.json") "{ `"variants`": { `"`": { `"model`": `"moderndoors:block/$id`" } } }"
	Write-Json (Join-Path $assets "items\$id.json") "{ `"model`": { `"type`": `"minecraft:model`", `"model`": `"moderndoors:block/$id`" } }"
	Write-Json (Join-Path $data "loot_table\blocks\$id.json") @"
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1,
			"conditions": [ { "condition": "minecraft:survives_explosion" } ],
			"entries": [ { "type": "minecraft:item", "name": "moderndoors:$id" } ]
		}
	]
}
"@
	# Wood: planks and a stick make 4. Metal: two iron ingots and a dye make 8. Concrete: two concrete and a stick make 4.
	$frameRecipe = switch ($style) {
		'wood' { @('[ "PSP" ]', "`"P`": `"$ingredient`", `"S`": `"minecraft:stick`"", 4, $ingredient) }
		'metal' { @('[ "IDI" ]', "`"I`": `"minecraft:iron_ingot`", `"D`": `"minecraft:$(if ($mid -eq 'black_steel') { 'black' } else { 'white' })_dye`"", 8, 'minecraft:iron_ingot') }
		'concrete' { @('[ "CSC" ]', "`"C`": `"$ingredient`", `"S`": `"minecraft:stick`"", 4, $ingredient) }
	}
	Write-Json (Join-Path $data "recipe\$id.json") @"
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"group": "door_frame",
	"pattern": $($frameRecipe[0]),
	"key": { $($frameRecipe[1]) },
	"result": { "id": "moderndoors:$id", "count": $($frameRecipe[2]) }
}
"@
	Write-Json (Join-Path $data "advancement\recipes\building_blocks\$id.json") @"
{
	"parent": "minecraft:recipes/root",
	"criteria": {
		"has_material": { "conditions": { "items": [ { "items": "$($frameRecipe[3])" } ] }, "trigger": "minecraft:inventory_changed" },
		"has_the_recipe": { "conditions": { "recipes": "moderndoors:$id" }, "trigger": "minecraft:recipe_unlocked" }
	},
	"requirements": [ [ "has_the_recipe", "has_material" ] ],
	"rewards": { "recipes": [ "moderndoors:$id" ] }
}
"@
}

$lang['message.moderndoors.use_on_frame'] = 'Build door frames around an opening, then use the door on a frame to fit it'
$lang['message.moderndoors.no_opening'] = 'No opening found: surround an empty space (up to %s wide and %s tall) with door frames on every side'
$lang['message.moderndoors.too_small'] = 'This door needs an opening at least %s wide and %s tall'

# ---- Tool tags, names, and the mod icon ----
function Tag-List($ids) { return ($ids | ForEach-Object { "`t`t`"$_`"" }) -join ",`n" }
Write-Json (Join-Path $root 'data\minecraft\tags\block\mineable\axe.json') "{`n`t`"values`": [`n$(Tag-List $axe)`n`t]`n}"
Write-Json (Join-Path $root 'data\minecraft\tags\block\mineable\pickaxe.json') "{`n`t`"values`": [`n$(Tag-List $pickaxe)`n`t]`n}"

$langLines = ($lang.Keys | ForEach-Object { "`t`"$_`": `"$($lang[$_])`"" }) -join ",`n"
Write-Json (Join-Path $assets 'lang\en_us.json') "{`n$langLines`n}"

Draw (Icon 'pivot') @{ 'F' = '161616'; 'M' = 'a0cfe8f2'; 'L' = 'd0f0fbff'; 'H' = 'c9a24a' } (Join-Path $assets 'icon.png') 8

Write-Host "Generated $($axe.Count + $pickaxe.Count - $materials.Count) doors and $($materials.Count) door frames."
