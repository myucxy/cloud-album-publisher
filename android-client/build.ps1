param(
    [ValidateSet("assembleDebug", "assembleRelease", "bundleDebug", "bundleRelease", "installDebug", "installRelease")]
    [string]$Task = "assembleDebug",
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleUserHome = Join-Path $projectDir ".gradle-user-home"
if (-not (Test-Path $gradleUserHome)) {
    New-Item -ItemType Directory -Path $gradleUserHome -Force | Out-Null
}
$env:GRADLE_USER_HOME = $gradleUserHome

function Get-ExpectedGradleVersion {
    param([string]$PropertiesPath)

    if (-not (Test-Path $PropertiesPath)) {
        return $null
    }

    $distributionLine = Get-Content $PropertiesPath | Where-Object { $_ -like "distributionUrl=*" } | Select-Object -First 1
    if (-not $distributionLine) {
        return $null
    }

    if ($distributionLine -match "gradle-([0-9.]+)-") {
        return $Matches[1]
    }
    return $null
}

function Find-GradleExecutable {
    param([string]$ProjectDir)

    $command = Get-Command gradle -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $expectedVersion = Get-ExpectedGradleVersion -PropertiesPath (Join-Path $ProjectDir "gradle\wrapper\gradle-wrapper.properties")
    $distRoot = Join-Path $env:USERPROFILE ".gradle\wrapper\dists"
    if (-not (Test-Path $distRoot)) {
        throw "Gradle executable not found. Install Gradle or open the project in Android Studio once to download the Gradle distribution."
    }

    $candidates = Get-ChildItem $distRoot -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue
    if ($expectedVersion) {
        $versioned = $candidates | Where-Object { $_.FullName -like "*gradle-$expectedVersion*" }
        if ($versioned) {
            return ($versioned | Select-Object -First 1).FullName
        }
    }

    if ($candidates) {
        return ($candidates | Sort-Object FullName -Descending | Select-Object -First 1).FullName
    }

    throw "Gradle executable not found in PATH or under $distRoot."
}

function Get-BuildOutputPath {
    param(
        [string]$ProjectDir,
        [string]$BuildTask
    )

    switch ($BuildTask) {
        "assembleDebug" { return Join-Path $ProjectDir "app\build\outputs\apk\debug\app-debug.apk" }
        "assembleRelease" { return Join-Path $ProjectDir "app\build\outputs\apk\release\app-release.apk" }
        "bundleDebug" { return Join-Path $ProjectDir "app\build\outputs\bundle\debug\app-debug.aab" }
        "bundleRelease" { return Join-Path $ProjectDir "app\build\outputs\bundle\release\app-release.aab" }
        default { return $null }
    }
}

$gradleExecutable = Find-GradleExecutable -ProjectDir $projectDir
$tasks = @()
if ($Clean) {
    $tasks += "clean"
}
$tasks += ":app:$Task"

Write-Host "Using Gradle: $gradleExecutable"
Write-Host "Project Dir : $projectDir"
Write-Host "Tasks       : $($tasks -join ', ')"

& $gradleExecutable -p $projectDir @tasks
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$outputPath = Get-BuildOutputPath -ProjectDir $projectDir -BuildTask $Task
if ($outputPath -and (Test-Path $outputPath)) {
    Write-Host "Output      : $outputPath"
}
