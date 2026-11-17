<#
  LegalBAF - run everything with a local LLM (Ollama). No API key, no cost.

  What it does, in order:
    1. Checks Java 17+ and Ollama (offers to install them with winget if missing)
    2. Starts the Ollama server if it is not running, downloads the model once
    3. Builds the project (the Maven wrapper downloads Maven by itself the first time)
    4. Judge pass: the local judge weighs the REAL submissions of every case
       (so the simulation is compared against the same judge, not against Claude)
    5. Simulation: the local agents argue every case for 3 rounds
    6. Starts the website at http://localhost:8080 and opens it

  Every step saves its results under data\local\<model>\ and is skipped next time.
  Usage (from this folder):   .\run-local.cmd
  Options:  -Model gemma4:26b   -Cases 001-164453,001-59207   -Fresh
            -SkipJudge   -SkipSimulation   -NoServer   -Think
#>
param(
    [string]$Model = $(if ($env:LEGALSIM_MODEL) { $env:LEGALSIM_MODEL } else { 'gemma4:12b' }),
    [string[]]$Cases = @(),
    [switch]$Fresh,
    [switch]$SkipJudge,
    [switch]$SkipSimulation,
    [switch]$NoServer,
    [switch]$Think
)

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
# through run-local.cmd, "-Cases a,b" arrives as one string
$Cases = @($Cases | ForEach-Object { $_ -split '[,\s]+' } | Where-Object { $_ })
$OllamaUrl = 'http://localhost:11434'

function Step($text) { Write-Host ''; Write-Host "==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host ''; Write-Host "ERROR: $text" -ForegroundColor Red; exit 1 }

function Ask-Install($what, $wingetId) {
    Write-Host "$what was not found." -ForegroundColor Yellow
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        Fail "Install $what yourself, then run this script again."
    }
    # never install without a person typing yes: with no keyboard (redirected input) we only explain
    if ([Console]::IsInputRedirected) {
        Fail "Install $what (winget install --id $wingetId -e), then run this script again."
    }
    $answer = [string](Read-Host "Install $what now with winget ($wingetId)? Type yes to install")
    if (@('y', 'yes') -notcontains $answer.Trim().ToLower()) { Fail "Install $what, then run this script again." }
    winget install --id $wingetId -e --accept-source-agreements --accept-package-agreements
    Write-Host ''
    Write-Host "$what installed. Close this window, open a new one, and run .\run-local.cmd again" -ForegroundColor Green
    Write-Host '(a new window is needed so Windows picks up the new PATH).'
    exit 0
}

function Invoke-Maven([string[]]$mavenArgs) {
    & .\mvnw.cmd -q -B @mavenArgs
    if ($LASTEXITCODE -ne 0) { Fail "Maven failed: $($mavenArgs -join ' ')" }
}

# ---------------------------------------------------------------- 1. Java
Step 'Checking Java'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Ask-Install 'Java 21 (Temurin JDK)' 'EclipseAdoptium.Temurin.21.JDK'
}
$javaVersion = (& cmd /c 'java -version 2>&1' | Select-Object -First 1)
if ($javaVersion -match 'version "(\d+)') {
    if ([int]$Matches[1] -lt 17) {
        Write-Host "Found $javaVersion - this project needs 17 or newer." -ForegroundColor Yellow
        Ask-Install 'Java 21 (Temurin JDK)' 'EclipseAdoptium.Temurin.21.JDK'
    }
}
Write-Host $javaVersion

# ---------------------------------------------------------------- 2. Ollama + model
Step 'Checking Ollama'
if (-not (Get-Command ollama -ErrorAction SilentlyContinue)) {
    Ask-Install 'Ollama' 'Ollama.Ollama'
}
function Test-Ollama {
    try { Invoke-RestMethod "$OllamaUrl/api/version" -TimeoutSec 3 } catch { $null }
}
$version = Test-Ollama
if (-not $version) {
    Write-Host 'Ollama is not running - starting it...'
    Start-Process ollama -ArgumentList 'serve' -WindowStyle Hidden
    for ($i = 0; $i -lt 30 -and -not $version; $i++) { Start-Sleep -Seconds 1; $version = Test-Ollama }
    if (-not $version) { Fail 'Could not start Ollama. Open the Ollama app from the Start menu and try again.' }
}
Write-Host "Ollama $($version.version) is running"

$installed = (Invoke-RestMethod "$OllamaUrl/api/tags").models | ForEach-Object { $_.name }
if ($installed -notcontains $Model -and $installed -notcontains "$($Model):latest") {
    Step "Downloading $Model (one time, several GB)"
    & ollama pull $Model
    if ($LASTEXITCODE -ne 0) {
        Fail "Could not download $Model. If Ollama says it needs a newer version, update Ollama (winget upgrade Ollama.Ollama)."
    }
} else {
    Write-Host "Model $Model is installed"
}

$env:LEGALSIM_LLM = 'ollama'
$env:LEGALSIM_MODEL = $Model
$env:LEGALSIM_THINK = if ($Think) { 'true' } else { 'false' }

# ---------------------------------------------------------------- 3. Build
Step 'Building the project (first run downloads Maven and libraries)'
Invoke-Maven @('compile')

function Run-Main([string]$mainClass, [string[]]$programArgs) {
    $mavenArgs = @('exec:java', "-Dexec.mainClass=$mainClass")
    if ($programArgs.Count -gt 0) { $mavenArgs += "-Dexec.args=$($programArgs -join ' ')" }
    Invoke-Maven $mavenArgs
}

# ---------------------------------------------------------------- 4. Judge the real submissions
if (-not $SkipJudge) {
    Step "Judge pass: $Model weighs the real submissions (a few minutes per case)"
    Run-Main 'ac.qbaf.legalsim.RealGraphRun' $Cases
}

# ---------------------------------------------------------------- 5. Simulation
if (-not $SkipSimulation) {
    $simArgs = @($Cases)
    if ($Fresh) { $simArgs += '--fresh' }
    Step "Simulation: the agents argue each case for 3 rounds (several minutes per case)"
    Run-Main 'ac.qbaf.legalsim.SimulationRun' $simArgs
}

# ---------------------------------------------------------------- 6. Website
if (-not $NoServer) {
    Step 'Starting the website at http://localhost:8080  (Ctrl+C to stop)'
    Start-Job -ScriptBlock {
        for ($i = 0; $i -lt 120; $i++) {
            try { Invoke-WebRequest 'http://localhost:8080/api/health' -UseBasicParsing -TimeoutSec 2 | Out-Null; break }
            catch { Start-Sleep -Seconds 1 }
        }
        Start-Process 'http://localhost:8080'
    } | Out-Null
    Run-Main 'ac.qbaf.legalsim.web.WebServer' @()
}

Step "Done. Results are in data\local\ (one folder per model)."
