param(
	[Parameter(Mandatory = $true, Position = 0)][string]$Name,
	[Parameter(Position = 1)][string]$DisplayName,
	[switch]$Food
)

trap { Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
. (Join-Path $PSScriptRoot 'common.ps1')

Assert-ContentName $Name
if (-not $DisplayName) { $DisplayName = ConvertTo-DisplayName $Name }
$modId = Get-ModId
$constant = $Name.ToUpper()
$assets = Join-Path $ProjectRoot "src\main\resources\assets\$modId"

$code = if ($Food) {
	"`tpublic static final Item $constant = Register.food(`"$Name`", 4, 0.3F);"
} else {
	"`tpublic static final Item $constant = Register.item(`"$Name`");"
}
Add-JavaLine 'ModItems.java' 'new-item-marker' $code $constant

Write-TextFile (Join-Path $assets "models\item\$Name.json") @"
{
	"parent": "minecraft:item/generated",
	"textures": {
		"layer0": "${modId}:item/$Name"
	}
}
"@

Write-TextFile (Join-Path $assets "items\$Name.json") @"
{
	"model": {
		"type": "minecraft:model",
		"model": "${modId}:item/$Name"
	}
}
"@

$texture = Join-Path $assets "textures\item\$Name.png"
New-PlaceholderTexture $texture $Name 'item'
Add-LangEntry $modId "item.$modId.$Name" $DisplayName

Write-Host ""
Write-Host "Added item '$DisplayName' ($modId`:$Name)." -ForegroundColor Green
Write-Host "  Code:    ModItems.java -> $constant"
Write-Host "  Texture: $texture"
Write-Host "Paint over the placeholder texture (16x16 PNG), then run run.bat to try it."
