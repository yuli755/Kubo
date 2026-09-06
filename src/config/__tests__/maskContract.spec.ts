import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 脱敏契约门禁（前端 F2 的卡点）
 *
 * 下面每一条都来自实际踩到的坑，不是"看起来该有"（清单长了就别再数条数，数字会烂在文档里）：
 *
 * 1. **前端 `MaskType` 必须覆盖后端 `ColumnMaskingService#applyMask` 的全部分支**。
 *    后端早已支持 `LAST4` / `HASH`，前端 union 里只有 3 个值 —— 后果不是编译失败，
 *    而是前端落进"未知类型"分支被按 `****` 处理（过度遮蔽），排查时两头都"没错"。
 * 2. **前端写的 `resourceKey` 必须是后端注册过的数据资源**。写错一个字母，
 *    `my-mask` 永远返回 `null` = "该字段无需脱敏"，脱敏静默失效（fail-open）。
 * 3. **只有后端真的提供 export 端点的实体才允许开 `showExport`**。`BaseController`
 *    全局 `@Extends(exclude = {Curd.Export, Curd.QueryExport})`，目前只有 `Material`
 *    加回来；导出走文件流不经过 Jackson，是脱敏的已知旁路，前端别随手开。
 * 4. **模型里的敏感字段必须显式表态**：`@Table({ desensitize })` 或 `mask-exempt: 理由`。
 *    框架在列表渲染层认这个元数据，一处声明全视图生效；漏了就是"某个页面的手机号裸奔"。
 * 5. **`useMaskPolicy(resourceKey, fields)` 里的字段名必须真的是后端那个实体上被
 *    `@SensitiveField` 标记的字段**。这是比资源键更容易犯的错（复制粘贴改一个词），
 *    后果同第 2 条：策略查不到 = 前端认为"无需脱敏"，静默 fail-open，
 *    而界面上一切看起来都正常 —— 所以必须在提交前被机器拦住。
 * 6. **视图层不得自己算"导出能不能带明文"**。判据在 `ExportBypassGuard`/`ExportColumnMasker`，
 *    前端再实现一遍就是第二套真相；面板骗人的代价是管理员开始无视它。
 * 7. **`/dataResource/export-gaps` 这类自查端点的路径必须前后端对得上**。取数失败被刻意做成
 *    沉默（一个自查面板不该打断列表页），于是路径写错在运行期毫无痕迹 —— 只能由 CI 对账。
 * 8. **`infra/scripts/check-*.mjs` 必须真的被 CI 调用**。"脚本存在且本地跑绿"不等于每轮都跑；
 *    而没人调用的门禁与没有门禁等价，更糟的是它给出"已经防住了"的错觉。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java', 'cn', 'hamm', 'spms')

function collect(dir: string, ext: string, out: string[] = []): string[] {
  if (statSync(dir, { throwIfNoEntry: false })?.isDirectory() !== true) {
    return out
  }
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      collect(full, ext, out)
    }
    else if (name.endsWith(ext)) {
      out.push(full)
    }
  }
  return out
}

const read = (path: string): string => readFileSync(path, 'utf-8')

/**
 * Java 源码里只看代码、不看注释
 *
 * 门禁靠正则读后端源码，而注释里的示例（`@SensitiveField(resourceKey = "wms.inventory")`）
 * 与真代码长得一模一样：不过滤的话，"文档里举了个例子"就会被当成"后端注册过这个资源"，
 * 于是资源键写错也能通过门禁 —— 这是自我安慰式的检查，比没有检查更糟。
 */
function readJavaCode(path: string): string {
  return read(path)
    .split('\n')
    .filter(line => !/^\s*(?:\*|\/\/)/.test(line))
    .join('\n')
}

describe('mask contract · 前后端脱敏口径', () => {
  it('前端 MaskType 覆盖后端 applyMask 的全部分支', () => {
    const java = readJavaCode(join(SERVER, 'module', 'system', 'datapermission', 'service', 'ColumnMaskingService.java'))
    const body = java.slice(java.indexOf('private Object applyMask('))
    const backend = [...body.slice(0, 1500).matchAll(/case "([A-Z0-9_]+)"/g)].map(m => m[1]).sort()
    expect(backend.length).toBeGreaterThanOrEqual(5)

    const types = read(join(ROOT, 'packages', 'shared', 'src', 'types', 'mask.ts'))
    const union = (types.match(/export type MaskType =([^\n]+)/)?.[1] ?? '')
      .split('|')
      .map(part => part.trim().replace(/'/g, ''))
      .filter(Boolean)

    // 后端有、前端没有 = 前端会把它当"未知类型"过度遮蔽
    const uncovered = backend.filter(value => !union.includes(value))
    expect(uncovered).toEqual([])
  })

  it('前端引用的 resourceKey 都必须在后端注册（写错会静默 fail-open）', () => {
    const registered = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = readJavaCode(file)
      for (const m of source.matchAll(/setResourceKey\("([^"]+)"\)/g)) {
        registered.add(m[1])
      }
      for (const m of source.matchAll(/@SensitiveField\(resourceKey = "([^"]+)"\)/g)) {
        registered.add(m[1])
      }
    }
    expect(registered.size).toBeGreaterThan(10)

    // 自检：注释过滤真的在起作用。`SensitiveField` 的 javadoc 里放着一个可直接抄的示例键
    // `wms.inventory`，它只应存在于注释中 —— 若哪天示例被删，请连同这两行一起删掉，
    // 不要为了让自检通过而把示例加回代码。
    const annotationFile = join(SERVER, 'module', 'system', 'datapermission', 'annotation', 'SensitiveField.java')
    expect(/@SensitiveField\(resourceKey = "wms\.inventory"\)/.test(read(annotationFile))).toBe(true)
    expect(/@SensitiveField\(resourceKey = "wms\.inventory"\)/.test(readJavaCode(annotationFile))).toBe(false)

    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src'), '.vue'), ...collect(join(ROOT, 'src'), '.ts')]) {
      const source = read(file)
      for (const m of source.matchAll(/use(?:MaskPolicy|ExportVisibility)\(\s*'([^']+)'|resourceKey:\s*'([^']+)'/g)) {
        const key = m[1] ?? m[2]
        if (!registered.has(key)) {
          offenders.push(`${relative(ROOT, file)} → ${key}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('useMaskPolicy 声明的字段必须是后端该资源真实标记过的敏感字段', () => {
    // 后端事实：resourceKey → 被 @SensitiveField 标记的字段名（装饰器紧贴字段，故按"字段上方的块"归属）
    const fieldsByResource = new Map<string, Set<string>>()
    for (const file of collect(SERVER, '.java')) {
      const code = readJavaCode(file)
      if (!code.includes('@SensitiveField')) {
        continue
      }
      let pending: string[] = []
      for (const line of code.split('\n')) {
        // 类型段刻意不容空白（\s 与类型字符集重叠会造出多项回溯），泛型内部允许任意非 > 字符；
        // 因此 `private Map<String, Object> x;` 这类写法会被跳过 —— 本门禁只关心 @SensitiveField
        // 标在简单类型上的字段，扫不到时的后果是"少校验一个字段"，不是误报，可接受。
        const field = line.match(/^\s*private\s+[\w.]+(?:<[^>]*>)?\s+(\w+)\s*[=;]/)
        if (!field) {
          pending.push(line)
          continue
        }
        const key = pending.join('\n').match(/@SensitiveField\(resourceKey = "([^"]+)"\)/)?.[1]
        if (key) {
          if (!fieldsByResource.has(key)) {
            fieldsByResource.set(key, new Set<string>())
          }
          fieldsByResource.get(key)!.add(field[1])
        }
        pending = []
      }
    }
    // 前提自检：后端样本必须非空，否则这条门禁会因为"什么都没解析到"而恒真通过
    expect(fieldsByResource.size).toBeGreaterThanOrEqual(4)
    expect([...fieldsByResource.get('personnel.user') ?? []]).toEqual(
      expect.arrayContaining(['phone', 'email', 'idCard']),
    )

    const offenders: string[] = []
    let checked = 0
    for (const file of [...collect(join(ROOT, 'src', 'view'), '.vue'), ...collect(join(ROOT, 'src', 'view'), '.ts')]) {
      for (const m of read(file).matchAll(/useMaskPolicy\(\s*'([^']+)'\s*,\s*\[([^\]]*)\]/g)) {
        const key = m[1]
        const declared = [...m[2].matchAll(/'([^']+)'/g)].map(x => x[1])
        checked += declared.length
        const known = fieldsByResource.get(key)
        if (!known) {
          offenders.push(`${relative(ROOT, file)} → ${key}（后端没有任何 @SensitiveField 字段，声明字段无意义）`)
          continue
        }
        for (const name of declared) {
          if (!known.has(name)) {
            offenders.push(`${relative(ROOT, file)} → ${key}.${name}（后端该资源只标记了 ${[...known].join('/')}）`)
          }
        }
      }
    }
    expect(checked).toBeGreaterThan(0)
    expect(offenders).toEqual([])
  })

  it('showExport 只允许出现在后端提供 export 端点的实体视图上', () => {
    const exportCapable = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = readJavaCode(file)
      const extendsLine = source.match(/@Extends\(\{([^}]*)\}\)/)?.[1] ?? ''
      if (!extendsLine.includes('Export') || extendsLine.includes('exclude')) {
        continue
      }
      // 用 controller 所在目录推断模块路径，例如 .../module/asset/material → asset/material
      const rel = relative(SERVER, file).split(/[\\/]/)
      const modulePath = rel.slice(1, 3).join('/')
      exportCapable.add(modulePath)
    }
    expect([...exportCapable]).toEqual(expect.arrayContaining(['asset/material']))

    const offenders: string[] = []
    const viewRoot = join(ROOT, 'src', 'view', 'console')
    for (const file of collect(viewRoot, '.vue')) {
      if (!/showExport|:show-export/.test(read(file))) {
        continue
      }
      const rel = relative(viewRoot, file).split(/[\\/]/).slice(0, 2).join('/')
      if (!exportCapable.has(rel)) {
        offenders.push(`${relative(ROOT, file)}（后端 ${rel} 没有 export 端点）`)
      }
    }
    expect(offenders).toEqual([])
  })
})

describe('mask contract · 模型元数据', () => {
  // 只用 .test()，因此分组一律非捕获（捕获组会被 lint 判为无用）
  const SENSITIVE_FIELD = /^(?:phone|mobile|tel|telephone|mobilePhone|email|mail|idCard|idNo|bankCard|bankAccount|cardNo|contactPhone)$/

  it('实体里的敏感字段必须声明 desensitize 或写明豁免理由', () => {
    const missing: string[] = []
    for (const file of collect(join(ROOT, 'src', 'model'), '.ts')) {
      const lines = read(file).split('\n')
      // pending = 当前字段上方的装饰器与注释（生成代码里装饰器总是紧贴字段）
      let pending: string[] = []
      lines.forEach((line, index) => {
        const field = line.match(/^\s*(\w+)\s*[!?]:/)
        if (!field) {
          pending.push(line)
          return
        }
        if (SENSITIVE_FIELD.test(field[1])) {
          const block = pending.join('\n')
          const declared = /@Table\([\s\S]*?desensitize/.test(block)
          const exemptMark = block.match(/mask-exempt:([^\n]*)/)
          if (!declared && !exemptMark) {
            missing.push(`${relative(ROOT, file)}:${index + 1} ${field[1]}（补 @Table({ desensitize }) 或写 mask-exempt: 理由）`)
          }
          if (!declared && exemptMark && exemptMark[1].replace(/[^0-9A-Z\u4E00-\u9FA5]/gi, '').length < 4) {
            missing.push(`${relative(ROOT, file)}:${index + 1} ${field[1]} 的 mask-exempt 必须写理由`)
          }
        }
        pending = []
      })
    }
    expect(missing).toEqual([])
  })
})
/**
 * 去掉注释只留代码（与 readJavaCode 同一立场：注释里的示例不该参与门禁判定，
 * 反之也不该因为"某行注释提到了 ****"就报违规）
 */
function stripComments(source: string): string {
  return source
    .split('\n')
    .filter(line => !/^\s*(?:\/\/|\*)/.test(line))
    .join('\n')
}

describe('mask contract · 脱敏实现只允许存在一处', () => {
  /** 允许直接使用底层脱敏原语的白名单：策略层与指令层本身 */
  const ALLOWED = new Set(['src/directives/mask.ts', 'src/composables/useMaskPolicy.ts'])
  const FORBIDDEN = [
    /\blooksMasked\s*\(/,
    /\bapplyMaskPolicy\s*\(/,
    /\bnormalizeMaskPolicy\s*\(/,
    /['"`]\*{2,}['"`]/,
  ]

  it('视图层不得自行判断/生成掩码（否则又出现第二套实现）', () => {
    const offenders: string[] = []
    for (const file of collect(join(ROOT, 'src', 'view'), '.vue')) {
      if (ALLOWED.has(relative(ROOT, file))) {
        continue
      }
      const code = stripComments(read(file))
      FORBIDDEN.forEach((pattern, index) => {
        if (pattern.test(code)) {
          offenders.push(`${relative(ROOT, file)} 命中规则 ${index}（视图只该消费 useMaskPolicy / v-mask / 模型 desensitize）`)
        }
      })
    }
    expect(offenders).toEqual([])
  })

  it('视图层不得自己算"导出能不能带明文"（判据只有一份，在后端）', () => {
    /**
     * 导出的授权语义是 `actionName=EXPORT && allowPlaintext=true`，后端已有两处实现
     * （`ExportBypassGuard.allGrantedForExport` 与 `ExportColumnMasker.grantsPlaintextExport`）。
     * 视图里再写一次第三份，漂移之后表现是"提示说能导、请求被拒"或相反 —— 提示一旦骗人就被无视，
     * 比没有提示更糟。所以 `my-export-visibility` 只回结论（字段 → 布尔），
     * 这里守住"结论不许在视图里重算"这条边界。
     */
    // 判据是"比较运算"，不是"出现字段名"：策略编辑器的表单里 `<AFormField field=\"allowPlaintext\" />`
    // 是管理员在**写**策略，完全正当。一刀切禁名字会逼人在模板里绕路，把门禁做成误报机器
    // ——这种规则第一次红错就会被加白名单，之后就没人信它了。
    const VERDICT = /allowPlaintext\s*(?:===|!==|==|!=|[?<>]|&&|\|\|)|!\s*[\w.]*allowPlaintext|Boolean\([^)]*allowPlaintext/i
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view'), '.vue'), ...collect(join(ROOT, 'src', 'view'), '.ts')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (VERDICT.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders).toEqual([])

    // 自检：这条规则扫得到东西才算数 —— 模型与 composable 里必须真的出现过这个字段名，
    // 否则某天它被改名，本用例会安静地永远通过。
    const model = read(join(ROOT, 'src', 'model', 'system', 'datapermission', 'DataFieldPolicyEntity.ts'))
    expect(model).toContain('allowPlaintext')
    const composable = read(join(ROOT, 'src', 'composables', 'useExportVisibility.ts'))
    expect(composable).toContain('getMyExportVisibility')
    // 规则本身要能红：在临时字符串上验一次比较运算被识别（不依赖真的有人写错视图）
    expect(VERDICT.test('if (row.allowPlaintext === true) {')).toBe(true)
    expect(VERDICT.test('<AFormField field=\"allowPlaintext\" />')).toBe(false)
  })

  it('导出缺口自查的前后端路径必须对得上（它失败是静默的）', () => {
    /**
     * `useExportGapReport` 的 catch 分支刻意只标记 `failed`、不弹错：一个自查面板不该把列表页打断。
     * 代价是"路径写错"这件事在运行期毫无痕迹 —— 404 被吞掉，界面从此只显示"未能取得结果"，
     * 久了没人记得它本该显示什么。所以拼写必须由 CI 对账，而不是靠人点进去看。
     */
    const api = read(join(ROOT, 'src', 'api', 'system.ts'))
    expect(api).toContain('request.get(\'/dataResource/export-gaps\')')

    const controllerPath = join(SERVER, 'module/system/datapermission/controller/DataResourceController.java')
    expect(readJavaCode(controllerPath)).toContain('@GetMapping("export-gaps")')
    expect(readJavaCode(controllerPath)).toContain('@Api("dataResource")')

    // 反向：前端不许把这条自查挂到业务用户也会调的控制器上（跨角色策略地图）
    expect(api).not.toMatch(/request\.get\('\/dataFieldPolicy\/export-gaps'\)/)
  })

  it('缺口条目的字段名前后端一致，且编辑器真的在用它', () => {
    /**
     * 两侧各写一遍字段名，就是两个会独立烂掉的真相：后端 record 改个组件名（或 Jackson 版本不支持
     * record ⇒ 序列化成 `{}`），前端 TS 类型不会报错，界面只是"永远没有缺口"。
     * 所以字段清单在这里对账；序列化能力本身由后端用例守（FieldPolicyGapPayloadContractTest）。
     */
    const names = ['resourceId', 'resourceKey', 'fieldName', 'roleCode', 'reason']

    const types = read(join(ROOT, 'src', 'api', 'system', 'types.ts'))
    const ifaceStart = types.indexOf('export interface ExportGapItem')
    expect(ifaceStart, '前端 ExportGapItem 类型不见了，本用例已失效').toBeGreaterThan(-1)
    const iface = types.slice(ifaceStart, ifaceStart + 1200)
    for (const name of names) {
      expect(iface, `前端类型缺字段 ${name}`).toContain(`  ${name}:`)
    }

    const java = readJavaCode(join(SERVER, 'module', 'system', 'datapermission', 'service', 'DataResourceService.java'))
    const recordStart = java.indexOf('public record ExportGapItem(')
    expect(recordStart, '后端 ExportGapItem record 不见了，本用例已失效').toBeGreaterThan(-1)
    const record = java.slice(recordStart, recordStart + 400)
    for (const name of names) {
      expect(record, `后端 record 缺组件 ${name}`).toContain(` ${name}`)
    }

    /**
     * 回显必须落到编辑器：只做列表页汇总的话，管理员在"该补哪条"的决策窗口里仍然什么都看不见，
     * 而这个用例正是为了防止那半边被后续重构悄悄删掉（删提示不会有任何测试变红）。
     */
    const editor = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'fieldPolicy', 'component', 'editor.vue'))
    expect(editor).toContain('useExportGapReport')
    expect(editor).toContain('gapsForField')
    // 只查标识符是假门禁：把 import 删掉时上面两条仍然绿（函数体里还有这些名字），
    // 而真正的回归长这样"提示不再渲染"。所以直接断模板，并钉住"文案取后端 reason"。
    expect(editor, '编辑器没把缺口提示渲染到表单上').toMatch(/<ElAlert[^>]*v-if="fieldGaps\(\)\.length"/)
    expect(editor).toContain('g.reason')
    // 反向：编辑器不许自己下结论（判据只有一份）——缺口条目里的 allowPlaintext 一律不许碰
    expect(/gap\.allowPlaintext|fieldGaps\(\)\s*(?:===|!==)/.test(editor)).toBe(false)
  })

  it('每个 infra 自查脚本都必须真的被 CI 调用（本地跑过 != 每轮都跑）', () => {
    /**
     * 本轮差点凭"ci.yml 里 grep 不到 check-source-text"就断言门禁没接 CI —— 实际接线是
     * `run: pnpm text:check`，中间隔了一层 package.json 别名。反向的风险更实在：
     * 以后新增一个 check-*.mjs 忘了接进 CI，它就成了只在作者机器上跑过的装饰品。
     * 这里按"文件名 → 别名 → CI 步骤"三层追一遍，断任何一层都红。
     */
    const pkg = JSON.parse(read(join(ROOT, 'package.json'))) as { scripts: Record<string, string> }
    const ci = read(join(ROOT, '.github', 'workflows', 'ci.yml'))
    const scriptFile = (cmd: string) => cmd.match(/node\s+(infra\/scripts\/[\w.-]+\.mjs)/)?.[1]
    const runAliases = [...ci.matchAll(/^\s*run:\s*pnpm\s+([\w:.-]+)/gm)].map(m => m[1])
    const covered = new Set(runAliases.map(alias => scriptFile(pkg.scripts[alias] ?? '')).filter(Boolean))

    const scripts = readdirSync(join(ROOT, 'infra', 'scripts')).filter(f => f.startsWith('check-') && f.endsWith('.mjs'))
    // 自检：扫描本身要有效，否则这条用例会安静地永远通过
    expect(scripts.length, '扫不到 infra/scripts/check-*.mjs：目录或命名已变，本用例失效').toBeGreaterThan(0)

    for (const name of scripts) {
      expect(covered.has(`infra/scripts/${name}`), `${name} 没有任何 CI 步骤调用它（只在本地跑 = 没有门禁）`).toBe(true)
    }
  })

  it('白名单确实存在（防止规则因为文件被改名而空转）', () => {
    const directive = read(join(ROOT, 'src', 'directives', 'mask.ts'))
    const composable = read(join(ROOT, 'src', 'composables', 'useMaskPolicy.ts'))
    expect(directive).toContain('applyMaskPolicy')
    expect(composable).toContain('ensureResourceMaskPolicies')
  })
})

it('`x-visibility` 只能作为"已作废"出现在文档里，不能进代码', () => {
  /**
   * 计划文档里那个"响应头带列可见性"的通道从未实现过，却一直被当成待办引用（§19.11 第 5 条）。
   * 真正的风险不是文档措辞，而是**有人照着实现一遍**：权限判定的输入一旦由浏览器读到的头决定，
   * 遮不遮就成了客户端可左右的事。所以这里不是整理文字，是钉住一条实现边界：
   * 前后端源码里出现 `x-visibility`（任意大小写）即红；文档里出现则必须带"作废/不存在"字样。
   */
  const code = [
    ...collect(join(ROOT, 'src'), '.ts'),
    ...collect(join(ROOT, 'src'), '.vue'),
    ...collect(join(SERVER, 'module/system/datapermission'), '.java'),
    ...collect(join(SERVER, 'common'), '.java'),
  ]
  // 扫描要排除测试自身：本用例的标题里就写着这个词，不排除的话门禁会被自己绊倒
  // （同一类错误在"资源键门禁"那次也踩过：拿仓库里的真实值当自检样本）。
  const offenders = code
    .filter(file => !file.includes('__tests__'))
    .filter(file => /x-visibility/i.test(read(file)))
  expect(offenders.map(f => relative(ROOT, f)), '受限列元数据只允许走 my-mask-map').toEqual([])
})

describe('mask contract · 界面文案与后端能力对齐', () => {
  /**
   * 策略编辑器里的 tip 是唯一会被人当成"能不能这么配"依据的地方。
   * 它一旦与后端实现漂移，代价不是显示错误，而是**运维照着过时的话说"配了没用"**，
   * 或者反过来在能力其实没上线时提前放开 enforce。所以这里做双向绑定，而不是只测"文案别写错别字"。
   */
  const tips = (dir: string): string =>
    collect(join(ROOT, 'src/view/console/system/datapermission', dir), '.vue')
      .map(file => read(file))
      .join('\n')

  const STALE_CLAIM = /暂不生效|尚未接线|暂未被消费|未被消费|不生效/

  it('eXPORT 的文案与 ExportBypassGuard 的存在性必须一致（双向）', () => {
    const guard = join(SERVER, 'module/system/datapermission/service/ExportBypassGuard.java')
    const hasGuard = statSync(guard, { throwIfNoEntry: false })?.isFile() === true
    const tip = tips('fieldPolicy')

    expect(tip).toContain('EXPORT')
    if (hasGuard) {
      // 能力已在：不许还写着"配了不生效"——那是让人不去配授权，等于把 fail-closed 变成事实上的全拒
      expect(tip).not.toMatch(STALE_CLAIM)
      expect(tip).toContain('明文')
    }
    else {
      // 能力被撤：文案必须回到"未接线"，否则界面在承诺后端没有的行为
      expect(tip).toMatch(STALE_CLAIM)
    }
  })

  it('fACTORY 的文案与后端默认值必须一致，且 tip 引用的类名要真存在', () => {
    const props = read(join(SERVER, 'module/system/datapermission/config/DataPermissionProperties.java'))
    // 默认值就是"生产未就绪"的机器事实：翻成 true 那天，这段文案必须同步改
    const factoryOff = /factoryDimensionEnabled\s*=\s*false/.test(props.replace(/\s+/g, ''))
    const tip = tips('policy')

    if (factoryOff) {
      expect(tip).toContain('user_factory')
      expect(tip).toMatch(/未落地|会被.*拒绝|被拒/)
    }
    else {
      expect(tip).not.toMatch(/未落地/)
    }

    // tip 里点名的后端类必须存在（引用一个不存在的类，比不写更误导）
    for (const cls of tip.match(/\b[A-Z][A-Za-z0-9]*Guard\b/g) ?? []) {
      const hit = collect(join(SERVER, 'module/system/datapermission'), '.java')
        .some(file => file.endsWith(`/${cls}.java`))
      expect(hit, `tip 引用的 ${cls} 在后端找不到`).toBe(true)
    }
  })
})

describe('mask contract · 数据权限动作字典 ↔ 后端枚举', () => {
  /**
   * 动作名是**字符串存的**（`data_field_policy.action_name`），前后端没有编译期耦合：
   * 前端少一个成员，管理员就选不到那个动作（配不出来 = 该能力在界面上不存在）；
   * 前端多一个成员，就能配出一条后端永远查不到的策略（看起来配了，实际 fail-open）。
   * 两种漂移都不会让任何一方报错，所以必须在这里对账 —— 而且**读的是 Java 源码**，
   * 不是这份测试里手抄一份名单（抄的那份同样会漂移）。
   */
  const ENUM = join(SERVER, 'module/system/datapermission/enums/DataPermissionAction.java')
  const DICT = join(ROOT, 'src/model/system/datapermission/DataPermissionActionType.ts')

  const backendActions = (): string[] => {
    const java = readJavaCode(ENUM)
    const body = java.slice(java.indexOf('enum DataPermissionAction'))
    const members = [...body.matchAll(/^ {4}([A-Z][A-Z0-9_]*)[,;]?$/gm)].map(m => m[1])
    // 解析不到成员 = 后端把枚举改了形状（换成常量类/加了注释块），这时"两边都空"会假绿
    expect(members.length, '没解析到后端 DataPermissionAction 成员：解析逻辑已失效').toBeGreaterThan(0)
    return members
  }

  interface DictEntry { name: string, value: string, label: string, disabled: boolean }

  const dictEntries = (): DictEntry[] => {
    // 按行扫而不是一个大正则：这个文件里每个成员是 `static readonly X = new Y('X', 标签)`，
    // 后面跟着若干 `.setColor(...)` 链式调用、**行尾没有分号**，
    // 指望 `...);` 收尾的正则会一条都不匹配，而"匹配不到"在门禁里长得像"通过"。
    const lines = read(DICT).split(/\r?\n/)
    const entries: DictEntry[] = []
    for (let i = 0; i < lines.length; i++) {
      const m = /^\s*static readonly ([A-Z][A-Z0-9_]*)\s*=\s*new [A-Za-z]+\('([A-Z0-9_]+)',\s*'([^']*)'\)/.exec(lines[i])
      if (!m) {
        continue
      }
      let j = i + 1
      let tail = ''
      while (j < lines.length && /^\s*\.\w+\(/.test(lines[j])) {
        tail += lines[j]
        j++
      }
      i = j - 1
      expect(m[1], `键名 ${m[1]} 与字典值 ${m[2]} 不一致（后端按值匹配）`).toBe(m[2])
      entries.push({ name: m[1], value: m[2], label: m[3], disabled: /setDisabled\(true\)/.test(tail) })
    }
    expect(entries.length, '一个成员都没解析到：字典形状变了，别把空转读成通过').toBeGreaterThan(0)
    return entries
  }

  it('前端字典成员必须与后端枚举一一对应（双向）', () => {
    const backend = backendActions().sort()
    const front = dictEntries().map(e => e.name).sort()
    expect(front).toEqual(backend)
  })

  it('eXPORT 可选性必须与后端导出实现的存在性一致（双向）', () => {
    /**
     * 这一条来自本轮实际发现的状态：`EXPORT` 被 `.setDisabled(true)` 灰掉，注释写的是
     * "导出侧尚未接脱敏" —— 而 `ExportBypassGuard` 与 `ExportColumnMasker` 都已落地。
     * 后果不是"少个选项"：enforce 打开后受限角色**无法被授权**明文导出，全量导出被拒，
     * 且管理端没有入口去修。灰掉一个已实现的能力 = 把 fail-closed 变成事实上的全拒。
     */
    const impl = ['ExportBypassGuard.java', 'ExportColumnMasker.java']
      .map(f => join(SERVER, 'module/system/datapermission/service', f))
    const implemented = impl.every(f => statSync(f, { throwIfNoEntry: false })?.isFile() === true)
    const entry = dictEntries().find(e => e.name === 'EXPORT')
    expect(entry, '字典里没有 EXPORT：后端枚举有，前端就必须能选').toBeTruthy()

    if (implemented) {
      expect(entry!.disabled, '后端导出遮蔽已实现，字典里仍灰掉 EXPORT 会让管理员无法授权').toBe(false)
      // 标签也不许继续承诺"没接线"：那会让人不去配授权，与灰掉等价
      const label = entry!.label
      expect(label).not.toMatch(/未接线|尚未|暂不|没生效|不生效/)
      expect(label).toContain('导出')
    }
    else {
      expect(entry!.disabled, '后端导出遮蔽不在了，前端就不能给一个会静默失效的选项').toBe(true)
    }
  })

  it('字典 tip 引用的后端类必须真实存在', () => {
    // 与 fieldPolicy 编辑器 tip 同一条规矩：写一个不存在的类名，比不写更误导
    const doc = read(DICT)
    for (const cls of doc.match(/\b(?:Export|Masked|Dimension|Column)[A-Za-z0-9]*(?:Guard|Service|Masker)\b/g) ?? []) {
      const hit = collect(join(SERVER, 'module/system/datapermission'), '.java')
        .some(file => file.endsWith(`/${cls}.java`))
      expect(hit, `字典注释引用的 ${cls} 在后端找不到`).toBe(true)
    }
  })
})
