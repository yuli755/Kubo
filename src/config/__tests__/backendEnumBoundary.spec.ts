import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const server = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
const movedEnums = [
  ['module/personnel/user/enums/UserTokenType.java', 'UserTokenType'],
  ['module/system/coderule/enums/CodeRuleParam.java', 'CodeRuleParam'],
  ['module/system/file/enums/FileCategory.java', 'FileCategory'],
  ['module/system/file/enums/FileUploadPlatform.java', 'FileUploadPlatform'],
  ['module/system/permission/enums/PermissionType.java', 'PermissionType'],
] as const

function code(path: string) {
  return readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
}

function javaFiles(root: string): string[] {
  return readdirSync(root, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? javaFiles(join(root, entry.name)) : entry.name.endsWith('.java') ? [join(root, entry.name)] : [],
  )
}

function legacyDictionaryTargets(source: string): string[] {
  const explicit = /^import\s+([\w.]+\.Dictionary);/m.exec(source)?.[1]
  const legacyShortName = explicit
    ? explicit === 'cn.hamm.airpower.core.annotation.Dictionary'
    : /^import\s+cn\.hamm\.airpower\.core\.annotation\.\*;/m.test(source)
  const targets: string[] = []
  for (const [, qualified, arguments_] of source.matchAll(/@(cn\.hamm\.airpower\.core\.annotation\.)?Dictionary\s*\(([^)]*)\)/g)) {
    if (!qualified && !legacyShortName)
      continue
    const target = /(?:^|,)\s*(?:value\s*=\s*)?([\w.]+)\.class/.exec(arguments_)?.[1]?.split('.').pop()
    if (target)
      targets.push(target)
  }
  return targets
}

describe('b2c · 只迁走不受旧字典校验约束的枚举', () => {
  it.each(movedEnums)('%s 是普通 enum，不是换名字的接口包装', (path, name) => {
    const source = code(join(server, path))
    expect(source).toContain(`public enum ${name} {`)
    expect(source).not.toMatch(/\bimplements\b|cn\.hamm\.airpower|import lombok/)
    expect(source).toContain('int getKey()')
    expect(source).toContain('String getLabel()')
  })

  it('已迁走的枚举未被旧 @Dictionary 当作约束类型使用（不能等 Maven 才发现类型边界删错）', () => {
    const names = new Set<string>(movedEnums.map(([, name]) => name))
    const violations: string[] = []
    for (const path of javaFiles(server)) {
      for (const target of legacyDictionaryTargets(code(path))) {
        if (names.has(target))
          violations.push(`${path}: ${target}`)
      }
    }
    expect(violations).toEqual([])
    // 不让参数顺序/通配导入漏过，也不拦未来真正自持的同名注解。
    expect(legacyDictionaryTargets('@cn.hamm.airpower.core.annotation.Dictionary(groups = {}, value = UserTokenType.class)'))
      .toEqual(['UserTokenType'])
    expect(legacyDictionaryTargets('import cn.hamm.airpower.core.annotation.*;\n@Dictionary(FileCategory.class)'))
      .toEqual(['FileCategory'])
    expect(legacyDictionaryTargets('import cn.hamm.airpower.core.annotation.*;\nimport owned.Dictionary;\n@Dictionary(FileCategory.class)'))
      .toEqual([])
  })

  it('其余旧字典约束保留，不能为清接口顺手删除字段校验', () => {
    for (const [file, enums] of [
      ['module/system/coderule/CodeRuleEntity.java', ['CodeRuleField', 'SerialNumberUpdate']],
      ['module/system/config/ConfigEntity.java', ['ConfigType']],
      ['module/wms/input/InputEntity.java', ['InputStatus', 'InputType']],
    ] as const) {
      const source = code(join(server, file))
      for (const name of enums)
        expect(source).toContain(`@Dictionary(value = ${name}.class`)
    }
  })

  it('创建后置仍同步，只在最后一个钩子处捕获 Exception，不能扩大成吞掉写入错误', () => {
    const source = code(join(server, 'base/bill/AbstractBaseBillService.java'))
    const body = source.slice(source.indexOf('protected void afterAppAdd('), source.indexOf('protected void afterBillAdd('))
    expect(body).toMatch(/try\s*\{\s*afterBillAdd\(bill\.getId\(\)\);\s*\}\s*catch\s*\(Exception error\)/)
    expect(body).toContain('log.error(error.getMessage(), error)')
    expect(body.indexOf('saveDetails(')).toBeLessThan(body.indexOf('try {'))
    expect(body.indexOf('audit(')).toBeLessThan(body.indexOf('try {'))
    expect(body).not.toMatch(/runAsync|CompletableFuture|Executor|catch\s*\(Throwable/)
  })
})
