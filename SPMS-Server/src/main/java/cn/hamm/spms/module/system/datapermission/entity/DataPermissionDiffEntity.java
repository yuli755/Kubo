package cn.hamm.spms.module.system.datapermission.entity;

import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

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
@TableName("data_permission_diff_log")
@Data
@Description("数据权限影子比对差异")
public class DataPermissionDiffEntity extends BaseEntity<DataPermissionDiffEntity> {

    @Description("数据资源标识")
    private String resourceKey;

    @Description("动作")
    private String actionName;

    @Description("请求链路标识（便于把差异回溯到具体查询）")
    private String traceId;

    @Description("主体用户")
    private Long userId;

    @Description("主体租户")
    private Long tenantId;

    @Description("不加权限条件的行数")
    private Long baselineCount;

    @Description("加权限条件后的行数")
    private Long guardedCount;

    @Description("结论：CONSISTENT / EXPECTED_FILTER / ANOMALY / ERROR")
    private String verdict;

    @Description("比对耗时（毫秒）")
    private Long costMs;

    @Description("明细（被过滤掉的主键样本等，受 shadow.max-detail-rows 限制）")
    private String detail;

    /**
     * 结论枚举名（避免为了一个字符串字段再建表）
     */
    public static final String VERDICT_CONSISTENT = "CONSISTENT";
    public static final String VERDICT_EXPECTED_FILTER = "EXPECTED_FILTER";
    public static final String VERDICT_ANOMALY = "ANOMALY";
    public static final String VERDICT_ERROR = "ERROR";
}
