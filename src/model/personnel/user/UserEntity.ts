import type { IPayload, IUser } from '@airpower/web'
import { DesensitizeType, Field, Form, Search, Table, Type } from '@airpower/web'
import { BaseEntity } from '@/base/BaseEntity'
import { DesensitizeType as KDesensitizeType, Field as KField, Form as KForm, Search as KSearch, Table as KTable, Type as KType } from '@/framework'
import { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'
import { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { UserGenderEnum } from '@/model/personnel/user/UserGenderEnum'

/**
 * # 用户实体
 *
 * ⚠️ K 侧（`@K*`）与 airpower 侧刻意**不完全对称**，这是迁移中的有意取舍：
 * - 表单只暴露 `nickname / email / phone`（与旧 airpower 编辑器逐字段一致），
 *   其余字段（`realName` / `idCard` / `gender` / `bio`）保持"代码里有、界面没开口"的原状 ——
 *   换底座不该顺手改业务表单的可写字段；要放开时单独评估（尤其 `idCard` 受列级脱敏与写守卫约束）。
 * - 表格也不给 `idCard` 开列：airpower 的 `hide: true` + 列选择器在新底座里没有对应物
 *   （`getTableConfigList` 不过滤 `hide`，KTable 也没有列选择器），
 *   与其把身份证号硬塞进列表，不如让框架层直接不声明它 —— 需要看的时候走详情/权限申请。
 * - 编辑时的"整实体更新"语义不变：`KFormDialog` 编辑态回填的是**服务端详情整对象**，
 *   未在表单里出现的字段会原样带回，不会被写空。
 */
export class UserEntity extends BaseEntity implements IUser, IPayload {
  @Form({
    requiredString: true,
  })
  @Table({
    force: true,
  })
  @Search()
  @Field({
    label: '昵称',
  })
  @KTable({ order: 100, minWidth: 160, force: true, sortable: true })
  @KForm({ requiredString: true, maxLength: 32 })
  @KField({ label: '昵称' })
  @KSearch()
  nickname!: string

  @Form({
    mobilePhone: true,
    requiredString: true,
    defaultValue: '13888888888',
  })
  @Table({
    force: true,
    phone: true,
    copy: true,
    desensitize: DesensitizeType.MOBILE,
  })
  @Search()
  @Field({
    label: '手机',
  })
  @KTable({ order: 90, minWidth: 150, force: true, copy: true, phone: true, desensitize: KDesensitizeType.MOBILE })
  @KForm({ requiredString: true, mobilePhone: true, maxLength: 20 })
  @KField({ label: '手机' })
  @KSearch()
  phone!: string

  @Form({
    email: true,
    requiredString: true,
  })
  @Table({
    force: true,
    copy: true,
    desensitize: DesensitizeType.EMAIL,
  })
  @Search()
  @Field({
    label: '邮箱',
  })
  @KTable({ order: 80, minWidth: 200, force: true, copy: true, desensitize: KDesensitizeType.EMAIL })
  @KForm({ requiredString: true, email: true, maxLength: 64 })
  @KField({ label: '邮箱' })
  @KSearch()
  email!: string

  @Form({
    mobilePhone: true,
    requiredString: true,
    defaultValue: '13888888888',
  })
  @Table({
    copy: true,
    width: 100,
    hide: true,
  })
  @Search()
  @Field({
    label: '姓名',
  })
  realName!: string

  @Form({})
  @Table({
    hide: true,
    copy: true,
    width: 200,
    // 列表页对所有人遮蔽（含列选择器打开后）；详情表单不脱敏，
    // 由后端 @SensitiveField / my-mask 策略决定谁能看明文
    desensitize: DesensitizeType.ID_CARD,
  })
  @Search()
  @Field({
    label: '身份证号',
  })
  idCard!: string

  @Field({
    label: '头像',
  })
  avatar!: string

  @Form({
    password: true,
  })
  @Field({
    label: '密码',
  })
  password!: string

  @Field({
    label: '性别',
    dictionary: UserGenderEnum,
  })
  @Form({
    defaultValue: UserGenderEnum.FEMALE.key,
    clearable: false,
    radio: true,
  })
  gender!: number

  @Table({
    removed: false,
  })
  @Search({
    hide: false,
  })
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

  @Form({
    textarea: true,
    maxLength: 100,
  })
  @Field({
    label: '个人签名',
  })
  bio!: string

  departmentId?: number

  @Type(RoleEntity, true)
  @KType(RoleEntity)
  roleList!: RoleEntity[]

  @Type(DepartmentEntity, true)
  @KType(DepartmentEntity)
  departmentList!: DepartmentEntity[]

  getPayloadLabel(): string {
    return this.nickname
  }
}
