param(
    [string] $Version = "2.0.21"
)

$OriginalMetadata = Get-Content -Path "gradle.metadata.properties"

function Set-KmpVersion($v)
{
    Write-Host "[MATRIX] PREPARING KOTLIN $v..."
    $content = Get-Content -Path "gradle.metadata.properties"
    $newContent = $content -replace "kotlin=.*", "kotlin=$v"

    # Map Kotlin version to a compatible KSP version
    $ksp = switch -Regex ($v)
    {
        "^2\.0" {
            "2.0.21-1.0.28"
        }
        "^2\.1" {
            "2.1.0-1.0.29"
        }
        "^2\.2" {
            "2.3.11"
        }
        "^2\.4" {
            "2.3.11"
        }
        Default {
            "2.0.21-1.0.28"
        }
    }
    $newContent = $newContent -replace "ksp=.*", "ksp=$ksp"
    Set-Content -Path "gradle.metadata.properties" -Value $newContent
}

try
{
    Write-Host "=== KProxyable Comprehensive Check ($Version) ==="
    Set-KmpVersion $Version

    Write-Host "[MATRIX] Cleaning..."
    ./gradlew clean --no-daemon

    Write-Host "[MATRIX] Running JVM Samples..."
    ./gradlew :sample-jvm:jvmRun :sample-kmp:jvmRun --no-daemon
    if ($LASTEXITCODE -ne 0)
    {
        throw "JVM Samples Failed"
    }

    Write-Host "[MATRIX] Running Web Samples (JS + WasmJs)..."
    ./gradlew :sample-kmp:jsNodeDevelopmentRun :sample-kmp:wasmJsNodeDevelopmentRun --no-daemon
    if ($LASTEXITCODE -ne 0)
    {
        throw "Web Samples Failed"
    }

    Write-Host "[MATRIX] Running Common Tests..."
    ./gradlew :sample-common:allTests --no-daemon
    if ($LASTEXITCODE -ne 0)
    {
        throw "Tests Failed"
    }

    Write-Host "SUCCESS for Kotlin $Version" -ForegroundColor Green
}
catch
{
    Write-Error "FAILED for Kotlin $Version : $_"
    exit 1
}
finally
{
    Write-Host "Restoring original environment..."
    Set-Content -Path "gradle.metadata.properties" -Value $OriginalMetadata
}
