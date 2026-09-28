@echo off
rem Usage: new-item ruby "Ruby"          (a plain item)
rem        new-item apple_pie "Apple Pie" -Food
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\new-item.ps1" %*
