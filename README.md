# 智控云影后端服务（cloud-album-publisher）

Spring Boot 3 后端主工程，承载认证、用户、相册、媒体源、设备、内容分发、审核等核心能力；仓库同时包含管理端前端和设备端客户端。

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

## 仓库结构

- `src/main/java/...`：Spring Boot 后端
- `src/main/resources/...`：配置、数据库脚本、日志配置
- `frontend/`：管理端前端（Vue + Vite）
- `device-client/`：设备播放客户端（Vue + Vite + Electron）

## 本地快速启动

### 前提条件

- Docker Desktop 已安装并运行
- JDK 17+
- Maven 3.8+
- Node.js / npm（用于 `frontend` 和 `device-client`）

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

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/actuator/health`
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

因此，如果遇到“客户端没有获取到新的分发队列数据”，要优先检查：
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

### 设备 `/api/v1/devices`
- 用户侧：设备管理、分组、按设备 UID 拉取内容
- 设备侧：
  - `GET /api/v1/devices/pull/current`
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

