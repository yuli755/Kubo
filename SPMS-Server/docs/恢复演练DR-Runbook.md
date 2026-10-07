# 恢复演练 DR Runbook（生产级能力补全 · 恢复演练）

> 触发：生产级审查报告「恢复演练 DR drill」项。`备份与恢复策略方案.md` §4 给了演练的「是什么/为什么」，
> 本文把演练落成**可执行的 Runbook + 脚本**：怎么在隔离环境恢复、怎么判「真的恢复了」、怎么记录。
> 核心原则不变：**未经演练的备份 ≈ 没有备份**。

---

## 0. 结论先行

1. 演练只做一件事：**在隔离环境从「最近全量备份（+ 可选 binlog）」把库完整恢复回来，并用关键表
   行数 + schema 证明「能恢复」**，而不是证明「备份文件存在」。
2. 已交付两个脚本（模板，运维填真实拓扑后即可跑）：
   - `infra/backup/backup-mysql.sh` —— 每日全量备份（mysqldump 热备 + `gzip -t` 校验 + 保留策略 + 可选推送备份机）；
   - `infra/backup/restore-drill.sh` —— 恢复演练（**默认拒绝触碰生产库** → drop+create 恢复 → 关键表行数校验 → 可选 PITR 回放）。
3. 已交付静态门禁 `infra/scripts/check-backup-drill.mjs`（接入 `pnpm drill:check` + `check` 链）：
   锁死「恢复脚本必须有拒绝生产护栏」「必须 fail-fast」「备份必须 `gzip -t` 校验」，防灾难当天才发现脚本本身是坏的。
4. **诚实边界**：脚本在沙箱跑不了（无 docker/MySQL），`bash -n` 语法与静态门禁已过，**真实恢复
   行为需运维在隔离环境执行一次**（见 §3 首演清单）。

---

## 1. 演练频率与触发

| 触发 | 频率 | 说明 |
| --- | --- | --- |
| 定期演练 | 每季度（建议） | 全链路：全量恢复 + PITR + 应用冒烟 |
| 备份变更后 | 立即 | 改了备份脚本/策略后，必须先演练再上线 |
| 关键升级前 | 前置 | 大版本升级、迁移脚本大批量执行前，先用最新备份练一遍回退 |

---

## 2. 演练前置检查

- [ ] 最近全量备份存在且 `gzip -t` 通过（`ls -lt /backup/mysql/` + `gzip -t <latest>`）；
- [ ] 若执行 `backup-mysql.sh --remote`，已配置真实 `BINLOG_SOURCE_DIR`，目录内存在 `mysql-bin.*`；脚本会把 binlog 与全量备份、`.sha256` 一并推送，否则 fail-closed；
- [ ] binlog 已外置到备份机，且远端 `binlog/` 目录可读取（若做 PITR，不能只确认 `.sql.gz` 存在）；
- [ ] 隔离演练环境可用（独立的 MySQL 8.0.36 容器，**绝不与 kubo-mysql 同容器/同库**）；
- [ ] 通知相关方：演练期间隔离环境可能短暂占用备份机带宽。

---

## 3. 首演（第一次跑，务必人工盯）

```bash
# 1. 拉起隔离演练容器（一次性；复用 docker 已配的 mysql:8.0.36 镜像）
docker run -d --name kubo-mysql-drill -e MYSQL_ROOT_PASSWORD=root -p 3307:3306 mysql:8.0.36

# 2. 确认安全护栏生效（演练容器 ≠ 生产容器，且 DRILL_ALLOW_PROD 未开）
./infra/backup/restore-drill.sh
#    预期输出末尾：✓ 恢复演练通过：/backup/mysql/kubo-xxx.sql.gz → kubo-mysql-drill.kubo_drill

# 3. （可选）PITR 回放验证
./infra/backup/restore-drill.sh --pitr '2026-09-10 14:00:00'

# 4. 演练后清理隔离容器
docker rm -f kubo-mysql-drill
```

**安全护栏验证**：故意把 `DRILL_CONTAINER=kubo-mysql` 且不设 `DRILL_ALLOW_PROD`，脚本必须拒绝执行
（输出「✗ 拒绝：演练目标容器与生产容器相同」）。这一步证明护栏真的会拦，而不是摆设。

---

## 4. 判定「真的恢复了」（验收标准）

| 项 | 判定 |
| --- | --- |
| 关键表行数 | `users/roles/menus/materials/devices/orders` 等非 0 且与备份时点吻合 |
| schema | 在演练库跑迁移脚本（`src/main/resources/db/migration/*.sql`，经 Flyway 或按版本手工执行）不报错，或 `mysqldump --no-data` 比对表结构 |
| 应用冒烟 | 临时把后端 datasource 指向演练库，启动成功 + 登录 + 拉一条列表（可选，最接近真实） |
| PITR | 回放到指定时间点后，该时间点之后的数据**不**存在、之前的**存在**（用一条已知写入验证） |

---

## 5. 演练记录表（每次回写）

| 日期 | 备份时点 | 恢复耗时 | PITR | 关键表行数 | 问题/修正 | 执行人 |
| --- | --- | --- | --- | --- | --- | --- |
| （示例）2026-09-10 | 2026-09-10 02:00 | 4 分 12 秒 | 未做 | users 3 / orders 0（订单表空，属正常） | 无 | - |
|  |  |  |  |  |  |  |

> 恢复耗时对表 RTO（备份方案 §3 的 RTO ≤ 1 小时），若首演超时，回写瓶颈（备份下载/导入/回放）并优化。

---

## 6. 自动化（可选强化，需 CI 额度）

`备份与恢复策略方案.md` §8.4 的「CI 每夜恢复验证 job」落地形态（待 CI 额度）：

```yaml
# .github/workflows 内新增 job 的示意（真实文件待 CI 恢复后加）
restore-drill:
  runs-on: ubuntu-latest
  services:
    mysql:
      image: mysql:8.0.36
      env: { MYSQL_ROOT_PASSWORD: root }
  steps:
    - uses: actions/checkout@v4
    - run: ./infra/backup/restore-drill.sh   # 隔离 service 上跑，备份来自仓库外的对象存储
```

每夜从对象存储拉最新备份跑一次恢复 + 行数校验，**备份损坏当天即被发现**，而非灾难那天。
该 job 依赖 CI 额度与「备份推送对象存储」先落地，属后续强化项，不影响本批交付。

---

## 7. 与其它能力的关系

- **备份与恢复策略方案.md**：本 Runbook 是其 §4 的执行版；RPO/RTO 目标与资产分级见该文档。
- **密钥轮换 SOP**：演练冒烟会启动后端，若恰好撞上密钥轮换窗口，注意新密钥与演练库 datasource 的一致性。
- **迁移方言门禁**（`migration:check`）：演练第 4 步「在演练库跑迁移脚本」会复用这批脚本，两者配合验证 schema 可重建。
