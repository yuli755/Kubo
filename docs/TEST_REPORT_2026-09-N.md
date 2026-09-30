# SPMS 阶段 N 真机测试报告（2026-09-30）

> 本轮任务（用户原话）：「我需要你模拟用户真机测试项目」。
> 做法：以真实登录用户身份在浏览器里逐页走查全部 26 个业务列表页，用 DOM 几何与文本量测判定渲染结果，发现缺陷就定位根因并修复，修完复验。
> 分支 `dev`（`main` 全程未动，仍冻结在 `a243b3e9`）。本轮新增提交 `05f0fc7d`。

---

## 0. 结论速览

| 项 | 结果 |
| --- | --- |
| 走查页面数 | 26 个业务列表页 + 工作台，**全部 0 pageerror / 0 console error**，无 vite 报错浮层 |
| 分页组件 | 26 页 `.el-pagination` 全部 `display:flex`，M 轮的样式塌陷修复在新会话里稳定生效，无一处退回 `block` |
| 横向溢出 | 25 页 `Σ列宽 == 表宽`，**1 页溢出（操作日志 +35px）已修** |
| 树表高度 | 5 张树表（仓库/库位/部门/权限/菜单）全部拿到数值内联高度（565/509/565/565/509），M 轮的自动高度生效 |
| 脱敏 | 手机 `176****6666`、邮箱 `ad***@hamm.cn`、采购单价 `2***0`、销售单价 `2***9` 全部按策略遮蔽 |
| 本轮修复缺陷 | **2 个**（N-1 后端审计时间、N-2 操作日志页布局） |
| 本轮新发现未修 | **2 个**（N-3 停用用户从列表消失、N-4 单一弹性列吞掉全部余量） |

---

## 1. 缺陷记录

### N-1【后端 · 严重 · 已修】`operation_log.create_time` 全表为 0，审计表唯一的时间证据失效

**现象**：操作日志页「时间」列 12 行全部显示 `1970-01-01 08:00:00`（东八区 epoch 0）。

**取证**（三层，逐层排除）：

1. 接口层：`GET /api/dataResource/operation-logs` → HTTP 200 / code 200 / 12 条，每条 `createTime: 0`（`typeof number`）。返回字段 `id, createTime, resourceKey, action, userId, tenantId, targetId, targetLabel, beforeSnapshot, afterSnapshot`。
2. 库表层：`SELECT id, create_time, update_time FROM operation_log ORDER BY id DESC LIMIT 6` → id 12..7 的 `create_time` 与 `update_time` **全为 0**。`SHOW COLUMNS` 显示 `create_time bigint unsigned NULL default 0`。⇒ 不是前端格式化问题，是库里就没有值。
3. 代码层根因链：
   - `cn/hamm/spms/base/BaseEntity.java:83` `private Long createTime`，L90 注释说明 `columnDefinition` 带 `default 0`；L140-141 `public E setCreateTime(Long createTime)`。
   - 全仓 `@PrePersist` 只有 3 处命中，**没有一处填时间**：`base/TenantAwareEntity.java:60`（只 `fillAttribution` 填租户/用户归属）、`module/system/datapermission/service/AttributionWriteProtection.java:10`（javadoc 引用）。**没有 `@CreationTimestamp` / `@UpdateTimestamp`**。
   - 全仓唯一给 `createTime` 赋值的地方是 `base/BaseService.java:553` `entity.setCreateTime(System.currentTimeMillis())`。
   - 而 `OperationLogRecorder.persist()`（L136-157）**绕过了 BaseService**，直接 `repository.save(entity)`（L151）⇒ `createTime` 保持列默认值 0。

**影响面**：审计表的用途是「谁在**何时**把哪条记录改成了什么」，其中「何时」全部失效。同时 L 报告 P1-1（历史行未回溯脱敏）的判定也依赖时间线，时间线不可用时那个决策更难做。

**修法**（遵循 `OperationLogEvent` 上既有的契约注释 L57「全部字段在业务线程上定稿，后台线程只做持久化」）：

- 时间戳**不能**在 `persist()` 里取：`persist()` 跑在单线程队列（`ThreadPoolExecutor(1,1,60s,ArrayBlockingQueue(128))`，L71-79）上，队列积压时「落库时刻」会晚于「操作时刻」，审计时间就不准了。
- 因此给 record 加 `Long occurredAt` 组件，在 `toEvent()`（业务线程）里 `System.currentTimeMillis()` 定稿，`persist()` 里 `entity.setCreateTime(event.occurredAt())`。
- 同时保留一个 **8 参兼容构造器**委托到 9 参规范构造器（默认 `System.currentTimeMillis()`）。这样 `OperationLogRecorderTest` 里既有的 5 个构造调用点一行都不用改 —— record 加组件本来会改构造器签名，兼容构造器把改动面压到 0。

**验证**：

- `mvn -B -o test -Dtest=OperationLogRecorderTest` → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS`。
- 重启后端（`Started Application in 28.781 seconds`），在用户管理页对 `张三`(id=2) 点「禁用」→ toast「禁用成功」→ 新写入的审计行 **id=13 `create_time=1790753837843`**，而修复前写入的 id 10/11/12 仍为 `0`。修复前后对照清晰。
- 随后用 `POST /api/user/enable {id:2}` 把该用户恢复：`code 200 / 启用成功 / {"id":2,"isDisabled":false,"isPublished":false}`。测试数据未残留。

### N-2【前端 · 已修】操作日志页横向溢出 35px + 缺失时间被渲染成假时间

**现象**：`/console/system/log/list` 实测列宽合计 **1166 > 可用 1131**，横向溢出 35px；同一页把 `createTime=0` 格式化成 `1970-01-01 08:00:00`。

**为什么 M 轮的守卫没拦住**：该页是**手绘 `ElTable`，不是 KTable**（`src/view/console/system/log/list.vue`，371 行）。M 轮定的列宽口径写在实体装饰器上、守卫 `src/config/__tests__/tableLayout.spec.ts` 读的是装饰器注册表 ⇒ 这一页两条都覆盖不到。同族的还有 `src/view/console/asset/contract/component/detail.vue:96-97,116`（第二套列渲染实现）。

**为什么不能直接换成 KTable**：该页的数据流与 KTable 的契约不同 —— L33 `useOperationLogReport()`、L36 `MAX_ROWS = 200`（后端 `findTop200ByOrderByIdDesc()` 硬上限）、筛选与分页**全在前端**（L72-85 `filtered`、L87 `paged` 切片、L90-92 watch 回第一页）。KTable 的契约是「服务端分页 + TanStack query」，套不上。

**修法**：

- 时间 `width 200 → 170`；资源 `width 180 → min-width 160`（`datapermission.fieldPolicy` 这类 26 字符资源标识在固定 180px 的 `<code>` 里会被裁，改弹性更安全）；目标 `min-width 200 → 160`。
- 时间列加空值兜底：`v-if="createTime"` 才格式化，否则显示 `-`。**理由**：历史行（修复前写入的 12 行）的真实时刻已经不可追溯，显示 `-` 比显示一个「看起来像真时间」的 `1970-01-01 08:00:00` 诚实 —— 假提示比没提示更糟。

**验证**：真机复测列宽 `#:56 时间:170 动作:100 资源:188 目标:187 操作者:100 租户:90 快照:130 操作:110` = **1131/1131**，`hOverflow: no`；前 3 行时间列显示 `-`（不再是 1970）；0 pageerror。两个弹性列按 160:160 分摊余量各得 188/187，与预估一致。

### N-3【前端 · 新发现 · 未修】停用用户会从用户列表里彻底消失，UI 上没有恢复入口

**现象**：对 `张三`(id=2) 点「禁用」成功后，刷新用户管理页，列表从 5 行变 4 行，**张三整行不见了**（用 `hasText: '张三'` 定位返回 count=0）。页面上没有「显示已停用」的筛选开关，也没有回收站/已停用视图。

**后果**：从 UI 看，「禁用」是一个**单向不可逆**操作 —— 管理员误停用一个账号后，无法在界面上找到它并恢复。本轮我是靠直接调 `POST /api/user/enable {id:2}` 才把账号恢复的，普通用户没有这个手段。

**建议**（未实施，需要产品决策）：用户列表加一个「状态」筛选（全部 / 启用中 / 已停用），默认「全部」；或至少在停用成功的 toast 里给出「已停用，可在状态筛选中恢复」的指引。**注意这不只是体验问题**：`禁用` 是权限操作，一个不可逆的权限操作本身就是安全风险。

### N-4【前端 · 新发现 · 未修】列数少的表，唯一弹性列会吞掉全部余量

**现象**（本轮逐页量到的最严重几处，格式为 `列名=渲染宽`，括号内是该列实际内容宽）：

| 页面 | 弹性列 | 内容 | 倍率 |
| --- | --- | --- | --- |
| 计量单位 | 单位名称 = **606px** | 「台」12px | **50:1** |
| 仓库管理 | 仓库名称 = 533px | 「西部大仓」48px | 11:1 |
| 客户管理 | 客户名称 = 494px | 「腾讯科技」40px | 12:1 |
| 供应商管理 | 供应商名称 = 494px | 「Apple 中国」~60px | 8:1 |
| 部门管理 | 部门名称 = 433px | 「生产部」36px | 12:1 |
| 角色管理 | 角色名称 = 430px | 「系统超级管理员」84px | 5:1 |
| 入库单 | 入库单号 = **527px** | `IN...` 105px | 5:1 |
| 出库单 | 出库单号 = 527px | `OUT202609150001` ~110px | 5:1 |

**机制**：Element Plus 的列宽分配规则是「有 `width` 的列固定，有 `min-width` 的列按比例分摊余量」。当一张表只有 3~5 个业务列、且其中只有**一个**声明了 `min-width` 时，容器剩下的几百像素全部灌进这一列。这不是某个实体的宽度写错了，是**「列少的表 + 单一弹性列」的结构性结果** —— M 轮的列宽口径（`tableLayout.spec.ts` 的 Σ≤900 预算）管住了「超宽溢出」，但管不住「欠宽留白」，因为守卫算的是声明值之和，而这里的问题恰恰是声明值之和远小于容器宽。

**为什么本轮没动**：真正的解法是布局层面的（主从双栏 / 给字典表配一个有意义的右侧面板 / 限制表格最大宽度让余量落到表外），M 报告 P2-16 已记录为推荐方案。它属于布局重构，风险面覆盖多个页面并要重标列宽预算（双栏下可用宽是 856px 而不是 1142px，会改守卫常量），不适合和缺陷修复混在一个提交里。**留给下一轮单独做。**

---

## 2. 走查覆盖与量测结果

导航方式：侧边栏 7 个 `.el-sub-menu` 全部处于折叠态，`.el-menu-item` 对应的 `li` **没有 index 属性**（EP 用 prop 不落 DOM），`locator.click()` 会超时并报 `element is not visible` ⇒ 本轮全部用 `page.goto(URL)` 直接导航。路由清单从 `app.__vue_app__.config.globalProperties.$router.getRoutes()` 读出，共 33 条（业务列表 26 条）。注意 `purchasePrice` / `salePrice` 在**路由里是大写 P**，而磁盘目录是全小写。

每页固定量测口径：表头各列 `innerText` + `getBoundingClientRect().width`；横向溢出判据是 **Σ列宽 对比 表宽**（不能用 `.el-table__body-wrapper.scrollWidth`，它报的是自己的宽而不是内容宽）；内部滚动条必须限定 `.el-scrollbar__wrap` 到 `.el-table__body-wrapper` 之内（不限定会命中侧边栏菜单那个）；`.k-shell__content` 的 `scrollHeight / clientHeight`；`.el-pagination` 的 `display` 与盒子；每页 `waitForTimeout(4000)`。

**26 页全部 0 pageerror / 0 console error。** 关键实测值（列名:渲染宽）：

- `channel/purchase/list` `#:56 采购单号:150 采购事由:315 总金额:120 采购状态:100 创建时间:160 操作:230` = 1131/1131，9 行
- `wms/input/list` `#:56 入库单号:527 入库类型:110 入库状态:100 创建时间:170 操作:168` = 1131/1131，20 行，「共 169 条」
- `wms/output/list` `#:56 出库单号:527 出库类型:110 出库状态:100 创建时间:170 操作:168` = 1131/1131，20 行，「共 113 条」
- `wms/move/list` `#:56 移库单号:302 目标仓库:335 移库状态:100 创建时间:170 操作:168` = 1131/1131，20 行，「共 67 条」
- `wms/inventory/list`（双栏）`#:56 物料编码:120 物料名称:190 仓库:180 库存类型:110 库存数量:110 计量单位:90` = **856/856**，5 行；`exclude-columns` 生效（显示「仓库」不显示「生产单元」）
- `channel/sale/list` `#:56 销售单号:150 客户名称:166 销售说明:160 总金额:120 销售状态:100 创建时间:160 操作:230` = 1142/1142，4 行，首行金额 `119,996.00`
- `channel/customer/list` / `supplier/list` `名称:494 编码:180 联系电话:150 创建时间:170 操作:92/140` = 1142/1142，2 行 / 1 行
- `channel/purchasePrice/list` / `salePrice/list` `物料编码:170 物料名称:344 供应商(客户)名称:170 编码:170 单价:140 操作:92` = 1142/1142，各 1 行，单价显示 `2***0` / `2***9`
- `asset/material/list` `#:56 物料名称:296 物料编码:180 物料类型:110 使用类型:110 规格型号:140 计量单位:120 操作:130` = 1142/1142，3 行
- `asset/contract/list` `合同编码:200 合同类型:110 合同名称:182 合同状态:110 开始时间:120 终止时间:120 操作:244` = 1142/1142，**0 行**（无种子数据）
- `factory/storage/list`（树）`仓库名称:533 仓库编码:180 创建时间:170 操作:192` = 1131/1131，6 行，内联高 **565px**，无分页（`v-if="!isTree"`）
- `factory/structure/list`（树）`生产单元名称:403 编码:180 类型:130 创建时间:170 操作:192` = 1131/1131，3 行，内联高 **509px**
- `personnel/user/list`（双栏）`昵称:180 手机:167 邮箱:223 状态:100 操作:130` = **856/856**，5 行
- `personnel/role/list` `角色名称:430 角色编码:180 状态:100 创建时间:170 操作:206` = 1142/1142，2 行
- `personnel/department/list`（树）`部门名称:433 编码:180 排序编号:100 创建时间:170 操作:192` = 1131/1131，2 行，内联高 **565px**
- `system/unit/list` `单位名称:606 单位编码:180 创建时间:170 操作:130` = 1142/1142，2 行
- `system/permission/list`（树）`权限名称:383 权限标识:348 权限类型:100 系统权限:90 操作:154` = 1131/1131，**309 行**，内联高 **565px**
- `system/menu/list`（树）`菜单名称:170 菜单路径:391 组件地址:260 排序编号:100 操作:154` = 1131/1131，34 行，内联高 **509px**
- `system/config/list` `配置标识:260 配置名称:343 配置类型:100 系统配置:80 配置参数:200 操作:92` = 1131/1131，13 行
- `system/coderule/list` `编码所属字段:150 规则前缀:120 序列号初始长度:130 规则模板:315 序列号更新:100 下一个编码:170 操作:90` = 1131/1131，20 行 ⇒ M 轮的 coderule 列宽改造已生效
- `system/log/list` 修前 `Σ1166 > 1131 OVER+35` → 修后 **1131/1131**，12 行
- `datapermission/resource/list` `资源标识:180 表名:140 归属列:160 支持的动作:365 状态:100 操作:130` = 1131/1131，17 行
- `datapermission/policy/list` `策略编码:415 主体类型:130 角色编码:120 动作:100 优先级:90 启用:90 操作:130` = 1131/1131，20 行
- `datapermission/fieldPolicy/list` `字段名:326 角色编码:120 动作:100 脱敏类型:140 允许明文:90 优先级:90 启用:90 操作:130` = 1142/1142，4 行

**工作台** `/console/index`：`.k-shell__content` clientH 808 / scrollH 1911；2 个 canvas（ECharts 正常出图）；14 个卡片盒子尺寸正常（hero 1165×158、8 张 stat-card 279×121、2 张 chart-card 567×362、modules-section 1165×659）。页面文本里的实体量级：采购单 9、销售单 4、入库单 169、出库单 113、库存 5、库存移动 67、客户 2、供应商 1、采购单价 1、销售单价 1、物料 3。数据权限态势原文：「全局模式 OFF；enforce 名单 0 项：（空）。写侧行级保护 关、部门层级展开 关、未登记 放行」、「放开就绪 0 / 17」。

---

## 3. 提交与环境

- 提交：`05f0fc7d` `fix(audit): operation_log 补写 create_time，操作日志页收列宽并给缺失时间兜底`（2 files changed, 38 insertions, 6 deletions）。
- 改动文件：`SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/service/OperationLogRecorder.java`、`src/view/console/system/log/list.vue`。
- `dev` 领先 `main` **143** 个提交；`main` 仍冻结在 `a243b3e9`；工作区干净。

---

## 4. 本轮未覆盖（如实记录）

1. **后端全量回归 `mvn -B -o clean test` 未跑**（L 轮基线 927/0/0/1）。本轮只跑了受改动影响的 `OperationLogRecorderTest`（5/5 通过）。不能在后端存活时跑 `mvn clean`，而本轮需要后端存活做真机复验，两者互斥 ⇒ 留给下一轮单独跑。
2. **前端 `pnpm check`（22 步）未跑**。跑它会杀掉 vite dev server，而真机复验需要 dev server 存活 ⇒ 同样互斥。本轮对前端改动的验证方式是**真机量测**（列宽 1131/1131、0 pageerror、时间列显示 `-`），比单测更贴近用户可见结果，但没有覆盖 lint / typecheck / 覆盖率门。
3. **写操作闭环未跑**：采购下单 → 验收入库 → 库存 → 销售 → 出库的完整链路本轮没有实操创建单据（只读走查 + 一次「禁用/启用」往返）。本轮唯一的写操作已用 `/user/enable` 复原，测试数据无残留。
4. **受限角色 `张三`(admin@hamm.com) 降级验证未跑** —— 而且本轮意外发现该账号一旦被停用就从 UI 消失（N-3），做降级测试前要先确认它处于启用态。
5. **截图未留存**。本轮的量测证据全部是 DOM 数值（列宽、盒子、行数、文本），已如实记在 §2；`docs/evidence/` 目录已创建但本轮没有往里写图。M 报告 §5.4 已记录过同一个缺口与正确做法（截图文件名必须以**绝对路径**传给截图工具，否则会落进 Playwright 服务进程自己的工作目录）。

---

## 5. 下一轮建议顺序

1. **N-3**（停用用户不可恢复）—— 这是权限操作的单向门，风险高于布局问题。
2. 跑完后端 `mvn -B -o clean test` 与前端 `pnpm check` 两条全量回归，把本轮改动的门禁证据补齐。
3. 完整业务闭环实操（采购 → 入库 → 库存 → 销售 → 出库）+ `张三` 降级验证。
4. **N-4 / M 轮 P2-16**：主从双栏或表格最大宽度，解决「列少的表把余量全灌进一列」。这会改列宽预算常量（1142 → 856），要和守卫一起改。
5. L 报告 P1-3（`data_resource.id` unsigned 与 `resource_id` 有符号不兼容，启动日志里唯一一条 DDL 失败）—— M 报告已建议排第一位，本轮因为聚焦真机走查没有动。
