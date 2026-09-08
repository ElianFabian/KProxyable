$KV = "2.4.10"
$KS = "2.3.11"

$PropFile = "gradle.metadata.properties"
$TomlFile = "gradle/libs.versions.toml"

$OriginalProps = Get-Content $PropFile
$OriginalToml = Get-Content $TomlFile

try {
    # Physical update of metadata
    $NewProps = $OriginalProps -replace "kotlin=.*", "kotlin=$KV"
    $NewProps = $NewProps -replace "ksp=.*", "ksp=$KS"
    $NewProps | Set-Content $PropFile

    # Physical update of TOML to avoid classpath conflicts
    $NewToml = $OriginalToml -replace 'kotlin = ".*"', "kotlin = `"$KV`""
    $NewToml = $NewToml -replace 'ksp = ".*"', "ksp = `"$KS`""
    $NewToml | Set-Content $TomlFile

    $WasmTask = ":sample-wasmjs:wasmJsNodeDevelopmentRun"
    cmd /c "gradlew clean :sample-common:allTests :sample-jvm:run $WasmTask -Pkproxyable.matrix=true -Pkotlin.js.yarn.check=false --no-daemon --no-configuration-cache"

} finally {
    $OriginalProps | Set-Content $PropFile
    $OriginalToml | Set-Content $TomlFile
}
