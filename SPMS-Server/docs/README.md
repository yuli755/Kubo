# Kubo 平台文档索引

> **最后更新**: 2026-09-17
> 本索引反映仓库**实际目录结构**。规划期文档已归档至 [`archive/`](./archive/)。

## 当前口径（单一事实源，优先读这里）

| 文档 | 说明 |
| --- | --- |
| [`数据权限目标态落地施工图.md`](./数据权限目标态落地施工图.md) | 二次改造**唯一施工口径**，最新落账 §19.59（P5 死信闭环） |
| [`真实环境验收测试清单.md`](./真实环境验收测试清单.md) | 需在真实环境逐条执行的验收清单（B/D/E 系列） |
| [`二次改造进度盘点.md`](./二次改造进度盘点.md) | 进度快照（数据截至 2026-09-08，已标注过时） |
| [`八资源读侧导出验收矩阵.md`](./八资源读侧导出验收矩阵.md) | 8 资源读侧 Mapper 换轨验收矩阵 |
| [`业务功能未开发盘点.md`](./业务功能未开发盘点.md) | 各业务模块「未开发/未实现」的代码事实盘点（整模块/功能点/已闭环/建议） |

## 工程规范与策略

| 文档 | 说明 |
| --- | --- |
| [`DATABASE_MIGRATION_STRATEGY.md`](./DATABASE_MIGRATION_STRATEGY.md) | 手写幂等迁移的策略、约定与回滚 |
| [`API_DESIGN_GUIDE.md`](./API_DESIGN_GUIDE.md) | RESTful 设计规范、错误码、幂等、权限 |
| [`API_VERSIONING_STRATEGY.md`](./API_VERSIONING_STRATEGY.md) | 语义化版本、废弃策略 |

> **WebSocket 通道已移除（2026-09-17）**：`/websocket` 反代、`src/websocket/WebSocketManager.ts`、
> `AppWebSocketHandler`、`WEBSOCKET_PROTOCOL.md` 一并删除，nginx CSP 不再放行 `ws:/wss:`。
> 实时通道需要时重新立项（SSE / MQ 推送），不要沿用旧协议稿。


## 目录

| 目录 | 内容 |
| --- | --- |
| [`adr/`](./adr/) | 架构决策记录 ADR-001~008（含模板） |
| [`02-Architecture/`](./02-Architecture/) | 二次改造设计文档（历史设计稿，已标注过时） |
| [`04-Infrastructure/`](./04-Infrastructure/) | 基础设施（INFRASTRUCTURE_OVERVIEW） |
| [`05-Development/`](./05-Development/) | 开发规范（DEVELOPMENT / CODE_STYLE / GIT_COMMIT / TESTING / SECURITY） |
| [`database/`](./database/) | 迁移策略 + `tenant_readiness_matrix.md`；SQL 已收编为 Flyway `V1..V10`（`../src/main/resources/db/migration/`） |
| [`archive/`](./archive/) | **已归档**的规划期过程文档（见 `archive/README.md`） |

## 前端文档

| 文档 | 说明 |
| --- | --- |
| [`../docs/API_DOCS_GUIDE.md`](../../docs/API_DOCS_GUIDE.md) | API 文档生成与类型同步指南 |
| [`../docs/web_docs/`](../../docs/web_docs/) | 前端开发规范与协作流程 |
| [`前端技术栈现状.md`](./前端技术栈现状.md) | 当前前端技术栈、airpower 退场结果（0 引用）、门禁与清理口径 |
| [`前端F4·airpower 逐域迁移分批清单.md`](./前端F4·airpower%20逐域迁移分批清单.md) | 前端换底座的逐域迁移批次与验收 |

> 国际化（i18n）：**当前不做**——目标技术栈未包含 `vue-i18n`，代码里也没有任何 i18n 运行时。
> 2026-09 清理时删除了旧的「前端国际化方案设计」（那份文档标着"已定稿、强制执行"，
> 但对应实现从未落地，属于会误导后来者的历史稿）。需要多语言时重新立项评估。

---

*维护规则：新增文档时同步更新本索引；规划/方案类文档落定后归档至 `archive/`。*
