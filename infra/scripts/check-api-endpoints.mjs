#!/usr/bin/env node
/**
 * # 前后端**端点级**契约门禁（没有 JDK / 没有后端进程也能跑）
 *
 * ## 为什么需要它
 * `pnpm perms:check` 只对**权限码**（`{Entity}_{method}`）做前后端一致性校验，它假定
 * 「权限码存在 ⇒ 端点存在」。可 2026-09-19 的 WMS 页面对账里抓到两个反例：
 *
 *   - `getTreeList` —— 前端 5 个树页 + 2 个授权弹窗 + 库存选择器都在 POST 它，
 *     后端**没有任何映射**（树导航在后端是 `getList` 的后置行为：
 *     `beforeGetList` 把空 `parentId` 兜成根、`afterGetList` 递归回填 `children`）；
 *   - `setBillDetailsAllFinished` —— 前端单据基类里的方法指向 POST 同名端点，
 *     后端只有同名 **Service 内部回调**（明细全部完成时由 `addFinish` 触发），没有控制器映射。
 *
 * 这两个错都**不在权限码面上**，所以整套门禁没有一个能看见它们；而运行期表现又是最坏的一种：
 * 演示 mock 恰好实现了这两个动作（预览一切正常），连真后端才 404 —— 「树永远空」「一键完成点了没反应」，
 * 排查从"是不是没数据"开始，方向全错。
 *
 * ## 判定
 *   ① 扫后端全部 `@PostMapping/@GetMapping/@PutMapping/@DeleteMapping` 的**字面量**（含连字符端点）
 *   ② 扫前端"生产代码"里的端点字面量：
 *      - `protected urlXxx = 'xxx'`（`framework/http.ts` / `framework/bill.ts` / 各域 `K*Service`）
 *      - `this.api('xxx')` / `new Http('xxx')` 这类直接写的
 *      排除 `__tests__/**` 与 `src/demo/**`（演示适配器不是契约），也排除含 `/` 的路径片段
 *      （`getDetail/${id}` 这种，取第一段 `getDetail`）
 *   ③ 前端每一个端点字面量都必须能在后端找到同名映射 → 否则**失败**，按文件:行号列出
 *   ④ 防空转：后端映射 < 40 条、或前端字面量 < 12 条 → **失败**
 *      （路径写错/正则失效时，本门禁必须报错而不是"全绿"——这是本仓在
 *      `migration:check`、CI 的 `dependency:tree -q` 上空转过两次的老坑）
 *
 * ## 不做什么
 * 不校验参数形状、不校验路径参数（`getDetail/{id}`），也不做"后端每个端点都必须被前端调用"
 * 的反向判定 —— 后端有大量管理端点（数据权限看板、outbox 处置）不是给当前前端用的。
 *
 * ## 用法
 * ```
 * pnpm api:check                 # 闸门
 * pnpm api:check -- --json       # 给脚本读
 * pnpm api:check -- --verbose    # 连后端端点清单一起打印
 * ```
 *
 * @author Arena agent
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const JAVA_DIR = path.join(ROOT, 'SPMS-Server', 'src', 'main', 'java')
const FRONT_DIR = path.join(ROOT, 'src')

const argv = process.argv.slice(2)
const asJson = argv.includes('--json')
const verbose = argv.includes('--verbose')

/** 下限（防空转）：低于这个数说明扫描面失效，不是"没问题" */
const MIN_BACKEND_ENDPOINTS = 40
const MIN_FRONT_ENDPOINTS = 12

const MAPPING_RE = /@(?:Post|Get|Put|Delete)Mapping\(\s*"([^"]+)"\s*\)/g
/** 前端端点字面量：`protected urlXxx = 'xxx'` */
const URL_CONST_RE = /(?:protected\s+)?(?:readonly\s+)?url[A-Z]\w*\s*[:=]\s*'([^']+)'/g
/** 前端端点字面量：`this.api('xxx')` / `new Http('xxx')` */
const API_CALL_RE = /(?:this\.api|new\s+Http)\(\s*'([^']+)'/g

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) {
    return out
  }
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const stat = fs.statSync(p)
    if (stat.isDirectory()) {
      walk(p, out)
    } else if (name.endsWith('.java') || name.endsWith('.ts') || name.endsWith('.vue')) {
      out.push(p)
    }
  }
  return out
}

/** 相对仓库根的短路径，报错信息里更好读 */
function rel(file) {
  return path.relative(ROOT, file).split(path.sep).join('/')
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length
}

/** 后端端点：字面量 → 出现位置（同名字面量可能被多个控制器声明） */
function collectBackend() {
  const endpoints = new Map()
  for (const file of walk(JAVA_DIR)) {
    if (!file.endsWith('.java')) {
      continue
    }
    const source = fs.readFileSync(file, 'utf8')
    for (const m of source.matchAll(MAPPING_RE)) {
      const action = m[1]
      if (!endpoints.has(action)) {
        endpoints.set(action, [])
      }
      endpoints.get(action).push(`${rel(file)}:${lineOf(source, m.index)}`)
    }
  }
  return endpoints
}

/** 前端端点：只扫生产代码（排除测试与演示适配器） */
function collectFrontend() {
  const uses = new Map()
  for (const file of walk(FRONT_DIR)) {
    const short = rel(file)
    if (short.includes('__tests__/') || short.startsWith('src/demo/')) {
      continue
    }
    const source = fs.readFileSync(file, 'utf8')
    for (const re of [URL_CONST_RE, API_CALL_RE]) {
      re.lastIndex = 0
      for (const m of source.matchAll(re)) {
        // 只取动作段：`getDetail/${id}` 这类路径片段按第一段判定
        const action = m[1].replace(/^\//, '').split('/')[0]
        if (!action) {
          continue
        }
        if (!uses.has(action)) {
          uses.set(action, [])
        }
        uses.get(action).push(`${short}:${lineOf(source, m.index)}`)
      }
    }
  }
  return uses
}

const backend = collectBackend()
const frontend = collectFrontend()

const missing = [...frontend.entries()]
  .filter(([action]) => !backend.has(action))
  .sort(([a], [b]) => a.localeCompare(b))

const failures = []
if (backend.size < MIN_BACKEND_ENDPOINTS) {
  failures.push(`后端端点只扫到 ${backend.size} 条（下限 ${MIN_BACKEND_ENDPOINTS}）—— 扫描面失效，门禁拒绝判定`)
}
if (frontend.size < MIN_FRONT_ENDPOINTS) {
  failures.push(`前端端点只扫到 ${frontend.size} 条（下限 ${MIN_FRONT_ENDPOINTS}）—— 扫描面失效，门禁拒绝判定`)
}
for (const [action, where] of missing) {
  failures.push(`前端调用了后端不存在的端点 \`${action}\`：${where.join('、')}`)
}

const report = {
  backendEndpoints: backend.size,
  frontendEndpoints: frontend.size,
  missing: missing.map(([action, where]) => ({ action, where })),
  failures,
}

if (asJson) {
  console.log(JSON.stringify(report, null, 2))
} else {
  console.log('\n🔌 前后端端点契约对账（前端生产代码 → 后端 @*Mapping 字面量）\n')
  console.log(`  后端端点字面量：${backend.size} 个`)
  console.log(`  前端端点字面量：${frontend.size} 个（已排除 __tests__ 与 src/demo）\n`)
  if (verbose) {
    console.log(`  后端端点：${[...backend.keys()].sort().join(', ')}\n`)
  }
  if (failures.length) {
    for (const failure of failures) {
      console.log(`  ✗ ${failure}`)
    }
    console.log('')
  } else {
    console.log('✓ 通过：前端用到的每个端点在后端都有映射；扫描面非空\n')
  }
}

process.exit(failures.length ? 1 : 0)
