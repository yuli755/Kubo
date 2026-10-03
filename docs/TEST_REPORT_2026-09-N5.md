# SPMS 测试报告 2026-09-N5（Round 46：修复建议 12 执行 + ROI 收尾清单 + 上线判定）

> 上一轮：TEST_REPORT_2026-09-N4.md（R45，commit `7b548d99`，后端 960/F0/E0）。
> 本轮输入（用户原话，m13605）：「执行修复建议12，并提供推进下一步建议清单（按推荐和投入价值比ROI排序，修复后有何收益效果？）和预估何时项目达到预期效果生产上线标准?真实的提供建议，不然就陷入无限优化循环之中了。」
> 三个交付物：①执行修复建议 12（本报告 §2–§3）；②ROI 排序清单（§4）；③上线时间预估与停止线（§5）。

## 1. 「修复建议 12」的定位

= L 报告 §4 P2-12 **控制台噪音**，两条：
1. `ECharts grid.containLabel` 已降级为 legacy 通道，未注册 `LegacyGridContainLabel` 插件时开发态每次渲染警告一次；
2. `Runtime directive used on component with non-element root node … at <ElTooltip>` 重复警告。

M 报告 §4.9 曾裁决：第 1 条一行改掉；第 2 条「要升级 EP 才有解，推荐不改但记录在案」。本轮不复用旧结论，两条都重新实测。

## 2. 执行：ECharts 半边（改掉）

- 全仓唯一 `containLabel` 在 `src/view/console/index/SecurityDashboard.vue:217`（`buildParityOption()`）。
- 权威等价替换（echarts 6.1.0 自带类型原文，`node_modules/echarts/types/dist/shared.d.ts:2958`）：
  「`grid.containLabel` is equivalent to `{outerBoundsMode: 'same', outerBoundsContain: 'axisLabel'}`」⇒ 布局像素等价，警告消除。
- **不走** `use(LegacyGridContainLabel)`：该通道上游已封存（`shared.d.ts:12716`「[CAUTION] Never export methods other than installLegacyGridContainLabel」），且警告本身被 `"development" !== 'production'` 包住、生产无影响——这是卫生项不是缺陷。
- 落地：`grid: { left:'8%', right:'4%', top:'8%', bottom:'22%', outerBoundsMode:'same' as const, outerBoundsContain:'axisLabel' as const }`（`as const` 是必需的：无上下文类型时字面量加宽成 `string` 会被 vue-tsc 拒；本文件既有约定如此）。
- 回归锁：`SecurityDashboard.spec.ts` 测试 1 新增三条断言（`containLabel` 必须 undefined、`outerBoundsMode==='same'`、`outerBoundsContain==='axisLabel'`）。
- **负样本探针（铁律）**：把 vue 改回 `containLabel: true` ⇒ `Tests 2 failed | 1 passed`，失败点 `SecurityDashboard.spec.ts:144` `AssertionError: expected true to be undefined`。断言会咬。变异已还原（`git diff` 复核仅含正式实现）。

## 3. 执行：ElTooltip 半边（实测已自愈，销案）

- 环境事实：`node_modules/element-plus/package.json` 已装 **2.14.4**（声明 ^2.12.0）；L 报告的警告记录于 ^2.12 时代。
- 仓库侧排查：四处 ElTooltip 消费者（ThemeSwitcher.vue:28、KTable.vue:649-658、WarehouseHeatmap.vue:627-636、components.d.ts 声明）均无指令挂在 tooltip 上；全仓 grep `v-if/v-show/v-permission…` 于 el-tooltip 零命中 ⇒ 警告源在 EP 内部（其 content.vue 渲染函数确有 `withDirectives` 调用）。
- 一次性探针（jsdom 挂 ElTooltip + mouseenter，数警告）：`PROBE_RESULT total_warns=0 runtime_directive_hits=0`。**EP 2.14.4 下该警告不再复现** ⇒ M 报告「升级 EP 才有解」的预期已被满足，P2-12 整条销案，探针文件已删。

## 4. ROI 排序的下一步清单（每条都有本轮实况核对，不是过期台账）

核对到的现状：Flyway 最新 `V15__operation_log_vocabulary.sql`（V16 空闲）；`ParticipantMapper.java` 全仓 **0 个**；`AssetServices.java` 已无死 getter（P2-13 已消化）；仓内无 ArchUnit；`.github/workflows/ci.yml` 存在且为硬门禁（web 22 步 / server compile+test 去 continue-on-error / migrations 真实 MySQL 8 / dependency-review PR 门）。

| # | 事项 | 投入 | 收益（修复后得到什么） |
|---|------|------|------------------------|
| 1 | **P1-3：V16 修 FK 符号位**（调 `V12:174-186` 现成的 `kubo_align_resource_id`）+ 零 DDL 失败 gate | 0.5–1 天 | 唯一已知的真实 MySQL 启动期 schema 错误消除，`ddl-auto=validate` 能过——**部署的硬前置**，M 报告自评「性价比最高」 |
| 2 | **推送触发 CI 全量**（web/server/migrations 三 job） | 0（等机器额度是本地的事，CI 在干净机跑） | R45 的 960 测试 + 22 步链 + build/size/coverage 获得正式口径证据；本地 OOM 阻塞的三项（build/size/coverage）由 CI 补上，不再依赖本机内存 |
| 3 | **P1-2：participant PII 收编**（建 ParticipantMapper + 注册 `asset.participant` + 脱敏） | ~1 天 | 最后一个未映射的 PII 资源进数据权限/脱敏体系；注意必须先建 mapper 再注册，否则 `DataResourceBindingRegistry.java:115` 启动报错 |
| 4 | **真机冒烟**：`kubo:*` 限流键 MONITOR + canary ENFORCE 预热 | 0.5 天（需真实 Redis/MySQL 环境） | 限流/封禁电路从「静态门禁绿」升级为「真环境验证过」，上线 confidence 的最后一级 |
| 5 | P1-4：结构守卫补洞（ArchUnit 禁 `BaseService` 上的 `@Transactional` 等） | 0.5–1 天（需新增 test 依赖） | 防架构回退的结构性防线；不加也不出 bug，是保险 |
| 6 | P2-6/7/11：退场语义收敛（一个 commit） | 1 天 | 数据清理/退场语义一致；**P2 级，发布后做不迟** |
| 7 | P1-1 审计表历史行脱敏 | 政策答复后 0.5 天 | **不是工程问题是政策问题**：等你回答「审计表允许被修正吗？」；允许→UPDATE 脚本+锁；不允许→出书面豁免即关闭 |
| 8 | P2-13 | — | **已消化**（本轮 grep 确认死 getter 不在库中），从未完成清单移除 |

明确**不推荐**再投入的（防无限优化循环）：P2-16 布局美化、view 层 ratchet 继续下压、EP 内部警告、覆盖率往 90%+ 打磨、18 张 legacy 表 DROP（V16 之后随运维窗口，不阻塞上线）——这些在 §5 判定线之后一律进 post-release backlog。

## 5. 上线判定与时间预估（含停止线）

**go-live 硬标准（缺一不可）**：
- G1 P1 清零：#1、#3 完成；#7 以「修掉或书面豁免」任一方式关闭；
- G2 CI 三 job 全绿一次（= #2，同时补上 build/size/coverage 欠账）；
- G3 真实环境冒烟通过（#4 + migrations job 对真 MySQL 的迁移/幂等/漂移三判据）。

**预估**：工程侧净投入 2.5–3.5 人日（#1+#3+#4+回归），全部是小改动+回归锁，无重构。**若机器额度与真实环境本周到位、政策问题本周有人拍板：约 2 个日历周达到可上线状态**（G2/G3 可在修复合入后 1–2 天内并行完成）；乐观口径 8–10 个工作日。

**停止线（建议采纳）**：G1–G3 达成即发版灰度，P2 全部（含 #6、#8 之外的遗留）转 post-release backlog，每两周回顾一次按线上反馈排优先级——继续在本地补跑门禁、压 ratchet、做布局，属于第 4 节末尾明列的无限优化区，不再计入上线前置。

## 6. 本轮改动清单

- `src/view/console/index/SecurityDashboard.vue`：`containLabel:true` → `outerBoundsMode:'same' + outerBoundsContain:'axisLabel'`（含依据注释）。
- `src/view/console/index/__tests__/SecurityDashboard.spec.ts`：测试 1 增加 outerBounds 三断言（负样本已证会咬）。
- 本报告。验证：spec 3/3 绿（修复态）、变异态 2 failed（`spec:144`）、`pnpm typecheck` EXIT=0。build/coverage 仍受本机 commit-charge 饥饿阻塞，正式口径由 CI 补（§4 #2）。
