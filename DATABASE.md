# Kubo 数据库表结构文档

> ⚠️ **这份列表不是建库依据**：建库的唯一真值是 `infra/init-sql/init.sql`（结构）+ `init_data.sql`（数据），
> 由 compose 首次启动自动执行或手工 `mysql < 文件`。**应用不建表、不改表**
> （2026-09-18 J4：`spring-boot-starter-data-jpa` 与 `spring.jpa`/`ddl-auto` 已整体退场）。
>
> 本文档早期由 JPA 实体自动生成，现在**人工维护**：与 `init.sql` 不一致时，以 `init.sql` 为准，
> 并由 `pnpm ddl:check`（列集合）与 `schemaContract.spec.ts`（含唯一键）在 CI 里对账 ——
> 发现漂移请改脚本或实体，而不是改这里的描述性文字。
>
> 数据权限相关的十张表（`data_resource` / `data_policy` / `data_field_policy` /
> `permission_change_outbox` / `tenant` / `user_tenant` / `user_factory` /
> `data_permission_diff_log` / `data_permission_deny_log` / `operation_log`）同样在这两份脚本里；
> 归属列矩阵见 `SPMS-Server/docs/database/tenant_readiness_matrix.md`。

## 📊 数据库信息

```yaml
数据库名: kubo
字符集: utf8mb4
排序规则: utf8mb4_unicode_ci
引擎: InnoDB
```

---

## 📋 数据表分类

### 1. 系统核心表 (System)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `user` | 用户表 | `module.personnel.user.UserEntity` |
| `department` | 部门表 | `module.personnel.department.DepartmentEntity` |
| `role` | 角色表 | `module.personnel.role.RoleEntity` |
| `permission` | 权限表 | `module.system.permission.PermissionEntity` |
| `menu` | 菜单表 | `module.system.menu.MenuEntity` |
| `config` | 系统配置表 | `module.system.config.ConfigEntity` |
| `code_rule` | 编码规则表 | `module.system.coderule.CodeRuleEntity` |
| `unit` | 计量单位表 | `module.system.unit.UnitEntity` |
| `app_version` | 应用版本表 | `module.system.appversion.AppVersionEntity` |

### 2. 资产管理表 (Asset)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `device` | 设备表 | `module.asset.device.DeviceEntity` |
| `material` | 物料表 | `module.asset.material.MaterialEntity` |
| `contract` | 合同表 | `module.asset.contract.ContractEntity` |
| `contract_document` | 合同文档表 | `module.asset.contract.document.ContractDocumentEntity` |
| `participant` | 合同参与者表 | `module.asset.contract.participant.ParticipantEntity` |

### 3. 渠道管理表 (Channel)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `customer` | 客户表 | `module.channel.customer.CustomerEntity` |
| `supplier` | 供应商表 | `module.channel.supplier.SupplierEntity` |
| `sale_price` | 销售价格表 | `module.channel.saleprice.SalePriceEntity` |
| `purchase_price` | 采购价格表 | `module.channel.purchaseprice.PurchasePriceEntity` |
| `purchase` | 采购单表 | `module.channel.purchase.PurchaseEntity` |
| `purchase_detail` | 采购单明细表 | `module.channel.purchase.detail.PurchaseDetailEntity` |
| `sale` | 销售单表 | `module.channel.sale.SaleEntity` |
| `sale_detail` | 销售单明细表 | `module.channel.sale.detail.SaleDetailEntity` |
| `delivery` | 发货单表 | `module.channel.delivery.DeliveryEntity` |
| `delivery_detail` | 发货单明细表 | `module.channel.delivery.detail.DeliveryDetailEntity` |
| `receipt` | 收货单表 | `module.channel.receipt.ReceiptEntity` |
| `receipt_detail` | 收货单明细表 | `module.channel.receipt.detail.ReceiptDetailEntity` |

### 4. 工厂管理表 (Factory)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `storage` | 仓库表 | `module.factory.storage.StorageEntity` |
| `structure` | 组织结构表 | `module.factory.structure.StructureEntity` |
| `inventory` | 库存表 | `module.factory.inventory.InventoryEntity` |
| `inventory_detail` | 库存明细表 | `module.factory.inventory.detail.InventoryDetailEntity` |
| `stock_in` | 入库单表 | `module.factory.stockin.StockInEntity` |
| `stock_in_detail` | 入库单明细表 | `module.factory.stockin.detail.StockInDetailEntity` |
| `stock_out` | 出库单表 | `module.factory.stockout.StockOutEntity` |
| `stock_out_detail` | 出库单明细表 | `module.factory.stockout.detail.StockOutDetailEntity` |
| `stock_check` | 盘点单表 | `module.factory.stockcheck.StockCheckEntity` |
| `stock_check_detail` | 盘点单明细表 | `module.factory.stockcheck.detail.StockCheckDetailEntity` |

### 5. MES 制造执行表 (MES)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `operation` | 工序表 | `module.mes.operation.OperationEntity` |
| `bom` | BOM 表 | `module.mes.bom.BomEntity` |
| `bom_detail` | BOM 明细表 | `module.mes.bom.detail.BomDetailEntity` |
| `routing` | 工艺路线表 | `module.mes.routing.RoutingEntity` |
| `routing_operation` | 工艺路线工序表 | `module.mes.routing.operation.RoutingOperationEntity` |
| `work_order` | 工单表 | `module.mes.workorder.WorkOrderEntity` |
| `work_order_detail` | 工单明细表 | `module.mes.workorder.detail.WorkOrderDetailEntity` |
| `work_report` | 报工表 | `module.mes.workreport.WorkReportEntity` |
| `work_report_detail` | 报工明细表 | `module.mes.workreport.detail.WorkReportDetailEntity` |

### 6. 物联网表 (IoT)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `parameter` | 参数表 | `module.iot.parameter.ParameterEntity` |
| `device_parameter` | 设备参数关联表 | `module.iot.deviceparameter.DeviceParameterEntity` |
| `report` | 上报数据表 | `module.iot.report.ReportEntity` |

### 7. 质量管理表 (QMS)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `quality_check` | 质检单表 | `module.qms.qualitycheck.QualityCheckEntity` |
| `quality_check_detail` | 质检单明细表 | `module.qms.qualitycheck.detail.QualityCheckDetailEntity` |

### 8. 聊天通讯表 (Chat)

| 表名 | 说明 | 实体类路径 |
|------|------|-----------|
| `room` | 聊天室表 | `module.chat.room.RoomEntity` |
| `message` | 消息表 | `module.chat.message.MessageEntity` |

---

## 🔍 核心表结构详解

### user - 用户表

```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `nickname` varchar(255) DEFAULT '' COMMENT '昵称',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像',
  `real_name` varchar(255) DEFAULT '' COMMENT '真实姓名',
  `id_card` varchar(255) DEFAULT '' COMMENT '身份证号',
  `email` varchar(255) DEFAULT '' COMMENT '邮箱',
  `phone` varchar(255) DEFAULT '' COMMENT '手机号',
  `password` varchar(255) DEFAULT '' COMMENT '密码',
  `salt` varchar(255) DEFAULT '' COMMENT '密码盐',
  `gender` int unsigned DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_email` (`email`),
  UNIQUE KEY `UK_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
```

**关联关系**:
- 多对多: `department` (通过中间表 `user_department`)
- 多对多: `role` (通过中间表 `user_role`)

---

### material - 物料表

```sql
CREATE TABLE `material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(255) DEFAULT '' COMMENT '物料编码',
  `name` varchar(255) DEFAULT '' COMMENT '物料名称',
  `spc` varchar(255) DEFAULT '' COMMENT '规格型号',
  `material_type` int unsigned DEFAULT 1 COMMENT '物料类型 1-自产品 2-外购品',
  `unit_id` bigint DEFAULT NULL COMMENT '计量单位ID',
  `purchase_price` double DEFAULT 0 COMMENT '采购价格',
  `sale_price` double DEFAULT 0 COMMENT '销售价格',
  `remark` text COMMENT '备注',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_code` (`code`),
  KEY `FK_unit` (`unit_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料';
```

**关联关系**:
- 多对一: `unit` (计量单位)

---

### device - 设备表

```sql
CREATE TABLE `device` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(255) DEFAULT '' COMMENT '设备编码',
  `name` varchar(255) DEFAULT '' COMMENT '设备名称',
  `spc` varchar(255) DEFAULT '' COMMENT '规格型号',
  `status` int unsigned DEFAULT 0 COMMENT '设备状态',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备';
```

---

### storage - 仓库表

```sql
CREATE TABLE `storage` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(255) DEFAULT '' COMMENT '仓库编码',
  `name` varchar(255) DEFAULT '' COMMENT '仓库名称',
  `parent_id` bigint DEFAULT NULL COMMENT '父级仓库ID',
  `remark` text COMMENT '备注',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  KEY `FK_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仓库';
```

**关联关系**:
- 自关联: `parent_id` (父级仓库)

---

### work_order - 工单表

```sql
CREATE TABLE `work_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` varchar(255) DEFAULT '' COMMENT '工单编码',
  `material_id` bigint DEFAULT NULL COMMENT '生产物料ID',
  `quantity` double DEFAULT 0 COMMENT '生产数量',
  `routing_id` bigint DEFAULT NULL COMMENT '工艺路线ID',
  `status` int unsigned DEFAULT 1 COMMENT '工单状态',
  `plan_start_time` bigint DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_time` bigint DEFAULT NULL COMMENT '计划结束时间',
  `actual_start_time` bigint DEFAULT NULL COMMENT '实际开始时间',
  `actual_end_time` bigint DEFAULT NULL COMMENT '实际结束时间',
  `remark` text COMMENT '备注',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_code` (`code`),
  KEY `FK_material` (`material_id`),
  KEY `FK_routing` (`routing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单';
```

**关联关系**:
- 多对一: `material` (生产物料)
- 多对一: `routing` (工艺路线)
- 一对多: `work_order_detail` (工单明细)

---

## 🔗 通用字段说明

所有实体表都继承自 `BaseEntity`，包含以下通用字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | bigint | 主键ID (自增) |
| `create_time` | bigint | 创建时间 (时间戳毫秒) |
| `update_time` | bigint | 更新时间 (时间戳毫秒) |
| `is_disabled` | tinyint(1) | 是否禁用 (0-否 1-是) |

---

## 📊 初始化测试数据

首次启动后，系统会自动创建以下测试数据：

### 用户数据
| 昵称 | 手机 | 邮箱 | 密码 |
|------|------|------|------|
| 凌小云 | 17666666666 | admin@hamm.cn | Aa123456 |
| 张三 | 13888888888 | admin@hamm.com | Aa123456 |

### 组织数据
- 部门: 生产部、材料部

### 资产数据
- 设备: 设备1 (Simulator001)、设备2 (Simulator002)
- 物料: MacBook Pro M4 Max (32G-1TB)
- 单位: 台

### 渠道数据
- 客户: 腾讯科技
- 供应商: Apple 中国
- 销售价格: ¥29,999
- 采购价格: ¥28,000

### 工厂数据
- 仓库: 东部大仓 (东部1仓、东部2仓)、西部大仓 (西部1仓、西部2仓)
- 组织结构: 笔记本电脑产线 (工位1、工位2)

### MES 数据
- 工序: 键盘安装、屏幕贴膜、系统安装
- BOM: 笔记本电脑清单、键盘安装清单、屏幕贴膜清单
- 工艺路线: 笔记本安装

---

## 🛠️ 如何查看表结构

### 方式 1: MySQL 命令行

```bash
# 连接数据库
mysql -u root -proot kubo

# 查看所有表
SHOW TABLES;

# 查看表结构
DESC user;

# 查看建表语句
SHOW CREATE TABLE user;

# 查看表数据量
SELECT
  TABLE_NAME as '表名',
  TABLE_ROWS as '行数',
  TABLE_COMMENT as '说明'
FROM
  information_schema.TABLES
WHERE
  TABLE_SCHEMA = 'kubo'
ORDER BY
  TABLE_ROWS DESC;
```

### 方式 2: 数据库管理工具

推荐使用:
- **Navicat** - 功能强大的商业工具
- **DBeaver** - 免费开源的通用工具
- **MySQL Workbench** - MySQL 官方工具
- **PhpMyAdmin** - Web 版管理工具

### 方式 3: 查看后端日志

需要看 SQL 时打开 MyBatis 的日志（`logging.level` 里给对应的 Mapper 包名开 `DEBUG`）：
J4 之后没有 Hibernate，也就不再有 `spring.jpa.show-sql`。

---

## 📝 表结构变更说明

### 如何修改表结构？

1. 改实体（MyBatis-Plus 口径：`@TableName` / `@TableField`；唯一列用 `@Unique`）；
2. 改 `infra/init-sql/init.sql` 里那张表的 `CREATE TABLE`（新列 / 索引 / 唯一键一并写）；
3. 存量库要补数据就写进 `init_data.sql`（幂等）；
4. 跑 `pnpm check`，推送后看 CI 的 `migrations` job（真实 MySQL 8 空库连跑两遍）。

### 示例: 给 user 表添加新字段

```java
// UserEntity.java
@TableField("wechat")
private String wechat;
```

```sql
-- infra/init-sql/init.sql：user 表里补上这一列（幂等脚本，直接改这一行）
`wechat` varchar(255) NOT NULL DEFAULT '' COMMENT '微信号',
```

---

## ⚠️ 注意事项

1. **没有任何自动改表机制**：应用不建表、不改表，也没有 `ddl-auto`（J4 起 JPA 整体退场）——
   忘了改 `init.sql` 不会在启动期报错，而是在用到那一列时报 500，所以 `pnpm ddl:check` 必须绿。
2. 修改字段类型/删列需要迁移存量数据时：备份 → 改 `init.sql` → 在 `init_data.sql` 里写幂等回填。
3. **先建库再启动**：`mysql < infra/init-sql/init.sql` + `init_data.sql`（compose 首次起库会自动跑，见 `docker-compose.yml` 的 `/docker-entrypoint-initdb.d` 挂载）。
4. 全仓**只有这两个 SQL 脚本**（`pnpm migration:check` 守着）：不再新增迁移文件、不再引入迁移框架。

---

**更多信息**: 查看 [WHY_NO_SQL.md](./WHY_NO_SQL.md)（为什么只有两个 SQL 文件）与
[SPMS-Server/docs/DATABASE_MIGRATION_STRATEGY.md](./SPMS-Server/docs/DATABASE_MIGRATION_STRATEGY.md)（脚本策略与判据）
