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
 *    全局 `@Extends(exclude = {Curd.Export, Curd.QueryExport})`，二十一个读侧换轨资源
 *    （material / inventory / input / output / move / order / plan / picking / purchase / sale /
 *    customer / supplier / purchasePrice / salePrice / bom / operation / routing / device / contract /
 *    storage / structure）
 *    都已把 `Curd.Export` 加回来并覆写了 `export(QueryListRequest)`；导出走文件流不经过 Jackson，
 *    是脱敏的已知旁路，前端别在没加回来的实体上随手开。
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

// 归一化换行：Windows 工作区 checkout 出 CRLF，而下面的判据全是 LF 语义的正则
// （`row-write:\n` 之类）。在 CI（Linux）与本地（Windows）跑出同一结果，判据本身不变。
const read = (path: string): string => readFileSync(path, 'utf-8').replace(/\r\n/g, '\n')

/** 文件名提取：Windows 的 path.join 产出反斜杠，只按 '/' 切会把整条路径当成文件名 */
const baseName = (p: string): string => p.split(/[\\/]/).pop() ?? p

/**
 * Java 源码里只看代码、不看注释
 *
 * 门禁靠正则读后端源码，而注释里的示例（`@SensitiveField(resourceKey = "wms.inventory")`）
 * 与真代码长得一模一样：不过滤的话，"文档里举了个例子"就会被当成"后端注册过这个资源"，
 * 于是资源键写错也能通过门禁 —— 这是自我安慰式的检查，比没有检查更糟。
 */
function readJavaCode(path: string): string {
  // 把注释行**抹成空行**而不是 filter 掉：门禁的报错文案要带行号，而删行会让编号整体前移
  // （实测把 69 行报成 28 行）。空行对所有 .toContain/.match 判据等价，却保住了"点开就是那一行"。
  return read(path)
    .split('\n')
    .map(line => (/^\s*(?:\*|\/\/)/.test(line) ? '' : line))
    .join('\n')
}

/**
 * 取方法体（按大括号配对）：只看"文件里出现过方法名"不够 —— 接线从钩子里挪走（比如只剩一个私有方法）
 * 照样绿，而那时它已经不再拦任何东西了。
 */
function methodBody(code: string, signature: string): string {
  const at = code.indexOf(signature)
  expect(at, `找不到方法 ${signature}：接线点被改名或移除，本用例要跟着重写`).toBeGreaterThan(-1)
  let depth = 0
  for (let i = code.indexOf('{', at); i < code.length; i++) {
    if (code[i] === '{') {
      depth++
    }
    if (code[i] === '}') {
      depth--
      if (!depth) {
        return code.slice(at, i + 1)
      }
    }
  }
  return ''
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

  it('策略脱敏列必须真的渲染进业务表格（接线被删时先红）', () => {
    /**
     * 审核记录项 2 的钉死：useMaskPolicy 曾被当作"已接好"引用，实际业务表格根本没用它——
     * 半成品基础设施比没有更糟，因为文档会说"已经做了"。所以每个 @SensitiveField 资源
     * 的列表视图必须同时满足：① 调用 useMaskPolicy 并 load；② 模板里有对应列插槽走
     * restricted/masked。声明了策略却没接渲染 = fail-open（列照旧显示后端原值）。
     */
    const WIRING: Array<[string, string, string[]]> = [
      // [视图路径, 资源键, 必须出现在插槽里的字段]
      ['src/view/console/channel/purchasePrice/list.vue', 'channel.purchasePrice', ['price']],
      ['src/view/console/channel/salePrice/list.vue', 'channel.salePrice', ['price']],
      ['src/view/console/asset/material/list.vue', 'asset.material', ['purchasePrice', 'salePrice']],
      ['src/view/console/personnel/user/list.vue', 'personnel.user', ['phone', 'email']],
    ]
    for (const [file, resourceKey, fields] of WIRING) {
      const source = read(join(ROOT, file))
      // 判据写成转义正则而不是示例字符串：示例里的「组合式名 + 资源键」字面量会被
      // 上面的"资源键注册"门禁扫成前端真实引用（它不排除 __tests__）。
      expect(source, `${file} 必须声明资源 ${resourceKey} 的策略组合式`).toMatch(
        new RegExp(String.raw`useMaskPolicy\(\s*'${resourceKey}'`),
      )
      expect(source, `${file} 必须触发策略加载`).toMatch(/Policy\.load\(\)/)
      for (const field of fields) {
        expect(source, `${file} 的 ${field} 列没有渲染策略值（restricted/masked 接线被删）`)
          .toMatch(new RegExp(`restricted\\('${field}'\\)|masked\\('${field}'`))
      }
    }
  })

  it('showExport 只允许出现在后端提供 export 端点的实体视图上', () => {
    const exportCapable = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = readJavaCode(file)
      // 两种 @Extends 写法都要认：@Extends({Curd.Export, ...}) 与
      // @Extends(value = {Curd.Export, ...}, exclude = ...)（exclude 在 value 之外，不算排除 Export）
      const extendsLine = source.match(/@Extends\(\{([^}]*)\}\)/)?.[1]
        ?? source.match(/@Extends\(\s*value\s*=\s*\{([^}]*)\}/)?.[1]
        ?? ''
      if (!extendsLine.includes('Export') || extendsLine.includes('exclude')) {
        continue
      }
      // 用 controller 所在目录推断模块路径，例如 .../module/asset/material → asset/material
      const rel = relative(SERVER, file).split(/[\\/]/)
      const modulePath = rel.slice(1, 3).join('/')
      exportCapable.add(modulePath)
    }
    expect([...exportCapable]).toEqual(expect.arrayContaining([
      'asset/material',
      'asset/device',
      'asset/contract',
      'wms/inventory',
      'wms/input',
      'wms/output',
      'wms/move',
      'mes/order',
      'mes/plan',
      'mes/picking',
      'channel/purchase',
      'channel/sale',
      'channel/customer',
      'channel/supplier',
      'channel/purchaseprice',
      'channel/saleprice',
      'mes/bom',
      'mes/operation',
      'mes/routing',
      'factory/storage',
      'factory/structure',
    ]))

    const offenders: string[] = []
    const viewRoot = join(ROOT, 'src', 'view', 'console')
    for (const file of collect(viewRoot, '.vue')) {
      // Vue 模板里规范写法是 kebab（show-export）；showExport 留给 JSX/对象写法
      if (!/show-export|showExport/.test(read(file))) {
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

  it('存量盘点的前后端路径必须对得上，且视图不许自己判"哪些动作被消费"', () => {
    /**
     * 两条一起写，因为它们守的是同一次改动（inert 盘点）的两半：
     * 路径写错 ⇒ 请求 404 被 composable 的 catch 沉默掉，界面只是永远显示"没有孤儿策略"；
     * 判定搬到前端 ⇒ 视图里出现 `actionName === 'QUERY'` 这类式子，后端加一个消费动作就漂移。
     */
    const api = read(join(ROOT, 'src', 'api', 'system.ts'))
    expect(api).toContain('request.get(\'/dataResource/inert-policies\')')

    const controllerPath = join(SERVER, 'module/system/datapermission/controller/DataResourceController.java')
    expect(readJavaCode(controllerPath)).toContain('@GetMapping("inert-policies")')

    // 反向：这份跨角色的盘点结果不许挂到业务用户也会调的控制器上
    const fieldPolicyController = readJavaCode(join(SERVER, 'module/system/datapermission/controller/DataFieldPolicyController.java'))
    expect(fieldPolicyController).not.toContain('inert-policies')

    // 视图里出现"动作名比较"就是在重写消费集：消费集只该有后端常量与字典里那两份
    const VERDICT = /actionName\s*(?:===|!==|==|!=)\s*['"]/
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view'), '.vue'), ...collect(join(ROOT, 'src', 'view'), '.ts')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (VERDICT.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders, '视图在自行判定动作是否被消费（判据只该在后端）').toEqual([])
    // 自检：这条扫描要认得违规写法，也不能把"取数后展示"当违规
    expect(VERDICT.test('if (row.actionName === \'DETAIL\') {')).toBe(true)
    expect(VERDICT.test('<span>{{ row.actionName }}</span>')).toBe(false)

    // 回显不许被悄悄删掉：删提示不会让任何后端测试变红，所以在这里钉住模板
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'fieldPolicy', 'list.vue'))
    expect(list, '列级策略列表没渲染存量盘点结果').toMatch(/<ElAlert[^>]*v-if="inertCount"/)
    expect(list).toContain('useInertPolicyReport')
  })

  it('行级盘点：路径前后端对得上，且前端不抄判定', () => {
    /**
     * 三个自查端点（export-gaps / inert-policies / row-policy-risks）共用同一套失效模式：
     * composable 的 catch 刻意沉默 ⇒ 路径写错不会有任何界面异常，只会让面板长期显示"没问题"。
     * 所以每加一个盘点，这里就必须多一条对账，否则它等于没接线。
     */
    const api = read(join(ROOT, 'src', 'api', 'system.ts'))
    expect(api).toContain('request.get(\'/dataResource/row-policy-risks\')')

    const controllerPath = join(SERVER, 'module/system/datapermission/controller/DataResourceController.java')
    expect(readJavaCode(controllerPath)).toContain('@GetMapping("row-policy-risks")')

    // 反向：行级策略自己的控制器上没有权限校验之外的额外暴露面，盘点不放那儿
    const policyController = readJavaCode(join(SERVER, 'module/system/datapermission/controller/DataPolicyController.java'))
    expect(policyController).not.toContain('row-policy-risks')

    // 前端不许抄判定：出现 kind 比较 = 在重算风险类别；出现动作/主体白名单 = 在抄消费集
    const COMPARO = /kind\s*(?:===|!==|==|!=)\s*['"]|includes\('(?:QUERY|EXPORT|ROLE|FACTORY)'\)/
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'composables'), '.ts'), ...collect(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission'), '.vue')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (COMPARO.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders, 'composable/视图在自行判风险类别或抄动作白名单').toEqual([])
    // 自检：扫描认得违规、不误伤"把 kind 显示出来"
    expect(COMPARO.test('if (item.kind === \'FACTORY\') {')).toBe(true)
    expect(COMPARO.test('<span>{{ item.kind }}</span>')).toBe(false)

    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'policy', 'list.vue'))
    expect(list, '行级策略列表没渲染盘点结果').toMatch(/<ElAlert[^>]*v-if="riskCount"/)
    expect(list).toContain('useRowPolicyRiskReport')
  })

  it('显式拒绝（EXPLICIT_DENY）类别与运行期 CREATE 放行同源', () => {
    const analyzer = readJavaCode(join(SERVER, 'module/system/datapermission/service/RowPolicyRiskAnalyzer.java'))
    expect(analyzer, '显式拒绝类别被删：CREATE 配 deny:true 会静默放行，而盘点不再提示')
      .toContain('KIND_EXPLICIT_DENY')
    const isDeny = methodBody(analyzer, 'boolean isExplicitDeny(')
    expect(isDeny, '显式拒绝判据不再 parse 条件树：那它就是在猜，与运行期 PermissionRuleParser 分叉')
      .toContain('PARSER.parse(')
    // 判据的锚点是 RowWriteGuard 对 CREATE 直接放行：一旦 CREATE 开始走行级判定，这条类别的前提就变了
    const guard = methodBody(
      readJavaCode(join(SERVER, 'module/system/datapermission/service/RowWriteGuard.java')),
      'public void enforce(',
    )
    expect(guard, 'RowWriteGuard 不再对 CREATE 放行：EXPLICIT_DENY 的判据前提（新建无既存行可判）被改写，类别要跟着重写')
      .toContain('DataPermissionAction.CREATE')
  })
  it('维度可绑定性盘点：路径对账、前端不重算实体形状、判据与运行期同源', () => {
    /**
     * 第四个自查端点。前三个的共同教训是：**composable 的 catch 只置 failed、不弹错**，
     * 于是"路径写错"没有任何运行期痕迹，面板会安静地长期显示"没问题"。
     * 所以每加一个盘点就多一条对账；不加对账的盘点等于没接线。
     */
    const api = read(join(ROOT, 'src', 'api', 'system.ts'))
    expect(api).toContain('request.get(\'/dataResource/dimension-binding-risks\')')

    const controllerPath = join(SERVER, 'module/system/datapermission/controller/DataResourceController.java')
    expect(readJavaCode(controllerPath)).toContain('@GetMapping("dimension-binding-risks")')

    // 反向：不许为它新开控制器或挂到业务控制器上（那会把权限面从 dataResource:* 漂走）
    for (const file of collect(join(SERVER, 'module/system/datapermission/controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `盘点端点不该出现在 ${baseName(file)}`).not.toContain('dimension-binding-risks')
    }

    // 前端不许重算：判 kind/subjectType 就是重抄风险类别，写属性名字面量就是抄实体形状
    const SHAPE = /(?:kind|subjectType)\s*(?:===|!==|==|!=)\s*['"]|['"](?:tenantId|warehouseId|factoryId|departmentId|creatorId)['"]/
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'composables'), '.ts'), ...collect(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission'), '.vue')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (SHAPE.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders, 'composable/视图在自行判维度或抄实体属性名（判据只该在后端分析器里）').toEqual([])
    // 自检：这条扫描要认得违规写法，也不能把"把 subjectType 显示出来"当违规
    expect(SHAPE.test('if (item.subjectType === \'WAREHOUSE\') {')).toBe(true)
    expect(SHAPE.test('<span>{{ item.subjectType }} / {{ item.kind }}</span>')).toBe(false)

    // 渲染断言：删掉告警不会让任何后端测试变红，所以必须在模板这里钉住
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list, '资源列表页没渲染维度可绑定性结果').toMatch(/<ElAlert[^>]*v-if="bindingCount"/)
    expect(list).toContain('useDimensionBindingRiskReport')

    // 分析器里那几个"代码写死的属性名"必须与运行期拼条件的判据同源：
    // 谁改了判据而没改分析器，面板就开始按过时的列名报问题（这比不报更糟）。
    // §19.35 之前判据住在 DataPermissionSpecification 里，现在收进 SubjectDimensionRules，
    // 这条门禁跟着搬走 —— 它守的是"同值"这件事，不是"住在哪个文件"这个旧形状。
    const subjectRules = stripComments(read(join(SERVER, 'module/system/datapermission/rule/SubjectDimensionRules.java')))
    const analyzer = stripComments(read(join(SERVER, 'module/system/datapermission/service/DimensionBindingAnalyzer.java')))
    for (const name of ['warehouseId', 'factoryId', 'departmentId']) {
      expect(subjectRules, `判据里已不再使用 ${name}：分析器的建议需要跟着改`).toContain(name)
      expect(analyzer).toContain(name)
    }

    // 取数不许绕开唯一的"什么算启用策略"入口：自己 new 一份查询就是第二份口径
    const service = readJavaCode(join(SERVER, 'module/system/datapermission/service/DataResourceService.java'))
    expect(service, '维度盘点要复用 DataPolicyService 的取数入口，别自己查表').toContain('getEnabledPoliciesByResource')
    expect(service).toContain('EntityShapeIndex.entityClasses')
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
    expect(editor, '编辑器没把缺口提示渲染到表单上').toMatch(/<ElAlert[^>]*v-if="fieldGaps\.length"/)
    expect(editor).toContain('gap.reason')

    // 预填的两条硬约束：只在新增态出现（否则会把已有 QUERY 遮蔽行改成导出授权 = 静默解除遮蔽），
    // 且动作/类型必须走字典枚举而不是裸字符串 —— 枚举成员由本文件另一条用例与后端 Java 枚举对账。
    expect(editor, '预填按钮必须被新增态判定包着，编辑已有策略时点它会删掉遮蔽')
      .toMatch(/<ElButton[^>]*v-if="isCreating"[^>]*@click="prefill\(gap\)"/)
    expect(editor).toContain('DataPermissionActionType.EXPORT.key')
    expect(editor).toContain('DataPermissionMaskType.FULL.key')
    expect(editor, '别在视图里写死动作名：后端枚举改名时这里不会报错，只会在保存时 500')
      .not
      .toMatch(/actionName\s*=\s*'(?:EXPORT|QUERY)'/)
    // "不许自己下结论"由上面那条扫 src/view 全量的规则统一守（本文件就在 src/view 下），
    // 这里不再重复一份正则 —— 两处规则会各自烂掉。
  })

  it('视图 script 段不许直接读 formData.x（框架返回的是 Ref）', () => {
    /**
     * 本轮 CI 的 typecheck 抓到的就是这个：`:model="formData"` 在模板里自动解包，
     * 于是"formData 是个普通对象"读起来很自然，script 里写 `formData.resourceId` 就直接编译失败。
     * 沙箱跑不了 vue-tsc（只在 CI 有），所以这类"模板/脚本解包规则不对称"的错误必须本地就有等价物。
     *
     * 判据只认 `<script>` 段里的 `formData.<不是 value>`：模板里的 `formData.x` 是合法写法，
     * 扫全文会大面积误伤 —— 那会让这条规则第一天就被加白名单。
     */
    const offenders: string[] = []
    for (const file of collect(join(ROOT, 'src'), '.vue')) {
      const block = /<script[^>]*>([\s\S]*?)<\/script>/.exec(stripComments(read(file)))?.[1]
      if (!block) {
        continue
      }
      block.split('\n').forEach((line, index) => {
        if (/\bformData\s*\.\s*(?!value\b)[A-Za-z_]/.test(line)) {
          offenders.push(`${relative(ROOT, file)}:script+${index + 1}`)
        }
      })
    }
    expect(offenders, `这些地方要么写 formData.value.x，要么放到模板里：\n${offenders.join('\n')}`).toEqual([])

    // 自检：认得违规写法、且不把 .value 与模板用法当违规（否则这条规则等于没写）
    expect(/\bformData\s*\.\s*(?!value\b)[A-Za-z_]/.test('const a = formData.resourceId')).toBe(true)
    expect(/\bformData\s*\.\s*(?!value\b)[A-Za-z_]/.test('const a = formData.value.resourceId')).toBe(false)
    // 规则的边界是"只看 script 段"，靠的是取块的正则；这步退化就会变成扫全文 ⇒ 模板写法大面积误伤
    expect(/<script[^>]*>([\s\S]*?)<\/script>/.exec('<template>{{ formData.fieldName }}</template>')?.[1]).toBeUndefined()
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
        .some(file => baseName(file) === `${cls}.java`)
      expect(hit, `tip 引用的 ${cls} 在后端找不到`).toBe(true)
    }

    // D13-B：维度落地后，主体字典也不许再把 FACTORY 灰掉（灰掉一个已落地维度 =
    // 管理员无法授权该维度，与 EXPORT 那条「灰掉已实现能力」同一套道理）。
    const subjectDict = read(join(ROOT, 'src/model/system/datapermission/DataPermissionSubjectType.ts'))
    const factoryTail = (() => {
      const lines = subjectDict.split(/\r?\n/)
      for (let i = 0; i < lines.length; i++) {
        if (/static readonly FACTORY\s*=/.test(lines[i])) {
          let tail = ''
          for (let j = i + 1; j < lines.length && /^\s*\.\w+\(/.test(lines[j]); j++) {
            tail += lines[j]
          }
          return tail
        }
      }
      return ''
    })()
    expect(factoryTail.length, '没解析到 FACTORY 字典项：字典形状变了，本用例已失效').toBeGreaterThan(0)
    if (factoryOff) {
      expect(factoryTail).toMatch(/setDisabled\(true\)/)
    }
    else {
      expect(factoryTail, '工厂维度已落地，字典里仍把 FACTORY 灰掉会让管理员无法授权').not.toMatch(/setDisabled\(true\)/)
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

  it('cOLUMN_MASKED 必须等于后端列级遮蔽实际消费的动作', () => {
    /**
     * `action_name` 是字符串存的，前后端没有编译期耦合：后端哪天让 DETAIL 也参与遮蔽，
     * 前端这边"这一行不会被读到"的提示就变成谎话 —— 而谎话比沉默更糟，管理员会拿它当依据。
     * 所以消费集不看注释、直接读后端那两个常量的字面值。
     */
    const masking = readJavaCode(join(SERVER, 'module/system/datapermission/service/ColumnMaskingService.java'))
    const guard = readJavaCode(join(SERVER, 'module/system/datapermission/service/ExportBypassGuard.java'))
    const readAction = /DEFAULT_ACTION = "([A-Z0-9_]+)"/.exec(masking)?.[1]
    const exportAction = /ACTION_EXPORT = "([A-Z0-9_]+)"/.exec(guard)?.[1]
    expect(readAction, '没解析到 ColumnMaskingService.DEFAULT_ACTION：后端改了写法，本用例已失效').toBeTruthy()
    expect(exportAction, '没解析到 ExportBypassGuard.ACTION_EXPORT：后端改了写法，本用例已失效').toBeTruthy()
    const consumed = [readAction!, exportAction!].sort()

    const block = /static readonly COLUMN_MASKED[^=]*= \[([\s\S]*?)\]/.exec(read(DICT))?.[1] ?? ''
    const declared = [...block.matchAll(/DataPermissionActionType\.([A-Z][A-Z0-9_]*)\.key/g)].map(m => m[1]).sort()
    expect(declared.length, 'COLUMN_MASKED 解析不到成员：字典形状变了，别把空转读成通过').toBeGreaterThan(0)
    expect(declared, `前端声明的消费集与后端不一致：后端是 ${consumed.join('/')}`).toEqual(consumed)
  })

  it('列级遮蔽不许悄悄接上新动作（接了就要同步编辑器提示）', () => {
    /**
     * 上一条对的是"常量值"，这一条对的是"调用点"：只看常量会漏掉直接传字面量的写法
     * （`getEffectiveMaskPolicy(rk, f, "DETAIL", ctx)`），那正是最容易在重构里顺手写出来的形式。
     */
    const allowed = new Set(['QUERY', 'EXPORT'])
    const CALL = /\bget(?:Effective)?MaskPolicy(?:Map)?\(/
    const offenders: string[] = []
    let sites = 0
    for (const file of collect(join(SERVER, 'module/system/datapermission'), '.java')) {
      // 按行扫，不用"配对到分号"的跨行正则：那种写法会把方法声明和它体内的调用吃成同一个 match
      // （声明的 `(` 里没有分号，贪婪量词一路吞到内部调用之后），于是违规被当成声明跳过 —— 反证时就没红。
      // 调用点在这一带都是一行一句，按行读反而与真实形状一致，也不会漏掉同类内部调用（它没有点号）。
      readJavaCode(file).split('\n').forEach((line, index) => {
        if (!CALL.test(line)) {
          return
        }
        const literal = /"([A-Z0-9_]+)"/.exec(line)?.[1]
        const constant = /\b(?:DEFAULT_ACTION|ACTION_EXPORT)\b/.test(line)
        if (line.match(/\b(?:public|private|protected)\b/)) {
          return // 声明行本身没有语义，动作由参数传进来
        }
        if (!literal && !constant) {
          return // 两参重载 = 走默认动作，不是一次"显式选动作"
        }
        sites++
        if (literal && !allowed.has(literal)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1} → "${literal}"`)
        }
      })
    }
    expect(sites, '一个遮蔽调用点都没抓到：解析已失效，这条用例等于没写').toBeGreaterThan(0)
    expect(offenders, '列级遮蔽出现了 QUERY/EXPORT 之外的动作 —— 字典的 COLUMN_MASKED 与编辑器提示都要跟着改').toEqual([])

    // 自检：必须认得"同类内部调用"（上一版只认带点号的写法而漏检过一次）
    const inside = '        return getEffectiveMaskPolicy(resourceKey, fieldName, "DETAIL", context);'
    expect(CALL.test(inside) && /"([A-Z0-9_]+)"/.exec(inside)?.[1]).toBe('DETAIL')
    expect(/\b(?:public|private|protected)\b/.test('    public Map<String, X> getMaskPolicy(String k) {')).toBe(true)
  })

  it('supportedActions 只被开发期初始化读取，不参与权限判定', () => {
    /**
     * 这条最初写的是"后端不许有任何读取点"—— 被 DevDataInitRunner:733 直接证伪（它用
     * `getSupportedActions().contains("CREATE")` 决定要不要给该资源生成示例策略）。
     * 于是判据收窄成既能守文案又不误伤的形式：**读取点只许出现在开发初始化里**。
     * 谁哪天在判定路径上读它（拦截器、guard、specification），资源编辑器的说明即刻过期 ⇒ 用例先红。
     */
    const ALLOWED_READER = /(?:^|[\\/])DevDataInitRunner\.java$/
    const entity = join(SERVER, 'module/system/datapermission/entity/DataResourceEntity.java')
    expect(readJavaCode(entity), 'DataResourceEntity 里没有 supportedActions：字段已改名或移除，本用例要重写')
      .toContain('supportedActions')

    const readers = collect(SERVER, '.java')
      .map(file => ({ file: relative(ROOT, file), code: readJavaCode(file) }))
      .filter(item => /\.getSupportedActions\(\)/.test(item.code))
    expect(readers.length, '一个读取点都没扫到：字段没人读了，这条用例与那句文案都要重写').toBeGreaterThan(0)

    const illegal = readers.filter(item => !ALLOWED_READER.test(item.file)).map(item => item.file)
    expect(illegal, `supportedActions 被判定路径读取了（${illegal.join(', ')}）：资源编辑器那句「不参与权限判定」必须改`).toEqual([])

    const editor = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'component', 'editor.vue'))
    expect(editor, '资源编辑器要如实说明这一列不影响判定').toContain('不参与权限判定')
  })

  it('归属列登记语法：前端只展示，不许自己解析', () => {
    /**
     * 登记串的语法住在后端 OwnerColumnRegistration 里：维度前缀、点号关联路径、逗号分隔。
     * 前端一旦也解析一遍（拆逗号、看前缀、比列名），下次改语法就会只改对一半 —— 而"配了但没生效"
     * 恰好是这个模块最难查的那类问题，所以这里盯的是"只许有一份解析"，不是"提示写得好看"。
     */
    const VIEW = join(ROOT, 'src', 'view', 'console', 'system', 'datapermission')
    const MODEL = join(ROOT, 'src', 'model', 'system', 'datapermission')
    // 窗口式匹配而不是「名字后面紧跟点号」：String(form.ownerColumns).split(",") 与 ownerColumns?.split(",")
    // 都是同一件事，前一种写法会绕过严格相邻的正则 —— 门禁漏一种写法，就等于允许那种写法存在
    const PARSE = /(?:ownerColumns|tenantColumn|filterableColumns)\b[^;\n]{0,40}\.(?:split|includes|startsWith|slice|indexOf|replace|match)\(/
    const files = [...collect(VIEW, '.vue'), ...collect(VIEW, '.ts'), ...collect(MODEL, '.ts')]
    const offenders = files
      .filter(file => [...stripComments(read(file)).split('\n')]
        .some(line => PARSE.test(line) && !line.trim().startsWith('*')))
      .map(file => relative(ROOT, file))
    expect(offenders, `登记串在前端被自己解析了（${offenders.join(', ')}）：删掉它，判定只认后端`).toEqual([])
    // 反证：这条不是在空转（本仓的门禁一律要先被证明能红）
    expect(PARSE.test('if (form.ownerColumns.split(",").length > 1) {')).toBe(true)
    expect(PARSE.test('const parts = String(form.ownerColumns).split(",")')).toBe(true)
    expect(PARSE.test('form.ownerColumns?.includes("DEPARTMENT:")')).toBe(true)
    expect(PARSE.test('label: \'归属列（可按维度登记）\',')).toBe(false)

    // 编辑器与列表页告警都要带上语法：盘点把人指到编辑页，编辑页却不写怎么写，就等于没指
    const tip = read(join(VIEW, 'resource', 'component', 'editor.vue'))
    const riskList = read(join(VIEW, 'resource', 'list.vue'))
    for (const shape of ['DEPARTMENT:', 'WAREHOUSE:storage.id']) {
      expect(tip, `归属列提示里少了 ${shape}：后端支持的登记法，UI 不写就等于没有`).toContain(shape)
      expect(riskList, `盘点告警的修法里少了 ${shape}`).toContain(shape)
    }
    // 「最多一跳」只在编辑器里写：告警那行已经够长了，深度上限是"怎么写"的细节，不是"往哪改"的指引
    expect(tip).toContain('最多一跳')

    /**
     * 回退顺序目前在判据侧与分析器各写了一遍（§19.26 已记为已知风险），这里钉住那一句本体：
     * "有 DIMENSION: 前缀项就用它们，一个都没有才沿用既有取值"。谁把某一边的回退翻过来
     * （例如改成"没前缀就当没登记"），面板与运行期立刻分家 —— 那正是"升级即查空"的形状。
     * §19.35 之后判据侧这一份住在 SubjectDimensionRules（以前在 DataPermissionSpecification 里
     * 直接拼 Criteria），门禁跟着搬家，两处对账的语义不变。
     */
    const FALLBACK = 'scoped.isEmpty() ? fallback : scoped'
    for (const cls of ['rule/SubjectDimensionRules', 'service/DimensionBindingAnalyzer']) {
      const code = readJavaCode(join(SERVER, `module/system/datapermission/${cls}.java`))
      expect(code, `${cls} 不再走 OwnerColumnRegistration.candidatesFor：回退顺序又变成两份`)
        .toContain('OwnerColumnRegistration.candidatesFor')
      expect(code, `${cls} 自己写了一份回退式（这一句现在只许住在 OwnerColumnRegistration 里）`)
        .not
        .toContain(FALLBACK)
      expect(code, `${cls} 又自己拆 owner_columns 了`).not.toMatch(/getOwnerColumns\(\)\s*\.split\(/)
    }
    const registration = readJavaCode(join(SERVER, 'module/system/datapermission/rule/OwnerColumnRegistration.java'))
    expect(registration, `回退式没了（${FALLBACK}）：读侧与盘点会各自回退成两套语义`).toContain(FALLBACK)
    /**
     * 写侧行级保护：判定与接线各钉一处
     *
     * 这类"挂在框架钩子上的守卫"最容易变成摆设 —— 判定逻辑写得再对，没接进 BaseService 的落库路径
     * 就等于没做（本仓已有先例：脱敏写回一开始也只接了 beforeUpdate，publish 走的是另一条路）。
     * 所以这里断言的是"接在哪、用谁的谓词"，而不是"有这个类"。
     */
    /**
     * 取某个方法的方法体（按大括号配对），用来判"接线到底在不在这个方法里"。
     * 只看"文件里出现过方法名"是不够的：守卫被从钩子里挪走（比如只剩一个私有方法）照样绿，
     * 而那时它已经不再拦截任何东西了。
     */
    const bodyOf = methodBody

    const base = readJavaCode(join(SERVER, 'base/BaseService.java'))
    for (const hook of ['E beforeSaveToDatabase(', 'void beforeDelete(']) {
      expect(bodyOf(base, hook), `BaseService.${hook} 没接写侧行级守卫：那条路径上"这行归谁"没人判`)
        .toContain('enforceRowPermission(')
    }
    const probe = readJavaCode(join(SERVER, 'module/system/datapermission/service/JpaRowVisibilityProbe.java'))
    expect(probe, '可见性探针不复用读侧条件生成：读写判定会分家').toContain('buildPredicates')
    expect(probe, '探针没用 COMMIT flush：改完再判等于给绕过留门').toContain('FlushModeType.COMMIT')
    expect(probe, 'JPA 的 FlushModeType 没有 MANUAL：别退回 Hibernate 专有枚举，那会多绑一层实现依赖')
      .not
      .toContain('FlushModeType.MANUAL')
    expect(probe).not.toMatch(/getOwnerColumns\(\)/)
    const props = readJavaCode(join(SERVER, 'module/system/datapermission/config/DataPermissionProperties.java'))
    expect(props, '写侧行级保护必须默认关（开了会直接打断业务，见 §19.27）')
      .toMatch(/class RowWrite[\s\S]{0,400}private boolean enabled = false/)
    const appYml = read(join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'))
    const rowWriteSwitch = /row-write:[ \t]*\n[ \t]+enabled:[ \t]+(\w+)/.exec(appYml)
    expect(rowWriteSwitch, 'application.yml 里没有 row-write.enabled：没人看见的开关等于没有开关').not.toBeNull()
    expect(rowWriteSwitch![1], '写侧行级保护默认必须关（打开的代价是"保存被拒"，见 §19.27）').toBe('false')

    // 资源登记的保存期拦截：语法判据只有一份，这里只钉"接上了"
    const resourceService = readJavaCode(join(SERVER, 'module/system/datapermission/service/DataResourceService.java'))
    for (const hook of ['DataResourceEntity beforeAdd(@NotNull', 'DataResourceEntity beforeUpdate(@NotNull']) {
      expect(bodyOf(resourceService, hook), `DataResourceService.${hook} 没接登记校验：配错只能等运行期猜`)
        .toContain('validateRegistrations(entity)')
    }
    expect(resourceService, '保存期校验要复用后端那份语法判据').toContain('OwnerColumnRegistration.validate(')
    // 写侧校验不许退回自己那份 addCsv（两份白名单迟早一边多一边少）
    const writeSide = readJavaCode(join(SERVER, 'module/system/datapermission/service/DataPolicyService.java'))
    expect(writeSide, '写侧白名单要复用 FieldAllowList.ofRegistrations').toContain('ofRegistrations')
    expect(writeSide, '写侧又自己 addCsv 了').not.toMatch(/private void addCsv\(/)

    // 提示与实现同改：后端把上限从一跳调开时，这条先红，逼着两边一起动
    const reg = readJavaCode(join(SERVER, 'module/system/datapermission/rule/OwnerColumnRegistration.java'))
    expect(reg, 'OwnerColumnRegistration 的 MAX_SEGMENTS 不再是 2：editor.vue 的「最多一跳」要跟着改').toMatch(/MAX_SEGMENTS\s*=\s*2/)
  })

  it('归属列防篡改：新建覆盖 + 更新恢复，各钉一处（D1 写侧兑现）', () => {
    /**
     * §19.27 缺口③：普通用户可 PUT 改 creatorId 把行送出自己视野。
     * D1 已定归属只信服务端，所以补的不是「谁有权改归属」的判定，而是两处硬约束：
     * 新建无条件覆盖（@PrePersist），更新恢复库中值（BaseService#protectAttribution）。
     * 「客户端没填才补」是错误形状：那会把 tenant_id 的最终值交给请求方。
     */
    const bodyOf = methodBody
    const tae = readJavaCode(join(SERVER, 'base/TenantAwareEntity.java'))
    const fill = bodyOf(tae, 'protected void fillAttribution()')
    expect(fill, 'fillAttribution 不再无条件覆盖：新建归属又退回「客户端没填才补」的可伪造形状')
      .toContain('tenantId = context.tenantId();')
    expect(fill, 'fillAttribution 仍保留 tenantId == null 守卫：客户端提交非空 tenant_id 就能伪造归属')
      .not
      .toMatch(/tenantId\s*==\s*null/)
    expect(fill, 'fillAttribution 仍保留 creatorId == null 守卫：客户端提交非空 creator_id 就能伪造归属')
      .not
      .toMatch(/creatorId\s*==\s*null/)

    const base = readJavaCode(join(SERVER, 'base/BaseService.java'))
    expect(bodyOf(base, 'E beforeUpdate(@NotNull'), 'BaseService.beforeUpdate 没接归属恢复：更新时归属列仍可被 PUT 篡改')
      .toContain('protectAttribution(')
    const protect = bodyOf(base, 'private void protectAttribution(')
    expect(protect, '归属恢复没调 AttributionWriteProtection.restoreOnUpdate：判定没落在一处可单测的函数上')
      .toContain('AttributionWriteProtection.restoreOnUpdate(')
    expect(protect, '归属恢复没回表读既存值：只覆盖不恢复，等于把库中值也覆盖掉').toContain('repository.findById(id)')
  })

  it('字典 tip 引用的后端类必须真实存在', () => {
    // 与 fieldPolicy 编辑器 tip 同一条规矩：写一个不存在的类名，比不写更误导
    const doc = read(DICT)
    for (const cls of doc.match(/\b(?:Export|Masked|Dimension|Column)[A-Za-z0-9]*(?:Guard|Service|Masker)\b/g) ?? []) {
      const hit = collect(join(SERVER, 'module/system/datapermission'), '.java')
        .some(file => baseName(file) === `${cls}.java`)
      expect(hit, `字典注释引用的 ${cls} 在后端找不到`).toBe(true)
    }
  })
})

/**
 * # 部门层级闭包 / 策略主体判定（B2① 的卡点）
 *
 * 这一组钉的都是"同一件事被抄第二遍"的形态，而不是某个方法的签名：
 *
 * 1. **subjectType 的 switch 只许有一份**。曾经它有三份副本（管理端服务、带缓存的运行时提供者、
 *    测试替身），三份对 null 的写法都不一样 —— 后果是"预览说策略可达、运行时判它不可达"，
 *    而且加一个维度必然漏一处。测试替身也必须走同一份：替身自己发明规则，
 *    所有走 fixture 的用例就都在测一个生产里不存在的判定。
 * 2. **"某部门在不在我的范围内"只许问 context**。判据散在调用点，就会出现
 *    "可达性按 A 判、谓词按 B 拼"的自相矛盾，而这种不一致在单元测试里看不出来。
 * 3. **展开是放宽，开关默认必须关且必须写进 application.yml**（没人看见的开关等于没有开关）。
 * 4. **树遍历只许在 HierarchyClosure 里**。{@code DepartmentService} 那份旧递归一边查库一边递归（N+1），
 *    而且递归体丢掉返回值 ⇒ 恒返回空集；`parentId` 是业务可编辑列，自己写递归迟早 StackOverflow。
 */
describe('mask contract · 部门层级闭包与主体判定只有一份', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const TEST_SRC = join(ROOT, 'SPMS-Server', 'src', 'test', 'java', 'cn', 'hamm', 'spms')

  it('策略主体可达性判定只存在一处，测试替身也走同一份', () => {
    const matcher = readJavaCode(join(DPM, 'service', 'DataPermissionSubjectMatcher.java'))
    expect(matcher, 'matcher 里 DEPARTMENT 没走 coversDepartment：范围判据会重新分叉')
      .toContain('case "DEPARTMENT" -> context.coversDepartment(')
    expect(matcher, '未知的 subjectType 必须 fail-closed，不能默认放行')
      .toContain('default -> false;')

    const copies = [
      [join(DPM, 'service', 'DataPermissionService.java'), '管理端'],
      [join(DPM, 'service', 'JpaPermissionRuleProvider.java'), '运行时（带缓存）'],
      [join(TEST_SRC, 'module/system/datapermission/service/TestPermissionRuleProvider.java'), '测试替身'],
    ] as const
    for (const [file, label] of copies) {
      const code = readJavaCode(file)
      expect(code, `${label}又自己写了一遍 subjectType 判定：三份副本必然漂移`).not.toMatch(/case "DEPARTMENT"/)
      expect(code, `${label}没接统一的 matcher：改了 matcher 它不会跟着变`)
        .toContain('DataPermissionSubjectMatcher.matches(')
    }
  })

  it('部门范围只许问 PermissionContext#coversDepartment', () => {
    for (const file of ['DataPermissionService.java', 'DataPermissionSpecification.java']) {
      expect(readJavaCode(join(DPM, 'service', file)), `${file} 直接查直属部门集合：绕过了"含子部门"的范围，父部门策略会莫名不生效`)
        .not
        .toMatch(/context\.departmentIds\(\)\.contains\(/)
    }
    const ctx = readJavaCode(join(DPM, 'context', 'PermissionContext.java'))
    expect(ctx, 'coversDepartment 的判据被改写：范围（含子部门）才是权限口径')
      .toMatch(/public boolean coversDepartment\(Long departmentId\) \{\s*return departmentId != null && departmentScopeIds\.contains\(departmentId\);/)
    expect(ctx, '兼容构造没了：全仓三十多处 7 参构造（含测试）会一起编译失败')
      .toMatch(/public PermissionContext\(\s*Long userId,\s*Long tenantId,\s*Set<Long> departmentIds,\s*Set<Long> factoryIds,/)
    expect(ctx, '范围为空时必须回落成直属（漏传只能少看数据，不能退化成放开）')
      .toMatch(/departmentScopeIds == null \|\| departmentScopeIds\.isEmpty\(\)/)
  })

  it('展开开关默认关、写进 application.yml，且只在开关打开时才碰索引', () => {
    const props = readJavaCode(join(DPM, 'config', 'DataPermissionProperties.java'))
    expect(props, 'hierarchy.department-enabled 默认必须是 false：打开意味着父部门策略盖住整棵子树')
      .toMatch(/class Hierarchy[\s\S]{0,900}?private boolean departmentEnabled = false/)

    const yml = read(join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'))
    const sw = /hierarchy:[ \t]*\n[ \t]+department-enabled:[ \t]+(\w+)/.exec(yml)
    expect(sw, 'application.yml 里没有 hierarchy.department-enabled：没人看见的开关等于没有开关').not.toBeNull()
    expect(sw![1], '层级展开默认必须关（见 §19.28）').toBe('false')

    const interceptor = readJavaCode(join(SERVER, 'common', 'interceptor', 'RequestInterceptor.java'))
    expect(interceptor, '没判开关就展开：默认关闭时代价必须为零').toMatch(/isDepartmentEnabled\(\)/)
    expect(interceptor, '展开结果没进 context 的部门范围：等于白算')
      .toMatch(/departmentIds,[ \t]*\n[ \t]*departmentScopeIds,/)
    expect(interceptor, '截断没打 WARN：脏数据导致的"少看数据"会变成查不出的悬案')
      .toMatch(/if \(closure\.truncated\(\)\) \{/)
  })

  it('树遍历只允许 HierarchyClosure 一处，部门改动必须打掉快照', () => {
    const deptService = readJavaCode(join(SERVER, 'module/personnel/department/DepartmentService.java'))
    expect(deptService, 'DepartmentService 又自己写了一遍递归（旧实现连结果集都没收）')
      .not
      .toMatch(/private Set<Long> getListByParentId\(/)
    expect(deptService, 'getListByParentId 没走索引：N+1 与环保护会一起回来')
      .toContain('hierarchyIndex.descendantsOf(')
    for (const hook of ['protected void beforeDelete(', 'protected void afterAppAdd(', 'protected void afterUpdate(']) {
      expect(methodBody(deptService, hook), `部门树的 ${hook} 没打掉快照：改完树还按旧范围放行，那是越权方向`)
        .toContain('afterTreeChanged()')
    }

    const index = readJavaCode(join(SERVER, 'module/personnel/department/DepartmentHierarchyIndex.java'))
    expect(index, '索引绕开 HierarchyClosure 自己遍历：环保护与深度上限会丢')
      .toContain('HierarchyClosure.expand(')
    expect(index, '失效改成"把时间戳推成很久以前"会在 nanoTime 减法上溢出成永不过期')
      .toMatch(/public void invalidate\(\) \{\s*snapshot = Snapshot\.unusable\(\);/)
    expect(index, '重建没做 double-check：TTL 到点时每个请求都会去读全表')
      .toMatch(/if \(latest != previous\) \{/)

    const closure = readJavaCode(join(SERVER, 'common/tree', 'HierarchyClosure.java'))
    expect(closure, 'visited 没了：parentId 可编辑，成环时递归会 StackOverflow 把整条链路打挂')
      .toContain('visited')
    expect(closure, '闭包结果被做成可变集合：它是多请求共享的快照')
      .toMatch(/Collections\.unmodifiableSet\(new LinkedHashSet<>\(ids\)\)/)

    const listener = readJavaCode(join(DPM, 'event', 'DataPermissionEventListener.java'))
    expect(listener, '跨节点广播没接上：Redis 在跑但快照不失效，等于只在本节点生效')
      .toContain('case DEPARTMENT_TREE_CHANGED ->')
  })

  it('工厂层级展开（D13）默认关、写进 yml，且 storage 树快照与失效接线齐全', () => {
    const props = readJavaCode(join(DPM, 'config', 'DataPermissionProperties.java'))
    expect(props, 'hierarchy.factory-enabled 默认必须是 false：打开意味着工厂策略盖住整棵子树')
      .toMatch(/class Hierarchy[\s\S]*?private boolean factoryEnabled = false/)

    const yml = read(join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'))
    const sw = /factory-enabled:[ \t]+(\w+)/.exec(yml)
    expect(sw, 'application.yml 里没有 hierarchy.factory-enabled：没人看见的开关等于没有开关').not.toBeNull()
    expect(sw![1], '工厂层级展开默认必须关（见 §19.28）').toBe('false')

    const interceptor = readJavaCode(join(SERVER, 'common', 'interceptor', 'RequestInterceptor.java'))
    expect(interceptor, '没判维度开关就解析 factoryIds：维度默认关闭时代价必须为零')
      .toMatch(/isFactoryDimensionEnabled\(\)/)
    expect(interceptor, '没判层级开关就展开：工厂闭包默认必须关').toMatch(/isFactoryEnabled\(\)/)

    const index = readJavaCode(join(SERVER, 'module', 'factory', 'storage', 'StorageHierarchyIndex.java'))
    expect(index, '工厂树索引绕开 HierarchyClosure 自己遍历：环保护与深度上限会丢')
      .toContain('HierarchyClosure.expand(')
    expect(index, '失效改成推时间戳会在 nanoTime 减法上溢出成永不过期')
      .toMatch(/public void invalidate\(\) \{\s*snapshot = Snapshot\.unusable\(\);/)

    const storageService = readJavaCode(join(SERVER, 'module', 'factory', 'storage', 'StorageService.java'))
    for (const hook of ['protected void beforeDelete(', 'protected void afterAppAdd(', 'protected void afterUpdate(']) {
      expect(methodBody(storageService, hook), `仓库/工厂树的 ${hook} 没打掉快照：改完树还按旧范围放行，那是越权方向`)
        .toContain('afterTreeChanged()')
    }

    const listener = readJavaCode(join(DPM, 'event', 'DataPermissionEventListener.java'))
    expect(listener, '仓库/工厂树的跨节点广播没接上：Redis 在跑但快照不失效')
      .toContain('case STORAGE_TREE_CHANGED ->')
    const publisher = readJavaCode(join(DPM, 'event', 'DataPermissionEventPublisher.java'))
    expect(publisher, '发布器缺 publishStorageTreeChanged 入口：StorageService 广播会编译失败')
      .toContain('publishStorageTreeChanged')
  })
})

/**
 * # 灰度状态面板 + 写侧 OFF 档（B2② 的卡点）
 *
 * 1. **面板报的档位必须与运行期走的是同一份判定**。`mode` 与 `enforce-resources` 的三态规则
 *    （enforce 时名单是"例外清单"、shadow 时是"提前放开清单"）只有一份；前端或面板服务里再算一遍，
 *    就会出现"面板说 enforce、实际是 shadow"，而管理员是照着面板决定放不放开下一个资源的。
 * 2. **取数失败不能显示成"一切正常"**：composable 的 catch 只置 `failed`（一个自查面板不该打断列表页），
 *    代价是路径写错在运行期毫无痕迹 —— 每加一个盘点端点就必须加一条路径对账。
 * 3. **record 字段名前后端一致**：状态面板是纯展示契约，后端改字段名而前端不动，TS 不会报
 *    （`any` 一路传下去），表现是那一格永远空着 —— 所以字段清单要机器对账。
 * 4. **写侧动作级守卫必须看 mode**：`mode: off` 是本模块唯一的回滚手段，而它原先只被读侧认；
 *    写侧不看 mode 时，"把配置改回去"救不了保存路径（这条以前就是无条件抛异常）。
 */
describe('mask contract · 灰度状态面板与写侧 OFF 档', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const TYPES = join(ROOT, 'src', 'api', 'system', 'types.ts')
  const API = join(ROOT, 'src', 'api', 'system.ts')

  it('runtime-status 端点前后端路径对得上，且只挂在管理端点上', () => {
    expect(read(API)).toContain('request.get(\'/dataResource/runtime-status\')')
    const controller = join(DPM, 'controller', 'DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("runtime-status")')
    expect(readJavaCode(controller)).toContain('@Api("dataResource")')
    for (const file of collect(join(DPM, 'controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `状态端点不该出现在 ${baseName(file)}（会把权限面从 dataResource 漂走）`)
        .not
        .toContain('runtime-status')
    }
  })

  it('前端只显示档位，不自己算名单', () => {
    // 命中写法：拿名单或字符串比较去反推档位；显示 `{{ item.effectiveMode }}` 不算
    const SELF_CALC = /(?:effectiveMode|globalMode)\s*(?:===|!==|==|!=)\s*['"]|enforceResources\s*\.\s*(?:includes|indexOf|some|find)\s*\(/
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'composables'), '.ts'), ...collect(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission'), '.vue')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (SELF_CALC.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders, 'composable/视图在自行反推生效档位：名单规则只该有一份（后端 effectiveMode）').toEqual([])
    // 自检：认得违规，也不误伤"把档位原样显示出来"
    expect(SELF_CALC.test('if (item.effectiveMode === \'ENFORCE\') {')).toBe(true)
    expect(SELF_CALC.test('status.enforceResources.includes(key)')).toBe(true)
    expect(SELF_CALC.test('<span>{{ row.effectiveMode }}</span>')).toBe(false)

    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list, '资源列表页没渲染灰度状态').toMatch(/<ElAlert[^>]*v-if="runtimeReady"/)
    expect(list, '取数失败必须能被看见（不能安静地显示成一切正常）').toContain('runtimeFailed')
    expect(list).toContain('useDataPermissionRuntimeStatus')
  })

  it('状态面板的档位取自 properties，不重算三态规则', () => {
    const service = readJavaCode(join(DPM, 'service', 'DataResourceService.java'))
    expect(methodBody(service, 'ResourceRuntimeStatus statusOf('), '面板自己算档位：名单的"例外清单 / 提前放开清单"两套语义必然会被抄错')
      .toContain('properties.effectiveMode(')
    // 未纳管服务清单要来自真实的资源键解析器，而不是前端传进来的一份名字表
    expect(service, '未纳管清单绕开了 DataPermissionResourceResolver').toContain('DataPermissionResourceResolver.resolve(')
  })

  it('record 字段名与前端 interface 一一对得上', () => {
    const java = stripComments(read(join(DPM, 'service', 'DataResourceService.java')))
    const ts = stripComments(read(TYPES))
    for (const name of ['ResourceRuntimeStatus', 'RuntimeStatus']) {
      // 后端：public record X(...){ 里的组件名
      const at = java.indexOf(`public record ${name}(`)
      expect(at, `后端找不到 record ${name}：状态面板的契约变了`).toBeGreaterThan(-1)
      // 含收尾括号：最后一个组件后面就是它，不含会漏判一个字段（record 少一个分量、前端多一个，正是要抓的形状）
      const params = java.slice(at, java.indexOf(')', at) + 1)
      const javaFields = [...params.matchAll(/([A-Z]\w*)\s*[,)]/gi)].map(m => m[1]).filter(f => f !== name)
      // 前端：TS 侧名字可能不同（RuntimeStatus ↔ DataPermissionRuntimeStatus），按候选名找
      const tsNames = name === 'RuntimeStatus' ? ['DataPermissionRuntimeStatus', 'RuntimeStatus'] : [name]
      let tsFields: string[] = []
      for (const tsName of tsNames) {
        const start = ts.indexOf(`export interface ${tsName} {`)
        if (start < 0) {
          continue
        }
        const block = ts.slice(start, ts.indexOf('\n}', start))
        tsFields = [...block.matchAll(/^\s{2}(\w+)[:?]/gm)].map(m => m[1])
        break
      }
      expect(tsFields.length, `前端找不到 interface ${tsNameHint(tsNames)}`).toBeGreaterThan(0)
      expect(new Set(tsFields), `${name}：前端有后端没有的字段（会一直是 undefined）`)
        .toEqual(new Set(javaFields.filter(f => tsFields.includes(f))))
      for (const field of javaFields) {
        expect(tsFields, `${name}.${field} 在前端 interface 里缺失：那一格会永远空着`).toContain(field)
      }
    }
  })

  it('灰度页逐资源回显 ORM_PARITY 四档分布，不把缺证据显示成零', () => {
    const service = readJavaCode(join(DPM, 'service', 'DataResourceService.java'))
    const status = methodBody(service, 'ResourceRuntimeStatus statusOf(')
    for (const field of ['ormParityRequired', 'ormParityTableAvailable', 'ormParityConsistentCount', 'ormParityStricterCount', 'ormParityLooserCount', 'ormParityErrorCount']) {
      const suffix = field.slice('ormParity'.length).replace(/Count$/, '')
      const accessor = suffix.charAt(0).toLowerCase() + suffix.slice(1)
      expect(status, `状态面板缺少 ${field}：灰度页会把 ORM 对账结论丢掉`).toContain(`parity.${accessor}()`)
    }
    const composable = read(join(ROOT, 'src', 'composables', 'useDataPermissionRuntimeStatus.ts'))
    expect(composable).toContain('resources: ResourceRuntimeStatus[]')
    expect(composable).toContain('state.resources = state.status?.resources ?? []')
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list, '资源列表页没有逐资源渲染 ORM 对账分布').toContain('v-for="item in runtimeResources"')
    for (const field of ['ormParityRequired', 'ormParityTableAvailable', 'ormParityConsistentCount', 'ormParityStricterCount', 'ormParityLooserCount', 'ormParityErrorCount']) {
      expect(list, `资源列表页没有显示 ${field}`).toContain(`item.${field}`)
    }
  })

  it('证据留存有正数天数、独立定时清理与 create_time 索引', () => {
    const properties = readJavaCode(join(DPM, 'config', 'DataPermissionProperties.java'))
    const retention = readJavaCode(join(DPM, 'service', 'DataPermissionEvidenceRetention.java'))
    const diffRepository = readJavaCode(join(DPM, 'repository', 'DataPermissionDiffRepository.java'))
    const parityRepository = readJavaCode(join(DPM, 'repository', 'DataPermissionOrmParityRepository.java'))
    const yml = read(join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'))
    expect(properties).toContain('retentionCleanupEnabled = false')
    expect(properties).toContain('retentionDays = 30')
    expect(properties).toContain('retentionCleanupCron')
    expect(yml).toMatch(/retention-cleanup-enabled:\s*false/)
    expect(yml).toMatch(/retention-days:\s*30/)
    const property = '$' + '{kubo.data-permission.shadow.retention-cleanup-cron:0 15 3 * * *}'
    const schedule = `@Scheduled(cron = "${property}")`
    expect(retention).toContain(schedule)
    expect(retention).toContain('cutoffEpochMillis')
    expect(diffRepository).toContain('delete from data_permission_diff_log where create_time < :cutoff')
    expect(parityRepository).toContain('delete from data_permission_orm_parity_log where create_time < :cutoff')
    const migration = read(join(ROOT, 'SPMS-Server', 'docs', 'database', 'migration', '20260909_data_permission_migrations_consolidated.sql'))
    expect(migration).toContain('idx_dp_diff_create_time')
    expect(migration).toContain('idx_dp_parity_create_time')
    expect(migration).toContain('kubo_dp_add_evidence_time_index')
    expect(migration).toContain('information_schema.STATISTICS')
  })

  it('写侧动作级守卫必须认得 mode 与「资源是否纳管」', () => {
    const guard = readJavaCode(join(DPM, 'service', 'DataPermissionGuard.java'))
    const body = methodBody(guard, 'void enforce(String resourceKey, DataPermissionAction action)')
    expect(body, 'OFF 档写侧仍在拦：那"把 mode 改回 off"就不是回滚手段')
      .toContain('properties.computes(resourceKey)')
    expect(body, '未登记资源变成无条件抛异常：新模块没登记时所有保存/删除都 500，而页面上看不出原因')
      .toContain('findResource(resourceKey)')
    expect(body).toContain('isDenyUnregistered()')
    // 读侧用的是同一个 properties 入口（两边都短路才算同一个三态语义）
    const spec = readJavaCode(join(DPM, 'service', 'DataPermissionSpecification.java'))
    expect(spec, '读侧不再走 effectiveMode：写侧刚接上的判据又分叉了')
      .toContain('properties.effectiveMode(resourceKey)')
    // 未抛异常的资源查找只有一份定义，requireResource 必须复用它
    const service = readJavaCode(join(DPM, 'service', 'DataPermissionService.java'))
    expect(methodBody(service, 'DataResourceEntity requireResource(String resourceKey)'))
      .toContain('findResource(resourceKey)')
  })
})

function tsNameHint(names: string[]): string {
  return names.join(' / ')
}

describe('mask contract · 逐资源放开的前置检查', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const SERVICE = join(DPM, 'service', 'DataResourceService.java')
  const API = join(ROOT, 'src', 'api', 'system.ts')
  const TYPES = join(ROOT, 'src', 'api', 'system', 'types.ts')
  const READINESS_TEST = join(ROOT, 'SPMS-Server/src/test/java/cn/hamm/spms/module/system/datapermission/service/DataResourceReadinessTest.java')

  it('enforce-readiness 路径前后端对得上，且只挂在管理端点上', () => {
    expect(read(API)).toContain('request.get(\'/dataResource/enforce-readiness\'')
    const controller = join(DPM, 'controller', 'DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("enforce-readiness")')
    for (const file of collect(join(DPM, 'controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `放开前置检查出现在 ${baseName(file)}：诊断端点的权限面会从 dataResource 漂走`)
        .not
        .toContain('enforce-readiness')
    }
  })

  it('前端只回显结论，不反推 ready', () => {
    // 命中写法：用 checks/blockers 自己算「能不能放开」，或在判断里读 blocking（那正是后端的判据）
    const RECOMPUTE = /\bready\s*=[^\n]*\.(?:some|every|reduce|find)\s*\(|(?:checks|blockers)\.[^\n]{0,40}(?:some|every|reduce|find)\s*\(|\.blocking\b/
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'composables'), '.ts'), ...collect(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission'), '.vue')]) {
      stripComments(read(file)).split('\n').forEach((line, index) => {
        if (RECOMPUTE.test(line)) {
          offenders.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(offenders, '前端在自行反推「能不能放开」：影子证据那条判据（要有 EXPECTED_FILTER 正向样本）前端无从复现').toEqual([])
    // 自检：认得违规，也不误伤「把结论显示出来」
    expect(RECOMPUTE.test('const ready = status.checks.every(c => c.passed)')).toBe(true)
    expect(RECOMPUTE.test('row.blockers.find(x => x)')).toBe(true)
    expect(RECOMPUTE.test('v-if="c.blocking"')).toBe(true)
    expect(RECOMPUTE.test('const readyCount = items.filter(item => item.ready).length')).toBe(false)
    expect(RECOMPUTE.test('state.readyCount = summary.readyCount')).toBe(false)
    expect(RECOMPUTE.test('<ElTag v-if="row.ready">可放开</ElTag>')).toBe(false)

    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list).toContain('useEnforceReadiness')
    expect(list, '取数失败必须能被看见（这里显示「都可以放开」会直接诱导一次错误的放开）')
      .toMatch(/<ElAlert[^>]*v-if="readinessFailed"/)
  })

  it('判据与灰度面板同源，影子证据只认常量', () => {
    const service = readJavaCode(SERVICE)
    // 两处判据必须共用一份 runtimeNotes，否则面板说「先补策略」、检查说「可以放开」
    expect(methodBody(service, 'List<ReadinessCheck> readinessChecks('), '放开前置检查另抄了一份配置判据：面板与检查会说法不一')
      .toContain('runtimeNotes(')
    // 影子证据读的是 verdict 常量，不是字符串（表里改枚举时这里要一起红）。
    // ORM_PARITY 的四档计数现在由面板与 readiness 共用的快照集中读取，不能只盯 readinessOf 的装配体。
    const assembly = methodBody(service, 'Readiness readinessOf(DataResourceEntity resource, long bindingRiskCount')
    const parity = methodBody(service, 'OrmParityCounts ormParityCounts(String resourceKey)')
    for (const constant of ['VERDICT_ANOMALY', 'VERDICT_ERROR', 'VERDICT_EXPECTED_FILTER']) {
      expect(assembly, `影子证据不再认 ${constant}：判据与表里的枚举定义开始分叉`).toContain(constant)
    }
    for (const constant of ['VERDICT_CONSISTENT', 'VERDICT_MAPPER_STRICTER', 'VERDICT_MAPPER_LOOSER', 'VERDICT_ERROR']) {
      expect(parity, `ORM 对账分布不再认 ${constant}：面板与 readiness 会丢掉一档`).toContain(constant)
    }
    expect(service, '用字符串比较 verdict：枚举改名时会静默失效').not.toMatch(/equals\("(?:ANOMALY|ERROR|EXPECTED_FILTER)"\)/)
    expect(parity, '读不到 ORM 对账表时不得当成「没有差异」').toContain('getIfAvailable()')
  })

  it('行级策略计数只有一份数法（面板与放开检查共用）', () => {
    const code = readJavaCode(SERVICE)
    const loops = code.match(/DataPermissionAction\.QUERY\.name\(\)\.equals\(/g) ?? []
    expect(loops.length, '按动作数策略的循环被抄了第二遍：两处口径迟早分叉，面板与检查会对同一个资源给出相反说法')
      .toBe(1)
    expect(methodBody(code, 'ResourceRuntimeStatus statusOf(')).toContain('countRowPolicies(')
    expect(methodBody(code, 'Readiness readinessOf(DataResourceEntity resource, long bindingRiskCount'))
      .toContain('countRowPolicies(')
  })

  it('前置检查只报数：不进请求链路、不抛异常', () => {
    const service = readJavaCode(SERVICE)
    for (const signature of ['Readiness readiness(String resourceKey)', 'List<Readiness> readinessAll()']) {
      const body = methodBody(service, signature)
      expect(body).not.toContain('throw new')
    }
    const guard = readJavaCode(join(DPM, 'service', 'DataPermissionGuard.java'))
    expect(guard, '守卫开始依赖 readiness：判据一处抛错就会把所有保存变成 500').not.toContain('readiness(')
  })

  it('enforce 名单不是软提示：启动期必须复用 readiness 并拒绝红灯配置', () => {
    const gate = readJavaCode(join(DPM, 'service', 'DataPermissionEnforceReadiness.java'))
    expect(gate).toContain('@EventListener(ApplicationReadyEvent.class)')
    expect(methodBody(gate, 'public void verifyOnStartup()')).toContain('readinessAll()')
    expect(methodBody(gate, 'public void verifyOnStartup()')).toContain('throw new IllegalStateException')
    expect(gate).toContain('static List<String> validate(')
    expect(gate).toContain('enforce-resources')
  })

  it('契约字段前后端一一对得上（Readiness ↔ TS）', () => {
    const java = stripComments(read(SERVICE))
    const ts = stripComments(read(TYPES))
    const pairs: Array<[string, string]> = [['Readiness', 'EnforceReadiness'], ['ReadinessCheck', 'EnforceReadinessCheck']]
    for (const [javaName, tsName] of pairs) {
      const at = java.indexOf(`public record ${javaName}(`)
      expect(at, `后端找不到 record ${javaName}：放开前置检查的契约变了`).toBeGreaterThan(-1)
      const params = java.slice(at, java.indexOf(')', at) + 1)
      const javaFields = [...params.matchAll(/([A-Z]\w*)\s*[,)]/gi)].map(m => m[1]).filter(f => f !== javaName)
      const start = ts.indexOf(`export interface ${tsName} {`)
      expect(start, `前端缺 interface ${tsName}：那一格会永远空着`).toBeGreaterThan(-1)
      const block = ts.slice(start, ts.indexOf('\n}', start))
      const tsFields = [...block.matchAll(/^\s{2}(\w+)[:?]/gm)].map(m => m[1])
      expect(new Set(tsFields), `${tsName}：前端有后端没有的字段（会一直是 undefined）`)
        .toEqual(new Set(javaFields.filter(f => tsFields.includes(f))))
      for (const field of javaFields) {
        expect(tsFields, `${javaName}.${field} 在前端 interface 里缺失`).toContain(field)
      }
    }
  })

  it('最容易被「顺手改宽松」的两条判据有测试钉住', () => {
    const test = read(READINESS_TEST)
    expect(test, '缺了「0 差异 ≠ 安全」这条断言：把影子证据改成「差异为 0 就通过」将无人拦').toContain('EXPECTED_FILTER')
    expect(test).toContain('consistentIsNotEnough')
    expect(test, '缺了空判据默认不通过的断言').toContain('emptyChecksAreNotReady')
  })
})

describe('mask contract · 拒答语义与留痕口径', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const DENIAL = join(DPM, 'service', 'DataPermissionDenial.java')
  const TESTS = 'SPMS-Server/src/test/java/cn/hamm/spms'
  const GUARD_TESTS = [`${TESTS}/DataPermissionGuardCudTest.java`, `${TESTS}/module/system/datapermission/DataPermissionGuardTest.java`, `${TESTS}/module/system/datapermission/service/RowWriteGuardTest.java`, `${TESTS}/module/system/datapermission/DataPermissionIntegrationTest.java`].map(f => join(ROOT, f))

  it('请求路径上的权限拒绝只有一个出口', () => {
    for (const name of ['DataPermissionGuard.java', 'RowWriteGuard.java']) {
      const code = readJavaCode(join(DPM, 'service', name))
      expect(code, `${name} 里自己 throw：拒答语义（403/500）与留痕会在第二个副本里漂移`)
        .not
        .toContain('throw new IllegalStateException')
      expect(code, `${name} 绕开了统一出口`).toContain('DataPermissionDenial.denied(')
    }
    const exportGuard = readJavaCode(join(DPM, 'service', 'ExportBypassGuard.java'))
    expect(exportGuard, '导出直接调框架 show：真拒又不进 deny 计数了（这一处正是本轮补上的漏）')
      .not
      .toContain('FORBIDDEN.show(')
    expect(exportGuard).toContain('DataPermissionDenial.denied(')
  })

  it('出口的纪律：先留痕再抛，抛不动也要兜底', () => {
    const body = methodBody(readJavaCode(DENIAL), 'static void denied(')
    const denyAt = body.indexOf('audit.deny(')
    const showAt = body.indexOf('FORBIDDEN.show(')
    expect(denyAt, '不再写 deny 计数').toBeGreaterThan(-1)
    expect(showAt, '不再走框架的 forbidden 语义').toBeGreaterThan(-1)
    expect(denyAt < showAt, '顺序反了：先抛后记，异常路径上这笔账永远不会出现').toBe(true)
    expect(showAt < body.indexOf('throw new IllegalStateException'), '少了兜底 throw：框架哪天把 show 改成「只登记不抛」，这里就会悄悄变成放行')
      .toBe(true)
  })

  it('出口必须是静态工具，不能是可被 mock 的 Bean', () => {
    const code = read(DENIAL)
    expect(/@Component|@Service/.test(code), '做成 Bean 之后，测试里 mock 掉它 = deny 分支静默不抛（本仓被这类替身坑过两次）')
      .toBe(false)
    expect(code).toContain('private DataPermissionDenial()')
  })

  it('分类纪律：配置与接线错误不许伪装成「没权限」', () => {
    for (const name of ['ExportColumnMasker.java', 'SensitiveWriteProtection.java', 'FactoryDimensionReadiness.java', 'DataPermissionService.java', 'DataResourceService.java']) {
      const code = readJavaCode(join(DPM, 'service', name))
      expect(code, `${name} 开始用 forbidden 语义：那类"代码接错了"的问题会被查授权的人接手，永远查不出来`)
        .not
        .toContain('Errors.FORBIDDEN')
      expect(code, `${name} 把配置错误也送进拒答出口：见上一条`).not.toContain('DataPermissionDenial.denied(')
    }
  })

  it('守卫测试不许只断「抛了异常」（消息才是契约）', () => {
    for (const file of GUARD_TESTS) {
      const code = stripComments(read(file))
      if (!code.includes('assertThrows(RuntimeException.class')) {
        continue
      }
      expect(code, `${baseName(file)} 里有只断类型的 assertThrows：`
      + '「资源未注册」与「策略没配」两种原因在断言里会长得一模一样')
        .toContain('getMessage()')
    }
  })
})

describe('mask contract · 影子双跑的调用点', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const SPEC = join(DPM, 'service', 'DataPermissionSpecification.java')
  const DIFF = join(DPM, 'service', 'ShadowDiffService.java')

  it('sHADOW 分支必须把比对叫起来（否则差异表永远空，放开前置永远「无证据」）', () => {
    const code = readJavaCode(SPEC)
    const shadow = methodBody(code, 'private <T> void submitShadowDiff(')
    expect(shadow, '读侧不再交给比对：那 data_permission_diff_log 没有生产者，'
    + 'enforce-readiness 的 SHADOW_EVIDENCE 判据在任何环境都会永远红')
      .toContain('shadowDiffService.compare(')
    // 条件必须是"读侧算出来的那份"，不能是比对侧另拼一份
    expect(shadow, '比对用的是另拼的条件：那比的是两份实现，不是「加条件会不会少行」')
      .toContain('buildPredicatesFromPolicies(')
    // 只在影子档跑：enforce 下条件已注入，再双跑是多打一次全表 COUNT
    const apply = methodBody(code, 'private <T> List<Predicate> apply(')
    expect(apply.indexOf('submitShadowDiff('), '调用点不在 shadow 分支里').toBeGreaterThan(apply.indexOf('audit.shadow(resourceKey, "would-filter"'))
    expect(apply.indexOf('return predicates;'), '调用点跑到 enforce 分支去了').toBeLessThan(apply.length)
  })

  it('观测件坏了不能影响判定，也绝不读 ThreadLocal', () => {
    const code = readJavaCode(SPEC)
    const submit = methodBody(code, 'private <T> void submitShadowDiff(')
    expect(submit, '缺 Bean 时改成抛异常：一个观测件把查询打挂，这是灰度工具最典型的自伤')
      .toContain('if (shadowDiffService == null)')
    const diff = readJavaCode(DIFF)
    expect(diff, '后台线程读 ThreadLocal：拿到的是"上一个占用该线程的请求"的身份')
      .not
      .toContain('PermissionContextHolder')
    const compare = methodBody(diff, 'public void compare(String resourceKey,')
    expect(compare, '比对改回同步跑：影子双跑是额外一次 COUNT，占用业务线程就是在拿生产 QPS 换观测')
      .toContain('executor.execute(')
    expect(compare, '采样判定被抄第二份').toContain('sampled(resourceKey)')
    expect(diff.match(/nextDouble\(\)/g)?.length ?? 0, '出现第二处随机采样：两处采样率会互相掩盖')
      .toBe(1)
  })
})

describe('mask contract · 读侧 Mapper（P2 换轨的注册面）', () => {
  const RESOURCES = join(ROOT, 'SPMS-Server', 'src', 'main', 'resources')
  const MAPPER_XML_DIR = join(RESOURCES, 'mapper')
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const CONFIG = join(DPM, 'config', 'MapperReadConfiguration.java')
  const PREDICATE = join(DPM, 'service', 'RowPermissionPredicate.java')
  const MAPPER_INTERFACE = /interface\s+(\w+)\s+extends\s+KuboBaseMapper</

  /**
   * main 里所有读侧 Mapper 接口（约定：extends KuboBaseMapper）
   */
  function mapperInterfaces(): { name: string, file: string, source: string }[] {
    return collect(SERVER, '.java')
      .map(file => ({ file, source: readJavaCode(file), name: '' }))
      .map((entry) => {
        const matched = MAPPER_INTERFACE.exec(entry.source)
        return matched ? { name: matched[1], file: entry.file, source: entry.source } : null
      })
      .filter((entry): entry is { name: string, file: string, source: string } => entry !== null)
  }

  function xmlMappers(): { file: string, source: string }[] {
    return collect(MAPPER_XML_DIR, '.xml').map(file => ({ file, source: read(file) }))
  }

  /**
   * 去掉注释的 XML：`<!-- tenant_id = 1 -->` 是文档，不是语句
   */
  function stripXmlComments(source: string): string {
    return source.replace(/<!--[\s\S]*?-->/g, '')
  }

  it('读侧 Mapper 只由开关注册：接口不标 @Mapper，扫描只认 markerInterface', () => {
    const found = mapperInterfaces()
    expect(found.length, '一个 KuboBaseMapper 子接口都没有：本条门禁会空转，请先确认扫描规则或删掉本条')
      .toBeGreaterThan(0)
    for (const mapper of found) {
      expect(mapper.source, `${mapper.name} 标了 @Mapper：MyBatis-Plus 的自动扫描器不认识 `
      + 'kubo.data-permission.mapper-read 开关，application.yml 里那行 enabled: false 就成了装饰')
        .not
        .toMatch(/@Mapper(?!\s*Scan)/)
    }
    const config = readJavaCode(CONFIG)
    expect(config, '注册脱离了 @ConditionalOnProperty：默认关就不再等于"容器里没有这条读路径"')
      .toMatch(/@ConditionalOnProperty\(prefix = "kubo\.data-permission\.mapper-read", name = "enabled", havingValue = "true"/)
    expect(config, '扫描改成了全量注册：只有继承 KuboBaseMapper 的接口才该成为 Mapper')
      .toContain('markerInterface = KuboBaseMapper.class')
    const scans = collect(SERVER, '.java').filter(file => readJavaCode(file).includes('@MapperScan'))
    expect(scans.length, '@MapperScan 出现多于一处 = 注册面散开，开关管不住全部')
      .toBe(1)
    // 反证：伪造一份带 @Mapper 的接口源码，判定必须为红（"它没红"不等于"它在防"）
    expect(/@Mapper(?!\s*Scan)/.test('public interface X { } @Mapper\ninterface Y { }'), '这条判据本身失效了：正则不再能识别 @Mapper').toBe(true)
  })

  it('每个 KuboBaseMapper 子接口都有同名 XML，namespace 指向接口全限定名', () => {
    const xmls = xmlMappers()
    expect(xmls.length, 'resources/mapper 下没有 XML：要么目录漂了，要么 XML 没进版本库').toBeGreaterThan(0)
    for (const mapper of mapperInterfaces()) {
      const pkg = /package\s+([\w.]+);/.exec(mapper.source)?.[1] ?? ''
      const expected = `${pkg}.${mapper.name}`
      const hit = xmls.filter(entry => stripXmlComments(entry.source).includes(`namespace="${expected}"`))
      expect(hit.length, `${mapper.name} 没有对应 XML（namespace="${expected}"）：接口能编译、启动不报错，`
      + '第一次调用才抛 Invalid bound statement —— 这类错必须由门禁在提交前拦住')
        .toBe(1)
      expect(hit[0].source, `${mapper.name} 的 XML 用了 MP 推断的表名（没写 FROM 目标表）`)
        .toMatch(/FROM\s+\w+/)
    }
  })

  it('行级判据只许来自渲染器：XML 里不得手写归属条件', () => {
    const xmls = xmlMappers()
    expect(xmls.length, 'resources/mapper 下没有 XML：这条门禁会空转').toBeGreaterThan(0)
    for (const entry of xmls) {
      const body = stripXmlComments(entry.source)
      expect(body, `${baseName(entry.file)} 里手写了归属列条件：那"谁能看哪些行"就有两份实现，`
      + '改策略时改一处漏一处，而漏的那处不报错、只多放行行')
        .not
        .toMatch(/(?:tenant|creator|department|factory|warehouse)_id\s*(?:=|in)\s/i)
    }
    const name = /FRAGMENT_KEY\s*=\s*"(\w+)"/.exec(readJavaCode(PREDICATE))?.[1] ?? ''
    expect(name, 'RowPermissionPredicate.FRAGMENT_KEY 必须是个简单标识符（它是 ${} 的键名）')
      .toMatch(/^[A-Z_]\w*$/i)
    expect(xmls.some(entry => entry.source.includes(`\${${name}}`)), `没有任何 mapper XML 使用 \${${name}}：片段注入只是名义上接上了`)
      .toBe(true)
  })

  it('命名参数换写与片段工厂各只有一处（判据合一前不许出现第二份实现）', () => {
    const offenders: string[] = []
    const factories = /RowPermissionPredicate\s*\.\s*(?:tenant|owner|department|factory|warehouse)\s*\(/
    for (const file of collect(SERVER, '.java')) {
      if (file === PREDICATE) {
        continue
      }
      const code = readJavaCode(file)
      if (code.includes('"#{') || factories.test(code)) {
        offenders.push(baseName(file))
      }
    }
    expect(offenders, '出现了第二处把片段写成 MyBatis 形式、或直接用 SQL 侧重建判据的地方：'
    + 'JPA 侧与 Mapper 侧各算一遍，迟早给出不同答案（判据合一见施工图 §19.34）')
      .toEqual([])
    expect(factories.test('return RowPermissionPredicate.tenant("material", "tenant_id", id);'), '这条判据本身失效了：正则不再能识别片段工厂').toBe(true)
  })
})

describe('mask contract · 归属判据合一', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const RULE = join(DPM, 'rule')
  const SUBJECT = join(RULE, 'SubjectDimensionRules.java')
  const SPEC = join(DPM, 'service', 'DataPermissionSpecification.java')
  const PREDICATE = join(DPM, 'service', 'RowPermissionPredicate.java')
  const RENDERERS = ['JpaPermissionRuleCompiler.java', 'SqlPermissionRuleRenderer.java', 'PermissionRuleEvaluator.java']

  const dimensionSubjectSwitch = /case\s+"TENANT"\s*->/

  it('主体维度只有一处判据：拼条件的 switch 不许长在 JPA 侧', () => {
    const subject = readJavaCode(SUBJECT)
    expect(subject, '归属判据工厂里已经没有租户档了：它被挪去哪了？合一的前提是只有一处')
      .toMatch(dimensionSubjectSwitch)

    const spec = readJavaCode(SPEC)
    expect(spec, 'JPA 侧又开始自己 switch 主体类型：那 SQL 侧迟早要再抄一份（§19.35 就是删掉这个形状）')
      .not
      .toMatch(dimensionSubjectSwitch)
    for (const name of ['buildTenantPredicate', 'buildOwnerPredicate', 'buildDepartmentPredicate', 'buildFactoryPredicate', 'buildWarehousePredicate', 'compareAny(']) {
      expect(spec, `归属判定被抄回了 Specification（${name}）：判据两份实现各自绿，比一份都不写更危险`)
        .not
        .toContain(name)
    }
    expect(spec, 'Specification 直接碰实体元模型去试绑归属列：那是把"哪个列能绑"这个决定又搬回 JPA 侧')
      .not
      .toContain('JpaPathResolver')

    // 分工必须还是两件事：判"适用"与判"拼什么条件"各自一处，且不许在第三个文件里出现第三种写法
    const holders = collect(SERVER, '.java').filter(file => dimensionSubjectSwitch.test(readJavaCode(file))).map(file => baseName(file)).sort()
    expect(holders, '主体类型的 switch 出现在意料之外的文件里：合一之后它只该有两个住处（拼条件 = SubjectDimensionRules、判适用 = SubjectMatcher）')
      .toEqual(['DataPermissionSubjectMatcher.java', 'SubjectDimensionRules.java'].sort())
    // 反证：伪造一份"JPA 侧自己又 switch 了"的源码，判定必须为真
    expect(dimensionSubjectSwitch.test('switch (t) { case "TENANT" -> x; }'), '这条判据本身失效了').toBe(true)
  })

  it('同一棵树的三个渲染方向都必须认 Dimension 节点（少一边就是静默不一致）', () => {
    for (const file of RENDERERS) {
      const code = readJavaCode(join(RULE, file))
      expect(code, `${file} 不认 Dimension：主体维度到了它这里会被当成"没有条件"或"不成立"，`
      + '而这种静默在行数上表现为多放行一些行，不会报错')
        .toContain('Dimension')
    }
    const predicate = readJavaCode(PREDICATE)
    expect(predicate, 'SQL 侧又出现了维度工厂（tenant/owner/...）：那是第二份"主体→列"的判定，必须走规则树')
      .not
      .toMatch(/public static RowPermissionPredicate (?:tenant|owner|department|factory|warehouse)\(/)
  })

  it('恒假只有一个写法：兜底出口不许各抄字面量', () => {
    const files = collect(SERVER, '.java').filter(file => readJavaCode(file).includes('"1 = 0"'))
    expect(files.map(file => baseName(file)), '"1 = 0" 出现在多个文件里：哪一处写错成 "1 = 1"，'
    + '那个分支就静默放开全表，而 grep 只会先找到那个写对的地方')
      .toEqual(['RowPermissionPredicate.java'])
  })
})

describe('mask contract · ORM 对账的证据落点（§19.36）', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const CONFIG = join(DPM, 'config', 'MapperReadConfiguration.java')
  const SHADOW = join(DPM, 'service', 'ShadowDiffService.java')
  const REGISTRY = join(DPM, 'service', 'RowParityRegistry.java')
  const COUNTER = join(DPM, 'service', 'RowParityCounter.java')
  const READINESS = join(DPM, 'service', 'DataResourceService.java')
  const PARITY_ENTITY = join(DPM, 'entity', 'DataPermissionOrmParityEntity.java')
  const DIFF_ENTITY = join(DPM, 'entity', 'DataPermissionDiffEntity.java')
  const MIGRATION_DIR = join(ROOT, 'SPMS-Server', 'docs', 'database', 'migration')
  const TEST_SCHEMA = join(ROOT, 'SPMS-Server', 'src', 'test', 'resources', 'schema-test.sql')
  const YML = join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml')
  const PARITY_TABLE = 'data_permission_orm_parity_log'

  it('对账表名三处同源：迁移脚本 / 实体 / H2 测试 schema', () => {
    const migration = collect(MIGRATION_DIR, '.sql').filter(file => read(file).includes(`CREATE TABLE IF NOT EXISTS \`${PARITY_TABLE}\``))
    expect(migration, `迁移脚本里没有 ${PARITY_TABLE}：生产上是"实体在、表不在"，`
    + '启动期 ddl-auto=validate 直接崩，而 H2 那边因为另有手抄 schema 完全不会红')
      .toHaveLength(1)
    expect(read(PARITY_ENTITY), '对账实体没把表名钉在 @Table 上：那三处同源就无从对起')
      .toContain(`@Table(name = "${PARITY_TABLE}")`)
    expect(read(TEST_SCHEMA), 'H2 测试 schema 里没有这张表：对账用例会在"表不存在"上红，'
    + '而那条红看起来像业务缺陷，实际是缺一份手抄')
      .toContain(PARITY_TABLE)

    // 实体声明的每一列都要在迁移脚本里出现（ddl:check 也查这件事，但它跑在 Web job；
    // 这里只查表名与列名是否同现于那份 CREATE TABLE，出错时 annotation 更早、更靠近改代码的人）
    const ddl = read(migration[0])
    const block = ddl.slice(ddl.indexOf(`CREATE TABLE IF NOT EXISTS \`${PARITY_TABLE}\``))
    const columns = [...readJavaCode(PARITY_ENTITY).matchAll(/@Column\(name = "([a-z_]+)"/g)].map(m => m[1])
    // 列数写死是有意的：加列时必须同时改迁移脚本、H2 手抄与这个数字，
    // 少改一处就会在这里红 —— 换成"至少 12 列"就变成一条永远过的计数
    expect(columns, '对账实体的 @Column 数量与门禁不符：新增/删除列要同时改迁移脚本、schema-test.sql 和这一处')
      .toHaveLength(12)
    for (const column of columns) {
      expect(block, `${PARITY_TABLE} 少了列 ${column}：MySQL 上"表存在但少列"是运行期 500，不是启动期报错`)
        .toContain(`\`${column}\``)
    }
  })

  it('两张证据表互不越界：verdict 常量不许复用', () => {
    const parity = readJavaCode(PARITY_ENTITY)
    const diff = readJavaCode(DIFF_ENTITY)
    expect(parity, '对账表里出现了影子差异的 verdict：两种判据混进一张表之后，'
    + 'enforce-readiness 的"有正向样本"就会被"两套 ORM 一致"这种无关语义污染')
      .not
      .toContain('EXPECTED_FILTER')
    expect(diff, '影子差异表里出现了对账的 MAPPER_* verdict：同上，方向反了')
      .not
      .toMatch(/VERDICT_MAPPER_(?:LOOSER|STRICTER)/)
    for (const verdict of ['CONSISTENT', 'MAPPER_STRICT', 'MAPPER_LOOSE', 'ERROR']) {
      expect(parity, `对账实体缺 verdict 常量 ${verdict}：判据分档写死在 SQL 字符串上，改一处漏一处`)
        .toContain(`"${verdict}"`)
    }
  })

  it('对账只在 mapper-read 那块开关底下注册；registry 全仓只构造一次', () => {
    const config = readJavaCode(CONFIG)
    expect(config, 'RowParityRegistry 的构造点跑了：注册面与开关分离之后，'
    + '"关掉就没有对账"这句不再成立，运维要从两个地方确认同一件事')
      .toContain('new RowParityRegistry(')
    expect(config, 'MapperReadConfiguration 不再是条件装配的：它一存在，探针就在，默认关等于没关')
      .toMatch(/@ConditionalOnProperty\(prefix = "kubo\.data-permission\.mapper-read", name = "enabled", havingValue = "true"\)/)

    const holders = collect(SERVER, '.java')
      .filter(file => !file.endsWith('RowParityRegistry.java') && readJavaCode(file).includes('new RowParityRegistry('))
      .map(file => baseName(file))
    expect(holders, '`new RowParityRegistry(` 出现在第二个地方：注册面分裂，谁在跑对账就没人说得清')
      .toEqual(['MapperReadConfiguration.java'])

    const yml = read(YML)
    // 注释写在键的<em>上面</em>（YAML 的习惯），所以从键往前取一段再判：
    // 只往后取会永远判不到，那条"配置必须自己说清楚"的门禁就变成一条常绿的装饰
    const keyAt = yml.indexOf('mapper-read:\n')
    expect(keyAt, 'application.yml 里找不到 mapper-read 这一段：读侧开关漂了位置，注释与配置对不上').toBeGreaterThan(-1)
    const block = yml.slice(Math.max(0, keyAt - 1200), keyAt + 60)
    expect(block, 'application.yml 里 mapper-read 那段没提对账：一个开关管两件事，配置必须自己说清楚，'
    + '否则运维以为它只管读端点')
      .toMatch(/ORM_PARITY|对账/)
    expect(yml.match(/^ {4}mapper-read:\n {6}enabled: false$/m), '读侧 Mapper 不再是默认关：那对账就默认在生产跑').toBeTruthy()
    expect(readJavaCode(REGISTRY), '注册表没有对"同一资源两个探针"设防：静默取第一个，证据就来自随机那张表')
      .toContain('duplicated RowParityCounter')
  })

  it('探针接口的方法形状：MyBatis 会给抽象方法找语句', () => {
    const counter = readJavaCode(COUNTER)
    expect(counter, 'RowParityCounter 自己声明了非 default 的业务方法之外的东西：实现方一旦把它写成抽象方法，'
    + 'MyBatis 会去找一条不存在的语句，报 Invalid bound statement（通常已经是线上）')
      .toContain('String parityResourceKey();')

    const implementors = collect(SERVER, '.java').filter(file => /implements[^;]*RowParityCounter|extends[^;]*RowParityCounter/.test(readJavaCode(file)))
    expect(implementors, '没有任何探针实现：这张表与整套对账判据就成了没人消费的机器，要么接上第一个资源，要么删掉')
      .toHaveLength(21)
    for (const file of implementors) {
      expect(readJavaCode(file), `${baseName(file)} 的 parityResourceKey 必须是 default 方法`)
        .toMatch(/default\s+String\s+parityResourceKey\s*\(\)/)
    }
  })

  it('对账的组合方向必须是 ALL_OF，且判据只认 LOOSE/ERROR 两档', () => {
    const shadow = readJavaCode(SHADOW)
    const parityBody = shadow.slice(shadow.indexOf('private void runParity('))
    expect(parityBody, '对账侧不是 ALL_OF：多策略在 JPA 侧是被 builder.and(...) 合起来的，'
    + '这里写成 ANY_OF 会让两边一起放松，行数还"一致"，对账从此只生产假绿灯')
      .toContain('PermissionRuleOp.ALL_OF')
    expect(parityBody, '对账侧出现了 ANY_OF：同上，这是能把整套证据变成噪声的一处改动')
      .not
      .toContain('ANY_OF')

    const readiness = readJavaCode(READINESS)
    for (const verdict of ['VERDICT_MAPPER_LOOSER', 'VERDICT_ERROR', 'VERDICT_CONSISTENT', 'VERDICT_MAPPER_STRICTER']) {
      expect(readiness, `放开判据没引用 ${verdict}：改成"verdict 不等于 CONSISTENT 就算异常"是一把捞，`
      + '会把可解释的 MAPPER_STRICT 也变成长期红灯')
        .toContain(verdict)
    }
    // 阻塞位用"读结构"而不是"匹配一段代码"：后者要靠 [\s\S]{0,400} 这种区间正则，
    // 既能被 ESLint 的 no-super-linear-backtracking 挡下，也容易在下次换行就悄悄不再匹配
    const from = readiness.indexOf('in.parityRequired()')
    const to = readiness.indexOf('checks.add(new ReadinessCheck("CONFIG_NOTES"')
    expect(from, 'ORM_PARITY 的三档判据找不到了：是谁把它挪出 readinessChecks 的？').toBeGreaterThan(-1)
    const parityBlock = readiness.slice(from, to > from ? to : readiness.length)
    // 逐档把 (passed, blocking) 两个位读出来比"数逗号"可靠：反证时我先写成统计 'true, false,'，
    // 结果把 LOOSE 那一档改成 'false, false,' 照样过关 —— 计数式判据只认它自己想到过的写法，
    // 而这种门禁的价值恰恰在"没想到的那种改法也要红"
    const flags = [...parityBlock.matchAll(
      /new ReadinessCheck\("ORM_PARITY",\s*"[^"]*",\s*(true|false),\s*(true|false),/g,
    )].map(hit => `${hit[1]}/${hit[2]}`)
    expect(flags, 'ORM_PARITY 五档的 (passed, blocking) 组合变了。期望：不适用=通过且不阻塞，'
    + '缺表/多放行/零样本=红且阻塞，有证据=绿且阻塞。'
    + '把任何一档的 blocking 降成 false，换轨就重新变成"一次改配置就能打坏生产"的动作；'
    + '把"不适用"升成 true，未换轨的 23 个资源会被一条它永远无法满足的判据挡住')
      .toEqual(['true/false', 'false/true', 'false/true', 'false/true', 'true/true'])
  })
})

describe('mask contract · 拒绝事件落库（B4）', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const AUDIT = join(DPM, 'service', 'DataPermissionAudit.java')
  const RECORDER = join(DPM, 'service', 'DataPermissionDenyLogRecorder.java')

  it('deny 计数之外必须真的落下拒绝事件', () => {
    const body = methodBody(readJavaCode(AUDIT), 'public void deny(')
    expect(body, 'deny 不再调 recorder：事后追认「谁在何时、因何、在哪个档位下被拒」又回到只有计数+日志的旧口径')
      .toContain('recorder.record(')
  })

  it('身份与档位在业务线程取成值，异步体不读 ThreadLocal', () => {
    const code = readJavaCode(RECORDER)
    const toEvent = methodBody(code, 'DenyEvent toEvent(')
    const persist = methodBody(code, 'void persist(')
    expect(toEvent, 'toEvent 不在业务线程取上下文：后台线程会读到「上一个占用该线程的请求」的身份，写进审计就是串号')
      .toContain('PermissionContextHolder')
    expect(persist, '异步体去读 PermissionContextHolder：ThreadLocal 在后台线程拿到的不是这次拒绝的身份')
      .not
      .toContain('PermissionContextHolder')
  })

  it('落库失败与缺 Bean 都不许改变拒绝结果', () => {
    const persist = methodBody(readJavaCode(RECORDER), 'void persist(')
    expect(persist, 'persist 不再判 getIfAvailable：缺 Bean（测试上下文/未建表）会把异步任务炸掉，证据失败不该有第二个出口')
      .toContain('getIfAvailable()')
    expect(persist, 'persist 吞不掉写库异常：拒绝路径会被落库失败改写（审计坏了不能变成 500 或放行）')
      .toContain('catch (RuntimeException')
  })

  it('deny-logs 路径前后端对得上，且只挂在管理端点上', () => {
    const API = join(ROOT, 'src', 'api', 'system.ts')
    expect(read(API)).toContain('request.get(\'/dataResource/deny-logs\'')
    const controller = join(DPM, 'controller', 'DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("deny-logs")')
    for (const file of collect(join(DPM, 'controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `拒绝事件端点出现在 ${baseName(file)}：这份「谁被拒过」的跨角色数据会从 dataResource 权限面漂走`)
        .not
        .toContain('deny-logs')
    }
  })

  it('前端只回显拒绝事件，不把档位/原因当判定', () => {
    const report = read(join(ROOT, 'src', 'composables', 'useDenyLogReport.ts'))
    expect(report, '前端开始判 mode/reason 语义：档位与原因是后端判定的，抄一遍就是第二套真相')
      .not
      .toMatch(/mode\s*===|reason\s*===|reason\s*!==/)
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list).toContain('useDenyLogReport')
    expect(list, '取数失败必须能被看见（这里显示「没人被拒」等于把取不到表读成审计干净）')
      .toMatch(/<ElAlert[^>]*v-else-if="denyLogFailed"/)
  })
})

describe('mask contract · 操作审计落库（P3）', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const RECORDER = join(DPM, 'service', 'OperationLogRecorder.java')

  it('operation-logs 路径前后端对得上，且只挂在管理端点上', () => {
    const API = join(ROOT, 'src', 'api', 'system.ts')
    expect(read(API)).toContain('request.get(\'/dataResource/operation-logs\'')
    const controller = join(DPM, 'controller', 'DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("operation-logs")')
    for (const file of collect(join(DPM, 'controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `操作审计端点出现在 ${baseName(file)}：这份「谁改过」的跨角色数据会从 dataResource 权限面漂走`)
        .not
        .toContain('operation-logs')
    }
  })

  it('前端只回显操作审计，不把动作当判定', () => {
    const report = read(join(ROOT, 'src', 'composables', 'useOperationLogReport.ts'))
    expect(report, '前端开始判 action 语义：动作是后端 BaseService 钩子判定的，抄一遍就是第二套真相')
      .not
      .toMatch(/action\s*===|action\s*!==/)
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list).toContain('useOperationLogReport')
    expect(list, '取数失败必须能被看见（这里显示「没人改过」等于把取不到表读成审计干净）')
      .toMatch(/<ElAlert[^>]*v-else-if="operationLogFailed"/)
  })

  it('落库失败与缺 Bean 都不许改变业务结果', () => {
    const persist = methodBody(readJavaCode(RECORDER), 'void persist(')
    expect(persist, 'persist 不再判 getIfAvailable：缺 Bean（测试上下文/未建表）会把异步任务炸掉，证据失败不该有第二个出口')
      .toContain('getIfAvailable()')
    expect(persist, 'persist 吞不掉写库异常：业务路径会被落库失败改写（审计坏了不能变成 500）')
      .toContain('catch (RuntimeException')
  })
})

describe('mask contract · 策略指向缺失/未启用资源盘点', () => {
  const DPM = join(SERVER, 'module', 'system', 'datapermission')
  const ANALYZER = join(DPM, 'service', 'PolicyResourceReferenceAnalyzer.java')

  it('policy-resource-risks 路径前后端对得上，且只挂在管理端点上', () => {
    const API = join(ROOT, 'src', 'api', 'system.ts')
    expect(read(API)).toContain('request.get(\'/dataResource/policy-resource-risks\'')
    const controller = join(DPM, 'controller', 'DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("policy-resource-risks")')
    for (const file of collect(join(DPM, 'controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `策略资源引用盘点出现在 ${baseName(file)}：这份跨角色策略地图会从 dataResource 权限面漂走`)
        .not
        .toContain('policy-resource-risks')
    }
  })

  it('判据与运行期同源：ENABLED 是 findResource 的唯一放行状态', () => {
    const analyzer = readJavaCode(ANALYZER)
    expect(analyzer, '分析器不再以 ENABLED 判定资源可用：与 DataPermissionService#findResource 分叉，面板会把「能用」的资源报成风险')
      .toContain('ENABLED')
    // 运行期判据：resource == null || !"ENABLED".equals(status) ? null : resource
    const findResource = methodBody(
      readJavaCode(join(DPM, 'service', 'DataPermissionService.java')),
      'DataResourceEntity findResource(String resourceKey)',
    )
    expect(findResource, 'findResource 改了放行状态却没同步分析器').toContain('ENABLED')
  })

  it('前端只回显结论，不自己判 kind / 资源是否可用', () => {
    const report = read(join(ROOT, 'src', 'composables', 'usePolicyResourceRiskReport.ts'))
    expect(report, '前端开始判 kind / resourceId 语义：孤儿与未启用的判据在 findResource 里只有一份，抄第二遍就是第二套真相')
      .not
      .toMatch(/kind\s*===|kind\s*!==|resourceId\s*===|ENABLED/)
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'policy', 'list.vue'))
    expect(list).toContain('usePolicyResourceRiskReport')
    expect(list, '取数失败必须能被看见（这里显示「没有风险」等于把取不到表读成盘点干净）')
      .toMatch(/<ElAlert[^>]*v-else-if="refRiskFailed"/)
  })
})

describe('mask contract · Outbox 同事务写入（P5 第一刀）', () => {
  it('发布先落 outbox 再走 Redis：持久化与实时两条路径各司其职', () => {
    const publisher = readJavaCode(join(SERVER, 'module/system/datapermission/event/DataPermissionEventPublisher.java'))
    const body = methodBody(publisher, 'private void publish(')
    expect(body, 'publish 不再写 outbox：权限变更又回到「Redis 一挂就丢」').toContain('outboxRecorder.record(')
    expect(body, 'publish 不再走 Redis 实时广播：多实例缓存失效会被退化成单实例').toContain('redisHelper.publish(')
    expect(body.indexOf('outboxRecorder.record('), '落库必须在广播之前：先广播后落库，落库失败时其它实例已经拿到失效、本实例却以为没发出')
      .toBeLessThan(body.indexOf('redisHelper.publish('))
  })

  it('记录器落 PENDING 且不吞 DB 异常：落不下 outbox 必须让调用方看见', () => {
    const recorder = readJavaCode(join(SERVER, 'module/system/datapermission/service/PermissionChangeOutboxRecorder.java'))
    expect(recorder, '记录器不再写 PENDING：初值不再与 DDL 默认一致').toContain('PermissionChangeOutboxStatus.PENDING.name()')
    expect(recorder, '记录器丢了幂等键：event_key 唯一约束会退化成「随它去」').toContain('setEventKey(')
    // 只幂等跳过重复键，其余异常不 catch（否则「变更发生但无持久化记录」会被静默吞掉）
    expect(recorder, '记录器把 save 的异常也 catch 掉了：outbox 会给出「已记录」的错觉')
      .not
      .toMatch(/catch\s*\(\s*(?:Exception|RuntimeException)\b/)
  })

  it('退避纯逻辑只有一份：MAX_RETRY 与 nextRetryAtMillis 钉住，Dispatcher 不得重抄', () => {
    const backoff = readJavaCode(join(SERVER, 'module/system/datapermission/service/PermissionChangeOutboxBackoff.java'))
    expect(backoff, '退避逻辑没了 MAX_RETRY：死信阈值无单一来源').toContain('MAX_RETRY')
    expect(backoff, '退避逻辑没了 nextRetryAtMillis：Dispatcher 会各自抄一份指数退避').toContain('nextRetryAtMillis(')
    const status = readJavaCode(join(SERVER, 'module/system/datapermission/service/PermissionChangeOutboxStatus.java'))
    for (const s of ['PENDING', 'PROCESSING', 'DONE', 'DEAD']) {
      expect(status, `状态机缺 ${s}`).toContain(s)
    }
  })

  it('dispatcher 状态迁移只走 OutboxDispatchStep，且默认关（真实环境才开）', () => {
    const dispatcher = readJavaCode(join(SERVER, 'module/system/datapermission/service/PermissionChangeOutboxDispatcher.java'))
    const body = methodBody(dispatcher, 'private void dispatch(')
    expect(body, 'Dispatcher 不再调 OutboxDispatchStep：状态迁移又抄了一份').toContain('OutboxDispatchStep.markDone(')
    expect(body, 'Dispatcher 失败路径不再调 OutboxDispatchStep.markFailed：死信/退避判据分叉').toContain('OutboxDispatchStep.markFailed(')
    expect(dispatcher, 'Dispatcher 没挂 @Scheduled：真实环境开了开关也不会轮询').toContain('@Scheduled(')
    expect(dispatcher, 'Dispatcher 没复用实时发布同一频道：重投会丢到别的频道，消费端收不到').toContain('DataPermissionEventPublisher.CHANNEL')
    const appYml = read(join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'))
    const outboxSwitch = /outbox:\n\s+enabled:\s+(\w+)/.exec(appYml)
    expect(outboxSwitch, 'application.yml 里没有 outbox.enabled：没人看见的开关等于没有开关').not.toBeNull()
    expect(outboxSwitch![1], 'Dispatcher 默认必须关（本地/测试环境不该启动轮询）').toBe('false')
  })

  it('死信重放/丢弃是独立特权接口，重放状态迁移单源，且落审计（P5 人工补偿）', () => {
    const controller = readJavaCode(join(SERVER, 'module/system/datapermission/controller/DataResourceController.java'))
    expect(controller, '缺死信列表端点：管理员看不到哪些变更通知没送出去').toContain('@GetMapping("outbox-dead")')
    expect(controller, '缺死信重放端点：DEAD 永远死着，人工补偿没有入口').toContain('@PostMapping("outbox-replay")')
    expect(controller, '缺死信丢弃端点：已无意义的事件无法清掉').toContain('@PostMapping("outbox-discard")')

    const identities = readJavaCode(join(SERVER, 'module/system/datapermission/SystemPermissionIdentities.java'))
    expect(identities, '缺 DATA_PERMISSION_OUTBOX 身份：死信处置没有独立权限门').toContain('DATA_PERMISSION_OUTBOX')

    const admin = readJavaCode(join(SERVER, 'module/system/datapermission/service/PermissionChangeOutboxAdminService.java'))
    const body = methodBody(admin, 'private void applyReplay(')
    expect(body, '重放状态迁移没走 OutboxDispatchStep.markReplay：状态判据又抄了一份').toContain('OutboxDispatchStep.markReplay(')
    expect(admin, '重放前不校验 outbox 权限：任何能调端点的人都能重放/丢弃死信').toContain('systemPermissionService.has(')
    expect(admin, '重放/丢弃不落审计：特权操作不可追责').toContain('denyLogRecorder.record(')

    const step = readJavaCode(join(SERVER, 'module/system/datapermission/service/OutboxDispatchStep.java'))
    expect(step, 'OutboxDispatchStep 缺 markReplay：重放语义没有单一来源').toContain('public static Result markReplay(')
  })

  it('死信处置路径前后端对得上，且只挂在管理端点上', () => {
    const API = join(ROOT, 'src', 'api', 'system.ts')
    expect(read(API)).toContain('request.get(\'/dataResource/outbox-dead\'')
    expect(read(API)).toContain('request.post(\'/dataResource/outbox-replay\'')
    expect(read(API)).toContain('request.post(\'/dataResource/outbox-discard\'')
    const controller = join(SERVER, 'module/system/datapermission/controller/DataResourceController.java')
    expect(readJavaCode(controller)).toContain('@GetMapping("outbox-dead")')
    expect(readJavaCode(controller)).toContain('@PostMapping("outbox-replay")')
    expect(readJavaCode(controller)).toContain('@PostMapping("outbox-discard")')
    for (const file of collect(join(SERVER, 'module/system/datapermission/controller'), '.java')) {
      if (file.endsWith('DataResourceController.java')) {
        continue
      }
      expect(read(file), `死信处置端点出现在 ${baseName(file)}：这份「哪些变更通知没送出去」的数据会从 dataResource 权限面漂走`)
        .not
        .toContain('outbox-dead')
    }
  })

  it('前端只回显与触发死信处置，不重算状态迁移', () => {
    const panel = read(join(ROOT, 'src', 'composables', 'useOutboxDead.ts'))
    expect(panel, '前端开始判 status/retryCount 语义：什么算死信、重放后状态怎么走都是后端判定的').not.toMatch(/status\s*===|retryCount\s*>=|DEAD\s*===/)
    const list = read(join(ROOT, 'src', 'view', 'console', 'system', 'datapermission', 'resource', 'list.vue'))
    expect(list).toContain('useOutboxDead')
    expect(list, '取数失败必须能被看见（这里显示「没有死信」等于把取不到表读成审计干净）').toMatch(/<ElAlert[^>]*v-else-if="outboxFailed"/)
  })
})

describe('mask contract · F3 数据层收敛（system + personnel + wms + factory + open + iot + asset + channel + mes 模块已迁，全部清零）', () => {
  it('system 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'system'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'system'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'system 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useUnit', 'useCodeRule', 'useConfig', 'useMenu', 'usePermission', 'useLog', 'useMcp', 'useDataResource', 'useDataPolicy', 'useDataFieldPolicy']) {
      expect(read(join(ROOT, 'src', 'hook', 'system', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('personnel 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'personnel'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'personnel'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'personnel 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useDepartment', 'useRole', 'useUser']) {
      expect(read(join(ROOT, 'src', 'hook', 'personnel', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('wms 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'wms'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'wms'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'wms 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useInput', 'useInventory', 'useMove', 'useOutput']) {
      expect(read(join(ROOT, 'src', 'hook', 'wms', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('factory 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'factory'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'factory'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'factory 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useStorage', 'useStructure']) {
      expect(read(join(ROOT, 'src', 'hook', 'factory', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('open 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'open'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'open'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'open 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useApp', 'useOpenLog', 'useNotify']) {
      expect(read(join(ROOT, 'src', 'hook', 'open', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('iot 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'iot'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'iot'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'iot 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useParameter']) {
      expect(read(join(ROOT, 'src', 'hook', 'iot', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('asset 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'asset'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'asset'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'asset 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useContract', 'useDevice', 'useMaterial']) {
      expect(read(join(ROOT, 'src', 'hook', 'asset', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('channel 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'channel'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'channel'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'channel 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useCustomer', 'usePurchase', 'usePurchasePrice', 'useSale', 'useSalePrice', 'useSupplier']) {
      expect(read(join(ROOT, 'src', 'hook', 'channel', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('mes 模块视图不再直接 import @/model/*Service（由 hook 转发）', () => {
    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src', 'view', 'console', 'mes'), '.vue'), ...collect(join(ROOT, 'src', 'view', 'console', 'mes'), '.ts')]) {
      if (/from\s+['"]@\/model\/[^'"]*Service['"]/.test(read(file))) {
        offenders.push(file)
      }
    }
    expect(offenders, 'mes 模块视图仍直接 import @/model/*Service：F3 已迁但被回退').toEqual([])
    for (const hook of ['useBom', 'useOperation', 'useOrder', 'usePicking', 'usePlan', 'useRouting']) {
      expect(read(join(ROOT, 'src', 'hook', 'mes', `${hook}.ts`)), `${hook} 没转发 Service 类：视图又得回头 import @/model`)
        .toMatch(/export\s*\{[^}]*Service[^}]*\}\s*from\s*['"]@\/model\//)
    }
  })

  it('视图直连 model Service 总数不反弹（当前基线 0，全部清零，只许降不许升）', () => {
    const files = [...collect(join(ROOT, 'src', 'view'), '.vue'), ...collect(join(ROOT, 'src', 'view'), '.ts')]
    const total = files.reduce(
      (sum, file) => sum + (read(file).match(/from\s+['"]@\/model\/[^'"]*Service['"]/g)?.length ?? 0),
      0,
    )
    expect(total, `视图直连 @/model/*Service 反弹到 ${total}：F3 基线（0）被突破，收敛只能前进不能后退`)
      .toBeLessThanOrEqual(0)
  })
})

describe('mask contract · 安全响应头（S4 第一批）', () => {
  const FILTER = join(SERVER, 'common', 'filter', 'SecurityHeadersFilter.java')

  it('后端只注入零风险头，CSP 不进后端（会误伤前端内联脚本/图表而白屏）', () => {
    const filter = readJavaCode(FILTER)
    expect(filter, '缺 nosniff：JSON 响应可被 MIME 嗅探当 HTML 执行').toContain('X-Content-Type-Options')
    expect(filter, '缺 Referrer-Policy：令牌/路径可能经 Referer 跨源泄露').toContain('Referrer-Policy')
    expect(filter, '缺 Permissions-Policy：管理后台未禁用摄像头/麦克风/定位').toContain('Permissions-Policy')
    // CSP 涉及前端内联脚本 + ECharts dataURI + WebSocket，加在后端会白屏，必须留待前端托管层/CI 验证
    expect(filter, 'CSP 被加进后端过滤器：配置错会白屏，应在前端托管层（Nginx）收紧').not.toContain('Content-Security-Policy')
  })
})

describe('mask contract · 生产包体日志关闭（安全加固 P3）', () => {
  const PROD = join(ROOT, 'SPMS-Server', 'src', 'main', 'resources', 'application-production.yml')

  it('生产配置必须显式关闭请求/响应包体日志（airpower 会把完整 body 打印到日志）', () => {
    const yml = read(PROD)
    // 逐行断言，避免误匹配到注释里的解释文字
    const lines = yml.split('\n')
    const reqLog = lines.find(l => l.trim().startsWith('request-log:'))
    const resLog = lines.find(l => l.trim().startsWith('response-log:'))

    expect(reqLog, '生产 request-log 未显式关闭：airpower 会把请求 body 打印到日志（登录密码 / token / PII）')
      .toBeDefined()
    expect(reqLog?.trim(), '生产 request-log 必须为 false').toBe('request-log: false')

    expect(resLog, '生产 response-log 未显式关闭：airpower 会把响应 body 打印到日志（客户信息 / 查询结果）')
      .toBeDefined()
    expect(resLog?.trim(), '生产 response-log 必须为 false').toBe('response-log: false')
  })
})

describe('mask contract · 归属迁移（B1 余半块）', () => {
  it('唯一豁免挂在专用接口 + 独立权限身份，默认防篡改不变', () => {
    const identities = readJavaCode(join(SERVER, 'module/system/datapermission/SystemPermissionIdentities.java'))
    expect(identities, '缺 DATA_PERMISSION_OWNERSHIP 身份：改归属没有独立权限门').toContain('DATA_PERMISSION_OWNERSHIP')

    const controller = readJavaCode(join(SERVER, 'module/system/datapermission/controller/DataResourceController.java'))
    expect(controller, '缺 migrate-ownership 端点：改归属没有专用入口').toContain('@PostMapping("migrate-ownership")')

    const service = readJavaCode(join(SERVER, 'module/system/datapermission/service/DataOwnershipMigrationService.java'))
    const body = methodBody(service, 'public void migrate(')
    expect(body, '迁移前不校验 ownership 权限：任何能调端点的人都能改归属').toContain('systemPermissionService.has(')
    expect(body, '迁移不落审计：谁在何时把哪条数据改给谁无处可查').toContain('denyLogRecorder.record(')
    expect(body, '审计 reason 不是 ownership-migrated：特权操作没进可查口径').toContain('REASON_OWNERSHIP_MIGRATED')
    expect(body, '迁移走业务 update（会被 protectAttribution 恢复回去）：这条唯一豁免永远不生效').not.toMatch(/updateToDatabase\(|service\.update\(/)

    // 默认防篡改仍不动：豁免是「显式持有权限走专用接口」，不是把默认改松
    const protection = readJavaCode(join(SERVER, 'module/system/datapermission/service/AttributionWriteProtection.java'))
    expect(protection, '归属防篡改的默认实现被删：唯一豁免反而把默认门拆了').toContain('restoreOnUpdate(')
  })
})
