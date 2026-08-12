# Kubo Platform Git Commit Specification

> **Version**: 1.0  
> **Status**: 🟢 Active  
> **Last Updated**: 2026-08-02  
> **Enforcement**: Commitlint + Husky + CI Gate

---

## 📋 Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 规则

| 部分 | 必填 | 规则 |
|------|------|------|
| **type** | ✅ | 从预定义列表选择 |
| **scope** | ✅ | 模块名、组件名、或 `*` |
| **subject** | ✅ | 祈使语气、小写开头、不超过 72 字符、无句号 |
| **body** | ⭕ | 说明动机、对比变更前后、换行 72 字符 |
| **footer** | ⭕ | 关联 Issue、Breaking Change 声明 |

---

## 🏷️ Type 类型定义

| Type | 含义 | 示例 | 版本影响 |
|------|------|------|----------|
| **feat** | 新功能 | `feat(wms): 新增库存预扣减接口` | MINOR |
| **fix** | 缺陷修复 | `fix(auth): 修复 Token 刷新并发问题` | PATCH |
| **perf** | 性能优化 | `perf(wms): 优化库存扣减 Lua 脚本` | PATCH |
| **refactor** | 重构 (非功能/非修复) | `refactor(common): 统一请求网关拦截器` | PATCH |
| **style** | 代码风格 (格式、分号等) | `style: 统一缩进为 2 空格` | 无 |
| **docs** | 文档变更 | `docs(api): 更新 WebSocket 协议文档` | 无 |
| **test** | 测试相关 | `test(wms): 增加库存扣减单测` | 无 |
| **chore** | 构建/工具/依赖/配置 | `chore(deps): 升级 Spring Boot 到 3.2.5` | 无/PATCH |
| **ci** | CI/CD 配置 | `ci: 添加性能回归测试门禁` | 无 |
| **build** | 构建系统/外部依赖 | `build: 调整 Docker 镜像构建参数` | 无 |
| **revert** | 回滚提交 | `revert: 撤销 feat(wms): 新增预扣减` | 视原提交 |

### 特殊 Type

| Type | 场景 | 示例 |
|------|------|------|
| **breaking** | 破坏性变更 (配合 footer) | `feat(api)!: 重构用户认证接口` |
| **security** | 安全修复 | `security(auth): 修复 JWT 算法混淆漏洞` |

---

## 🎯 Scope 规范

### 后端 Scope (模块名)

| Scope | 含义 | 示例 |
|-------|------|------|
| `auth` | 认证授权 | `feat(auth): 新增 OAuth2 授权码模式` |
| `gateway` | API 网关 | `fix(gateway): 修复跨域预检请求` |
| `system` | 系统管理 | `feat(system): 新增字典批量导入` |
| `wms` | 仓储管理 | `feat(wms): 新增库存预扣减` |
| `mes` | 制造执行 | `perf(mes): 优化工单排程算法` |
| `finance` | 财务核算 | `fix(finance): 修复凭证借贷不平衡` |
| `procurement` | 采购管理 | `feat(procurement): 新增供应商对账` |
| `sales` | 销售管理 | `feat(sales): 新增订单自动分拣` |
| `asset` | 资产设备 | `refactor(asset): 重构点检计划生成` |
| `personnel` | 人事组织 | `feat(personnel): 新增考勤异常告警` |
| `factory` | 工厂建模 | `docs(factory): 更新 BOM 版本管理说明` |
| `iot` | 物联网关 | `feat(iot): 新增 Modbus TCP 解析` |
| `report` | 报表分析 | `perf(report): 优化大数据导出` |
| `notify` | 通知中心 | `fix(notify): 修复短信发送重复` |
| `file` | 文件存储 | `feat(file): 新增断点续传` |
| `common` | 公共基础库 | `chore(common): 升级错误码枚举` |
| `infra` | 基础设施 | `ci(infra): 新增混沌工程演练` |

### 前端 Scope (包/应用名)

| Scope | 含义 | 示例 |
|-------|------|------|
| `web-main` | 主应用 | `feat(web-main): 新增首页仪表盘` |
| `web-admin` | 管理后台 | `fix(web-admin): 修复菜单折叠状态` |
| `request` | 请求网关库 | `feat(request): 新增离线队列` |
| `query` | 服务端状态库 | `perf(query): 优化缓存失效策略` |
| `ws` | WebSocket 库 | `feat(ws): 新增心跳重连策略` |
| `permission` | 权限引擎库 | `refactor(permission): 重构指令实现` |
| `components` | 业务组件库 | `feat(components): 新增 VirtualTable` |
| `monitor` | 性能监控库 | `feat(monitor): 新增 LongTask 采集` |
| `utils` | 工具函数库 | `chore(utils): 新增格式化函数` |
| `config` | 共享配置 | `chore(config): 更新 ESLint 规则` |
| `locales` | 国际化 | `feat(locales): 新增繁体中文支持` |

### 通用 Scope

| Scope | 含义 |
|-------|------|
| `*` | 跨模块、全局变更 |
| `deps` | 依赖升级 |
| `release` | 版本发布 |
| `docs` | 纯文档变更 |

---

## 📝 Subject 编写规范

### ✅ 正确示例

```
feat(wms): 新增库存批量预扣减接口
fix(auth): 修复刷新令牌并发刷新导致登出
perf(wms): 优化库存扣减 Lua 脚本减少 Redis 往返
refactor(common): 统一异常处理为全局切面
docs(api): 补充 WebSocket 协议文档 v1.1
test(wms): 增加库存扣减边界场景单测
chore(deps): 升级 MyBatis-Plus 到 3.5.7
ci: 新增 PR 自动跑单测+覆盖率门禁
```

### ❌ 错误示例

```
# 类型错误
update(wms): 新增接口          → feat(wms): 新增接口
modify(auth): 修复bug          → fix(auth): 修复并发登录问题
change(wms): 优化代码          → refactor(wms): 重构库存服务层

# 主题格式错误
feat(wms): 新增库存预扣减接口。  → 去掉句号
feat(wms): Add inventory API     → 中文
feat(wms): 新增库存预扣减接口，支持批量操作，性能提升30%  → 超过 72 字符

# Scope 错误
feat(inventory): 新增接口        → feat(wms): 新增接口
feat(backend): 新增接口          → feat(wms): 新增接口
```

---

## 📄 Body 编写指南

### 何时需要 Body

- 复杂变更、架构调整
- 性能优化 (需说明优化前后对比)
- 重构 (说明重构动机、影响范围)
- 破坏性变更 (说明迁移指南)

### Body 模板

```
# 动机
说明为什么要做这个变更，解决什么问题

# 变更内容
- 变更点 1
- 变更点 2

# 影响范围
- 受影响模块: wms, finance
- 数据库变更: 新增 inventory_locks 表
- API 变更: 新增 POST /api/v1/inventory/batch-deduct

# 性能对比 (如适用)
| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 单 SKU 扣减 QPS | 3000 | 5000 | +66% |
| P99 延迟 | 120ms | 45ms | -62% |

# 迁移指南 (Breaking Change 时必填)
1. 更新依赖版本
2. 修改调用方式: ...
3. 数据库执行迁移脚本: V2026.08.02__add_inventory_locks.sql
```

---

## 🔚 Footer 规范

### 关联 Issue

```
Closes #123
Relates #456, #789
```

### Breaking Change 声明

```
BREAKING CHANGE: 接口响应格式从 {data: T} 变更为 {code, message, data}
迁移指南: https://wiki.kubo.example.com/migration/v2
```

### 依赖 Issue

```
Depends-on: #234
```

### 完整 Footer 示例

```
Closes #123
Relates #456

BREAKING CHANGE: 库存扣减接口参数从单条变更为批量数组
迁移指南: https://wiki.kubo.example.com/migration/v2-inventory-deduct

Co-authored-by: Zhang San <zhangsan@kubo.example.com>
```

---

## 📋 完整提交示例

### 标准功能提交

```
feat(wms): 新增库存批量预扣减接口

- 支持单次调用扣减多个 SKU
- 原子性保证：全成功或全失败
- 返回每条明细的扣减结果

Closes #123
```

### 复杂重构提交

```
refactor(common): 统一请求网关为 useRequest + @tanstack/vue-query

# 动机
原有 axios 直接调用分散在各 Service，缺乏统一缓存、重试、去重、离线队列

# 变更内容
- 新增 kubo-web-request 库封装 useRequest/useMutation
- 引入 @tanstack/vue-query 管理服务端状态
- 所有列表页迁移至 useQuery，详情页迁移至 useQuery
- 新增 RequestCache (memory/swr/persist 三级缓存)
- 新增 OfflineQueue (IndexedDB 持久化)

# 影响范围
- 受影响页面: 40+ 列表页、15+ 详情页
- API 无变更，仅前端调用方式变更

# 性能对比
| 指标 | 重构前 | 重构后 | 提升 |
|------|--------|--------|------|
| 重复请求率 | 15% | 0% | -100% |
| 缓存命中率 | 0% | 85% | +85% |
| 首屏加载 | 2.1s | 1.3s | -38% |

Relates #456, #789
```

### Breaking Change 提交

```
feat(api)!: 重构库存扣减接口为批量模式

BREAKING CHANGE: POST /api/v1/inventory/deduct 参数从单条对象变更为数组
原参数: {warehouseId, materialId, quantity}
新参数: [{warehouseId, materialId, quantity}]

迁移指南:
1. 前端调用处修改为数组格式
2. 后端 Controller 入参改为 List<InventoryDeductCmd>
3. 运行数据库迁移: V2026.08.02__alter_inventory_deduct.sql

Closes #123
```

---

## 🤖 自动化工具链

### 1. Commitlint 配置

```javascript
// commitlint.config.js
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    'type-enum': [2, 'always', [
      'feat', 'fix', 'perf', 'refactor', 'style', 'docs',
      'test', 'chore', 'ci', 'build', 'revert', 'breaking', 'security'
    ]],
    'scope-enum': [2, 'always', [
      'auth', 'gateway', 'system', 'wms', 'mes', 'finance',
      'procurement', 'sales', 'asset', 'personnel', 'factory',
      'iot', 'report', 'notify', 'file', 'common', 'infra',
      'web-main', 'web-admin', 'request', 'query', 'ws',
      'permission', 'components', 'monitor', 'utils', 'config',
      'locales', 'deps', 'release', 'deps', '*'
    ]],
    'subject-case': [2, 'always', 'lower-case'],
    'subject-max-length': [2, 'always', 72],
    'body-max-line-length': [2, 'always', 72],
    'footer-max-line-length': [2, 'always', 72]
  }
}
```

### 2. Husky Git Hooks

```bash
# 安装
pnpm add -D husky @commitlint/cli @commitlint/config-conventional
npx husky install

# 添加 commit-msg hook
npx husky add .husky/commit-msg 'npx --no -- commitlint --edit "$1"'

# 添加 pre-commit hook (可选)
npx husky add .husky/pre-commit 'pnpm run lint-staged'
```

### 3. lint-staged 配置

```json
// package.json
{
  "lint-staged": {
    "*.{js,ts,vue}": ["eslint --fix", "prettier --write"],
    "*.{java,xml}": ["mvnw spotless:apply -q"],
    "*.{md,yml,yaml,json}": ["prettier --write"]
  }
}
```

### 4. CI 门禁

```yaml
# .github/workflows/commit-lint.yml
name: Commit Lint

on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  commit-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 需要完整历史
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm install -g @commitlint/cli @commitlint/config-conventional
      - run: commitlint --from ${{ github.event.pull_request.base.sha }} --to ${{ github.event.pull_request.head.sha }} --verbose
```

---

## 📋 常见问题

### Q: 一个提交包含多个类型怎么办？
**A**: 拆分为多个提交，每个提交只做一件事。

### Q: 如何处理大型重构的提交？
**A**: 按逻辑模块拆分：`refactor(wms): 重构库存服务层 (1/3) - 提取接口`、`refactor(wms): 重构库存服务层 (2/3) - 迁移实现`、`refactor(wms): 重构库存服务层 (3/3) - 清理旧代码`。

### Q: 合并别人分支时 commit message 怎么写？
**A**: 使用 `Merge branch 'feat/xxx' into develop`，Git 自动生成，无需遵循规范。

### Q: 热修复分支提交规范？
**A**: 同主分支，建议 scope 加 `hotfix`：`fix(wms/hotfix): 修复库存扣减死锁问题`。

### Q: 如何修改已推送的提交信息？
**A**: 
```bash
# 仅修改最近一条
git commit --amend -m "feat(wms): 修正提交信息"
git push --force-with-lease

# 修改历史多条 (谨慎)
git rebase -i HEAD~3
# 然后 force push
```

---

## 📚 相关链接

- [Conventional Commits 1.0.0](https://www.conventionalcommits.org/zh-hans/v1.0.0/)
- [Commitlint 规则文档](https://commitlint.js.org/#/reference-rules)
- [GitHub 提交信息最佳实践](https://github.blog/2022-06-30-write-better-commits-build-better-projects/)

---

*Last Updated: 2026-08-02*  
*Enforcement: Commitlint + Husky + CI Gate Required*