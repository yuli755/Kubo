# JWT 密钥轮换 SOP（生产级能力补全 · 密钥轮换）

> 触发：生产级审查报告「密钥轮换 SOP（JWT 双密钥过渡）」项。本文基于**代码事实**，给出可执行的
> 轮换流程与双密钥过渡方案，并诚实标注「哪些现在就能做、哪些受 airpower 框架限制只能上框架 PR」。

---

## 0. 结论先行

1. **现状是「单密钥」**：本仓 `framework.api`（原 airpower-api，2026-09-18 整模块搬入本仓）用**一个**密钥
   `spms.api.access-token-secret`（环境变量 `SPMS_API_ACCESS_TOKEN_SECRET`）对 Access Token 做 HS256 签名与验签。**密钥一旦变更，
   所有存量 Token 立即失效**（用户被强制登出）。这是代码事实，不是可以「配置」绕过的。
2. **没有 Refresh Token**：当前实现只有单个 Access Token（TTL 默认 1 天），`SECURITY_GUIDE.md`
   里的「15min Access + 7 天 Refresh」是**目标态愿景**，后端尚未落地。所以轮换窗口内用户会直接
   掉线，不存在「无感续签」兜底。
3. **已补一道前置防线**：新增 `AccessTokenSecretGuard`，生产 profile 启动时 fail-fast——用弱默认
   密钥 `1234567890` 或过短密钥直接拒绝启动（密钥卫生是轮换的前提）。
4. **推荐路径（零代码，立即可做）**：**受控轮换**（§2），在低峰窗口换密钥 + 重启，接受「一次全员
   重登」。真正**零停机的双密钥过渡**（§3）需要 airpower 上游支持「新旧密钥并存验签」，本仓库
   无法在不 fork/打补丁框架的前提下落地，已给出可提交给上游的改造点。

---

## 1. 现状盘点（代码事实）

| 项 | 事实 | 出处 |
| --- | --- | --- |
| 密钥配置 | `spms.api.access-token-secret: '${SPMS_API_ACCESS_TOKEN_SECRET:1234567890}'` | `application.yml`（`spms:` 段） |
| 默认值 | `1234567890`（弱，10 字符） | `application.yml`（**生产必须用环境变量覆盖**，`AccessTokenSecretGuard` 会 fail-fast） |
| 验签方 | **本仓库源码**：`cn.hamm.spms.framework.api.config.ApiConfig.accessTokenSecret`（单字段，HS256）——原 airpower-api 已整模块搬入本仓（MIT），改名同时把配置前缀改成 `spms.api` | `framework/api/config/ApiConfig.java` |
| Token TTL | 默认 `authorizeExpireSecond = 86400`（1 天），仓库未覆盖 | 本仓 `framework/curd/config/AccessConfig.java` |
| Token 载体 | 前端 `Authorization: Bearer <token>`，token 存 localStorage key `Authorization` | `src/api/axios.ts:61`、`src/utils/airpowerCompat.ts` |
| Refresh Token | **无**（`SECURITY_GUIDE.md` 所述为目标态） | 全仓无 refresh 端点 |
| 轮换后果 | 换密钥 = 全员登出 | airpower 单密钥验签 |

---

## 2. 受控轮换（推荐 · 零代码 · 立即可做）

适用：绝大多数场景。接受「一次全员重登」的成本（Token TTL 1 天，用户感知为「突然要重登一次」）。

### 2.1 前置准备

1. **生成强随机密钥**（≥ 256 位熵）：
   ```bash
   openssl rand -hex 32   # 64 字符 hex，HS256 推荐
   # 或 base64url：openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n'
   ```
2. **确认 `AccessTokenSecretGuard` 已生效**（生产启动时弱密钥会直接拒绝，天然防呆）。
3. **选定低峰窗口**：看监控（Grafana / Loki）找并发最低时段；提前一天在公告渠道通知「系统将于
   X 日 Y 时进行安全维护，届时需重新登录」。
4. **记录当前密钥的托管位置**：密钥应放密钥管理（KMS / 部署平台的 Secret），不写死、不进 Git、
   不进镜像。若当前密钥还写在宿主机 `~/.bashrc` 或 CI 变量里，本次一并迁移。

### 2.2 轮换步骤

1. 在密钥托管处新增**新密钥**（保留旧密钥一份，用于回滚，见 §2.3）。
2. 更新部署侧环境变量 `SPMS_API_ACCESS_TOKEN_SECRET = <新密钥>`。
3. 滚动重启后端（优雅停机已配 `server.shutdown: graceful` + 30s 超时，见 `application-production.yml`）。
4. 启动自检：日志出现 `JWT 签名密钥已就绪（长度 64）`，且无 `IllegalStateException`。
5. **冒烟验证**：登录接口 `/user/login`（邮箱+密码）签发新 token → 用新 token 访问任意受保护接口
   返回 200；用**旧 token** 访问应返回 401（证明旧密钥已彻底失效）。
6. 观察 15 分钟：监控看 401 突增（预期：全员重登的一次性毛刺，之后回落）、登录成功率、错误上报
   （`/api/error-report` 若收到 `source=manual,context=login` 的批量失败，可能是用户重登不畅）。

### 2.3 回滚

- 把环境变量 `SPMS_API_ACCESS_TOKEN_SECRET` 回退为**旧密钥** → 重启 → 存量（旧）token 立即
  恢复有效。回滚同样只花一次重启，无需数据库操作。

### 2.4 验收标准

- [ ] 生产启动日志无 `IllegalStateException`；
- [ ] 新密钥可正常签发/验签（冒烟通过）；
- [ ] 旧 token 明确 401；
- [ ] 401 毛刺在窗口后回落；
- [ ] 旧密钥已从密钥托管中删除（防泄露面）。

---

## 3. 双密钥过渡（零停机 · 需 airpower 上游支持）

> **诚实结论**：本仓库**无法在不 fork/打补丁框架的前提下**实现真正的双密钥过渡。原因：JWT 验签
> 发生在 airpower 框架内部，用的是**单一** `accessTokenSecret` 字段，没有「旧密钥列表」概念；
> 在业务层加一个「先试旧密钥」的过滤器**没有意义**——框架随后仍会用新密钥验签并拒绝旧 token。
> 下面给出**可提交给 airpower 上游的改造点**，以及在「上游合入前」的临时替代思路。

### 3.1 目标态（上游改造点）

给 `airpower.api` 增加可选配置：

```yaml
airpower:
  api:
    access-token-secret: '${AIRPOWER_API_ACCESS_TOKEN_SECRET:...}'       # 新密钥（签发+主验签）
    access-token-secret-old: '${AIRPOWER_API_ACCESS_TOKEN_SECRET_OLD:}'  # 旧密钥（仅验签，过渡期）
```

验签逻辑改为：**先用新密钥验签 → 失败再用 `access-token-secret-old`（若配置）验签**；旧密钥验签成功
时打 `WARN` 日志（「检测到旧密钥签发的 token，过渡期结束后应移除 old 密钥」），但不拒请求。
签发始终只用新密钥。

过渡流程：
1. 部署：配好新密钥 + `old` = 当前密钥 → 滚动重启 → 存量 token（旧签名）仍可用，新登录拿新签名 token；
2. 等待 **≥ 1 个 Token TTL（1 天）**：保证所有旧 token 自然过期；
3. 摘除 `access-token-secret-old` → 再滚动重启 → 旧 token 彻底失效，轮换完成。

### 3.2 上游合入前的临时替代（不推荐，仅应急）

如果必须在「单密钥」框架下尽量缩小登出影响，唯一可行是**把 Token TTL 临时调短**，让「存量旧 token」
更快自然过期，从而缩短「换密钥后的混乱期」：

```yaml
airpower:
  curd:
    access:
      authorize-expire-second: 900   # 临时 15 分钟；轮换完成后恢复 86400
```

- 缺点：用户会被更频繁地要求重登；只缩小窗口、不消除「换密钥瞬间全员掉线」。
- 这不是双密钥过渡，**不要**把它当作零停机方案。

---

## 4. 轮换频率与审计建议

| 场景 | 建议频率 | 说明 |
| --- | --- | --- |
| 常规轮换 | 90 天 | 与密码策略一致，降低密钥泄露窗口 |
| 疑似泄露 | 立即 | 走 §2 受控轮换，并强制全员重登（顺带吊销所有存量 token） |
| 人员离职/密钥外泄 | 立即 + 全量审计 | 结合 `operation_log` 审计接口访问异常 |

配套动作：
- 密钥**不进 Git/镜像/日志**（`application-production.yml` 已关 `request-log`/`response-log` 包体打印，
  防 token 泄进日志；`/api/error-report` 已对 `accessToken/authorization/Bearer` 做正则脱敏）。
- 轮换动作记录到变更单，与 `备份与恢复策略方案.md` 的演练节奏对齐。

---

## 5. 与其它能力的关系

- **登录防爆破**（`LoginBruteForceGuard`）：轮换后全员重登，会瞬时抬高登录失败/成功计数，阈值
  （账号 5 次/IP 50 次失败锁 15 分钟）不会误伤「正常重登」，但要留意错误密码输入集中时可能触发
  IP 限流，建议轮换窗口内适当放宽或提前告知。
- **错误上报闭环**（`/api/error-report`）：轮换窗口若登录链路出问题，前端 `context=login` 的错误
  会第一时间汇聚到 Loki，是可观测兜底。
