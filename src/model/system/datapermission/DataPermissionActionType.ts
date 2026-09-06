import { WebColor, WebEnum } from '@airpower/web'

/**
 * # 数据权限动作
 *
 * 与后端 `DataPermissionAction` 同名（`data_policy.action_name` /
 * `data_field_policy.action_name` 存字符串）。
 *
 * 注意消费范围：**列级脱敏的读链路目前只解析 QUERY**（`ColumnMaskingService.DEFAULT_ACTION`），
 * 给其它动作配脱敏策略不会报错、也不会生效；行级策略同理按 action 精确匹配。
 * 这点在界面上说明，比让人"配了却查不到原因"便宜得多。
 */
export class DataPermissionActionType extends WebEnum<string> {
  static readonly QUERY = new DataPermissionActionType('QUERY', '列表查询')
    .setColor(WebColor.NORMAL)

  static readonly DETAIL = new DataPermissionActionType('DETAIL', '详情')
    .setColor(WebColor.NORMAL)

  static readonly CREATE = new DataPermissionActionType('CREATE', '新增')
    .setColor(WebColor.SUCCESS)

  static readonly UPDATE = new DataPermissionActionType('UPDATE', '修改')
    .setColor(WebColor.WARNING)

  static readonly DELETE = new DataPermissionActionType('DELETE', '删除')
    .setColor(WebColor.DANGER)

  /**
   * 导出：`data_resource.supported_actions` 里可声明，但导出侧尚未接脱敏
   * （导出走文件流不经过 Jackson），因此这里灰掉，避免"配了以为生效"。
   */
  static readonly EXPORT = new DataPermissionActionType('EXPORT', '导出（脱敏未接线）')
    .setColor(WebColor.NORMAL)
    .setDisabled(true)
}
