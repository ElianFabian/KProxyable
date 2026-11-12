$ErrorActionPreference = 'Stop'
# This test deliberately invokes a failing native command and validates its output below.
$PSNativeCommandUseErrorActionPreference = $false
$repoRoot = Split-Path $PSScriptRoot -Parent
$fixturePath = Join-Path $repoRoot 'kproxyable-processor/src/testFixtures/ExtensionProperty.kt.txt'
$targetPath = Join-Path $repoRoot 'sample-kmp/src/commonTest/kotlin/ExtensionPropertyNegativeFixture.kt'
$logPath = Join-Path $repoRoot 'build/extension-property-negative.log'
if (Test-Path -LiteralPath $targetPath) { throw 'The temporary fixture already exists; refusing to overwrite it.' }
Push-Location $repoRoot
try {
    Copy-Item -LiteralPath $fixturePath -Destination $targetPath
    & .\gradlew.bat :sample-kmp:kspTestKotlinJvm '-Pkproxyable.matrix=true' *> $logPath
    if ($LASTEXITCODE -eq 0) { throw 'Expected extension property compilation to fail.' }
    if (-not (Select-String -LiteralPath $logPath -SimpleMatch 'KProxyable does not support extension properties')) {
        throw 'Compilation failed without the expected extension property diagnostic.'
    }
    Write-Output 'Extension property diagnostic verified.'
}
finally {
    Remove-Item -LiteralPath $targetPath
    Pop-Location
}

# GitHub Actions' pwsh wrapper propagates LASTEXITCODE even when the script succeeds.
# Clear the expected Gradle failure only after validation and cleanup have succeeded.
$global:LASTEXITCODE = 0
