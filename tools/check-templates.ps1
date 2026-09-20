# Runs isolated real-client template checks and rejects missing or stale success reports.
param([ValidateSet('vanilla','iris','iris-active')][string]$Profile = 'vanilla')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a Java 21 JDK before running client checks.' }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
    $fixture = if ($Profile -eq 'vanilla') { 'template-client' } else { "template-client-$Profile" }
    $resultPath = Join-Path $projectRoot "build/$fixture/template-check-result.json"
    $startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    & ./gradlew.bat runClient -PverifyTemplates "-PtemplateProfile=$Profile" --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Client check failed: exit $LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $resultPath)) { throw 'Client produced no result report.' }
    $result = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
    if ($result.status -ne 'passed' -or $result.started -lt $startedAt) { throw 'Client report is stale or did not pass.' }
    Write-Output "PASS: $Profile templates (actual pixels, updates, slab faces and menu)"
} finally { Pop-Location }
