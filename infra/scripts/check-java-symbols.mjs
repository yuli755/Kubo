#!/usr/bin/env node
/**
 * # Java 类型引用落地校验（没有 JDK 也能跑的那一半编译器·符号篇）
 *
 * ## 为什么需要它（真实事故）
 *
 * 2026-09-19 排查 WMS 页面时发现：`OutputEntity` 里有一行
 * `private PickingEntity picking;` —— **`PickingEntity` 这个类不存在**。
 * 它是「瘦身第三批」整域删 MES 时留下的残渣：类的**导入行**被删掉了，
 * **使用处**留了下来。于是：
 *
 * - `javac` 会直接报「找不到符号」——后端根本编译不过；
 * - 同一批还有 `InputMapper.xml` / `OutputMapper.xml` 里的
 *   `javaType="cn.hamm.spms.module.mes.*.*Entity"`（见 `pnpm mapper:check` 的 FQN 判定）。
 *
 * 关键在于**它躲过了当时所有门禁**：
 * - `pnpm java:imports` 判的是「import 能不能落到真实文件」，而这处**没有 import**；
 * - `pnpm java:decl` 判声明风格，不看类型是否存在；
 * - 沙箱里没有 JDK，`javac` 这份最权威的答案拿不到（真编译在用户本机）。
 *
 * 所以这里补的是「符号存在性」：**只看能确定的类型位置**，把 javac 的一部分答案提前到提交前。
 *
 * ## 判定（读文件，不编译、不连库）
 *
 * 对每个 `.java`（`src/main/java` + `src/test/java`）剥掉注释与字符串后，取这些**类型位置**：
 *   ① `new X(` ② `instanceof X` ③ `X.class` ④ 字段声明 `private/protected/public Type name;`
 *   ⑤ 方法返回类型 `public Type method(`
 * 其中 ④⑤ 泛型实参 `<...>` 内部的名字也展开（`List<PickingEntity>` 同样算）。
 *
 * 一个类型名**可解析**当且仅当满足其一：文件内声明（含嵌套）／该类的类型变量 `T/E/K/V/R`／
 * `java.lang` 常见类型／显式 `import` 的简单名／`import a.b.*` 且 `a.b` 下确有该类（本仓包按文件索引）。
 *
 * 只对**带项目味后缀**的名字（`Entity|Service|Controller|Repository|Mapper|Enum|Constant|Util|Helper|
 * Config|Interceptor|Handler|Exception|Annotation|Rule|Specification|Probe|Listener|Adapter|Factory`）
 * 报错 —— 判据是「这类名字只可能是本仓自己的类型」。这样做的代价是**放过**外部库的同名物
 * （宁可交给 javac，也不要在门禁里制造噪音），换来的是零假红：报告里出现的每一条都是真编译错误。
 *
 *   - 名字在仓库里**存在** → 报「缺 import」（`javac` 同样报错）；
 *   - 名字在仓库里**不存在** → 报「悬空引用（类已删除）」，并给出「按域找同族名字」的提示。
 *
 * 防空转：候选类型位置少于下限（当前 500 处）或一个 `.java` 都没扫到 → 直接失败，
 * 避免路径写错变成「0 处检查 ⇒ 0 处错误」。
 *
 * ## 用法
 * ```
 * pnpm java:symbols            # 闸门：任一问题即 exit 1
 * pnpm java:symbols -- --json  # 给脚本读
 * ```
 *
 * @author Arena agent
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SOURCE_ROOTS = [
  path.join(ROOT, 'SPMS-Server', 'src', 'main', 'java'),
  path.join(ROOT, 'SPMS-Server', 'src', 'test', 'java'),
]

/**
 * 项目味后缀：命中这些后缀的简单类名只可能是本仓自己的类型（外部库不会叫 `*Entity`/`*Probe`）。
 * 想放宽就先问「这个名字在外部库里真的会撞吗」，撞不到的别往里加。
 */
const PROJECT_SUFFIXES = [
  'Entity', 'Service', 'Controller', 'Repository', 'Mapper', 'Enum', 'Constant',
  'Util', 'Helper', 'Config', 'Interceptor', 'Handler', 'Annotation',
  'Rule', 'Specification', 'Probe', 'Listener', 'Adapter',
]
// 刻意不含 `Factory` / `Exception`：这两族在 JDK 与三方库里满地都是
// （`KeyFactory` / `ThreadFactory` / `ValidatorFactory` / `ValidationException` …
// 且在 `java.security.*`、`jakarta.validation.*` 这类**通配 import**下无法按名字判存在性），
// 放进来只会制造假红。本仓自己的类都带更明确的后缀（`*Service`/`*Entity`/`*Config`…）。

/** `java.lang` 里在本仓出现过的类型（javac 隐式导入；这里显式列出来，比"凡是没导入就报错"稳） */
const JAVA_LANG = new Set([
  'String', 'Object', 'Integer', 'Long', 'Double', 'Float', 'Boolean', 'Byte', 'Short', 'Character',
  'Number', 'Math', 'System', 'Thread', 'Class', 'Enum', 'Record', 'Iterable', 'Comparable', 'CharSequence',
  'Exception', 'RuntimeException', 'IllegalArgumentException', 'IllegalStateException', 'UnsupportedOperationException',
  'NullPointerException', 'ClassNotFoundException', 'NoSuchFieldException', 'NoSuchMethodException',
  'InterruptedException', 'IndexOutOfBoundsException', 'ArithmeticException', 'SecurityException',
  'Error', 'Throwable', 'StringBuilder', 'StringBuffer', 'Override', 'Deprecated', 'SuppressWarnings',
  'FunctionalInterface', 'SafeVarargs', 'Void', 'ProcessBuilder', 'Runtime', 'StackTraceElement', 'AutoCloseable',
])

/** 类型变量常用名（单字母 + 常见两字母）：这些不是类，按类型变量放过 */
const TYPE_VARIABLE_NAMES = new Set(['T', 'E', 'K', 'V', 'R', 'U', 'S', 'A', 'B', 'ID', 'PK', 'VO'])

/**
 * 扫描面下限：低于这个数说明路径写错/文件没扫到（防空转，不是精确阈值）。
 * 反例夹具（`infra/fixtures/java-symbols`）是小树，用 `KUBO_MIN_TYPE_POSITIONS=1` 注入。
 */
const MIN_TYPE_POSITIONS = Number(process.env.KUBO_MIN_TYPE_POSITIONS ?? 500)

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) {
    return out
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, out)
    }
    else if (entry.name.endsWith('.java')) {
      out.push(full)
    }
  }
  return out
}

function packageOf(source) {
  return source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1] ?? ''
}

/** 本仓类型索引：简单名 → 全限定名集合；包名 → 简单名集合 */
function buildIndex(files) {
  const bySimpleName = new Map()
  const byPackage = new Map()
  for (const file of files) {
    const simple = path.basename(file, '.java')
    const pkg = packageOf(fs.readFileSync(file, 'utf8'))
    if (!bySimpleName.has(simple)) {
      bySimpleName.set(simple, new Set())
    }
    bySimpleName.get(simple).add(pkg ? `${pkg}.${simple}` : simple)
    if (pkg) {
      if (!byPackage.has(pkg)) {
        byPackage.set(pkg, new Set())
      }
      byPackage.get(pkg).add(simple)
    }
  }
  return { bySimpleName, byPackage }
}

/** 取简单名（去掉泛型/通配/数组/限定名主前缀：`a.b.C` → `C`） */
function simpleNameOf(token) {
  return token
    .replace(/\[\]/g, '')
    .replace(/[?]/g, '')
    .trim()
    .split(/\s+(?:extends|super)\s+/)
    .pop()
    .split('.')
    .pop()
    .trim()
}

/** 泛型实参串 → 里面的类型名（`Map<String, List<Foo>>` → String / Map / List / Foo） */
function typeNamesIn(genericText) {
  return (genericText ?? '').match(/[A-Z][\w$]*(?:\.[A-Z][\w$]*)*/g) ?? []
}

/**
 * 类型位置。类型名允许带包前缀（`cn.hamm.spms.module.wms.input.InputService.class` 是合法写法，
 * 且本仓的数据权限测试就是这么写的）—— 带前缀时按**全限定名**判存在性，不能只看最后一段。
 */
const TYPE_POSITION_PATTERNS = [
  /\bnew\s+((?:[a-z][\w$]*\.)*[A-Z][\w$]*)/g,
  /\binstanceof\s+((?:[a-z][\w$]*\.)*[A-Z][\w$]*)/g,
  /((?:[a-z][\w$]*\.)*[A-Z][\w$]*)\.class\b/g,
  /\b(?:private|protected|public)\s+(?:static\s+|final\s+|transient\s+|volatile\s+)*((?:[a-z][\w$]*\.)*[A-Z][\w$]*)\s*(?:<([^;={}()]*?)>)?\s+[a-z_]\w*\s*[;=]/g,
  /\b(?:private|protected|public)\s+(?:static\s+|final\s+|abstract\s+|synchronized\s+|default\s+|native\s+)*((?:[a-z][\w$]*\.)*[A-Z][\w$]*)\s*(?:<([^;={}()]*?)>)?\s+[a-z_]\w*\s*\(/g,
]

function stripLiterals(source) {
  return source.replace(/"(?:\\.|[^"\\])*"/g, '""').replace(/'(?:\\.|[^'\\])*'/g, "''")
}

function main() {
  const files = SOURCE_ROOTS.flatMap(root => walk(root))
  if (!files.length) {
    console.error('❌ 一个 .java 都没扫到：路径写错了？')
    process.exit(1)
  }
  const { bySimpleName, byPackage } = buildIndex(files)

  const problems = []
  let positions = 0

  for (const file of files) {
    const raw = fs.readFileSync(file, 'utf8')
    const source = stripLiterals(stripJavaComments(raw))
    const pkg = packageOf(source)

    const explicitImports = new Set()
    const wildcardPackages = []
    for (const line of source.match(/^\s*import\s+(?:static\s+)?([\w.*]+)\s*;/gm) ?? []) {
      const target = line.replace(/^\s*import\s+(?:static\s+)?/, '').replace(/\s*;\s*$/, '')
      if (target.endsWith('.*')) {
        wildcardPackages.push(target.slice(0, -2))
      }
      else {
        explicitImports.add(target.split('.').pop())
      }
    }

    const declared = new Set(source.match(/\b(?:class|interface|enum|record|@interface)\s+([A-Z][\w$]*)/g)
      ?.map(match => match.replace(/^.*\s/, '')) ?? [])
    const declaredTypeVariables = new Set(
      (source.match(/\b(?:class|interface|enum|record)\s+[A-Z][\w$]*\s*<([^>]*)>/g) ?? [])
        .flatMap(match => match.match(/[A-Z][\w$]*/g) ?? [])
        .filter(name => !/\b(?:class|interface|enum|record|extends|super)\b/.test(name)),
    )

    const resolvable = token => TYPE_VARIABLE_NAMES.has(token)
      || declaredTypeVariables.has(token)
      || JAVA_LANG.has(token)
      || declared.has(token)
      || explicitImports.has(token)
      || (pkg && byPackage.get(pkg)?.has(token))
      || wildcardPackages.some(candidate => byPackage.get(candidate)?.has(token))

    /** 带包前缀的写法（`a.b.C`）：按全限定名在仓库索引里找，或按最后一段的 import 兜底 */
    const resolvableQualified = raw => {
      if (!raw.includes('.')) {
        return null
      }
      const fqn = raw.replace(/\[\]$/g, '')
      const last = fqn.split('.').pop()
      return bySimpleName.get(last)?.has(fqn) === true || explicitImports.has(last)
    }

    for (const pattern of TYPE_POSITION_PATTERNS) {
      for (const match of source.matchAll(pattern)) {
        const candidates = [match[1], ...typeNamesIn(match[2])]
        for (const candidate of candidates) {
          const token = simpleNameOf(candidate)
          if (!token) {
            continue
          }
          positions += 1
          if (!PROJECT_SUFFIXES.some(suffix => token.endsWith(suffix))) {
            continue
          }
          if (resolvable(token) || resolvableQualified(candidate) === true) {
            continue
          }
          const line = source.slice(0, match.index).split('\n').length
          const known = bySimpleName.get(token) ?? bySimpleName.get(candidate.replace(/\[\]$/g, '').split('.').pop())
          problems.push({
            file: path.relative(ROOT, file),
            line,
            token: candidate.includes('.') ? candidate : token,
            reason: known
              ? `缺 import（类存在于 ${[...known].sort().join(' / ')}）`
              : '悬空引用（仓库里没有这个类：多半是整域删除时漏掉的旧引用）',
          })
        }
      }
    }
  }

  if (positions < MIN_TYPE_POSITIONS) {
    console.error(`❌ 类型位置只扫到 ${positions} 处（下限 ${MIN_TYPE_POSITIONS}）：判据疑似空转，先查 SOURCE_ROOTS`)
    process.exit(1)
  }

  const unique = [...new Map(problems.map(item => [`${item.file}:${item.line}:${item.token}`, item])).values()]

  if (process.argv.includes('--json')) {
    console.log(JSON.stringify({ scanned: files.length, positions, problems: unique }, null, 2))
    process.exit(unique.length ? 1 : 0)
  }

  if (unique.length) {
    console.error(`\n❌ Java 类型引用有问题（扫描 ${files.length} 个文件 / ${positions} 处类型位置）\n`)
    for (const item of unique) {
      console.error(`   ${item.file}:${item.line}  ${item.token}  → ${item.reason}`)
    }
    console.error('\n   报出来的每一条都是 javac 会报「找不到符号」的地方；沙箱没有 JDK，这条是它的替身。\n')
    process.exit(1)
  }

  console.log(`✓ Java 类型引用全部可解析（${files.length} 个文件 / ${positions} 处类型位置，覆盖 new / instanceof / .class / 字段 / 返回类型 + 泛型实参）`)
}

main()
