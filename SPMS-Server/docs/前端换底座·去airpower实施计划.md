# 前端换底座 · 去 airpower 实施计划

> 决策依据：用户指令「airpower 不需要它，改用既定技术栈」。
> 既定技术栈：**Vue 3 + Vite + Element Plus + ECharts + TanStack Query + Pinia + axios + vue-router**（后端 Spring Boot 3 + MyBatis 不变）。

## 一、为什么要换

- airpower 是「整个前端框架」：装饰器元数据 + 组件库 + hooks + HTTP 层 + 工具类，共 **192 个源文件**依赖它。
- 它把边界渗进了业务写法（`@Table/@Form/@Search`、`useEditor`、`ADialog`、`AbstractCurdService`…），
  换掉它的**唯一低成本路径**是：把它的对外契约在项目内重新实现一遍，业务代码只改 import 来源。

## 二、可行性：契约对齐即可零改动

| 业务写法 | 换底座后 |
| --- | --- |
| `import { ATable, Field, useEditor } from '@airpower/web'` | `from '@/framework'` |
| `@Model/@Field/@Table/@Form/@Search/@Type` | 自研装饰器 + 元数据注册表（语义逐条对齐） |
| `class XxxService extends AbstractCurdService<E>` | 自研服务基类，**URL 与后端 airpower 控制器完全一致** |
| `FeedbackUtil / DialogUtil / WebValidator / DateTimeFormatter / WebEnum / WebColor / DesensitizeType` | 同名同签名自研实现 |
| `ADialog / ATable / AFormField / APanel / ASelector / AGroup …` | 基于 Element Plus 的自研组件，props/slots 对齐 |

关键证据：`src/framework/__tests__/service.spec.ts` 锁死 URL（`/demo/getPage` 等）与分页请求体形状，
`metadata.spec.ts` 锁死「继承 + order 降序 + removed/hide 语义」——这两处一旦回归，后端契约与页面渲染会立刻红。

## 三、分批实施

| 批次 | 内容 | 状态 |
| --- | --- | --- |
| **F1 地基** | `src/framework/`：元数据装饰器、实体基类与序列化、查询模型、HTTP 与服务基类、校验、时间、反馈、枚举、配置 | ✅ 本批完成（20 个契约测试） |
| **F2 组件层** | `ATable`（含搜索区/分页/列选择/权限）、`ADialog`、`AFormField`、`APanel`、`ASelector`、`AGroup`、`AButton`、`AInput`、`ASelect`、`AMoney`、`ACopy`、`ATabs/ATab`、`ATreeBox`、`AImage`、`ADateTime`、`AUser`、`APayload`、`APage`、`AFrame`、`AEmpty`；hooks：`useTable` / `useEditor` / `useDetail` / `useTableTree` | 进行中 |
| **F3 试点** | 单页端到端（`system/unit`：列表 + 编辑弹窗 + 选择器），跑逐页探针确认无回归 | 待做 |
| **F4 批量迁移** | 按域替换 import 并逐域回归：`wms` → `channel` → `asset` → `factory` → `personnel` → `system`(+datapermission) → 公共组件（`src/component/**`） | 待做 |
| **F5 拆伞** | 删 `@airpower/web` 依赖、`src/config/airpower-bootstrap.ts`、`infra/plugins/airpower-*.ts` 两个 Vite 插件、airpower 样式；复核门禁与体积 | 待做 |

## 四、风险与对策

| 风险 | 对策 |
| --- | --- |
| 表格行为差异（搜索区、列选择、排序、权限按钮） | `ATable` 按现有 44 处用法逐 props 对齐；F3 用逐页冒烟探针 + `demoPreview` 门禁回归 |
| 表单/校验行为差异 | `WebValidator.createRules` 由 `@Form` 元数据生成，规则与字段同源；编辑弹窗逐页验证 |
| 演示模式（mock 适配器）失效 | 框架 HTTP 层复用 `src/api/axios.ts` 的同一实例，适配器继续生效 |
| 边角能力（导出/上传/图片/二维码） | 先按调用方契约保留入口，未实现处显式报错而不是静默失败（如 `DialogUtil.showUpload`） |
| 迁移期间两套库并存导致包体变大 | 体积门禁 `pnpm size` 仍在；F5 拆伞后应低于当前 342 KB 首屏 |

## 五、验收口径

1. `pnpm check` 全绿（lint / typecheck / 测试 / 权限码 / 构建 / 演示门禁 / 体积）；
2. 逐页冒烟探针 27 个菜单页全绿（`src/demo/__tests__/menuSmoke.probe.spec.ts`）；
3. 全仓库 `grep -r "@airpower" src` 为空；
4. 首屏体积不高于现基线（342 KB / 450 KB）。
