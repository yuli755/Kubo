# Kubo 启动指南

## ✅ 已完成的准备工作

- ✅ MySQL 数据库 `kubo` 已创建
- ✅ 数据库连接配置已填写 (root/root)
- ✅ 配置文件已清理 (移除邮箱/MQTT/InfluxDB 配置)
- ✅ 品牌已统一为 Kubo

---

## 🚀 快速启动 (3 步)

### 第 1 步: 检查环境

**Windows 用户**：
```bash
# 双击运行或命令行执行
check-env.bat
```

**Linux/Mac 用户**：
```bash
bash check-env.sh
```

这个脚本会自动检查：
- ✓ MySQL 是否连接正常
- ✓ 数据库 `kubo` 是否存在
- ✓ Redis 是否运行
- ✓ Java 是否安装

---

### 第 2 步: 启动 Redis (如果未运行)

**方式 1: 使用 Docker (推荐)**
```bash
# 双击运行
start-redis.bat

# 或手动执行
docker run -d --name kubo-redis -p 6379:6379 redis:alpine
```

**方式 2: 下载 Redis Windows 版本**
1. 下载: https://github.com/tporadowski/redis/releases
2. 解压后运行 `redis-server.exe`

---

### 第 3 步: 启动项目

**启动后端**：
```bash
cd SPMS-Server

# Maven 启动
./mvnw spring-boot:run

# 或使用 IDEA
# 1. 打开 SPMS-Server 目录
# 2. 右键 Application.java -> Run
# 3. 确保 Active profiles: local
```

**启动前端** (新开终端):
```bash
cd D:\JAVA\Code_repository\SPMS-Web

# 首次运行先安装依赖
yarn install

# 启动开发服务器
yarn s
```

**访问**：
- 前端: http://localhost:3000
- 后端: http://localhost:8080

---

## 🔐 登录测试

系统首次启动会**自动创建**测试账号和演示数据：

### 默认管理员账号
```
账号: 17666666666  (或 admin@hamm.cn)
密码: Aa123456
```

### 第二个测试账号
```
账号: 13888888888  (或 admin@hamm.com)
密码: Aa123456
```

**登录方式**: 使用手机号或邮箱登录

**详细说明**: 查看 [LOGIN.md](./LOGIN.md) 了解完整的测试数据和账号信息

---

## 📋 当前配置信息

### MySQL 配置
```yaml
地址: localhost:3306
数据库: kubo
用户名: root
密码: root
字符集: utf8mb4
```

### Redis 配置
```yaml
地址: localhost:6379
密码: (无)
数据库: 0
```

### 应用端口
```yaml
后端: 8080
前端: 3000
```

---

## 🎯 验证启动成功

启动成功后检查：

- [ ] 后端控制台显示 `Started Application in X.XXX seconds`
- [ ] 后端日志显示 `初始化` 相关信息
- [ ] 访问 http://localhost:8080 有响应
- [ ] MySQL 数据库 `kubo` 中自动创建了表
- [ ] 前端自动打开 http://localhost:3000
- [ ] 登录页显示 "请先登录 Kubo!" 提示
- [ ] Logo 显示为 Kubo 立方体图标
- [ ] 使用 `17666666666` / `Aa123456` 可以成功登录

---

## 🔧 常见问题

### 问题 1: Redis 连接失败
```
Error: Unable to connect to Redis
```

**解决**：
1. 运行 `start-redis.bat` 启动 Redis
2. 或检查 Redis 是否在 6379 端口运行：`netstat -ano | findstr :6379`

---

### 问题 2: MySQL 连接失败
```
Error: Communications link failure
```

**解决**：
1. 确认 MySQL 服务已启动
2. 测试连接：`mysql -u root -proot`
3. 检查 `application-local.yml` 中的用户名密码

---

### 问题 3: 端口被占用
```
Port 8080 was already in use
```

**解决**：
```bash
# Windows 查找占用进程
netstat -ano | findstr :8080

# 结束进程
taskkill /PID <进程ID> /F
```

---

### 问题 4: 登录失败 - 用户不存在

**原因**: 数据初始化未执行

**解决**:
1. 检查后端日志是否有 `已存在 init.lock 文件，无需初始化数据`
2. 如果有，删除 `SPMS-Server/init.lock` 文件
3. 重启后端，查看日志确认初始化执行

---

### 问题 5: 数据库表未创建

**不需要手动建表！**

JPA 会在首次启动时**自动创建所有表**（`ddl-auto: update`）

如果表未创建，检查：
1. 数据库 `kubo` 是否存在
2. 后端启动日志是否有错误
3. `application-local.yml` 配置是否正确

---

## 📂 辅助文档和脚本

| 文件 | 说明 |
|------|------|
| `STARTUP.md` | 启动指南 (本文档) |
| `LOGIN.md` | 登录测试和默认数据说明 |
| `README.md` | 项目介绍 |
| `AGENT.md` | 开发规范 |
| `check-env.bat` / `check-env.sh` | 环境检查脚本 |
| `start-redis.bat` | 一键启动 Redis (Docker) |

---

## 📊 自动初始化的数据

首次启动后，系统会自动创建：

- 👥 **用户**: 2个测试账号
- 🏢 **部门**: 生产部、材料部
- 🔧 **设备**: 2台测试设备
- 📦 **物料**: MacBook Pro M4 Max 等
- 🏭 **仓库**: 东部大仓、西部大仓 (各2个子仓)
- 🔄 **工序**: 键盘安装、屏幕贴膜、系统安装
- 📋 **BOM**: 笔记本电脑清单
- 🛤️ **工艺路线**: 笔记本安装流程
- 💬 **聊天室**: 广场、测试

详细信息查看 [LOGIN.md](./LOGIN.md)

---

## 🔄 重新初始化数据

如果需要重新初始化测试数据：

```bash
# 方式 1: 删除锁文件
cd SPMS-Server
rm init.lock
# 重启后端即可重新初始化

# 方式 2: 清空数据库
mysql -u root -proot -e "DROP DATABASE kubo; CREATE DATABASE kubo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# 重启后端会自动建表并初始化数据
```

---

**🎉 祝你启动顺利！**

如有问题，请查看日志或参考 [LOGIN.md](./LOGIN.md) 获取更多帮助。
