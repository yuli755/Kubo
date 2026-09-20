# Kubo Server 开发规范

本文档约定了 **Kubo Server** 项目的开发规范，供 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等）参考，保证生成的代码与项目现有风格一致。

## 后端退场的当前边界（2026-09-20）

- **前端清零不等于后端清零**：后端仍保留 `airpower-curd:6.4.3`，真实足迹用 `pnpm airpower:backend` 查询。
- B2a 已迁走 8 个工具类和 `ITree` 的直接用法。新增工具在 `cn.hamm.spms.framework.util`，
  **不得 import airpower**，也不要通过改包名/继承旧工具伪装自持。
- 使用 `DecimalMath`（数量/价格）、`RandomValues`（盐/验证码/Cookie，SecureRandom）、
  `FilePaths`（本地上传）、`EnumValues`（显式选项字段）、`Trees` + `TreeNode`（树处理）。
  应用错误边界是 `common.dictionary.EnumDictionaries` / `common.tree.TreeDeletionGuard`。
  日期/时长直接用 `java.time`；字符串判空直接用 Spring `StringUtils`。
- B2b：Mapper CSV 统一经 `common.export.EntityCsvExport` → 自持 `CsvWriter` / `CsvText`。
  `CollectionUtil` / `ReflectUtil` 的**直接引用**已清零，反射使用 `BeanFields`；
  `common.metadata.LegacyEntityMetadata` 仍显式依赖三个旧注解，不得把它当成已完成的 B3。
  CSV 字段范围、取数权限、请求线程脱敏、JPA 回退及 `fileCode → queryExport` 协议不得因换工具而改变。
- B2c：UserTokenType、CodeRuleParam、FileCategory、FileUploadPlatform、PermissionType 已是普通 enum，
  不实现旧接口，也不新造同名接口包装。构造器/getter 显式声明，键值、标签、上传白名单与保护标记不变。
  独立源码清单 `infra/scripts/backend-independent-sources.mjs` 同时供 javac 与零回潮门禁使用。
  项目里的 TaskUtil 直接用法已清零；创建后置钩子仍同步且仅捕获该钩子的 Exception。
- B3a：Customer/Supplier/User 的电话字段改用 Jakarta `@Pattern` + `PhoneRules`，包括 User 原通配导入隐藏的第三处。
  所有 AirPower 通配导入现已禁止。Errors/CustomError/ConfigFlag 移除无用字典标记，异常接口/配置类型仍保留。
  不把“手机号”字段擅自收窄为只收手机：旧规则还允许座机；null/空串、分组与文案均需保持。
- 仍被 `@Dictionary` 约束的 `IDictionary`、`IPermission`、`Json`、异常运行时和 CRUD 基类仍是旧框架契约，
  不能看到工具替换就直接删依赖。以下涉及它们的示例仅描述**存量**，不是鼓励新增耦合。
- 验证分层：`pnpm java:unit` 是 **JDK 17 真实编译 16 个生产源码（10 工具/规则 + 5 业务枚举 + 1 常量）/91 契约 + 全量源文件 AST**；
  `pnpm java:check` 只是文本检查；整仓仍需 `./mvnw -B -s settings.xml compile` 和 `test`。
  AST 也不是完整类型检查。B2a–B3a 接线测试尚未完整验收，最新状态见 `docs/后端B3a·电话约束与通配清理.md`。
  本轮已修复 B2a 漏掉的 List import，并拦下历史重复注解；不能因静态门禁绿就宣称整仓 Java 可编译。

## 技术栈

| 技术              | 版本              | 说明     |
|-----------------|-----------------|--------|
| Java            | 17              | 开发语言   |
| Spring Boot     | 3.x             | 应用框架   |
| Maven           | 3.9+            | 依赖管理   |
| JPA + Hibernate | Spring Data JPA | ORM框架  |
| MySQL           | 8.x             | 数据库    |
| Redis           | -               | 缓存     |
| AirPower4J      | airpower-curd 6.4.3 | 待退场的存量 CRUD / API 契约，不得回潮 |
| Lombok          | -               | 代码简化   |

## 项目整体结构

```
SPMS-Server/
├── src/main/java/cn/hamm/spms/
│   ├── base/               # 项目基础类继承
│   ├── common/             # 公共工具类和常量
│   ├── module/             # 业务模块（按模块划分）
│   │   ├── asset/          # 资产模块
│   │   ├── channel/        # 渠道模块
│   │   ├── chat/           # 聊天模块
│   │   ├── factory/        # 工厂模块
│   │   ├── iot/            # 物联网模块
│   │   ├── mcp/            # MCP模块
│   │   ├── mes/            # MES模块
│   │   ├── open/           # 开放接口模块
│   │   ├── personnel/      # 人事模块
│   │   ├── system/         # 系统模块
│   │   ├── wechat/         # 微信模块
│   │   └── wms/            # WMS仓库管理模块
│   ├── Application.java    # 启动类
│   ├── DevDataInitRunner.java # 开发数据初始化
│   └── WebConfig.java      # Web配置
└── src/main/resources/
    ├── application.yml         # 主配置文件
    ├── application-template.yml # 配置模板
    ├── application-production.yml # 生产配置
    └── logback-spring.xml     # 日志配置
```

## 模块内部结构规范

每个业务模块下，按照业务功能划分分包，每个业务功能包包含以下文件：

```
module/
└── function/               # 业务功能包
    ├── enums/              # 枚举类目录（可选）
    │   └── XXXType.java    # 枚举定义
    ├── XXXEntity.java      # 实体类
    ├── XXXRepository.java  # 数据访问层接口
    ├── XXXService.java     # 业务逻辑层
    └── XXXController.java  # 接口控制层
```

### 命名规范

| 层次         | 命名规范                  | 示例                            |
|------------|-----------------------|-------------------------------|
| 包名         | 全小写，使用英文单词            | `inventory`、`input`、`output`  |
| 实体类        | 大驼峰命名 + Entity后缀      | `InventoryEntity`             |
| Repository | 大驼峰命名 + Repository后缀  | `InventoryRepository`         |
| Service    | 大驼峰命名 + Service后缀     | `InventoryService`            |
| Controller | 大驼峰命名 + Controller后缀  | `InventoryController`         |
| 枚举类        | 大驼峰命名 + Type/Status后缀 | `InventoryType`               |
| 方法名        | 小驼峰命名，见名知意            | `getByMaterialIdAndStorageId` |
| 变量名        | 小驼峰命名                 | `materialId`、`storageId`      |
| 常量         | 全大写下划线分隔              | `DEFAULT_PAGE_SIZE`           |

## 编码风格规范

### 1. 文件头注释

每个文件必须包含类说明和作者：

```java
/**
 * <h1>类描述</h1>
 *
 * @author Hamm.cn
 */
```

### 2. 类注解顺序（实体类示例）

```java

@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "table_name")
@Description("描述")
public class XxxEntity extends BaseEntity<XxxEntity> {
    // ...
}
```

### 3. Controller 注解顺序

```java

@Api("api_path")
@Description("功能描述")
@Extends({GetDetail, GetPage})
public class XxxController extends BaseController<XxxEntity, XxxService, XxxRepository> {
}
```

一般情况下使用 `@Extends` 继承 CURD 接口即可，不需要重复写方法。常用的 CURD 接口：

- `GetDetail` - 根据ID查询详情
- `GetPage` - 分页查询
- `Add` - 新增
- `Update` - 更新
- `Delete` - 删除

### 4. Service 注解

```java

@Service
public class XxxService extends BaseService<XxxEntity, XxxRepository> {
    // ...
}
```

### 5. Repository 注解

```java

@Repository
public interface XxxRepository extends BaseRepository<XxxEntity> {
    // 自定义查询方法
}
```

### 6. 字段定义

- 使用 `@Description` 注解描述每个字段
- 数据库字段使用 `@Column` 定义 `columnDefinition`，包含类型和默认值注释
- 外键使用 `@ManyToOne`，指定 `fetch = EAGER`

示例：

```java

@Description("物料信息")
@ManyToOne(fetch = EAGER)
private MaterialEntity material;

@Description("库存数量")
@Column(columnDefinition = "double(20, 6) UNSIGNED default 0 comment '库存数量'")
private Double quantity;

@Description("存储类型")
@Column(columnDefinition = "int UNSIGNED default 1 comment '存储类型'")
@Dictionary(value = InventoryType.class, groups = {WhenAdd.class, WhenUpdate.class})
private Integer type;
```

### 7. 枚举使用

- 普通选项枚举只需稳定键值/getter，使用 `EnumValues` 查找和投影，**不要默认实现旧 IDictionary**。
- 下方是尚未迁移的实体校验示例：旧 `@Dictionary` 的 value 仍要求 IDictionary，不能单删接口让校验失效。
- 已迁走的五个普通枚举不再用于旧 `@Dictionary`。需要迁移字段校验时连同约束与行为测试一起做。
- 持久化字段继续使用 Integer 键值，不用 ordinal。

示例：

```java

@Description("存储类型")
@Column(columnDefinition = "int UNSIGNED default 1 comment '存储类型'")
@Dictionary(value = InventoryType.class, groups = {WhenAdd.class, WhenUpdate.class})
private Integer type;
```

### 8. 方法编写规范

- 方法必须添加 JavaDoc 注释，说明方法用途和参数
- 参数类型使用包装类（`Long`、`Integer`、`Double`、`Boolean`）而非基本类型
- 空值判断使用 `Objects.isNull()` 或 `Objects.nonNull()`
- 使用 `@NotNull` 注解标记不可为空的参数
- 条件分支使用 `switch` 表达式时，使用 `->` 语法，`default` 留空即可

### 9. 异常处理

使用本项目 `cn.hamm.spms.common.exception.Errors` 抛出通用错误；业务码使用同包 `CustomError`。
注意：它们目前仍借用旧异常运行时，不能宣称整个异常体系已自持：

```java
Errors.FORBIDDEN_EDIT.when(subtract < 0, "库存数量不足");
```

### 10. 依赖注入

使用 `@Autowired` 注解注入依赖，放在字段上：

```java

@Autowired
private StorageService storageService;
```

### 11. 代码格式化

- 使用 4 空格缩进（不使用 Tab）
- 大括号不换行，跟随语句
- 每个方法之间保留一个空行
- 导入包不使用通配符，每个类单独导入；已退场的工具导入会被零回潮门禁拦截。

> **注（2026-09-18）**：`cn.hamm.airpower` 是**待退场**的框架（前端对应的 `@airpower/web` 已删净）。
> 后端退场分批方案见 `docs/后端airpower退场·可行性评估与分批建议.md`；
> 门禁 `pnpm airpower:backend -- --check` 会拦住"足迹变大"——新增用法前先看那份文档，
> 能自建的通用件优先自建（`pnpm airpower:backend` 会打印按用量排序的"要自建什么"清单）。

## 继承体系

所有类都继承项目提供的基类：

| 层次         | 基类                                                                                                  |
|------------|-----------------------------------------------------------------------------------------------------|
| Entity     | `BaseEntity<E extends BaseEntity<E>>`                                                               |
| Repository | `BaseRepository<E extends BaseEntity<E>>`                                                           |
| Service    | `BaseService<E extends BaseEntity<E>, R extends BaseRepository<E>>`                                 |
| Controller | `BaseController<E extends BaseEntity<E>, S extends BaseService<E, R>, R extends BaseRepository<E>>` |

## 数据库规范

- 表名使用下划线分隔小写命名
- 所有表继承基类的 `id`、`createTime`、`updateTime` 字段
- 使用 `unsigned` 非负整数
- 字符串字段必须指定长度
- 小数使用 `double(20, 6)` 格式
- 添加 `comment` 描述字段用途
- 使用 `bit(1)` 存储布尔类型，默认值 `0`

## 存量 CRUD 契约与自持工具的使用边界

### 1. CURD 扩展

Controller 使用 `@Extends` 注解继承需要暴露的 CURD 接口：

```java
@Extends({GetDetail, GetPage, Add, Update, Delete})
```

### 2. 查询扩展

Service 中重写 `beforeGetPage`、`beforeCreatePredicate`、`addSearchPredicate` 方法添加自定义查询条件。

### 3. 字典转换

使用 `EnumDictionaries.require(EnumClass.class, value, EnumClass::getKey, EnumClass::getLabel)` 获取枚举实例。
选项接口使用 `EnumValues.options`，显式声明 `key` / `label` / 附加字段；不要再用 lambda 反射猜字段名。

### 4. 树形结构

使用 `Trees.getChildrenIdList(id, supplier)` 获取后代 ID（不含起点）；组树用 `Trees.buildTreeList`。
节点实现自有 `TreeNode`，不要再引入 `ITree`。保留 `IPermission` 仅为旧权限扫描兼容。

### 5. 数字计算

使用 `DecimalMath.add()`、`subtract()`、`multiply()` 做双操作数十进制运算，不复制无人使用的框架方法。

### 6. 更新锁

使用 `updateWithLock(id, consumer)` 进行并发安全的更新。

## 导入包顺序

1. `java.*`
2. `jakarta.*`
3. `org.*`
4. `springframework.*`
5. `cn.hamm.airpower.*`
6. `cn.hamm.spms.*`
7. `lombok.*`

**导入包不使用通配符，每个类单独导入。**

## 依赖新增原则

- 新代码优先使用 JDK / Spring 与自持底座。未迁移的 CRUD 契约通过项目 `base` 层复用，禁止扩大 AirPower 足迹。
- 不随意新增第三方依赖，确实需要时先评审
- 新增依赖必须指定版本

## 示例代码参考

完整的示例可以参考：

- `cn.hamm.spms.module.wms.inventory` 包下的代码
- `cn.hamm.spms.module.factory.storage` 包下的代码
