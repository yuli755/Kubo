package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.core.annotation.Description;
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
 * <h1>影子双跑差异记录</h1>
 *
 * <p>shadow 模式的产出物：同一查询"不加权限条件"与"加条件"的行数差。
 * 它是"开 enforce 会发生什么"的唯一可信证据 —— 只看代码评审得出的结论不算。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Entity
@Data
@DynamicInsert
@DynamicUpdate
@Table(name = "data_permission_diff_log")
@Description("数据权限影子比对差异")
public class DataPermissionDiffEntity extends BaseEntity<DataPermissionDiffEntity> {

    @Description("ID")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Description("数据资源标识")
    @Column(name = "resource_key", nullable = false, length = 128)
    private String resourceKey;

    @Description("动作")
    @Column(name = "action_name", nullable = false, length = 16)
    private String actionName;

    @Description("请求链路标识（便于把差异回溯到具体查询）")
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Description("主体用户")
    @Column(name = "user_id")
    private Long userId;

    @Description("主体租户")
    @Column(name = "tenant_id")
    private Long tenantId;

    @Description("不加权限条件的行数")
    @Column(name = "baseline_count")
    private Long baselineCount;

    @Description("加权限条件后的行数")
    @Column(name = "guarded_count")
    private Long guardedCount;

    @Description("结论：CONSISTENT / EXPECTED_FILTER / ANOMALY / ERROR")
    @Column(name = "verdict", nullable = false, length = 16)
    private String verdict;

    @Description("比对耗时（毫秒）")
    @Column(name = "cost_ms")
    private Long costMs;

    @Description("明细（被过滤掉的主键样本等，受 shadow.max-detail-rows 限制）")
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /**
     * 结论枚举名（避免为了一个字符串字段再建表）
     */
    public static final String VERDICT_CONSISTENT = "CONSISTENT";
    public static final String VERDICT_EXPECTED_FILTER = "EXPECTED_FILTER";
    public static final String VERDICT_ANOMALY = "ANOMALY";
    public static final String VERDICT_ERROR = "ERROR";
}
