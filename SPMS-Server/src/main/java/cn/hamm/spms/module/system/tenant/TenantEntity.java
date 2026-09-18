package cn.hamm.spms.module.system.tenant;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>租户</h1>
 *
 * <p>真多租户（决策 D1/D2）的租户主数据。
 * 表结构见 {@code docs/database/migration/20260906_multitenant_foundation.sql}，
 * 两侧字段必须严格一致（生产 {@code ddl-auto=validate}，不一致会启动失败）。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("tenant")
@Description("租户")
public class TenantEntity extends BaseEntity<TenantEntity> {
    @Description("租户编码")
    private String code;

    @Description("租户名称")
    private String name;

    @Description("状态")
    private Integer status = 1;

    @Description("到期时间")
    private Long expireTime;
}
