$Versions = @(
    @{ kotlin = "2.0.21"; ksp = "2.0.21-1.0.28" },
    @{ kotlin = "2.1.10"; ksp = "2.1.10-1.0.31" },
    @{ kotlin = "2.2.0";  ksp = "2.2.0-1.0.33" },
    @{ kotlin = "2.3.0";  ksp = "2.3.4" },
    @{ kotlin = "2.4.10"; ksp = "2.3.11" }
)

Write-Host "=== KProxyable Multi-Version Testing Matrix (Kotlin 2.0 to 2.4) ===" -ForegroundColor Cyan

$Results = @()

foreach ($V in $Versions) {
    $KV = $V.kotlin
    $KS = $V.ksp
    Write-Host "`n[MATRIX] Testing Kotlin $KV with KSP $KS..." -ForegroundColor Yellow

    ./gradlew clean :sample-common:allTests :sample-jvm:run :sample-wasmjs:wasmJsNodeRun `
        "-PkotlinVersion=$KV" `
        "-PkspVersion=$KS" `
        --no-configuration-cache

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED for Kotlin $KV" -ForegroundColor Red
        $Results += [PSCustomObject]@{ Version = $KV; Status = "FAILED" }
    } else {
        Write-Host "PASSED for Kotlin $KV" -ForegroundColor Green
        $Results += [PSCustomObject]@{ Version = $KV; Status = "PASSED" }
    }
}

Write-Host "`n=== Matrix Summary ===" -ForegroundColor Cyan
$Results | Format-Table -AutoSize
