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
$BuildDir = Join-Path $RootDir "docker-build"
$StagedReleasesDir = Join-Path $BuildDir "releases"
$LatestTag = "latest"

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

function Copy-ReleaseFile {
    param([string]$RelativePath)

    $normalizedRelativePath = $RelativePath -replace '/', '\'
    $source = Join-Path (Join-Path $RootDir "releases") $normalizedRelativePath
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Release artifact referenced by manifest was not found: releases\$normalizedRelativePath"
    }

    $destination = Join-Path $StagedReleasesDir $normalizedRelativePath
    $destinationDir = Split-Path -Parent $destination
    New-Item -ItemType Directory -Force -Path $destinationDir | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

function Resolve-VersionFilePath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $RootDir $Path
}

function Read-DockerVersion {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return "0.0.0"
    }

    $value = (Get-Content -LiteralPath $Path -Raw -Encoding UTF8).Trim()
    if (-not $value) {
        return "0.0.0"
    }
    return $value
}

function Step-DockerVersion {
    param(
        [string]$CurrentVersion,
        [string]$Increment
    )

    if ($CurrentVersion -notmatch '^(?<prefix>v?)(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)$') {
        throw "Docker version '$CurrentVersion' is invalid. Use semantic version format like 1.0.0 or v1.0.0."
    }

    $prefix = $Matches.prefix
    $major = [int]$Matches.major
    $minor = [int]$Matches.minor
    $patch = [int]$Matches.patch

    switch ($Increment) {
        "major" {
            $major += 1
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor += 1
            $patch = 0
        }
        "patch" {
            $patch += 1
        }
        "none" {}
    }

    return "$prefix$major.$minor.$patch"
}

function Write-DockerVersion {
    param(
        [string]$Path,
        [string]$Version
    )

    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    Set-Content -LiteralPath $Path -Value $Version -Encoding ASCII
}

Set-Location $RootDir

Require-Command "docker"
Require-Command "node"
Require-Command "mvn"

$versionFilePath = Resolve-VersionFilePath $VersionFile
$ShouldPersistVersion = $false
if ($Tag) {
    $VersionTag = $Tag
    Write-Host "Using explicit Docker tag: $VersionTag"
} else {
    $currentVersion = Read-DockerVersion $versionFilePath
    $VersionTag = Step-DockerVersion $currentVersion $VersionIncrement
    Write-Host "Docker version: $currentVersion -> $VersionTag"
    $ShouldPersistVersion = $true
}

$VersionImageRef = "${ImageName}:${VersionTag}"
$LatestImageRef = "${ImageName}:${LatestTag}"

if (-not $SkipClientPackage) {
    Require-Command "npm"
    if (-not $SkipNpmInstall) {
        Write-Host "[1/5] Installing npm dependencies..."
        if ($ReleaseTarget -eq "frontend" -or $ReleaseTarget -eq "all") {
            Invoke-External "npm" @("--prefix", "frontend", "install", "--prefer-offline")
        }
        if ($ReleaseTarget -eq "pc" -or $ReleaseTarget -eq "all") {
            Invoke-External "npm" @("--prefix", "device-client", "install", "--prefer-offline")
        }
    } else {
        Write-Host "[1/5] Skipping npm install."
    }

    $releaseArgs = @("scripts/package-release.mjs", "--target", $ReleaseTarget)
    if ($AndroidVariant) {
        $releaseArgs += @("--android-variant", $AndroidVariant)
    }
    Write-Host "[2/5] Building frontend and client packages..."
    Invoke-External "node" $releaseArgs
} else {
    Write-Host "[1/5] Skipping client package build; using existing releases/ contents."
}

Write-Host "[3/5] Building Spring Boot jar..."
$mavenArgs = @("-Dmaven.repo.local=$MavenRepo", "clean", "package")
if (-not $RunTests) {
    $mavenArgs += "-DskipTests"
}
Invoke-External "mvn" $mavenArgs

Write-Host "[4/5] Preparing Docker build context..."
if (Test-Path -LiteralPath $BuildDir) {
    Remove-Item -LiteralPath $BuildDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
New-Item -ItemType Directory -Force -Path $StagedReleasesDir | Out-Null

$jar = Get-ChildItem -Path (Join-Path $RootDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    throw "No runnable jar was found in target/."
}

Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $BuildDir "app.jar") -Force

$sourceReleasesDir = Join-Path $RootDir "releases"
$manifestPath = Join-Path $sourceReleasesDir "manifest.json"
if (Test-Path -LiteralPath $manifestPath) {
    if ($IncludeAllReleases) {
        Copy-Item -Path (Join-Path $sourceReleasesDir "*") -Destination $StagedReleasesDir -Recurse -Force
    } else {
        Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $StagedReleasesDir "manifest.json") -Force
        $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
        foreach ($platform in $manifest.platforms.PSObject.Properties) {
            foreach ($channel in $platform.Value.channels.PSObject.Properties) {
                $relativePath = $channel.Value.downloadUrl
                if ($relativePath -match "/downloads/(.+)$") {
                    Copy-ReleaseFile $Matches[1]
                } elseif ($channel.Value.fileName) {
                    Copy-ReleaseFile (Join-Path $platform.Name $channel.Value.fileName)
                }
            }
        }
    }
} else {
    Write-Warning "releases/manifest.json was not found; the image will not contain client download metadata."
}

Write-Host "[5/5] Building Docker image $VersionImageRef and $LatestImageRef..."
Invoke-External "docker" @("build", "-f", "Dockerfile", "--build-arg", "BASE_IMAGE=$BaseImage", "-t", $VersionImageRef, "-t", $LatestImageRef, ".")

if ($ShouldPersistVersion) {
    Write-DockerVersion $versionFilePath $VersionTag
}

Write-Host ""
Write-Host "Docker images built:"
Write-Host "  $VersionImageRef"
Write-Host "  $LatestImageRef"
Write-Host "Example run:"
Write-Host "docker run --rm -p 8080:8080 -e DB_HOST=host.docker.internal -e DB_PORT=3306 -e DB_NAME=cloud_album -e DB_USER=root -e DB_PASSWORD=root -e REDIS_HOST=host.docker.internal -e MINIO_ENDPOINT=http://host.docker.internal:9000 $LatestImageRef"
