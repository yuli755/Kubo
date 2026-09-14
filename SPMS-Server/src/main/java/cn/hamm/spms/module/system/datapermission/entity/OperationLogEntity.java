package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

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
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "operation_log")
@Description("通用操作审计日志")
public class OperationLogEntity extends BaseEntity<OperationLogEntity> {

    @Description("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Description("资源标识（如 asset.material）")
    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Description("动作（CREATE / UPDATE / DELETE）")
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Description("操作者用户")
    @Column(name = "user_id")
    private Long userId;

    @Description("操作者租户")
    @Column(name = "tenant_id")
    private Long tenantId;

    @Description("目标记录 ID")
    @Column(name = "target_id")
    private Long targetId;

    @Description("目标记录摘要（编码/名称）")
    @Column(name = "target_label", length = 255)
    private String targetLabel;

    @Description("变更前快照（JSON）")
    @Column(name = "before_snapshot", columnDefinition = "text")
    private String beforeSnapshot;

    @Description("变更后快照（JSON）")
    @Column(name = "after_snapshot", columnDefinition = "text")
    private String afterSnapshot;
}
