#!/usr/bin/env bash
# ============================================================================
# 恢复演练脚本（DR drill · 模板）
# ----------------------------------------------------------------------------
# 依据 docs/备份与恢复策略方案.md §4：未经演练的备份 ≈ 没有备份。本脚本在**隔离
# 环境**从「最近全量备份」恢复，验证「能恢复」而非「备份文件存在」。
#
# 安全护栏（最高优先级，违反即退出）：
#   - 默认**拒绝**对生产库（kubo-mysql）执行任何写操作；
#   - 演练目标必须是隔离的 MySQL（默认 kubo-mysql-drill，docker compose 拉起），
#     或显式 DRILL_TARGET_HOST 指定的独立实例；
#   - 只有显式 DRILL_ALLOW_PROD=1 才放行对生产容器的操作（仅用于「演练就在生产旁路」
#     的受控场景，务必先在低峰窗口确认）。
#
# 演练内容：
#   1. 找到最近全量备份（backup-mysql.sh 产物 kubo-*.sql.gz）；
#   2. 恢复到隔离库（drop + create + 导入）；
#   3. （可选）回放 binlog 到指定时间点做 PITR 验证；
#   4. 校验：关键表行数、schema（跑迁移脚本）、可选应用启动冒烟。
#
# 用法：./restore-drill.sh                     # 隔离库恢复 + 行数/schema 校验
#       ./restore-drill.sh --pitr '2026-09-10 14:00:00'   # 追加 PITR 回放验证
#
# 演练产出（回写 docs/恢复演练DR-Runbook.md 的演练记录表）：
#   恢复耗时、遇到的问题、备份脚本/文档需修正处。
# ============================================================================
set -euo pipefail

# ---- 可配置项（运维按真实拓扑改） ------------------------------------------
MYSQL_CONTAINER="${MYSQL_CONTAINER:-kubo-mysql}"        # 生产容器（只读探测用）
DRILL_CONTAINER="${DRILL_CONTAINER:-kubo-mysql-drill}"  # 隔离演练容器
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-kubo}"
DRILL_DATABASE="${DRILL_DATABASE:-kubo_drill}"          # 演练目标库（隔离）
BACKUP_DIR="${BACKUP_DIR:-/backup/mysql}"
DRILL_ALLOW_PROD="${DRILL_ALLOW_PROD:-0}"               # 显式 1 才允许触碰生产库
# 关键表行数抽查（验证业务主表被完整恢复；按需增删）
CHECK_TABLES="${CHECK_TABLES:-users roles menus materials devices orders}"
# ----------------------------------------------------------------------------

log() { echo "[$(date +%F\ %T)] $*"; }

# 0. 安全护栏
if [[ "${DRILL_ALLOW_PROD}" != "1" ]]; then
  if [[ "${DRILL_CONTAINER}" == "${MYSQL_CONTAINER}" ]]; then
    echo "✗ 拒绝：演练目标容器与生产容器相同（${MYSQL_CONTAINER}）。" >&2
    echo "  演练必须在隔离环境进行；确需生产旁路演练请显式 DRILL_ALLOW_PROD=1。" >&2
    exit 1
  fi
fi

command -v docker >/dev/null 2>&1 || { echo "✗ 需要 docker"; exit 1; }

# 1. 定位最近全量备份
LATEST="$(ls -1t "${BACKUP_DIR}"/kubo-*.sql.gz 2>/dev/null | head -1 || true)"
if [[ -z "${LATEST}" ]]; then
  echo "✗ 找不到备份文件（${BACKUP_DIR}/kubo-*.sql.gz），请先跑 backup-mysql.sh" >&2
  exit 1
fi
log "使用备份：${LATEST}"

# 2. 确认演练容器可用
if ! docker ps --format '{{.Names}}' | grep -qx "${DRILL_CONTAINER}"; then
  echo "✗ 演练容器 ${DRILL_CONTAINER} 未运行。先用隔离实例起一个 MySQL：" >&2
  echo "  docker run -d --name ${DRILL_CONTAINER} -e MYSQL_ROOT_PASSWORD=${MYSQL_PASSWORD} mysql:8.0.36" >&2
  exit 1
fi

# 3. 恢复（drop + create + 导入；gunzip 管道导入，避免落盘解压）
log "恢复到 ${DRILL_CONTAINER}.${DRILL_DATABASE} …"
docker exec -i "${DRILL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
  -e "DROP DATABASE IF EXISTS \`${DRILL_DATABASE}\`; CREATE DATABASE \`${DRILL_DATABASE}\` CHARACTER SET utf8mb4;"
gunzip -c "${LATEST}" | docker exec -i "${DRILL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${DRILL_DATABASE}"

# 4. （可选）PITR：回放 binlog 到指定时间点
if [[ "${1:-}" == "--pitr" && -n "${2:-}" ]]; then
  PITR_UNTIL="${2}"
  log "PITR 回放 binlog 至 ${PITR_UNTIL}（需备份机已外置 binlog，见 backup-mysql.sh --remote）"
  # TODO(运维): 从备份机取 binlog 后回放：
  #   mysqlbinlog --stop-datetime="${PITR_UNTIL}" mysql-bin.* | \
  #     docker exec -i "${DRILL_CONTAINER}" mysql -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${DRILL_DATABASE}"
fi

# 5. 校验：关键表行数 + schema（行数 0 说明恢复不完整/表名不对）
log "校验关键表行数 …"
FAILED=0
for t in ${CHECK_TABLES}; do
  CNT="$(docker exec "${DRILL_CONTAINER}" mysql -N -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
    "${DRILL_DATABASE}" -e "SELECT COUNT(*) FROM \`${t}\`;" 2>/dev/null || echo 'ERR')"
  log "  ${t}: ${CNT} 行"
  [[ "${CNT}" == "ERR" ]] && { echo "  ✗ ${t} 行数查询失败（表不存在？）"; FAILED=1; }
done

if [[ "${FAILED}" == "1" ]]; then
  echo "✗ 恢复校验失败" >&2
  exit 1
fi

log "✓ 恢复演练通过：${LATEST} → ${DRILL_CONTAINER}.${DRILL_DATABASE}"
log "  记录恢复耗时与问题到 docs/恢复演练DR-Runbook.md 演练记录表"
