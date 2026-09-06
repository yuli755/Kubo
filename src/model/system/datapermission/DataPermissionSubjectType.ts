import { WebColor, WebEnum } from '@airpower/web'

/**
 * # 数据权限主体类型
 *
 * key 直接用后端 `DataPermissionSubjectType.name()`（`data_policy.subject_type` 存的是
 * 枚举名字符串，不是序号），所以这里必须是 `WebEnum<string>`：选中所提交的就是后端读到的值。
 *
 * **`FACTORY` 标记为 disabled**：没有 `user_factory` 关系表，`PermissionContext.factoryIds()`
 * 恒为空集合，后端 `DimensionGuard` 保存时会直接拒绝。界面上灰掉而不是删掉，
 * 是为了让人看到"这一维存在但尚未落地"，而不是对着一个消失的选项猜。
 */
export class DataPermissionSubjectType extends WebEnum<string> {
  static readonly TENANT = new DataPermissionSubjectType('TENANT', '租户')
    .setColor(WebColor.NORMAL)

  static readonly USER = new DataPermissionSubjectType('USER', '用户（仅创建人可见）')
    .setColor(WebColor.SUCCESS)

  static readonly DEPARTMENT = new DataPermissionSubjectType('DEPARTMENT', '部门')
    .setColor(WebColor.WARNING)

  static readonly FACTORY = new DataPermissionSubjectType('FACTORY', '工厂（未启用，保存会被拒绝）')
    .setColor(WebColor.DANGER)
    .setDisabled(true)

  static readonly WAREHOUSE = new DataPermissionSubjectType('WAREHOUSE', '仓库')
    .setColor(WebColor.NORMAL)

  static readonly ROLE = new DataPermissionSubjectType('ROLE', '角色')
    .setColor(WebColor.NORMAL)

  static readonly SYSTEM = new DataPermissionSubjectType('SYSTEM', '系统（放行全部）')
    .setColor(WebColor.NORMAL)
}
