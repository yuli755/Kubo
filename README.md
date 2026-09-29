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

**Kubo** 智能生产管理系统，是一个集成化、智能化的企业级应用软件。当前**已落地**的业务域
（与 `SPMS-Server/src/main/java/cn/hamm/spms/module/` 下的 6 个模块、`src/view/console/` 下的页面一一对应）：

- **WMS 仓库管理** - 入库、出库、移库、库存与批次全流程（`wms.input/output/move/inventory`）
- **渠道管理** - 采购单、销售单、客户、供应商，以及采购/销售价格（`channel.*`）
- **工厂主数据** - 仓库与产线/工位的树形结构（`factory.storage` / `factory.structure`）
- **资产管理** - 合同（含参与方、附件）与物料、计量单位（`asset.contract` / `asset.material`）
- **组织与权限** - 用户、角色、部门；菜单/按钮权限目录，行级 + 列级数据权限治理（`personnel.*` / `system.*`）
- **系统治理** - 编码规则、参数配置、通知、文件、操作日志与数据权限证据面板（`system.*`）

覆盖从采购、仓储到销售全流程的信息化、精细化、智能化管理。

> **已退场的域**：MES（工单/计划/领料/BOM/工艺路线/工序）、ERP、QMS、IoT、聊天室、设备台账等
> 已在「瘦身」中整体下线 —— 菜单与权限身份由 `V12__retired_module_catalog_convergence.sql` 收敛，
> 数据权限目录里的残留资源与策略由 `V13__retired_resource_policy_convergence.sql` 收敛。
> 需要时按新领域模型重建，本仓不再提供。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `/`（根） | 前端 **kubo-web**（Vue 3 + Vite + Pinia + Element Plus + TanStack Query） |
| `SPMS-Server/` | 后端 **SPMS-Server**（Spring Boot 3.4.4 + Spring Data JPA + MyBatis-Plus 3.5.9，已彻底完成全自持现代架构重构，AirPower 依赖 0 残留，构建由官方 Spring Boot BOM 自持管理）。全仓 19 道门禁守护，足迹棘轮 `pnpm airpower:backend -- --check` |
| `SPMS-Server/docs/` | 后端/全栈文档（见 [文档索引](./SPMS-Server/docs/README.md)） |
| `src/framework/` | 自持底座框架层（元数据/实体/HTTP/脱敏策略等），业务只从 `@/framework` 取能力 |

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

# 技术栈一览（整个仓库：前端 + 后端 + 部署 + CI，全部读真文件）
pnpm stack:project        # 加 `-- --json` 给脚本读
```

> 📚 **整个项目跑在什么上**：见 [`SPMS-Server/docs/项目整体技术栈详细信息.md`](SPMS-Server/docs/项目整体技术栈详细信息.md)
> （机器版：`pnpm stack:project`；airpower 退场进度：`pnpm airpower:check` / `pnpm airpower:backend`）。

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
