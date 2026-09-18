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

**Kubo** 物流仓储管理系统 —— 以 **WMS 为主线**的企业业务系统，当前实际落地的模块（与代码一一对应）：

- **WMS 仓储管理** - 入库单、出库单、移库单、库存（单据含明细与完成流程）
- **渠道管理** - 客户、供应商、采购单/采购价、销售单/销售价
- **资产管理** - 物料主数据、合同（含附件与参与人）
- **工厂建模** - 仓库、库位结构（树）
- **人事组织 / 系统管理** - 用户、角色、部门；权限、菜单、系统配置、编号规则、操作日志
- **数据权限** - 资源登记、行级策略、列级脱敏，读/写/导出三个出口统一受控

> **关于"生产/质量/物联"**：MES（制造执行）、QMS（质量）、IoT（物联网）与财务模块曾在早期版本里，
> **已于 2026-09 的整域瘦身中拆除**（见 `SPMS-Server/docs/瘦身执行记录·第三批.md`）。
> 定位与取舍的理由在 `SPMS-Server/docs/项目重定位·砍什么留什么（物流仓储与半导体方向）.md`。
> 需要批次/效期、序列号、波次、盘点、货主计费等 WMS 进阶能力时，按该文档 §4.3 的清单补。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `/`（根） | 前端 **kubo-web**（Vue 3 + Vite + Pinia + Element Plus + TanStack Query） |
| `SPMS-Server/` | 后端 **SPMS-Server**（Spring Boot 3 + MyBatis-Plus 3.5.9 + MySQL + Redis）。**前后端的 airpower 均已于 2026-09-18 退场完毕**：前端 `@airpower/web` 0 引用（`pnpm airpower:check`）；后端 `cn.hamm:airpower` **0 引用 / 0 依赖 / 无父 POM / 0 配置键**（`pnpm airpower:backend -- --check`，基线 0 必须保持）——原 airpower 的 core/curd/api/redis 四模块已整模块搬入本仓 `cn.hamm.spms.core.*` 与 `cn.hamm.spms.framework.*`（MIT，来源见各文件头），**框架层从此由本仓拥有**。全项目技术栈见 `SPMS-Server/docs/项目整体技术栈详细信息.md`，退场全过程见 `SPMS-Server/docs/后端airpower退场·可行性评估与分批建议.md` |

| `SPMS-Server/docs/` | 后端/全栈文档（见 [文档索引](./SPMS-Server/docs/README.md)） |
| `src/framework/` | 新底座框架层（元数据/实体/HTTP/脱敏策略等），业务只从 `@/framework` 取能力 |

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
- MySQL 8+（后端用；表结构唯一真值是 `infra/init-sql/init.sql` + `init_data.sql` 两份脚本，由 `pnpm ddl:check` / `schemaContract` 与实体对账）
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
> （机器版：`pnpm stack:project`；airpower 退场验收：`pnpm airpower:check`（前端 0 引用）/ `pnpm airpower:backend -- --check`（后端 0/0/0，目标态必须保持））。

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
