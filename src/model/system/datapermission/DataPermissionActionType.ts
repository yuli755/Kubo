import { WebColor, WebEnum } from '@airpower/web'

/**
 * # 数据权限动作
 *
 * 与后端 `DataPermissionAction` 同名（`data_policy.action_name` /
 * `data_field_policy.action_name` 存字符串）。
 *
 * 消费范围（写在这里是为了别让"配了不生效"变成口头传说）：
 *
 * - `QUERY`：列级脱敏的读链路默认动作（`ColumnMaskingService.DEFAULT_ACTION`），界面与详情都走它；
 * - `EXPORT`：由 `ExportBypassGuard`（请求线程上决定"这次导出能不能发生"）与
 *   `ExportColumnMasker`（导出内容层）共同消费 —— 受限角色要导出带某列明文，必须有
 *   `actionName=EXPORT + allowPlaintext=true` 的这条策略，缺一条整个导出被拒；
 * - `DETAIL` / `CREATE` / `UPDATE` / `DELETE`：行级策略按 action 精确匹配；列级脱敏不看这些动作，
 *   给它们配脱敏策略不报错、也不会生效。
 *
 * 枚举成员与后端 `DataPermissionAction` 的一致性由 `src/config/__tests__/maskContract.spec.ts`
 * 对账（它直接读 Java 源码，不靠这里的手抄清单）。
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
   * 导出：受限角色导出某一列明文的**唯一入口**（`ExportBypassGuard` 认这条策略）。
   *
   * 这里曾经写的是"导出侧尚未接脱敏 ⇒ `.setDisabled(true)`"。那个前提已经不存在：
   * `ExportBypassGuard`（enforce 下拒）与 `ExportColumnMasker`（内容层遮蔽）都已落地，
   * 而灰掉的选项会让人**根本没法授权** —— enforce 一开，所有受限角色的导出都是"缺明文授权"全拒，
   * 且管理端没有入口去修。灰掉一个已实现的能力，比少一个功能严重得多。
   * 反向保险在测试里：后端那两个类一旦被删，门禁就要求这里重新灰掉。
   */
  static readonly EXPORT = new DataPermissionActionType('EXPORT', '导出（明文需单独授权）')
    .setColor(WebColor.NORMAL)

  /**
   * 列级遮蔽（读侧 + 导出侧）真正消费的动作。
   *
   * 为什么这份名单写在字典里而不是让编辑器各写一遍：本字典同时被行级策略（DataPolicyEntity）
   * 与列级策略（DataFieldPolicyEntity）使用。行级按动作精确匹配、列级只读 QUERY/EXPORT，
   * 所以不能把 DETAIL/CREATE/UPDATE/DELETE 整族 setDisabled —— 那会直接让行级策略配不出来
   * （同一个坑的反向版本就写在本文件 EXPORT 的注释里）。列级这边只负责告诉管理员"这一行没人读"。
   *
   * 位置也有讲究：必须排在 EXPORT 之后，静态成员按声明顺序初始化，放前面会引用到未构造的成员。
   */
  static readonly COLUMN_MASKED: string[] = [
    DataPermissionActionType.QUERY.key,
    DataPermissionActionType.EXPORT.key,
  ]

  /**
   * 这个动作在列级遮蔽里会不会被读到
   *
   * 一致性不靠这段注释：maskContract.spec.ts 直接读后端 ColumnMaskingService.DEFAULT_ACTION
   * 与 ExportBypassGuard.ACTION_EXPORT 的字面值对账，还扫一遍遮蔽调用点（抄名单的对账等于没对账）。
   */
  static isColumnMasked = (key?: string): boolean => {
    return !!key && DataPermissionActionType.COLUMN_MASKED.includes(key)
  }
}
