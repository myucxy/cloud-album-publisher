import { fileURLToPath } from 'node:url'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { copyFile, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises'
import { basename, dirname, extname, isAbsolute, join, relative, sep } from 'node:path'
import { spawn } from 'node:child_process'
import readline from 'node:readline/promises'
import { stdin as input, stdout as output } from 'node:process'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const rootDir = dirname(scriptDir)
const configPath = join(rootDir, 'release.config.json')
const releasesDir = join(rootDir, 'releases')
const frontendDistDir = join(rootDir, 'frontend', 'dist')
const backendStaticDir = join(rootDir, 'src', 'main', 'resources', 'static')
const pcPackagePath = join(rootDir, 'device-client', 'package.json')
const pcPackageLockPath = join(rootDir, 'device-client', 'package-lock.json')
const pcReleaseDir = join(rootDir, 'device-client', 'release')
const androidProjectDir = join(rootDir, 'android-client')
const androidBuildGradlePath = join(rootDir, 'android-client', 'app', 'build.gradle')
const androidKeystorePropertiesPath = join(rootDir, 'android-client', 'keystore.properties')
const androidReleaseDir = join(rootDir, 'android-client', 'app', 'build', 'outputs', 'apk', 'release')
const androidDebugDir = join(rootDir, 'android-client', 'app', 'build', 'outputs', 'apk', 'debug')
const dockerBuildDir = join(rootDir, 'docker-build')
const dockerStagedReleasesDir = join(dockerBuildDir, 'releases')
const backendTargetDir = join(rootDir, 'target')
const releaseManifestPath = join(releasesDir, 'manifest.json')
const validTargets = new Set(['frontend', 'pc', 'android', 'all', 'version', 'docker'])
const scriptedAnswers = hasFlag('--interactive') && !input.isTTY ? readFileSync(0, 'utf8').split(/\r?\n/) : null

let target = getArgValue('--target') || 'all'
let config = await readJson(configPath)
let persistedConfigSnapshot = JSON.stringify(config)
let versionBumped = false

await main()

async function main() {
  if (hasFlag('--interactive')) {
    await collectInteractiveOptions()
  }
  if (!validTargets.has(target)) {
    throw new Error(`Unsupported target: ${target}`)
  }
  const shouldBuildDocker = hasFlag('--docker') || target === 'docker'

  if (hasFlag('--bump')) {
    applyVersionBump()
  }

  const versionOptionsChanged = await applyVersionOptions()
  await syncConfiguredVersions(versionOptionsChanged)

  const builtArtifacts = {}
  if (hasFlag('--install-deps')) {
    await installNpmDependencies(target)
  }
  if (target === 'frontend' || target === 'all') {
    await buildFrontend()
  }
  if (target === 'pc' || target === 'all') {
    builtArtifacts.pc = await buildPc()
  }
  if (target === 'android' || target === 'all') {
    builtArtifacts.android = await buildAndroid()
  }

  await writeManifest(builtArtifacts)
  const dockerResult = shouldBuildDocker ? await buildDockerImage() : null
  await persistConfigIfChanged()
  printCompletionMessage(builtArtifacts, dockerResult)
}

async function collectInteractiveOptions() {
  const rl = readline.createInterface({ input, output })
  try {
    printCurrentConfig()
    console.log('提示：升版本会在确认并执行成功后写入配置；取消或打包失败不会占用版本号。')
    console.log('')
    const action = await askChoice(rl, '请选择操作', [
      ['all-bump', '全量发布：PC+Android 自动升版本，构建管理前端/PC/Android，更新 manifest'],
      ['all-docker-bump', '全量发布 + Docker：PC+Android 自动升版本，构建全部客户端后构建 Docker 镜像'],
      ['all', '全量重打包：不改版本，构建管理前端/PC/Android，更新 manifest'],
      ['all-docker', '全量重打包 + Docker：不改版本，构建全部客户端后构建 Docker 镜像'],
      ['pc-bump', 'PC 发布：PC 自动升版本，只构建 PC 客户端，更新 manifest'],
      ['pc', 'PC 重打包：不改版本，只构建 PC 客户端，更新 manifest'],
      ['android-bump', 'Android 发布：Android 自动升版本，只构建 Android APK，更新 manifest'],
      ['android', 'Android 重打包：不改版本，只构建 Android APK，更新 manifest'],
      ['frontend', '管理前端：只构建 frontend，并复制到后端 static'],
      ['docker', 'Docker 镜像：不打客户端，使用现有 releases/manifest.json 构建后端 jar 和镜像'],
      ['version-bump', '只升版本：PC+Android 自动升版本并同步到 package/Gradle，不打包'],
      ['version', '只同步版本：手动修改或同步 PC/Android 版本，不打包']
    ], 'all-bump')

    applyInteractiveAction(action)

    if (hasFlag('--bump')) {
      applyVersionBump()
    }

    if (target === 'version' && !hasFlag('--bump')) {
      const autoBump = await askYesNo(rl, '是否自动递增 PC 和 Android 版本？', true)
      if (autoBump) {
        setArgFlag('--bump')
        applyVersionBump()
      } else {
        await collectPcVersion(rl)
        await collectAndroidVersion(rl)
      }
    }

    if (!hasFlag('--bump') && ['all', 'pc', 'android'].includes(target)) {
      const shouldUpdateVersion = await askYesNo(rl, '是否手动修改版本号？', false)
      if (shouldUpdateVersion) {
        if (target === 'all') {
          const sameVersion = await askYesNo(rl, 'PC 和 Android 是否使用同一个版本号/versionCode？', false)
          if (sameVersion) {
            const version = await askRequired(rl, '统一版本号', firstText(config.pc?.version, config.android?.version, '1.0.0'))
            const versionCode = await askPositiveInt(rl, '统一 versionCode', Math.max(config.pc?.versionCode || 1, config.android?.versionCode || 1))
            setArgValue('--version', version)
            setArgValue('--version-code', String(versionCode))
          } else {
            await collectPcVersion(rl)
            await collectAndroidVersion(rl)
          }
        } else if (target === 'pc') {
          await collectPcVersion(rl)
        } else if (target === 'android') {
          await collectAndroidVersion(rl)
        }
      }
    }

    const platformsForReleaseFields = target === 'all' || target === 'version'
      ? ['pc', 'android']
      : target === 'pc' || target === 'android'
        ? [target]
        : []
    for (const platform of platformsForReleaseFields) {
      const platformConfig = ensurePlatformConfig(platform)
      if (await askYesNo(rl, `是否修改 ${platformLabel(platform)} 更新说明？`, false)) {
        platformConfig.releaseNotes = await askText(rl, `${platformLabel(platform)} 更新说明`, platformConfig.releaseNotes || '')
      }
      platformConfig.forceUpdate = await askYesNo(rl, `${platformLabel(platform)} 是否强制更新？`, Boolean(platformConfig.forceUpdate))
    }

    printSelectedOptions()
    const confirmed = await askYesNo(rl, '确认执行以上操作？', true)
    if (!confirmed) {
      console.log('已取消。')
      process.exit(0)
    }
  } finally {
    rl.close()
  }
}

function applyInteractiveAction(action) {
  const actionMap = {
    'all-bump': ['all', true],
    'all-docker-bump': ['all', true, true],
    all: ['all', false],
    'all-docker': ['all', false, true],
    'pc-bump': ['pc', true],
    pc: ['pc', false],
    'android-bump': ['android', true],
    android: ['android', false],
    frontend: ['frontend', false],
    docker: ['docker', false],
    'version-bump': ['version', true],
    version: ['version', false]
  }
  const [nextTarget, shouldBump, shouldBuildDocker] = actionMap[action]
  target = nextTarget
  if (shouldBump) {
    setArgFlag('--bump')
  }
  if (shouldBuildDocker) {
    setArgFlag('--docker')
  }
}

function applyVersionBump() {
  if (versionBumped) {
    return
  }
  versionBumped = true
  const platforms = target === 'pc' ? ['pc'] : target === 'android' ? ['android'] : ['pc', 'android']
  for (const platform of platforms) {
    const platformConfig = ensurePlatformConfig(platform)
    platformConfig.version = bumpPatchVersion(platformConfig.version || '0.0.0')
    platformConfig.versionCode = Number(platformConfig.versionCode || 0) + 1
  }
}

async function collectPcVersion(rl) {
  const version = await askRequired(rl, 'PC 版本号', config.pc?.version || '0.1.0')
  const versionCode = await askPositiveInt(rl, 'PC versionCode', config.pc?.versionCode || 1)
  setArgValue('--pc-version', version)
  setArgValue('--pc-version-code', String(versionCode))
}

async function collectAndroidVersion(rl) {
  const version = await askRequired(rl, 'Android versionName', config.android?.version || '0.1.0')
  const versionCode = await askPositiveInt(rl, 'Android versionCode', config.android?.versionCode || 1)
  setArgValue('--android-version', version)
  setArgValue('--android-version-code', String(versionCode))
}

async function applyVersionOptions() {
  const sharedVersion = getArgValue('--version')
  const sharedVersionCode = getIntegerArgValue('--version-code')
  const pcVersion = getArgValue('--pc-version') || sharedVersion
  const pcVersionCode = getIntegerArgValue('--pc-version-code') ?? sharedVersionCode
  const androidVersion = getArgValue('--android-version') || sharedVersion
  const androidVersionCode = getIntegerArgValue('--android-version-code') ?? sharedVersionCode

  let changed = false
  if (pcVersion) {
    ensurePlatformConfig('pc').version = pcVersion
    changed = true
  }
  if (pcVersionCode !== null && pcVersionCode !== undefined) {
    ensurePlatformConfig('pc').versionCode = pcVersionCode
    changed = true
  }
  if (androidVersion) {
    ensurePlatformConfig('android').version = androidVersion
    changed = true
  }
  if (androidVersionCode !== null && androidVersionCode !== undefined) {
    ensurePlatformConfig('android').versionCode = androidVersionCode
    changed = true
  }

  if (changed) {
    return true
  }
  return changed
}

async function persistConfigIfChanged() {
  const nextSnapshot = JSON.stringify(config)
  if (nextSnapshot === persistedConfigSnapshot) {
    return
  }
  await writeJson(configPath, config)
  persistedConfigSnapshot = nextSnapshot
}

async function syncConfiguredVersions(force = false) {
  if (force || target === 'pc' || target === 'all' || target === 'version') {
    const version = requireConfiguredVersion('pc')
    await updatePcVersion(version)
  }
  if (force || target === 'android' || target === 'all' || target === 'version') {
    const version = requireConfiguredVersion('android')
    const versionCode = requireConfiguredVersionCode('android')
    await updateAndroidVersion(version, versionCode)
  }
}

async function updatePcVersion(version) {
  const packageJson = await readJson(pcPackagePath)
  if (packageJson.version !== version) {
    packageJson.version = version
    await writeJson(pcPackagePath, packageJson)
  }

  if (existsSync(pcPackageLockPath)) {
    const packageLock = await readJson(pcPackageLockPath)
    let changed = false
    if (packageLock.version !== version) {
      packageLock.version = version
      changed = true
    }
    if (packageLock.packages?.[''] && packageLock.packages[''].version !== version) {
      packageLock.packages[''].version = version
      changed = true
    }
    if (changed) {
      await writeJson(pcPackageLockPath, packageLock)
    }
  }
}

async function updateAndroidVersion(version, versionCode) {
  const content = await readFile(androidBuildGradlePath, 'utf8')
  let next = content.replace(/versionCode\s+\d+/, `versionCode ${versionCode}`)
  next = next.replace(/versionName\s+["'][^"']+["']/, `versionName "${version}"`)
  if (next !== content) {
    await writeFile(androidBuildGradlePath, next, 'utf8')
  }
}

async function buildFrontend() {
  await run('npm', ['--prefix', 'frontend', 'run', 'build'])
  await copyFrontendDist()
}

async function installNpmDependencies(buildTarget) {
  if (buildTarget === 'version' || buildTarget === 'docker') {
    return
  }
  await requireCommand('npm')
  if (buildTarget === 'frontend' || buildTarget === 'all') {
    await run('npm', ['--prefix', 'frontend', 'install', '--prefer-offline'])
  }
  if (buildTarget === 'pc' || buildTarget === 'all') {
    await run('npm', ['--prefix', 'device-client', 'install', '--prefer-offline'])
  }
}

async function copyFrontendDist() {
  await rm(backendStaticDir, { recursive: true, force: true })
  await mkdir(backendStaticDir, { recursive: true })
  await copyDirectory(frontendDistDir, backendStaticDir, path => !basename(path).startsWith('__cc_'))
}

async function copyDirectory(sourceDir, targetDir, filter) {
  const entries = await readdir(sourceDir, { withFileTypes: true })
  await mkdir(targetDir, { recursive: true })
  for (const entry of entries) {
    const sourcePath = join(sourceDir, entry.name)
    if (filter && !filter(sourcePath)) {
      continue
    }
    const targetPath = join(targetDir, entry.name)
    if (entry.isDirectory()) {
      await copyDirectory(sourcePath, targetPath, filter)
    } else if (entry.isFile()) {
      await copyFile(sourcePath, targetPath)
    }
  }
}

async function buildPc() {
  await run('npm', ['--prefix', 'device-client', 'run', 'package:win'])
  const artifact = await findNewestFile(pcReleaseDir, file => {
    const name = file.toLowerCase()
    return (name.endsWith('.exe') || name.endsWith('.zip')) && !name.includes('blockmap')
  })
  if (!artifact) {
    throw new Error('PC package artifact was not found in device-client/release')
  }
  const version = config.pc.version
  return copyReleaseArtifact('pc', artifact, `cloud-album-device-pc-${version}${extname(artifact)}`)
}

async function buildAndroid() {
  const variant = getAndroidVariant()
  if (variant === 'release') {
    await ensureAndroidReleaseKeystore()
  }
  await run(resolveGradleCommand(), [`:app:assemble${capitalize(variant)}`], androidProjectDir)
  const artifactDir = variant === 'debug' ? androidDebugDir : androidReleaseDir
  const artifact = await findNewestFile(artifactDir, file => {
    const name = basename(file).toLowerCase()
    return name.endsWith('.apk') && !name.includes('unsigned')
  })
  if (!artifact) {
    throw new Error(`Android ${variant} APK artifact was not found or is unsigned`)
  }
  const version = config.android.version
  const suffix = variant === 'debug' ? '-debug' : ''
  return copyReleaseArtifact('android', artifact, `cloud-album-device-android-${version}${suffix}.apk`)
}

async function ensureAndroidReleaseKeystore() {
  if (!existsSync(androidKeystorePropertiesPath)) {
    throw new Error('Android release build requires android-client/keystore.properties')
  }

  const properties = parseProperties(await readFile(androidKeystorePropertiesPath, 'utf8'))
  const storeFile = requireProperty(properties, 'storeFile', androidKeystorePropertiesPath)
  const storePassword = requireConfiguredSecret(properties, 'storePassword', androidKeystorePropertiesPath)
  const keyAlias = requireProperty(properties, 'keyAlias', androidKeystorePropertiesPath)
  const keyPassword = requireConfiguredSecret(properties, 'keyPassword', androidKeystorePropertiesPath)
  const keystorePath = resolveAndroidProjectPath(storeFile)

  if (existsSync(keystorePath)) {
    console.log(`Android release 签名文件已存在：${toDisplayPath(relative(rootDir, keystorePath))}`)
    return
  }

  await requireCommand('keytool')
  await mkdir(dirname(keystorePath), { recursive: true })

  const validity = positiveIntegerOrDefault(properties.validityDays, 36500)
  const keySize = positiveIntegerOrDefault(properties.keySize, 2048)
  const keyAlg = properties.keyAlg || 'RSA'
  const dname = properties.dname || 'CN=Cloud Album Publisher, OU=Cloud Album, O=Cloud Album, L=Shanghai, ST=Shanghai, C=CN'

  console.log(`Android release 签名文件不存在，正在生成：${toDisplayPath(relative(rootDir, keystorePath))}`)
  await runDirect('keytool', [
    '-genkeypair',
    '-v',
    '-storetype',
    properties.storeType || 'PKCS12',
    '-keystore',
    keystorePath,
    '-alias',
    keyAlias,
    '-keyalg',
    keyAlg,
    '-keysize',
    String(keySize),
    '-validity',
    String(validity),
    '-storepass',
    storePassword,
    '-keypass',
    keyPassword,
    '-dname',
    dname
  ])
}

async function copyReleaseArtifact(platform, source, fileName) {
  const platformDir = join(releasesDir, platform)
  await mkdir(platformDir, { recursive: true })
  const destination = join(platformDir, fileName)
  await copyFile(source, destination)
  const fileStat = await stat(destination)
  return {
    fileName,
    relativePath: toUrlPath(relative(releasesDir, destination)),
    size: fileStat.size,
    sha256: await sha256(destination)
  }
}

async function writeManifest(artifacts) {
  const hasArtifacts = Object.keys(artifacts).length > 0
  if (!hasArtifacts) {
    return
  }
  await mkdir(releasesDir, { recursive: true })
  const existing = existsSync(join(releasesDir, 'manifest.json'))
    ? await readJson(join(releasesDir, 'manifest.json'))
    : { generatedAt: null, platforms: {} }

  const manifest = {
    generatedAt: new Date().toISOString(),
    platforms: existing.platforms || {}
  }

  if (artifacts.pc) {
    manifest.platforms.pc = buildPlatformManifest('pc', artifacts.pc)
  }
  if (artifacts.android) {
    manifest.platforms.android = buildPlatformManifest('android', artifacts.android)
  }

  await writeFile(join(releasesDir, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8')
  await pruneReleaseArtifacts(manifest)
}

async function buildDockerImage() {
  const options = resolveDockerOptions()
  await requireCommand('docker')
  await requireCommand('mvn')

  const { tag, shouldPersistVersion } = await resolveDockerTag(options)
  const versionImageRef = `${options.imageName}:${tag}`
  const latestImageRef = `${options.imageName}:latest`

  console.log('')
  console.log(`Docker 镜像版本：${tag}`)
  console.log('[Docker 1/3] 构建 Spring Boot jar...')
  await buildBackendJar(options)

  console.log('[Docker 2/3] 准备 Docker build 上下文...')
  await prepareDockerBuildContext(options)

  console.log(`[Docker 3/3] 构建镜像 ${versionImageRef} 和 ${latestImageRef}...`)
  await run('docker', [
    'build',
    '-f',
    'Dockerfile',
    '--build-arg',
    `BASE_IMAGE=${options.baseImage}`,
    '-t',
    versionImageRef,
    '-t',
    latestImageRef,
    '.'
  ])

  if (shouldPersistVersion) {
    await writeDockerVersion(options.versionFilePath, tag)
  }

  return { versionImageRef, latestImageRef }
}

function resolveDockerOptions() {
  const versionIncrement = getArgValue('--docker-version-increment') || process.env.DOCKER_VERSION_INCREMENT || 'patch'
  if (!['major', 'minor', 'patch', 'none'].includes(versionIncrement)) {
    throw new Error('--docker-version-increment must be major, minor, patch, or none')
  }

  return {
    imageName: getArgValue('--docker-image') || process.env.DOCKER_IMAGE || 'myucxy/cloud-album-publisher',
    explicitTag: getArgValue('--docker-tag') || process.env.DOCKER_TAG || null,
    versionFilePath: resolveRootPath(getArgValue('--docker-version-file') || process.env.DOCKER_VERSION_FILE || '.docker-version'),
    versionIncrement,
    baseImage: getArgValue('--docker-base-image') || process.env.DOCKER_BASE_IMAGE || 'eclipse-temurin:17-jre-jammy',
    mavenRepo: resolveRootPath(getArgValue('--maven-repo') || process.env.MAVEN_REPO || join('.m2', 'repository')),
    includeAllReleases: hasFlag('--docker-include-all-releases') || isTruthyEnv(process.env.DOCKER_INCLUDE_ALL_RELEASES),
    runTests: hasFlag('--docker-run-tests') || hasFlag('--run-tests') || isTruthyEnv(process.env.DOCKER_RUN_TESTS) || isTruthyEnv(process.env.RUN_TESTS)
  }
}

async function resolveDockerTag(options) {
  if (options.explicitTag) {
    console.log(`使用指定 Docker tag: ${options.explicitTag}`)
    return { tag: options.explicitTag, shouldPersistVersion: false }
  }

  const currentVersion = await readDockerVersion(options.versionFilePath)
  const nextVersion = stepDockerVersion(currentVersion, options.versionIncrement)
  console.log(`Docker version: ${currentVersion} -> ${nextVersion}`)
  return { tag: nextVersion, shouldPersistVersion: true }
}

async function readDockerVersion(path) {
  if (!existsSync(path)) {
    return '0.0.0'
  }
  const value = (await readFile(path, 'utf8')).trim()
  return value || '0.0.0'
}

function stepDockerVersion(currentVersion, increment) {
  const match = String(currentVersion).match(/^(v?)(\d+)\.(\d+)\.(\d+)$/)
  if (!match) {
    throw new Error(`Docker version '${currentVersion}' is invalid. Use semantic version format like 1.0.0 or v1.0.0.`)
  }

  const prefix = match[1]
  let major = Number(match[2])
  let minor = Number(match[3])
  let patch = Number(match[4])

  if (increment === 'major') {
    major += 1
    minor = 0
    patch = 0
  } else if (increment === 'minor') {
    minor += 1
    patch = 0
  } else if (increment === 'patch') {
    patch += 1
  }

  return `${prefix}${major}.${minor}.${patch}`
}

async function writeDockerVersion(path, version) {
  await mkdir(dirname(path), { recursive: true })
  await writeFile(path, `${version}\n`, 'ascii')
}

async function buildBackendJar(options) {
  const args = [`-Dmaven.repo.local=${options.mavenRepo}`, 'clean', 'package']
  if (!options.runTests) {
    args.push('-DskipTests')
  }
  await run('mvn', args)
}

async function prepareDockerBuildContext(options) {
  await rm(dockerBuildDir, { recursive: true, force: true })
  await mkdir(dockerStagedReleasesDir, { recursive: true })

  const jar = await findNewestFile(backendTargetDir, file => {
    const name = basename(file).toLowerCase()
    return name.endsWith('.jar') && !name.endsWith('.original')
  })
  if (!jar) {
    throw new Error('No runnable jar was found in target/.')
  }
  await copyFile(jar, join(dockerBuildDir, 'app.jar'))

  if (!existsSync(releaseManifestPath)) {
    console.warn('Warning: releases/manifest.json was not found; the image will not contain client download metadata.')
    return
  }

  if (options.includeAllReleases) {
    await copyDirectory(releasesDir, dockerStagedReleasesDir)
    return
  }

  await copyFile(releaseManifestPath, join(dockerStagedReleasesDir, 'manifest.json'))
  const manifest = await readJson(releaseManifestPath)
  for (const relativePath of collectDockerReleaseArtifactPaths(manifest)) {
    await copyReleaseFileToDockerContext(relativePath)
  }
}

function collectDockerReleaseArtifactPaths(manifest) {
  const paths = new Set()
  const platforms = manifest.platforms || {}
  for (const [platform, platformRelease] of Object.entries(platforms)) {
    const channels = platformRelease?.channels || {}
    for (const channel of Object.values(channels)) {
      const relativePath = releasePathFromDownloadUrl(channel?.downloadUrl) || fallbackReleasePathFromDownloadUrl(channel?.downloadUrl)
      if (relativePath) {
        paths.add(relativePath)
      } else if (channel?.fileName) {
        paths.add(toUrlPath(join(platform, channel.fileName)))
      }
    }
  }
  return paths
}

async function copyReleaseFileToDockerContext(relativePath) {
  const normalizedRelativePath = String(relativePath).split(/[\\/]+/).join(sep)
  const source = join(releasesDir, normalizedRelativePath)
  if (!existsSync(source)) {
    throw new Error(`Release artifact referenced by manifest was not found: releases/${toUrlPath(normalizedRelativePath)}`)
  }
  const destination = join(dockerStagedReleasesDir, normalizedRelativePath)
  await mkdir(dirname(destination), { recursive: true })
  await copyFile(source, destination)
}

function buildPlatformManifest(platform, artifact) {
  const channel = config.channel || 'stable'
  const platformConfig = config[platform] || {}
  return {
    channels: {
      [channel]: {
        version: platformConfig.version,
        versionCode: platformConfig.versionCode,
        forceUpdate: Boolean(platformConfig.forceUpdate),
        downloadUrl: `${trimTrailingSlash(config.downloadsBasePath || '/downloads')}/${artifact.relativePath}`,
        fileName: artifact.fileName,
        sha256: artifact.sha256,
        size: artifact.size,
        releaseNotes: platformConfig.releaseNotes || '',
        publishedAt: new Date().toISOString()
      }
    }
  }
}

async function pruneReleaseArtifacts(manifest) {
  const keepFiles = collectManifestArtifactPaths(manifest)
  const files = await listFiles(releasesDir)
  for (const file of files) {
    const relativePath = toUrlPath(relative(releasesDir, file))
    if (relativePath === 'manifest.json' || keepFiles.has(relativePath)) {
      continue
    }
    await rm(file, { force: true })
  }
}

function collectManifestArtifactPaths(manifest) {
  const keepFiles = new Set()
  const platforms = manifest.platforms || {}
  for (const [platform, platformRelease] of Object.entries(platforms)) {
    const channels = platformRelease?.channels || {}
    for (const channel of Object.values(channels)) {
      const relativePath = releasePathFromDownloadUrl(channel?.downloadUrl)
      if (relativePath) {
        keepFiles.add(relativePath)
      } else if (channel?.fileName) {
        keepFiles.add(toUrlPath(join(platform, channel.fileName)))
      }
    }
  }
  return keepFiles
}

function releasePathFromDownloadUrl(downloadUrl) {
  if (!downloadUrl) {
    return null
  }
  const marker = `${trimTrailingSlash(config.downloadsBasePath || '/downloads')}/`
  const text = String(downloadUrl)
  const index = text.indexOf(marker)
  return index === -1 ? null : text.slice(index + marker.length)
}

async function findNewestFile(dir, predicate) {
  if (!existsSync(dir)) {
    return null
  }
  const files = await listFiles(dir)
  const candidates = []
  for (const file of files) {
    if (predicate(file)) {
      candidates.push({ file, mtimeMs: (await stat(file)).mtimeMs })
    }
  }
  candidates.sort((a, b) => b.mtimeMs - a.mtimeMs)
  return candidates[0]?.file || null
}

async function listFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...await listFiles(path))
    } else if (entry.isFile()) {
      files.push(path)
    }
  }
  return files
}

async function sha256(file) {
  const buffer = await readFile(file)
  return createHash('sha256').update(buffer).digest('hex')
}

async function readJson(path) {
  return JSON.parse(await readFile(path, 'utf8'))
}

async function writeJson(path, data) {
  await writeFile(path, `${JSON.stringify(data, null, 2)}\n`, 'utf8')
}

function parseProperties(content) {
  const properties = {}
  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line || line.startsWith('#') || line.startsWith('!')) {
      continue
    }
    const separatorIndex = findPropertySeparatorIndex(line)
    const key = separatorIndex === -1 ? line : line.slice(0, separatorIndex).trim()
    const value = separatorIndex === -1 ? '' : line.slice(separatorIndex + 1).trim()
    if (key) {
      properties[key] = value
    }
  }
  return properties
}

function findPropertySeparatorIndex(line) {
  const equalsIndex = line.indexOf('=')
  const colonIndex = line.indexOf(':')
  if (equalsIndex === -1) {
    return colonIndex
  }
  if (colonIndex === -1) {
    return equalsIndex
  }
  return Math.min(equalsIndex, colonIndex)
}

function requireProperty(properties, name, sourcePath) {
  const value = properties[name]
  if (!value) {
    throw new Error(`${toDisplayPath(relative(rootDir, sourcePath))} is missing required property: ${name}`)
  }
  return value
}

function requireConfiguredSecret(properties, name, sourcePath) {
  const value = requireProperty(properties, name, sourcePath)
  if (value.startsWith('CHANGE_ME')) {
    throw new Error(`${toDisplayPath(relative(rootDir, sourcePath))} must replace ${name} before building Android release`)
  }
  return value
}

async function requireCommand(command) {
  const checker = process.platform === 'win32' ? 'where' : 'which'
  const args = [command]
  try {
    await runQuiet(checker, args)
  } catch {
    throw new Error(`Required command was not found: ${command}`)
  }
}

function runQuiet(command, args, cwd = rootDir) {
  return new Promise((resolve, reject) => {
    const child = spawn(resolveCommand(command), resolveArgs(command, args), { cwd, stdio: 'ignore' })
    child.on('error', reject)
    child.on('exit', code => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`))
      }
    })
  })
}

function runDirect(command, args, cwd = rootDir) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, stdio: 'inherit' })
    child.on('error', reject)
    child.on('exit', code => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`))
      }
    })
  })
}

function run(command, args, cwd = rootDir) {
  return new Promise((resolve, reject) => {
    const child = spawn(resolveCommand(command), resolveArgs(command, args), { cwd, stdio: 'inherit' })
    child.on('error', reject)
    child.on('exit', code => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`))
      }
    })
  })
}

function resolveCommand(command) {
  if (process.platform !== 'win32') {
    return command
  }
  return 'cmd.exe'
}

function resolveArgs(command, args) {
  if (process.platform !== 'win32') {
    return args
  }
  const executable = command === 'npm' ? 'npm.cmd' : command === 'gradle' ? 'gradle.bat' : command
  return ['/d', '/s', '/c', [executable, ...args].map(quoteWindowsArg).join(' ')]
}

function quoteWindowsArg(value) {
  const text = String(value)
  return /^[A-Za-z0-9_./:=@\\-]+$/.test(text) ? text : `"${text.replace(/"/g, '\\"')}"`
}

function ensurePlatformConfig(platform) {
  config[platform] = config[platform] || {}
  return config[platform]
}

function requireConfiguredVersion(platform) {
  const version = config[platform]?.version
  if (!version) {
    throw new Error(`${platform}.version is required in release.config.json`)
  }
  return version
}

function requireConfiguredVersionCode(platform) {
  const versionCode = config[platform]?.versionCode
  if (!Number.isInteger(versionCode) || versionCode < 1) {
    throw new Error(`${platform}.versionCode must be a positive integer in release.config.json`)
  }
  return versionCode
}

function getArgValue(name) {
  const index = process.argv.indexOf(name)
  if (index === -1) {
    return null
  }
  return process.argv[index + 1] || null
}

function setArgValue(name, value) {
  const index = process.argv.indexOf(name)
  if (index === -1) {
    process.argv.push(name, value)
  } else {
    process.argv[index + 1] = value
  }
}

function setArgFlag(name) {
  if (!process.argv.includes(name)) {
    process.argv.push(name)
  }
}

function bumpPatchVersion(version) {
  const text = String(version || '0.0.0')
  const match = text.match(/^(.*?)(\d+)([^\d]*)$/)
  if (!match) {
    return `${text}.1`
  }
  const prefix = match[1]
  const numberText = match[2]
  const suffix = match[3]
  const nextNumber = String(Number(numberText) + 1).padStart(numberText.length, '0')
  return `${prefix}${nextNumber}${suffix}`
}

function getIntegerArgValue(name) {
  const value = getArgValue(name)
  if (value === null) {
    return null
  }
  const parsed = Number(value)
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${name} must be a positive integer`)
  }
  return parsed
}

function hasFlag(name) {
  return process.argv.includes(name)
}

function resolveGradleCommand() {
  const wrapper = process.platform === 'win32' ? join(rootDir, 'android-client', 'gradlew.bat') : join(rootDir, 'android-client', 'gradlew')
  if (!existsSync(wrapper)) {
    return 'gradle'
  }
  return process.platform === 'win32' ? '.\\gradlew.bat' : './gradlew'
}

function getAndroidVariant() {
  const value = (getArgValue('--android-variant') || config.android?.variant || 'debug').toLowerCase()
  if (value !== 'debug' && value !== 'release') {
    throw new Error('--android-variant must be debug or release')
  }
  return value
}

function capitalize(value) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function toUrlPath(path) {
  return path.split(sep).join('/')
}

function trimTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '')
}

function resolveRootPath(path) {
  return isAbsolute(path) ? path : join(rootDir, path)
}

function resolveAndroidProjectPath(path) {
  return isAbsolute(path) ? path : join(androidProjectDir, path)
}

function positiveIntegerOrDefault(value, defaultValue) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : defaultValue
}

function toDisplayPath(path) {
  return toUrlPath(path)
}

function isTruthyEnv(value) {
  return ['1', 'true', 'yes', 'y', 'on'].includes(String(value || '').trim().toLowerCase())
}

function fallbackReleasePathFromDownloadUrl(downloadUrl) {
  const match = String(downloadUrl || '').match(/\/downloads\/(.+)$/)
  return match ? match[1] : null
}

function firstText(...values) {
  return values.find(value => typeof value === 'string' && value.trim())
}

function platformLabel(platform) {
  return platform === 'pc' ? 'PC' : platform === 'android' ? 'Android' : platform
}

function printCurrentConfig() {
  console.log('')
  console.log('=== 云影发布打包工具 ===')
  console.log(`当前 PC: ${config.pc?.version || '-'} / versionCode ${config.pc?.versionCode || '-'}`)
  console.log(`当前 Android: ${config.android?.version || '-'} / versionCode ${config.android?.versionCode || '-'}`)
  console.log('')
}

function printSelectedOptions() {
  console.log('')
  console.log('将执行：')
  console.log(`- 目标：${target}`)
  if (hasFlag('--docker') || target === 'docker') {
    console.log('- Docker 镜像：是')
  }
  console.log(`- PC: ${config.pc?.version || '-'} / versionCode ${config.pc?.versionCode || '-'} / forceUpdate=${Boolean(config.pc?.forceUpdate)}`)
  console.log(`- Android: ${config.android?.version || '-'} / versionCode ${config.android?.versionCode || '-'} / forceUpdate=${Boolean(config.android?.forceUpdate)}`)
  console.log('')
}

function printCompletionMessage(artifacts, dockerResult) {
  if (target === 'version') {
    console.log('版本同步完成。')
  } else if (target === 'docker') {
    console.log('Docker 打包完成。')
  } else {
    console.log('打包完成。')
  }
  if (artifacts.pc) {
    console.log(`PC: releases/${artifacts.pc.relativePath}`)
  }
  if (artifacts.android) {
    console.log(`Android: releases/${artifacts.android.relativePath}`)
  }
  if (Object.keys(artifacts).length > 0) {
    console.log('Manifest: releases/manifest.json')
  }
  if (dockerResult) {
    console.log(`Docker: ${dockerResult.versionImageRef}`)
    console.log(`Docker latest: ${dockerResult.latestImageRef}`)
    console.log(`示例运行: docker run --rm -p 8080:8080 -e DB_HOST=host.docker.internal -e DB_PORT=3306 -e DB_NAME=cloud_album -e DB_USER=root -e DB_PASSWORD=root -e REDIS_HOST=host.docker.internal -e MINIO_ENDPOINT=http://host.docker.internal:9000 ${dockerResult.latestImageRef}`)
  }
}

async function askRaw(rl, question) {
  if (scriptedAnswers) {
    const answer = scriptedAnswers.length ? scriptedAnswers.shift() : ''
    output.write(`${question}${answer}\n`)
    return answer
  }
  return rl.question(question)
}

async function askChoice(rl, question, choices, defaultValue) {
  choices.forEach(([value, label], index) => {
    console.log(`${index + 1}. ${label} [${value}]`)
  })
  const defaultIndex = Math.max(choices.findIndex(([value]) => value === defaultValue), 0)
  while (true) {
    const answer = (await askRaw(rl, `${question} [${defaultIndex + 1}]: `)).trim()
    if (!answer) {
      return choices[defaultIndex][0]
    }
    const numeric = Number(answer)
    if (Number.isInteger(numeric) && numeric >= 1 && numeric <= choices.length) {
      return choices[numeric - 1][0]
    }
    const found = choices.find(([value]) => value === answer)
    if (found) {
      return found[0]
    }
    console.log('输入无效，请重新选择。')
  }
}

async function askYesNo(rl, question, defaultValue) {
  const suffix = defaultValue ? 'Y/n' : 'y/N'
  while (true) {
    const answer = (await askRaw(rl, `${question} [${suffix}]: `)).trim().toLowerCase()
    if (!answer) {
      return defaultValue
    }
    if (['y', 'yes', '是'].includes(answer)) {
      return true
    }
    if (['n', 'no', '否'].includes(answer)) {
      return false
    }
    console.log('请输入 y 或 n。')
  }
}

async function askText(rl, question, defaultValue = '') {
  const answer = await askRaw(rl, `${question}${defaultValue ? ` [${defaultValue}]` : ''}: `)
  return answer.trim() || defaultValue
}

async function askRequired(rl, question, defaultValue = '') {
  while (true) {
    const answer = await askText(rl, question, defaultValue)
    if (answer) {
      return answer
    }
    console.log('不能为空。')
  }
}

async function askPositiveInt(rl, question, defaultValue) {
  while (true) {
    const answer = await askText(rl, question, String(defaultValue || 1))
    const parsed = Number(answer)
    if (Number.isInteger(parsed) && parsed >= 1) {
      return parsed
    }
    console.log('请输入正整数。')
  }
}
