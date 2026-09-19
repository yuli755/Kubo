import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 「类型引用落地」两道门禁的反例测试
 *
 * 背景是 2026-09-19 在 WMS 页面排查里撞见的一串**编译期就错**的残渣（都是「整域删 MES」那批留下的）：
 *
 * - `OutputEntity` 里 `private PickingEntity picking;` —— 类的 import 删了、使用处留下了；
 * - `InputMapper.xml` / `OutputMapper.xml` 的 `javaType` 指向 `module.mes.*.*Entity`；
 * - `MapperReadFallbackTest` 里 6 个已删域的 Mapper 断言；
 * - 另外顺手抓到 `ParticipantRepository.xml` 抄了 Contract 的表名与 resultType（锁错行）；
 * - 4 份数据权限仓储 XML 的 resultType 写成了 `repository.XEntity`（实体其实在 `entity` 包）。
 *
 * 它们能活下来的原因是**没有一道门禁在看这件事**：`java:imports` 只看 import 能不能落地
 * （而这处压根没有 import），沙箱又没有 JDK。于是补了两道机器判据：
 * `pnpm java:symbols`（Java 侧）与 `pnpm mapper:check` 的类型引用项（XML 侧）。
 *
 * 这一组用例守住三件事：
 *   1. **会红**：拿故意违规的夹具树跑真脚本，两条判据（悬空引用 / 缺 import）与 XML 的类型引用都要被抓到；
 *   2. **不空转**：真仓库跑出来必须是 0 问题（顺带证明判据没有在真代码上乱报）；
 *   3. **接进链**：两道门禁必须出现在 `package.json` 的 `check` 与 CI 里 ——
 *      "写了门禁但没人跑"是本仓踩过的坑（`migration:check` 曾 0 个脚本 ⇒ 0 处错误）。
 *
 * 夹具在 `infra/fixtures/**`（不被 eslint / vue-tsc 管），根目录通过 `KUBO_ROOT` 注入。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const SYMBOL_GATE = join(ROOT, 'infra', 'scripts', 'check-java-symbols.mjs')
const MAPPER_GATE = join(ROOT, 'infra', 'scripts', 'check-mapper-namespaces.mjs')
const SYMBOL_FIXTURE = join(ROOT, 'infra', 'fixtures', 'java-symbols')
const MAPPER_FIXTURE = join(ROOT, 'infra', 'fixtures', 'mapper-types')

interface GateRun {
  exitCode: number
  stdout: string
}

/** 跑门禁（`--json`）；门禁发现问题时会以非零码退出，这里把 stdout 从异常里捞回来 */
function runGate(script: string, env: Record<string, string> = {}): GateRun {
  try {
    const stdout = execFileSync(process.execPath, [script, '--json'], {
      env: { ...process.env, ...env },
      encoding: 'utf8',
    })
    return { exitCode: 0, stdout }
  }
  catch (error) {
    const failure = error as { status?: number, stdout?: string }
    return { exitCode: failure.status ?? -1, stdout: failure.stdout ?? '' }
  }
}

describe('java:symbols · 反例（悬空引用 / 缺 import 都要会红）', () => {
  const fixture = runGate(SYMBOL_GATE, { KUBO_ROOT: SYMBOL_FIXTURE, KUBO_MIN_TYPE_POSITIONS: '1' })
  const report = JSON.parse(fixture.stdout || '{"problems":[]}') as {
    scanned: number
    positions: number
    problems: Array<{ file: string, token: string, reason: string }>
  }

  it('整域删除后留下的使用处：报「悬空引用」', () => {
    const dangling = report.problems.filter(item => item.file.endsWith('bad/Bad.java'))
    expect(dangling.map(item => item.token).sort()).toEqual(['DeletedEntity', 'DeletedEnum', 'DeletedMapper', 'DeletedService'])
    expect(dangling.every(item => item.reason.includes('悬空引用'))).toBe(true)
  })

  it('类还在、只是忘了 import：报「缺 import」并给出真实包名', () => {
    const missing = report.problems.filter(item => item.file.endsWith('missing/MissingImport.java'))
    expect(missing.length, '缺 import 这条判据没生效 → 这类问题会一路漏到 javac').toBeGreaterThan(0)
    expect(missing[0].reason).toContain('demo.other.NeighborService')
  })

  it('干净样本不误报（import 齐全 / 同文件声明 / 泛型实参 / java.lang）', () => {
    expect(report.problems.some(item => item.file.endsWith('ok/Ok.java'))).toBe(false)
  })

  it('覆盖到的类型位置数不为 0（判据没空转）', () => {
    expect(report.positions).toBeGreaterThan(0)
    expect(report.scanned).toBeGreaterThan(0)
  })

  it('发现问题时以非零退出（否则 CI 里永远绿）', () => {
    expect(fixture.exitCode, '门禁只打印不退出码 = 没人拦得住').not.toBe(0)
  })
})

describe('mapper:check · 反例（XML 里的类型引用必须落地）', () => {
  const fixture = runGate(MAPPER_GATE, { KUBO_ROOT: MAPPER_FIXTURE, KUBO_MIN_TYPE_REFERENCES: '1' })
  const report = JSON.parse(fixture.stdout || '{"problems":[]}') as {
    unresolvedTypes: Array<{ file: string, attribute: string, value: string }>
    problems: string[]
  }

  it('javaType / resultMap type 指向已删域：逐条点出来', () => {
    const values = report.unresolvedTypes.map(item => item.value).sort()
    expect(values).toEqual(['demo.DeletedEntity', 'demo.gone.LegacyEntity'])
    expect(report.unresolvedTypes.map(item => item.attribute).sort()).toEqual(['javaType', 'type'])
  })

  it('发现问题时以非零退出', () => {
    expect(fixture.exitCode).not.toBe(0)
  })

  it('内建别名与 JDK 全限定名不算问题（`long` / `java.util.Map` 是合法写法）', () => {
    // 真仓库里 92 处引用零问题，等于把「别名 + JDK 前缀」这条放宽口径也一起证伪了
    const real = runGate(MAPPER_GATE)
    const realReport = JSON.parse(real.stdout) as { typeReferences: number, unresolvedTypes: unknown[] }
    expect(real.exitCode, '真仓库的 Mapper XML 必须全绿').toBe(0)
    expect(realReport.typeReferences).toBeGreaterThanOrEqual(40)
    expect(realReport.unresolvedTypes).toEqual([])
  })
})

describe('两道门禁必须真的接在链上（防「写了没人跑」）', () => {
  const pkg = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8')) as { scripts: Record<string, string> }
  const ci = readFileSync(join(ROOT, '.github', 'workflows', 'ci.yml'), 'utf8')

  it('package.json 有脚本定义', () => {
    expect(pkg.scripts['java:symbols']).toContain('check-java-symbols.mjs')
    expect(pkg.scripts['mapper:check']).toContain('check-mapper-namespaces.mjs')
  })

  it('`pnpm check` 全链里两道都在', () => {
    expect(pkg.scripts.check).toContain('pnpm java:symbols')
    expect(pkg.scripts.check).toContain('pnpm mapper:check')
  })

  it('ci 工作流里两道都在', () => {
    expect(ci).toContain('pnpm java:symbols')
    expect(ci).toContain('pnpm mapper:check')
  })

  it('真仓库的 java 类型引用全绿（不空转、不误报）', () => {
    const real = runGate(SYMBOL_GATE)
    expect(real.exitCode, '真仓库必须全绿；若这里红了，先看报告指向哪一处悬空引用').toBe(0)
    expect(JSON.parse(real.stdout).positions).toBeGreaterThanOrEqual(500)
  })
})
