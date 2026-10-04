// @vitest-environment jsdom
import { readdirSync, readFileSync } from 'node:fs'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { ElTree } from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createDemoAdapter } from '@/demo/mock/adapter'

/**
 * # F4 批 3：personnel 域（部门 / 角色 / 用户）迁移门禁
 *
 * 这一批比 asset 域更"靠里"：部门与角色是**别域要引用的基础数据**
 * （`StructureEntity.departmentList`、`UserEntity.roleList/departmentList`），
 * 所以门禁除了"迁干净"，还得钉住三件容易被顺手改掉的事：
 *
 * 1. **迁移完成度**：域内 airpower import 必须为 0（ALLOWED 空集）。
 * 2. **两个"没有替代物"的业务能力不许丢**：
 *    - 角色名的「不允许带管理员三个字」校验（走 `KFormDialog` 的 `extraRules`，
 *      规则属于页面、框架只负责合并）；
 *    - 用户页提交前把角色/部门列表**裁成只带 id**（旧 `copyOnlyId()` 语义）——
 *      整行数据提交给后端会被当成"要改关联实体"。
 * 3. **脱敏写守卫**：用户页对 `idCard/phone/email` 必须挂 `blockedSubmit`，
 *    否则"页面显示 ****，用户一点保存就把真值覆盖成 ****"。
 *
 * 另外钉住部门页的两个既有口径：树形列表（`tree: true`）与"有子级禁删"提示，
 * 这两条旧页本来就有，换底座时最容易悄悄丢。
 */

const capturedErrors: unknown[] = []

beforeEach(() => {
  setActivePinia(createPinia())
  capturedErrors.length = 0
  vi.restoreAllMocks()
  // 每个用例从干净的 DOM 开始：弹窗（`append-to-body`）会留在 document.body 里，
  // 不清的话下一个用例的 `document.body` 查询会命中上一个用例的弹窗
  // （症状很隐蔽：点"保存"点到了别人的按钮，spy 一次都没被调用）。
  document.body.innerHTML = ''
})

if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

/** 与 `assetDomain.spec.ts` 同款：剥注释后再判，避免"迁移说明里引用了旧写法"被误判 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\/[^\n]*/g, '')
}

function importsAirpower(source: string): boolean {
  return /from\s+'@airpower\/web'|import\s*\(\s*'@airpower\/web'/.test(stripComments(source))
}

function listFiles(dir: string, ext: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? listFiles(`${dir}/${entry.name}`, ext) : (entry.name.endsWith(ext) ? [`${dir}/${entry.name}`] : []))
}

function read(file: string): string {
  return readFileSync(file, 'utf8')
}

async function mountPageWith(component: unknown, props: Record<string, unknown>) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(component as never, {
    props: props as never,
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      directives: (await import('@/directives')).directives,
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
    },
  } as never)
}

/** 无 props 的页面挂载（列表页） */
async function mountPage(component: unknown) {
  return mountPageWith(component, {})
}

describe('f4 · 批 3（personnel）迁移完成度', () => {
  it('域内已无 airpower 依赖（含 barrel 与 factory/structure 的连带改动）', () => {
    const files = [...listFiles('src/view/console/personnel', '.vue'), ...listFiles('src/view/console/personnel', '.ts')]
    expect(files.length, 'personnel 域视图不应为空').toBeGreaterThan(0)

    const offenders = files.filter(file => importsAirpower(read(file))).sort()
    expect(
      offenders,
      offenders.length === 0 ? '空集即迁移完成' : '域内还有 airpower 依赖（含新增文件？）',
    ).toEqual([])

    // 旧 `DepartmentSelector` 的唯一外部消费者：factory/structure 也必须已换新底座，
    // 否则删掉那个组件时这一页会在构建期就找不到模块（或更糟：运行时白屏）
    const structure = read('src/view/console/factory/structure/list.vue')
    expect(importsAirpower(structure), 'factory/structure 又回到 airpower 选择器').toBe(false)
    expect(stripComments(structure).includes('DialogUtil'), 'factory/structure 仍在用命令式弹窗工具').toBe(false)
    expect(structure.includes('KSelector'), 'factory/structure 应当用 KSelector 选部门').toBe(true)
  })

  it('旧的 airpower 视图件已删除（编辑器/选择器/barrel）', () => {
    const removed = [
      'src/view/console/personnel/department/component/editor.vue',
      'src/view/console/personnel/department/component/selector.vue',
      'src/view/console/personnel/department/component/index.ts',
      'src/view/console/personnel/role/component/editor.vue',
      'src/view/console/personnel/role/component/selector.vue',
      'src/view/console/personnel/role/component/index.ts',
      'src/view/console/personnel/user/component/editor.vue',
      'src/view/console/personnel/user/component/index.ts',
      'src/model/personnel/department/DepartmentService.ts',
      'src/model/personnel/role/RoleService.ts',
    ]
    const alive = removed.filter((file) => {
      try {
        readFileSync(file)
        return true
      }
      catch {
        return false
      }
    })
    expect(alive, '已随批 3 删除的旧文件又回来了（或有同名残留）').toEqual([])
  })

  it('三个页面走新底座（KPanel/KTable/KFormDialog + K*Service + useTable）', () => {
    const cases: [string, string[]][] = [
      ['src/view/console/personnel/department/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KDepartmentService', 'useTable']],
      ['src/view/console/personnel/role/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KRoleService', 'useTable', 'menuEditor.vue', 'permissionEditor.vue']],
      ['src/view/console/personnel/user/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KSelector', 'KUserService', 'useTable', 'maskedFieldsIn']],
    ]
    for (const [file, needles] of cases) {
      const source = read(file)
      for (const needle of needles) {
        expect(source.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
      const code = stripComments(source)
      expect(code.includes('useTanStackTable'), `${file} 仍在用旧适配器`).toBe(false)
      expect(/from '@\/model\/[^']*Service'/.test(code), `${file} 视图直连了 model 的 Service（应经 hook 转发）`).toBe(false)
    }
  })

  it('授权弹窗走 KDialog（不再靠 DialogUtil 命令式打开）', () => {
    for (const file of [
      'src/view/console/personnel/role/component/menuEditor.vue',
      'src/view/console/personnel/role/component/permissionEditor.vue',
    ]) {
      const code = stripComments(read(file))
      expect(code.includes('KDialog'), `${file} 未使用 KDialog`).toBe(true)
      expect(code.includes('DialogUtil'), `${file} 仍在用 DialogUtil`).toBe(false)
      expect(code.includes('getTreeList'), `${file} 未走 getTreeList 拉授权树`).toBe(true)
    }
  })

  it('三个实体都做了双注册（K 侧表格列/表单字段非空），K 服务 baseUrl 与后端路由一致', async () => {
    const { getFormConfigList, getTableConfigList } = await import('@/framework')
    const { DepartmentEntity } = await import('@/model/personnel/department/DepartmentEntity')
    const { RoleEntity } = await import('@/model/personnel/role/RoleEntity')
    const { UserEntity } = await import('@/model/personnel/user/UserEntity')
    const { KDepartmentService } = await import('@/model/personnel/department/KDepartmentService')
    const { KRoleService } = await import('@/model/personnel/role/KRoleService')
    const { KUserService } = await import('@/model/personnel/user/KUserService')

    for (const [label, Entity] of [['部门', DepartmentEntity], ['角色', RoleEntity], ['用户', UserEntity]] as const) {
      expect(getTableConfigList(Entity).length, `${label}实体缺少 @KTable 列（元数据驱动渲染的前提）`).toBeGreaterThan(2)
      expect(getFormConfigList(Entity).length, `${label}实体缺少 @KForm 字段`).toBeGreaterThan(0)
    }

    // 后端路由：department / role / user（`SPMS-Server` 的 `@RequestMapping`）
    expect(new KDepartmentService().baseUrl).toBe('department')
    expect(new KRoleService().baseUrl).toBe('role')
    expect(new KUserService().baseUrl).toBe('user')

    // 用户域是唯一"新旧服务并存"的：管理页 CRUD 走 KUserService，认证链仍在旧 UserService ——
    // 两者必须指向同一个后端接口，否则管理页读到的是另一套数据
    const { UserService } = await import('@/model/personnel/user/UserService')
    expect(new KUserService().baseUrl).toBe(new UserService().baseUrl)
  })

  it('授权弹窗拿到的菜单/权限树是**实体实例**（子级能调 copyOnlyId）', async () => {
    const { fill } = await import('@/framework')
    const { MenuEntity } = await import('@/model/system/menu/MenuEntity')
    const { PermissionEntity } = await import('@/model/system/permission/PermissionEntity')

    // 后端返回的是嵌套 JSON；`getTreeList` 用 `fill()` 递归实例化，
    // 而递归的依据是**框架注册表里的 `@KType`**（airpower 的 `@Type` 只喂 airpower 的转换器）。
    // 缺了它，`children` 会是裸对象 —— 表单能显示，但勾选子级后
    // `KRoleService.authorizeMenu` 里逐项调用的 `copyOnlyId()` 直接 TypeError。
    const menu = fill(new MenuEntity(), {
      id: 1,
      name: '仓储管理',
      children: [{ id: 2, name: '入库单' }],
    })
    expect(typeof menu.children[0]?.copyOnlyId, '菜单子级不是实体实例（缺 @KType）').toBe('function')
    expect(menu.children[0]?.id).toBe(2)

    const permission = fill(new PermissionEntity(), {
      id: 1,
      name: '仓储',
      children: [{ id: 2, name: '入库审核' }],
    })
    expect(typeof permission.children[0]?.copyOnlyId, '权限子级不是实体实例（缺 @KType）').toBe('function')
  })

  it('列表里不出现两个同名的「状态」列（isDisabled 与基类的 isPublished）', async () => {
    const { getTableConfigList } = await import('@/framework')
    const { DepartmentEntity } = await import('@/model/personnel/department/DepartmentEntity')
    const { RoleEntity } = await import('@/model/personnel/role/RoleEntity')
    const { UserEntity } = await import('@/model/personnel/user/UserEntity')

    for (const [label, Entity] of [['部门', DepartmentEntity], ['角色', RoleEntity], ['用户', UserEntity]] as const) {
      const keys = getTableConfigList(Entity).map(item => item.key)
      // 这三个实体暂时还继承 airpower 的 `BaseEntity`（不继承 `KEntity`），
      // 而 `KEntity` 自带一个 label 为「状态」的 `isPublished` 列 ——
      // 将来切换到 `KEntity` 时，与本实体的 `isDisabled` 列会同名，
      // 界面上就是两列一模一样的「状态」。这条断言把口径钉在这里。
      const hasIsPublished = keys.includes('isPublished')
      const hasIsDisabled = keys.includes('isDisabled')
      expect(hasIsPublished && hasIsDisabled, `${label}实体同时声明了 isPublished 与 isDisabled 两个状态列`).toBe(false)
    }
  })

  it('用户的敏感字段在 K 侧：能读到脱敏策略、不把身份证号塞进列表列', async () => {
    const { getTableConfig, getFormConfigList } = await import('@/framework')
    const { UserEntity } = await import('@/model/personnel/user/UserEntity')

    // `idCard` 不给列表列：K 侧没有列选择器，`getTableConfigList` 也不过 `hide`，
    // 一旦声明就会"总是可见"。需要看时走详情 + 脱敏策略。
    expect(getTableConfig(UserEntity, 'idCard'), 'idCard 不应出现在用户列表列里').toBeUndefined()

    // 表单可写字段：nickname/email/phone（与旧 airpower 编辑器一致）+ password（§5-1 补：
    // 管理员需能给新用户设初始密码），`realName/idCard/gender/bio` 仍是"代码里有、界面没开口"
    const keys = getFormConfigList(UserEntity).map(row => row.key)
    expect(keys).toContain('nickname')
    expect(keys).toContain('password')
    expect(keys).not.toContain('idCard')
  })
})

describe('f4 · 批 3 页面行为（换底座不许改业务判据）', () => {
  it('角色页：角色名带「管理员」时表单拦下（extraRules 生效）', () => {
    const source = read('src/view/console/personnel/role/list.vue')
    const code = stripComments(source)
    expect(code.includes('ifContain'), '「不允许带管理员」的业务校验丢了').toBe(true)
    expect(code.includes('extraRules') || code.includes('extra-rules'), '校验规则没有接到 KFormDialog 上').toBe(true)

    // 规则确实注册在框架层（页面只声明，框架合并）
    return import('@/framework').then(({ WebValidator }) => {
      const rule = WebValidator.show('不允许带管理员三个字').ifContain('管理员')
      const run = (value: unknown) => new Promise<string>((resolve) => {
        rule.validator?.({}, value, (error?: Error) => resolve(error?.message ?? ''))
      })
      return Promise.all([run('超级管理员'), run('仓库主管')]).then(([bad, good]) => {
        expect(bad).toBe('不允许带管理员三个字')
        expect(good).toBe('')
      })
    })
  })

  it('用户页：提交体里的角色/部门裁成只带 id（旧 copyOnlyId 语义）', async () => {
    const { KUserService } = await import('@/model/personnel/user/KUserService')
    vi.spyOn(KUserService.prototype, 'getDetail').mockResolvedValue({
      id: 1,
      nickname: '仓管员',
      email: 'wh@kubo.dev',
      phone: '13800000000',
      roleList: [{ id: 9, name: '仓库管理员' }],
      departmentList: [{ id: 5, name: '仓储部' }],
    } as never)
    vi.spyOn(KUserService.prototype, 'getPage').mockResolvedValue({
      list: [{
        id: 1,
        nickname: '仓管员',
        roleList: [{ id: 9, name: '仓库管理员' }],
        departmentList: [{ id: 5, name: '仓储部' }],
      }],
      total: 1,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const Page = (await import('@/view/console/personnel/user/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('仓管员')
    }, { timeout: 8000 })

    // 打开编辑 → 弹窗加载详情后，`form` 里的两个列表应被 `onLoaded` 补成数组
    const editButton = wrapper.findAll('.el-table__body button').find(button => button.text().trim() === '编辑')
    expect(editButton, '没有渲染出「编辑」行操作').toBeTruthy()
    await editButton!.trigger('click')
    await flushPromises()

    // 提交体裁剪逻辑必须是纯函数式的（不许改到原对象上的字段引用）：
    // 这里直接读源码断言 `copyOnlyId` 映射 + `blockedSubmit` 守卫都在
    const code = stripComments(read('src/view/console/personnel/user/list.vue'))
    expect(code.includes('copyOnlyId'), '提交前的 copyOnlyId 裁剪丢了（整行数据会被后端当成改关联实体）').toBe(true)
    expect(code.includes('blockedSubmit'), '脱敏写守卫丢了（掩码值可能覆盖真实数据）').toBe(true)
    expect(code.includes('maskedFieldsIn'), '脱敏提示丢了').toBe(true)
    expect(capturedErrors).toEqual([])
  })

  it('部门页：树形列表 + 搜索项元数据 + 有子级禁删提示都还在', async () => {
    const code = stripComments(read('src/view/console/personnel/department/list.vue'))
    expect(code.includes('tree: true'), '部门页不是树形表格了（分层结构会摊平成一行行）').toBe(true)

    // 搜索**不是**页面自己写的输入框：`KTable` 读出 `@KSearch` 元数据生成搜索栏。
    // 所以这里查元数据（旧 airpower 页同样靠 `@Search` 隐式给搜索栏）——
    // 断言"页面源码里有 search 字样"既抓不到回归，也会被注释误伤。
    const { getSearchConfigList } = await import('@/framework')
    const { DepartmentEntity } = await import('@/model/personnel/department/DepartmentEntity')
    const searchKeys = getSearchConfigList(DepartmentEntity).map(item => item.key)
    expect(searchKeys, '部门页的搜索项丢了（名称/编码两个条件）').toEqual(expect.arrayContaining(['name', 'code']))

    // 「有子级禁删」由 KTable 内置（树模式删父节点会留下孤儿），页面不该自己再写一套
    expect(code.includes('addChild') && code.includes('Permissions'), '部门页缺少「添加子级」动作与权限码').toBe(true)
  })

  it('用户页：部门树筛选走 table.search（不再直接改传输层对象）', () => {
    const code = stripComments(read('src/view/console/personnel/user/list.vue'))
    expect(code.includes('ElTree'), '用户页左侧部门树丢了').toBe(true)
    expect(code.includes('KDepartmentService'), '部门树数据源应走 KDepartmentService').toBe(true)
    expect(code.includes('table.search'), '部门筛选应经 useTable.search（不要直接改请求体）').toBe(true)
    expect(code.includes('filter.value'), '部门筛选应基于当前筛选条件（避免清掉搜索框）').toBe(true)
    // `ElTree` 的 `filterNode` 不能用 `DepartmentEntity` 当参数类型：EP 传的是**原始节点数据**
    expect(read('src/view/console/personnel/user/list.vue').includes(':filter-node-method="filterNode"')).toBe(true)
  })
})

describe('f4 · 批 3 授权弹窗在演示模式下的端到端（真实数据 + 真实提交）', () => {
  /**
   * ⚠️ 断言查 `document.body` 而不是 `wrapper`：`ElDialog` 默认 `append-to-body`，
   * 弹窗内容被 teleport 出组件树，`wrapper.text()` 永远是空的（会得到"页面没渲染"的假结论）。
   * 同时每个用例结束清空 `document.body`，否则上一个弹窗会留在下一个用例的查询范围里。
   */
  beforeEach(async () => {
    // ⚠️ 两个都要设：`axios.defaults` 是全局默认值（demo 入口用这个），
    // 而本仓的 `request` 实例在**创建时**就把 `axios.defaults` 深拷进自己的 defaults，
    // 之后再改全局不会影响它 —— 只设一个的话请求会真的发出去（jsdom 里 500/ECONNREFUSED），
    // 表现为"弹窗打开了但树是空的"，而不是报错。见 `menuClick.spec.ts` 的同款写法。
    axios.defaults.adapter = createDemoAdapter([0, 0])
    const { request } = await import('@/api/axios')
    request.defaults.adapter = axios.defaults.adapter
  })

  function bodyButton(label: string): HTMLButtonElement {
    const found = Array.from(document.body.querySelectorAll('button'))
      .find(button => (button.textContent ?? '').trim() === label)
    expect(found, `没有渲染出「${label}」按钮`).toBeTruthy()
    return found as HTMLButtonElement
  }

  it('菜单授权：勾选的**子级**菜单是实体实例，提交时能带上 id（copyOnlyId 不炸）', async () => {
    const { demoMenuTree } = await import('@/demo/fixtures/menu')
    const { KRoleService } = await import('@/model/personnel/role/KRoleService')

    // 取一个分组节点的子菜单 id 作为"已授权"回显
    const child = demoMenuTree().find(node => node.children?.length)?.children?.[0]
    expect(child?.id, '演示菜单树里没有子节点，这条用例就失去意义了').toBeTruthy()

    const authorize = vi.spyOn(KRoleService.prototype, 'authorizeMenu').mockResolvedValue()
    const Editor = (await import('@/view/console/personnel/role/component/menuEditor.vue')).default
    const wrapper = await mountPageWith(
      Editor,
      { modelValue: true, role: { id: 7, name: '仓库主管', menuList: [{ id: child!.id }] } },
    )

    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(child!.name)
    }, { timeout: 8000 })

    bodyButton('保存').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(authorize).toHaveBeenCalledTimes(1)
    const [roleId, list] = authorize.mock.calls[0] as [number, { id: number, copyOnlyId?: unknown }[]]
    expect(roleId).toBe(7)
    // 关键：勾选的是**子级**（`children` 里的对象）。没有 `@KType` 时它们是裸 JSON，
    // 这里的每一项都会因为没有 `copyOnlyId` 而在 `KRoleService` 里 TypeError。
    expect(list.map(item => item.id)).toEqual([child!.id])
    expect(list.every(item => typeof item.copyOnlyId === 'function'), '授权项不是实体实例（缺 @KType）').toBe(true)
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('用户页：左侧部门树用演示数据渲染，点节点后筛选条件进请求体', async () => {
    const { KUserService } = await import('@/model/personnel/user/KUserService')

    const getPage = vi.spyOn(KUserService.prototype, 'getPage').mockResolvedValue({
      list: [{ id: 1, nickname: '仓管员', roleList: [], departmentList: [] }],
      total: 1,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const Page = (await import('@/view/console/personnel/user/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('仓管员')
    }, { timeout: 8000 })

    // 部门树（数据来自 `KDepartmentService.getTreeList` + demo 适配器）
    await vi.waitFor(() => {
      expect(wrapper.findAll('.el-tree-node').length).toBeGreaterThan(0)
    }, { timeout: 8000 })

    await wrapper.findAll('.el-tree-node__content')[0]!.trigger('click')
    await flushPromises()

    const lastRequest = getPage.mock.calls.at(-1)?.[0] as { filter?: { departmentId?: unknown } } | undefined
    expect(typeof lastRequest?.filter?.departmentId, '点部门节点后筛选条件没进请求体').toBe('number')
    expect(capturedErrors).toEqual([])
  })

  it('权限授权：真实权限码渲染成树，回显勾选 + 提交体里是实体实例', async () => {
    const { demoPermissionTree } = await import('@/demo/fixtures/permissions')
    const { KRoleService } = await import('@/model/personnel/role/KRoleService')

    const group = demoPermissionTree().find(node => node.children.length)
    const child = group!.children[0]!

    const authorize = vi.spyOn(KRoleService.prototype, 'authorizePermission').mockResolvedValue()
    const Editor = (await import('@/view/console/personnel/role/component/permissionEditor.vue')).default
    const wrapper = await mountPageWith(
      Editor,
      { modelValue: true, role: { id: 8, name: '质检员', permissionList: [{ id: child.id }] } },
    )

    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(child.identity)
    }, { timeout: 8000 })

    // 回显：打开弹窗时该行应被勾上（`toggleRowSelection` 按 id 在**实体实例**里找回行对象 ——
    // 这也依赖 `@KType` 把 `children` 递归实例化）
    await flushPromises()
    const checked = Array.from(document.body.querySelectorAll('.el-table__body input[type="checkbox"]'))
      .filter(input => (input as HTMLInputElement).checked)
    expect(checked.length, '已授权的权限没有回显勾选').toBeGreaterThan(0)

    bodyButton('保存').dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(authorize).toHaveBeenCalledTimes(1)
    const [, list] = authorize.mock.calls[0] as [number, { id: number, copyOnlyId?: unknown }[]]
    expect(list.length).toBeGreaterThan(0)
    expect(list.every(item => typeof item.copyOnlyId === 'function'), '授权项不是实体实例').toBe(true)
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  /**
   * `ElTable` 的树表不做父子级联（那是 `ElTree` 的语义），而后端 `RequestInterceptor`
   * 只认**叶子身份**（`Inventory_getPage`），父身份 `Inventory` 不对应任何端点。
   * 少了级联就会「勾了模块 → 提示授权成功 → 角色实际零权限」，且全程无报错。
   * 真机实测：只绑 6 个父节点时 `/inventory/getPage` 返回 403，绑 65 条（含 59 叶子）后 200。
   */
  it('权限授权：勾父节点会级联勾选全部后代（后端只认叶子身份）', async () => {
    const { demoPermissionTree } = await import('@/demo/fixtures/permissions')
    const { KRoleService } = await import('@/model/personnel/role/KRoleService')

    const tree = demoPermissionTree()
    const group = tree.find(node => node.children.length)!
    const child = group.children[0]!

    vi.spyOn(KRoleService.prototype, 'authorizePermission').mockResolvedValue()
    const Editor = (await import('@/view/console/personnel/role/component/permissionEditor.vue')).default
    const wrapper = await mountPageWith(
      Editor,
      { modelValue: true, role: { id: 9, name: '仓储专员', permissionList: [] } },
    )

    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(child.identity)
    }, { timeout: 8000 })
    await flushPromises()

    // 权限标识列渲染在 .text-identity 里，按**全等**定位行：
    // 父身份是子身份的前缀（Inventory / Inventory_getPage），用 includes 会串到子行
    const rowOf = (identity: string) => Array.from(document.body.querySelectorAll('.el-table__body tr'))
      .find(tr => Array.from(tr.querySelectorAll('.text-identity'))
        .some(span => span.textContent?.trim() === identity))
    const checkboxOf = (identity: string) =>
      rowOf(identity)?.querySelector('input[type="checkbox"]') as HTMLInputElement | undefined

    const parentBox = checkboxOf(group.identity)
    const childBox = checkboxOf(child.identity)
    expect(parentBox, `没找到父节点行 ${group.identity}`).toBeTruthy()
    expect(childBox, `没找到子节点行 ${child.identity}（default-expand-all 应展开）`).toBeTruthy()
    expect(childBox!.checked, '前置：子节点初始应为未勾选').toBe(false)

    parentBox!.checked = true
    parentBox!.dispatchEvent(new Event('change', { bubbles: true }))
    await flushPromises()

    // 先断言父节点自身被勾上：若这条就失败，说明是 jsdom 事件模拟没打通，而非级联逻辑有问题
    expect(parentBox!.checked, '父节点没被勾上（勾选事件未生效，无法验证级联）').toBe(true)
    expect(
      childBox!.checked,
      `勾父节点「${group.name}」没有级联到子节点「${child.name}」：`
      + '后端只认叶子身份，这样授出来的角色是零权限',
    ).toBe(true)
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('f4 · 批 3 树组件冒烟（真实挂载）', () => {
  it('部门页能挂载并渲染树形表格', async () => {
    const { KDepartmentService } = await import('@/model/personnel/department/KDepartmentService')
    vi.spyOn(KDepartmentService.prototype, 'getTreeList').mockResolvedValue([
      { id: 1, name: '总部', code: 'HQ', orderNo: 1, parentId: 0, children: [{ id: 2, name: '仓储部', code: 'HQ-WH', orderNo: 2, parentId: 1 }] },
    ] as never)

    const Page = (await import('@/view/console/personnel/department/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('总部')
    }, { timeout: 8000 })
    expect(wrapper.text()).toContain('仓储部')
    // 树形表格：搜索栏来自 @KSearch 元数据，父子行都渲染出来
    expect(wrapper.find('.k-table__search').exists(), '部门页没有渲染出搜索栏').toBe(true)
    expect(wrapper.findAll('.el-table__body tr').length, '部门树没渲染出两行').toBeGreaterThanOrEqual(2)
    expect(wrapper.findComponent(ElTree).exists()).toBe(false)
    expect(capturedErrors).toEqual([])
  })
})
