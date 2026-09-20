import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
function code(file: string) {
  return readFileSync(resolve(root, file), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

describe('b3a · 电话字段切标准约束不许改变验证边界', () => {
  it.each([
    ['module/channel/customer/CustomerEntity.java', 'WhenAdd.class, WhenUpdate.class', 'PhoneRules.INVALID_PHONE_MESSAGE'],
    ['module/channel/supplier/SupplierEntity.java', 'WhenAdd.class, WhenUpdate.class', 'PhoneRules.INVALID_PHONE_MESSAGE'],
    ['module/personnel/user/UserEntity.java', 'WhenResetMyPassword.class, WhenSendSms.class', '"手机格式不正确"'],
  ])('%s 保留分组、文案与同一规则', (file, groups, message) => {
    const source = code(file)
    expect(source).not.toMatch(/@Phone\b|core\.annotation\.\*/)
    expect(source).toContain('import jakarta.validation.constraints.Pattern;')
    const annotation = /@Pattern\(([^)]*)\)[\s\S]*?private String phone;/.exec(source)?.[1]
    expect(annotation).toBeDefined()
    expect(annotation).toContain('regexp = PhoneRules.OPTIONAL_PHONE')
    expect(annotation).toContain(`groups = {${groups}}`)
    expect(annotation).toContain(`message = ${message}`)
  })

  it('用户字段原有脱敏与性别字典校验未被通配导入清理一起移除', () => {
    const source = code('module/personnel/user/UserEntity.java')
    expect(source).toContain('@Dictionary(value = UserGender.class')
    expect(source).toContain('@Desensitize(DesensitizeType.CHINESE_NAME)')
    expect(source).toContain('@Desensitize(DesensitizeType.ID_CARD)')
    expect(source).toContain('@SensitiveField(resourceKey = "personnel.user")')
  })

  it.each(['Errors', 'CustomError'])('%s 只去字典标记，保留原异常接口及 getter', (name) => {
    const source = code(`common/exception/${name}.java`)
    expect(source).not.toContain('IDictionary')
    expect(source).toContain('implements IException<')
    expect(source).toContain('int getKey()')
    expect(source).toContain('String getLabel()')
  })
})
