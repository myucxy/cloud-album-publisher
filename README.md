# 智控云影后端服务（cloud-album-publisher）

Spring Boot 3 后端主工程，承载认证、用户、相册、媒体源、设备、内容分发、审核、焦点检测等核心能力；仓库同时包含管理端前端、设备端客户端和安卓播放客户端。

---

> **推荐优先使用 [ShowcaseApp](https://github.com/mrjoechen/ShowcaseApp)**
>
> 本项目的相册显示模式、日历模式、Bento 布局等功能大量参考了 [ShowcaseApp](https://github.com/mrjoechen/ShowcaseApp)。ShowcaseApp 是一款优雅的数字相框应用，支持 Android、iOS 多平台，提供 FTP/SFTP/SMB/WebDAV 等多种媒体源接入，以及幻灯片、图片墙、日历、Bento 等丰富的显示样式。
>
> **如果你的需求是将闲置设备变成数字相框展示照片，ShowcaseApp 是更轻量、更成熟的选择，强烈建议优先使用。**
>
> 本项目（cloud-album-publisher）侧重于**服务端管理 + 多设备集中分发**的场景，适合需要通过后台统一管理相册、媒体源、设备绑定和内容分发的用户。

---
展示：![展示1](./demoImage/1780231315243.jpg)



## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Security | 6.x |
| MyBatis-Plus | 3.5.7 |
| MySQL | 8.0 |
| H2 | 测试 / 可选本地文件库 |
| Redis | 7.0 |
| MinIO | 8.5.11 |
| SpringDoc OpenAPI | 2.5.0 |
| Vue 3 + Vite | frontend / device-client |
| Electron | device-client |
| Android (Gradle) | android-client |

## 仓库结构

- `src/main/java/...`：Spring Boot 后端
- `src/main/resources/...`：配置、数据库脚本、日志配置
- `frontend/`：管理端前端（Vue + Vite）
- `device-client/`：设备播放客户端（Vue + Vite + Electron）
- `android-client/`：安卓播放客户端（Java + Gradle）

## 本地快速启动

### 前提条件

- Docker Desktop 已安装并运行
- JDK 17+
- Maven 3.8+
- Node.js / npm（用于 `frontend` 和 `device-client`）
- Android Studio（可选，用于 `android-client`）

### 第一步：启动基础设施

```bash
docker-compose up -d
```

等待所有服务 healthy：

```bash
docker-compose ps
```

服务清单：

| 服务 | 端口 | 说明 |
|---|---|---|
| MySQL 8.0 | 3306 | 主数据库 |
| Redis 7.0 | 6379 | 缓存 / Token 存储 |
| MinIO | 9000 / 9001 | 对象存储 API / Console |

MinIO Console：`http://localhost:9001`

### 第二步：启动后端

```bash
mvn spring-boot:run
```

或构建后运行：

```bash
mvn clean package -DskipTests
java -jar target/cloud-album-publisher-1.0.0-SNAPSHOT.jar
```

### 第三步：启动前端（可选）

管理端：

```bash
cd frontend
npm install
npm run dev
```

设备端：

```bash
cd device-client
npm install
npm run dev
```

### 第四步：验证

- Swagger UI：`http://localhost:8910/swagger-ui.html`
- 健康检查：`http://localhost:8910/actuator/health`
- 数据库：默认连接 MySQL（`localhost:3306/cloud_album`，可通过 `DB_*` 环境变量覆盖）

## 开发环境与数据库说明

- 默认 `application.yml` 使用 MySQL + Redis + MinIO。
- 默认激活 profile 是 `dev`，`application-dev.yml` 不再覆盖数据源，因此开发启动默认也是 MySQL。
- MySQL 连接默认值：
  - `DB_HOST=localhost`
  - `DB_PORT=3306`
  - `DB_NAME=cloud_album`
  - `DB_USER=root`
  - `DB_PASSWORD=root`
- 如需临时使用 H2 文件库，显式指定 `SPRING_PROFILES_ACTIVE=h2`，会使用：
  - `./data/cloud_album.mv.db`
  - `src/main/resources/db/h2/schema.sql`

## 日志

后端日志由 `src/main/resources/logback-spring.xml` 配置：

- 全量日志：
  - `logs/cloud-album-publisher.log`
- ERROR 独立日志：
  - `logs/cloud-album-publisher-error.log`

如果要定位运行时问题，优先看 error 日志文件。

## 当前核心业务能力

### 1. 媒体源能力
后端已支持以下外部媒体源类型：

- SMB
- FTP
- SFTP
- WebDAV

统一入口由媒体源模块提供：
- 创建/更新媒体源
- 浏览已绑定目录
- 服务端代理预览外部文件
- 导入外部文件到内部媒体库

关键后端抽象：
- `MediaSourceTypeHandler`
- `MediaSourceFileClient`
- `MediaSourceServiceImpl`

### 2. 相册支持内部媒体 + 外部媒体
相册不再只支持 `t_media` 内部媒体。

当前相册内容与封面都支持两种来源：
- 内部媒体：通过 `mediaId`
- 外部媒体：通过 `sourceId + externalMediaKey + path` 等外部引用字段

接口上体现为：
- `POST /api/v1/albums/{id}/contents`
- `PATCH /api/v1/albums/{id}/cover`

相册内容列表现在也会返回统一的内容结构，内部媒体与外部媒体都能带回预览 URL。

### 3. 分发与设备播放支持外部媒体
外部媒体加入相册后，可以继续走：
- 分发激活校验
- 设备拉取队列
- 设备通过服务端代理访问外部媒体原文件/缩略图

设备相关接口除了内部媒体外，还支持：
- 外部媒体原文件访问
- 外部媒体缩略图访问
- 相册封面（包括外部封面）访问

### 4. 焦点检测（Focal Point）
支持对相册图片进行焦点检测，用于智能裁切显示。

支持的检测方式：
- **OpenCV**：基于 Haar 级联分类器的人脸检测
- **ONNX Runtime**：基于 ONNX 模型的显著性检测
- **Vision LLM**：基于视觉大模型的智能焦点分析
- **手动**：管理端 UI 手动设置焦点坐标

焦点数据会下发到设备端和安卓客户端，播放时根据焦点位置进行智能裁切（`object-fit: cover` + `object-position`）。

管理端入口：`/focal-point` 路由下的焦点管理页面。

### 5. 相册显示模式
相册支持多种设备端显示模式：
- `SINGLE`：单图轮播
- `BENTO`：Bento 布局
- `FRAME_WALL`：相框墙
- `CAROUSEL`：轮播墙
- `CALENDAR`：日历模式

显示模式可通过分发配置覆盖，也可在相册级别设置默认值。

### 6. 安卓播放客户端
安卓客户端（`android-client/`）支持：
- 设备绑定与激活
- 自动/手动同步播放队列
- 焦点裁切（`FocalCropTransformation`）
- 分块拉取（`pullCurrentChunk`）
- 开机自启动（可在设置中开关，默认关闭）
- 自动更新检查
- 多种显示模式（单图、日历等）

构建命令：
```bash
# 使用隔离 Gradle 环境
GRADLE_USER_HOME="android-client/.gradle-user-home" gradle -p android-client :app:assembleDebug --no-daemon
```

### 7. 设备播放客户端
设备端客户端（`device-client/`）支持：
- Electron 桌面应用
- 自动同步播放队列
- 焦点裁切显示
- 多种显示模式
- 时钟叠加层

## 重要业务规则 / 注意事项

### 相册可见性规则
相册可见性：
- `PUBLIC`
- `PRIVATE`
- `DEVICE_ONLY`

**重要限制：`PRIVATE` 相册不能下发到设备。**

当前实现里：
- 设备拉取时会过滤 `PRIVATE` 相册
- 分发激活时也会直接拦截 `PRIVATE` 相册并返回明确错误
- 前端分发页会提示：私有相册不能下发到设备

因此，如果遇到"客户端没有获取到新的分发队列数据"，要优先检查：
1. 分发是否是 `ACTIVE`
2. 目标设备是否正确绑定到该分发
3. 相册是否是 `PUBLIC` 或 `DEVICE_ONLY`
4. 相册内容是否全部通过内部/外部媒体校验

### 数据库变更
数据库变更必须同时关注：
- MySQL migration：`src/main/resources/db/migration/*.sql`
- 如果仍需支持 H2 profile，同步维护：`src/main/resources/db/h2/schema.sql`

尤其是相册外部媒体模型相关字段：
- `t_album` 外部封面字段
- `t_album_media` 外部媒体引用字段
- `t_album_media.media_id` 必须允许为 `NULL`

默认 dev 使用 MySQL，数据库结构以 Flyway migration 为准。

### 媒体源路径范围
媒体源 browse / 预览严格受 `boundPath` 限制。
如果请求路径超出绑定目录，会返回：
- `访问路径超出已绑定目录范围`

联调时如果 browse 报这个错，先检查：
- 当前媒体源 `boundPath`
- 你传入的 `path`
- 是否在该绑定目录及其子路径范围内

## 主要接口分组

### 认证 `/api/v1/auth`
- `POST /register`
- `POST /login`
- `POST /refresh`
- `POST /logout`
- `POST /change-password`

### 相册 `/api/v1/albums`
- `GET /api/v1/albums`
- `POST /api/v1/albums`
- `GET /api/v1/albums/{id}`
- `PUT /api/v1/albums/{id}`
- `DELETE /api/v1/albums/{id}`
- `GET /api/v1/albums/{id}/contents`
- `POST /api/v1/albums/{id}/contents`
- `DELETE /api/v1/albums/{id}/contents/{contentId}`
- `PATCH /api/v1/albums/{id}/cover`
- `PATCH /api/v1/albums/{id}/bgm`
- `GET /api/v1/albums/{id}/cover`

### 媒体源 `/api/v1/media-sources`
- `GET /api/v1/media-sources`
- `POST /api/v1/media-sources`
- `PATCH /api/v1/media-sources/{id}`
- `DELETE /api/v1/media-sources/{id}`
- `GET /api/v1/media-sources/{id}/browse`
- `POST /api/v1/media-sources/browse`
- `GET /api/v1/media-sources/{id}/content`
- `GET /api/v1/media-sources/{id}/thumbnail`
- `POST /api/v1/media-sources/{id}/import`

### 分发 `/api/v1/distributions`
- `GET /api/v1/distributions`
- `GET /api/v1/distributions/active`
- `GET /api/v1/distributions/{id}`
- `POST /api/v1/distributions`
- `PUT /api/v1/distributions/{id}`
- `DELETE /api/v1/distributions/{id}`
- `PATCH /api/v1/distributions/{id}/activate`
- `PATCH /api/v1/distributions/{id}/disable`

### 焦点检测 `/api/v1/focal-points`
- `POST /api/v1/focal-points/albums/{albumId}/batch`
- `PUT /api/v1/focal-points/albums/{albumId}/media/{mediaId}`
- `DELETE /api/v1/focal-points/albums/{albumId}/media/{mediaId}`
- `GET /api/v1/focal-points/albums/{albumId}/settings`
- `PUT /api/v1/focal-points/albums/{albumId}/settings`

### Vision LLM 配置 `/api/v1/vision-llm-configs`
- `GET /api/v1/vision-llm-configs`
- `POST /api/v1/vision-llm-configs`
- `PUT /api/v1/vision-llm-configs/{id}`
- `DELETE /api/v1/vision-llm-configs/{id}`
- `POST /api/v1/vision-llm-configs/{id}/test`

### 设备 `/api/v1/devices`
- 用户侧：设备管理、分组、按设备 UID 拉取内容
- 设备侧：
  - `GET /api/v1/devices/pull/current`
  - `GET /api/v1/devices/pull/current/chunk`
  - `GET /api/v1/devices/albums/{albumId}/cover`
  - `GET /api/v1/devices/media/{mediaId}/content`
  - `GET /api/v1/devices/media/{mediaId}/thumbnail`
  - `GET /api/v1/devices/media-sources/{sourceId}/content`
  - `GET /api/v1/devices/media-sources/{sourceId}/thumbnail`

## 运行测试

```bash
mvn test
```

单测：

```bash
mvn -Dtest=ClassName test
mvn -Dtest=ClassName#methodName test
```

## 停止本地环境

```bash
docker-compose down
```

删除数据卷：

```bash
docker-compose down -v
```
