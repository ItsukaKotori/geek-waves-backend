# GeekWaves Server

程序员技术资讯站后端(Spring Boot 4 / Java 21)。

## 前置条件

- **JDK 21**(必需,项目为 21 字节码)
- **Node.js**:非必需,前后端分离开发时前端项目另起工程
- 首次构建需访问 Nexus 私服拉取 `org.itsuka:*` 内部依赖(见「Nexus 凭据注入」)

## 启动

```bash
cd geekwaves-server

# 1. 注入加密集(必填;缺失时应用启动会失败)
export GEEKWAVES_CRYPTO_KEY=$(openssl rand -base64 32)

# 2. 启动(监听 8082;首次启动自动执行 Flyway 建表并插入 3 条内置源)
./gradlew bootRun
```

启动后自检:

```bash
curl -s localhost:8082/api/ping          # {"success":true,"data":"pong",...}
curl -s localhost:8082/api/admin/sources # 内置源(HN/GitHub Trending/V2EX 热点)
```

## 零中间件说明

当前为单机运行形态,无需 Redis/MySQL/Elasticsearch:

- **数据库**:H2 文件库,路径 `./data/geekwaves`(SQL 兼容 MySQL 模式;该目录已被 gitignore,不提交)
- **缓存/限流/存储端口**:本地实现(Caffeine + JVM 进程内锁),见 `config/port/*`

## 外嵌模式(可选:MySQL)

默认零中间件(H2 文件库)已可运行。若需外接 MySQL,项目已提供 MySQL 方言迁移脚本与 `mysql` profile 配置,但**默认构建未引入 MySQL 驱动**(`com.mysql.cj.jdbc.Driver` 不随发行包打包)——外嵌需自行添加 `com.mysql:mysql-connector-j` 依赖,或由后续版本内置。

```bash
# 1. 新建数据库(字符集 utf8mb4)
mysql -e 'CREATE DATABASE geekwaves DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci'

# 2. 注入连接凭据(环境变量)
export MYSQL_USERNAME=geekwaves
export MYSQL_PASSWORD=你的密码

# 3. 以 mysql profile 启动(自动执行 db/migration/mysql 下迁移)
./gradlew bootRun --args='--spring.profiles.active=mysql'
```

- 迁移脚本与 H2 版字段一一对应:JSON 列(`config_json`/`extra_json`)用 MySQL `JSON` 类型,`CLOB` 改用 `LONGTEXT`,时间列 `DATETIME(3)`,`TINYINT` 保持
- **Redis 支持缺口**:当前缓存/限流/存储端口仍为本地实现(Caffeine + JVM 进程内锁),外嵌模式暂不依赖 Redis;Redis 端口接口已就位(见 `config/port/*`),待外嵌需求落地时启用

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/ping` | 健康探测 |
| GET/POST | `/api/admin/sources` | 源列表 / 新建源 |
| PUT/DELETE | `/api/admin/sources/{id}` | 更新 / 删除源 |
| POST | `/api/admin/sources/{id}/trigger-fetch` | 手动触发抓取 |
| GET/POST | `/api/admin/providers` | AI Provider 列表 / 新建 |
| PUT/DELETE | `/api/admin/providers/{id}` | 更新 / 删除 Provider |
| POST | `/api/admin/providers/{id}/set-default` | 设置默认 Provider |
| GET/POST | `/api/admin/frameworks` | 框架关注列表 / 新建 |
| PUT/DELETE | `/api/admin/frameworks/{id}` | 更新 / 删除框架关注 |
| POST | `/api/tools/http-request` | HTTP 请求测试工具(带 SSRF 守卫) |
| GET | `/api/monitor/overview` | 系统指标 + JVM 指标组合(前端 5s 轮询) |
| GET | `/api/monitor/processes` | OS 进程 Top 快照(10s 轮询) |
| GET | `/api/monitor/tasks` | 调度器 / 线程池 / 数据源健康 / Provider 状态 |
| GET/POST | `/api/monitor/probe-targets` | 端口探测目标列表 / 新建 |
| PUT/DELETE | `/api/monitor/probe-targets/{id}` | 更新 / 删除探测目标 |
| POST | `/api/monitor/probe-targets/{id}/probe` | 立即探测单个目标 |

## 服务器监控

`com.geekwaves.monitor` 模块提供系统性能与运行服务检测,OSHI 采集系统指标,JDK MXBean 采集 JVM 指标:

- **采样架构**:Controller 不触碰 OSHI —— `SystemMetricsSampler`(5s)与 `ProcessMetricsSampler`(10s)后台差分采样,`volatile` 快照发布;任何采集异常都降级为带 `error` 字段的 DTO,绝不抛出
- **进程 CPU 归一**:进程级 CPU 按「逻辑核数」归一(否则多核机器单进程可 >100%);进程列表刻意不采集 commandLine(避免泄露启动参数中的密钥)
- **端口探测**:目标持久化在 `probe_target` 表(Flyway V2),定时扫描到期目标复用抓取线程池并发执行 TCP connect,UP / TIMEOUT / DOWN 三态 + 延迟;手动探测同步返回
- **SSRF 边界**:探测接口不套 SsrFGuard(会拦掉 localhost 首要用例)。防线 = 仅可探测已持久化的目标 + CRUD 严格校验(host 拒绝 URL 前缀、port 1-65535、超时 200-5000ms、间隔 10-86400s)+ 目标总量上限(默认 64)

配置项(`application.yaml` 的 `geekwaves.monitor.*`):

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关;`false` 时接口返回降级提示、采样与探测调度全部退出 |
| `sample-ms` | `5000` | 系统指标采样间隔 |
| `process-sample-ms` | `10000` | 进程采样间隔 |
| `process-top-n` | `50` | 进程快照保留条数 |
| `probe-fixed-delay-ms` | `30000` | 到期探测扫描频率 |
| `max-targets` | `64` | 探测目标数量上限 |

> 监控接口未做鉴权,仅适用于本机 / 内网自托管场景;对外暴露请自行加反向代理鉴权,或以 `--geekwaves.monitor.enabled=false` 关闭。建议公网部署时同时设置 `--server.address=127.0.0.1`。

## Nexus 凭据注入(仅本地配置,禁止提交)

内部依赖 `org.itsuka:*` 走 Nexus 私服,凭据写入用户级 Gradle 配置文件(**不入库、不提交**):

```properties
# ~/.gradle/gradle.properties
nexusUsername=你的账号
nexusPassword=你的密码
```

## 测试

```bash
./gradlew test        # 全量测试
./gradlew clean test  # 干净构建 + 全量测试
```

## H2 控制台(调试用)

默认关闭。临时启用:

```bash
./gradlew bootRun --args='--spring.h2.console.enabled=true'
```

访问 `http://localhost:8082/h2-console`:
JDBC URL 填 `jdbc:h2:file:./data/geekwaves;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE`,用户名 `sa`,密码留空。
