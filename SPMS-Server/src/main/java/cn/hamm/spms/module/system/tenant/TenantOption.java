package cn.hamm.spms.module.system.tenant;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <h1>当前用户可选租户</h1>
 *
 * <p>只向前端返回租户切换所需的最小投影，不把租户实体的内部字段带出。</p>
 *
 * @author Hamm.cn
 */
@Data
@Accessors(chain = true)
public class TenantOption {
    private Long id;
    private String code;
    private String name;
    private Boolean isDefault;
}
