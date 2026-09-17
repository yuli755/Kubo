#!/usr/bin/env node
/**
 * 迁移脚本 MySQL 8 方言语法完整性门禁
 * ============================================================================
 * 为什么要有这个脚本：迁移脚本（SPMS-Server/src/main/resources/db/migration/*.sql，Flyway 版本化）用的是 MySQL 8 方言
 * （存储过程 + DELIMITER + information_schema + 动态 SQL PREPARE/EXECUTE），H2 那份
 * schema-test.sql 是手抄件、永远判不出方言问题。CI 的 Migrations job 用真实 MySQL 8
 * 跑，但沙箱既无 MySQL 也下不了镜像 —— 于是「DELIMITER 忘闭合」「存储过程没 END$$ 收尾」
 * 「CREATE TABLE 漏 IF NOT EXISTS」「PREPARE 漏 DEALLOCATE」这类**确定性错误**只能在
 * CI 或生产第一次执行时才炸。
 *
 * 本脚本把「不依赖数据库就能判定的语法完整性」提前到提交前：
 *   ① DELIMITER $$ / DELIMITER ; 必须成对、不得交叉、不得奇数；
 *   ② 每个存储过程 CREATE PROCEDURE 必须有 BEGIN、且以 END$$ 收尾（END$$ 数 = 过程数）；
 *   ③ 幂等性：CREATE TABLE 必须 IF NOT EXISTS；CREATE PROCEDURE 之前必须有对应 DROP PROCEDURE；
 *   ④ 动态 SQL：PREPARE 必须配 DEALLOCATE PREPARE（漏了会累积 prepared stmt）。
 *
 * 它**不替代** CI 的真实 MySQL 8 job（information_schema 反向核对列清单、连跑两遍判幂等
 * 仍然只有真库能判）；它拦的是「连真库都不用、读文件就能看出来的错」，让真库 job 只用来
 * 验证真正需要它验证的东西。
 *
 * 防空转：脚本内嵌自检（见 selfCheck），坏样本逐条判据反证，门禁失灵时 CI 立刻红。
 *
 * 用法：node infra/scripts/check-migration-dialect.mjs [--verbose]
 */
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
// ⚠️ 2026-09 的 S0 把手工 SQL 收编成了 Flyway 版本化迁移（`V1__baseline_schema.sql` … `V10__…`），
// 目录从 `docs/database/migration/` 移到了这里。旧路径下已无 `.sql`，本门禁会**空转通过**
// （0 个脚本 ⇒ 0 处错误）—— 这正是 2026-09 残留审计抓到的问题：门禁还在跑，脚本早没了。
// 所以下面除了改路径，还加了"一个脚本都没扫到就失败"的兜底。
const DDL_DIR = join(REPO, 'SPMS-Server', 'src', 'main', 'resources', 'db', 'migration')
const VERBOSE = process.argv.includes('--verbose')

/**
 * 对一份迁移脚本文本做方言完整性检查，返回错误描述数组（空 = 通过）。
 * 主流程与自检共用此入口，避免两边行为漂移。
 */
function checkDialect(name, src) {
  const errors = []
  const fail = (msg) => errors.push(`${name}: ${msg}`)

  // ① DELIMITER 配平：成对、奇数即错、$$ 开 ; 收
  const delimiters = []
  src.split('\n').forEach((line, i) => {
    const m = /^\s*DELIMITER\s+(\S+)\s*$/.exec(line)
    if (m) delimiters.push({ line: i + 1, delim: m[1] })
  })
  if (delimiters.length % 2 !== 0) {
    const last = delimiters.at(-1)
    fail(`DELIMITER 数量为奇数（${delimiters.length}），存在未闭合的 DELIMITER（最后是 \`${last.delim}\` @${last.line}）`)
  } else {
    for (let i = 0; i < delimiters.length; i += 2) {
      const open = delimiters[i]
      const close = delimiters[i + 1]
      if (open.delim === close.delim) {
        fail(`DELIMITER 未闭合：\`${open.delim}\` @${open.line} 配对的仍是 \`${close.delim}\` @${close.line}（应 $$ 开、; 收）`)
      }
      if (close.delim !== ';') {
        fail(`DELIMITER @${close.line} 应为 \`;\` 恢复默认，实际是 \`${close.delim}\``)
      }
    }
  }

  // ② 存储过程：CREATE PROCEDURE 必须有 BEGIN + END$$ 收尾；END$$ 数 = 过程数
  const procs = []
  {
    const re = /CREATE\s+PROCEDURE\s+([a-zA-Z0-9_]+)/g
    let m
    while ((m = re.exec(src)) !== null) {
      procs.push({ name: m[1], startIdx: m.index })
    }
  }
  for (const proc of procs) {
    const endMarker = src.indexOf('END$$', proc.startIdx)
    if (endMarker < 0) {
      fail(`存储过程 ${proc.name} 找不到 END$$ 收尾（可能 DELIMITER 未闭合）`)
    } else if (!/\bBEGIN\b/.test(src.slice(proc.startIdx, endMarker))) {
      fail(`存储过程 ${proc.name} 缺少 BEGIN（复合语句块起始）`)
    }
  }
  const endMarkers = (src.match(/END\$\$/g) || []).length
  if (endMarkers !== procs.length) {
    fail(`END$$ 数量（${endMarkers}）与存储过程数量（${procs.length}）不一致`)
  }

  // ③ 幂等性：CREATE TABLE 必须 IF NOT EXISTS；CREATE PROCEDURE 前必须有 DROP PROCEDURE
  {
    const re = /CREATE\s+TABLE\s+(?!IF\s+NOT\s+EXISTS)/gi
    let m
    while ((m = re.exec(src)) !== null) {
      const lineNo = src.slice(0, m.index).split('\n').length
      fail(`CREATE TABLE @${lineNo} 缺 IF NOT EXISTS（幂等性：脚本须可重复执行）`)
    }
  }
  for (const proc of procs) {
    const before = src.slice(0, proc.startIdx)
    const dropped = new RegExp(`DROP\\s+PROCEDURE\\s+(IF\\s+EXISTS\\s+)?${proc.name}\\b`).test(before)
    if (!dropped) {
      fail(`存储过程 ${proc.name} 创建前没有 DROP PROCEDURE（幂等性：重复执行会因已存在而报错）`)
    }
  }

  // ④ 动态 SQL 配平：PREPARE 与 DEALLOCATE PREPARE（DEALLOCATE 里的 PREPARE 不计入）
  {
    const prepareAll = (src.match(/\bPREPARE\b/g) || []).length
    const deallocs = (src.match(/\bDEALLOCATE\s+PREPARE\b/g) || []).length
    const prepares = prepareAll - deallocs
    if (prepares !== deallocs) {
      fail(`动态 SQL 配平失败：PREPARE ${prepares} 处 / DEALLOCATE PREPARE ${deallocs} 处（漏 DEALLOCATE 会累积 prepared stmt）`)
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
      name: 'DELIMITER $$ 忘了闭合回 ;',
      sql: 'DELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\n  SELECT 1;\nEND$$\n',
      expect: /DELIMITER 数量为奇数/,
    },
    {
      name: '存储过程缺 END$$ 收尾',
      sql: 'DROP PROCEDURE IF EXISTS p;\nDELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\n  SELECT 1;\nEND;\nDELIMITER ;\n',
      expect: /找不到 END\$\$ 收尾/,
    },
    {
      name: 'CREATE TABLE 漏 IF NOT EXISTS',
      sql: 'CREATE TABLE `foo` (\n  `id` BIGINT PRIMARY KEY\n);\n',
      expect: /缺 IF NOT EXISTS/,
    },
    {
      name: 'CREATE PROCEDURE 前无 DROP PROCEDURE',
      sql: 'DELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\n  SELECT 1;\nEND$$\nDELIMITER ;\n',
      expect: /创建前没有 DROP PROCEDURE/,
    },
    {
      name: 'PREPARE 漏 DEALLOCATE',
      sql: 'DROP PROCEDURE IF EXISTS p;\nDELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\n  SET @s = "SELECT 1";\n  PREPARE st FROM @s; EXECUTE st;\nEND$$\nDELIMITER ;\n',
      expect: /动态 SQL 配平失败/,
    },
    {
      name: '合法：DELIMITER 成对 + DROP 前置 + PREPARE/DEALLOCATE 配平',
      sql: 'DROP PROCEDURE IF EXISTS p;\nDELIMITER $$\nCREATE PROCEDURE p()\nBEGIN\n  SET @s = "SELECT 1";\n  PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;\nEND$$\nDELIMITER ;\nCREATE TABLE IF NOT EXISTS `foo` (`id` BIGINT PRIMARY KEY);\n',
      expect: null,
    },
  ]
  let checked = 0
  for (const c of cases) {
    checked++
    const errs = checkDialect('<self-test>', c.sql)
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
const sqlFiles = readdirSync(DDL_DIR).filter((f) => f.endsWith('.sql')).sort()
if (sqlFiles.length === 0) {
  console.error(`✗ ${DDL_DIR} 下没有 .sql —— 门禁空转（路径写错或脚本被搬走）。`)
  console.error('  宁可红一次，也不要"0 个脚本 ⇒ 0 处错误"的假绿。')
  process.exit(1)
}
const allErrors = []
for (const f of sqlFiles) {
  const src = readFileSync(join(DDL_DIR, f), 'utf-8')
  const errs = checkDialect(f, src)
  if (errs.length) allErrors.push(...errs)
  if (VERBOSE) {
    const procCount = (src.match(/CREATE\s+PROCEDURE\s+/g) || []).length
    const delimCount = (src.match(/^\s*DELIMITER\s+/gm) || []).length
    console.log(`  ${f}: ${procCount} 个存储过程 / ${delimCount} 个 DELIMITER / ${src.split('\n').length} 行`)
  }
}

const { problems, checked } = selfCheck()
if (problems.length > 0) {
  console.error('\n✗ check-migration-dialect 自检失败：门禁本身不可信，先修门禁再谈迁移脚本')
  problems.forEach((p) => console.error(`  ${p}`))
  process.exit(1)
}

if (allErrors.length > 0) {
  console.error(`✗ 迁移脚本方言语法完整性 ${allErrors.length} 处错误（${sqlFiles.length} 个脚本）：`)
  allErrors.forEach((e) => console.error(`  · ${e}`))
  process.exit(1)
}

console.log(`✓ 迁移方言静态检查通过：${sqlFiles.length} 个脚本，DELIMITER/存储过程/幂等性/动态 SQL 配平均正确；门禁自检 ${checked} 例通过`)
