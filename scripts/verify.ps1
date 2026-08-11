param(
    [string]$JdkHome = 'C:\Users\27623\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath "$JdkHome\bin\java.exe")) {
    throw "JDK 17 not found: $JdkHome"
}

$env:JAVA_HOME = $JdkHome
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$env:Path = "$JdkHome\bin;$machinePath;$userPath"

function Invoke-GradleStep {
    param([Parameter(Mandatory)][string[]]$Arguments)
    & "$PSScriptRoot\..\gradlew.bat" @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Arguments -join ' ')"
    }
}

Invoke-GradleStep @(':app:testDebugUnitTest')
Invoke-GradleStep @(':app:lintDebug')
Invoke-GradleStep @(':app:assembleDebug')

$apk = Join-Path $PSScriptRoot '..\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Debug APK not found: $apk"
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant()
Write-Host "VERIFICATION_OK"
Write-Host "APK=$([IO.Path]::GetFullPath($apk))"
Write-Host "SHA256=$hash"
