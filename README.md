# 智控云影后端服务（cloud-album-publisher）

Spring Boot 3 后端主工程，负责认证、用户、相册三个核心模块。

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| Spring Security | 6.x |
| MyBatis-Plus | 3.5.7 |
| MySQL | 8.0 |
| Redis | 7.0 |
| JJWT | 0.12.6 |
| MinIO | 8.5.11 |
| SpringDoc OpenAPI | 2.5.0 |

## 模块职责

| 开发者 | 模块 | 说明 |
|---|---|---|
| **A（本工程）** | auth / user / album | 认证、用户、相册，公共骨架 |
| B | media / device | 媒体上传、设备管理、异步任务 |
| C | distribution / audit | 内容分发、审核、评审 |

---

## 本地快速启动

### 前提条件

- Docker Desktop 已安装并运行
- JDK 17+
- Maven 3.8+

### 第一步：启动基础设施

```bash
docker-compose up -d
```

等待所有服务 healthy（约 30 秒）：

```bash
docker-compose ps
```

服务清单：

| 服务 | 端口 | 说明 |
|---|---|---|
| MySQL 8.0 | 3306 | 数据库，自动执行 V1__init_schema.sql 初始化 |
| Redis 7.0 | 6379 | 缓存 / Token 存储 |
| MinIO | 9000 / 9001 | 对象存储 API / Console |

MinIO Console 访问：`http://localhost:9001`，账号 `minioadmin` / `minioadmin`

### 第二步：启动应用

```bash
mvn spring-boot:run
```

开发环境默认使用 H2 文件库 `./data/cloud_album.mv.db`，启动时会执行 `src/main/resources/db/h2/schema.sql`。
该脚本会兼容旧的本地 H2 开发库，并补齐相册外部媒体引用相关列。
如果你在升级代码后仍遇到 H2 列缺失异常，先重启一次应用；如果旧本地库状态仍异常，再删除 `data/cloud_album.*` 后重启。

或构建后运行：

```bash
mvn clean package -DskipTests
java -jar target/cloud-album-publisher-0.0.1-SNAPSHOT.jar
```

### 第三步：验证

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- 健康检查：`http://localhost:8080/actuator/health`

---

## API 接口概览

### 认证模块 `/api/v1/auth`

| 方法 | 路径 | 说明 | 是否需要 Token |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 注册 | 否 |
| POST | `/api/v1/auth/login` | 登录 | 否 |
| POST | `/api/v1/auth/refresh` | 刷新 Token | 否（携带 refreshToken） |
| POST | `/api/v1/auth/logout` | 登出 | 是 |

### 用户模块 `/api/v1/users`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/api/v1/users` | ROLE_ADMIN |
| GET | `/api/v1/users/{id}` | 本人或 ADMIN |
| PUT | `/api/v1/users/{id}` | 本人 |
| PATCH | `/api/v1/users/{id}` | 本人 |
| DELETE | `/api/v1/users/{id}` | ROLE_ADMIN |

### 相册模块 `/api/v1/albums`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/albums` | 当前用户相册列表（分页） |
| POST | `/api/v1/albums` | 创建相册 |
| GET | `/api/v1/albums/{id}` | 相册详情 |
| PUT | `/api/v1/albums/{id}` | 更新相册 |
| DELETE | `/api/v1/albums/{id}` | 逻辑删除相册 |
| GET | `/api/v1/albums/{id}/contents` | 相册内容列表（分页） |
| POST | `/api/v1/albums/{id}/contents` | 添加媒体到相册 |
| PATCH | `/api/v1/albums/{id}/cover` | 更新封面 URL |
| PATCH | `/api/v1/albums/{id}/bgm` | 设置 BGM（占位） |

---

## JWT 认证流程

```
注册/登录 → 返回 accessToken（15 min）+ refreshToken（7 天）
                                │
每次请求 → Authorization: Bearer <accessToken>
                                │
Token 过期 → POST /auth/refresh，携带 { "refreshToken": "..." }
                                │
登出 → POST /auth/logout → access jti 写入 Redis 黑名单
```

---

## 配置说明

默认配置文件 `application.yml` + `application-dev.yml`（开发环境）。

生产环境使用 `application-prod.yml`（已加入 .gitignore，需手动创建）。

支持环境变量覆盖：

```bash
DB_HOST=192.168.1.100 \
DB_PASSWORD=secret \
JWT_SECRET=your-256-bit-secret \
MINIO_ENDPOINT=http://minio.internal:9000 \
java -jar cloud-album-publisher.jar --spring.profiles.active=prod
```

---

## 给 B / C 开发者

### B（媒体/设备模块）

- 媒体表 `t_media`：**B 自行创建**，`t_album_media.media_id` 关联该表
- MinIO 客户端 Bean 已在 `MinioConfig` 注入，直接注入 `MinioClient` 使用
- 上传完成后，通过 `POST /api/v1/albums/{id}/contents` 传入 `mediaId` 建立关联
- 统一响应体：`Result<T>`（`com.cloudalbum.publisher.common.model.Result`）
- 分页响应体：`PageResult<T>`（`com.cloudalbum.publisher.common.model.PageResult`）
- 获取当前用户 ID：`SecurityUtil.getCurrentUserId()`
- 错误码枚举：`ResultCode`，可在此扩展 B 专属错误码

### C（分发/审核模块）

- 相册可见性枚举：`PUBLIC` / `PRIVATE` / `DEVICE_ONLY`（`t_album.visibility`）
- 相册状态枚举：`DRAFT` / `PUBLISHED`（`t_album.status`）
- 公共异常：`BusinessException(ResultCode, message)` 抛出业务错误
- 需要鉴权的接口统一挂在 Security 过滤链下，白名单见 `SecurityConfig`

---

## 运行测试

```bash
# 需要本地 docker-compose 基础设施先启动
mvn test
```

---

## 停止本地环境

```bash
docker-compose down
# 保留数据卷：docker-compose down（不加 -v）
# 清除所有数据：docker-compose down -v
```
