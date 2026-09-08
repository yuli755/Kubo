import type {
  CodeRuleEntity,
  CodeRuleField,
  CodeRuleParam,
  CodeRuleQuery,
  ConfigEntity,
  ConfigQuery,
  DataFieldPolicyEntity,
  DataFieldPolicyQuery,
  DataPermissionRuntimeStatus,
  DenyLogItem,
  DimensionBindingRiskItem,
  EnforceReadiness,
  ExportGapItem,
  InertPolicyItem,
  MaskPolicy,
  MenuEntity,
  MenuQuery,
  OutboxDeadItem,
  PermissionEntity,
  PermissionQuery,
  PolicyResourceRiskItem,
  RowPolicyRiskItem,
  UnitEntity,
  UnitQuery,
} from './system/types'
import type { ChangePasswordRequest, RefreshTokenRequest, RefreshTokenResponse, TenantOption, UserEntity } from './types'
import { api, request } from './axios'

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
  getMyTenantList(): Promise<TenantOption[]> {
    return api.post('/user/getMyTenantList', undefined, { tenantAware: false })
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

export const dataResourceApi = {
  /**
   * 「导出明文授权」缺口自查：配了 QUERY 遮蔽、没配 EXPORT 明文的字段清单。
   *
   * 挂在 `dataResource`（管理端点）而不是 `dataFieldPolicy`（业务用户也会调）之下：
   * 这份数据是跨角色的策略地图，普通用户读到它就能推断"哪个角色的哪个字段有明文"。
   *
   * @returns 缺口条目列表（每条自带资源 ID / 资源键），只含有缺口的资源
   */
  getExportGaps(): Promise<ExportGapItem[]> {
    return request.get('/dataResource/export-gaps')
  },

  /**
   * 逐资源放开的前置检查：「现在能不能把这个资源写进 `enforce-resources`」。
   *
   * 名单少配一项 = 防护形同虚设，多配一项 = 保存报错或列表查空，两种错都要到放开之后才暴露，
   * 所以放开前先看这份报告。它只报数、不新增拦阻（放开与否仍是配置动作）。
   *
   * 与 `getExportGaps` 同挂 `dataResource`（跨资源的策略地图，不进业务用户也会调的策略接口）。
   *
   * @param resourceKey 省略则返回全部已登记资源
   * @returns 单资源时是一条记录，全部时是数组
   */
  getEnforceReadiness(resourceKey?: string): Promise<EnforceReadiness | EnforceReadiness[]> {
    return request.get('/dataResource/enforce-readiness', resourceKey ? { params: { resourceKey } } : undefined)
  },

  /**
   * 「没人读」的列级策略盘点（存量）
   *
   * 与 `getExportGaps` 同一个管理端点组：返回的都是跨角色的策略元数据，
   * 因此都挂在 `dataResource` 下继承其权限校验，不放 `dataFieldPolicy`（业务用户也会调）。
   *
   * @returns 条目列表；没有存量问题时为空数组
   */
  getInertPolicies(): Promise<InertPolicyItem[]> {
    return request.get('/dataResource/inert-policies')
  },

  /**
   * 行级策略"永不命中"盘点（安全侧：这类行显示"已启用"，实际不参与任何请求）
   *
   * @returns 风险条目列表；无问题时为空数组
   */
  getRowPolicyRisks(): Promise<RowPolicyRiskItem[]> {
    return request.get('/dataResource/row-policy-risks')
  },

  /**
   * 「策略指向缺失 / 未启用资源」盘点（安全侧：这类行显示\"已启用\"，实际运行期永远轮不到它）
   *
   * 与 `getRowPolicyRisks` 互补：那份按资源遍历查「策略自身写错」，这份反过来按策略遍历
   * 查「资源引用坏掉」（资源已删 / 未启用）。同挂 `dataResource` 管理端点。
   *
   * @returns 风险条目列表；无问题时为空数组
   */
  getPolicyResourceRisks(): Promise<PolicyResourceRiskItem[]> {
    return request.get('/dataResource/policy-resource-risks')
  },

  /**
   * 维度可绑定性盘点（安全侧：策略读得到、列却绑不上，enforce 下等于全员查不到）
   *
   * @returns 风险条目列表；无问题时为空数组
   */
  getDimensionBindingRisks(): Promise<DimensionBindingRiskItem[]> {
    return request.get('/dataResource/dimension-binding-risks')
  },

  /**
   * 数据权限灰度状态总览：当前配置 + 每个资源此刻实际生效的档位
   *
   * 档位（OFF / SHADOW / ENFORCE）由后端 `DataPermissionProperties#effectiveMode` 给出，
   * 前端只原样显示 —— 名单的三态规则（enforce 时名单是「例外清单」、shadow 时是「提前放开清单」）
   * 只能有一份，抄第二遍就会让面板开始骗人，而放开决策正是照着它做的。
   *
   * @returns 总览；取不到时由 composable 标记 failed，不显示成「一切正常」
   */
  getRuntimeStatus(): Promise<DataPermissionRuntimeStatus> {
    return request.get('/dataResource/runtime-status')
  },

  /**
   * 最近的拒绝事件（事后追认：谁在何时、因何、在哪个档位下被数据权限拒绝）。
   *
   * 与其余盘点接口同挂 `dataResource`（跨角色的「谁被拒过」，不进业务用户也会调的策略接口）。
   * 取不到表 / 没有记录时后端返回空数组，调用方要按「取不到 ≠ 从没拒绝过」来处理
   * （见 `useDenyLogReport` 的失败取向）。
   *
   * @param resourceKey 省略则返回全量最近记录（最多 50 条，最新在前）
   * @returns 拒绝事件列表；无记录 / 表未建时为空数组
   */
  getDenyLogs(resourceKey?: string): Promise<DenyLogItem[]> {
    return request.get('/dataResource/deny-logs', resourceKey ? { params: { resourceKey } } : undefined)
  },

  /**
   * 权限变更 Outbox 死信列表（P5：连续重投仍失败的变更通知，需人工处置）。
   *
   * 只读，挂在 `dataResource` 管理端点下，后端校验 `system:data-permission:outbox` 身份
   * （root 视为持有）；无权限时 403。前端不判死信语义（什么算 DEAD 在后端），只照实回显。
   *
   * @returns 死信事件（最新在前，最多 100 条）；无死信时为空数组
   */
  getOutboxDead(): Promise<OutboxDeadItem[]> {
    return request.get('/dataResource/outbox-dead')
  },

  /**
   * 重放单条死信（P5 人工补偿）：DEAD → PENDING，交还 Dispatcher 下轮重投。
   * 特权接口，每次重放后端落审计（reason=outbox-replayed）。
   *
   * @param eventKey 事件唯一键
   */
  replayOutbox(eventKey: string): Promise<void> {
    return request.post('/dataResource/outbox-replay', { eventKey })
  },

  /**
   * 重放全部死信（P5 人工补偿，故障恢复后批量续投）。
   *
   * @returns 后端返回重放条数（Json.success 无 data，这里以 void 处理，条数以后端提示为准）
   */
  replayAllOutbox(): Promise<void> {
    return request.post('/dataResource/outbox-replay-all')
  },

  /**
   * 丢弃单条死信（P5 人工补偿：管理员判定事件已无意义，物理删除）。
   * 特权接口，每次丢弃后端落审计（reason=outbox-discarded）。
   *
   * @param eventKey 事件唯一键
   */
  discardOutbox(eventKey: string): Promise<void> {
    return request.post('/dataResource/outbox-discard', { eventKey })
  },
}

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
   * 当前用户的「导出会被怎么处理」预览（受限字段名 → 是否可明文导出）。
   *
   * 结论由后端算（判据与 `ExportBypassGuard` 同源）：前端若自己再实现一遍
   * "EXPORT + allowPlaintext 才算授权"，就会多出第三份真相，
   * 表现是"提示说能导、后端把请求拒了"。
   *
   * 空对象 = 你在这个资源上没有受限字段（或后端还没有权限上下文），**不代表被拒**。
   */
  getMyExportVisibility(resourceKey: string): Promise<Record<string, boolean>> {
    return request.get('/dataFieldPolicy/my-export-visibility', { params: { resourceKey } })
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
