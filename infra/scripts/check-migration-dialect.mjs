#!/usr/bin/env node
/**
 * 数据库脚本门禁（MySQL 8 方言 + 幂等 + "只有两个文件"）
 * ============================================================================
 * 2026-09-18 起，全仓只有两个数据库脚本，住在 `infra/init-sql/`：
 *
 *     init.sql       结构（全部 CREATE TABLE IF NOT EXISTS）
 *     init_data.sql  数据（默认租户 / 系统权限身份 / 根角色与绑定 / 归属列回填）
 *
 * 口径变了，判据也得跟着变。**Flyway 已退场**：不再有 V<n>__ 版本化迁移、
 * 不再有 DELIMITER/存储过程/information_schema 探测 + 动态 SQL 的那套幂等技巧
 * （历史脚本为了"在已有库上重复执行"必须写存储过程，现在结构由 init.sql 一次
 * 建全，幂等靠 `IF NOT EXISTS` 与 `WHERE NOT EXISTS` 就够了）。
 *
 * 本脚本拦的是「不连库、读文件就能看出来的错」：
 *   ① 目录里**恰好两个** .sql，且名字就是 init.sql / init_data.sql
 *      —— "只保留这两个文件"是用户的硬规则，这里让它变成机器判据；
 *   ② 防空转：init.sql 至少 30 张表、init_data.sql 至少 1 条 INSERT；
 *   ③ 幂等：CREATE TABLE 必须 IF NOT EXISTS；INSERT 必须自带幂等守卫
 *      （WHERE NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY UPDATE）；
 *   ④ 安全：初始化脚本里禁止 DROP / TRUNCATE / DELETE / REPLACE INTO
 *      —— 建库脚本不该删数据（历史 DELETE 是"菜单路径规范化的存量脏数据"专用，随 V10 退役）；
 *   ⑤ 方言：禁止 DELIMITER / CREATE PROCEDURE / PREPARE / RUNSCRIPT
 *      （Flyway 时代的存量写法，新脚本一律不许出现）；
 *   ⑥ 结构规范：每张表都要 ENGINE = InnoDB + utf8mb4。
 *
 * 它**不替代** CI 的 Migrations job（用真实 MySQL 8 空库跑两遍、information_schema
 * 反查列清单）：真库能判的（能不能执行、是否真幂等）它判不了，它判的是提交前就能
 * 看见的确定性错误。
 *
 * 防空转：脚本内嵌自检（见 selfCheck），坏样本逐条反证、好样本确认不误伤；
 * 门禁自己失灵时 CI 立刻红。
 *
 * 用法：node infra/scripts/check-migration-dialect.mjs [--verbose]
 */
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const DDL_DIR = join(REPO, 'infra', 'init-sql')
const SCHEMA_FILE = 'init.sql'
const DATA_FILE = 'init_data.sql'
const VERBOSE = process.argv.includes('--verbose')

/** 幂等守卫：INSERT 必须带其中一种（`AND NOT EXISTS` 与 `WHERE NOT EXISTS` 都算） */
const IDEMPOTENT_GUARD = /NOT\s+EXISTS|INSERT\s+IGNORE|ON\s+DUPLICATE\s+KEY\s+UPDATE/i
/** 不许出现在建库脚本里的危险语句 / Flyway 时代写法 */
const BANNED_STATEMENTS = [
  { re: /^\s*DROP\b/im, why: 'DROP（建库脚本不该删对象）' },
  { re: /^\s*TRUNCATE\b/im, why: 'TRUNCATE（建库脚本不该清数据）' },
  { re: /^\s*DELETE\b/im, why: 'DELETE（建库脚本不该删数据）' },
  { re: /^\s*REPLACE\s+INTO\b/im, why: 'REPLACE INTO（会隐式删行，改用 INSERT ... WHERE NOT EXISTS）' },
  { re: /^\s*DELIMITER\b/im, why: 'DELIMITER（Flyway 时代存储过程写法）' },
  { re: /^\s*CREATE\s+PROCEDURE\b/im, why: 'CREATE PROCEDURE（改用 IF NOT EXISTS / WHERE NOT EXISTS）' },
  { re: /^\s*PREPARE\b/im, why: 'PREPARE 动态 SQL（同上）' },
  { re: /RUNSCRIPT/i, why: 'RUNSCRIPT（H2 专有语法）' },
]

/**
 * 对一份脚本文本做检查，返回错误描述数组（空 = 通过）。
 * 主流程与自检共用此入口，避免两边行为漂移。
 */
function checkScript(name, src) {
  const errors = []
  const fail = (msg) => errors.push(`${name}: ${msg}`)

  // ① 危险语句 / 退役写法
  for (const { re, why } of BANNED_STATEMENTS) {
    const m = re.exec(src)
    if (m) {
      const line = src.slice(0, m.index).split('\n').length
      fail(`出现禁止的写法：${why}（第 ${line} 行）`)
    }
  }

  // ② CREATE TABLE 必须 IF NOT EXISTS + 必须声明存储引擎与字符集
  const creates = [...src.matchAll(/^\s*CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?/gim)]
  for (const m of creates) {
    if (!m[1]) {
      fail(`CREATE TABLE \`${m[2]}\` 缺 IF NOT EXISTS —— 重复执行会报 1050，脚本不再幂等`)
    }
  }
  for (const m of src.matchAll(/CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`(\w+)`\s*\(([\s\S]*?)\n\)\s*ENGINE([^;]*);/gi)) {
    const tail = `ENGINE${m[3]}`
    if (!/utf8mb4/i.test(tail)) {
      fail(`表 \`${m[1]}\` 未声明 utf8mb4 字符集（中文注释会变问号，且与其余表不一致）`)
    }
  }

  // ③ INSERT 必须自带幂等守卫
  const inserts = [...src.matchAll(/^\s*INSERT\s+(?:IGNORE\s+)?INTO\b[\s\S]*?;/gim)]
  for (const m of inserts) {
    if (!IDEMPOTENT_GUARD.test(m[0])) {
      const line = src.slice(0, m.index).split('\n').length
      fail(`INSERT（第 ${line} 行）没有幂等守卫：加 WHERE NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY UPDATE`)
    }
  }

  // ④ 防空转（文件级：至少一条可执行语句）
  const statements = src.split('\n').filter(l => l.trim() && !l.trim().startsWith('--'))
  if (!statements.length) {
    fail('文件里没有任何可执行语句（只剩注释）')
  }
  return errors
}

/** 目录级判据：恰好两个脚本、名字正确、分工正确 */
function checkDirectory(files, texts) {
  const errors = []
  const sqlFiles = files.filter(f => f.endsWith('.sql')).sort()
  if (sqlFiles.join(',') !== [DATA_FILE, SCHEMA_FILE].sort().join(',')) {
    errors.push(`${join('infra', 'init-sql')} 下的 .sql 必须恰好是 ${SCHEMA_FILE} + ${DATA_FILE}`
      + `（当前：${sqlFiles.join(', ') || '（无）'}）—— "全仓只有两个数据库脚本"是硬规则，`
      + `新表/新列/新数据都改这两个文件，不要新增迁移文件`)
  }
  const schema = texts.get(SCHEMA_FILE) || ''
  const data = texts.get(DATA_FILE) || ''
  const tableCount = [...schema.matchAll(/CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS/gi)].length
  if (tableCount < 30) {
    errors.push(`${SCHEMA_FILE} 只解析到 ${tableCount} 张表（< 30）—— 解析失效或文件被截断，别让门禁空转`)
  }
  const insertCount = [...data.matchAll(/^\s*INSERT\s+(?:IGNORE\s+)?INTO\b/gim)].length
  if (insertCount < 1) {
    errors.push(`${DATA_FILE} 里没有 INSERT —— 要么解析失效，要么数据被搬走了（别让门禁空转）`)
  }
  const backfill = [...data.matchAll(/^\s*UPDATE\s+`(\w+)`/gim)].map(m => m[1])
  const known = new Set([...schema.matchAll(/CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`(\w+)`/gi)].map(m => m[1]))
  for (const t of backfill) {
    if (!known.has(t)) {
      errors.push(`${DATA_FILE} 回填了 \`${t}\`，但 ${SCHEMA_FILE} 里没有这张表 —— 脚本会 1146 报错`)
    }
  }
  return errors
}

/**
 * 门禁自检：证明每条判据真的能咬人，且不误伤合法写法。
 * 返回检查过的样本数；有问题直接打印并返回 null。
 */
function selfCheck() {
  const problems = []
  const cases = [
    { name: '合法脚本', src: 'CREATE TABLE IF NOT EXISTS `t` (\n  `id` bigint\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;\n', expect: null },
    { name: 'CREATE TABLE 缺 IF NOT EXISTS', src: 'CREATE TABLE `t` (\n  `id` bigint\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;', expect: 'IF NOT EXISTS' },
    { name: '表缺字符集', src: 'CREATE TABLE IF NOT EXISTS `t` (\n  `id` bigint\n) ENGINE = InnoDB;', expect: 'utf8mb4' },
    { name: '裸 INSERT（无幂等守卫）', src: 'INSERT INTO `t` (`id`) VALUES (1);', expect: '幂等守卫' },
    { name: '带 WHERE NOT EXISTS 的 INSERT', src: 'INSERT INTO `t` (`id`)\nSELECT 1\nWHERE NOT EXISTS (SELECT 1 FROM `t` WHERE `id` = 1);', expect: null },
    { name: 'INSERT IGNORE', src: 'INSERT IGNORE INTO `t` (`id`) VALUES (1);', expect: null },
    { name: 'DROP TABLE 残留', src: 'DROP TABLE IF EXISTS `t`;\nCREATE TABLE IF NOT EXISTS `t` (\n `id` bigint\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;', expect: 'DROP' },
    { name: 'DELETE 残留', src: 'DELETE FROM `t` WHERE `id` = 1;', expect: 'DELETE' },
    { name: 'REPLACE INTO', src: 'REPLACE INTO `t` (`id`) VALUES (1);', expect: 'REPLACE' },
    { name: 'DELIMITER / 存储过程（Flyway 时代写法）', src: 'DELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\nEND$$\nDELIMITER ;', expect: 'DELIMITER' },
    { name: 'PREPARE 动态 SQL', src: 'SET @s = "SELECT 1";\nPREPARE stmt FROM @s;', expect: 'PREPARE' },
    { name: '空文件', src: '-- 只有注释\n', expect: '没有任何可执行语句' },
  ]
  for (const c of cases) {
    const errs = checkScript('自检', c.src)
    if (c.expect === null) {
      if (errs.length) {
        problems.push(`自检：「${c.name}」被误判为错误（${errs.join(' / ')}）`)
      }
    } else if (!errs.some(e => e.includes(c.expect))) {
      problems.push(`自检：「${c.name}」没有被检出（期望包含「${c.expect}」，实际 ${errs.join(' / ') || '（无错误）'}）`)
    }
  }

  // 目录级自检：少一个文件 / 多一个文件都要咬人
  const dirCases = [
    { name: '只有 init.sql', files: ['init.sql'], want: 1 },
    { name: '多了一个迁移文件', files: ['init.sql', 'init_data.sql', 'V11__x.sql'], want: 1 },
    { name: '恰好两个', files: ['init.sql', 'init_data.sql'], want: 0 },
  ]
  const okTexts = new Map([
    ['init.sql', 'CREATE TABLE IF NOT EXISTS `user` (\n `id` bigint\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;\n'
      + Array.from({ length: 30 }, (_, i) => `CREATE TABLE IF NOT EXISTS \`t${i}\` (\n \`id\` bigint\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;`).join('\n')],
    ['init_data.sql', 'INSERT INTO `user` (`id`) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE `id` = 1);'],
  ])
  for (const c of dirCases) {
    const errs = checkDirectory(c.files, okTexts)
    if (errs.length !== c.want) {
      problems.push(`自检（目录级）：「${c.name}」期望 ${c.want} 条错误，实际 ${errs.length} 条（${errs.join(' / ') || '（无）'}）`)
    }
  }
  const badBackfill = new Map(okTexts)
  badBackfill.set('init_data.sql', 'UPDATE `ghost` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL;')
  if (!checkDirectory(['init.sql', 'init_data.sql'], badBackfill).length) {
    problems.push('自检（目录级）：回填了脚本里不存在的表却没被检出')
  }

  if (problems.length) {
    console.error('✗ check-migration-dialect 自检失败：门禁本身不可信，先修门禁再谈脚本')
    problems.forEach(p => console.error(`   · ${p}`))
    return null
  }
  return cases.length + dirCases.length + 1
}

const checked = selfCheck()
if (checked === null) {
  process.exit(1)
}

let files
try {
  files = readdirSync(DDL_DIR).sort()
} catch {
  console.error(`✗ 读不到 ${DDL_DIR} —— 目录被删了？两个脚本必须住在这里（init.sql / init_data.sql）`)
  process.exit(1)
}
const texts = new Map(files.filter(f => f.endsWith('.sql')).map(f => [f, readFileSync(join(DDL_DIR, f), 'utf8')]))

const errors = [...checkDirectory(files, texts)]
for (const [name, src] of texts) {
  errors.push(...checkScript(name, src))
  if (VERBOSE) {
    const tables = [...src.matchAll(/CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`(\w+)`/gi)].length
    const inserts = [...src.matchAll(/^\s*INSERT\s+(?:IGNORE\s+)?INTO\b/gim)].length
    const updates = [...src.matchAll(/^\s*UPDATE\s+`\w+`/gim)].length
    console.log(`   · ${name}: CREATE TABLE ${tables} · INSERT ${inserts} · UPDATE ${updates} · ${src.split('\n').length} 行`)
  }
}

if (errors.length) {
  console.error('\n✗ 数据库脚本检查失败：')
  errors.forEach(e => console.error(`   · ${e}`))
  process.exit(1)
}
const schemaTables = [...(texts.get(SCHEMA_FILE) || '').matchAll(/CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS/gi)].length
console.log(`✓ 数据库脚本检查通过：2 个脚本（${SCHEMA_FILE} ${schemaTables} 张表 / ${DATA_FILE}），`
  + '幂等守卫、危险语句、Flyway 时代写法、字符集全部合规；门禁自检 ' + checked + ' 例通过')
