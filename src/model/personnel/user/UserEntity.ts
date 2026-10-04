import type { IPayload, IUser } from '@/framework'
import { BaseEntity } from '@/base/BaseEntity'
import { DesensitizeType as KDesensitizeType, Field as KField, Form as KForm, Search as KSearch, Table as KTable, Type as KType } from '@/framework'
import { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'
import { RoleEntity } from '@/model/personnel/role/RoleEntity'

/**
 * # 用户实体
 *
 * ⚠️ K 侧（`@K*`）与 airpower 侧刻意**不完全对称**，这是迁移中的有意取舍：
 * - 表单暴露 `nickname / email / phone`（与旧 airpower 编辑器逐字段一致）与 `password`
 *   （测试报告 §5-1 后补 —— 管理员新建用户时原先没有任何地方能设定初始密码；
 *   新增必填、编辑留空即"不修改"，校验语义在表单页，见 view/console/personnel/user/list.vue）。
 *   其余字段（`realName` / `idCard` / `gender` / `bio`）依旧保持"代码里有、界面没开口"的原状 ——
 *   换底座不该顺手改业务表单的可写字段；要放开时单独评估（尤其 `idCard` 受列级脱敏与写守卫约束）。
 * - 表格也不给 `idCard` 开列。**注意这里靠的不是 `hide: true`**：`@KTable({ hide: true })`
 *   的语义是「默认隐藏，用户能在列选择器里勾回来」（`KTable` 的 `defaultHiddenColumnKeys`，
 *   由 `framework/__tests__/metadata.spec.ts:62` 钉住），它是**排版**手段，不是权限控制 ——
 *   任何能打开这张表的人都能把默认隐藏的列勾出来。身份证号要么受列级脱敏管、要么根本不进列表，
 *   所以这里直接不声明该列，需要看的时候走详情/权限申请。
 *   （历史注释曾说「`getTableConfigList` 不过滤 `hide`，KTable 也没有列选择器」：后半句早已过时，
 *   前半句是刻意的 —— 过滤动作归 KTable，这样列选择器才列得出可恢复的项。）
 * - 编辑时的"整实体更新"语义不变：`KFormDialog` 编辑态回填的是**服务端详情整对象**，
 *   未在表单里出现的字段会原样带回，不会被写空。
 */
export class UserEntity extends BaseEntity implements IUser, IPayload {
  @KTable({ order: 100, minWidth: 160, force: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 32 })
  @KField({ label: '昵称' })
  @KSearch()
  nickname!: string

  @KTable({ order: 90, minWidth: 150, force: true, copy: true, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ requiredString: true, mobilePhone: true, maxLength: 20 })
  @KField({ label: '手机' })
  @KSearch()
  phone!: string

  @KTable({ order: 80, minWidth: 200, force: true, copy: true, desensitize: KDesensitizeType.EMAIL })
  @KForm({ requiredString: true, email: true, maxLength: 64 })
  @KField({ label: '邮箱' })
  @KSearch()
  email!: string

  realName!: string

  /**
   * 身份证号。
   *
   * mask-exempt: 该字段目前没有任何展示位 —— 列表不开列、详情与表单也不渲染，
   * 因此不声明 `desensitize`。**要在界面上加回它之前，必须先补脱敏声明**
   * （这是写在 maskContract 门禁里的硬约束：敏感字段要么声明脱敏、要么写明豁免理由）。
   */
  idCard!: string

  avatar!: string

  /**
   * 密码（只写字段）。
   *
   * 表单开口见文件头。校验分工：长度上下限走元数据；「新增必填 / 编辑留空=不修改」属于页面语义，
   * 在 list.vue（passwordRules + beforeSubmit）。服务端 `@JsonProperty(access = WRITE_ONLY)`
   * 使详情接口永不回传密码，表单永远从空开始 —— 这正是「编辑留空 = 未修改」成立的前提；
   * 落库前 UserService.beforeAppSaveToDatabase 对有文本的 password 统一加盐散列。
   */
  @KForm({ password: true, minLength: 6, maxLength: 32 })
  @KField({ label: '密码' })
  password!: string

  gender!: number

  @KTable({
    order: -98,
    width: 100,
    /** 与角色页同一口径：状态列说「启用中 / 已禁用」，与行操作按钮（基于 `isDisabled`）一致 */
    formatter: (row: never) => ((row as { isDisabled?: boolean }).isDisabled ? '已禁用' : '启用中'),
  })
  @KField({ label: '状态' })
  declare isDisabled: boolean

  code!: string

  appKey!: string

  oldPassword!: string

  bio!: string

  departmentId?: number

  @KType(RoleEntity)
  roleList!: RoleEntity[]

  @KType(DepartmentEntity)
  departmentList!: DepartmentEntity[]

  getPayloadLabel(): string {
    return this.nickname
  }
}
