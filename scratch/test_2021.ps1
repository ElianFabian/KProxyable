$v = "2.0.21"
$ksp = "2.0.21-1.0.28"

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

    Write-Host "SUCCESS: Kotlin 2.0.21 works perfectly!" -ForegroundColor Green
} finally {
    Set-Content -Path "gradle.metadata.properties" -Value $OriginalMetadata
}
