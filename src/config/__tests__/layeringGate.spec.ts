import { execFileSync } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 分层门禁的反例测试（门禁自己也要能被证伪）
 *
 * 2026-09-18 的教训：`check-layering.mjs` 曾经**解析不到 `import static X.Y.*;`** ——
 * 正则 `[\w.]+` 遇到通配符整行不匹配，于是通配符静态导入成了免检通道。
 * 那次是我手工塞了个探针文件才发现"门禁其实没在拦"，而手工探针不会留在仓库里：
 * 下一批重构的人照样会误以为 445 个文件都被看过了。
 *
 * 所以这一组用例把"门禁必须会红"钉成契约：拿一份**故意违规**的夹具树跑真实脚本，
 * 断言每个规则（L0 / L2 / L2-通配符 / L3 / L4 / L5 / L6 / L7）都能被点出来，
 * 同时断言 L7 的装配类例外**不会**被误报。
 *
 * 夹具在 `src/config/__tests__/fixtures/layering/`：它只被本用例读取，
 * 不参与 `SPMS-Server` 的任何编译与门禁扫描（那些只扫 `SPMS-Server/src`）。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const FIXTURE = join(ROOT, 'src', 'config', '__tests__', 'fixtures', 'layering')
const SCRIPT = join(ROOT, 'infra', 'scripts', 'check-layering.mjs')

interface Violation {
  rule: string
  file: string
  detail: string
}

/** 跑门禁脚本（`--json`），返回结构化结果；夹具根目录通过 KUBO_ROOT 注入（脚本本来就支持） */
function runGate(): { violations: Violation[], files: number, allowlist: number, stale: unknown[], status: number } {
  const stdout = execFileSync(process.execPath, [SCRIPT, '--json'], {
    env: { ...process.env, KUBO_ROOT: FIXTURE },
    encoding: 'utf8',
  })
  return JSON.parse(stdout)
}

describe('layering gate · 反例（门禁必须真的会红）', () => {
  const result = runGate()

  it('每一层规则都能被点出来', () => {
    const rules = new Set(result.violations.map(v => v.rule))
    for (const rule of ['L0', 'L2', 'L3', 'L4', 'L5', 'L6', 'L7']) {
      expect(rules, `${rule} 的对应用例没被门禁抓到：这条规则其实是摆设`).toContain(rule)
    }
  })

  it('通配符静态导入不得成为免检通道（曾经的盲区）', () => {
    const wildcard = result.violations.find(v => v.file.endsWith('base/BadWildcard.java'))
    expect(wildcard, 'import static …module…*；没被解析 → 这一行等于没人看')
      .toBeDefined()
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

  it('夹具本身是干净白名单：违规数量与规则一一对应', () => {
    expect(result.allowlist, '夹具不该依赖白名单放行').toBe(0)
    expect(result.stale.length).toBe(0)
    expect(result.violations.length, '夹具树里每一处违规都应当有名字（多出来的是误报）').toBe(8)
  })

  it('--check 时以非零退出码收场（CI 才会真的红）', () => {
    let status = 0
    try {
      execFileSync(process.execPath, [SCRIPT, '--check'], {
        env: { ...process.env, KUBO_ROOT: FIXTURE },
        stdio: 'pipe',
      })
    }
    catch (error) {
      status = (error as { status?: number }).status ?? 0
    }
    expect(status, '门禁只打印不退出码 = CI 里永远绿').toBe(1)
  })
})
