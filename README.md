# zhikao-server

知考公考学习 App 后端服务（Spring Boot 3.3.5 + Java 21 + MySQL + Redis + MyBatis-Plus + JWT）。

详见《公考常识与成语学习App项目开发任务书》v1.4 与《任务清单》。

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 编译运行必需 |
| Maven | 3.9+ | 构建 |
| MySQL | 8.0+ / 9.x | 主数据库（开发默认 `root/123456`，库名 `zhikao`） |
| Redis | 6.2+ | 缓存、分布式锁、游客配额（默认 `127.0.0.1:6379`） |

## 快速启动

```bash
# 1. 初始化数据库（首次）——schema + 种子数据
mysql -uroot -p123456 --default-character-set=utf8mb4 \
  < src/main/resources/db/schema.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 \
  < src/main/resources/db/init-data.sql

# 2.（可选）生产内容批量导入（C.1~C.3 内容，幂等）
node scripts/generate-content.js          # 生成 scripts/content-data/content-seed.sql
mysql -uroot -p123456 --default-character-set=utf8mb4 \
  < scripts/content-data/content-seed.sql

# 3. 启动 Redis（Windows 示例）
Start-Process redis-server

# 4. 运行
mvn spring-boot:run

# 5. 验证
#    管理端登录：POST http://127.0.0.1:8080/admin/login {"username":"admin","password":"admin123456"}
#    接口文档：  http://127.0.0.1:8080/doc.html （knife4j）
```

## 测试与构建

```bash
mvn test                  # 单元测试（ReviewScheduleServiceTest 9 项 + RedisCacheAndLockTest 3 项）
mvn package -DskipTests   # 打包 jar
scripts/api-regression.ps1  # API 回归（16 项）
scripts/flow-tests.ps1      # 全流程测试（登录/学习/练习/复习/一致性）
```

## 配置说明（src/main/resources/application.yml）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.datasource.*` | root/123456@localhost:3306/zhikao | 生产环境改用环境变量 |
| `spring.data.redis.*` | localhost:6379 | Redis 连接 |
| `zhikao.jwt.secret` | 开发默认值 | 生产必须更换并走环境变量 |
| `zhikao.jwt.admin-secret` | 开发默认值 | 管理端 JWT 密钥 |
| `zhikao.admin.username/password` | admin/admin123456 | 管理端初始账号，生产必须修改 |
| `zhikao.storage.*` | 本地 `uploads/` | 上传文件存储目录 |

生产部署建议：密钥、口令一律通过环境变量注入，禁止在仓库明文保存（见《测试与发布记录.md》发布前置检查清单）。

## 目录结构

```
src/main/java/com/zhikao/server/
├── common/        # Result / ErrorCode / BizException / 全局异常 / CacheService / RedisLock
├── config/        # SecurityConfig / MybatisPlus / Redis / Async 配置
├── security/      # JwtUtil / AdminJwtUtil / JwtAuthenticationFilter / 认证入口
├── entity/ mapper/ service/ controller/  # 领域模块（19 张表）
├── document/      # 文档导入解析（PdfParser / WordParser / TxtParser / ContentExtractService）
├── storage/       # FileStorage / LocalDiskStorage
└── controller/admin/  # 管理端接口
src/main/resources/db/   # schema.sql / init-data.sql / bulk-import-sample.sql
scripts/                 # 回归 / 流程测试 / 内容生成器
```

## 错误码约定（任务书 6.1）

`0` 成功；`1001/1002/1003` 参数/账号/关联校验；`2001/2002/2003` 未登录/过期/无权限；`3001` 管理端无权限；`4001~4004` 导入文档相关；`5000` 系统异常；`9001` 游客配额超限。
