<p align="center">
<img src="https://raw.githubusercontent.com/s-pms/SPMS-Web/main/src/assets/img/logo.svg" width="240"/>
</p>

<p align="center">
  <img src="https://svg.hamm.cn?key=Lang&value=Java17&bg=green"/>
  <img src="https://svg.hamm.cn?key=Base&value=SpringBoot3"/>
  <img src="https://svg.hamm.cn?key=Data&value=MySQL8"/>
  <img src="https://svg.hamm.cn?key=ORM.&value=MyBatis-Plus"/>
</p>

## Kubo · 智方 (后端项目)

**Kubo** 智能生产管理系统后端项目，基于 **Java 17 + Spring Boot 3 + MySQL 8 + MyBatis-Plus 3.5.9** 开发。
原 **AirPower4J** 框架层已于 2026-09-18 整模块搬入本仓（`cn.hamm.spms.core` / `cn.hamm.spms.framework`，MIT，见各文件头），**不再依赖任何外部框架包**。

当前落地的模块：**WMS 仓储管理**（入库/出库/移库/库存）、**渠道管理**（客户/供应商/采购/销售）、
**资产管理**（物料/合同）、**工厂建模**（仓库/库位）、**人事组织**、**系统管理**与**数据权限**，
实现从采购、仓储到销售全流程的信息化管理。

> 早期版本里的 MES / QMS / IoT / 财务模块已在 2026-09 的整域瘦身中拆除；
> 定位取舍见 `docs/项目重定位·砍什么留什么（物流仓储与半导体方向）.md`，执行记录见 `docs/瘦身执行记录·第三批.md`。

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 6.0+
- IntelliJ IDEA (推荐)

### 下载源码

```shell
git clone https://github.com/yuli755/Kubo.git
cd Kubo/SPMS-Server          # 后端在本仓的 SPMS-Server/ 子目录，前端在仓库根目录
```

### 配置数据库

连接默认值已入库（`application.yml` / `application-local.yml`，本地 `root/root@localhost:3306/kubo`），
**直接编辑 `application-local.yml` 即可**（本地 profile 默认启用；原 `application-template.yml` 模板文件已不存在，
`cp` 那一步作废）。要改的连接长这样：

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

表结构的唯一真值是 **`infra/init-sql/init.sql`**（49 张表，幂等 `CREATE TABLE IF NOT EXISTS`），种子数据在 `init_data.sql`：

- 全新环境：`docker compose up -d mysql`（脚本挂进 `/docker-entrypoint-initdb.d`，首次启动自动执行）；
- 已有库 / 手动：`mysql -u root -p kubo < infra/init-sql/init.sql && mysql -u root -p kubo < infra/init-sql/init_data.sql`；
- **应用不建表、不改表**：`spring.jpa`（含 `ddl-auto`）随 JPA 于 2026-09-18 整体退场（J4），
  "实体 ↔ 库表"是否一致改由 `pnpm ddl:check` 与 `schemaContract.spec.ts` 在 CI 里判。
- 改结构的动作：改实体 + 改 `init.sql`（+ `init_data.sql` 补数据），两处必须同批。

## 依赖框架

原 **AirPower4J** 框架层已整模块搬入本仓（`cn.hamm.spms.core` / `cn.hamm.spms.framework`，共 84 类），
`pom.xml` 里没有任何 `cn.hamm` 依赖（`pnpm pom:check` + `pnpm airpower:backend -- --check` 守着），
因此不再需要引入外部框架包。上游项目仍可参考：
- [Github](https://github.com/AirPowerTeam/AirPower4J)（MIT，本仓搬入代码的来源）

## AI 辅助开发

如果使用 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等），请让 AI 参考 **[AGENT.md](./AGENT.md)** 文档，其中详细约定了项目的开发规范、目录结构、编码风格等。

## 开源协议

**Kubo** 所有代码均在 **MIT** 开源协议规范下免费提供，你可以放心使用。

如有定制需求，欢迎联系我们：admin#hamm.cn
