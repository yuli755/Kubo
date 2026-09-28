# SPMS 测试报告 —— 阶段 J（真机 GUI 黑盒测试）

- 日期：2026-09-16
- 方式：浏览器真机实操（T1–T11 计划），截图存 `docs/gui-test-screenshots/`；
  测试数据统一 `AUDIT-GUI` 前缀，测后清理；全程不删除既有业务数据。
- 状态：**滚动更新中**——已确认缺陷见 §1，T 用例结果随进度追加于 §3。

## 1. 缺陷记录（发现即报）

### GUI-1 菜单被重复初始化 4 份（数据完整性缺陷 · 已修复+已清理）

- **现象**（T1 登录进控制台时发现）：侧边菜单每个一级模块及其全部子项出现 **4 份完全相同的拷贝**；
  数据库 `menu` 表共 171 行，8 个一级菜单名各 ×4。
- **影响**：非破坏性——路由按 path 注册、权限按 permission 路径匹配，功能可用，但界面严重劣化，
  且说明每次后端重启都会再复制一整棵菜单树（线性膨胀）。
- **根因链**（三因叠加）：
  1. `MenuService.initMenu()`（`SPMS-Server/src/main/java/cn/hamm/spms/module/system/menu/MenuService.java:36`）
     **无条件逐条 `add()`，无存在性检查**——与 `DevDataInitRunner` 其余 init 方法
     （initCodeRules/initConfigs/initParameters/initDevData/数据资源注册均有 `if exists return`）风格不一致；
  2. 其唯一防重入口是 `DevDataInitRunner.run()` 的 `init.lock` 文件检查，而 `new File("init.lock")`
     是**相对工作目录**的路径——后端从不同工作目录启动（IDE 运行配置差异）时锁查找失效；
  3. 本轮审计期间 `ddl-auto=update`（数据持久）+ `is-dev-mode: true`，锁失效窗口内每次重启全量重插。
     现存 `SPMS-Server/init.lock`（2026-09-14 21:37 生成）解释了其后重启未再新增。
- **修复**（`MenuService.initMenu()` 入口加守卫，与既有幂等风格一致）：
  ```java
  if (!filter(new MenuEntity()).isEmpty()) {
      return;
  }
  ```
  `mvn compile` 通过。守卫独立于 init.lock，工作目录问题不再构成数据风险。
- **数据处置**（遵循"不破坏既有数据"约束）：
  - 备份：`docs/backups/menu_backup_2026-09-16.sql`（mysqldump 全表 171 行，22.5 KB，可完整回滚）；
  - 删除旧 3 份拷贝 `id 1–126`，**保留 id 127–171**——只有最新一份是含
    「数据资源/行级数据权限/列级脱敏策略」的完整现行树（10 项系统管理子菜单），
    旧树缺 3 项且为陈旧代码版本产物；
  - 删除前置核查：`role_menu_list` 关联表 **0 行**；全库仅该表含 `menu_list_id` 列；
    授权实际经 `role_permission_list` 按权限路径关联，不受菜单行删除影响；
  - 删后验证：`COUNT=45`（8 一级 + 37 子项）；一级名重名 0；孤儿节点（parent 不存在）0；
    子菜单重名 0；`数据资源/行级数据权限/列级脱敏策略` 菜单在位（id 155/156/157）。
- **遗留建议**（report-only，不改）：`init.lock` 的 CWD 相对语义仍在——建议后续把锁改为
  基于数据源的标志（如 `config` 表 `INIT_DONE` 标志位），使初始化幂等性与运行目录彻底解耦。

### GUI-2 核心仪表盘两接口 500：库存统计 NPE + 参数名绑定缺失（已修复+已复验）

发现于 GUI-1 复验截图中首页「未能取得业务概况」。独立复现两组后端缺陷：

#### GUI-2-A `/dashboard/overview` → 500（InventoryService 空拆箱 NPE）

- 堆栈（traceId 6e9ebb8a）：`DashboardService.overview` → `InventoryService.getMapperCount`
  → `filter(new InventoryEntity())` → `InventoryService.beforeCreatePredicate:279` —
  `switch(DictionaryUtil.getDictionary(InventoryType.class, inventory.getType()))` 对
  **过滤模板**的 `Integer type` 无判空直接拆箱；仪表盘以空实体做整表统计必然触发。
- 修复：`beforeCreatePredicate` 入口判空——`type == null` 表示无类型条件可移除，原样返回。
- 验证：重启后 `/dashboard/overview` 200，14 项统计正确（入库 145/出库 97/库存 3/移动 61…）；
  GUI 首页 14 张业务概况卡全部渲染（截图 T1-dashboard-fixed.png）。

#### GUI-2-B `/dataResource/deny-logs` → 500（编译缺 `-parameters` + 可选参数缺注解）

- 堆栈（traceId c2fd71ee）：`Name for argument of type [java.lang.String] not specified,
  and parameter name information not available via reflection. Ensure that the compiler
  uses the '-parameters' flag.`
- 根因两层：
  1. `airpower:6.4.3` parent 未配置 `-parameters`，而 Spring Boot 3 下仓库惯例
     （`@RequestParam String resourceKey` 不写显式 name）完全依赖编译期参数名元数据；
  2. `DataResourceController.getDenyLogs/getOperationLogs` 的 `resourceKey` **连
     `@RequestParam` 都没标**——Spring 对未注解简单类型按必填处理，前端「省略=全量」契约
     即便有 -parameters 也会 400。
- 修复：
  1. `SPMS-Server/pom.xml` 增加 `maven-compiler-plugin` `<parameters>true</parameters>`（根治全场）；
  2. 两方法显式 `@RequestParam(name = "resourceKey", required = false)`。
- 验证：`deny-logs` 200 + `[]`，首页拒绝事件卡显示「最近无数据权限拒绝事件」（正确取向）。

#### 环境重大发现（影响本轮全部复验有效性）

排查中发现 **8 个残留 java 进程（4 对 spring-boot:run parent+fork）**——8080 由最老构建持有，
其后磁盘上的修复从未进入运行实例。已全部终止（仅匹配 `SPMS-Server|cn.hamm.spms` 命令行），
从 `SPMS-Server`（init.lock 所在目录）单实例重启，并复验：

- 菜单行数保持 **45**——重启未再触发初始化复制（init.lock + GUI-1 守卫双保险生效）；
- GUI-1/GUI-2 修复均在真实运行实例上复验通过；
- 清理脚本留存：`SPMS-Server/kill-stale.ps1`（可复用）。

## 2. 复验为正常的项

| 项 | 结果 |
|---|---|
| T2 未登录访问受保护路由 | **PASS**——守卫拦截跳 `/login`，登录后回跳原地址 |
| FIX-G1 airpower vue-router 双实例桥接 | **PASS**——AFrame/Menu/AppConfig 的 `useRoute()` 正常，无 `currentRoute` 崩溃 |
| T6 `/privacy` `/terms` | **PASS**——`legal.vue` 静态文案正常渲染，无注入点（截图 T6-privacy/T6-terms） |

## 3. T 用例执行记录（滚动追加）

- T1 登录/控制台/菜单链路：登录 200+token PASS；菜单 4× 重复 → 见 GUI-1，已修复清理，
  待 GUI 刷新复验（截图 T1-login-filled-3000）。

## 4. 数据足迹

| 操作 | 对象 | 可逆性 |
|---|---|---|
| DELETE 126 行 | `menu` 表（bug 产生的重复拷贝） | ✅ `docs/backups/menu_backup_2026-09-16.sql` 全量回滚 |
| （待补） | AUDIT-GUI 前缀测试数据 | 测后按前缀清理 |
