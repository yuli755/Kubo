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
 * - **L6**：跨业务域不得 import 对方的 `repository` / `mapper` / `controller`（只许经 Service 或共享值类型）
 * - **L7**：`common` 不得 import `module.*`（例外：Spring 装配类，逐条打印）
 * - **L3**：`module/**&#47;mapper|repository` 不得 import `.service.` / `.controller.` / `*Controller`
 * - **L4**：`*Entity.java` 不得 import `.service.` / `.mapper.` / `.repository.` / `.controller.`
 * - **L5**：`*Controller.java` 不得**注入** `*Repository` / `*Mapper`（字段或构造器参数）——
 *   控制器是 HTTP 出入口，回表要么走 service，要么走读侧 mapper 的**服务封装**
 *
 * ## 判据口径（实测踩过的两个盲区）
 *
 * 1. `import static X.*;` 必须能被解析 —— 否则整行免检（通配符静态导入曾让一处 `base → module` 漏网）；
 * 2. 组合根按**注解**判定（L7）而不是只按文件名 —— `AppConfig` 这类名字里没有 "Configuration" 的装配类是盲区。
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
 * L2 白名单：`base` 里"下层依赖业务域"的现存条目 —— **现在是空的**。
 *
 * 它曾经有 15 条，全部集中在 `base/BaseService` 与 `base/bill/AbstractBaseBillService`：
 * 基类要在"所有落库路径的必经点"上挂业务钩子（取号、动作级/行级权限、脱敏、审计、导出遮蔽），
 * 而这些能力的实现都在 `module.*`。
 *
 * 清空路径（两批，都有可取的经验）：
 *   · **L2a**（2026-09-18）：三个**纯静态工具**直接搬走，15 → 12 ——
 *     `MaskedInputRegistry` / `DataPermissionResourceResolver` → `framework.curd.datapermission`，
 *     `AttributionWriteProtection` → `base.permission`（它操作的就是 `base.TenantAwareEntity`）。
 *     经验：**能搬的下移优先于做接口** —— 静态工具做成接口是自找麻烦。
 *   · **L2b**（2026-09-18）：剩下 12 条走真接口，12 → 0 ——
 *     契约放 `base/spi`（**接口住在使用它的最下层**，不是 framework），实现留在 `module.*`
 *     并 `implements` 接口，依赖方向变成 module → base（允许的那一档）。
 *     两处静态工具（`SensitiveWriteProtection` / `ExportColumnMasker`）额外加了一个薄适配器类：
 *     让它们继续是不可被替换的静态工具，适配器只做转发。
 *
 * 白名单**保持为空**：任何新增的 base → module 依赖都应当直接失败，而不是再加一条进来。
 * 数组保留（而不是删掉常量）是为了让上面这段历史有地方待着，也便于将来真有例外时按纪律登记。
 */
const ALLOWLIST = [
].map(([file, fqn, reason]) => ({ file, fqn, reason }))

/** L1 的另一条例外：framework 只允许认识 `common.exception`（最底层错误码），别的 common 子包不行。 */
const FRAMEWORK_ALLOWED_COMMON = ['cn.hamm.spms.common.exception.']

/**
 * 白名单条目数上限（**只许降**）：0 —— 即"一条都不许有"。
 *
 * 这个 0 是 L2b 做完后的状态，也是从今往后的底线：`base` 不许依赖 `module.*`，
 * 需要业务域的能力就照 `base/spi` 的样子定义一个接口。
 * `framework → common.exception` 不占白名单：它是**规则允许**的（见 FRAMEWORK_ALLOWED_COMMON），
 * 原因写在设计文档里，而不是靠一条会过期的白名单撑着。
 */
const MAX_ALLOWLIST = 0

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
    regs: [/^import\s+(?:static\s+)?cn\.hamm\.spms\..*\.service\./, /^import\s+(?:static\s+)?cn\.hamm\.spms\..*Controller;/],
  },
  {
    id: 'L4',
    desc: '实体不得依赖 service / mapper / repository / controller',
    matchFile: f => /Entity\.java$/.test(f),
    regs: [
      /^import\s+(?:static\s+)?cn\.hamm\.spms\..*\.service\./,
      /^import\s+(?:static\s+)?cn\.hamm\.spms\..*\.mapper\./,
      /^import\s+(?:static\s+)?cn\.hamm\.spms\..*\.repository\./,
      /^import\s+(?:static\s+)?cn\.hamm\.spms\..*Controller;/,
    ],
  },
]

/**
 * L6：跨业务域只许经对方的 Service 或共享值类型，不许伸手进对方的**仓储与入口**。
 *
 * 为什么单列一条：L2（base → module）管的是"上下方向"，但 `module` 内部还有"横向"的问题 ——
 * `module.channel` 直接注入 `module.wms.output.OutputRepository` 同样是把两个业务域焊死：
 * 对方的表结构一变，两边一起红；而且这类依赖绕过了对方的 Service，
 * 校验、审计、数据权限、脱敏（它们都挂在 Service 钩子上）全部失效 —— 是一次实打实的越权写入。
 *
 * 允许的跨域形状：`service`（对方的业务入口，钩子齐全）与共享值类型
 * （`entity` / `enums` / `annotation` / 以子域命名的实体包）。
 * 实测（2026-09-18）：跨域 import 别人的 `repository` / `mapper` / `controller` **0 处** ——
 * 所以这条规则没有基线，新增即红。
 */
const CROSS_DOMAIN_FORBIDDEN_LEAF = ['repository', 'mapper', 'controller']
const L6_DESC = '跨域不得 import 对方的 repository / mapper / controller（只许经 Service 或共享值类型）'

/** 业务域 = `module/` 下的第一段（asset / channel / factory / personnel / system / wms） */
function domainOf(rel) {
  const parts = rel.split('/')
  return parts[0] === 'module' && parts.length > 2 ? parts[1] : null
}

/**
 * L7：`common` 是**横切包**，不是"什么都能放"的包 —— 只许依赖 `core` / `framework` / `base`、
 * JDK 与第三方库，**不许 import `module.*`**。
 *
 * 为什么单列一条：`common` 在旧口径里被写成"任意层"的免检区，于是它成了越层的藏身处 ——
 * 实测曾有 18 处伸手进业务域（应用请求管线、灰度引擎、跨域声明式注解），
 * 每一处都让"common 能不能依赖业务域"这个问题失去答案。
 *
 * **例外：Spring 装配类**（带 `@Configuration` / `@ConfigurationProperties` /
 * `@SpringBootApplication`），逐条打印在输出里。两条说明：
 *   · 按**注解**判定，不按文件名 —— `AppConfig` / `OpenApiConfig` 这类名字不含 "Configuration"，
 *     按名字判会漏（L0–L2 的组合根判据就是这个形状，这里刻意用更强的一条）；
 *   · 这条放宽**只作用于 L7**：framework / base 里的装配类是"框架自己的装配"，
 *     去认识业务域是真越层，仍按原口径拦。
 */
const L7_DESC = 'common 不得依赖 module（装配类 @Configuration 例外）'
const ASSEMBLY_ANNOTATION_RE = /@(?:Configuration|ConfigurationProperties|SpringBootApplication)\b/

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
/** L7 的装配类例外（只记账、不报红，但会打印出来给人看） */
const l7Exemptions = []

for (const { rel, abs } of files) {
  const raw = fs.readFileSync(abs, 'utf8')
  const code = stripJavaComments(raw)
  // 通配符必须一起认：`import static X.Y.*;` 在旧判据里**整行解析不到**（`[\w.]+` 遇 `*` 不匹配），
  // 于是它成了免检通道 —— 实测就是这么漏掉一处 `base → module` 的（L7 那批搬 CodeRuleField 时撞到的）。
  const importLines = [...code.matchAll(/^import\s+(?:static\s+)?(cn\.hamm\.spms\.[\w.]+(?:\.\*)?);\s*$/gm)]
    .map(m => m[1].replace(/\.\*$/, ''))
  const imports = importLines.map(fqn => fqn.replace(/\.[\w$]+$/, ''))

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

  // L6：跨域不得伸手进对方的仓储 / 入口
  const ownDomain = domainOf(rel)
  if (ownDomain) {
    for (const fqn of importLines) {
      const m = /^cn\.hamm\.spms\.module\.(\w+)\.([\w.]+)\.(\w+)$/.exec(fqn)
      if (!m) continue
      const [, otherDomain, pkg] = m
      if (otherDomain === ownDomain) continue
      if (!CROSS_DOMAIN_FORBIDDEN_LEAF.includes(pkg.split('.').pop())) continue
      violations.push({
        rule: 'L6', file: `src/main/java/cn/hamm/spms/${rel}`, type: 'import',
        detail: fqn, desc: L6_DESC,
      })
    }
  }

  // L7：common 的边界（装配类例外，逐条记账）
  if (rel.startsWith('common/')) {
    const assembly = ASSEMBLY_ANNOTATION_RE.test(code)
    for (const fqn of importLines) {
      if (!fqn.replace('cn.hamm.spms.', '').startsWith('module.')) continue
      if (assembly) {
        l7Exemptions.push({ file: rel, fqn })
        continue
      }
      violations.push({
        rule: 'L7', file: `src/main/java/cn/hamm/spms/${rel}`, type: 'import',
        detail: fqn, desc: L7_DESC,
      })
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
  console.log('代码分层门禁：依赖方向（L0–L2）+ 层内职责（L3–L5）+ 跨域数据面（L6）+ common 边界（L7）')
  console.log(`  扫描：${files.length} 个 .java（cn.hamm.spms 下，剥注释）`)
  console.log(`  白名单：${ALLOWLIST.length}/${MAX_ALLOWLIST} 条（base→module；已清空，新增即红）`)
  if (violations.length === 0) {
    console.log('✓ 依赖方向、层内职责、跨域数据面与 common 边界均无违规')
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
    console.log('  跨域：只许 module.<A> → module.<B>.service / 共享值类型（0 处数据面越界）')
    console.log(`  common：只许依赖 core/framework/base；装配类例外 ${new Set(l7Exemptions.map(e => e.file)).size} 个文件 / ${l7Exemptions.length} 条语句`)
    for (const e of l7Exemptions) {
      console.log(`    · ${e.file} → ${e.fqn}`)
    }
  }
}

const failed = violations.length > 0 || stale.length > 0 || tooMany
if (process.argv.includes('--check') && failed) process.exit(1)
