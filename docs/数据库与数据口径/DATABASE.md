# Kubo 数据库表结构文档

> 本文档根据系统实际实体结构（JPA Entity）与 Flyway 版本化迁移脚本（V1–V10）整理生成。
> 表结构在生产环境由 **Flyway** 自动管理演进；本地开发与测试通过验证模式保障实体与数据库零偏差。

---

## 📊 数据库信息

```yaml
数据库名: kubo
默认字符集: utf8mb4
排序规则: utf8mb4_unicode_ci
存储引擎: InnoDB
```

---

## 📋 数据表分类（共 41 个业务实体表 + 中间表）

### 1. 系统核心与权限表 (System & Personnel)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `user` | 用户表 | `cn.hamm.spms.module.personnel.user.UserEntity` |
| `department` | 部门表 | `cn.hamm.spms.module.personnel.department.DepartmentEntity` |
| `role` | 角色表 | `cn.hamm.spms.module.personnel.role.RoleEntity` |
| `user_department` | 用户与部门多对多关联表 | 中间表 |
| `user_role` | 用户与角色多对多关联表 | 中间表 |
| `role_permission` | 角色与权限多对多关联表 | 中间表 |
| `personal_token` | 个人访问令牌表 | `cn.hamm.spms.module.personnel.user.token.PersonalTokenEntity` |
| `permission` | 权限标识表 | `cn.hamm.spms.module.system.permission.PermissionEntity` |
| `menu` | 菜单导航表 | `cn.hamm.spms.module.system.menu.MenuEntity` |
| `config` | 系统全局配置表 | `cn.hamm.spms.module.system.config.ConfigEntity` |
| `code_rule` | 单据编码规则表 | `cn.hamm.spms.module.system.coderule.CodeRuleEntity` |
| `unit` | 计量单位表 | `cn.hamm.spms.module.system.unit.UnitEntity` |
| `file` | 上传文件元数据表 | `cn.hamm.spms.module.system.file.FileEntity` |

### 2. 多租户与数据安全表 (Data Permission & Security)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `tenant` | 租户主表 | `cn.hamm.spms.module.system.tenant.TenantEntity` |
| `user_tenant` | 用户租户关联表 | `cn.hamm.spms.module.system.tenant.UserTenantEntity` |
| `data_resource` | 数据权限受控资源登记表 | `cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity` |
| `data_policy` | 行级数据过滤策略表 | `cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity` |
| `data_field_policy` | 列级字段脱敏策略表 | `cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity` |
| `data_permission_diff_log` | 影子模式对账差异日志表 | `cn.hamm.spms.module.system.datapermission.entity.DataPermissionDiffEntity` |
| `data_permission_deny_log` | 越权拦截与拒绝访问审计表 | `cn.hamm.spms.module.system.datapermission.entity.DataPermissionDenyLogEntity` |
| `data_permission_orm_parity_log` | ORM 等价性校验日志表 | `cn.hamm.spms.module.system.datapermission.entity.DataPermissionOrmParityEntity` |
| `permission_change_outbox` | 权限变更事件发件箱（Outbox） | `cn.hamm.spms.module.system.datapermission.entity.PermissionChangeOutboxEntity` |
| `operation_log` | 业务操作审计日志表 | `cn.hamm.spms.module.system.datapermission.entity.OperationLogEntity` |

### 3. 资产与合同管理表 (Asset)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `material` | 物料档案表 | `cn.hamm.spms.module.asset.material.MaterialEntity` |
| `contract` | 合同主表 | `cn.hamm.spms.module.asset.contract.ContractEntity` |
| `contract_document` | 合同电子文档关联表 | `cn.hamm.spms.module.asset.contract.document.ContractDocumentEntity` |
| `participant` | 合同签署与参与方表 | `cn.hamm.spms.module.asset.contract.participant.ParticipantEntity` |

### 4. 渠道与供应链表 (Channel)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `customer` | 客户档案表 | `cn.hamm.spms.module.channel.customer.CustomerEntity` |
| `supplier` | 供应商档案表 | `cn.hamm.spms.module.channel.supplier.SupplierEntity` |
| `purchase_price` | 采购基准价格表 | `cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity` |
| `sale_price` | 销售基准价格表 | `cn.hamm.spms.module.channel.saleprice.SalePriceEntity` |
| `purchase` | 采购订单主表 | `cn.hamm.spms.module.channel.purchase.PurchaseEntity` |
| `purchase_detail` | 采购订单明细表 | `cn.hamm.spms.module.channel.purchase.detail.PurchaseDetailEntity` |
| `sale` | 销售订单主表 | `cn.hamm.spms.module.channel.sale.SaleEntity` |
| `sale_detail` | 销售订单明细表 | `cn.hamm.spms.module.channel.sale.detail.SaleDetailEntity` |

### 5. 工厂与空间建模表 (Factory)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `storage` | 仓库与仓位层级表 | `cn.hamm.spms.module.factory.storage.StorageEntity` |
| `structure` | 生产车间与作业空间表 | `cn.hamm.spms.module.factory.structure.StructureEntity` |

### 6. WMS 仓储作业表 (WMS)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `inventory` | 实时库存现存量表 | `cn.hamm.spms.module.wms.inventory.InventoryEntity` |
| `input` | 入库单主表 | `cn.hamm.spms.module.wms.input.InputEntity` |
| `input_detail` | 入库单明细表 | `cn.hamm.spms.module.wms.input.detail.InputDetailEntity` |
| `output` | 出库单主表 | `cn.hamm.spms.module.wms.output.OutputEntity` |
| `output_detail` | 出库单明细表 | `cn.hamm.spms.module.wms.output.detail.OutputDetailEntity` |
| `move` | 库内移位/跨库调拨单主表 | `cn.hamm.spms.module.wms.move.MoveEntity` |
| `move_detail` | 移库单明细表 | `cn.hamm.spms.module.wms.move.detail.MoveDetailEntity` |

---

## 🔗 通用实体继承树与字段约束

### 1. `BaseEntity` 基础实体
所有实体基类，提供全局通用生命周期追踪字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | bigint | 主键 ID (自增) |
| `create_time` | bigint | 创建时间戳 (毫秒) |
| `update_time` | bigint | 更新时间戳 (毫秒) |
| `is_disabled` | tinyint(1) | 是否禁用 (0-正常 1-禁用) |

### 2. `TenantAwareEntity` 多租户业务实体
继承自 `BaseEntity`，所有支持行级多租户隔离与归属控制的业务表均继承此基类：

| 字段名 | 类型 | 说明 | 约束规则 |
|--------|------|------|----------|
| `tenant_id` | bigint | 租户 ID | `@ReadOnly` 写侧强隔离，由当前会话上下文强制注入 |
| `creator_id` | bigint | 创建人用户 ID | `@ReadOnly` 归属不可变硬约束，禁止客户端修改或篡改 |

### 3. `AbstractBaseBillEntity` 业务单据主表
继承自 `TenantAwareEntity`，为单据主表提供标准状态机流转控制：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `code` | varchar(255) | 单据编号（由编码规则引擎自动生成） |
| `status` | int | 单据状态（草稿/待审核/审核通过/驳回/作业中/已完成） |

### 4. `BaseBillDetailEntity` 单据明细表
单据关联物料与作业完成量跟踪：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `bill_id` | bigint | 所属主单据 ID |
| `quantity` | double | 计划数量 / 录入数量 |
| `finish_quantity`| double | 实际作业完成数量（分布式互斥排他锁保护） |
| `is_finished` | tinyint(1) | 该行明细是否全部作业完毕 |

---

## 📊 初始系统种子数据

开发环境启动时由 `DevDataInitRunner` 自动播种基础字典与演示数据：

### 1. 系统管理员账号
- 账户 1：`admin@hamm.cn` / 手机 `17666666666` / 密码 `Aa123456`
- 账户 2：`admin@hamm.com` / 手机 `13888888888` / 密码 `Aa123456`

### 2. 基础组织架构
- 部门：`生产部`（编码 a1）、`材料部`（编码 aaa1）
- 计量单位：`台`

### 3. 供应链基准
- 客户：`凌云科技`
- 供应商：`Apple 中国`
- 基础物料：`MacBook Pro M4 Max` (规格: 32G-1TB)
- 销售价：¥29,999.00 / 采购价：¥28,000.00

### 4. 仓储设施
- 仓库：`东部大仓`（东部1仓、东部2仓）、`西部大仓`（西部1仓、西部2仓）
- 作业空间：`笔记本电脑产线`（工位1、工位2）

---

## 🛠️ 数据库变更与迁移规范

1. **结构事实源**：实体（JPA Entity）定义 Java 领域模型；数据库 Schema 由 **Flyway** 版本化演进。
2. **迁移脚本位置**：`SPMS-Server/src/main/resources/db/migration/V{N}__{description}.sql`。
3. **环境规范**：
   - 本地开发：可使用 `validate` 或 `update`，脚本执行受 `init.lock` 防重保护。
   - 生产环境：强制 `spring.jpa.hibernate.ddl-auto: validate`，严禁生产自动 DDL 改表，必须走 Flyway 幂等脚本演进。
