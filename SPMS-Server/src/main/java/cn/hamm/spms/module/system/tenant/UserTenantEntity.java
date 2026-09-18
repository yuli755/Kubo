package cn.hamm.spms.module.system.tenant;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.hamm.spms.core.annotation.Description;
import cn.hamm.spms.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <h1>用户-租户关系</h1>
 *
 * <p><b>这是租户 ID 的唯一可信来源</b>（决策 D1）：
 * {@code PermissionContext.tenantId} 必须由本表解析得出，
 * <b>禁止</b>直接采信前端提交的 {@code X-Tenant-Id}。</p>
 *
 * <p>一个用户可属于多个租户（平台运营方、跨厂协作场景），
 * 其中 {@code isDefault} 标记登录后的默认租户；前端切换租户时只能在
 * 本表存在的租户集合内选择，否则按越权处理（403）。</p>
 *
 * @author Hamm.cn
 */
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Data
@TableName("user_tenant")
@Description("用户租户关系")
public class UserTenantEntity extends BaseEntity<UserTenantEntity> {
    @Description("用户 ID")
    private Long userId;

    @Description("租户 ID")
    private Long tenantId;

    @Description("是否默认租户")
    private Boolean isDefault = false;
}
