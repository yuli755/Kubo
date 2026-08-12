# 为什么没有 SQL 文件？

## 💡 Kubo 使用 JPA/Hibernate ORM 自动建表

### 原理说明

这个项目使用 **Spring Boot JPA + Hibernate**，采用的是 **ORM (对象关系映射)** 技术：

```
Java 实体类 (Entity)  →  Hibernate  →  自动生成 SQL  →  创建数据库表
```

**不需要手动编写 SQL 文件**，表结构由 Java 代码定义！

---

## 📋 实际例子

### Java 实体类定义

```java
// UserEntity.java
@Entity
@Table(name = "user")
public class UserEntity extends BaseEntity {
    
    @Column(columnDefinition = "varchar(255) default '' comment '昵称'")
    @NotBlank(message = "昵称不能为空")
    private String nickname;
    
    @Column(columnDefinition = "varchar(255) default '' comment '邮箱'", unique = true)
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Column(columnDefinition = "varchar(255) default '' comment '手机号'", unique = true)
    private String phone;
    
    @Column(columnDefinition = "varchar(255) default '' comment '密码'")
    @JsonProperty(access = WRITE_ONLY)
    private String password;
    
    // ... 更多字段
}
```

### Hibernate 自动生成的 SQL

首次启动时，Hibernate 会自动生成并执行：

```sql
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nickname` varchar(255) DEFAULT '' COMMENT '昵称',
  `email` varchar(255) DEFAULT '' COMMENT '邮箱',
  `phone` varchar(255) DEFAULT '' COMMENT '手机号',
  `password` varchar(255) DEFAULT '' COMMENT '密码',
  `avatar` varchar(255) DEFAULT '' COMMENT '头像',
  `real_name` varchar(255) DEFAULT '' COMMENT '真实姓名',
  `id_card` varchar(255) DEFAULT '' COMMENT '身份证号',
  `gender` int unsigned DEFAULT 0 COMMENT '性别',
  `salt` varchar(255) DEFAULT '' COMMENT '密码盐',
  `create_time` bigint DEFAULT NULL COMMENT '创建时间',
  `update_time` bigint DEFAULT NULL COMMENT '更新时间',
  `is_disabled` tinyint(1) DEFAULT 0 COMMENT '是否禁用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_email` (`email`),
  UNIQUE KEY `UK_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';
```

---

## ⚙️ 自动建表配置

在 `application-local.yml` 中：

```yaml
spring:
  jpa:
    show-sql: true              # 显示生成的 SQL 语句
    hibernate:
      ddl-auto: update          # 自动更新表结构
```

### ddl-auto 选项说明

| 选项 | 行为 | 适用场景 |
|------|------|----------|
| `create` | 每次启动删除并重建表 | ⚠️ 会丢失数据 |
| `create-drop` | 启动时创建，关闭时删除 | 测试环境 |
| **`update`** | 自动更新表结构，保留数据 | ✅ **开发环境** |
| `validate` | 只验证，不修改表 | 生产环境 |
| `none` | 不做任何操作 | 手动管理 |

**当前使用**: `update` - 首次启动自动建表，后续启动自动更新字段

---

## 🔍 如何查看生成的 SQL？

### 方式 1: 查看后端启动日志

启动后端时，控制台会输出 Hibernate 生成的 SQL 语句：

```
Hibernate: create table user (...)
Hibernate: create table department (...)
Hibernate: create table device (...)
...
```

### 方式 2: 连接数据库查看

```bash
# 连接 MySQL
mysql -u root -proot kubo

# 查看所有表
SHOW TABLES;

# 查看表结构
DESC user;

# 查看建表语句
SHOW CREATE TABLE user;
```

### 方式 3: 使用数据库管理工具

- **Navicat**
- **DBeaver**
- **MySQL Workbench**
- **PhpMyAdmin**

---

## 📊 Kubo 的所有数据表

项目启动后会自动创建以下数据表：

### 核心系统表
- `user` - 用户表
- `department` - 部门表
- `role` - 角色表
- `permission` - 权限表
- `menu` - 菜单表
- `config` - 系统配置表
- `code_rule` - 编码规则表
- `unit` - 计量单位表

### 资产管理
- `device` - 设备表
- `material` - 物料表
- `contract` - 合同表
- `contract_document` - 合同文档表
- `participant` - 合同参与者表

### 渠道管理
- `customer` - 客户表
- `supplier` - 供应商表
- `sale_price` - 销售价格表
- `purchase_price` - 采购价格表
- `purchase` - 采购单表
- `purchase_detail` - 采购单明细表
- `sale` - 销售单表
- `sale_detail` - 销售单明细表

### 工厂管理
- `storage` - 仓库表
- `structure` - 组织结构表
- `inventory` - 库存表
- `stock_in` - 入库单表
- `stock_in_detail` - 入库单明细表
- `stock_out` - 出库单表
- `stock_out_detail` - 出库单明细表

### MES 制造
- `operation` - 工序表
- `bom` - BOM 表
- `bom_detail` - BOM 明细表
- `routing` - 工艺路线表
- `routing_operation` - 工艺路线工序表
- `work_order` - 工单表
- `work_order_detail` - 工单明细表
- `work_report` - 报工表

### 物联网
- `parameter` - 参数表
- `device_parameter` - 设备参数关联表
- `report` - 上报数据表

### 其他
- `room` - 聊天室表
- `message` - 消息表

**总计约 40+ 张表**，全部由 Hibernate 自动创建！

---

## 🎯 数据初始化

表结构自动创建后，测试数据由 `DevDataInitRunner.java` 初始化：

```java
@Component
public class DevDataInitRunner implements CommandLineRunner {
    @Override
    public void run(String... args) {
        // 1. 初始化用户
        userService.add(new UserEntity()
            .setNickname("凌小云")
            .setPhone("17666666666")
            .setEmail("admin@hamm.cn")
            .setPassword(encodePassword("Aa123456", salt))
        );
        
        // 2. 初始化部门
        departmentService.add(new DepartmentEntity()
            .setName("生产部")
            .setCode("a1")
        );
        
        // 3. 初始化设备、物料、仓库...
        // ...
    }
}
```

---

## ✅ 为什么这样设计？

### 优势

1. **维护方便**: 修改 Java 实体类，表结构自动更新
2. **类型安全**: Java 类型检查，减少 SQL 错误
3. **跨数据库**: 同一份代码支持 MySQL、PostgreSQL、Oracle 等
4. **版本控制**: 表结构变更通过 Git 追踪 Java 代码
5. **开发效率**: 不需要手写 SQL，专注业务逻辑

### 劣势

1. **缺少文档**: 没有独立的 SQL 文件作为参考
2. **性能优化**: 复杂查询可能需要手写 SQL
3. **数据迁移**: 大版本升级需要谨慎处理

---

## 🛠️ 如果需要 SQL 文件怎么办？

### 方式 1: 导出当前数据库结构

```bash
# 仅导出表结构
mysqldump -u root -proot --no-data kubo > kubo_schema.sql

# 导出表结构和数据
mysqldump -u root -proot kubo > kubo_full.sql
```

### 方式 2: 使用 Hibernate Schema Export

在 `application-local.yml` 中临时启用：

```yaml
spring:
  jpa:
    properties:
      hibernate:
        hbm2ddl:
          schema-generation:
            script:
              create-target: schema.sql
```

启动一次后会生成 `schema.sql` 文件。

### 方式 3: 手动从数据库管理工具导出

使用 Navicat、DBeaver 等工具的"导出 SQL"功能。

---

## 📝 总结

**Kubo 项目没有 SQL 文件是正常的！**

- ✅ 表结构由 **Java 实体类** 定义
- ✅ Hibernate 在启动时 **自动创建表**
- ✅ 测试数据由 **DevDataInitRunner** 初始化
- ✅ 你只需要启动项目，一切自动完成

**想查看表结构**？
1. 启动后端，查看控制台输出的 SQL
2. 或连接 MySQL: `mysql -u root -proot kubo` 然后 `SHOW TABLES;`

---

**这就是现代 Spring Boot 项目的标准做法！** 🎉
