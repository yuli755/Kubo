package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>通用操作审计日志</h1>
 *
 * <p>记录「谁在何时对哪条业务记录做了什么」。触发点是 {@code BaseService} 的增/改/删钩子，
 * 只对标记了 {@code @OperationLog} 的实体生效；身份在业务线程取成值后异步落库，
 * 因此与 {@code data_permission_deny_log} 一样是「事后可查、不影响业务事务」的证据表。</p>
 *
 * <p><b>快照口径</b>：{@code before_snapshot}/{@code after_snapshot} 只存标量字段的 JSON
 * （{@link cn.hamm.spms.module.system.datapermission.service.OperationLogSnapshot} 负责安全序列化，
 * 跳过关系/懒加载字段），不存敏感字段的明文聚合，避免把审计台做成绕过列级脱敏的旁路。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("operation_log")
@Data
@Description("通用操作审计日志")
public class OperationLogEntity extends BaseEntity<OperationLogEntity> {

    @Description("资源标识（如 asset.material）")
    private String resourceKey;

    @Description("动作（CREATE / UPDATE / DELETE）")
    private String action;

    @Description("操作者用户")
    private Long userId;

    @Description("操作者租户")
    private Long tenantId;

    @Description("目标记录 ID")
    private Long targetId;

    @Description("目标记录摘要（编码/名称）")
    private String targetLabel;

    @Description("变更前快照（JSON）")
    private String beforeSnapshot;

    @Description("变更后快照（JSON）")
    private String afterSnapshot;
}
