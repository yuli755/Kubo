# Kubo Web 开发规范（当前底座）

> 供 AI 编程助手与人类开发者参考。**这份文档在 2026-09 换底座时整体重写过**：
> 旧版本教的是 `@airpower/web` 的 `APanel`/`ATable`/`ADialog` 写法 —— 那套已于 2026-09-18
> **整体退场（0 引用）**，仓库里再出现它的引用会被 `pnpm airpower:check` 直接拦下，
> 照旧文档写会写出**死栈代码**。全过程与踩坑见 `SPMS-Server/docs/前端F4·airpower 逐域迁移分批清单.md`。
>
> 新代码只认下面这一套。技术栈与版本以 `pnpm stack` 为准，解读见 `SPMS-Server/docs/前端技术栈现状.md`。

## 一、底座在哪

| 层 | 位置 | 说明 |
| --- | --- | --- |
| 框架层 | `src/framework/**` | 元数据装饰器、实体基类、HTTP/服务基类、查询模型、反馈（toast/确认/命令式弹窗）、枚举字典、格式化、单元格、导出、**上传** |
| 实体基类 | `src/base/KEntity.ts` | 新页实体的基类（注册框架层元数据） |
| 框架组件 | `src/component/framework/K*.vue` | `KTable` / `KFormDialog` / `KPanel` / `KSelector` / `KDetail` / `KDialog` / `KUpload` |
| 数据层 | `src/hook/kubo/useTable.ts`、`useDetail.ts` | 列表页与详情页的状态与请求流（TanStack Query） |
| 视图层 | `src/view/console/<域>/...` | 路由路径 == 视图路径；菜单由后端下发（演示模式用 fixtures） |

**视图层不得直接 import `@/model/**Service`**（ESLint `no-restricted-imports` 拦），统一经 `src/hook/<域>/*` 转发。

## 二、硬规则（每条都有门禁或踩坑记录）

1. **SQL 只能写在专属文件里**（用户 2026-09-18 定）：生产语句进 `SPMS-Server/src/main/resources/mapper/**/*.xml`，测试夹具进 `src/test/resources/sql/**`（`SqlFixtures` 加载）。`@Query`/`@Select`/`@Delete`/`last("…")` 全算违规，`pnpm sql:check` 是硬门禁；唯一豁免是"非可执行 SQL"（恶意输入样例），且必须写 `// SQL-IGNORE: 理由`。
1. **不许出现 `@airpower/web`**（已整体退场）。新文件、新页面一律走 `src/framework` + `K*` 组件；
   `pnpm airpower:check` 是硬门禁 —— 剥注释后出现任何 `@airpower/*` 的 import 或依赖即红。
2. **模板里的 `<el-*>` 必须显式 `import`**（如 `import { ElButton } from 'element-plus'`）。
   新用到的 Element Plus 组件要在 `src/types/element-plus-aggregate.ts` 里追加 re-export ——
   该聚合入口是 `element-plus` 别名的落点，**从它 import 的组件会进首屏**，所以只加"首屏真要用"的；
   体积敏感的（如日期/时间选择器）走子路径，见 `KFormDialog.vue` 的注释。
   忘补的后果是 `vue-tsc`/单测全绿、**只有 `pnpm build` 会炸** —— `elementPlusAggregate.spec.ts` 现在会提前拦下。
3. **一切查询/写操作走服务类**：页面持有 `useTable(KXxxService, { queryKey })`，弹窗用 `KFormDialog`。
   页面不自己拼 URL、不自己 `new Axios`。
4. **实体只挂 K 装饰器**：`@KModel/@KTable/@KForm/@KField/@KSearch`（`KEntity` 注册表驱动表格与表单）。
   继承根仍是 `src/base/BaseEntity.ts`（已不 import airpower），F5 收敛时再统一。
5. **`KDialog` 只 `emit`**：使用方写 `@confirm="props.onConfirm?.(值)"` 转发。
   不要写 `:on-confirm="handler"`（会静默不派发），也不要手动调 `props.onConfirm`（会回调两次）。
6. **命令式弹窗**：`DialogUtil.show/select/selectList/showUpload`；取消时 resolve `undefined`，调用方必须判空。
7. **路由/菜单位置**：新增页面要同时进角色权限与菜单种子，否则「做了页面却点不到」——
   `src/config/__tests__/menuContract.spec.ts` 会拦。

## 三、编码风格

- ESLint 用 `@antfu/eslint-config`（2 空格、单引号、无分号）；提交前 `pnpm lint:ci` 必须干净。
- 类型：`vue-tsc --noEmit` 必须干净；泛型组件进测试时用 `X as unknown as Component`。
- 命名：组件 `PascalCase.vue`；hook `useXxx.ts`；服务 `KXxxService.ts`（新）/ `XxxService.ts`（存量）；实体 `XxxEntity.ts`。
- 注释写**为什么**（尤其是反直觉的坑），不写"这里加了一个变量"式废注释。

## 四、提交前跑什么

```bash
pnpm check          # 全链：lint → 死代码（文件级/导出级）→ typecheck → test:coverage → 各契约门禁 → build → demo:check → size
pnpm stack          # 当前技术栈一览（含"死重量"：零引用依赖、没人用的 public 资源）
pnpm airpower:check  # 前端 airpower 不得回潮（剥注释后 0 引用才算过）
pnpm airpower:backend -- --check  # 后端 airpower 归零门禁（代码/pom 依赖/父 POM/yml 配置键 四项都必须为 0）
pnpm stack:project   # 整个仓库的技术栈（前端+后端+部署+CI，读真文件，不靠人写）
pnpm pom:check       # 后端 pom 自持校验（B0 之后没有父 POM 兜底，靠这道离线闸门）
pnpm java:imports    # Java 导入可达性（没有 JDK 时的半个编译器：import 必须落地 / 自持层不得反依赖 airpower / 同名副本必须显式承认）
pnpm jpa:check       # JPA 足迹棘轮（去 JPA 的尺子：文件 / 引用 / import 只许降；`-- --json` 出机器版）
pnpm sql:check       # SQL 分层门禁：禁止 .java 写 SQL，只能住 mapper XML（测试夹具住 src/test/resources/sql）
pnpm layer:check     # 分层门禁：依赖方向（core→framework→base→module）+ 层内职责（控制器不注入仓储、实体不依赖服务…）
```

体积红线：首屏 ≤ 450 KB、总体 ≤ 900 KB（`pnpm size`）；第一次超预算基本都是"把懒加载页面的依赖挪进了首屏"。

## 五、相关文档

| 文档 | 内容 |
| --- | --- |
| `SPMS-Server/docs/项目整体技术栈详细信息.md` | **整个项目**的技术栈（版本、架构、部署、CI、退场现状与存量决策项） |
| `SPMS-Server/docs/后端airpower退场·可行性评估与分批建议.md` | 后端退场的分批（B0–B8）、每批口径、为什么必须先有编译环境 |
| `SPMS-Server/docs/前端技术栈现状.md` | 现在装的是什么、和目标栈差在哪、怎么删（纯删 / 边删边补 / 先补才能删） |
| `SPMS-Server/docs/前端F4·airpower 逐域迁移分批清单.md` | 逐域迁移的批次、每批验收口径、踩坑 |
| `SPMS-Server/docs/前端换底座·去airpower实施计划.md` | 换底座的总体设计与取舍 |
| `STARTUP.md` / `LOGIN.md` | 本地启动、测试账号 |
