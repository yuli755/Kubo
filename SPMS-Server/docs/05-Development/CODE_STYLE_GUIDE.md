# Kubo Platform Code Style Guide

> **Version**: 1.0
> **Status**: 🟢 Active
> **Last Updated**: 2026-08-02
> **Enforcement**: CI Gate (Checkstyle / Spotless / ESLint)

---

## 🎯 Principles

1. **Consistency > Preference** - 统一胜过个人偏好
2. **Readability > Cleverness** - 可读性优于技巧性
3. **Explicit > Implicit** - 显式优于隐式
4. **Fail Fast** - 尽早暴露问题

---

## ☕ Java (Backend)

### Formatting (Spotless + Google Java Format)

```xml
<!-- pom.xml spotless-maven-plugin 配置 -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>2.42.1</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.23.0</version>
                <style>GOOGLE</style>
            </googleJavaFormat>
            <licenseHeader>
                <content>/*
 * Copyright (c) 2026 Kubo Platform
 * All rights reserved.
 */</content>
            </licenseHeader>
            <importOrder>
                <order>java,javax,jakarta,org,com,cn.hamm.airpower,cn.hamm.spms</order>
            </importOrder>
        </java>
    </configuration>
</plugin>
```

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Package | 全小写、单数、功能域分层 | `cn.hamm.spms.module.wms.inventory` |
| Class/Interface | PascalCase、名词 | `InventoryEntity`, `InventoryService` |
| Enum | PascalCase、单数 | `InventoryStatus` |
| Enum Constant | UPPER_SNAKE_CASE | `PUBLISHED`, `OUT_OF_STOCK` |
| Method | camelCase、动词开头 | `getAvailableQuantity()`, `deductStock()` |
| Variable | camelCase、名词 | `availableQuantity`, `warehouseId` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Type Parameter | 单个大写字母 | `<T>`, `<E extends BaseEntity>` |

### Code Structure

```java
// 1. 文件头注释 (强制)
/**
 * <h1>库存实体</h1>
 * <p>描述库存的核心属性和业务行为</p>
 *
 * @author Hamm.cn
 * @version 1.0
 * @since 2026-08-01
 */

// 2. 注解顺序 (实体类)
//    J3（2026-09-18）起实体只标 MyBatis-Plus 的映射注解：@TableName / @TableField / @TableId。
//    索引与唯一键不在注解里：schema 的唯一真相是 infra/init-sql/init.sql（ddl:check 与 schemaContract 守着两边一致）。
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("inventory")
@Description("库存实体")
public class InventoryEntity extends BaseEntity<InventoryEntity> {

    // 3. 字段顺序: （id 在 CurdEntity 上，不在实体里重复声明）-> 业务主键 -> 关联对象 + 其标量外键 -> 基础属性 -> 状态/审计字段

    @Description("仓库")
    @TableField(exist = false)          // 关系对象不是列：MP 只映射列，写它会去查一个叫 warehouse 的列
    private WarehouseEntity warehouse;

    @Description("仓库 ID")
    @TableField("warehouse_id")         // 真正落库的是这个标量字段（写由 RelationMapping 同步、读由 RelationHydrator 回填）
    private Long warehouseId;

    @Schema(description = "物料", requiredMode = Schema.RequiredMode.REQUIRED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private MaterialEntity material;

    @Column(columnDefinition = "decimal(20,6) default 0 comment '可用数量'")
    @Schema(description = "可用数量")
    private BigDecimal availableQty;

    @Column(columnDefinition = "bit(1) default 0 comment '是否冻结'")
    @Schema(description = "是否冻结")
    private Boolean frozen;

    // 4. 构造器、Getter/Setter (Lombok @Data 自动生成)

    // 5. 业务方法 (放在最后)
    public boolean canDeduct(BigDecimal quantity) {
        return availableQty != null && availableQty.compareTo(quantity) >= 0 && !Boolean.TRUE.equals(frozen);
    }
}
```

### Controller Layer

```java
@Tag(name = "库存管理", description = "库存查询、扣减、调拨、盘点")
@Api("inventory")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "分页查询库存", description = "支持仓库、物料、状态多维过滤")
    @GetMapping("/page")
    public PageResult<InventoryVO> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "页大小") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "仓库 ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "物料 ID") @RequestParam(required = false) Long materialId,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status) {
        return inventoryService.page(page, pageSize, warehouseId, materialId, status);
    }

    @Operation(summary = "库存扣减", description = "原子扣减，支持幂等")
    @PostMapping("/deduct")
    public Result<Void> deduct(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryDeductCmd cmd) {
        inventoryService.deduct(cmd, idempotencyKey);
        return Result.success();
    }
}
```

### Service Layer

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository repository;
    private final RedisTemplate<String, String> redisTemplate;

    @GlobalTransactional(timeoutMills = 30000, name = "inventory-deduct")
    public void deduct(InventoryDeductCmd cmd, String idempotencyKey) {
        // 1. 幂等检查
        if (!idempotentCheck(idempotencyKey)) {
            CustomError.IDEMPOTENT_CONFLICT.show(); // 业务错误码统一登记在 CustomError 枚举
        }

        // 2. Redis 预扣减 (Lua 脚本原子操作)
        boolean preDeducted = redisDeduct(cmd.getWarehouseId(), cmd.getMaterialId(), cmd.getQuantity());
        if (!preDeducted) {
            CustomError.INSUFFICIENT_STOCK.show();
        }

        // 3. 数据库扣减 (乐观锁)
        int updated = repository.deductWithVersion(
            cmd.getWarehouseId(), cmd.getMaterialId(), cmd.getQuantity(), cmd.getVersion());
        if (updated == 0) {
            // 回滚 Redis 预扣减
            redisRollback(cmd.getWarehouseId(), cmd.getMaterialId(), cmd.getQuantity());
            CustomError.VERSION_CONFLICT.show();
        }

        // 4. 发布领域事件
        publishEvent(new InventoryDeductedEvent(cmd.getWarehouseId(), cmd.getMaterialId(), cmd.getQuantity()));
    }
}
```

### Prohibited Patterns

| ❌ 禁止 | ✅ 推荐 |
|---------|---------|
| `public void method() { ... }` 无注解 | `@Operation` + `@Api` + 参数校验 |
| `try { ... } catch (Exception e) { log.error(e.getMessage()) }` | 具体异常类型 + 完整堆栈 |
| `new ArrayList<>()` 无初始容量 | `new ArrayList<>(expectedSize)` |
| `String.format()` 在热路径 | `StringBuilder` 或预编译模板 |
| `Optional.ofNullable(x).orElse(y)` 连续调用 | 单次 `Optional.ofNullable(x).orElse(y)` |
| 硬编码字符串 | 常量类 `Constants` 或枚举 |

---

## 🌐 TypeScript (Frontend)

### Formatting (ESLint + Prettier)

```json
// .eslintrc.js / eslint.config.js
{
  "extends": [
    "@antfu/eslint-config",
    "plugin:vue/vue3-recommended",
    "plugin:@typescript-eslint/recommended"
  ],
  "rules": {
    "vue/multi-word-component-names": "off",
    "ts/consistent-type-imports": "error",
    "no-console": ["warn", { "allow": ["warn", "error"] }],
    "curly": ["error", "all"],
    "eqeqeq": ["error", "always"],
    "@typescript-eslint/no-unused-vars": ["error", { "argsIgnorePattern": "^_" }]
  },
  "formatters": {
    "prettier": {
      "semi": false,
      "singleQuote": true,
      "tabWidth": 2,
      "trailingComma": "es5",
      "printWidth": 100
    }
  }
}
```

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| File (Vue) | kebab-case | `inventory-list.vue`, `inventory-editor.vue` |
| File (TS) | PascalCase | `InventoryEntity.ts`, `InventoryService.ts` |
| Component | PascalCase | `InventoryList`, `InventoryEditor` |
| Composables | camelCase + `use` 前缀 | `useInventory()`, `usePermission()` |
| Variables/Functions | camelCase | `fetchInventoryList()`, `inventoryList` |
| Types/Interfaces | PascalCase + `I` 前缀 | `IInventoryVO`, `IInventoryQuery` |
| Enums | PascalCase | `InventoryStatus` |
| Enum Members | PascalCase | `Published`, `OutOfStock` |
| Constants | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE`, `DEFAULT_WAREHOUSE_ID` |
| CSS Classes | kebab-case | `.inventory-list`, `.stock-status--low` |

### Vue Component Structure

```vue
<script setup lang="ts">
// 1. 类型导入
import type { InventoryEntity, InventoryQuery } from '@/api/generated'

// 2. Composables 导入
import { useInventory } from '@/api/generated'
import { usePermission } from '@/composables/usePermission'
import { useFormat } from '@/locales/composables/useFormat'

// 3. Props / Emits 定义
interface Props {
  readonly initialQuery?: InventoryQuery
  readonly warehouseId?: number
}
const props = withDefaults(defineProps<Props>(), {
  initialQuery: () => ({}),
  warehouseId: undefined
})

const emit = defineEmits<{
  edit: [entity: InventoryEntity]
  refresh: []
}>()

// 4. 响应式状态
const query = reactive<InventoryQuery>({
  page: 1,
  pageSize: 20,
  ...props.initialQuery
})

// 5. Composables 调用
const { t, tc } = useI18n()
const { formatCurrency, formatDate } = useFormat()
const { check: hasPermission } = usePermission()

const { data: pageData, loading, refetch } = useInventoryList(query)

// 6. 计算属性
const isLowStock = computed(() => (entity: InventoryEntity) =>
  entity.availableQty !== null && entity.availableQty.lt(entity.warningThreshold))

// 7. 方法
async function onEdit(entity: InventoryEntity) {
  emit('edit', entity)
}

async function onRefresh() {
  await refetch()
}

// 8. 生命周期
onMounted(() => {
  if (props.warehouseId) {
    query.warehouseId = props.warehouseId
    refetch()
  }
})
</script>

<template>
  <div class="inventory-page">
    <!-- 搜索栏 -->
    <ProSearch
      v-model="query"
      :schema="searchSchema"
      @search="refetch"
      @reset="query = getDefaultQuery()"
    />

    <!-- 表格 -->
    <VirtualTable
      v-if="pageData"
      :data="pageData.content"
      :loading="loading"
      :total="pageData.totalElements"
      :columns="columns"
      :row-key="row => row.id"
      @page-change="handlePageChange"
    >
      <template #action="{ row }">
        <el-button
          size="small"
          :disabled="!hasPermission('inventory:edit')"
          @click="onEdit(row)"
        >
          {{ t('common.edit') }}
        </el-button>
      </template>
    </VirtualTable>

    <!-- 分页 -->
    <ProPagination
      v-show="pageData"
      :total="pageData.totalElements"
      :page-size="query.pageSize"
      :current-page="query.page"
      @update:page="query.page = $event"
      @update:page-size="handlePageSizeChange"
    />
  </div>
</template>

<style scoped lang="scss">
.inventory-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}
</style>
```

### API Layer

```typescript
// src/api/generated/inventory.ts (自动生成，勿手动修改)
// 手动封装业务逻辑放在 src/api/business/inventory.ts

import type { InventoryDeductCmd, InventoryEntity, InventoryQuery } from '@/api/generated'
// src/api/business/inventory.ts
import { inventoryApi } from '@/api/generated'

export const inventoryBusinessApi = {
  // 分页查询 (自动缓存 30s)
  async getList(query: InventoryQuery) {
    return inventoryApi.getInventoryList(query)
  },

  // 库存扣减 (带幂等)
  async deduct(cmd: InventoryDeductCmd, idempotencyKey: string) {
    return inventoryApi.deductInventory(cmd, {
      headers: { 'Idempotency-Key': idempotencyKey }
    })
  },

  // 批量扣减 (事务)
  async batchDeduct(cmds: InventoryDeductCmd[]) {
    return inventoryApi.batchDeductInventory(cmds)
  }
}
```

### Prohibited Patterns

| ❌ 禁止 | ✅ 推荐 |
|---------|---------|
| `any` 类型 | 严格类型、泛型约束 |
| `console.log` 生产代码 | `logger.debug()` / `useMonitor()` |
| `v-if` + `v-for` 同元素 | 包裹 `<template>` |
| `v-model` 直接绑定 props | `defineModel()` (Vue 3.4+) |
| 直接操作 DOM | Ref + Composables |
| 组件内直接 `fetch/axios` | 统一 `useRequest` / 生成的 API |
| 硬编码字符串 | `t('i18n.key')` / 常量文件 |

---

## 🗄️ SQL (Database)

### 分层（硬规则，门禁 `pnpm sql:check`）

> **禁止 `.java` 文件编写 SQL 语句，只能使用专属文件编写。**（2026-09-18 定）

| 类型 | 专属文件 | 说明 |
| --- | --- | --- |
| 生产 SQL | `SPMS-Server/src/main/resources/mapper/**/*.xml` | MyBatis mapper XML（`mybatis-plus.mapper-locations: classpath*:/mapper/**/*.xml`）。接口只留方法声明，语句、列名、`LIMIT`、`FOR UPDATE` 全在 XML 里 |
| 测试夹具 / 验证查询 | `SPMS-Server/src/test/resources/sql/**/*.sql` | 用 `cn.hamm.spms.support.SqlFixtures` 加载（`execute` 执行脚本、`text` 取单条语句、支持 `${占位符}`） |

**为什么**：① SQL 可以被 review、被 DBA 读到、能直接拿去 `EXPLAIN`；② 换 ORM 时不用把同一道题重做一遍 ——
`@Query`（JPA）与 `@Select`/`@Delete`（MyBatis 注解）都是"把语句藏进 Java"，两者都被这条规则挡掉。

**判据**（`infra/scripts/check-sql-in-java.mjs`，剥注释后只查字符串字面量与注解）：
`select … from` / `insert into` / `delete from` / `update … set` / `truncate|alter|create|drop table` /
`for update` / `order by` / `group by` / `limit N` / 裸 `where` 片段；注解面：`@Select`/`@Insert`/`@Update`/`@Delete`/
`@SelectKey`/`@*Provider`/`@Query`/`nativeQuery = true`/`createNativeQuery(`。
`sortBy`、`orderNo`、`updateTime` 这类**标识符不会误报**（只有字面量与注解才算）。

**唯一豁免**：非可执行 SQL —— 安全测试里的**恶意输入样例**（`"1; DROP TABLE x"` 是"值"，用来断言它不会被拼进 SQL）、
以及被测对象的输入。写法是违规行（或上一行）加 `// SQL-IGNORE: 理由`；门禁会把全部豁免连理由一起打印出来，
所以"悄悄加一个豁免"在 review 与 CI 日志里都看得见。生产代码不留任何豁免。

### Naming

| Object | Convention | Example |
|--------|------------|---------|
| Table | snake_case、复数 | `inventories`, `inventory_flows` |
| Column | snake_case | `warehouse_id`, `available_qty` |
| Index | `idx_{table}_{columns}` | `idx_inventories_warehouse_material` |
| Foreign Key | `fk_{table}_{ref_table}` | `fk_inventories_warehouses` |
| Sequence | `seq_{table}` | `seq_inventories` |

### DDL Standards

```sql
-- 表定义模板
CREATE TABLE inventories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '租户 ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库 ID',
    material_id BIGINT UNSIGNED NOT NULL COMMENT '物料 ID',
    location_id BIGINT UNSIGNED DEFAULT NULL COMMENT '货位 ID',

    available_qty DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0 COMMENT '可用数量',
    locked_qty DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0 COMMENT '锁定数量',
    frozen_qty DECIMAL(20,6) UNSIGNED NOT NULL DEFAULT 0 COMMENT '冻结数量',
    warning_threshold DECIMAL(20,6) UNSIGNED DEFAULT NULL COMMENT '预警阈值',

    is_frozen BIT(1) NOT NULL DEFAULT 0 COMMENT '是否冻结',
    version INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本',

    created_by BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT UNSIGNED NOT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted BIT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_warehouse_material (tenant_id, warehouse_id, material_id),
    INDEX idx_warehouse_material (warehouse_id, material_id),
    INDEX idx_tenant_warehouse (tenant_id, warehouse_id),
    INDEX idx_location (location_id),
    INDEX idx_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存表';

-- 分区表示例 (按月分区)
CREATE TABLE inventory_flows (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT UNSIGNED NOT NULL,
    warehouse_id BIGINT UNSIGNED NOT NULL,
    material_id BIGINT UNSIGNED NOT NULL,
    flow_type TINYINT UNSIGNED NOT NULL COMMENT '1入库 2出库 3调拨 4盘点',
    quantity DECIMAL(20,6) NOT NULL,
    unit_cost DECIMAL(20,6) DEFAULT NULL,
    reference_type VARCHAR(50) DEFAULT NULL,
    reference_id BIGINT UNSIGNED DEFAULT NULL,
    operator_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id, created_at),
    INDEX idx_warehouse_material_time (warehouse_id, material_id, created_at),
    INDEX idx_reference (reference_type, reference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水'
PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    PARTITION p_max VALUES LESS THAN MAXVALUE
);
```

---

## 🔧 CI Enforcement

```yaml
# .github/workflows/code-quality.yml
name: Code Quality

on: [push, pull_request]

jobs:
  backend-style:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {java-version: '17', distribution: temurin}
      - run: ./mvnw spotless:check checkstyle:check -q

  frontend-style:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
        with: {version: 9}
      - uses: actions/setup-node@v4
        with: {node-version: 20, cache: pnpm}
      - run: pnpm install --frozen-lockfile
      - run: pnpm run lint

  sql-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: sqlfluff-action@v1
        with: {args: lint --dialect mysql}
```

---

## 📋 Code Review Checklist (Automated)

```markdown
# PR 自动检查项 (CI Gate)

## Backend
- [ ] Spotless 格式检查通过
- [ ] Checkstyle 检查通过
- [ ] 单元测试覆盖率 > 80% (核心 > 95%)
- [ ] 无 Checkstyle/Spotless/Sonar 新增警告
- [ ] 无硬编码字符串 (除常量类)
- [ ] 所有 Public API 有 @Operation/@Schema 注解

## Frontend
- [ ] ESLint 无错误
- [ ] TypeScript 编译无错误
- [ ] 单测覆盖率 > 80%
- [ ] 无 `any` 类型 (除第三方库适配)
- [ ] 组件遵循 `<script setup>` + 组合式 API
- [ ] 无直接 DOM 操作

## Database
- [ ] 所有表有主键、创建/更新时间、版本、逻辑删除
- [ ] 索引命名规范、无冗余索引
- [ ] 无 `SELECT *`、无隐式类型转换
- [ ] 改表结构改了 `infra/init-sql/init.sql`（同时改实体，`pnpm ddl:check` 会红）
```

---

*Last Updated: 2026-08-02*
*Enforcement: CI Gate Required*
