param(
    [string]$ImageName = $(if ($env:DOCKER_IMAGE) { $env:DOCKER_IMAGE } else { "myucxy/cloud-album-publisher" }),
    [string]$Tag = $(if ($env:DOCKER_TAG) { $env:DOCKER_TAG } else { "" }),
    [string]$VersionFile = $(if ($env:DOCKER_VERSION_FILE) { $env:DOCKER_VERSION_FILE } else { ".docker-version" }),
    [ValidateSet("major", "minor", "patch", "none")]
    [string]$VersionIncrement = $(if ($env:DOCKER_VERSION_INCREMENT) { $env:DOCKER_VERSION_INCREMENT } else { "patch" }),
    [string]$BaseImage = $(if ($env:DOCKER_BASE_IMAGE) { $env:DOCKER_BASE_IMAGE } else { "eclipse-temurin:17-jre-jammy" }),
    [ValidateSet("frontend", "pc", "android", "all")]
    [string]$ReleaseTarget = $(if ($env:RELEASE_TARGET) { $env:RELEASE_TARGET } else { "all" }),
    [string]$AndroidVariant = $env:ANDROID_VARIANT,
    [string]$MavenRepo = $(if ($env:MAVEN_REPO) { $env:MAVEN_REPO } else { Join-Path $PSScriptRoot ".m2\repository" }),
    [switch]$SkipClientPackage,
    [switch]$SkipNpmInstall,
    [switch]$IncludeAllReleases,
    [switch]$RunTests
)

$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command was not found: $Name"
    }
}

function Invoke-External {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory = $RootDir
    )

    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$FilePath $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

Require-Command "node"

$packageTarget = if ($SkipClientPackage) { "docker" } else { $ReleaseTarget }
$nodeArgs = @("scripts/package-release.mjs", "--target", $packageTarget)

if (-not $SkipClientPackage) {
    $nodeArgs += "--docker"
    if (-not $SkipNpmInstall) {
        $nodeArgs += "--install-deps"
    }
}

$nodeArgs += @(
    "--docker-image", $ImageName,
    "--docker-version-file", $VersionFile,
    "--docker-version-increment", $VersionIncrement,
    "--docker-base-image", $BaseImage,
    "--maven-repo", $MavenRepo
)

if ($Tag) {
    $nodeArgs += @("--docker-tag", $Tag)
}
if ($AndroidVariant) {
    $nodeArgs += @("--android-variant", $AndroidVariant)
}
if ($IncludeAllReleases) {
    $nodeArgs += "--docker-include-all-releases"
}
if ($RunTests) {
    $nodeArgs += "--docker-run-tests"
}

Invoke-External "node" $nodeArgs
