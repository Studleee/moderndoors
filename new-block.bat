@echo off
rem Usage: new-block ruby_block "Block of Ruby"
rem        new-block maple_planks "Maple Planks" -Tool axe
rem Tools: pickaxe (default), axe, shovel, hoe, none
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\new-block.ps1" %*
