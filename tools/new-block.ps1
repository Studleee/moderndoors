param(
	[Parameter(Mandatory = $true, Position = 0)][string]$Name,
	[Parameter(Position = 1)][string]$DisplayName,
	[ValidateSet('pickaxe', 'axe', 'shovel', 'hoe', 'none')][string]$Tool = 'pickaxe'
)

trap { Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
. (Join-Path $PSScriptRoot 'common.ps1')

Assert-ContentName $Name
if (-not $DisplayName) { $DisplayName = ConvertTo-DisplayName $Name }
$modId = Get-ModId
$constant = $Name.ToUpper()
$resources = Join-Path $ProjectRoot 'src\main\resources'
$assets = Join-Path $resources "assets\$modId"

$sound = switch ($Tool) { 'axe' { 'WOOD' } 'shovel' { 'GRAVEL' } 'hoe' { 'GRASS' } default { 'STONE' } }
$code = "`tpublic static final Block $constant = Register.block(`n`t`t`"$Name`",`n`t`tBlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.$sound)`n`t);"
Add-JavaLine 'ModBlocks.java' 'new-block-marker' $code $constant

Write-TextFile (Join-Path $assets "blockstates\$Name.json") @"
{
	"variants": {
		"": {
			"model": "${modId}:block/$Name"
		}
	}
}
"@

Write-TextFile (Join-Path $assets "models\block\$Name.json") @"
{
	"parent": "minecraft:block/cube_all",
	"textures": {
		"all": "${modId}:block/$Name"
	}
}
"@

Write-TextFile (Join-Path $assets "items\$Name.json") @"
{
	"model": {
		"type": "minecraft:model",
		"model": "${modId}:block/$Name"
	}
}
"@

Write-TextFile (Join-Path $resources "data\$modId\loot_table\blocks\$Name.json") @"
{
	"type": "minecraft:block",
	"pools": [
		{
			"rolls": 1.0,
			"entries": [
				{
					"type": "minecraft:item",
					"name": "${modId}:$Name"
				}
			],
			"conditions": [
				{
					"condition": "minecraft:survives_explosion"
				}
			]
		}
	]
}
"@

if ($Tool -ne 'none') {
	$tagPath = Join-Path $resources "data\minecraft\tags\block\mineable\$Tool.json"
	$values = @()
	if (Test-Path $tagPath) { $values = @(([System.IO.File]::ReadAllText($tagPath) | ConvertFrom-Json).values) }
	$values += "${modId}:$Name"
	$list = ($values | Select-Object -Unique | ForEach-Object { "`t`t`"$_`"" }) -join ",`n"
	Write-TextFile $tagPath "{`n`t`"replace`": false,`n`t`"values`": [`n$list`n`t]`n}`n"
}

$texture = Join-Path $assets "textures\block\$Name.png"
New-PlaceholderTexture $texture $Name 'block'
Add-LangEntry $modId "block.$modId.$Name" $DisplayName

Write-Host ""
Write-Host "Added block '$DisplayName' ($modId`:$Name)." -ForegroundColor Green
Write-Host "  Code:    ModBlocks.java -> $constant"
Write-Host "  Texture: $texture"
Write-Host "  Mined fastest with: $Tool. Drops itself when broken."
Write-Host "Paint over the placeholder texture (16x16 PNG), then run run.bat to try it."
