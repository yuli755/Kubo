import { WebColor, WebEnum } from '@airpower/web'

/**
 * # 数据权限主体类型
 *
 * key 直接用后端 `DataPermissionSubjectType.name()`（`data_policy.subject_type` 存的是
 * 枚举名字符串，不是序号），所以这里必须是 `WebEnum<string>`：选中所提交的就是后端读到的值。
 *
 * **`FACTORY` 已落地（D13-B）**：`user_factory` 关系表已建，登录/刷新会把工厂节点连同整棵
 * 仓库子树展开进 `factoryIds`，后端 `DimensionGuard` 不再拒绝保存。策略列绑定走
 * `FACTORY:storage.id` 关联路径（如 `wms.inventory` / `wms.move`），所以这里按可用维度放开。
 */
export class DataPermissionSubjectType extends WebEnum<string> {
  static readonly TENANT = new DataPermissionSubjectType('TENANT', '租户')
    .setColor(WebColor.NORMAL)

  static readonly USER = new DataPermissionSubjectType('USER', '用户（仅创建人可见）')
    .setColor(WebColor.SUCCESS)

  static readonly DEPARTMENT = new DataPermissionSubjectType('DEPARTMENT', '部门')
    .setColor(WebColor.WARNING)

  static readonly FACTORY = new DataPermissionSubjectType('FACTORY', '工厂')
    .setColor(WebColor.WARNING)

  static readonly WAREHOUSE = new DataPermissionSubjectType('WAREHOUSE', '仓库')
    .setColor(WebColor.NORMAL)

  static readonly ROLE = new DataPermissionSubjectType('ROLE', '角色')
    .setColor(WebColor.NORMAL)

  static readonly SYSTEM = new DataPermissionSubjectType('SYSTEM', '系统（放行全部）')
    .setColor(WebColor.NORMAL)
}
