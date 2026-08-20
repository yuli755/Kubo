package cn.hamm.spms.common.interceptor;

import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.curd.interceptor.CurdRequestInterceptor;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import cn.hamm.spms.module.personnel.user.token.PersonalTokenEntity;
import cn.hamm.spms.module.personnel.user.token.PersonalTokenService;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.SystemPermissionService;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.hamm.airpower.exception.Errors.FORBIDDEN;
import static cn.hamm.airpower.exception.Errors.UNAUTHORIZED;

/**
 * <h1>请求拦截器</h1>
 *
 * @author Hamm.cn
 */
@Component
public class RequestInterceptor extends CurdRequestInterceptor {
    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Autowired
    private PersonalTokenService personalTokenService;

    @Autowired
    private SystemPermissionService systemPermissionService;

    /**
     * 验证指定的用户是否有指定权限标识的权限
     *
     * @param verifiedToken      合法令牌
     * @param permissionIdentity 权限标识
     * @param request            请求对象
     * @apiNote 抛出异常则为拦截
     */
    @Override
    public void checkUserPermission(
            AccessTokenUtil.@NotNull VerifiedToken verifiedToken,
            String permissionIdentity,
            HttpServletRequest request
    ) {
        long userId = verifiedToken.getPayloadId();
        UserEntity existUser = userService.getWithEnable(userId);
        ensurePermissionContext(existUser, request);
        if (existUser.isRootUser()) {
            return;
        }
        PermissionEntity needPermission = permissionService.getPermissionByIdentity(permissionIdentity);
        FORBIDDEN.whenNull(needPermission, "权限不存在");
        if (systemPermissionService.has(existUser, permissionIdentity)) {
            return;
        }
        FORBIDDEN.show(String.format(
                "你无权访问 %s (%s)", needPermission.getName(), needPermission.getIdentity()
        ));
    }

    @Override
    public AccessTokenUtil.VerifiedToken getVerifiedToken(String accessToken) {
        AccessTokenUtil.VerifiedToken verifiedToken = super.getVerifiedToken(accessToken);
        Object tokenType = verifiedToken.getPayload(UserTokenType.TYPE);
        FORBIDDEN.whenNull(tokenType, "无效的令牌类型");
        UserTokenType userTokenType = DictionaryUtil.getDictionary(UserTokenType.class, Integer.parseInt(tokenType.toString()));
        switch (userTokenType) {
            case PERSONAL:
                PersonalTokenEntity personalToken = personalTokenService.getByToken(accessToken);
                UNAUTHORIZED.whenNull(personalToken, "无效的私人令牌");
                FORBIDDEN.when(personalToken.getIsDisabled(), "私人令牌已被禁用");
                break;
            case OAUTH2:
            case NORMAL:
                break;
            default:
                FORBIDDEN.show("不支持的令牌类型");
        }
        return verifiedToken;
    }

    /**
     * 确保当前请求的数据权限上下文已设置
     *
     * @param user    已验证的用户
     * @param request 请求对象
     */
    private void ensurePermissionContext(@NotNull UserEntity user, HttpServletRequest request) {
        if (PermissionContextHolder.current().isPresent()) {
            return;
        }

        Set<String> roleCodes = Set.of();
        if (user.getRoleList() != null) {
            roleCodes = user.getRoleList().stream()
                    .filter(Objects::nonNull)
                    .map(RoleEntity::getCode)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
        }

        Set<Long> departmentIds = Set.of();
        if (user.getDepartmentList() != null) {
            departmentIds = user.getDepartmentList().stream()
                    .filter(Objects::nonNull)
                    .map(DepartmentEntity::getId)
                    .collect(Collectors.toSet());
        }

        boolean bypass = user.isRootUser() || systemPermissionService.has(
                user, cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities.DATA_PERMISSION_BYPASS
        );

        Long tenantId = resolveTenantId(request);

        PermissionContext context = new PermissionContext(
                user.getId(),
                tenantId,
                departmentIds,
                Set.of(),
                Set.of(),
                roleCodes,
                bypass
        );

        PermissionContextHolder.open(context);
    }

    /**
     * 从请求中解析租户 ID
     */
    private Long resolveTenantId(HttpServletRequest request) {
        String tenantHeader = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(tenantHeader)) {
            try {
                return Long.parseLong(tenantHeader);
            } catch (NumberFormatException ignored) {
                // 忽略非法租户 ID
            }
        }
        return 0L;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) {
        PermissionContextHolder.clear();
    }
}


