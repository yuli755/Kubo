import { execFileSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 门禁的反例测试集合（门禁自己也要能被证伪）
 *
 * 覆盖三道门禁：后端分层（`check-layering.mjs`）、前端分层（`check-front-layering.mjs`）、
 * Java 声明级（`check-java-declarations.mjs`）。
 *
 * 2026-09-18 的教训：`check-layering.mjs` 曾经**解析不到 `import static X.Y.*;`** ——
 * 正则 `[\w.]+` 遇到通配符整行不匹配，于是通配符静态导入成了免检通道。
 * 那次是手工塞探针文件才发现的，而手工探针不会留在仓库里：
 * 下一批重构的人照样会误以为每一行都被看过。
 *
 * 所以这里把"门禁必须会红"钉成契约：拿**故意违规**的夹具树跑真实脚本，逐个规则断言。
 *
 * 夹具放在 `infra/fixtures/`（不在 `src/` 下）：它们不能被 `vue-tsc` 类型检查、
 * 也不该被 eslint 或任何门禁当成真代码 —— `infra/**` 在 eslint 的 ignores 里，
 * tsconfig 的 include 也只有 `src/**`。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const JAVA_GATE = join(ROOT, 'infra', 'scripts', 'check-layering.mjs')
const FRONT_GATE = join(ROOT, 'infra', 'scripts', 'check-front-layering.mjs')
const DECL_GATE = join(ROOT, 'infra', 'scripts', 'check-java-declarations.mjs')
const DECL_FIXTURE = join(ROOT, 'infra', 'fixtures', 'java-decl')
const JAVA_FIXTURE = join(ROOT, 'infra', 'fixtures', 'layering-java')
const FRONT_FIXTURE = join(ROOT, 'infra', 'fixtures', 'layering-front')

interface Violation {
  rule: string
  file: string
  detail: string
}

interface GateResult {
  files: number
  violations: Violation[]
  allowlist?: number
  stale?: unknown[]
}

/** 跑门禁脚本（`--json`）；夹具根目录通过 KUBO_ROOT 注入（两个脚本都支持） */
function runGate(script: string, fixture: string): GateResult {
  const stdout = execFileSync(process.execPath, [script, '--json'], {
    env: { ...process.env, KUBO_ROOT: fixture },
    encoding: 'utf8',
  })
  return JSON.parse(stdout)
}

/** `--check` 模式必须真的以非零退出（否则 CI 里永远绿） */
function checkExitCode(script: string, fixture: string): number {
  try {
    execFileSync(process.execPath, [script, '--check'], {
      env: { ...process.env, KUBO_ROOT: fixture },
      stdio: 'pipe',
    })
    return 0
  }
  catch (error) {
    return (error as { status?: number }).status ?? 0
  }
}

describe('后端分层门禁 · 反例（L0–L7 每条都要会红）', () => {
  const result = runGate(JAVA_GATE, JAVA_FIXTURE)

  it('每一层规则都能被点出来', () => {
    const rules = new Set(result.violations.map(v => v.rule))
    for (const rule of ['L0', 'L2', 'L3', 'L4', 'L5', 'L6', 'L7']) {
      expect(rules, `${rule} 的对应用例没被门禁抓到：这条规则其实是摆设`).toContain(rule)
    }
  })

  it('通配符静态导入不得成为免检通道（曾经的盲区）', () => {
    const wildcard = result.violations.find(v => v.file.endsWith('base/BadWildcard.java'))
    expect(wildcard, 'import static …module…*；没被解析 → 这一行等于没人看').toBeDefined()
    expect(wildcard!.rule).toBe('L2')
    expect(wildcard!.detail).toContain('SerialNumberUpdate')
  })

  it('跨域只拦数据面：别人的 service 不算违规', () => {
    const crossDomain = result.violations.filter(v => v.rule === 'L6')
    expect(crossDomain.length, '夹具里只有 1 处跨域数据面越界').toBe(1)
    expect(crossDomain[0].detail).toContain('repository')
  })

  it('common 的装配类例外不误报', () => {
    const files = result.violations.map(v => v.file)
    expect(files.some(f => f.endsWith('common/BadCommon.java')), 'common 依赖 module 必须报 L7').toBe(true)
    expect(files.some(f => f.endsWith('common/AssemblyOk.java')), '@Configuration 装配类被误报成越层').toBe(false)
  })

  it('夹具本身不依赖白名单，且违规数量与规则一一对应', () => {
    expect(result.allowlist, '夹具不该依赖白名单放行').toBe(0)
    expect(result.stale?.length ?? 0).toBe(0)
    expect(result.violations.length, '夹具树里每一处违规都应当有名字（多出来的是误报）').toBe(8)
  })

  it('--check 时以非零退出码收场', () => {
    expect(checkExitCode(JAVA_GATE, JAVA_FIXTURE), '门禁只打印不退出码 = CI 里永远绿').toBe(1)
  })
})

describe('前端分层门禁 · 反例（LF1–LF4 每条都要会红）', () => {
  const result = runGate(FRONT_GATE, FRONT_FIXTURE)

  it('四条规则都能被点出来', () => {
    const rules = new Set(result.violations.map(v => v.rule))
    for (const rule of ['LF1', 'LF2', 'LF3', 'LF4']) {
      expect(rules, `${rule} 的对应用例没被门禁抓到：这条规则其实是摆设`).toContain(rule)
    }
  })

  it('注释里的示例不算依赖（本仓真实踩过的假违规）', () => {
    const files = result.violations.map(v => v.file)
    expect(
      files.some(f => f.endsWith('composables/ok.ts')),
      'JSDoc 示例里写着 import(\'@/view/home.vue\') 就报红：文档让读者这么用，门禁却拦 —— 假违规会逼人删文档',
    ).toBe(false)
    expect(files.some(f => f.endsWith('composables/bad.ts')), '真代码里的 view 依赖必须报 LF1').toBe(true)
  })

  it('`.vue` 只看 `<script>`：`<style>` 里的 @import 不是依赖', () => {
    const files = result.violations.map(v => v.file)
    expect(
      files.some(f => f.endsWith('framework/styleOnly.vue')),
      '<style> 块里的 @import 被当成依赖：会得到一堆假边',
    ).toBe(false)
  })

  it('组合根、接缝方向与白名单各归各位', () => {
    const files = result.violations.map(v => v.file)
    expect(files.some(f => f.endsWith('config/routes.ts')), '组合根（路由表）认识视图是它的职责').toBe(false)
    expect(files.some(f => f.endsWith('framework/transport.ts')), '框架声明契约（不认识应用面）不该报红').toBe(false)
    expect(files.some(f => f.endsWith('api/axios.ts')), '应用把实例注册给框架（api → framework）是正确方向').toBe(false)
    expect(result.allowlist, '前端门禁没有白名单了').toBe(0)
    expect(result.stale?.length ?? 0).toBe(0)
  })

  it('数据层（base 与 model）共用 LF3 判据', () => {
    const lf3 = result.violations.filter(v => v.rule === 'LF3').map(v => v.file)
    expect(lf3, 'base/ 与 model/ 都属数据层，各要有一条 LF3 用例').toEqual([
      'src/base/bad.base.ts',
      'src/model/bad.model.ts',
    ])
  })

  it('夹具违规数量与规则一一对应，且 --check 以非零退出', () => {
    // LF1 命中三处（base/bad.base.ts、composables/bad.ts、model/bad.model.ts 各有 view 依赖）
    expect(result.violations.length, '夹具树里每一处违规都应当有名字（多出来的是误报）').toBe(7)
    expect(checkExitCode(FRONT_GATE, FRONT_FIXTURE), '门禁只打印不退出码 = CI 里永远绿').toBe(1)
  })
})

describe('声明级门禁（Java）· 反例（javac 会拒绝、脚本最容易撞出来的两类）', () => {
  const result = runGate(DECL_GATE, DECL_FIXTURE)

  it('形参重名与字段重名都要点出来', () => {
    const rules = new Set(result.violations.map(v => v.rule))
    expect(rules, 'D1（形参重名）漏了：L2b 就是这么把重复参数提交进仓库的').toContain('D1')
    expect(rules, 'D2（字段重名）漏了').toContain('D2')
    expect(result.violations.length, '夹具里恰好 2 处命中（多出来的是误报）').toBe(2)
  })

  it('局部变量与字段同名不算错（第一版判据在这里误报过）', () => {
    const details = result.violations.map(v => `${v.rule} ${v.detail}`)
    expect(
      details.some(d => d.includes('nickname')),
      '把局部变量当字段：会报出一堆假命中，然后所有人开始忽略这道门禁',
    ).toBe(false)
  })

  it('--check 以非零退出码收场', () => {
    expect(checkExitCode(DECL_GATE, DECL_FIXTURE)).toBe(1)
  })
})
