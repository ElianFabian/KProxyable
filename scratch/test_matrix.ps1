$Versions = @(
    @{ kotlin = "2.0.21"; ksp = "2.0.21-1.0.28" },
    @{ kotlin = "2.1.10"; ksp = "2.1.10-1.0.31" }
)

Write-Host "=== KProxyable Multi-Version Testing Matrix ===" -ForegroundColor Cyan

foreach ($V in $Versions) {
    $KV = $V.kotlin
    $KS = $V.ksp
    Write-Host "`nTesting Kotlin $KV with KSP $KS..." -ForegroundColor Yellow

    # Run tests with property overrides
    ./gradlew clean :sample-common:allTests :sample-wasmjs:wasmJsNodeRun `
        -PkotlinVersion=$KV `
        -PkspVersion=$KS `
        --no-configuration-cache

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED for Kotlin $KV" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "PASSED for Kotlin $KV" -ForegroundColor Green
}

Write-Host "`n=== All Matrix Versions PASSED ===" -ForegroundColor Cyan
