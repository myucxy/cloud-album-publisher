# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, run, and test commands

### Backend
- Compile only:
  - `mvn -q -DskipTests compile`
- Run tests:
  - `mvn test`
- Run a single test class:
  - `mvn -Dtest=ClassName test`
- Run a single test method:
  - `mvn -Dtest=ClassName#methodName test`
- Start backend in dev profile:
  - `mvn spring-boot:run`
- Package jar:
  - `mvn clean package -DskipTests`

### Frontend (`frontend/`)
- Install deps:
  - `npm install`
- Start dev server:
  - `npm run dev`
- Build:
  - `npm run build`
- Preview build:
  - `npm run preview`

### Device client (`device-client/`)
- Install deps:
  - `npm install`
- Start Electron + renderer dev mode:
  - `npm run dev`
- Build renderer:
  - `npm run build`
- Start Electron against built assets:
  - `npm run start`

### Android client (`android-client/`)
- Build debug apk:
  - `gradle -p android-client :app:assembleDebug`
- Build debug apk with isolated Gradle home (avoids machine-global init scripts interfering):
  - `GRADLE_USER_HOME="android-client/.gradle-user-home" gradle -p android-client :app:assembleDebug --no-daemon`
- If local Android SDK is not auto-detected, set `android-client/local.properties` with `sdk.dir=...`


- Default `application.yml` uses MySQL + Redis + MinIO; default profile is `dev`.
- `application-dev.yml` switches the backend to H2 file DB at `./data/cloud_album.mv.db` and always runs `src/main/resources/db/h2/schema.sql` on startup.
- If H2 dev schema seems stale after code changes, restart once so schema upgrades run. If the local file DB is still inconsistent, delete `data/cloud_album.*` and restart.
- Backend logs now go to:
  - all logs: `logs/cloud-album-publisher.log`
  - error-only logs: `logs/cloud-album-publisher-error.log`
- Auth whitelist is defined in `src/main/java/com/cloudalbum/publisher/config/SecurityConfig.java`; most `/api/v1/**` endpoints require JWT except auth, Swagger, health, and H2 console.

## Big-picture architecture

This repo is a Spring Boot backend plus two Vue apps:
- `src/main/java/...`: backend APIs, auth, album, media-source, device, distribution, review, audit
- `frontend/`: admin/user management UI
- `device-client/`: Electron/Vue playback client for bound devices

The backend uses MyBatis-Plus for persistence, MinIO for binary storage and generated thumbnails, Redis for token/session support, and Spring Security JWT for auth.

## Important backend subsystems

### 1. Media source abstraction (critical extension point)
The codebase now has a unified external media-source pipeline that supports SMB, FTP, SFTP, and WebDAV.

Key pieces:
- `src/main/java/com/cloudalbum/publisher/mediasource/type/MediaSourceTypeHandler.java`
- `src/main/java/com/cloudalbum/publisher/mediasource/service/impl/MediaSourceServiceImpl.java`
- `src/main/java/com/cloudalbum/publisher/mediasource/smb/MediaSourceFileClient.java`
- protocol handlers under `.../mediasource/type/`
- protocol file clients under `.../mediasource/{smb,ftp,sftp,webdav}/`

How it works:
- `MediaSourceTypeHandler` owns per-protocol config normalization, credential normalization, summary formatting, and connection construction.
- `MediaSourceFileClient` owns `list/open` operations against the remote source.
- `MediaSourceServiceImpl` is the shared orchestration layer for:
  - creating/updating media sources
  - bound-path-safe browsing
  - proxying external media content through backend endpoints
  - importing remote files into internal `t_media`
  - generating cached thumbnails/content in MinIO for external files

Important rule:
- When adding a new protocol or changing protocol behavior, keep the service flow in `MediaSourceServiceImpl` shared. Put protocol-specific behavior in a new handler and file client instead of branching the service everywhere.

### 2. Album model now supports internal and external media
Album content and album covers are no longer internal-media-only.

Key files:
- `src/main/java/com/cloudalbum/publisher/album/entity/Album.java`
- `src/main/java/com/cloudalbum/publisher/album/entity/AlbumMedia.java`
- `src/main/java/com/cloudalbum/publisher/album/dto/AlbumAddContentRequest.java`
- `src/main/java/com/cloudalbum/publisher/album/dto/AlbumCoverRequest.java`
- `src/main/java/com/cloudalbum/publisher/album/dto/AlbumContentResponse.java`
- `src/main/java/com/cloudalbum/publisher/album/service/impl/AlbumServiceImpl.java`

Current model:
- Internal album media uses `mediaId` and resolves through `t_media`.
- External album media uses:
  - `sourceId`
  - `sourceType`
  - `sourceName`
  - `externalMediaKey`
  - `filePath`
  - `fileName`
  - `contentType`
  - `mediaType`
- External cover fields are stored directly on `Album` with parallel `coverSource*` fields.

Important rule:
- Any feature touching album content or cover must preserve both branches:
  - internal media branch
  - external media branch
- Do not reintroduce assumptions that `mediaId` is always present.

### 3. Distribution/device pipeline filters and queue behavior
Device queue data is built from active distributions and filtered again at pull time.

Key files:
- `src/main/java/com/cloudalbum/publisher/distribution/service/impl/DistributionServiceImpl.java`
- `src/main/java/com/cloudalbum/publisher/device/service/impl/DeviceServiceImpl.java`
- `src/main/java/com/cloudalbum/publisher/device/dto/DevicePullResponse.java`
- `device-client/src/stores/player.js`
- `device-client/src/components/MediaPlayer.vue`
- `device-client/src/views/PlayerView.vue`

Current behavior:
- Distribution activation validates both internal album media and external album media.
- Device pull builds `mediaList` from album items and supports external media proxy URLs.
- The device client must treat external media as first-class queue items, not assume only internal `id`-based media.

Important rule:
- If you change `DevicePullResponse.MediaItem`, verify device-client identity logic and playback logic together.
- The player store uses a stable media identity concept; avoid regressions that depend only on `media.id`.

### 4. Visibility rules matter for device delivery
Album visibility is not just UI metadata; it changes whether devices receive queue items.

Visibility values:
- `PUBLIC`
- `PRIVATE`
- `DEVICE_ONLY`

Current enforced rule:
- `PRIVATE` albums must not be distributed to devices.
- The backend now blocks activation of distributions for `PRIVATE` albums with a clear business error.
- The frontend distribution UI warns users that private albums cannot be pushed to devices.

Important rule:
- If you change visibility semantics in one place, audit all of these together:
  - album access checks in album service
  - distribution activation validation
  - device pull filtering
  - distribution UI messaging

### 5. H2 and MySQL schema drift is a real risk
The repo supports both MySQL migrations and an H2 dev schema.

Key files:
- MySQL baseline: `src/main/resources/db/migration/V01__init_schema.sql`
- Incremental migration for external album media: `src/main/resources/db/migration/V09__album_external_media_reference.sql`
- H2 dev schema: `src/main/resources/db/h2/schema.sql`

Important rule:
- Any DB change that affects runtime code must be reflected in both:
  - MySQL migration files
  - H2 `schema.sql`
- H2 dev issues have previously appeared when `t_album_media.media_id` remained NOT NULL while external album media inserts used `media_id = null`.
- Before claiming a DB-related fix is done, make sure H2 startup upgrade logic covers existing local file DBs, not just fresh databases.

## Frontend architecture notes

### Admin/user frontend (`frontend/`)
Important views for current behavior:
- `frontend/src/views/media/MediaListView.vue`
  - unified media management
  - source group sidebar
  - external source drawer and configuration modal
  - source browsing modal
  - external browse mode on the right-hand list
- `frontend/src/views/album/AlbumDetailView.vue`
  - internal + external media picker for album contents
  - internal + external picker for album cover
- `frontend/src/views/distribution/DistributionListView.vue`
  - distribution creation/editing
  - PRIVATE album warning in distribution flow

Important rule:
- `MediaListView.vue` is large and carries many cross-source assumptions. Make small, targeted edits and rebuild immediately after each step.
- The album picker logic in `AlbumDetailView.vue` intentionally mirrors patterns already proven in `MediaListView.vue`; reuse those patterns instead of inventing a separate external-source UX.

### Device client (`device-client/`)
- Uses `/api/v1/devices/pull/current` for queue sync.
- `useSecureObjectUrl` is the path by which protected backend media URLs become usable in the renderer.
- Player behavior depends on queue identity stability. External media should continue to work even when there is no internal `mediaId`.

## Known high-risk areas / things easy to break

- `frontend/src/views/media/MediaListView.vue`
  - This file was previously broken by large copy/paste edits and duplicated template/script blocks. Prefer small `Edit` changes and build after each step.
- `src/main/java/com/cloudalbum/publisher/album/service/impl/AlbumServiceImpl.java`
  - Must preserve internal and external branches for content listing, adding content, updating cover, and cover streaming.
- `src/main/java/com/cloudalbum/publisher/device/service/impl/DeviceServiceImpl.java`
  - Queue filtering and URL generation can silently drop distributions if album visibility or media validity rules are wrong.
- `src/main/java/com/cloudalbum/publisher/distribution/service/impl/DistributionServiceImpl.java`
  - Activation validation is the place where many “why didn’t device get new queue data?” issues surface.

## Practical workflow for future changes

When changing behavior around external sources, prefer this order:
1. backend compile: `mvn -q -DskipTests compile`
2. admin frontend build: `npm --prefix frontend run build`
3. device client build: `npm --prefix device-client run build`
4. if runtime behavior is involved, use the real backend logs:
   - `logs/cloud-album-publisher.log`
   - `logs/cloud-album-publisher-error.log`

When investigating “device did not receive new queue data”, always check in order:
1. Is the distribution `ACTIVE`?
2. Does the distribution target the device or its group?
3. Is the album visibility `PUBLIC` or `DEVICE_ONLY` rather than `PRIVATE`?
4. Does the album contain at least one valid internal/external media item after validation?
5. Does `/api/v1/devices/pull` or `/api/v1/devices/pull/current` include the distribution?

When investigating “cannot add external media to album”, check in order:
1. H2/MySQL schema matches the current album external-media model
2. `t_album_media.media_id` can be null for external items
3. external source browse returns the target file under the requested/bound path
4. the album add-content request includes `externalMediaKey`, `sourceId`, and normalized `path`
