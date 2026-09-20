# Run isolated, silent config UI checks; a fresh result file is required.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a Java 21 JDK.' }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
    $startedAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    & ./gradlew.bat runClient -PverifyClient --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Client failed: $LASTEXITCODE" }
    $result = Get-Content -Raw build/config-client/config-check-result.json | ConvertFrom-Json
    if ($result.status -ne 'passed' -or $result.started -lt $startedAt) { throw 'Config check did not produce fresh success evidence.' }
    Write-Output 'PASS: localized native config theme selection, undo/reset and persistence'
} finally { Pop-Location }
