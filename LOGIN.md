# Kubo 登录测试指南

> 本文与代码逐条对齐（`UserController` / `UserService` / `DevDataInitRunner`）。
> 启动步骤见 [STARTUP.md](./STARTUP.md)；本文只讲「账号、演示数据、初始化条件」。

## 🔐 默认测试账号

系统首次启动（开发模式且运行目录无 `init.lock`）会**自动创建**两个测试账号：

### 管理员账号 1
```
邮箱:   admin@hamm.cn
手机号: 17666666666
密码:   Aa123456
昵称:   凌小云
```

### 管理员账号 2
```
邮箱:   admin@hamm.com
手机号: 13888888888
密码:   Aa123456
昵称:   张三
部门:   生产部
```

**登录说明**（三种方式，端点各不相同）:

| 方式 | 端点 | 账号字段 | 备注 |
| --- | --- | --- | --- |
| 账号 + 密码 | `POST /user/login` | **邮箱或手机号** | 后端先按邮箱查，查不到再按手机号查（`phone` 自 `V8` 迁移起是唯一键） |
| 邮箱 + 验证码 | `POST /user/loginViaEmail` | 邮箱 | 先 `POST /user/sendEmail` 取码，依赖已配置的邮件通道 |
| 手机号 + 验证码 | `POST /user/loginViaPhone` | 手机号 | 先 `POST /user/sendSms` 取码，依赖已配置的短信通道 |

- 密码区分大小写。
- 账号密码登录失败统一返回 `200001 用户账号或密码错误`，**不区分「账号不存在」与「密码错」**（避免账号枚举）。
- 连续失败会触发防爆破锁定，计数维度是「账号原值 + 客户端 IP」。

> ⚠️ 历史坑：账号密码登录曾**只按邮箱查**，用手机号登录会得到误导性的
> `200001 用户账号或密码错误`（K 报告 §4 P2-8 实测复现）。现已按上表支持手机号，
> 若仍失败请先确认该手机号确实写在 `user.phone` 且账号未被禁用。

---

## 📊 自动初始化的演示数据

首次启动后由 `DevDataInitRunner#initDevData` 创建（仅开发模式）：

### 组织架构
- 部门：生产部、材料部

### 资产管理
- 物料：MacBook Pro M4 Max (32G-1TB) 等
- 计量单位：台、个

### 渠道管理
- 客户：腾讯科技（17666666666）等
- 供应商：Apple 中国（17666666666）等
- 销售价格 ¥29,999 / 采购价格 ¥28,000

### 工厂管理
- 仓库：东部大仓（东部1仓、东部2仓）、西部大仓（西部1仓、西部2仓）
- 产线：笔记本电脑产线（工位1、工位2）

### 系统治理
- **权限目录**：启动时扫描代码注解后收敛（日志 `权限目录已收敛：扫描 N 项，库内 M 项…`）
- **菜单**：`MenuService#initMenu` 的种子菜单
- **数据权限**：17 个在世资源 + 列级脱敏策略

> **已退场的域不再出现在演示数据里。** 设备（`asset.device`）、MES
> （`mes.order/plan/picking/bom/routing/operation`）、聊天室（`chat.*`）、IoT 等模块已整体下线：
> 菜单与权限身份由 `V12__retired_module_catalog_convergence.sql` 清理，数据权限目录里的残留资源与
> 策略由 `V13__retired_resource_policy_convergence.sql` 清理。
>
> 老库里可能仍留着 `device` / `room` / `operation` / `bom` / `routing` / `picking` / `plan` / `orders`
> 等**历史表与历史数据** —— 它们已无对应实体、无页面、种子也不再创建；因为 `ddl-auto=update`
> 不删表，所以表会一直留在那儿（新库不会创建）。

---

## 🚀 如何登录测试

### 步骤 1: 启动项目

完整步骤见 [STARTUP.md](./STARTUP.md)。最小三步：

```bash
# 1. Redis（Docker 或本机已装的 Redis；仓库里没有 start-redis.bat）
docker run -d --name kubo-redis -p 6379:6379 redis:alpine

# 2. 后端（新终端）
cd SPMS-Server
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 3. 前端（新终端，仓库根目录）
pnpm dev
```

### 步骤 2: 访问登录页

浏览器打开 http://localhost:3000 （或 http://localhost:3000/login）

### 步骤 3: 输入账号密码

```
账号: admin@hamm.cn        # 或手机号 17666666666
密码: Aa123456
```

点击「登录」。

### 步骤 4: 进入控制台

登录成功后跳转 http://localhost:3000/console

---

## 🔍 初始化触发条件

演示数据初始化**仅在开发模式下**执行，需同时满足：

1. ✅ `AppConfig.isDevMode = true`
2. ✅ 运行目录不存在 `init.lock`，**或** `spring.jpa.hibernate.ddl-auto = create-drop`

初始化完成后在运行目录生成 `init.lock`，避免重复初始化。

**权限目录同步不受这两道闸限制**：`syncPermissionCatalog()` 在 `DevDataInitRunner#run` 的最前面执行，
生产环境（`isDevMode=false`）与已有 `init.lock` 的本地环境同样会同步 —— 否则新增端点会因权限行未落库
而对所有人（含超管）返回 403。

> ⚠️ `init.lock` 是**相对运行目录**的路径：从 `SPMS-Server/` 启动，与从仓库根用
> `mvn -f SPMS-Server/pom.xml spring-boot:run` 启动，锁文件会落在不同目录。删除前先确认后端进程的
> 实际工作目录（K 报告 §4 P2-10 已记录，建议后续改为基于数据源的标志位，与运行目录彻底解耦）。

---

## ⚙️ 重新初始化数据

### 方式 1: 删除锁文件
```powershell
cd D:\JAVA\Code_repository\SPMS-Web\SPMS-Server
Remove-Item init.lock        # bash: rm init.lock
# 重启后端即重新初始化
```

### 方式 2: 清空数据库
```bash
mysql -u root -proot -e "DROP DATABASE kubo; CREATE DATABASE kubo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# 重启后端：Flyway 跑 V1..V13 建表，JPA 校验/补齐业务表，然后初始化演示数据
```

---

## 🛠️ 修改默认密码

编辑 `SPMS-Server/src/main/java/cn/hamm/spms/DevDataInitRunner.java`（两个测试账号各一处，约 L720 / L727）：

```java
.setPassword(PasswordHasher.encode("Aa123456", salt))
// 改成你想要的密码，例如：
.setPassword(PasswordHasher.encode("YourPassword123", salt))
```

然后删除 `init.lock`、重启后端。

---

## ❓ 常见问题

### 问题 1: 找不到用户
```
登录失败: 用户不存在 / 用户账号或密码错误
```
**原因**: 演示数据初始化未执行。
**解决**:
1. 看后端日志有没有 `已存在 init.lock 文件，无需初始化数据`；
2. 有则删除 `SPMS-Server/init.lock`（注意上面「相对运行目录」的坑）；
3. 重启后端。

### 问题 2: 密码错误
```
code 200001 用户账号或密码错误
```
**确认**:
- 密码 `Aa123456`（大小写敏感）；
- 账号用 `admin@hamm.cn`，或手机号 `17666666666`；
- 用手机号仍失败时：`SELECT email, phone, is_disabled FROM user;` 核对该手机号是否真的在库里、账号是否被禁用。

### 问题 3: 无演示数据
```
登录成功但系统没有任何数据
```
**检查**: 后端启动日志是否有初始化记录；`kubo` 库里业务表是否有行；是否跑在非开发模式。

### 问题 4: 点菜单白屏或跳到 404
菜单指向的视图文件缺失（改名/删除，或库里的菜单被手改过 `path`/`component`）。
`initVueRouter` 对缺失组件**跳过注册**并打 `console.warn`（含菜单名与缺失键），该地址会落到 catch-all 404 ——
打开浏览器控制台即可看到缺哪个文件，而不是面对一片没有线索的空白。

---

## 📝 数据库表结构

建表由 JPA（`ddl-auto=update`，开发环境）+ Flyway（`V1..V13` 版本化迁移）共同完成。核心业务表：

| 分组 | 表 |
| --- | --- |
| 组织与账号 | `user`、`department`、`role`、`user_role_list`、`user_department_list`、`user_storage_list`、`tenant`、`user_tenant`、`user_factory` |
| 权限治理 | `menu`、`permission`、`role_menu_list`、`role_permission_list`、`permission_change_outbox`、`personal_token`、`user_third_login`、`open_app` |
| 数据权限 | `data_resource`、`data_policy`、`data_field_policy`、`data_permission_diff_log`、`data_permission_deny_log`、`data_permission_orm_parity_log` |
| 仓储（WMS） | `input`/`input_detail`、`output`/`output_detail`、`move`/`move_detail`、`inventory` |
| 渠道 | `purchase`/`purchase_detail`、`sale`/`sale_detail`、`customer`、`supplier`、`purchase_price`、`sale_price` |
| 资产 | `contract`、`contract_participant_list`、`contract_document(_list)`、`participant`、`material`、`unit` |
| 工厂 | `storage`、`structure` |
| 系统 | `config`、`coderule`、`notify`、`file`、`operation_log` |

> 本文**不锁定行数**：行数会随演示与实际操作变化，写死就是下一次「文档过期」。需要核对时直接查库：
> ```bash
> mysql -u root -proot kubo -e "SELECT COUNT(*) FROM user;"
> ```

---

## 🎉 快速验证

1. 打开 http://localhost:3000/login
2. 输入 `admin@hamm.cn` / `Aa123456`（或 `17666666666` / `Aa123456`）
3. 点击登录
4. 成功进入控制台即表示一切正常

---

**提示**: 所有演示数据仅用于开发和演示，生产环境请关闭 `isDevMode`（`DevDataInitRunner` 会直接返回，不写任何演示数据）。
