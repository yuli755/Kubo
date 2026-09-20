# 后端 B3a · 电话约束与通配清理（2026-09-20）

> **本地独立验证通过，完整 Maven 编译/运行时接线仍待验收。**
> 本轮不是重写 JSON、异常处理器或鉴权；继续保留 airpower-curd 6.4.3 及其运行时。

## 本轮改动

- CustomerEntity、SupplierEntity、UserEntity 的 `Phone` 改为 Jakarta `Pattern`，共用自持 `PhoneRules` 常量。
  没有复制旧约束注解、没有新建代理验证器。用户字段保留密码重置/短信分组和「手机格式不正确」文案。
- **UserEntity 原来使用通配导入**，故之前“2 个 Phone 导入”不代表只有两个使用方。本次检查全部注解用法后迁了第三处。
  剩下的 Description/Desensitize/Dictionary/Meta 全部显式导入，保留脱敏和性别校验。
- 错误码 Errors/CustomError 和配置枚举 ConfigFlag 的 IDictionary 没有消费者，移除标记。
  保留 getter、错误码、配置值；IException/ServiceException/ConfigType 仍在，未宣称这些类型能脱离旧运行时独立编译。
- 全部 AirPower 通配导入清零并禁止回潮；Phone 纳入已退场符号门禁，三枚举的无用 IDictionary 标记单独禁止回潮。

## 兼容依据

对照 core **6.3.1** 的 Phone、PatternConstant、StringUtil.isEmpty 和 ValidateUtil（固定源快照 fe1691b2c978）：

- 客户/供应商的默认文案仍为「不是有效的电话号码」。
- 空串允许，空白不允许；不 trim、不接受 Unicode 数字、不扩大国家码/区号/分机长度。
- 仍允许手机号 **或座机**，包括 400/800 和分机。用户字段虽标作「手机号」，旧规则并未限定 mobile-only，本批不擅自收窄。
- null 由 Jakarta Pattern 的标准可空语义承接，没有额外增加 NotBlank/NotNull。
- 错误处理器仍消费相同的字段错误文案；标准 Pattern 会提供可识别的正则元数据，但不改 HTTP 错误码/返回结构。

## 足迹：不靠通配符隐藏耦合

| 指标 | B2c 后 | B3a 后 |
| --- | --- | --- |
| 含 AirPower 引用的 Java 文件 | 162 / 459 | **161 / 462** |
| 符号 / 引用 | 46 / 546 | **44 / 544** |
| core / curd / api / redis / cookie | 228 / 263 / 41 / 13 / 1 | **226** / 263 / 41 / 13 / 1 |

减去两个显式 Phone 导入、三个无用字典标记；通配展开由 1 处变成 4 处，**如实加回 3 处**。
净减少 2 引用，不把“消除了三个 Phone 使用方”混报成“减少三个显式引用”。

## 验证与检查点

- `JAVA_HOME=<JDK17> pnpm java:unit`：**16 个生产源码真实编译，91 项独立契约通过**，UTF-8/US-ASCII 各跑同一组。
  本轮新增 12 个电话规则用例，含空白边界、手机号/座机、国家码、分机、非 ASCII 数字、原表达式差分。
- javac AST：462 个后端源文件通过，只证明语法和已知注解规则，**不等于完整类型检查**。
- `pnpm check`：**55 文件 / 523 用例通过**，含 35 项退场门禁与 6 项电话边界静态检查；构建、体积通过。
- 新增 **17 项 Maven 接线契约未执行**：真实 Jakarta Validation 对三个实体各五项，加错误/配置契约各一项。
  这些测试才覆盖实际 null/group/message/约束类型；不能把 JDK 正则测试当成提供者接线通过。
- 本地完整 Maven 重试依旧在 Wrapper 下载阶段 SSLHandshakeException / EOFException，**未进入项目编译**。
- 现有 B2a–B3a 工作将保存为当前 `arena/01a0bf0c-kubo` 分支的 CI 检查点，再检验 Actions 是否能启动。
  上一次基线 run 35515416860 因账户付款/额度问题没有执行步骤；本轮结果必须看实际 run，不能沿用旧结论。
  不合并、不发布、不跳过 server compile/test，也不靠假依赖或下调验证标准制造成功。

后端仍有 544 处源码引用。后续 Json/元数据主体/鉴权/CRUD 迁移需要完整依赖环境与接口回归，
这些独立子集的绿灯不构成整仓验收。
