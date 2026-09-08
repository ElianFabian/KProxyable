$versions = @("2.0.0", "2.0.21", "2.1.0", "2.2.0", "2.4.20")

foreach ($v in $versions)
{
    Write-Host "`n====================================================" -ForegroundColor Cyan
    Write-Host "TESTING KOTLIN $v" -ForegroundColor Cyan
    Write-Host "====================================================`n" -ForegroundColor Cyan

    powershell -File scratch/run_matrix.ps1 -Version $v

    if ($LASTEXITCODE -ne 0)
    {
        Write-Host "Stopping due to failure in $v" -ForegroundColor Red
        exit 1
    }
}

Write-Host "`nALL VERSIONS PASSED!" -ForegroundColor Green
