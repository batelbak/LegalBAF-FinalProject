@echo off
rem Double-click or run from a terminal. Passes any options on to run-local.ps1, e.g.:
rem   run-local.cmd -Model gemma4:26b -Cases 001-164453
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-local.ps1" %*
if errorlevel 1 pause
