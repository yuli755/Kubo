# 后端 B7a · Cookie 传递模块退场（2026-09-21）

> **全后端还没有清理干净。Cookie 源码引用已归零、传递依赖已在 POM 排除；整仓编译/解析后依赖树仍待验收。**
> 不改 Java 17、JWT、Cookie 登录/退出行为、Redis、JPA 写路径、Mapper 读侧开关或数据库迁移。

**后续状态**：用户已确认优先替换主干契约，P1 错误响应/异常已落地，见 [P1 记录](后端主干迁移·P1错误契约接管.md)。本页保留 B7a 历史证据。

## 先核对用户提供的数字

本轮开始执行 `pnpm stack:project`，真文件结果为 **160/468 Java 文件、518 引用，Flyway V1–V10 共 10 个 SQL**。
这与“171/534、593、26 迁移”不是同一快照；之前棘轮已收紧到 518，并非仍在 603 预算内。
前端零引用与后端还在退场是两个不同状态，本轮没有宣称整个项目已清零。

## 这轮不是只改 import

1. 核对固定上游 `c07a6bd3f4d7`：airpower-curd 6.4.3 的 POM 直接传递 airpower-cookie；
   API/CRUD/Redis 生产调用和各自 AutoConfiguration.imports 没有 Cookie 类链接。
   Cookie 依赖的 exception/core 仍由 API 等路径提供，不借本批误删运行时。
2. 在项目 `airpower-curd` 依赖上增加精确的 `cn.hamm:airpower-cookie` exclusion。
3. 删除 Application 中 `exclude = ...cookie.Auto.class`，不再靠“引用旧 Auto 再禁用它”维持启动。
4. CookieHelper/CookieConfig 仍由项目注册，`spms.cookie` 前缀和既有默认值不变。
5. 整个旧 Cookie 包进入源码零回潮门禁；POM 缺少正确 exclusion、只写注释、排错坐标、重新直接依赖都失败。
   CI 还会在**非空的实际 Maven 依赖树**中检查旧 Cookie，不把声明当成已解析事实。

| 指标 | 本轮前 | 本轮后 |
| --- | --- | --- |
| 含 AirPower 引用的 Java 文件 | 160 / 468 | **159 / 470** |
| 符号 / 引用 | 44 / 518 | **43 / 517** |
| cookie 源码模块 | 1 | **0** |
| Maven 声明 | curd 传递 cookie | **curd 保留、cookie 明确排除** |

数字只少一个引用，是因为业务 Cookie 调用早已在上一批改指项目实现；本批解决的是剩余传递模块与启动耦合。
不能把“只少一个 import”误读为“只做了一次改名”，也不能把它夸成整个 AirPower 已退场。

## 真实 Spring 切片验收

已核对 Maven Central 发布校验和的正式 Spring 6.2.5、Boot 3.4.4、Servlet 6.0.0、Micrometer 1.14.5、
JetBrains annotations 26.0.2、Lombok 1.18.36，仅用于沙箱测试缓存，不加入 Git、不引入第三方仓库作为生产镜像。
SHA-256 清单在 `infra/scripts/backend-cookie-artifacts.mjs`；缺少依赖或字节不符时 runner 非零退出。

`CookieContracts` 实际启动 AnnotationConfigApplicationContext，运行 ConfigurationPropertiesBindingPostProcessor，
注册并注入**真实生产 CookieHelper/CookieConfig**，创建真实 Servlet Cookie；未加载任何 AirPower JAR。
14 项契约覆盖单例 Bean、默认值、全部配置覆盖、宽松属性名绑定、旧前缀不生效、退出 Cookie、SameSite、
实例隔离、真实字段注入、错误配置拒绝和 Servlet 的 Cookie 名校验。

- **14/14 通过**，UTF-8/US-ASCII 各跑一次同一组；JUnit 入口复用同一组断言。
- Cookie 契约日志里的错误配置绑定 WARNING 是负向用例的预期结果，不是启动主流程失败。
- 编译用 `--release 17 -Werror -Xlint:all,-processing`；仅关闭“不需要处理器认领的运行时注解”提示，
  类型/方法/其它 lint 错误仍失败；不是用 stub 模拟 Spring 或 Servlet。
- 原有 JDK **107 项**、Jackson **25 项**契约通过；`pnpm check` **58 文件 / 584 用例及构建通过**。
- **不等于整个 Application 启动成功**。本地 Maven 仍在 Wrapper 下载阶段 TLS 握手失败，未进入整仓编译；
  实际传递依赖树和数据库/Redis/JWT 端到端回归仍未执行。

## 盘点也必须读对文件

新的 `backend-pom.mjs` 共用于 stack:project、后端足迹、POM 门禁；分开报告 dependency、managed/BOM 和 exclusion。
不能看到 `<artifactId>airpower-cookie</artifactId>` 就误报 Cookie 仍是直接依赖，也不能把错误位置的排除当成 curd 路径已处理。
测试实际执行 stack:project 并与当前 Java/POM/迁移目录核对，防止报告脚本自身运行失败却继续抄旧数。
人读报告现在明确显示 **“后端状态：未完成（棘轮通过不等于退场完成）”**。

```bash
JAVA_HOME=/path/to/jdk-17 KUBO_COOKIE_JARS=/path/to/verified-cookie-jars pnpm java:cookie
pnpm stack:project
pnpm airpower:backend -- --check
pnpm check
# 仍须恢复构建通道后执行
cd SPMS-Server
./mvnw -B -s settings.xml compile
./mvnw -B -s settings.xml test
./mvnw -B -s settings.xml dependency:tree
```

剩余源码耦合仍有 **curd 241 / core 222 / api 41 / redis 13**，总计 517；airpower-curd 6.4.3 仍保留。
这些数字和完整验收缺口必须继续报告，不能用局部绿灯替代“清理干净”。
