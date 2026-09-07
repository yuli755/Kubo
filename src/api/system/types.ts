/**
 * # System 模块 API 类型定义
 */

import type { MaskType } from '@kubo/shared'

// ──────────────────────────────────────────
// 菜单 (Menu) — 树形实体
// ──────────────────────────────────────────

export interface MenuEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  path: string
  icon: string
  component: string
  orderNo: number
  parentId: number
  children?: MenuEntity[]
  parent?: MenuEntity
  permission?: string
}

export interface MenuQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, path?: string, component?: string }
}

// ──────────────────────────────────────────
// 权限 (Permission) — 树形实体
// ──────────────────────────────────────────

export interface PermissionEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  identity: string
  type: number
  isSystem: boolean
  parentId: number
  children?: PermissionEntity[]
  parent?: PermissionEntity
}

export interface PermissionQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, identity?: string, type?: number }
}

// ──────────────────────────────────────────
// 计量单位 (Unit)
// ──────────────────────────────────────────

export interface UnitEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  code: string
}

export interface UnitQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string }
}

// ──────────────────────────────────────────
// 参数配置 (Config)
// ──────────────────────────────────────────

export interface ConfigEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  name: string
  description: string
  flag: string
  type: number
  isSystem: boolean
  config: string
}

export interface ConfigQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, flag?: string, type?: number }
}

// ──────────────────────────────────────────
// 编码规则 (CodeRule)
// ──────────────────────────────────────────

export interface CodeRuleEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean

  ruleField: number
  prefix: string
  snLength: number
  template: string
  snType: number
  nextCode: string
  currentSn: number
}

export interface CodeRuleQuery {
  page?: number
  pageSize?: number
  filter?: { template?: string, snType?: number }
}

export interface CodeRuleField {
  defaultPrefix: string
  label: string
  key: number
}

export interface CodeRuleParam {
  label: string
  value: number
  demo: string
  desc: string
}
// ──────────────────────────────────────────
// 数据字段权限策略（DataFieldPolicy · 列级脱敏）
// ──────────────────────────────────────────

/**
 * 后端脱敏类型与策略类型由 @kubo/shared 承载，这里仅做再导出，
 * 保持 `@/api/system/types` 的历史导入路径可用。
 */
export type { MaskPolicy, MaskType } from '@kubo/shared'

/**
 * ### 列级脱敏字段策略实体
 *
 * 与后端 `cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity` 对齐。
 * `my-mask` 端点在「当前用户无需脱敏」时返回 `null`。
 */
export interface DataFieldPolicyEntity {
  id: number
  /** 关联的数据资源 ID */
  resourceId: number
  /** 字段名（如 price / telephone） */
  fieldName: string
  /** 角色编码；`default` 为通配兜底 */
  roleCode: string
  /** 动作名（QUERY / EXPORT 等） */
  actionName: string
  /** 脱敏类型 */
  maskType: MaskType
  /** REPLACE 类型的替换表达式 */
  maskExpression?: string
  /** 是否允许明文 */
  allowPlaintext: boolean
  /** 策略优先级，越大越优先 */
  priority: number
  /** 是否启用 */
  enabled: boolean
}

export interface DataFieldPolicyQuery {
  page?: number
  pageSize?: number
  filter?: { fieldName?: string, roleCode?: string, actionName?: string, enabled?: boolean }
}

/**
 * 一条"导出明文授权"缺口：某角色在某字段上被遮蔽，却没有配套的 EXPORT 明文授权。
 *
 * 结论由后端算（`FieldPolicyGapAnalyzer`），前端只渲染：判据有两处实现就已经是隐患，
 * 三处必然漂移，而漂移的表现是"面板说没问题、用户点导出被拒"。
 *
 * 两个资源定位字段都是后端带的：策略编辑器手上只有 `resourceId`（表单就提交这个字段），
 * 若响应只给 resourceKey，前端就得再查一次"id → key"，多一次请求、多一处会写歪的映射。
 */
export interface ExportGapItem {
  /** 数据资源 ID，与 `DataFieldPolicyEntity.resourceId` 同域，可直接比对 */
  resourceId: number
  /** 数据资源标识（给人看的） */
  resourceKey: string
  /** 被遮蔽的字段名 */
  fieldName: string
  /** 生效角色；`default` 表示对所有角色兜底 */
  roleCode: string
  /** 人话说明（后端给，便于与 guard 的拒因保持同一措辞） */
  reason: string
}

/**
 * 一条"没人读"的列级策略：动作不在列级遮蔽的消费集里，保存成功也不会改变任何输出。
 *
 * 判定在后端（`FieldPolicyGapAnalyzer.inertPolicies` + 后端常量给出的消费集），
 * 前端只负责列出来 —— 在视图里再写一遍"哪些动作算被读"，就是第二套真相。
 */
export interface InertPolicyItem {
  /** 策略行主键（盘点只报告，不代替人删） */
  policyId: number
  /** 数据资源 ID */
  resourceId: number
  /** 数据资源标识 */
  resourceKey: string
  /** 字段名 */
  fieldName: string
  /** 角色编码 */
  roleCode: string
  /** 动作名，原样回显（可能正是拼错的那个值；未填时后端给「(未填)」） */
  actionName: string
}

/**
 * 一条"启用着但永远不会命中"的行级策略。
 *
 * 类别与原因都由后端给（`RowPolicyRiskAnalyzer` 的判据直接来自取数与主体匹配那两处代码）：
 * 前端若自己判"哪个动作算合法"，就多了一份会过期的消费集。
 */
/**
 * 一条"这一档维度在该资源上没有列可绑"的结论。
 *
 * 判据来自 `DimensionBindingAnalyzer`（对照 `DataPermissionSpecification` 里逐个维度拼谓词的那几行）：
 * 类别与原因都由后端给，前端只渲染 —— 前端一旦自己判"哪个维度该有哪一列"，
 * 实体改动就会让面板开始说谎。
 */
export interface DimensionBindingRiskItem {
  /** 数据资源 ID */
  resourceId: number
  /** 数据资源标识 */
  resourceKey: string
  /** 受影响的维度；整表没有映射时为 null */
  subjectType: string | null
  /** 风险类别，原样回显 */
  kind: string
  /** 人话说明（含建议写法） */
  detail: string
}

export interface RowPolicyRiskItem {
  /** 策略行主键 */
  policyId: number
  /** 数据资源 ID */
  resourceId: number
  /** 数据资源标识 */
  resourceKey: string
  /** 策略编码（人认这个，不认 ID） */
  policyCode: string
  /** 风险类别，原样回显 */
  kind: string
  /** 人话说明 */
  detail: string
}

/**
 * 一条「策略指向缺失 / 未启用资源」的结论。
 *
 * 与 `RowPolicyRiskItem` 互补：那份是「策略自身写错」，这份是「资源引用坏掉」。
 * `resourceId` 是策略引用的（可能失效的）资源 ID，孤儿 / 空引用时它不指向任何现存的资源行，
 * 因此这里**没有** `resourceKey`（资源已删除时拿不到），前端不要拿它去 join 资源列表。
 */
export interface PolicyResourceRiskItem {
  /** 策略行主键 */
  policyId: number
  /** 策略编码（人认这个，不认 ID） */
  policyCode: string
  /** 策略引用的资源 ID；空引用时为 null */
  resourceId: number | null
  /** 风险类别（DANGLING_RESOURCE / RESOURCE_NOT_ENABLED），原样回显 */
  kind: string
  /** 人话说明（含删除 / 补登记 / 启用资源的建议） */
  detail: string
}

/**
 * 一个资源「此刻实际是什么档」
 *
 * `effectiveMode` 是后端 `DataPermissionProperties#effectiveMode` 的结果，
 * 前端<em>不参与</em>判定（名单规则被抄第二遍，面板就会开始骗人）。
 */
export interface ResourceRuntimeStatus {
  /** 资源 ID */
  resourceId: number
  /** 资源标识（人认这个；资源表没有「名称」列） */
  resourceKey: string
  /** 资源启用状态（ENABLED / DRAFT / ...） */
  resourceStatus: string
  /** OFF | SHADOW | ENFORCE，原样回显 */
  effectiveMode: string
  /** 是否出现在 enforce-resources 名单里（只解释原因） */
  inEnforceList: boolean
  /** 写侧行级保护此刻是否会真的去问探针 */
  rowWriteGuarded: boolean
  /** 启用中的 QUERY 行级策略数 */
  queryPolicyCount: number
  /** 启用中的 UPDATE + DELETE 行级策略数 */
  writePolicyCount: number
  /** 启用中的 EXPORT 行级策略数 */
  exportPolicyCount: number
  /** 启用中的列级策略数 */
  fieldPolicyCount: number
  /** 维度可绑定性风险条数 */
  bindingRiskCount: number
  /** 是否已登记 Mapper 探针；false 表示本资源尚未进入换轨范围 */
  ormParityRequired: boolean
  /** 对账表是否可读；false 时不能把「没有样本」显示成「没有差异」 */
  ormParityTableAvailable: boolean
  /** 两套 ORM 行数一致的样本数 */
  ormParityConsistentCount: number
  /** Mapper 少放行的样本数（不泄露，但需留痕） */
  ormParityStricterCount: number
  /** Mapper 多放行的样本数（泄露方向） */
  ormParityLooserCount: number
  /** 无法完成对账的样本数 */
  ormParityErrorCount: number
  /** 后端算好的人话提示（配置建议） */
  notes: string[]
}

/**
 * 放开前置检查里的单项（判据全在后端，这里只是回显）
 */
export interface EnforceReadinessCheck {
  /** 稳定标识，前端样式/测试认它，不认文案 */
  code: string
  /** 人话标题 */
  title: string
  /** 是否通过 */
  passed: boolean
  /** 未通过时是否阻塞放开：false 表示只是提醒，不影响 ready */
  blocking: boolean
  /** 结论说明（含下一步） */
  detail: string
}

/**
 * 一个资源的"现在能不能进 enforce 名单"结论
 */
export interface EnforceReadiness {
  /** 资源标识 */
  resourceKey: string
  /** 此刻生效档位（OFF | SHADOW | ENFORCE） */
  effectiveMode: string
  /** 所有阻塞项都通过（后端算，前端不得再算一遍） */
  ready: boolean
  /** 未通过的阻塞项，可直接显示 */
  blockers: string[]
  /** 全部检查项（含提醒项） */
  checks: EnforceReadinessCheck[]
}

/**
 * 数据权限灰度状态总览（只读面板用）
 */
export interface DataPermissionRuntimeStatus {
  /** 全局模式：off | shadow | enforce */
  globalMode: string
  /** enforce-resources 名单（顺序与配置一致） */
  enforceResources: string[]
  /** 未登记资源是否默认拒绝 */
  denyUnregistered: boolean
  /** 写侧行级保护总开关 */
  rowWriteEnabled: boolean
  /** 部门层级闭包是否展开 */
  hierarchyDepartmentEnabled: boolean
  /** 派生出了资源键、但库里没有登记的服务键 */
  unmanagedServiceKeys: string[]
  /** 各资源状态 */
  resources: ResourceRuntimeStatus[]
}

/**
 * 一条数据权限拒绝事件（`data_permission_deny_log`）。
 *
 * 语义是「拒绝事件」，不是「策略命中审计」：本仓策略是过滤条件而非 allow/deny 规则，
 * 因此没有策略 ID 字段，只有资源、原因、档位、主体与明细 —— 前端不要把它渲染成
 * 「被某条策略命中」的列表。
 */
export interface DenyLogItem {
  /** 事件主键 */
  id: number
  /** 发生时间（毫秒时间戳，来自框架基类 create_time） */
  createTime: number
  /** 数据资源标识 */
  resourceKey: string
  /** 拒绝原因（unregistered-resource / write-without-policy / row-not-writable / export-bypass …） */
  reason: string
  /** 拒绝发生时该资源的生效档位：OFF / SHADOW / ENFORCE */
  mode: string
  /** 被拒主体用户；无上下文（内部调用）时为 null */
  userId: number | null
  /** 被拒主体租户；无上下文时为 null */
  tenantId: number | null
  /** 运维明细（ID、字段名、动作等）；可为 null */
  detail: string | null
}
