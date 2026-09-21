import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { afterEach, describe, expect, it } from 'vitest'

const script = resolve('infra/scripts/check-airpower-backend.mjs')
const fixtures: string[] = []

function check(source: string, file = 'module/Example.java') {
  const root = mkdtempSync(join(tmpdir(), 'kubo-airpower-gate-'))
  fixtures.push(root)
  const server = join(root, 'SPMS-Server')
  const target = join(server, 'src/main/java/cn/hamm/spms', file)
  mkdirSync(dirname(target), { recursive: true })
  mkdirSync(join(server, 'src/main/resources'), { recursive: true })
  writeFileSync(join(server, 'pom.xml'), '<project/>')
  writeFileSync(target, source)
  const result = spawnSync(process.execPath, [script, '--check'], {
    encoding: 'utf8',
    env: { ...process.env, KUBO_ROOT: root },
  })
  if (result.error)
    throw result.error
  return { status: result.status, output: result.stdout + result.stderr }
}

afterEach(() => {
  for (const fixture of fixtures.splice(0))
    rmSync(fixture, { recursive: true, force: true })
})

describe('后端 airpower · 总量棘轮之外的零回潮边界', () => {
  it('独立工具无第三方引用可以通过', () => {
    expect(check('package cn.hamm.spms.framework.util; class Example {}', 'framework/util/Example.java').status).toBe(0)
  })

  it('尚未迁走的存量符号仍由总量棘轮控制', () => {
    expect(check('import cn.hamm.airpower.core.Json; class Example {}').status).toBe(0)
  })

  it.each([
    'CollectionUtil',
    'ReflectUtil',
    'DateTimeUtil',
    'DictionaryUtil',
    'FileUtil',
    'NumberUtil',
    'RandomUtil',
    'StringUtil',
    'TraceUtil',
    'TreeUtil',
    'TaskUtil',
    'interfaces.ITree',
    'interfaces.IException',
    'annotation.Phone',
  ])('已退场 %s 即使只有一处、远低于总量上限也失败', (symbol) => {
    const result = check(`import cn.hamm.airpower.core.${symbol}; class Example {}`)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it.each([
    'common/AppConstant.java',
    'module/personnel/user/enums/UserTokenType.java',
    'module/system/coderule/enums/CodeRuleParam.java',
    'module/system/file/enums/FileCategory.java',
    'module/system/file/enums/FileUploadPlatform.java',
    'module/system/permission/enums/PermissionType.java',
  ])('独立业务类型 %s 不能重新接上仍有预算的旧接口', (file) => {
    const result = check('import cn.hamm.airpower.core.interfaces.IDictionary; class Example {}', file)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已独立编译的业务类型不得回潮')
  })

  it('仍受旧注解校验的 ConfigType 不会被错误地当成已迁走', () => {
    const result = check('import cn.hamm.airpower.core.interfaces.IDictionary; class Example {}', 'module/system/config/enums/ConfigType.java')
    expect(result.status).toBe(0)
  })

  it('静态成员导入不能绕过退场符号检查', () => {
    expect(check('import static cn.hamm.airpower.core.FileUtil.FILE_SCALE; class Example {}').status).toBe(1)
  })

  it('core 通配导入不能把已退场的工具隐藏起来', () => {
    expect(check('import cn.hamm.airpower.core.*; class Example {}').status).toBe(1)
    expect(check('import cn.hamm.airpower.core.interfaces.*; class Example {}').status).toBe(1)
  })

  it.each([
    'cn.hamm.airpower.core.annotation.*',
    'cn.hamm.airpower.curd.base.*',
    'cn.hamm.airpower.api.annotation.*',
  ])('通配 %s 不能隐藏后续注解或其他待迁类型', (target) => {
    const result = check(`import ${target}; class Example {}`)
    expect(result.status).toBe(1)
    expect(result.output).toContain('禁止 airpower 通配导入')
  })

  it('静态枚举通配也必须显式列出成员', () => {
    expect(check('import static cn.hamm.airpower.curd.base.Curd.*; class Example {}').status).toBe(1)
  })

  it.each([
    'common/exception/Errors.java',
    'common/exception/CustomError.java',
    'module/system/config/enums/ConfigFlag.java',
  ])('%s 不得恢复无用 IDictionary 标记', (file) => {
    const result = check('import cn.hamm.airpower.core.interfaces.IDictionary; class Example {}', file)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it('业务错误码不得恢复旧异常接口，即使总量低于预算', () => {
    expect(check('import cn.hamm.airpower.core.interfaces.IException; class Example {}', 'common/exception/Errors.java').status).toBe(1)
  })

  it.each([
    'common/errorreport/ErrorReportController.java',
    'module/system/dashboard/DashboardController.java',
    'module/system/datapermission/controller/DataResourceController.java',
    'module/system/datapermission/controller/DataFieldPolicyController.java',
    'module/system/coderule/CodeRuleController.java',
    'module/personnel/role/RoleController.java',
    'module/personnel/user/token/PersonalTokenController.java',
    'module/system/file/FileController.java',
    'base/BaseController.java',
    'base/bill/BaseBillController.java',
  ])('自持端点控制器 %s 不能恢复旧 Json 响应', (file) => {
    const result = check('import cn.hamm.airpower.core.Json; class Example {}', file)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it.each([
    'cn.hamm.airpower.curd.permission.PermissionUtil',
    'cn.hamm.airpower.curd.permission.IPermission',
  ])('已退场权限符号不得在任何文件回潮：%s', (symbol) => {
    const result = check(`import ${symbol}; class Example {}`)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it.each([
    'module/personnel/user/UserService.java',
    'DevDataInitRunner.java',
  ])('%s 已改用 PasswordHasher，不得恢复 PermissionUtil', (file) => {
    const result = check('import cn.hamm.airpower.curd.permission.PermissionUtil; class Example {}', file)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it.each([
    'module/system/datapermission/event/DataPermissionEventPublisher.java',
    'module/system/datapermission/event/DataPermissionEventListener.java',
    'module/system/datapermission/service/PermissionChangeOutboxRecorder.java',
    'module/system/datapermission/service/OperationLogSnapshot.java',
  ])('%s 不能恢复已迁走的 JSON 文本工具', (file) => {
    const result = check('import cn.hamm.airpower.core.Json; class Example {}', file)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it.each([
    'module/asset/contract/ContractService.java',
    'module/asset/material/MaterialService.java',
    'module/channel/customer/CustomerService.java',
    'module/channel/sale/SaleService.java',
    'module/channel/purchase/PurchaseService.java',
    'module/channel/purchaseprice/PurchasePriceService.java',
    'module/channel/saleprice/SalePriceService.java',
    'module/channel/supplier/SupplierService.java',
    'module/factory/storage/StorageService.java',
    'module/factory/structure/StructureService.java',
    'module/wms/input/InputService.java',
    'module/wms/move/MoveService.java',
    'module/wms/inventory/InventoryService.java',
    'module/wms/output/OutputService.java',

  ])('%s 不再直接依赖旧 Page/Sort，不能用总量下降抵消回潮', (file) => {
    for (const name of ['Page', 'Sort']) {
      const result = check(`import cn.hamm.airpower.curd.model.query.${name}; class Example {}`, file)
      expect(result.status).toBe(1)
      expect(result.output).toContain('已退场符号回潮')
    }
  })

  it('旧分页 DTO 仍允许留在项目 base 适配边界，不能假装已整体退场', () => {
    expect(check('import cn.hamm.airpower.curd.model.query.Page; class Example {}', 'base/BaseService.java').status).toBe(0)
  })

  it.each([
    'import cn.hamm.airpower.cookie.CookieHelper; class Example {}',
    'import cn.hamm.airpower.cookie.CookieConfig; class Example {}',
    'class Example { Class<?> oldAuto = cn.hamm.airpower.cookie.Auto.class; }',
    'class Example { String oldType = "cn.hamm.airpower.cookie.Auto"; }',
  ])('整个旧 Cookie 模块不得回潮：%s', (source) => {
    const result = check(source)
    expect(result.status).toBe(1)
    expect(result.output).toContain('已退场符号回潮')
  })

  it('反射字符串是真耦合，不作为注释忽略', () => {
    expect(check('class Example { String type = "cn.hamm.airpower.core.TreeUtil"; }').status).toBe(1)
  })

  it('迁移历史注释不算回潮', () => {
    const result = check(`
      // import cn.hamm.airpower.core.TreeUtil;
      /* cn.hamm.airpower.core.FileUtil.FILE_SCALE */
      class Example {}
    `)
    expect(result.status).toBe(0)
  })

  it('新底座不能把旧 Json 包装进去伪装成独立工具', () => {
    const result = check('import cn.hamm.airpower.core.Json; class Example {}', 'framework/util/Example.java')
    expect(result.status).toBe(1)
    expect(result.output).toContain('自持 framework 不得再依赖 airpower')
  })
})
