$v = "2.0.0"
$ksp = "2.0.0-1.0.21"

Write-Host "[TEST] PREPARING KOTLIN $v with KSP $ksp..."
$OriginalMetadata = Get-Content -Path "gradle.metadata.properties"

try {
    $content = Get-Content -Path "gradle.metadata.properties"
    $newContent = $content -replace "kotlin=.*", "kotlin=$v"
    $newContent = $newContent -replace "ksp=.*", "ksp=$ksp"
    Set-Content -Path "gradle.metadata.properties" -Value $newContent

    Write-Host "[TEST] Running build..."
    ./gradlew clean :sample-jvm:jvmRun --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build Failed" }

    Write-Host "SUCCESS: Kotlin 2.0.0 works with KProxyable!" -ForegroundColor Green
} finally {
    Set-Content -Path "gradle.metadata.properties" -Value $OriginalMetadata
}
