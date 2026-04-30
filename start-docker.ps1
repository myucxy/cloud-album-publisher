param(
    [string]$ImageName = $(if ($env:DOCKER_IMAGE) { $env:DOCKER_IMAGE } else { "myucxy/cloud-album-publisher" }),
    [string]$Tag = $(if ($env:DOCKER_TAG) { $env:DOCKER_TAG } else { "latest" }),
    [string]$NetworkName = $(if ($env:DOCKER_NETWORK) { $env:DOCKER_NETWORK } else { "cloud-album-publisher_default" }),
    [string]$MysqlRootPassword = $(if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "root" }),
    [string]$MysqlDatabase = $(if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "cloud_album" }),
    [string]$MysqlPort = $(if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }),
    [string]$RedisPort = $(if ($env:REDIS_PORT) { $env:REDIS_PORT } else { "6379" }),
    [string]$MinioRootUser = $(if ($env:MINIO_ROOT_USER) { $env:MINIO_ROOT_USER } else { "minioadmin" }),
    [string]$MinioRootPassword = $(if ($env:MINIO_ROOT_PASSWORD) { $env:MINIO_ROOT_PASSWORD } else { "minioadmin" }),
    [string]$MinioApiPort = $(if ($env:MINIO_API_PORT) { $env:MINIO_API_PORT } else { "9000" }),
    [string]$MinioConsolePort = $(if ($env:MINIO_CONSOLE_PORT) { $env:MINIO_CONSOLE_PORT } else { "9001" }),
    [string]$AppPort = $(if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }),
    [string]$MysqlImage = $(if ($env:MYSQL_IMAGE) { $env:MYSQL_IMAGE } else { "mysql:8.0" }),
    [string]$RedisImage = $(if ($env:REDIS_IMAGE) { $env:REDIS_IMAGE } else { "redis:7" }),
    [string]$MinioImage = $(if ($env:MINIO_IMAGE) { $env:MINIO_IMAGE } else { "minio/minio:latest" })
)

$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
$AppImage = "${ImageName}:${Tag}"

function Invoke-Docker {
    param([string[]]$Arguments)

    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-ContainerId {
    param([string]$Name)
    return (& docker ps -a -q --filter "name=^/$Name$").Trim()
}

function Test-ContainerRunning {
    param([string]$Name)
    $running = (& docker inspect -f "{{.State.Running}}" $Name 2>$null).Trim()
    return $running -eq "true"
}

function Ensure-Network {
    & docker network inspect $NetworkName *> $null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Creating network: $NetworkName"
        Invoke-Docker @("network", "create", $NetworkName)
    }
}

function Ensure-ContainerNetwork {
    param(
        [string]$Name,
        [string]$Alias
    )

    $networksJson = (& docker inspect -f "{{json .NetworkSettings.Networks}}" $Name 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $networksJson) {
        return
    }

    $networks = $networksJson | ConvertFrom-Json
    if ($networks.PSObject.Properties.Name -contains $NetworkName) {
        return
    }

    Write-Host "Connecting $Name to $NetworkName as $Alias"
    Invoke-Docker @("network", "connect", "--alias", $Alias, $NetworkName, $Name)
}

function Ensure-ExistingContainer {
    param(
        [string]$Name,
        [string]$Alias
    )

    if (-not (Test-ContainerRunning $Name)) {
        Write-Host "Starting existing container: $Name"
        Invoke-Docker @("start", $Name)
    } else {
        Write-Host "Using existing running container: $Name"
    }
    Ensure-ContainerNetwork $Name $Alias
}

function Ensure-MySql {
    $name = "cloud-album-mysql"
    if (Get-ContainerId $name) {
        Ensure-ExistingContainer $name "mysql"
        return
    }

    Write-Host "Creating container: $name"
    Invoke-Docker @(
        "run", "-d",
        "--name", $name,
        "--network", $NetworkName,
        "--network-alias", "mysql",
        "-p", "${MysqlPort}:3306",
        "-e", "MYSQL_ROOT_PASSWORD=$MysqlRootPassword",
        "-e", "MYSQL_DATABASE=$MysqlDatabase",
        "-v", "cloud_album_mysql_data:/var/lib/mysql",
        $MysqlImage,
        "--character-set-server=utf8mb4",
        "--collation-server=utf8mb4_unicode_ci"
    )
}

function Ensure-Redis {
    $name = "cloud-album-redis"
    if (Get-ContainerId $name) {
        Ensure-ExistingContainer $name "redis"
        return
    }

    Write-Host "Creating container: $name"
    Invoke-Docker @(
        "run", "-d",
        "--name", $name,
        "--network", $NetworkName,
        "--network-alias", "redis",
        "-p", "${RedisPort}:6379",
        "-v", "cloud_album_redis_data:/data",
        $RedisImage,
        "redis-server",
        "--appendonly", "yes"
    )
}

function Ensure-Minio {
    $name = "cloud-album-minio"
    if (Get-ContainerId $name) {
        Ensure-ExistingContainer $name "minio"
        return
    }

    Write-Host "Creating container: $name"
    Invoke-Docker @(
        "run", "-d",
        "--name", $name,
        "--network", $NetworkName,
        "--network-alias", "minio",
        "-p", "${MinioApiPort}:9000",
        "-p", "${MinioConsolePort}:9001",
        "-e", "MINIO_ROOT_USER=$MinioRootUser",
        "-e", "MINIO_ROOT_PASSWORD=$MinioRootPassword",
        "-v", "cloud_album_minio_data:/data",
        $MinioImage,
        "server", "/data",
        "--console-address", ":9001"
    )
}

function Ensure-App {
    $name = "cloud-album-app"
    if (Get-ContainerId $name) {
        Ensure-ExistingContainer $name "app"
        return
    }

    Write-Host "Creating container: $name"
    Invoke-Docker @(
        "run", "-d",
        "--name", $name,
        "--network", $NetworkName,
        "--network-alias", "app",
        "-p", "${AppPort}:8080",
        "-e", "DB_HOST=mysql",
        "-e", "DB_PORT=3306",
        "-e", "DB_NAME=$MysqlDatabase",
        "-e", "DB_USER=root",
        "-e", "DB_PASSWORD=$MysqlRootPassword",
        "-e", "REDIS_HOST=redis",
        "-e", "REDIS_PORT=6379",
        "-e", "MINIO_ENDPOINT=http://minio:9000",
        "-e", "MINIO_ACCESS_KEY=$MinioRootUser",
        "-e", "MINIO_SECRET_KEY=$MinioRootPassword",
        $AppImage
    )
}

Ensure-Network
Ensure-MySql
Ensure-Redis
Ensure-Minio
Ensure-App

Write-Host ""
Write-Host "Cloud Album stack is running."
Write-Host "App:   http://localhost:$AppPort"
Write-Host "MinIO: http://localhost:$MinioConsolePort"
Write-Host ""
Write-Host "Status:"
Invoke-Docker @("ps", "--filter", "name=cloud-album", "--format", "table {{.Names}}\t{{.Status}}\t{{.Ports}}")
