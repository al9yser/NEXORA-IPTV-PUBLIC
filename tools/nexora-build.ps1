param(
    [string]$Repository = "al9yser/NEXORA-IPTV-PUBLIC",
    [string]$Branch = "main",
    [int]$TimeoutMinutes = 30
)

$ErrorActionPreference = "Stop"

function Assert-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found. Install GitHub CLI (gh) first."
    }
}

Assert-Command "gh"

Write-Host "Checking GitHub authentication..." -ForegroundColor Cyan
gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run: gh auth login"
}

Write-Host "Checking Android build workflow..." -ForegroundColor Cyan
gh workflow view android.yml --repo $Repository
if ($LASTEXITCODE -ne 0) {
    throw "Workflow android.yml was not found or is not accessible in $Repository."
}

$before = gh run list --repo $Repository --workflow android.yml --branch $Branch --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null
if ($LASTEXITCODE -ne 0) { $before = "" }

Write-Host "Starting NEXORA Android build on GitHub Actions..." -ForegroundColor Cyan
gh workflow run android.yml --repo $Repository --ref $Branch
if ($LASTEXITCODE -ne 0) {
    throw "Could not start the workflow."
}

$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$runId = $null

do {
    Start-Sleep -Seconds 5
    $candidate = gh run list --repo $Repository --workflow android.yml --branch $Branch --limit 5 --json databaseId,status,createdAt --jq '.[0].databaseId' 2>$null
    if ($LASTEXITCODE -eq 0 -and $candidate -and $candidate -ne $before) {
        $runId = $candidate.Trim()
    }
} while (-not $runId -and (Get-Date) -lt $deadline)

if (-not $runId) {
    throw "Timed out waiting for the new GitHub Actions run to appear."
}

Write-Host "Build run: $runId" -ForegroundColor Cyan
gh run watch $runId --repo $Repository --exit-status
if ($LASTEXITCODE -ne 0) {
    gh run view $runId --repo $Repository
    throw "The remote build failed. Review the run logs above."
}

$downloadDir = Join-Path (Get-Location) "NEXORA-APK"
New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
gh run download $runId --repo $Repository --name "NEXORA-Android-APK" --dir $downloadDir
if ($LASTEXITCODE -ne 0) {
    throw "Build succeeded, but the APK artifact could not be downloaded."
}

$apk = Get-ChildItem -Path $downloadDir -Filter "*.apk" -Recurse | Select-Object -First 1
if (-not $apk) {
    throw "No APK file was found in the downloaded artifact."
}

Write-Host ""
Write-Host "SUCCESS: NEXORA APK is ready." -ForegroundColor Green
Write-Host ("APK: " + $apk.FullName) -ForegroundColor Green
Write-Host ("Size: " + [math]::Round($apk.Length / 1MB, 2) + " MB") -ForegroundColor Green
