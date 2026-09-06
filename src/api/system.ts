import type {
  CodeRuleEntity,
  CodeRuleField,
  CodeRuleParam,
  CodeRuleQuery,
  ConfigEntity,
  ConfigQuery,
  DataFieldPolicyEntity,
  DataFieldPolicyQuery,
  MaskPolicy,
  MenuEntity,
  MenuQuery,
  PermissionEntity,
  PermissionQuery,
  UnitEntity,
  UnitQuery,
} from './system/types'
import type { ChangePasswordRequest, RefreshTokenRequest, RefreshTokenResponse, UserEntity } from './types'
import { request } from './axios'

export const authApi = {
  login(data: { email: string, password: string }): Promise<string> {
    return request.post('/user/login', data)
  },
  loginViaEmail(data: { email: string, code: string }): Promise<string> {
    return request.post('/user/loginViaEmail', data)
  },
  loginViaPhone(data: { phone: string, code: string }): Promise<string> {
    return request.post('/user/loginViaPhone', data)
  },
  sendEmailCode(email: string): Promise<void> {
    return request.post('/user/sendEmail', { email })
  },
  sendSmsCode(phone: string): Promise<void> {
    return request.post('/user/sendSms', { phone })
  },
  logout(): Promise<void> {
    return request.post('/user/logout')
  },
  refreshToken(data: RefreshTokenRequest): Promise<RefreshTokenResponse> {
    return request.post('/user/refresh', data)
  },
  getCurrentUser(): Promise<UserEntity> {
    return request.post('/user/getMyInfo')
  },
  changePassword(data: ChangePasswordRequest): Promise<void> {
    return request.post('/user/updateMyPassword', data)
  },
  getCaptcha(): Promise<{ captchaId: string, image: string }> {
    return request.get('/user/captcha')
  },
}

// ──────────────────────────────────────────
// 菜单 (Menu)
// ──────────────────────────────────────────

export const menuApi = {
  getPage(params: MenuQuery): Promise<{ list: MenuEntity[], total: number }> {
    return request.post('/menu/getPage', params)
  },

  getTree(): Promise<MenuEntity[]> {
    return request.post('/menu/getTree')
  },

  getDetail(id: number): Promise<MenuEntity> {
    return request.post('/menu/getDetail', { id })
  },

  add(data: Partial<MenuEntity>): Promise<MenuEntity> {
    return request.post('/menu/add', data)
  },

  update(data: Partial<MenuEntity>): Promise<MenuEntity> {
    return request.post('/menu/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/menu/delete', { id })
  },
}

// ──────────────────────────────────────────
// 权限 (Permission)
// ──────────────────────────────────────────

export const permissionApi = {
  getPage(params: PermissionQuery): Promise<{ list: PermissionEntity[], total: number }> {
    return request.post('/permission/getPage', params)
  },

  getTree(): Promise<PermissionEntity[]> {
    return request.post('/permission/getTree')
  },

  getDetail(id: number): Promise<PermissionEntity> {
    return request.post('/permission/getDetail', { id })
  },

  add(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.post('/permission/add', data)
  },

  update(data: Partial<PermissionEntity>): Promise<PermissionEntity> {
    return request.post('/permission/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/permission/delete', { id })
  },
}

// ──────────────────────────────────────────
// 计量单位 (Unit)
// ──────────────────────────────────────────

export const unitApi = {
  getPage(params: UnitQuery): Promise<{ list: UnitEntity[], total: number }> {
    return request.post('/unit/getPage', params)
  },

  getDetail(id: number): Promise<UnitEntity> {
    return request.post('/unit/getDetail', { id })
  },

  add(data: Partial<UnitEntity>): Promise<UnitEntity> {
    return request.post('/unit/add', data)
  },

  update(data: Partial<UnitEntity>): Promise<UnitEntity> {
    return request.post('/unit/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/unit/delete', { id })
  },
}

// ──────────────────────────────────────────
// 参数配置 (Config)
// ──────────────────────────────────────────

export const configApi = {
  getPage(params: ConfigQuery): Promise<{ list: ConfigEntity[], total: number }> {
    return request.post('/config/getPage', params)
  },

  getDetail(id: number): Promise<ConfigEntity> {
    return request.post('/config/getDetail', { id })
  },

  add(data: Partial<ConfigEntity>): Promise<ConfigEntity> {
    return request.post('/config/add', data)
  },

  update(data: Partial<ConfigEntity>): Promise<ConfigEntity> {
    return request.post('/config/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/config/delete', { id })
  },
}

// ──────────────────────────────────────────
// 编码规则 (CodeRule)
// ──────────────────────────────────────────

export const codeRuleApi = {
  getPage(params: CodeRuleQuery): Promise<{ list: CodeRuleEntity[], total: number }> {
    return request.post('/coderule/getPage', params)
  },

  getDetail(id: number): Promise<CodeRuleEntity> {
    return request.post('/coderule/getDetail', { id })
  },

  add(data: Partial<CodeRuleEntity>): Promise<CodeRuleEntity> {
    return request.post('/coderule/add', data)
  },

  update(data: Partial<CodeRuleEntity>): Promise<CodeRuleEntity> {
    return request.post('/coderule/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/coderule/delete', { id })
  },

  getFieldList(): Promise<CodeRuleField[]> {
    return request.post('/coderule/getFieldList')
  },

  getParamList(): Promise<CodeRuleParam[]> {
    return request.post('/coderule/getParamList')
  },
}

// ──────────────────────────────────────────
// 数据字段权限策略 (DataFieldPolicy · 列级脱敏)
// ──────────────────────────────────────────

export const dataFieldPolicyApi = {
  /**
   * 获取「当前登录用户」在指定资源字段上生效的脱敏策略。
   *
   * 角色取自服务端权限上下文，**不接受前端传入 roleCode**，
   * 因此不能通过手工构造参数绕过脱敏。
   *
   * @param resourceKey 数据资源标识，如 `channel.salePrice`
   * @param fieldName   字段名，如 `price`
   * @returns 生效策略；`null` 表示该字段对当前用户无需脱敏
   */
  getMyMask(resourceKey: string, fieldName: string): Promise<MaskPolicy | null> {
    return request.get('/dataFieldPolicy/my-mask', { params: { resourceKey, fieldName } })
  },

  /**
   * 一次取回「当前用户在某资源全部字段」上的脱敏策略。
   *
   * 编辑表单需要知道哪些字段受限（8 个字段的表单逐字段查就是 8 个请求），
   * 列表页只关心 1~2 个列时可以继续用 `getMyMask`。
   *
   * @returns 字段名 → 生效策略；无策略的字段不会出现在返回对象里
   */
  getMyMaskMap(resourceKey: string): Promise<Record<string, MaskPolicy>> {
    return request.get('/dataFieldPolicy/my-mask-map', { params: { resourceKey } })
  },

  /**
   * 按资源 / 字段 / 角色查询策略列表（管理端与联调用）
   */
  getByResourceFieldRole(resourceKey: string, fieldName: string, roleCode?: string): Promise<DataFieldPolicyEntity[]> {
    return request.get('/dataFieldPolicy/by-resource-field-role', { params: { resourceKey, fieldName, roleCode } })
  },

  getPage(params: DataFieldPolicyQuery): Promise<{ list: DataFieldPolicyEntity[], total: number }> {
    return request.post('/dataFieldPolicy/getPage', params)
  },

  getDetail(id: number): Promise<DataFieldPolicyEntity> {
    return request.post('/dataFieldPolicy/getDetail', { id })
  },

  add(data: Partial<DataFieldPolicyEntity>): Promise<DataFieldPolicyEntity> {
    return request.post('/dataFieldPolicy/add', data)
  },

  update(data: Partial<DataFieldPolicyEntity>): Promise<DataFieldPolicyEntity> {
    return request.post('/dataFieldPolicy/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/dataFieldPolicy/delete', { id })
  },
}
