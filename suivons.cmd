@echo off
rem Suivre Notre Argent - recette locale (Windows). Double-cliquer ce fichier, ou : suivons.cmd demarrer
rem Seul prerequis : Docker Desktop. Guide : docs\demarrer-en-local.md
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\suivons.ps1" %*
if "%~1"=="" pause
