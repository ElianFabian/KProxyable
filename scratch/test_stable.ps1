$OriginalMetadata = Get-Content -Path "gradle.metadata.properties"
$StableMetadata = $OriginalMetadata -replace "kotlin=2.4.10", "kotlin=2.0.21" -replace "ksp=2.3.11", "ksp=2.0.21-1.0.28"

Set-Content -Path "gradle.metadata.properties" -Value $StableMetadata

Write-Host "=== TESTING STABLE KOTLIN (2.0.21) ==="
./gradlew :sample-jvm:jvmRun --no-daemon

Set-Content -Path "gradle.metadata.properties" -Value $OriginalMetadata
