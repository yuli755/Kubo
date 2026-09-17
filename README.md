<p align="center">
<img src="./src/assets/img/logo.svg" width="240"/>
</p>

<p align="center">
  <img src="https://svg.hamm.cn?key=Lang&value=TypeScript&bg=green"/>
  <img src="https://svg.hamm.cn?key=Base&value=Vue3"/>
  <img src="https://svg.hamm.cn?key=Build&value=Vite"/>
  <img src="https://svg.hamm.cn?key=UI&value=ElementPlus"/>
</p>

## Kubo · 智方

**Kubo** 智能生产管理系统，是一个集成化、智能化的企业级应用软件，集成了：

- **MES 制造执行系统** - 车间层生产活动优化管理，实时监控从订单下达到产品完成的整个生产过程
- **WMS 仓库管理系统** - 全面管理入库、出库、库存、盘点、批次等仓库业务流程
- **ERP 企业资源计划** - 整合财务、采购、销售、生产、库存等各环节信息
- **QMS 质量管理系统** - 质量策划、控制、保证及改进全流程管理
- **IoTS 物联网管理** - 连接生产设备和传感器，实现设备远程监控、预防性维护

实现从采购、生产、仓储到销售全流程的信息化、精细化、智能化管理。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `/`（根） | 前端 **kubo-web**（Vue 3 + Vite + Pinia + Element Plus + TanStack Query） |
| `SPMS-Server/` | 后端 **SPMS-Server**（Spring Boot 3 + JPA/MyBatis + airpower 框架） |
| `SPMS-Server/docs/` | 后端/全栈文档（见 [文档索引](./SPMS-Server/docs/README.md)） |
| `packages/shared/` | 前后端共享的脱敏/权限类型与工具（`@kubo/shared`） |

## 快速开始

### 📚 完整文档

- **[STARTUP.md](./STARTUP.md)** - 完整启动指南 (环境检查、Redis 启动、项目启动)
- **[LOGIN.md](./LOGIN.md)** - 默认账号和测试数据说明
- **[AGENT.md](./AGENT.md)** - AI 辅助开发的工程规范、目录结构、编码风格
- **[SPMS-Server/docs](./SPMS-Server/docs/README.md)** - 后端文档索引（数据权限施工图、真实环境验收清单、迁移脚本）

### 环境准备

- Node.js 18+
- pnpm 9+（`package.json` 已声明 `packageManager: pnpm@9.0.0`）
- TypeScript 5.9+
- MySQL 8+（后端用；表结构由 JPA `ddl-auto=validate` 校验，增量迁移是 Flyway 版本化脚本，见 `SPMS-Server/src/main/resources/db/migration/`）
- Redis（后端）
- JDK 17+（后端，仅运行 SPMS-Server 时需要）

### 安装依赖

```shell
pnpm install
```

### 配置环境变量

- **不需要 .env 文件**：生产构建不读任何 `VITE_*` 变量（原 `.env.production` 里的 `VITE_APP_NAME` /
  `VITE_APP_API_URL` 全仓无人读取，已于 2026-09 删除）。要新增环境变量时，先有读取代码再补声明。
- 本地开发无需手工配 `.env`：前端通过 Vite 代理把 `/api` 转发到 `http://127.0.0.1:8080/`（后端），见 `vite.config.mts`。

### 启动项目

```shell
# 启动前端开发服务器（后端需先跑在 8080）
pnpm dev

# 生产构建（唯一入口：原来还有个 `pnpm production` 别名，2026-09 与 `pnpm s` 一起删掉了）
pnpm build

# 代码格式检查与修复
pnpm lint

# 全部门禁（lint + typecheck + test + 权限码 + 文本 + DDL 漂移 + 构建 + 体积）
pnpm check
```

### 🔐 默认登录账号

首次启动后，系统会自动创建测试账号：

```
账号: 17666666666  (或 admin@hamm.cn)
密码: Aa123456
```

详细信息查看 **[LOGIN.md](./LOGIN.md)**

### VS Code 推荐插件

- Vue - Official
- ESLint
- SCSS Formatter

> 建议在工作区禁用 `Vetur` 插件，避免 Vue2/Vue3 冲突

## AI 辅助开发

如果使用 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等），请让 AI 参考 **[AGENT.md](./AGENT.md)** 文档，其中详细约定了项目的开发规范、目录结构、编码风格等，保证 AI 生成的代码与项目现有风格一致。

## 开源协议

**Kubo** 所有代码均在 **MIT** 开源协议规范下免费提供，你可以放心使用（见 [LICENSE](./LICENSE)）。

如有定制需求，欢迎联系我们：admin#hamm.cn
