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

  it('错误枚举保留的 IException 不误报为已迁走', () => {
    expect(check('import cn.hamm.airpower.core.interfaces.IException; class Example {}', 'common/exception/Errors.java').status).toBe(0)
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
