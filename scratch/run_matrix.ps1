param(
    [string]$Version = "2.0.21"
)

$OriginalMetadata = Get-Content -Path "gradle.metadata.properties"

function Set-KmpVersion($v) {
    Write-Host "[MATRIX] PREPARING KOTLIN $v..."
    $content = Get-Content -Path "gradle.metadata.properties"
    $newContent = $content -replace "kotlin=.*", "kotlin=$v"

    # Map Kotlin version to a compatible KSP version for testing
    $ksp = switch ($v) {
        "2.0.21" { "2.0.21-1.0.28" }
        "2.1.0" { "2.1.0-1.0.29" }
        "2.4.10" { "2.3.11" }
        Default { "2.0.21-1.0.28" }
    }
    $newContent = $newContent -replace "ksp=.*", "ksp=$ksp"
    Set-Content -Path "gradle.metadata.properties" -Value $newContent
}

try {
    Write-Host "=== KProxyable Compatibility Check ($Version) ==="
    Set-KmpVersion $Version

    Write-Host "[MATRIX] Building and testing..."
    ./gradlew clean :sample-jvm:jvmRun :sample-kmp:wasmJsNodeTest --no-daemon

    if ($LASTEXITCODE -eq 0) {
        Write-Host "SUCCESS for Kotlin $Version" -ForegroundColor Green
    } else {
        Write-Error "FAILED for Kotlin $Version"
    }
} finally {
    Write-Host "Restoring original environment..."
    Set-Content -Path "gradle.metadata.properties" -Value $OriginalMetadata
}
