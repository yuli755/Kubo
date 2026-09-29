package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.tenant.UserTenantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;
import static cn.hamm.spms.common.exception.Errors.PARAM_INVALID;

/**
 * <h1>租户上下文解析器</h1>
 *
 * <p><b>安全边界</b>：租户 ID 只能来自「已认证用户 + 服务端 user_tenant 关系」。
 * 请求头 {@code X-Tenant-Id} 仅表达「用户希望切换到哪个租户」的意图，
 * 必须经过成员关系校验后才可采用；不合法一律 403，
 * <b>不再像历史实现那样回落到 {@code 0L}</b>（那是横向越权入口）。</p>
 *
 * <p>规则：</p>
 * <ol>
 *   <li>用户无租户归属：{@code tenant-required=true} → 403；否则回落平台默认租户并告警；</li>
 *   <li>请求头未带租户：取用户默认租户（is_default，否则最小 ID）；</li>
 *   <li>请求头带租户：必须 ∈ 用户归属集合，否则 403；拥有
 *       {@value SystemPermissionIdentities#DATA_PERMISSION_BYPASS} 的系统账号可跨租户。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class TenantContextResolver {

    /**
     * 前端表达「切换到哪个租户」的意图头，不是可信来源
     */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final UserTenantService userTenantService;
    private final DataPermissionProperties properties;
    private final SystemPermissionService systemPermissionService;

    public TenantContextResolver(
            UserTenantService userTenantService,
            DataPermissionProperties properties,
            SystemPermissionService systemPermissionService) {
        this.userTenantService = userTenantService;
        this.properties = properties;
        this.systemPermissionService = systemPermissionService;
    }

    /**
     * 解析当前请求生效的租户 ID
     *
     * @param user    已认证用户
     * @param request 请求（只用于读取租户切换意图）
     * @return 租户 ID，保证非空
     */
    public Long resolve(UserEntity user, HttpServletRequest request) {
        Long userId = user.getId();
        Set<Long> owned = userId == null ? Set.of() : userTenantService.tenantIds(userId);
        boolean privileged = user.isRootUser() || systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_BYPASS);

        Long requested = readRequestedTenant(request);
        if (requested == null) {
            if (!owned.isEmpty()) {
                Long fallback = userTenantService.defaultTenantId(userId);
                return fallback != null ? fallback : properties.getDefaultTenantId();
            }
            return noTenantUser(privileged);
        }

        if (owned.contains(requested)) {
            return requested;
        }
        if (privileged) {
            log.warn("[data-permission] 系统账号 {} 跨租户访问 tenant={}", userId, requested);
            return requested;
        }
        FORBIDDEN.show("无权访问该租户：" + requested);
        // 不可达：FORBIDDEN.show 抛异常。保留返回值以满足编译器与控制流。
        return null;
    }

    /**
     * 无租户归属时的处理
     */
    private Long noTenantUser(boolean privileged) {
        if (privileged) {
            return properties.getDefaultTenantId();
        }
        FORBIDDEN.when(properties.isTenantRequired(), "账号未归属任何租户，请联系管理员");
        log.warn("[data-permission] 用户无租户归属，回落默认租户 {}；完成回填后请将 "
                + "kubo.data-permission.tenant-required 设为 true", properties.getDefaultTenantId());
        return properties.getDefaultTenantId();
    }

    /**
     * 读取并校验请求头中的租户意图，非法值直接拒绝而不是忽略
     */
    private Long readRequestedTenant(HttpServletRequest request) {
        String raw = request.getHeader(TENANT_HEADER);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        long parsed;
        try {
            parsed = Long.parseLong(raw.trim());
        }
        catch (NumberFormatException ex) {
            // 头里传了非数字 = **输入不合法**（4002），不是授权边界（403）。
            // 分开才有意义：403 会被监控与前端当成"越权尝试/去找管理员开权限"，
            // 而这里绝大多数是调用方把头拼错了。真正的越权在 L84「无权访问该租户」那条上。
            PARAM_INVALID.show("非法的租户标识：" + raw);
            return null;
        }
        PARAM_INVALID.when(parsed <= 0, "非法的租户标识：" + raw);
        return parsed;
    }
}
