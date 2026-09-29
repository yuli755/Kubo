<p align="center">
<img src="https://raw.githubusercontent.com/s-pms/SPMS-Web/main/src/assets/img/logo.svg" width="240"/>
</p>

<p align="center">
  <img src="https://svg.hamm.cn?key=Lang&value=Java17&bg=green"/>
  <img src="https://svg.hamm.cn?key=Base&value=SpringBoot3"/>
  <img src="https://svg.hamm.cn?key=Data&value=MySQL8"/>
  <img src="https://svg.hamm.cn?key=ORM.&value=JPA"/>
</p>

## Kubo · 智方 (后端项目)

**Kubo** 智能生产管理系统后端项目，基于 **Java 17 + Spring Boot 3.4.4 + MySQL 8 + JPA** 开发，
底座完全自持（AirPower 依赖 0 残留，构建由官方 `spring-boot-dependencies` BOM 管理）。

当前已落地 6 个业务模块，覆盖从采购、仓储到销售全流程的信息化管理：

| 模块 | 内容 |
| --- | --- |
| `wms` | 入库、出库、移库、库存 |
| `channel` | 采购单、销售单、客户、供应商、采购/销售价格 |
| `factory` | 仓库、产线/工位（树形结构） |
| `asset` | 合同（参与方/附件）、物料 |
| `personnel` | 用户、角色、部门 |
| `system` | 菜单、权限目录、数据权限（行级+列级）、配置、编码规则、通知、操作日志 |

> MES / ERP / QMS / IoT / 聊天室 / 设备台账等域已整体退场：菜单与权限身份由
> `V12__retired_module_catalog_convergence.sql` 收敛，数据权限目录里的残留资源与策略由
> `V13__retired_resource_policy_convergence.sql` 收敛。

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- IntelliJ IDEA (推荐)

### 下载源码

```shell
git clone https://github.com/s-pms/SPMS-Server.git
cd SPMS-Server
```

### 配置数据库

复制配置模板：

```shell
cp src/main/resources/application-template.yml src/main/resources/application-local.yml
```

修改 `application-local.yml` 中的数据库连接：

```yaml
spring:
  datasource:
    url: 'jdbc:mysql://localhost:3306/kubo?allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=utf8&useSSL=false'
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 启动项目

**方式 1: Maven 命令行**

```shell
./mvnw spring-boot:run
```

**方式 2: IDEA 运行**

1. 用 IDEA 打开项目
2. 等待 Maven 依赖加载完成
3. 在 Run Configuration 中设置 Active profiles 为 `local`
4. 运行 `Application.java`

### 数据库初始化

项目使用 **JPA** 自动管理数据库表结构：

- 开发环境：`ddl-auto: create-drop` 自动创建表结构
- 生产环境：导出测试完毕的数据库，设置 `ddl-auto: validate`，然后导入数据

## 依赖框架

本项目依赖 **AirPower4J** 基础框架：
- [Github](https://github.com/AirPowerTeam/AirPower4J)
- [Gitee](https://gitee.com/air-power/AirPower4J)

## AI 辅助开发

如果使用 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等），请让 AI 参考 **[AGENT.md](./AGENT.md)** 文档，其中详细约定了项目的开发规范、目录结构、编码风格等。

## 开源协议

**Kubo** 所有代码均在 **MIT** 开源协议规范下免费提供，你可以放心使用。

如有定制需求，欢迎联系我们：admin#hamm.cn
