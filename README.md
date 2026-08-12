<p align="center">
<img src="./src/assets/img/logo.svg" width="240"/>
</p>

<p align="center">
  <img src="https://svg.hamm.cn?key=Lang&value=TypeScript&bg=green"/>
  <img src="https://svg.hamm.cn?key=Base&value=Vue3"/>
  <img src="https://svg.hamm.cn?key=Build&value=Vite"/>
  <img src="https://svg.hamm.cn?key=UI&value=ElementPlus"/>
</p>

## Kubo · 智方

**Kubo** 智能生产管理系统，是一个集成化、智能化的企业级应用软件，集成了：

- **MES 制造执行系统** - 车间层生产活动优化管理，实时监控从订单下达到产品完成的整个生产过程
- **WMS 仓库管理系统** - 全面管理入库、出库、库存、盘点、批次等仓库业务流程
- **ERP 企业资源计划** - 整合财务、采购、销售、生产、库存等各环节信息
- **QMS 质量管理系统** - 质量策划、控制、保证及改进全流程管理
- **IoTS 物联网管理** - 连接生产设备和传感器，实现设备远程监控、预防性维护

实现从采购、生产、仓储到销售全流程的信息化、精细化、智能化管理。

## 快速开始

### 📚 完整文档

- **[STARTUP.md](./STARTUP.md)** - 完整启动指南 (环境检查、Redis启动、项目启动)
- **[LOGIN.md](./LOGIN.md)** - 默认账号和测试数据说明

### 环境准备

- Node.js 18+
- Yarn 包管理器
- TypeScript 5.9+
- MySQL 8+
- Redis

### 安装依赖

```shell
  yarn
```

### 配置环境变量

复制模板并按需修改：

```shell
  cp .env.template .env
```

修改配置：

```conf
VITE_APP_NAME = "开发环境"
VITE_APP_API_URL = "/api/"
```

### 启动项目

```shell
# 启动开发服务器
yarn s

# 打包生产环境
yarn production
```

### 🔐 默认登录账号

首次启动后，系统会自动创建测试账号：

```
账号: 17666666666  (或 admin@hamm.cn)
密码: Aa123456
```

详细信息查看 **[LOGIN.md](./LOGIN.md)**

### 启动项目

```shell
# 开发环境
yarn s

# 生产构建
yarn production

# 代码格式检查与修复
yarn lint
```

### VS Code 推荐插件

- Vue - Official
- ESLint
- SCSS Formatter

> 建议在工作区禁用 `Vetur` 插件，避免 Vue2/Vue3 冲突

## AI 辅助开发

如果使用 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等），请让 AI 参考 **[AGENT.md](./AGENT.md)** 文档，其中详细约定了项目的开发规范、目录结构、编码风格等，保证 AI 生成的代码与项目现有风格一致。

## 开源协议

**Kubo** 所有代码均在 **MIT** 开源协议规范下免费提供，你可以放心使用。

如有定制需求，欢迎联系我们：admin#hamm.cn
