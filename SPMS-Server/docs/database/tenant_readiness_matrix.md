# 真多租户 · 归属就绪矩阵（脚本自动生成）

> ⚠️ **历史存档（2026-09-18 J3 之前的口径）**：这张表由 `tools/scripts/gen_multitenant_migration.py`
> 扫描 JPA 注解（`@Entity` / `@Column` / `@ManyToOne`）生成，脚本与 JPA 注解在 J3 之后都不在了。
> 表里的分级与结论仍是当期的事实记录（它决定了归属列的接线方式），但**数字不再随代码更新**：
> 要看现状请用 `pnpm ddl:check`（实体 ↔ init.sql）、`pnpm jpa:check`（实体注解口径）
> 与 `docs/去JPA决策评估.md` §9 的执行记录。矩阵里的表名/列名口径在 J3 后依然成立。
>
> 生成：`python3 tools/scripts/gen_multitenant_migration.py`；表格内容由扫描结果决定，请勿手工编辑。

- 扫描 `@Entity`：**56**；已注册数据资源：**24**
- 分级：T1 已注册 **23** / T2 明细表 **9** / T3 共享域 **15** / T4 未注册 **9**
- 需从「单列唯一」降为「(tenant_id, col) 复合唯一」的列：**32 个**（这是真多租户最大的兼容性风险点）
- ⚠️ 已注册但**没有任何可注入归属列**的资源（行级只能做 tenant 维度）：`asset.contract`, `asset.device`, `channel.customer`, `channel.purchase`, `channel.supplier`, `mes.bom`, `mes.operation`

## 如何读这张表

- `SQL 可注入的归属列`：来自 `@ManyToOne` 生成的外键列（`xxx_id`）或手写标量 `xxxId`。
  MyBatis/JSqlParser 在 SQL 文本层注入时**可以直接用这些列**，无需改实体；
  这正是之前 JPA `root.get("warehouseId")` 空转的根因（实体没映射该标量字段）。
- `M2M`：中间表关系，SQL 里没有标量列，必须在服务端展开成 ID 集合后注入 `IN (...)`。
- `T4`：未注册资源。enforce 模式下未注册即默认拒绝，因此要么补注册、要么显式登记为共享/豁免，
  **不允许靠 `catch → return 放行`**（当前 `DataPermissionSpecification:67` 就是这个形状）。

---

| 模块 | 资源键 | 注册 | 表 | SQL 可注入的归属列 | 唯一列(需降级) | 分级 | 说明 |
|---|---|---|---|---|---|---|---|
| asset | `asset.contractDocument` | — | `contract_document` | **无** | — | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| asset | `asset.contract` | ✅ | `contract` | **无**（M2M: document_list, participant_list） | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| asset | `asset.device` | ✅ | `device` | **无**（M2M: parameters） | 3 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| asset | `asset.material` | ✅ | `material` | `unit_id` | 2 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| asset | `asset.participant` | — | `participant` | **无** | — | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| channel | `channel.customer` | ✅ | `customer` | **无** | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| channel | `channel.purchaseDetail` | — | `purchase_detail` | `material_id`, `supplier_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| channel | `channel.purchase` | ✅ | `purchase` | **无** | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| channel | `channel.purchasePrice` | ✅ | `purchase_price` | `material_id`, `supplier_id` | — | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| channel | `channel.saleDetail` | — | `sale_detail` | `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| channel | `channel.sale` | ✅ | `sale` | `customer_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| channel | `channel.salePrice` | ✅ | `sale_price` | `customer_id`, `material_id` | — | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| channel | `channel.supplier` | ✅ | `supplier` | **无** | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| chat | `chat.member` | — | `member` | `room_id`, `user_id` | — | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| chat | `chat.room` | — | `room` | `owner_id` | 2 | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| factory | `factory.storage` | ✅ | `storage` | `parent_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| factory | `factory.structure` | ✅ | `structure` | `parent_id`（M2M: operation_list） | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| iot | `iot.parameter` | — | `parameter` | **无** | 2 | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| mes | `mes.bomDetail` | — | `bom_detail` | `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| mes | `mes.bom` | ✅ | `bom` | **无**（M2M: details） | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.operation` | ✅ | `operation` | **无** | 2 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.orderDetail` | — | `order_detail` | **无** | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| mes | `mes.order` | ✅ | `orders` | `customer_id`, `material_id`, `plan_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.pickingDetail` | — | `picking_detail` | `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| mes | `mes.picking` | ✅ | `picking` | `order_id`, `structure_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.planDetail` | — | `plan_detail` | `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| mes | `mes.plan` | ✅ | `plan` | `customer_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.routing` | ✅ | `routing` | `bom_id`, `material_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| mes | `mes.routingOperation` | — | `routing_operation` | `bom_id`, `operation_id`, `routing_id` | — | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| open | `open.notify` | — | `notify` | **无** | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| open | `open.openApp` | — | `open_app` | **无** | 1 | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| open | `open.userThirdLogin` | — | `user_third_login` | `user_id` | — | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| personnel | `personnel.department` | ✅ | `department` | `parent_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| personnel | `personnel.personalToken` | — | `personal_token` | `user_id` | 1 | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| personnel | `personnel.role` | ✅ | `role` | **无**（M2M: menu_list, permission_list） | 2 | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| personnel | `personnel.user` | ✅ | `user` | `department_id`（M2M: role_list, department_list, storage_list） | 2 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| system | `system.codeRule` | — | `coderule` | **无** | 1 | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.config` | — | `config` | **无** | 1 | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.dataFieldPolicy` | — | `data_field_policy` | `resource_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.dataPermissionDiff` | — | `data_permission_diff_log` | `tenant_id`, `user_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.dataPolicy` | — | `data_policy` | `resource_id`, `subject_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.dataResource` | — | `data_resource` | **无** | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.file` | — | `file` | **无** | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.menu` | — | `menu` | `parent_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.permissionChangeOutbox` | — | `permission_change_outbox` | `aggregate_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.permission` | — | `permission` | `parent_id` | 1 | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.tenant` | — | `tenant` | **无** | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| system | `system.unit` | — | `unit` | **无** | 2 | T4 | 未注册业务表 → 先只加列不拦截，按模块排期接入 |
| system | `system.userTenant` | — | `user_tenant` | `tenant_id`, `user_id` | — | T3 | 共享域（平台/字典/权限元数据）→ 不加租户列 |
| wms | `wms.inputDetail` | — | `input_detail` | `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| wms | `wms.input` | ✅ | `input` | `move_id`, `order_id`, `purchase_id`, `structure_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| wms | `wms.inventory` | ✅ | `inventory` | `material_id`, `storage_id`, `structure_id` | — | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| wms | `wms.moveDetail` | — | `move_detail` | `inventory_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| wms | `wms.move` | ✅ | `move` | `storage_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
| wms | `wms.outputDetail` | — | `output_detail` | `inventory_id`, `material_id` | — | T2 | 单据明细表 → 随主表加列（冗余 tenant_id 便于直接过滤，避免强制 JOIN） |
| wms | `wms.output` | ✅ | `output` | `move_id`, `picking_id`, `sale_id` | 1 | T1 | 已注册权限资源 → 本期必加 tenant_id/creator_id 并进 enforce 灰度 |
