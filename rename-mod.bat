@echo off
rem Usage: rename-mod "Dragon Tools"              (id becomes dragontools)
rem        rename-mod "Dragon Tools" dragon_tools  (choose the id yourself)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\rename-mod.ps1" %*
