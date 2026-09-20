# Runs isolated real-client graphics checks and rejects stale/missing success reports.
param(
    [ValidateSet('vanilla','iris','iris-active')][string]$Profile = 'vanilla',
    [ValidateSet('fast','fancy','fabulous')][string]$Graphics = 'fancy'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a Java 21 JDK before running client checks.' }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
    $resultPath = Join-Path $projectRoot "build/portal-client-$Profile/portal-check-result.json"
    $startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    & ./gradlew.bat runClient -PverifyPortals "-PportalProfile=$Profile" "-PportalGraphics=$Graphics" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Client check failed: exit $LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $resultPath)) { throw 'Client produced no result report.' }
    $result = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
    if ($result.status -ne 'passed' -or $result.started -lt $startedAt) { throw 'Client report is stale or did not pass.' }
    Write-Output "PASS: $Profile / $Graphics (live client, snapshots, fallback and transfer lifecycle)"
} finally { Pop-Location }
