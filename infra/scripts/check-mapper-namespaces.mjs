#!/usr/bin/env node
/**
 * # Mapper XML 命名空间落地门禁（没有 JDK 也能跑的那一半）
 *
 * ## 为什么需要它
 * 2026-09-17 的「瘦身第三批」整域拆了 MES（`module/mes/**` 88 个后端文件全删），
 * 但 **`src/main/resources/mapper/mes/*.xml` 六份留了下来** —— 它们的
 * `namespace="cn.hamm.spms.module.mes.*.mapper.*Mapper"` 指向早已不存在的 Java 接口。
 *
 * 这种残留有两个特点，所以必须用机器判据而不是靠人眼：
 *
 *   1. **它不报错**。MyBatis 加载 XML 时按 namespace 找类，找不到只打 debug 日志
 *      （`bindMapperForNamespace` 吞掉 `ClassNotFoundException`），启动照过、测试照绿。
 *      真正的问题在于它是一份**活在仓库里的假事实**：读代码的人会以为 MES 还在，
 *      `grep` 表名/权限码会把它们捞出来当证据，下一次「这个功能在哪」的排查从假线索开始。
 *   2. **它会被误认为"SQL 分层合格"**。`pnpm sql:check` 只看 `.java` 里有没有 SQL，
 *      XML 里的语句它一律放行 —— 死 XML 里的语句因此躲过了全部门禁。
 *
 * 还有一类同源问题：**两份 XML 用同一个 namespace**（复制粘贴改一半）。这个 MyBatis
 * 会在启动期抛 `mapper already registered`（`MapperRegistry.addMapper`），属于"能在本地
 * 早发现就别留到部署"的一类，顺手一起查。
 *
 * 还有第二类同源残留：**XML 里的类型引用指向已删的域**。
 * 2026-09-19 在 WMS 页面排查里抓到两处：`InputMapper.xml` 的
 * `javaType="cn.hamm.spms.module.mes.order.OrderEntity"` 与 `OutputMapper.xml` 的
 * `…module.mes.picking.PickingEntity`。namespace 那一条判不到它们 —— namespace 是好的、
 * XML 也是活的，死掉的只有 association 上的类型。MyBatis 解析 resultMap 时会 `Class.forName`，
 * 所以这不是"文档失真"而是**启动/取数即错**。`pnpm java:symbols` 覆盖了 Java 侧的同族问题，
 * 这里补 XML 侧。
 *
 * ## 判定（都是"读文件"，不连库、不编译）
 *   ① 每份 XML 必须有 `namespace`，且它对应的 Java 接口文件必须存在 → 否则**失败**
 *   ② 同一个 namespace 不许出现在两份 XML 里 → 否则**失败**
 *   ③ `javaType` / `resultType` / `parameterType` / `type` / `ofType` 的类型要么是 MyBatis 内建别名，
 *      要么是**能在仓库里落地的全限定名**（`java.*`/`jakarta.*` 等 JDK 与三方前缀按包放行，
 *      因为本仓没有它们的源码；`cn.hamm.spms.*` 一律严格校验）→ 否则**失败**
 *   ④ 扫描面为空（0 份 XML / 0 处类型引用）→ **失败**（防"路径写错导致门禁空转"，
 *      这是本仓在 `migration:check` 上踩过的坑：0 个脚本 ⇒ 0 处错误）
 *
 * ## 用法
 * ```
 * pnpm mapper:check                 # 闸门：任一问题即 exit 1
 * pnpm mapper:check -- --json       # 给脚本读
 * ```
 *
 * @author Arena agent
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')
const MAPPER_DIR = path.join(SERVER, 'src', 'main', 'resources', 'mapper')
const JAVA_DIR = path.join(SERVER, 'src', 'main', 'java')

const argv = process.argv.slice(2)
const asJson = argv.includes('--json')

/** 深度遍历，返回文件绝对路径列表 */
function walk(dir, out = []) {
  if (!fs.existsSync(dir)) {
    return out
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, out)
    }
    else {
      out.push(full)
    }
  }
  return out
}

const rel = file => path.relative(ROOT, file).replaceAll('\\', '/')

const xmlFiles = walk(MAPPER_DIR).filter(f => f.endsWith('.xml'))

/** MyBatis 内建别名（`TypeAliasRegistry` 的默认注册）—— 这些不是全限定名，不参与落地判定 */
const MYBATIS_ALIASES = new Set([
  'string', 'byte', 'long', 'short', 'int', 'integer', 'double', 'float', 'boolean', 'boolean',
  'date', 'decimal', 'bigdecimal', 'biginteger', 'object', 'map', 'hashmap', 'list', 'arraylist',
  'collection', 'iterator', 'resultset', '_byte', '_long', '_short', '_int', '_integer', '_double',
  '_float', '_boolean', '_date', '_decimal', '_biginteger', '_object',
])

/** 仓库外的前缀：JDK / Jakarta / 三方库，按包放行（仓库里没有它们的源码） */
const OUTSIDE_PREFIXES = ['java.', 'javax.', 'jakarta.', 'org.', 'com.', 'net.', 'io.', 'lombok.']

/** 类型属性：resultMap 的 type、association 的 javaType、select 的 resultType/parameterType… */
const TYPE_ATTRIBUTES = ['javaType', 'resultType', 'parameterType', 'ofType', 'type']

/** 扫描面下限：少于这个数说明正则/路径漂了（防空转，不是精确阈值）；反例夹具注入 1 */
const MIN_TYPE_REFERENCES = Number(process.env.KUBO_MIN_TYPE_REFERENCES ?? 40)

/** 仓库里能落地的全限定名（main + test 都算：Mapper XML 只引 main，但测试资源同族） */
const repoTypes = new Set()
for (const root of [JAVA_DIR, path.join(SERVER, 'src', 'test', 'java')]) {
  for (const file of walk(root).filter(f => f.endsWith('.java'))) {
    const source = fs.readFileSync(file, 'utf8')
    const pkg = source.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1]
    if (pkg) {
      repoTypes.add(`${pkg}.${path.basename(file, '.java')}`)
    }
  }
}

/** namespace → 出现它的 XML（用于重复注册判定） */
const byNamespace = new Map()
const dangling = []
const noNamespace = []
const emptyXml = []
const unresolvedTypes = []
let typeReferences = 0

for (const file of xmlFiles) {
  const raw = fs.readFileSync(file, 'utf8')
  const match = raw.match(/<mapper\s+namespace="([^"]+)"/)
  if (!match) {
    noNamespace.push(rel(file))
    continue
  }
  const namespace = match[1]
  byNamespace.set(namespace, [...(byNamespace.get(namespace) ?? []), rel(file)])

  // 至少要有一条语句，否则是"空壳 XML"：注册得进 MyBatis，回答不了任何查询
  if (!/<(select|insert|update|delete|sql|resultMap)\b/.test(raw)) {
    emptyXml.push(rel(file))
  }

  const javaFile = path.join(JAVA_DIR, `${namespace.replaceAll('.', '/')}.java`)
  if (!fs.existsSync(javaFile)) {
    dangling.push({ file: rel(file), namespace })
  }

  for (const attribute of TYPE_ATTRIBUTES) {
    for (const match of raw.matchAll(new RegExp(`${attribute}="([^"]+)"`, 'g'))) {
      const value = match[1].trim()
      if (!value || MYBATIS_ALIASES.has(value.toLowerCase())) {
        continue
      }
      typeReferences += 1
      if (!value.includes('.')) {
        // 短名不是别名：MyBatis 会当成类型去解析，必然失败
        unresolvedTypes.push({ file: rel(file), attribute, value, reason: '既不是内建别名也不是全限定名' })
        continue
      }
      if (OUTSIDE_PREFIXES.some(prefix => value.startsWith(prefix))) {
        continue
      }
      if (!repoTypes.has(value)) {
        unresolvedTypes.push({ file: rel(file), attribute, value, reason: '仓库里没有这个类型（整域删除后的残留？）' })
      }
    }
  }
}

const duplicated = [...byNamespace.entries()]
  .filter(([, files]) => files.length > 1)
  .map(([namespace, files]) => ({ namespace, files }))

const problems = []
if (xmlFiles.length === 0) {
  problems.push(`扫描面为空：${rel(MAPPER_DIR)} 下没有任何 XML —— 门禁空转，先确认路径`)
}
for (const item of noNamespace) {
  problems.push(`${item}：没有 <mapper namespace="…">，MyBatis 无法绑定接口`)
}
for (const item of dangling) {
  problems.push(`${item.file}：namespace 指向的接口不存在 → ${item.namespace}`)
}
for (const item of duplicated) {
  problems.push(`namespace 被两份 XML 重复占用 → ${item.namespace}（${item.files.join('、')}）`)
}
for (const item of emptyXml) {
  problems.push(`${item}：一条语句都没有（空壳 XML）`)
}
for (const item of unresolvedTypes) {
  problems.push(`${item.file}：${item.attribute}="${item.value}" → ${item.reason}`)
}
if (typeReferences < MIN_TYPE_REFERENCES) {
  problems.push(`类型引用只扫到 ${typeReferences} 处（下限 ${MIN_TYPE_REFERENCES}）：判据疑似空转，先查 TYPE_ATTRIBUTES 与 MAPPER_DIR`)
}

const report = {
  mapperDir: rel(MAPPER_DIR),
  xmlFiles: xmlFiles.length,
  namespaces: byNamespace.size,
  dangling,
  duplicated,
  noNamespace,
  emptyXml,
  unresolvedTypes,
  typeReferences,
  problems,
}

if (asJson) {
  console.log(JSON.stringify(report, null, 2))
}
else {
  console.log('🔎 Mapper XML 命名空间门禁')
  console.log(`  扫描：${rel(MAPPER_DIR)} → ${xmlFiles.length} 份 XML / ${byNamespace.size} 个 namespace`)
  console.log(`  接口：${rel(JAVA_DIR)}（namespace 必须能落到这里的 .java）`)
  console.log(`  类型：${typeReferences} 处 javaType/resultType/parameterType/type/ofType 引用`)
  if (problems.length === 0) {
    console.log('✅ 全部落地：没有悬空 namespace、没有重复注册、没有空壳 XML、没有指向已删域的类型引用')
  }
  else {
    console.log(`\n❌ ${problems.length} 处问题：`)
    for (const item of problems) {
      console.log(`  · ${item}`)
    }
    console.log('\n修法二选一：① 这个域还在 → 补回 Java 接口；② 这个域已整域拆除 → 删掉 XML 本文件。')
  }
}

process.exit(problems.length === 0 ? 0 : 1)
