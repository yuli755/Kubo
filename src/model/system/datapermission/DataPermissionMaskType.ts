import { WebColor, WebEnum } from '@/framework'

/**
 * # 列级脱敏类型
 *
 * key 就是后端 `data_field_policy.mask_type` 里存的字符串，取值必须与
 * `ColumnMaskingService#applyMask` 的 case 分支一致 ——
 * `src/config/__tests__/maskContract.spec.ts` 会直接从 Java 源码抓 case 标签来对账：
 * 后端加一支而这里没跟上，前端会把它当"未知类型"按最严格处理（过度遮蔽，且两头都"没错"）。
 */
export class DataPermissionMaskType extends WebEnum<string> {
  /** 完全遮蔽：忽略原值与长度，只输出表达式 */
  static readonly FULL = new DataPermissionMaskType('FULL', '完全遮蔽（不泄漏长度）')
    .setColor(WebColor.DANGER)

  /** 部分遮蔽：保留首尾各 1 位，中间星号封顶 6 个 */
  static readonly PARTIAL = new DataPermissionMaskType('PARTIAL', '部分遮蔽（留首尾）')
    .setColor(WebColor.WARNING)

  /** 替换为固定表达式 */
  static readonly REPLACE = new DataPermissionMaskType('REPLACE', '替换为表达式')
    .setColor(WebColor.NORMAL)

  /** 只保留末四位；长度不足时整体遮蔽 */
  static readonly LAST4 = new DataPermissionMaskType('LAST4', '只留末四位')
    .setColor(WebColor.SUCCESS)

  /**
   * 不可逆摘要：`h:` + SHA-256 前 12 位，同值同码可比对不可还原。
   *
   * 前端**不会**本地复算它（浏览器端算不出同口径），所以 shadow 阶段该类型由后端负责；
   * 若后端尚未开启 enforce，这一列会显示 `****` 而不是伪摘要。
   */
  static readonly HASH = new DataPermissionMaskType('HASH', '不可逆摘要（可比对不可还原）')
    .setColor(WebColor.NORMAL)
}
