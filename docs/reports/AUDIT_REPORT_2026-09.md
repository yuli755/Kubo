# Kubo 平台分阶段安全与业务审核报告（2026-09）

> 审核方式：前后端逐模块、按业务场景黑盒运行验证（直连 8080 的真实系统 +
> MySQL 核对落库结果），发现即修复并以「单元/契约测试 + 黑盒探针」双重钉死；
> 每个阶段独立提交、全量回归后推送。

## 总览

| 阶段 | 范围 | 探针 | 提交 | 关键修复 |
|---|---|---|---|---|
| A | 认证 / 会话安全 | auditA 22/22 | `9e5edea4` | 明文密码入库、Bearer 链路、唯一键空串、密码轮换旧盐 |
| B | RBAC / 数据权限 | auditB 17/17 | `0c7378a3` | 大小写口径分裂、禁用态不生效、权限上下文跨请求残留 |
| C | WMS 核心事务 | auditC 12/12 | `ed98fdc1` | 单据可重复执行、并发审核竞态、超发、库存行直改等 7 项 |
| D | 系统配置与公共能力 | auditD 10/10 | `16a691ba` | 令牌过期/上限/IDOR、文件属主、弱密钥守卫加固 |
| E | 前端模块（守卫/store/请求层/编辑器） | auditE 6/6 | `b73d6129` | 登出复活、开放重定向、addFinish 载荷、令牌脱敏、401 风暴 |
| F | 后端遗留项专项（登录限流/异常语义/移库并发/菜单权限） | auditF 5/5 | 本次提交 | XFF 伪造绕过锁定、4034 误报、交叉移库丢失更新（根因级）、menu.permission 落地 |

最终基线：后端 JUnit 660/660；前端 vitest 289/289、vue-tsc 0 错、eslint 0 错；
auditA–F 合计 72 项黑盒场景全部 PASS。

---

## 阶段 A：认证 / 会话安全（`9e5edea4`）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| A-1 | 严重 | `/user/add`、`/user/update` 把请求体 password 裸写入库（明文+空盐） | `beforeAdd/beforeUpdate` 在「仅提交值」阶段换服务端盐+散列；`beforeAppSaveToDatabase` 兜底直存路径；内部成对凭据按形状透传 |
| A-2 | 高 | `getCurrentUserId()` 不剥 `Bearer ` 前缀，标准 Bearer 客户端所有 `my*` 接口恒 401 | `BearerTokenNormalizationFilter` 容器边缘归一头，其它 scheme 原样透传；logout 吊销链路实测生效 |
| A-3 | 中 | `user.email/phone` unique+default ''：第二个未填用户撞唯一键且被误报 4034 | 列默认值改 NULL + 存量清洗迁移 |
| A-4 | 高 | 轮换密码时框架 null-忽略合并旧盐，裸密码+旧盐入库、新密码永远登不上 | `beforeUpdate`（合并前）归一；`modifyPassword` 盐长统一 `PASSWORD_SALT_LENGTH` |

## 阶段 B：RBAC / 数据权限（`0c7378a3`）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| B-1 | 严重 | 权限标识大小写口径分裂（URL 派生 `material_getPage` vs 登记 `Material_getPage`），非 root 恒 403，授权通道自始未通 | `checkUserPermission` 以库中 identity 为准；`has()` equalsIgnoreCase 对齐库内 CI 口径 |
| B-7 | 高 | `has()`/`isRootUser` 不看 `is_disabled`，禁用角色/权限不产生收权（停用超管仍 root） | 两处统一跳过禁用行，历史 NULL 视为启用 |
| B-12 | 严重 | 鉴权抛异常时 PermissionContext 滞留 Tomcat 线程，下一请求继承上一用户 userId/租户/bypass（实测伪造 `X-Tenant-Id` 躲过解析返回 200） | 抛出先清上下文；`ensurePermissionContext` 取消线程级早退、按本请求用户无条件重建；禁用角色 code 不进数据权限匹配集 |

## 阶段 C：WMS 核心事务（`ed98fdc1`）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| C-1 | 致命 | Move 自动生成的 DONE 单据明细未完成，可再 addFinish 二次扣库存（实测净损失 15 件）；AUDITING 单可先动库存 | `canAddFinish` 状态闸门：仅「已审核/执行中」可录入 |
| C-2 | 高 | 领料完成「读-改-裸写」丢失更新 | 改走 `InventoryService.updateWithLock` |
| C-3 | 高 | 库存行无唯一键，并发插重复行、出库永远少算 | 幂等迁移：合并去重 + 两条唯一索引；输者整单可见失败 |
| C-4 | 高 | audit 无锁查-判-写（6 路并发 3 路成功），销售单双审派生两张出库单 | 行锁事务化；后置方法进事务，派生失败整体回滚 |
| C-5 | 高 | 完成量不设上限（10 件单可画进 999 件库存） | 累计完成量超单据数量即 403 |
| C-8 | 中 | 驳回更新错实例（status 原封不动）+ 原因从未落库 | 行锁 + 同实例更新 + rejectReason 入库 |
| C-9 | 高 | `/inventory/update` 等 HTTP 直改库存绕开全部单据流 | Inventory 控制器显式排除 Add/Update/Delete |

## 阶段 D：系统配置与公共能力（`16a691ba`）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| D-1 | 高 | 私人令牌永不过期 | 强制 365 天过期（`setExpireSecond`） |
| D-2 | 中 | 改名可无限刷令牌，吊销面失控 | 单用户上限 20 |
| D-3 | 高 | 通用 update/getDetail 无属主过滤（令牌 IDOR） | `beforeAppUpdate`/`afterGetDetail` 属主校验 |
| D-4 | 中 | 文件 MD5 秒传跨用户复用（泄露他人文件存在性/文件名） | 新增 `creator_id`（迁移 `20260914_file_creator_id.sql`），仅同属主复用 |
| D-5 | 中 | 文件删除无归属校验 | 属主放行、其余仅 root；匿名历史行仅 root 清理 |
| 密钥 | 高 | `@Profile("production")` 守卫：忘设 profile 的生产实例静默用默认弱密钥 | 守卫全 profile fail-fast；本地显式 `kubo.security.allow-weak-default-secret=true`；Dockerfile 部署说明 |

## 阶段 E：前端模块（`b73d6129`）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| E-C1 | 高 | 内置登出只删 `Authorization` 键，`kubo:user` 快照复活 token/refreshToken，共享电脑「注销」形同虚设 | persist 新增 `onRestore`（Authorization 缺失而快照含 token → 丢弃快照）；UserCard 真实登出后 `replace('/login')` |
| E-C2 | 高 | `?redirectUri=/?redirect=` 访问者可控，可把登录后会话/OAuth code 送到外域 | `safeRedirect` 只放行站内相对路径；内部应用跳转须与 App 登记回调精确相等（与后端 `isValidRedirectUri` 同规则）；authorize.vue 同步收口 |
| E-H1 | 高 | input/output「确认入库/出库」从未可用：载荷把本次数量放进 `finishQuantity`（后端读 `quantity`）且缺 `storage/inventory`，入库必 403、出库必 NPE | api/hook/编辑器三层载荷改为 `{id, quantity, storage|inventory:{id}}`；未选目标前置提示；出库默认值改「待出数量」 |
| E-H1b | 高 | （后端）MoveService 以明细总量为搬运量：部分录入未完成时可重复 addFinish，同一批库存整单反复搬运 | 改为按本次提交增量搬运（auditE E05：4 件+6 件 库存守恒） |
| E-H3 | 中 | `userInfo`（邮箱/手机号/实名明文）持久化进 localStorage | persist `pick` 去掉 userInfo，页面每次挂载重新拉取 |
| E-M1 | 中 | `getMyList` 的 `@ExposeAll` 绕过 `@Desensitize`，私人令牌整串明文列表返回（实测长度 196） | 移除 `@ExposeAll`，列表脱敏；完整值仅 `createMy` 一次性返回（与前端文案一致） |
| E-M4 | 中 | 401 每请求各自刷新（风暴+连环失效）；刷新成功但重试仍 401 时卡「已登录处处 401」；密码输错也触发登出循环 | single-flight 刷新 + 二次 401 强制登出 + `/user/login|refresh|logout` 豁免；新增 5 条 vitest 契约锁 |
| E-L1 | 低 | authGuard 写 `?redirect=` 而 login.vue 只读 `?redirectUri=`，登录后回跳被丢弃 | 两者都读，OAuth 参数优先 |
| E-M5 | 中 | 登出/换账号后 react-query 与脱敏策略缓存跨用户残留 | `logout()` 统一清空两类缓存 |

---

## 阶段 F：后端遗留项专项（报告记录项 1/7/8/9 的针对性修复）

| ID | 级别 | 问题 | 修复 |
|---|---|---|---|
| F-1 | 高 | 登录防爆破锁定与公开端点限流无条件信任 `X-Forwarded-For`/`X-Real-IP`：每次请求伪造一个 IP，IP 维度计数永不达阈值，锁定/限流形同虚设 | 新增 `ClientIpResolver`：仅当直连对端命中 `kubo.security.trusted-proxies`（CIDR/精确 IP，默认空=永不采信）才使用转发头；`LoginBruteForceGuard`/`RateLimitHelper` 统一接入；自实现 IPv4 CIDR 匹配；新增 6 条单测 + 守卫回归 2 条；auditF F01：5 次失败（每次换伪造 XFF）第 6 次即锁，换 IP 仍在锁定期 |
| F-2 | 中 | 唯一键/外键/列长等约束异常被框架统一映射为 4034「删除失败，数据正在使用中」，误导排障 | 新增 `DataIntegrityViolationAdvice`（`@Order(HIGHEST_PRECEDENCE)` 抢在框架 ExceptionInterceptor 前）：Duplicate→4031、外键引用→4034、子行缺失→403、列长/非空→4002，均带列名与原始原因；7 条单测；auditF F02：重复 code 现返回 4031「单位编码…已经存在」 |
| F-3 | 严重 | 交叉移库（A→B 与 B→A 并发）丢失更新：一轮审计先按「锁序」修（ID 升序预锁）后探针仍复现总量 +10；SQL 日志证明单侧事务的数量写入只更新了 `update_time`——airpower `updateWithLock` 的「FOR UPDATE 读 → 实体改 → 回写」在并发+REPEATABLE_READ 下数量写入可整体丢失 | 根因级修复：`InventoryService.addInventoryQuantity/reduceInventoryQuantity` 改为数据库端原子算术 UPDATE（`quantity = quantity ± ?`，负库存守卫进 WHERE），彻底消除读-改-写窗口；`MoveService` 保留按库存行 ID 升序的 `getForUpdate` 预锁（防交叉行锁死锁）；auditF F03 三轮竞速 6/6 成功且总量守恒；auditC 12/12（含 C06/C07 负库存守卫）回归通过 |
| F-4 | 中 | 前端路由守卫 `permissionGuard` 与 `applyMenuPermissions` 早已接通 `menu.permission → meta.permission`，但后端 menu 表无 permission 列，守卫恒过（记录项 1） | `MenuEntity` 新增 `permission` 列（`@Meta`，默认空串=不校验，存量行为不变）；迁移 `20260914_menu_permission.sql`（幂等）；菜单编辑器表单增加「权限标识」字段；auditF F04：`/menu/update` 写入 `Unit_getPage` 后 `getMyMenuList` 原样返回，前端守卫即可按 `permissionStore.hasAny` 拦截 |

配套：`ClientIpResolver`/`DataIntegrityViolationAdvice` 新增 13 条单测、
`RateLimitHelperTest`/`LoginBruteForceGuardTest` 按可信代理语义改写，
后端全量 660/660。

---

## 仅记录、未改动（建议后续迭代处理）

1. **meta.permission 死守卫（H）**：✅ 已在阶段 F-4 修复（menu.permission 列 + 迁移
   + 菜单表单字段，守卫按 `permissionStore.hasAny` 实拦；`v-permission` 仍仅控显示，
   服务端鉴权兜底）。
2. **v-mask 死设施（M）**：`useMaskPolicy` 已接入登出清理，但列级脱敏渲染器
   尚未接进业务表格，属半成品基础设施。
3. **URL 携带 token（M）**：WebSocket 查询串与 airpower `window.open` 导出下载
   把 token 放进 URL（会进访问日志/Referer）。根因在 @airpower/web 库层，
   需库升级或后端支持一次性签名下载票据后收口。
4. **rememberMe 装饰性（低）**：token 存活期与勾选无关。浏览器端无法可靠区分
   刷新/关标签，未做半残修复。
5. **缺失路由（低）**：登录页文案链接 `/forgot-password`、`/privacy`、`/terms` 无对应路由（404）。
6. **errorReportClient 无脱敏（低）**：错误上报体未做敏感字段 redaction，接入
   外部上报服务前必须补。
7. **X-Forwarded-For 伪造（后端，低）**：✅ 已在阶段 F-1 修复（可信代理白名单门控，
   默认空白名单=永不采信转发头；nginx 反代部署必须配置 `kubo.security.trusted-proxies`）。
8. **4034 误报（后端，低）**：✅ 已在阶段 F-2 修复（约束异常按类型映射
   4031/4034/403/4002 并带列名）。
9. **移库死锁窗口（后端，低）**：✅ 已在阶段 F-3 修复并超出原判断——真正的根因是
   锁内读-改-写的丢失更新；现数量变更为数据库端原子 UPDATE，另按库存行 ID 升序
   预锁消除交叉死锁。
10. **盘点模块缺失（业务）**：库存只有单据流，无盘点/盈亏调整单据，账实不符无
    合规修正通道。

## 部署检查清单（本次审核引入的运维要求）

- [ ] 生产设置 `AIRPOWER_API_ACCESS_TOKEN_SECRET` 强随机值（否则启动 fail-fast）。
- [ ] 生产**删除/关闭** `kubo.security.allow-weak-default-secret`（base 配置默认
      true 仅供本地开发；production profile 已置 false）。
- [ ] 正确设置 `SPRING_PROFILES_ACTIVE=production`（密钥守卫现已与 profile 无关，
      但数据源/日志仍依赖它）。
- [ ] 执行迁移：`20260914_file_creator_id.sql`（file.creator_id + 索引，幂等）、
      A-3 空串清洗、C-3 库存唯一键迁移；**F 阶段新增**：
      `20260914_menu_permission.sql`（menu.permission 列，幂等）。base profile 为
      `ddl-auto=validate`，先迁移后发版。
- [ ] **F-1**：如有 nginx 等反向代理，把代理机 IP/网段写入
      `kubo.security.trusted-proxies`（逗号分隔 CIDR）；不配置则所有请求按代理
      出口 IP 归并计数（安全但共享 IP 的用户会互相挤占限流/锁定额度）。
- [ ] **F-4**：按需为敏感菜单在「系统管理→菜单管理」填写权限标识（如
      `Unit_getPage`），留空 = 不校验，存量菜单行为不变。

## 复现与回归入口

```bash
# 后端
cd SPMS-Server && MAVEN_OPTS="-Xmx512m" JAVA_HOME=<jdk17> ./mvnw test -o
# 前端
pnpm test && pnpm typecheck && pnpm lint:ci
# 黑盒探针（需后端 8080 运行）
node SPMS-Server/target/auditA.cjs   # A 认证/会话
node SPMS-Server/target/auditB.cjs   # B RBAC/数据权限
node SPMS-Server/target/auditC.cjs   # C WMS 事务（含并发）
node SPMS-Server/target/auditD.cjs   # D 令牌/文件/配置
node SPMS-Server/target/auditE.cjs   # E 前端契约后端侧回归
node SPMS-Server/target/auditF.cjs   # F 安全遗留项专项（XFF/约束语义/交叉移库/菜单权限）
```

> 探针脚本位于 git-ignored 的 `SPMS-Server/target/`，重启构建后需从 git 历史
> （各阶段提交前的会话产物）或本报告说明重建；测试与契约锁均已入库。
