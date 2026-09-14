# Kubo 启动指南

> 本文档描述在**本仓库当前结构**（前端在根目录，后端在 `SPMS-Server/`）下本地启动的步骤。
> 数据权限二次改造引入了手写幂等迁移脚本（见 `SPMS-Server/docs/database/migration/`），
> 生产/`validate` 环境下必须**先跑迁移再启动**，详见下文「表结构怎么建」。

## ✅ 前置条件

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 后端编译/运行 |
| MySQL | 8.0+ | 数据库 `kubo`（utf8mb4） |
| Redis | 任意 | 缓存，默认 `localhost:6379` 无密码 |
| Node.js | 18+ | 前端 |
| pnpm | 9+ | 前端包管理（`package.json` 已声明 `pnpm@9.0.0`） |

---

## 🚀 快速启动 (3 步)

### 第 1 步: 启动 Redis 与 MySQL

```bash
# Redis（Docker 方式，或使用本机已装的 Redis）
docker run -d --name kubo-redis -p 6379:6379 redis:alpine

# MySQL：确保 kubo 库存在
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS kubo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### 第 2 步: 启动后端

```bash
cd SPMS-Server
./mvnw spring-boot:run        # 默认 application-local（ddl-auto=update，自动建业务表）
```

- 后端端口 **8080**。
- 首次启动会执行 `DevDataInitRunner` 自动写入测试账号与演示数据；运行目录存在 `init.lock` 则跳过初始化。

### 第 3 步: 启动前端

```bash
# 回到仓库根目录（新开终端）
pnpm install                  # 首次运行
pnpm dev                      # 启动开发服务器（默认 3000，/api 代理到 127.0.0.1:8080）
```

**访问**：
- 前端: http://localhost:3000
- 后端: http://localhost:8080

---

## 🔐 登录测试

系统首次启动会**自动创建**测试账号和演示数据：

```text
管理员账号: 17666666666  (或 admin@hamm.cn)   密码: Aa123456
测试账号:   13888888888  (或 admin@hamm.com)   密码: Aa123456
```

详细说明查看 [LOGIN.md](./LOGIN.md)。

---

## 📋 表结构怎么建（重要：本地 / 生产不一致）

| 环境 | `spring.jpa.hibernate.ddl-auto` | 建表方式 |
| --- | --- | --- |
| `application-local.yml` | `update` | JPA/Hibernate 自动建业务表（开发用） |
| `application.yml` / `application-production.yml` | `validate` | **不建表、只校验**，必须**先执行迁移脚本** |

**生产（validate）上线前**，先落一次合并迁移脚本（幂等、可重复执行）：

```bash
mysql -u root -p kubo < SPMS-Server/docs/database/migration/20260909_data_permission_migrations_consolidated.sql
```

该脚本只建 `data_*` / `tenant` / `user_tenant` / `user_factory` 等增量表并回填归属列；
`user`/`permission`/`bom`/`contract` 等业务表仍由 JPA 建（validate 只校验结构，不建表）。
详情见 [WHY_NO_SQL.md](./WHY_NO_SQL.md) 与 [SPMS-Server/docs/README.md](./SPMS-Server/docs/README.md)。

---

## 🔧 常见问题

### Redis 连接失败
```bash
# 启动 Redis（Docker）或检查本机 Redis
docker run -d --name kubo-redis -p 6379:6379 redis:alpine
```

### MySQL 连接失败
1. 确认 MySQL 服务已启动、`kubo` 库存在；
2. 检查 `SPMS-Server/src/main/resources/application-local.yml` 中的连接配置（默认 root/root）。

### 端口被占用
```bash
# 后端 8080 / 前端 3000
lsof -i :8080        # 或 netstat -ano | findstr :8080 (Windows)
```

### 登录失败 - 用户不存在
1. 检查后端日志是否有 `已存在 init.lock 文件，无需初始化数据`；
2. 若有，删除 `SPMS-Server/init.lock` 后重启后端，重新初始化。

### 表结构校验失败（validate 环境）
说明「实体与迁移脚本不一致」或「迁移脚本没跑」。用 `pnpm ddl:check` 本地核对 DDL↔实体漂移（CI 也会用真实 MySQL 反向核对）。

---

## 🔄 重新初始化测试数据

```bash
# 方式 1: 删除锁文件后重启
cd SPMS-Server && rm init.lock

# 方式 2: 清空数据库后重启（自动建表 + 初始化）
mysql -u root -proot -e "DROP DATABASE kubo; CREATE DATABASE kubo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

---

## 📂 相关文档

| 文件 | 说明 |
|------|------|
| [README.md](./README.md) | 项目介绍与命令 |
| [LOGIN.md](./LOGIN.md) | 登录测试和默认数据说明 |
| [AGENT.md](./AGENT.md) | 开发规范 |
| [WHY_NO_SQL.md](./WHY_NO_SQL.md) | JPA 建表 vs 手写迁移的现行口径 |
| [SPMS-Server/docs/README.md](./SPMS-Server/docs/README.md) | 后端文档索引（施工图、验收清单、迁移脚本） |

---

**🎉 祝你启动顺利！** 如有问题，请查看日志或参考 [LOGIN.md](./LOGIN.md) 获取更多帮助。
