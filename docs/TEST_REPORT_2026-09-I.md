# SPMS 测试报告 —— 阶段 I（前端定向修复：K 阶段对齐 + 审计遗留收口）

- 日期：2026-09-16
- 范围：承接 `TEST_REPORT_2026-09-H.md`（后端 K 阶段）——前端针对上述问题做对齐修复，
  并收口 `AUDIT_REPORT_2026-09.md` 前端记录项；全程未触既有业务数据。
- 结论：**vitest 308/308 · vue-tsc 0 错误 · eslint 0 告警 · 探针抽验 auditH 10/10**。

## 1. 本轮改动（2 项修复 + 2 个锁测文件）

### L-1 个人信息链：updateMyInfo 载荷最小化（对齐 K-1）

- 问题：`UserCard.vue` 换头像时把整份 `AppConfig.currentUser`（含 roleList/departmentList
  等授权快照）传给 `/user/updateMyInfo`。后端 K-1 已把授权类字段强制清空作兜底，但前端
  仍每次「过网」一份完整授权对象——日志/代理/浏览器历史都可能留痕，且若未来后端清空清单
  被调整，最坏路径会被前端顺手激活。
- 修复：改为构造只含 `avatar` 的最小 `UserEntity` 提交；顺带去掉一次多余的 `getMyInfo`
  前置请求与对共享状态的直接赋值。
- 兼容性核查（重要）：全仓 `updateMyInfo` 仅此一处调用；`UserAccount.vue`（账号安全中心）
  只做改密/社交账号/私人令牌，**不经该接口写手机/邮箱**——K-1 的字段清空不会误伤任何
  合法改绑流程。
- 锁测：`src/config/__tests__/profilePayloadContract.spec.ts`（3 例）——全仓禁止
  `updateMyInfo(AppConfig.currentUser…)` 形态调用、调用点数量钉死为 1、头像流程必须
  走最小实体。

### L-3 错误上报出网自净（对齐 K-4）

- 修复：`errorReportClient.ts` 对 message/stack/context 统一「脱敏 → 压平换行 → 截断」，
  上限与后端 `ErrorReportController` 一致（2000/200）。CRLF 在前端即被压平，
  Loki/promtail 采集链不再可能出现被拆行的假日志行；滥用载荷在出浏览器前收敛。
- 新增用例 3 例（`inlineTruncate` 语义 + body 无裸换行 + 截断边界）。

## 2. 复验为"已完成/无回归"的历史项（本轮核实）

| 项 | 出处 | 现状 |
|---|---|---|
| rememberMe 真实语义（tokenScope 作用域标记） | AUDIT 记录项 4 | 已实现并三处接线：`login.vue` 登记 → `stores/user.ts` 登出清理 → `main.ts` 冷启动兑现 |
| `/privacy` `/terms` `/forgot-password` 缺失路由 | AUDIT 记录项 5 | 已注册（共用 `legal.vue`），routes.spec 已锁 |
| 错误上报凭证脱敏 | AUDIT 记录项 6 | 上一轮已实现 redactSecrets，本轮 L-3 补齐换行/长度维度 |
| XSS 攻击面 | 每轮必查 | `v-html`/`innerHTML`/`eval`/`new Function`/`srcdoc`/`postMessage` 全仓 **0 命中**；`legal.vue` 静态文案无注入点 |
| WS 建连不带 JWT（票据化） | G §2.5 | `WebSocketManager` 票据优先 + 防重入，auditG 8/8 在本构建上复验 |

## 3. 本轮记录项（不修，理由如下）

| 项 | 说明 |
|---|---|
| 上传扩展名前端预检（L-2） | 唯一上传入口是 `@airpower/web` 的 `AImage` 组件（库产物），未暴露 `accept`/白名单配置入口。后端已按 `FileCategory` 白名单拦截并返回明确文案（实测「文件类型不允许上传」），airpower 请求层统一 toast 该文案；429 限流同理。UX 预检留待库升级 |
| v-mask 列脱敏扩展（L-4） | 后端 `@SensitiveField` 实体集（User/Material/PurchasePrice/SalePrice）与前端 WIRING 四视图**已一一对应**，门禁（maskContract 8 条契约）在位；扩展到 customer/supplier 等需先在后端实体加敏感注解并配置脱敏策略，属产品行为变更而非缺陷，按功能排期 |
| 全量 vitest 首轮 2 例负载抖动 | 首轮 2 worker 并发 + 后台 JVM 争抢导致超时敏感用例失败，单独跑与复跑均绿（与 G 报告先例同款）；不判缺陷，建议 CI 用 `--maxWorkers=1` 或重试策略 |

## 4. 回归矩阵（本阶段实测）

| 层 | 结果 |
|---|---|
| vitest 全量 | **308/308**（33 文件；首轮 2 例负载抖动，复跑全绿） |
| vue-tsc --noEmit | **0 错误** |
| eslint --max-warnings 0 | **0 告警** |
| 黑盒抽验（后端未改动，auditH 全量） | **10/10**（登录/提权防御/SSRF/文件属主/coderule/WS Origin/错误上报） |
| 后端全量（本轮零改动，沿用 H 结论） | 684/684 |

## 5. 数据足迹

本轮前端改动不落任何业务数据；探针抽验使用 `k-h1@kubo.test`（脚本自清 + cleanProbe 兜底，
终态 `CLEAN: probe-users 0/0`）。

## 6. 复现入口

```bash
cd SPMS-Web
NODE_OPTIONS="--max-old-space-size=6144" npx vitest run --pool=threads --maxWorkers=2
npx vue-tsc --noEmit && npx eslint . --max-warnings 0
# 抽验（需后端 8080）
node SPMS-Server/target/auditH.cjs
```
