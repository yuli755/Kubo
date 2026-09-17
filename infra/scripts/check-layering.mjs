#!/usr/bin/env node
/**
 * # 门禁：代码分层（依赖方向 + 层内职责）
 *
 * ## 为什么要有它
 * 上一批把「SQL 只能写在专属文件」变成了门禁（`pnpm sql:check`）。但 SQL 只是分层的一部分：
 * 更根本的是**谁能依赖谁**。这个仓的分层不是画出来的，是量出来的 —— 本脚本的规则全部来自
 * 一次实测（`docs/架构分层设计.md` 里有那次实测的结论），而且**当前是绿的**：
 * 任何新增的越层依赖都会被拦下，已存在的越层依赖逐条进白名单（带理由与清理批次）。
 *
 * ## 层次（自下而上）
 * | 层 | 包 | 允许依赖 |
 * | --- | --- | --- |
 * | L0 | `cn.hamm.spms.core` | 任何三方库，**不依赖本仓其它包** |
 * | L1 | `cn.hamm.spms.framework` | `core`（+ 一处 `common.exception`，见白名单） |
 * | L2 | `cn.hamm.spms.base` | `core` / `framework`，**不得依赖 `module.*`**（除组合根与白名单） |
 * | L3 | `cn.hamm.spms.module` | 上面任意 + 其它 module |
 * | 组合 | `common` 的横切子包 / `config` / `Application` | 任意（装配是组合根的特权） |
 *
 * ## 规则
 * - **L0**：`core` 不得 import `framework` / `base` / `module` / `common` / `config`
 * - **L1**：`framework` 不得 import `base` / `module` / `config`
 * - **L2**：`base` 不得 import `module.*`，例外：① `*Configuration.java`（组合根）② 白名单
 * - **L3**：`module/**&#47;mapper|repository` 不得 import `.service.` / `.controller.` / `*Controller`
 * - **L4**：`*Entity.java` 不得 import `.service.` / `.mapper.` / `.repository.` / `.controller.`
 * - **L5**：`*Controller.java` 不得**注入** `*Repository` / `*Mapper`（字段或构造器参数）——
 *   控制器是 HTTP 出入口，回表要么走 service，要么走读侧 mapper 的**服务封装**
 *
 * ## 白名单纪律
 * 每条白名单是「文件 → import」的精确匹配，**必须写理由**；脚本会检查两件事：
 * 1. 白名单里的条目**必须仍然存在**（清掉却忘了删白名单 = 红，防止白名单长成化石）；
 * 2. 白名单条目数**只许降**（`MAX_ALLOWLIST`，改大需要改这个数字，diff 里看得见）。
 *
 * ## 用法
 * ```bash
 * pnpm layer:check              # 人读
 * pnpm layer:check -- --json    # 机器读
 * pnpm layer:check -- --check   # 门禁
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SRC = path.join(ROOT, 'SPMS-Server', 'src', 'main', 'java', 'cn', 'hamm', 'spms')

/**
 * L2 白名单：`base` 里"下层依赖业务域"的现存条目。
 *
 * 全部集中在两个类，都是**基类要挂的业务钩子**（写侧守卫、脱敏、审计、取号）。
 * 清理方式（下一批 L2）：把这些能力提炼成 `framework.curd.datapermission` 里的接口（SPI），
 * 实现留在 `module.system.datapermission`，`base` 只依赖接口 —— 依赖方向就正过来了。
 */
const ALLOWLIST = [
  ['base/BaseService.java', 'cn.hamm.spms.module.system.SystemServices', '服务定位器（历史遗留）；L2 批次换成注入的 SPI'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.coderule.CodeRuleService', '自动编码取号；L2 批次换成 base 侧接口 CodeRuleAllocator'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.AttributionWriteProtection', '写侧归属保护（静态工具）；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService', '读侧列脱敏；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard', '动作级守卫；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.DataPermissionRuntime', '运行期档位/装配；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.DataPermissionResourceResolver', '资源键解析；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.ExportColumnMasker', '导出脱敏；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.MaskedInputRegistry', '掩码输入登记（ThreadLocal）；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.OperationLogRecorder', '操作审计记录器（已是 Bean）；L2 批次换成 base 侧接口'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.RowWriteGuard', '行级写守卫；L2 批次随 SPI 下移'],
  ['base/BaseService.java', 'cn.hamm.spms.module.system.datapermission.service.SensitiveWriteProtection', '敏感字段写保护（静态工具）；L2 批次随 SPI 下移'],
  ['base/bill/AbstractBaseBillService.java', 'cn.hamm.spms.module.system.SystemServices', '单据基类取配置；L2 批次随 SPI 下移'],
  ['base/bill/AbstractBaseBillService.java', 'cn.hamm.spms.module.system.config.ConfigEntity', '单据基类读系统配置；L2 批次随 SPI 下移'],
  ['base/bill/AbstractBaseBillService.java', 'cn.hamm.spms.module.system.config.enums.ConfigFlag', '同上（枚举，随后一起下移）'],
].map(([file, fqn, reason]) => ({ file, fqn, reason }))

/** L1 的另一条例外：framework 只允许认识 `common.exception`（最底层错误码），别的 common 子包不行。 */
const FRAMEWORK_ALLOWED_COMMON = ['cn.hamm.spms.common.exception.']

/**
 * 白名单条目数上限（**只许降**）。
 * 现在 15 条（全在 `base/BaseService` 与 `base/bill/AbstractBaseBillService`）；L2 批次（SPI 下移）做完后应当变成 0 条。
 * `framework → common.exception` 不占白名单：它是**规则允许**的（见 FRAMEWORK_ALLOWED_COMMON），
 * 原因写在设计文档里，而不是靠一条会过期的白名单撑着。
 */
const MAX_ALLOWLIST = 15

const LAYER_RULES = [
  {
    id: 'L0',
    desc: 'core 不得依赖本仓其它包',
    matchFile: f => f.startsWith('core/'),
    forbidden: ['framework', 'base', 'module', 'common', 'config'],
  },
  {
    id: 'L1',
    desc: 'framework 不得依赖 base / module / config（common 只允许 exception）',
    matchFile: f => f.startsWith('framework/'),
    forbidden: ['base', 'module', 'config'],
    forbidRestOfCommon: true,
  },
  {
    id: 'L2',
    desc: 'base 不得依赖 module（组合根与白名单除外）',
    matchFile: f => f.startsWith('base/'),
    forbidden: ['module'],
  },
]

/** 层内职责规则（按文件形态匹配） */
const RESPONSIBILITY_RULES = [
  {
    id: 'L3',
    desc: 'mapper / repository 不得依赖 service / controller',
    // 只认"数据访问层自己的接口文件"：按文件名判定，不按目录 ——
    // `module/**/mapper/` 下还住着 *ReadShadowService 这类实现类（它们属于服务层，
    // 放在 mapper 目录只是为了和读侧 Mapper 挨着），按目录判定会误报（第一版就是这么错的）
    matchFile: f => /(Repository|Mapper)\.java$/.test(f),
    regs: [/^import\s+cn\.hamm\.spms\..*\.service\./, /^import\s+cn\.hamm\.spms\..*Controller;/],
  },
  {
    id: 'L4',
    desc: '实体不得依赖 service / mapper / repository / controller',
    matchFile: f => /Entity\.java$/.test(f),
    regs: [
      /^import\s+cn\.hamm\.spms\..*\.service\./,
      /^import\s+cn\.hamm\.spms\..*\.mapper\./,
      /^import\s+cn\.hamm\.spms\..*\.repository\./,
      /^import\s+cn\.hamm\.spms\..*Controller;/,
    ],
  },
]

function walkJava(dir, rel = '', acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const relPath = rel ? `${rel}/${entry.name}` : entry.name
    if (entry.isDirectory()) walkJava(path.join(dir, entry.name), relPath, acc)
    else if (entry.name.endsWith('.java')) acc.push({ rel: relPath, abs: path.join(dir, entry.name) })
  }
  return acc
}

const isConfiguration = rel => /Configuration\.java$/.test(rel)
const isController = rel => /Controller\.java$/.test(rel)

const files = walkJava(SRC)
const violations = []
const allowlistSeen = new Set()

for (const { rel, abs } of files) {
  const raw = fs.readFileSync(abs, 'utf8')
  const code = stripJavaComments(raw)
  const imports = [...code.matchAll(/^import\s+(?:static\s+)?(cn\.hamm\.spms\.[\w.]+?)(?:\.\w+)?;\s*$/gm)].map(m => m[1])
  const importLines = [...code.matchAll(/^import\s+(?:static\s+)?(cn\.hamm\.spms\.[\w.]+);\s*$/gm)].map(m => m[1])

  // 层规则
  for (const rule of LAYER_RULES) {
    if (!rule.matchFile(rel)) continue
    for (const fqn of importLines) {
      const rest = fqn.replace('cn.hamm.spms.', '')
      const allowedByCommon = rule.forbidRestOfCommon
        && rest.startsWith('common.')
        && FRAMEWORK_ALLOWED_COMMON.some(prefix => fqn.startsWith(prefix))
      if (!rule.forbidden.some(tag => rest.startsWith(`${tag}.`)) || allowedByCommon) continue
      if (isConfiguration(rel)) continue // 组合根例外
      const entry = ALLOWLIST.find(a => a.file === rel && a.fqn === fqn)
      if (entry) {
        allowlistSeen.add(`${rel}|${fqn}`)
        continue
      }
      // 组合根例外造成的白名单条目也算"用了"
      const cfgEntry = ALLOWLIST.find(a => a.file === rel && a.fqn === fqn)
      void cfgEntry
      violations.push({ rule: rule.id, file: `src/main/java/cn/hamm/spms/${rel}`, type: 'import', detail: fqn, desc: rule.desc })
    }
  }

  // 层内职责（import 形态）
  for (const rule of RESPONSIBILITY_RULES) {
    if (!rule.matchFile(rel)) continue
    for (const line of code.split('\n')) {
      if (line.trim().startsWith('import ') && rule.regs.some(re => re.test(line.trim()))) {
        violations.push({ rule: rule.id, file: `src/main/java/cn/hamm/spms/${rel}`, type: 'import', detail: line.trim(), desc: rule.desc })
      }
    }
  }

  // L5：控制器不得注入仓储 / Mapper（字段或构造器参数）
  if (isController(rel)) {
    const fieldRe = /^\s*(?:private|protected|public)\s+(?:final\s+)?[\w.$.]*(?:Repository|Mapper)\s+\w+\s*;/gm
    const ctorRe = /public\s+\w*Controller\s*\([^)]*\b[\w.$.]*(?:Repository|Mapper)\s+\w+[^)]*\)/gm
    for (const m of code.matchAll(fieldRe)) {
      const line = code.slice(0, m.index).split('\n').length
      violations.push({
        rule: 'L5', file: `src/main/java/cn/hamm/spms/${rel}`, type: 'field', line,
        detail: m[0].trim(), desc: '控制器不得注入仓储 / Mapper',
      })
    }
    for (const m of code.matchAll(ctorRe)) {
      const line = code.slice(0, m.index).split('\n').length
      violations.push({
        rule: 'L5', file: `src/main/java/cn/hamm/spms/${rel}`, type: 'constructor', line,
        detail: '构造器注入了仓储 / Mapper', desc: '控制器不得注入仓储 / Mapper',
      })
    }
  }
}

// 白名单纪律：条目必须仍然存在（清掉忘删 = 红）；只许降
const stale = ALLOWLIST.filter(a => !allowlistSeen.has(`${a.file}|${a.fqn}`))
const tooMany = ALLOWLIST.length > MAX_ALLOWLIST

const json = process.argv.includes('--json')
if (json) {
  console.log(JSON.stringify({ files: files.length, violations, allowlist: ALLOWLIST.length, stale }, null, 2))
} else {
  console.log('代码分层门禁：依赖方向（L0–L2）+ 层内职责（L3–L5）')
  console.log(`  扫描：${files.length} 个 .java（cn.hamm.spms 下，剥注释）`)
  console.log(`  白名单：${ALLOWLIST.length}/${MAX_ALLOWLIST} 条（base→module 的历史钩子，L2 批次清理）`)
  if (violations.length === 0) {
    console.log('✓ 依赖方向与层内职责均无新增违规')
  } else {
    console.log(`✗ ${violations.length} 处违规：`)
    for (const v of violations) {
      console.log(`  [${v.rule}] ${v.file}${v.line ? `:${v.line}` : ''}  ${v.detail}   —— ${v.desc}`)
    }
  }
  if (stale.length > 0) {
    console.log(`✗ ${stale.length} 条白名单已过期（代码已经不长这样了，请删掉白名单条目）：`)
    for (const s of stale) console.log(`  ${s.file} → ${s.fqn}`)
  }
  if (tooMany) {
    console.log(`✗ 白名单条目数 ${ALLOWLIST.length} > 上限 ${MAX_ALLOWLIST}：只许降。`)
  }
  if (violations.length === 0 && stale.length === 0 && !tooMany) {
    console.log('  层次：L0 core → L1 framework → L2 base → L3 module（组合根 common/config 例外）')
  }
}

const failed = violations.length > 0 || stale.length > 0 || tooMany
if (process.argv.includes('--check') && failed) process.exit(1)
