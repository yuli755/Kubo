#!/usr/bin/env node
/**
 * 前后端权限码一致性校验（前端 F1 的 CI 卡点）
 *
 * ## 为什么需要它
 * 前端权限码写错时表现是"按钮消失"，而不是报错：`v-permission` 与 ATable 的
 * `*-permission` 都是"没有该权限码 → 隐藏"。于是 `Permissions.Order.delete` 这种
 * 后端根本不存在的码（OrderController 上有 `@Extends(exclude = Curd.Delete)`），
 * 会让所有人都看不到删除按钮，而 CI 与类型检查都发现不了 —— 只有拿后端真值对一遍。
 *
 * ## 真值来源
 * - 后端码格式：`{EntityName}_{controller 方法名}`（AirPower `PermissionUtil.scanPermission()`）
 * - 标准 CRUD 动作（add/update/delete/getDetail/disable/enable/import/export/publish）
 *   由框架基类提供，按"未被 `@Extends(exclude=...)` 排除"视为存在
 * - 单据基类 `BaseBillController` 提供 audit / reject / addFinish
 * - 模块 Controller 自己声明的 `@*Mapping` 方法名逐个计入
 *
 * 用法：node infra/scripts/check-permission-codes.mjs [--json report.json] [--verbose]
 */
import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java', 'cn', 'hamm', 'spms')
const FRONTEND_CONSTANTS = join(ROOT, 'src', 'config', 'permissions.ts')

/**
 * AirPower `CurdController` 默认为每个实体生成的动作。
 * 注意这是"能力开关"的基准，模块 Controller 通过 `@Extends({Curd.X})` 追加、
 * `@Extends(exclude = Curd.X)` 去掉；本项目 `base/BaseController` 已经排掉了
 * Export/QueryExport/Disable/Enable，所以只有显式声明的实体才有这些码。
 */
const AIRPOWER_BASE = ['add', 'update', 'delete', 'getDetail', 'getList', 'import', 'export', 'disable', 'enable']
/** 单据基类动作 */
const BILL_ACTIONS = ['audit', 'reject', 'addFinish']
/**
 * 由 AirPower 框架 jar 提供 controller 的实体：本脚本只扫仓库源码，扫不到它们，
 * 因此对这些实体的码不做"后端不存在"判定（否则会把 File_/Log_ 这类正常码报成死码）。
 * 新增此类实体时请顺手在这里登记，并在 PR 里说明依据。
 */
const FRAMEWORK_ENTITIES = new Set(['Log', 'File', 'Captcha', 'Oauth2'])
/** Curd 枚举名 → 动作名（只登记前端会用到的） */
const CURD_TO_ACTION = {
  Add: 'add',
  Update: 'update',
  Delete: 'delete',
  GetDetail: 'getDetail',
  GetList: 'getList',
  Import: 'import',
  Export: 'export',
  Disable: 'disable',
  Enable: 'enable',
}

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory())
      walk(p, out)
    else if (name.endsWith('.java'))
      out.push(p)
  }
  return out
}

function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

/** 解析后端 Controller → 每个实体可用的权限动作集合 */
function scanBackend() {
  const files = walk(SERVER).filter(f => f.endsWith('Controller.java'))
  /** 类名 → { entity, parent, adds, excludes, methods } */
  const classes = new Map()

  for (const file of files) {
    const source = stripComments(readFileSync(file, 'utf8'))
    const classMatch = source.match(/(?:public\s+|abstract\s+)?class\s+(\w+Controller)([\s\S]*?)(?:\{|extends)/)
    if (!classMatch)
      continue
    const className = classMatch[1]

    // 实体名来自 extends 的**第一个泛型实参**：`extends BaseController<MaterialEntity, ...>`
    const genericMatch = source.match(/extends\s+\w+Controller\s*<\s*(\w+)Entity\b/)
    const entity = genericMatch ? genericMatch[1] : null

    const parentMatch = source.match(/extends\s+(\w+Controller)\b/)
    const adds = new Set()
    const excludes = new Set()
    for (const m of source.matchAll(/@Extends\s*\(([\s\S]*?)\)\s*\n/g)) {
      const body = m[1]
      const hasExclude = /\bexclude\b/.test(body)
      let excluded = ''
      let addPart = body
      if (hasExclude) {
        const m = body.match(/exclude\s*=\s*\{?([^}]*)\}?/)
        excluded = m ? m[1] : ''
        addPart = body.slice(0, body.indexOf('exclude'))
      }
      for (const e of addPart.matchAll(/Curd\s*\.\s*(\w+)/g)) {
        const action = CURD_TO_ACTION[e[1]]
        if (action)
          adds.add(action)
      }
      for (const e of (excluded || '').matchAll(/Curd\s*\.\s*(\w+)/g)) {
        const action = CURD_TO_ACTION[e[1]]
        if (action)
          excludes.add(action)
      }
    }

    const methods = new Set()
    const methodRe
      = /@(?:Post|Get|Put|Delete|Patch)Mapping(?:\s*\([^)]*\))?[^;{]*?public\s[\w<>?,.[\]\s]+?\s(\w+)\s*\(/g
    for (const m of source.matchAll(methodRe)) methods.add(m[1])
    for (const m of source.matchAll(/@Permission\s*\(\s*"([^"]*)"/g)) methods.add(m[1])

    classes.set(className, { entity, parent: parentMatch ? parentMatch[1] : null, adds, excludes, methods })
  }

  /** 沿链合并：base(AirPower) → 各层 @Extends 增删 → 各层声明的方法 */
  const resolve = (className, seen = new Set()) => {
    const info = classes.get(className)
    if (!info || seen.has(className))
      return { actions: new Set(), parents: [] }
    seen.add(className)
    // 父类不在仓库里（例如 AirPower 的 CurdController）→ 以框架默认动作集为基准
    const up = info.parent && classes.has(info.parent)
      ? resolve(info.parent, seen)
      : { actions: new Set(AIRPOWER_BASE), parents: [] }
    const actions = new Set(up.actions)
    for (const a of info.excludes) actions.delete(a)
    for (const a of info.adds) actions.add(a)
    for (const m of info.methods) actions.add(m)
    if (className === 'BaseBillController') {
      for (const a of BILL_ACTIONS) actions.add(a)
    }
    return { actions, parents: [...up.parents, className] }
  }

  const actions = new Map()
  const parents = new Map()
  for (const [className, info] of classes) {
    if (!info.entity)
      continue // 抽象基类（泛型是类型变量）不算实体
    const resolved = resolve(className)
    const existing = actions.get(info.entity)
    if (existing) {
      for (const a of resolved.actions) existing.add(a)
    }
    else {
      actions.set(info.entity, resolved.actions)
    }
    parents.set(info.entity, resolved.parents.join(' → '))
  }
  return { actions, parents }
}

/** 解析前端 permissions.ts：group 路径 → 权限码 */
function scanFrontend() {
  const source = stripComments(readFileSync(FRONTEND_CONSTANTS, 'utf8'))
  const codes = new Map() // code → [{group, action}]
  const moduleRe = /^const (\w+) = \{([\s\S]*?)^\}/gm
  for (const moduleMatch of source.matchAll(moduleRe)) {
    const moduleName = moduleMatch[1]
    if (moduleName === 'Crud')
      continue
    const body = moduleMatch[2]
    const groupRe = /^ {2}(\w+): \{([\s\S]*?)^ {2}\},?$/gm
    for (const groupMatch of body.matchAll(groupRe)) {
      const groupName = groupMatch[1]
      const group = `${moduleName}.${groupName}`
      const fields = [...groupMatch[2].matchAll(/^\s{4}(\w+):\s*(`[^`]*`|'[^']*'),?$/gm)]
      for (const field of fields) {
        const raw = field[2].slice(1, -1)
        // 模板字面量 `${Crud.add}` 之类：用 Crud 表解析
        const resolved = raw.replace(/\$\{Crud\.(\w+)\}/g, (_, action) => {
          const crudMatch = source.match(/const Crud = \{([\s\S]*?)\} as const/)
          const table = new Map()
          if (crudMatch) {
            for (const entry of crudMatch[1].matchAll(/^\s*(\w+):\s*'([^']*)',?$/gm)) {
              table.set(entry[1], entry[2])
            }
          }
          return table.get(action) ?? action
        })
        if (!codes.has(resolved))
          codes.set(resolved, [])
        codes.get(resolved).push({ group, action: field[1] })
      }
    }
  }
  return codes
}

function parseArgs() {
  const argv = process.argv.slice(2)
  const out = { verbose: false, json: null }
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--verbose')
      out.verbose = true
    else if (argv[i] === '--json')
      out.json = argv[++i]
  }
  return out
}

const options = parseArgs()
const { actions: backend, parents } = scanBackend()
const frontend = scanFrontend()

/** 前端码 → {entity, action} */
function splitCode(code) {
  const index = code.lastIndexOf('_')
  if (index <= 0)
    return null
  return { entity: code.slice(0, index), action: code.slice(index + 1) }
}

const missingOnBackend = []
for (const [code, uses] of frontend) {
  const parsed = splitCode(code)
  if (!parsed) {
    missingOnBackend.push({ code, reason: '格式不符合 {Entity}_{action}', uses })
    continue
  }
  if (FRAMEWORK_ENTITIES.has(parsed.entity)) {
    continue
  }
  const entityActions = backend.get(parsed.entity)
  if (!entityActions) {
    missingOnBackend.push({ code, reason: `后端没有 ${entityNamed(parsed.entity)} 对应的 Controller`, uses })
    continue
  }
  if (!entityActions.has(parsed.action)) {
    const hint = parentHint(parsed.entity)
    missingOnBackend.push({
      code,
      reason: `后端 ${parsed.entity} 未提供动作 ${parsed.action}（该方法不存在或被 @Extends(exclude) 排除）${hint}`,
      uses,
    })
  }
}

// 后端有、前端没登记的动作（只报告，不失败：多数是无需按钮的读接口）
const notGatedInFrontend = []
for (const [entity, set] of backend) {
  if (FRAMEWORK_ENTITIES.has(entity))
    continue
  for (const action of set) {
    if (action === 'getList')
      continue // 列表读取没有独立按钮
    const code = `${entity}_${action}`
    if (!frontend.has(code))
      notGatedInFrontend.push(code)
  }
}

function entityNamed(entity) {
  return `${entity}Entity`
}

function parentHint(entity) {
  const parent = parents.get(entity)
  return parent ? `；父类 ${parent}` : ''
}

const report = {
  generatedAt: new Date().toISOString(),
  backendEntities: backend.size,
  frontendCodes: frontend.size,
  missingOnBackend: missingOnBackend.map(x => ({
    code: x.code,
    reason: x.reason,
    uses: x.uses.map(u => `${u.group}.${u.action}`),
  })),
  notGatedInFrontend: notGatedInFrontend.sort(),
}

if (options.json) {
  mkdirSync(dirname(resolve(ROOT, options.json)), { recursive: true })
  writeFileSync(resolve(ROOT, options.json), `${JSON.stringify(report, null, 2)}\n`)
}

console.log(`后端实体（Controller）：${report.backendEntities}；前端权限码：${report.frontendCodes}`)
if (options.verbose) {
  console.log('\n后端未提供但前端已登记的码：')
  for (const item of report.missingOnBackend) {
    console.log(`  ✗ ${item.code}  ← ${item.uses.join(', ')}\n      ${item.reason}`)
  }
  console.log('\n后端存在但前端未登记的码（前 40 条）：')
  console.log(`  ${report.notGatedInFrontend.slice(0, 40).join('\n  ')}`)
}

if (report.missingOnBackend.length > 0) {
  console.error(`\n✗ ${report.missingOnBackend.length} 个权限码在后端不存在（用它们做门控会把按钮永久隐藏）：`)
  for (const item of report.missingOnBackend) {
    console.error(`  - ${item.code}  (前端：${item.uses.join(', ')})  ${item.reason}`)
  }
  console.error(`\n  详情可跑：node infra/scripts/check-permission-codes.mjs --verbose`)
  process.exit(1)
}

console.log(`✓ 前端所有权限码都能在后端找到对应端点（未登记的后端动作 ${report.notGatedInFrontend.length} 个，通常是无按钮的读接口）`)
