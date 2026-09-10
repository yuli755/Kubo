#!/usr/bin/env bash
# ============================================================================
# MySQL 每日全量备份脚本（模板 · 由运维填入真实拓扑后 cron 调度）
# ----------------------------------------------------------------------------
# 依据 docs/备份与恢复策略方案.md §2.1：MySQL 是唯一「丢了就事故级」的资产，
# 全量备份 + binlog 外置（宿主机坏=binlog 没）是 PITR 的完整闭环。
#
# 本脚本基于 docker-compose.yml 的代码事实：
#   - 容器名 kubo-mysql，root 密码 root，库 kubo（见 docker-compose.yml mysql 段）
#   - binlog 已开：--log-bin=mysql-bin --binlog-format=ROW --gtid-mode=ON
#     --sync-binlog=1 --binlog-expire-days=7
#
# 约定：
#   - 备份产物：${BACKUP_DIR}/kubo-YYYYmmdd-HHMMSS.sql.gz（mysqldump --single-transaction）
#   - 备份机/对象存储：${REMOTE_BACKUP}（rsync 目标或 oss/rclone 命令，运维填真实值）
#   - 保留：本地保留 ${RETENTION_DAYS} 天
#
# 用法：./backup-mysql.sh            # 仅本机备份
#       ./backup-mysql.sh --remote   # 额外推送到备份机/对象存储
#
# 排障：全量备份失败必须「响」——脚本非零退出，交给 cron 的 MAILTO 或监控告警。
# ============================================================================
set -euo pipefail

# ---- 可配置项（运维按真实拓扑改） ------------------------------------------
MYSQL_CONTAINER="${MYSQL_CONTAINER:-kubo-mysql}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-kubo}"
BACKUP_DIR="${BACKUP_DIR:-/backup/mysql}"
REMOTE_BACKUP="${REMOTE_BACKUP:-}"          # TODO(运维): 如 user@backup-host:/backup/ 或 oss://bucket/path
RETENTION_DAYS="${RETENTION_DAYS:-14}"
# ----------------------------------------------------------------------------

command -v docker >/dev/null 2>&1 || { echo "✗ 需要 docker"; exit 1; }

mkdir -p "${BACKUP_DIR}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FILE="${BACKUP_DIR}/kubo-${STAMP}.sql.gz"
TMP_DUMP="${BACKUP_DIR}/kubo-${STAMP}.sql"

log() { echo "[$(date +%F\ %T)] $*"; }

# 1. 全量备份（--single-transaction 热备无锁；--routines/--triggers/--events 保完整）
log "开始全量备份 ${MYSQL_DATABASE} …"
if ! docker exec "${MYSQL_CONTAINER}" sh -c \
  "mysqldump --single-transaction --quick --routines --triggers --events \
     -u'${MYSQL_USER}' -p'${MYSQL_PASSWORD}' '${MYSQL_DATABASE}'" > "${TMP_DUMP}"; then
  echo "✗ mysqldump 失败" >&2
  rm -f "${TMP_DUMP}"
  exit 1
fi

# 2. 压缩 + 校验（gzip 成功后删除未压缩文件，失败即报错）
gzip -9 "${TMP_DUMP}"
if ! gzip -t "${DUMP_FILE}"; then
  echo "✗ 压缩校验失败：${DUMP_FILE}" >&2
  exit 1
fi
log "备份完成：${DUMP_FILE} ($(du -h "${DUMP_FILE}" | cut -f1))"

# 3. 本地保留策略（超过保留天数删除）
find "${BACKUP_DIR}" -name 'kubo-*.sql.gz' -mtime +"${RETENTION_DAYS}" -delete 2>/dev/null || true

# 4. 推送备份机/对象存储（可选，--remote 时执行；失败必须响）
if [[ "${1:-}" == "--remote" ]]; then
  if [[ -z "${REMOTE_BACKUP}" ]]; then
    echo "✗ --remote 但 REMOTE_BACKUP 未配置（填入备份机地址或对象存储命令）" >&2
    exit 1
  fi
  log "推送备份到 ${REMOTE_BACKUP} …"
  # TODO(运维): 二选一
  #   rsync -az "${DUMP_FILE}" "${REMOTE_BACKUP}"
  #   rclone copy "${DUMP_FILE}" "${REMOTE_BACKUP}"
fi

log "备份流程完成"
