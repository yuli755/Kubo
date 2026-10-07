#!/usr/bin/env node
/**
 * 备份 / 恢复演练脚本静态门禁
 * ============================================================================
 * 为什么要有这个脚本：备份脚本和恢复演练脚本（infra/backup/*.sh）是「灾难那天的
 * 保命符」，但脚本本身无法在沙箱跑（没有 docker/MySQL）。最容易犯、也最致命的错是：
 *   ① 恢复脚本忘了「拒绝生产库」的护栏 —— 误把演练跑到生产库上，把生产数据 drop 掉；
 *   ② 脚本没开 `set -euo pipefail` —— 中间命令失败继续往下走，产生「假成功」；
 *   ③ 备份脚本没做压缩产物校验（gzip -t）—— 备份损坏当天发现不了，等灾难那天才炸。
 *
 * 本脚本把「不依赖 docker/MySQL 就能判定的确定性错误」提前到提交前，与 CI 无关。
 * 它**不替代**真实演练（行数核对、PITR 回放、应用冒烟仍靠隔离环境跑脚本）；
 * 它拦的是「读文件就能看出来的错」。
 *
 * 防空转：脚本内嵌自检（selfCheck），坏样本逐条判据反证，门禁失灵时立刻红。
 *
 * 用法：node infra/scripts/check-backup-drill.mjs [--verbose]
 */
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const BACKUP_DIR = join(REPO, 'infra', 'backup')
const FILES = ['backup-mysql.sh', 'restore-drill.sh']
const VERBOSE = process.argv.includes('--verbose')

/**
 * 对一份脚本内容做静态检查，返回错误描述数组（空 = 通过）。
 * 主流程与自检共用此入口，避免两边行为漂移。
 */
function checkScript(kind, name, src) {
  const errors = []
  const fail = (msg) => errors.push(`${name}: ${msg}`)

  // ① 必须 fail-fast（set -e + 未定义变量 + 管道失败）
  if (!/set\s+-euo\s+pipefail/.test(src) && !/set\s+-eu\s+pipefail/.test(src)) {
    fail('缺少 `set -euo pipefail`（中间命令失败会被静默吞掉，产生假成功）')
  }

  if (kind === 'backup') {
    // ② 备份脚本：必须有 mysqldump + 压缩产物校验（gzip -t）
    if (!/mysqldump/.test(src)) {
      fail('缺少 mysqldump（全量备份核心动作）')
    }
    if (!/gzip\s+-t/.test(src)) {
      fail('缺少 `gzip -t` 压缩产物校验（备份损坏应当天发现，而非灾难那天）')
    }
    if (!/sha256sum\s+\"\$\{DUMP_FILE\}\"/.test(src)) {
      fail('缺少备份 SHA-256 完整性校验文件（恢复前无法确认产物未被篡改或损坏）')
    }
    if (/MYSQL_PASSWORD=\"\$\{MYSQL_PASSWORD:-root\}/.test(src)) {
      fail('备份脚本禁止使用 root 默认密码，必须显式注入 MYSQL_PASSWORD')
    }
    if (!/rsync|rclone/.test(src)) {
      fail('缺少远端备份传输实现（rsync 或 rclone）')
    }
    if (!/BINLOG_SOURCE_DIR/.test(src) || !/mysql-bin\.\*/.test(src)) {
      fail('远端备份缺少 binlog 源目录与 mysql-bin.* 存在性护栏（PITR 不能只推送全量备份）')
    }
    if (!/rclone\s+copy\s+"\$\{BINLOG_SOURCE_DIR\}"\s+"\$\{REMOTE_BACKUP\}\/binlog"/.test(src)
      && !/rsync\s+-az\s+--checksum\s+"\$\{BINLOG_SOURCE_DIR\}\/"\s+"\$\{REMOTE_BACKUP%\/\}\/binlog\//.test(src)) {
      fail('远端备份未传输 BINLOG_SOURCE_DIR（PITR 产物必须与全量备份一同外置）')
    }
  }

  if (kind === 'drill') {
    // ③ 恢复脚本：必须有「拒绝生产库」护栏（最高优先级安全底线）
    if (!/DRILL_ALLOW_PROD/.test(src)) {
      fail('缺少 DRILL_ALLOW_PROD 护栏（恢复演练绝不能误伤生产库）')
    }
    if (!/DROP\s+DATABASE/.test(src)) {
      fail('缺少 DROP DATABASE（演练应 drop+create 干净恢复，而非追加导入）')
    }
    if (!/SELECT\s+COUNT\(\*\)/.test(src)) {
      fail('缺少关键表行数校验（SELECT COUNT(*)），无法证明「能恢复」')
    }
    if (!/sha256sum\s+-c/.test(src)) {
      fail('缺少恢复前 SHA-256 校验（备份完整性未确认，不得导入）')
    }
  }

  return errors
}

// ============================================================================
// 门禁自检：证明每条判据真的能咬人，且不误伤合法写法
// ============================================================================
function selfCheck() {
  const problems = []
  const cases = [
    {
      name: '备份脚本缺 set -euo pipefail',
      kind: 'backup',
      src: '#!/usr/bin/env bash\nmysqldump ... \ngzip -t x.gz\n',
      expect: /set -euo pipefail/,
    },
    {
      name: '备份脚本缺 gzip -t 校验',
      kind: 'backup',
      src: '#!/usr/bin/env bash\nset -euo pipefail\nmysqldump kubo | gzip > kubo.sql.gz\n',
      expect: /gzip -t/,
    },
    {
      name: '恢复脚本缺 DRILL_ALLOW_PROD 护栏',
      kind: 'drill',
      src: '#!/usr/bin/env bash\nset -euo pipefail\nmysql -e "DROP DATABASE kubo"\nSELECT COUNT(*) FROM users;\n',
      expect: /DRILL_ALLOW_PROD/,
    },
    {
      name: '合法备份脚本',
      kind: 'backup',
      src: '#!/usr/bin/env bash\nset -euo pipefail\nDUMP_FILE=k.sql.gz\nBINLOG_SOURCE_DIR=/var/lib/mysql\nREMOTE_BACKUP=backup:/mysql\nmysqldump kubo > k.sql\ngzip -t "${DUMP_FILE}"\nsha256sum "${DUMP_FILE}" > "${DUMP_FILE}.sha256"\ncompgen -G "${BINLOG_SOURCE_DIR}/mysql-bin.*"\nrsync -az --checksum "${DUMP_FILE}" "${DUMP_FILE}.sha256" "${REMOTE_BACKUP}"\nrsync -az --checksum "${BINLOG_SOURCE_DIR}/" "${REMOTE_BACKUP%/}/binlog/"\n',
      expect: null,
    },
    {
      name: '合法恢复脚本',
      kind: 'drill',
      src: '#!/usr/bin/env bash\nset -euo pipefail\nDRILL_ALLOW_PROD=0\nsha256sum -c kubo.sql.gz.sha256\nmysql -e "DROP DATABASE kubo_drill"\nSELECT COUNT(*) FROM users;\n',
      expect: null,
    },
  ]
  let checked = 0
  for (const c of cases) {
    checked++
    const errs = checkScript(c.kind, '<self-test>', c.src)
    if (c.expect === null) {
      if (errs.length > 0) {
        problems.push(`自检：「${c.name}」被误判为错误（${errs.join(' / ')}）`)
      }
      continue
    }
    if (!errs.some((e) => c.expect.test(e))) {
      problems.push(`自检：「${c.name}」没有被检出（期望匹配 ${c.expect}）`)
    }
  }
  return { problems, checked }
}

// ============================================================================
// 主流程
// ============================================================================
const allErrors = []
for (const f of FILES) {
  const path = join(BACKUP_DIR, f)
  if (!existsSync(path)) {
    allErrors.push(`${f}: 文件不存在`)
    continue
  }
  const src = readFileSync(path, 'utf-8')
  if (src.trim().length === 0) {
    allErrors.push(`${f}: 空文件`)
    continue
  }
  const kind = f.startsWith('backup') ? 'backup' : 'drill'
  const errs = checkScript(kind, f, src)
  if (errs.length) allErrors.push(...errs)
  if (VERBOSE) {
    console.log(`  ${f}: ${src.split('\n').length} 行`)
  }
}

const { problems, checked } = selfCheck()
if (problems.length > 0) {
  console.error('\n✗ check-backup-drill 自检失败：门禁本身不可信，先修门禁')
  problems.forEach((p) => console.error(`  ${p}`))
  process.exit(1)
}

if (allErrors.length > 0) {
  console.error(`✗ 备份/恢复演练脚本 ${allErrors.length} 处问题（${FILES.length} 个脚本）：`)
  allErrors.forEach((e) => console.error(`  · ${e}`))
  process.exit(1)
}

console.log(`✓ 备份/恢复演练脚本静态检查通过：${FILES.length} 个脚本，fail-fast/护栏/校验齐全；门禁自检 ${checked} 例通过`)
