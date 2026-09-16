/**
 * # Demo 数据规格与行生成器（仅演示模式）
 *
 * 用途：在**没有后端**的情况下，为任意资源的 `getPage` / `getDetail` 生成「形状正确、
 * 语义可信、覆盖边界」的假数据，用于前端视觉与交互评审。
 *
 * ## 设计约束
 * 1. **确定性**：同一资源 + 同一行号永远生成同一行（用 `hash(resource#index)` 派生的 PRNG），
 *    刷新页面数据不跳变，走查与截图可复现。
 * 2. **字段名驱动**：表的列由实体 `@Table` 元数据决定，这里生成一个**宽字段集**
 *    （名称/编号/数量/金额/时间/关联名…）并按键名启发式赋值 —— 实体声明了哪些列，哪些列就有值。
 * 3. **枚举返回数字键**：状态类字段返回枚举 key（1/2/3…），airpower 的 `WebEnum` 会据此渲染
 *    带颜色的标签；返回字符串反而渲染成普通文本，看不出状态色语义。
 * 4. **覆盖边界**：每 7 行的备注刻意超长、每 11 行出现极值数量/零库存，用来暴露排版问题。
 *
 * ⚠️ 演示模式专用代码：生产构建中被 `DEMO_ENABLED` 常量消除（见 `src/demo/README.md`
 * 的移除清单与 CI 门禁 `infra/scripts/check-demo-build.mjs`）。
 */

export type Row = Record<string, unknown>

interface ResourceSpec {
  /** 资源中文名 */
  label: string
  /** 单号前缀，如 RK / CK / CG */
  codePrefix: string
  /** 状态枚举 key 池（数字键 → airpower 渲染成带色标签） */
  statusPool?: number[]
  /** 名称池（业务语义更可信） */
  namePool?: string[]
  /** 数值字段范围覆盖 */
  numbers?: Record<string, [number, number]>
  /** 固定字段（覆盖生成值） */
  fixed?: Row
  /** 该资源的总行数（默认 68） */
  total?: number
}

/** 需要生成的基础字段集（键名与后端实体对齐，命名差异见 *_Name 关联列） */
const BASE_FIELDS = [
  'code',
  'name',
  'remark',
  'description',
  'type',
  'spec',
  'model',
  'brand',
  'materialName',
  'customerName',
  'supplierName',
  'storageName',
  'departmentName',
  'roleName',
  'unitName',
  'creatorName',
  'updaterName',
  'nickname',
  'realName',
  'username',
  'phone',
  'email',
  'address',
  'contact',
  'managerName',
  'batchNo',
  'orderNo',
  'planNo',
  'totalQuantity',
  'quantity',
  'count',
  'stock',
  'minStock',
  'maxStock',
  'purchasePrice',
  'salePrice',
  'amount',
  'totalAmount',
  'weight',
  'createTime',
  'updateTime',
  'auditTime',
  'startTime',
  'endTime',
  'plannedStartTime',
  'plannedEndTime',
  'deliveryTime',
  'finishTime',
  'isFinished',
  'enabled',
  'published',
  'isAudited',
  'tenantId',
  'creatorId',
] as const

/** 文本池：按键名片段取用，避免「所有数据都叫测试数据」 */
const TEXT_POOLS: Array<[string, string[]]> = [
  ['materialName', ['6061 铝板', '304 不锈钢管', 'ABS 工程塑料', 'M8 内六角螺栓', '环氧树脂胶', '碳纤维预浸料']],
  ['customerName', ['华东精密制造有限公司', '云港智能装备股份', '天工模具有限公司', '宏远汽车零部件', '启元电子科技']],
  ['supplierName', ['宝钢材料贸易有限公司', '恒信五金供应站', '南方化工原料公司', '快捷物流耗材']],
  ['storageName', ['原料一库', '半成品暂存区', '成品立体库', '危化品专库', '线边周转仓']],
  ['departmentName', ['生产制造部', '仓储物流部', '质量管理部', '设备工程部', '采购中心', '销售中心']],
  ['roleName', ['系统管理员', '生产计划员', '仓库管理员', '质检员', '采购员', '访客']],
  ['unitName', ['件', '千克', '米', '平方米', '升', '套']],
  ['managerName', ['王建国', '李慧敏', '张伟强', '刘思远', '陈晓峰', '周雅琴']],
  ['creatorName', ['林一鸣', '赵敏', '孙嘉禾', '吴桐', '郑海宁', '何雨柔']],
  ['updaterName', ['林一鸣', '赵敏', '孙嘉禾', '吴桐']],
  ['nickname', ['林一鸣', '赵敏', '孙嘉禾', '吴桐', '郑海宁', '何雨柔']],
  ['realName', ['林一鸣', '赵敏', '孙嘉禾', '吴桐', '郑海宁', '何雨柔']],
  ['username', ['linyiming', 'zhaomin', 'sunjiahe', 'wutong', 'zhenghaining']],
  ['contact', ['王建国', '李慧敏', '张伟强', '刘思远', '陈晓峰']],
  ['address', ['江苏省苏州市工业园区星湖街 328 号', '广东省东莞市松山湖科技九路 12 号', '上海市嘉定区安亭镇墨玉路 88 号']],
  ['spec', ['600×1200×2.0', 'Φ32×3.5', 'M8×25', '180×180×18']],
  ['model', ['KB-2200A', 'KB-3300S', 'HX-880', 'GT-1500']],
  ['brand', ['Kubo', '宝钢', '海川', '开元']],
  ['batchNo', ['B20260901', 'B20260907', 'B20260912', 'B20260916']],
  ['orderNo', ['SO202609001', 'SO202609014', 'SO202609027']],
  ['planNo', ['PL202609001', 'PL202609008', 'PL202609015']],
  ['remark', ['按客户加急处理', '需质检二次确认', '供应商已确认交期', '']],
  ['description', ['常规生产任务', '试制批次，注意留样', '返工补充订单', '']],
]

/** 数值池：键名片段 → 取值区间（金额类按「分」给整数，避免浮点噪声） */
const NUMBER_POOLS: Array<[string, [number, number]]> = [
  ['purchasePrice', [800, 26000]],
  ['salePrice', [1200, 38000]],
  ['amount', [100000, 9800000]],
  ['price', [1000, 32000]],
  ['quantity', [1, 480]],
  ['stock', [0, 2400]],
  ['weight', [50, 26000]],
  ['percent', [0, 100]],
  ['rate', [0, 100]],
  ['score', [60, 100]],
  ['count', [1, 96]],
  ['type', [1, 3]],
]

const DATE_FIELDS = new Set([
  'createTime',
  'updateTime',
  'auditTime',
  'startTime',
  'endTime',
  'plannedStartTime',
  'plannedEndTime',
  'deliveryTime',
  'finishTime',
])

const BOOLEAN_FIELDS = new Set(['isFinished', 'enabled', 'published', 'isAudited'])

const LONG_REMARK
  = '这是一段刻意加长的备注：用于验证表格在超长文本下的换行与截断策略是否统一、单元格高度是否被撑开而破坏行距节奏。'

/** 各资源规格：只写「与默认不同」的部分 */
const RESOURCE_SPECS: Record<string, ResourceSpec> = {
  input: {
    label: '入库单',
    codePrefix: 'RK',
    statusPool: [1, 2, 3, 4],
    namePool: ['原材料采购入库', '半成品回库', '成品完工入库', '退货入库', '委外加工入库'],
  },
  output: {
    label: '出库单',
    codePrefix: 'CK',
    statusPool: [1, 2, 3, 4],
    namePool: ['生产领料出库', '成品发货出库', '样品出库', '报废出库'],
  },
  move: {
    label: '移库单',
    codePrefix: 'YK',
    statusPool: [1, 2, 3, 4],
    namePool: ['原料库转线边仓', '成品库位调整', '危化品专区转移'],
  },
  inventory: {
    label: '库存',
    codePrefix: 'KC',
    statusPool: [1, 2, 3],
    namePool: ['6061 铝板', '304 不锈钢管', 'M8 内六角螺栓', '环氧树脂胶'],
  },
  material: {
    label: '物料',
    codePrefix: 'WL',
    statusPool: [1, 2],
    namePool: ['6061 铝板', '304 不锈钢管', 'ABS 工程塑料', 'M8 内六角螺栓', '环氧树脂胶'],
    total: 126,
  },
  contract: {
    label: '合同',
    codePrefix: 'HT',
    statusPool: [1, 2, 3],
    namePool: ['年度框架采购合同', '设备维保服务合同', '物流运输服务合同'],
  },
  customer: {
    label: '客户',
    codePrefix: 'KH',
    statusPool: [1, 2],
    namePool: ['华东精密制造有限公司', '云港智能装备股份', '天工模具有限公司', '宏远汽车零部件'],
  },
  supplier: {
    label: '供应商',
    codePrefix: 'GYS',
    statusPool: [1, 2],
    namePool: ['宝钢材料贸易有限公司', '恒信五金供应站', '南方化工原料公司'],
  },
  purchase: {
    label: '采购单',
    codePrefix: 'CG',
    statusPool: [1, 2, 3, 4],
    namePool: ['9 月原料采购', '辅料补充采购', '设备备件采购'],
  },
  sale: {
    label: '销售单',
    codePrefix: 'XS',
    statusPool: [1, 2, 3, 4],
    namePool: ['华东精密 9 月订单', '云港智能首批交付', '天工模具试制订单'],
  },
  purchasePrice: {
    label: '采购价',
    codePrefix: 'CJ',
    statusPool: [1, 2],
    namePool: ['6061 铝板采购价', '304 不锈钢管采购价', 'M8 螺栓采购价'],
  },
  salePrice: {
    label: '销售价',
    codePrefix: 'SJ',
    statusPool: [1, 2],
    namePool: ['A 型机架销售价', '减速箱壳体销售价', '控制柜销售价'],
  },
  storage: {
    label: '仓库',
    codePrefix: 'WH',
    statusPool: [1, 2],
    namePool: ['原料一库', '半成品暂存区', '成品立体库', '危化品专库'],
  },
  structure: {
    label: '库位结构',
    codePrefix: 'KW',
    statusPool: [1, 2],
    namePool: ['A 区-01-01', 'A 区-01-02', 'B 区-02-07', 'C 区-03-15'],
  },
  user: {
    label: '用户',
    codePrefix: 'U',
    statusPool: [1, 2],
    namePool: ['林一鸣', '赵敏', '孙嘉禾', '吴桐', '郑海宁'],
  },
  role: {
    label: '角色',
    codePrefix: 'R',
    statusPool: [1, 2],
    namePool: ['系统管理员', '生产计划员', '仓库管理员', '质检员', '采购员'],
  },
  department: {
    label: '部门',
    codePrefix: 'D',
    statusPool: [1, 2],
    namePool: ['生产制造部', '仓储物流部', '质量管理部', '设备工程部'],
  },
  permission: {
    label: '权限',
    codePrefix: 'P',
    statusPool: [1, 2],
    namePool: ['用户新增', '用户修改', '入库单审核', '库存导出'],
  },
  menu: {
    label: '菜单',
    codePrefix: 'M',
    statusPool: [1, 2],
    namePool: ['入库单管理', '库存查询', '库存盘点', '数据权限'],
  },
  unit: {
    label: '计量单位',
    codePrefix: 'UN',
    statusPool: [1, 2],
    namePool: ['件', '千克', '米', '平方米', '升'],
  },
  config: {
    label: '系统配置',
    codePrefix: 'CFG',
    statusPool: [1, 2],
    namePool: ['默认租户', '单据编号规则', '库存预警阈值', '登录验证码开关'],
  },
  coderule: {
    label: '编号规则',
    codePrefix: 'CR',
    statusPool: [1, 2],
    namePool: ['入库单编号规则', '出库单编号规则', '移库单编号规则'],
  },
  log: {
    label: '操作日志',
    codePrefix: 'LOG',
    statusPool: [1, 2],
    namePool: ['登录成功', '修改用户信息', '审核入库单', '导出库存'],
    total: 240,
  },
  app: {
    label: '开放应用',
    codePrefix: 'APP',
    statusPool: [1, 2],
    namePool: ['MES 边缘网关', 'WMS 手持终端', 'BI 数据同步'],
  },
  notify: {
    label: '通知',
    codePrefix: 'NT',
    statusPool: [1, 2],
    namePool: ['库存低于安全线', '设备离线告警', '单据审核提醒'],
  },
}

/**
 * 关联对象字段：实体里形如 `material!: MaterialEntity` 的字段，后端返回的是**嵌套对象**
 * （列表列的 formatter 会写 `row.material.code`）。缺了这一层，列表页渲染时就会
 * `Cannot read properties of undefined (reading 'code')` 整页报错 —— 这是演示数据最容易
 * 漏掉的形状问题，因此独立成表统一生成。
 */
const RELATION_FIELDS: Array<[string, string]> = [
  ['material', 'WL'],
  ['supplier', 'GYS'],
  ['customer', 'KH'],
  ['storage', 'WH'],
  ['structure', 'KW'],
  ['unit', 'DW'],
]

const GENERIC_NAMES = ['示例名称一', '示例名称二', '示例名称三', '示例名称四', '示例名称五']

/** 32 位字符串散列（FNV-1a 变体），用于派生稳定种子 */
function hashString(text: string): number {
  let hash = 2166136261
  for (let i = 0; i < text.length; i++) {
    hash ^= text.charCodeAt(i)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

/** mulberry32：小而稳定的 PRNG，同一 seed 必然产出同一序列 */
function createRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6D2B79F5) >>> 0
    let t = state
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function pickFrom<T>(pool: readonly T[], random: () => number): T {
  return pool[Math.floor(random() * pool.length) % pool.length] as T
}

function randomInt([min, max]: [number, number], random: () => number): number {
  return Math.round(min + random() * (max - min))
}

/** 演示数据的时间锚点：固定为「今天」，保证日期列看起来是最近的 */
const ANCHOR_TIME = new Date('2026-09-17T09:30:00+08:00').getTime()

function randomDate(random: () => number, daySpread = 180): string {
  const offset = Math.floor(random() * daySpread) * 86400000 + Math.floor(random() * 43200000)
  return new Date(ANCHOR_TIME - offset).toISOString()
}

function pad(value: number, length: number): string {
  return value.toString().padStart(length, '0')
}

function findPool(key: string): readonly string[] | undefined {
  return TEXT_POOLS.find(([fragment]) => key.toLowerCase().endsWith(fragment.toLowerCase()))?.[1]
}

function findRange(key: string): [number, number] | undefined {
  return NUMBER_POOLS.find(([fragment]) => key.toLowerCase().endsWith(fragment.toLowerCase()))?.[1]
}

function resolveCodePrefix(resourceKey: string): string {
  const spec = RESOURCE_SPECS[resourceKey]
  if (spec) {
    return spec.codePrefix
  }
  // 未知资源：取资源键首段的大写字母，保证单号仍有可辨识前缀
  return resourceKey.replace(/[^a-z]/gi, '').slice(0, 3).toUpperCase() || 'DEMO'
}

/**
 * 生成一行数据。
 *
 * @param resourceKey 资源键（URL 路径最后一段，如 `input`）
 * @param index 行号（1 起）
 */
export function buildRow(resourceKey: string, index: number): Row {
  const spec = RESOURCE_SPECS[resourceKey]
  const random = createRandom(hashString(`${resourceKey}#${index}`))

  const name = spec?.namePool?.length
    ? pickFrom(spec.namePool, random)
    : pickFrom(GENERIC_NAMES, random)

  const statusPool = spec?.statusPool ?? [1, 2]
  const createdAt = randomDate(random)
  const updatedAt = randomDate(random, 90)
  const createdDate = new Date(createdAt)

  // 边界覆盖：每 7 行超长备注，每 11 行极值数量与零库存
  const isLongTextRow = index % 7 === 0
  const isEdgeNumberRow = index % 11 === 0

  const row: Row = {
    id: index,
    name,
    title: name,
    status: pickFrom(statusPool, random),
    code: `${resolveCodePrefix(resourceKey)}${createdDate.getFullYear()}${pad(createdDate.getMonth() + 1, 2)}${pad(1000 + index, 4)}`,
    tenantId: 1,
    creatorId: 1,
    createTime: createdAt,
    updateTime: updatedAt,
    auditTime: new Date(createdDate.getTime() + 3600000).toISOString(),
    startTime: randomDate(random, 30),
    endTime: randomDate(random, 15),
    plannedStartTime: randomDate(random, 20),
    plannedEndTime: randomDate(random, 10),
    deliveryTime: randomDate(random, 45),
    finishTime: randomDate(random, 8),
    isFinished: index % 3 === 0,
    enabled: index % 5 !== 0,
    published: index % 4 !== 0,
    isAudited: index % 2 === 0,
  }

  for (const field of BASE_FIELDS) {
    if (field in row) {
      continue
    }
    if (field === 'remark' && isLongTextRow) {
      row[field] = LONG_REMARK
      continue
    }
    row[field] = generateValue(field, random, index)
  }

  if (isEdgeNumberRow) {
    row.totalQuantity = 999999
    row.amount = 999999999
    row.stock = 0
  }

  if (spec?.numbers) {
    for (const [key, range] of Object.entries(spec.numbers)) {
      row[key] = randomInt(range, random)
    }
  }
  if (spec?.fixed) {
    Object.assign(row, spec.fixed)
  }

  // 关联对象（物料/供应商/客户/仓库/库位/单位）：列表列的 formatter 直接读 `row.x.code|name`
  for (const [relation, prefix] of RELATION_FIELDS) {
    row[relation] = buildRelation(relation, prefix, index, random)
  }
  // 物料的计量单位也是嵌套的（`row.material.unit.name`）
  const material = row.material as Row
  material.unit = buildRelation('unit', 'DW', index, random)

  return row
}

/** 生成一个关联对象：`{ id, code, name }`（名称优先取对应文本池，保持语义可信） */
function buildRelation(relation: string, prefix: string, index: number, random: () => number): Row {
  const poolKey = `${relation}Name`
  const pool = findPool(poolKey)
  const name = pool?.length ? pickFrom(pool, random) : `${relation}-${index}`
  return {
    id: (index % 37) + 1,
    code: `${prefix}${pad(1000 + ((index * 7) % 9000), 4)}`,
    name,
    label: name,
  }
}

/** 按字段名启发式生成值：优先文本池 → 数值池 → 日期 → 布尔 → 兜底 */
function generateValue(field: string, random: () => number, index: number): unknown {
  const pool = findPool(field)
  if (pool?.length) {
    return pickFrom(pool, random)
  }
  if (field === 'phone') {
    return `13${pad(Math.floor(random() * 100000000), 8)}`
  }
  if (field === 'email') {
    return `user${index}@kubo.example.com`
  }
  const range = findRange(field)
  if (range) {
    return randomInt(range, random)
  }
  if (DATE_FIELDS.has(field)) {
    return randomDate(random)
  }
  if (BOOLEAN_FIELDS.has(field)) {
    return index % 3 !== 0
  }
  return ''
}

/** 该资源的总行数（用于分页器演示） */
export function totalOf(resourceKey: string): number {
  return RESOURCE_SPECS[resourceKey]?.total ?? 68
}

/** 资源中文名 */
export function labelOf(resourceKey: string): string {
  return RESOURCE_SPECS[resourceKey]?.label ?? resourceKey
}
