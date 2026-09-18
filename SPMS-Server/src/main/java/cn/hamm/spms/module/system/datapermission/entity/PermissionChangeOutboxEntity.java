package cn.hamm.spms.module.system.datapermission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>权限变更 Outbox 事件</h1>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("permission_change_outbox")
@Description("权限变更消息")
public class PermissionChangeOutboxEntity extends BaseEntity<PermissionChangeOutboxEntity> {
    @Description("事件唯一键")
    private String eventKey;

    @Description("聚合类型")
    private String aggregateType;

    @Description("聚合 ID")
    private Long aggregateId;

    @Description("事件类型")
    private String eventType;

    @Description("事件载荷")
    private String payload;

    @Description("投递状态")
    private String status = "PENDING";

    @Description("重试次数")
    private Integer retryCount = 0;

    @Description("下次重试时间")
    private Long nextRetryAt;

    @Description("已处理时间")
    private Long processedAt;

    @Description("最后错误")
    private String lastError;
}
