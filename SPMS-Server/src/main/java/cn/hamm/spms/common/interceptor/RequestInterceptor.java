package cn.hamm.spms.common.interceptor;

import cn.hamm.spms.common.config.ApiConfig;
import cn.hamm.spms.common.security.AccessTokenUtil;
import cn.hamm.spms.framework.util.Tracer;
import org.slf4j.MDC;
import cn.hamm.spms.common.dictionary.EnumDictionaries;
import cn.hamm.spms.common.security.AccessTokenRevocationService;
import cn.hamm.spms.common.security.PermissionScanner;
import cn.hamm.spms.common.tree.HierarchyClosure;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.department.DepartmentHierarchyIndex;
import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.personnel.user.enums.UserTokenType;
import cn.hamm.spms.module.personnel.user.token.PersonalTokenEntity;
import cn.hamm.spms.module.personnel.user.token.PersonalTokenService;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.ExportBypassGuard;
import cn.hamm.spms.module.system.datapermission.service.MaskedInputRegistry;
import cn.hamm.spms.module.system.datapermission.service.SystemPermissionService;
import cn.hamm.spms.module.system.datapermission.service.TenantContextResolver;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static cn.hamm.spms.common.exception.Errors.FORBIDDEN;
import static cn.hamm.spms.common.exception.Errors.UNAUTHORIZED;

/**
 * <h1>全局请求拦截器</h1>
 *
 * <p>自持核心拦截器，替代旧 {@code CurdRequestInterceptor}。
 * 负责链路追踪 ID 注入、Token 验证、RBAC 权限校验与数据权限上下文建立与清理。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Component
public class RequestInterceptor implements HandlerInterceptor {
    public static final String REQUEST_METHOD_KEY = "REQUEST_METHOD_KEY";
    public static final String REQUEST_CONTROLLER_KEY = "REQUEST_CONTROLLER_KEY";

    @Autowired(required = false)
    protected ApiConfig apiConfig;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Autowired
    private PersonalTokenService personalTokenService;

    @Autowired
    private SystemPermissionService systemPermissionService;

    @Autowired
    private TenantContextResolver tenantContextResolver;

    @Autowired
    private ExportBypassGuard exportBypassGuard;

    @Autowired
    private DataPermissionProperties dataPermissionProperties;

    @Autowired
    private DepartmentHierarchyIndex departmentHierarchyIndex;

    @Autowired
    private cn.hamm.spms.module.factory.storage.StorageHierarchyIndex storageHierarchyIndex;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccessTokenRevocationService accessTokenRevocationService;

    @Override
    public boolean preHandle(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler
    ) {
        String traceId = request.getHeader("X-Trace-ID");
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Tracer.setTraceId(traceId);
        try {
            MDC.put("X-Trace-ID", traceId);
        } catch (Throwable ignored) {
        }
        response.setHeader("X-Trace-ID", traceId);

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Class<?> clazz = handlerMethod.getBeanType();
        Method method = handlerMethod.getMethod();

        setShareData(REQUEST_METHOD_KEY, method);
        setShareData(REQUEST_CONTROLLER_KEY, handlerMethod.getBean());

        handleRequest(request, response, clazz, method);
        return true;
    }

    protected final void setShareData(String key, Object value) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.setAttribute(key, value, RequestAttributes.SCOPE_REQUEST);
        }
    }

    private void handleRequest(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            Class<?> clazz,
            Method method
    ) {
        PermissionScanner.AccessRequirement access = PermissionScanner.getAccessRequirement(clazz, method);
        if (!access.login()) {
            return;
        }

        String authorizeHeader = apiConfig != null ? apiConfig.getAuthorizeHeader() : "Authorization";
        String accessToken = request.getHeader(authorizeHeader);
        String accessTokenFromParam = request.getParameter(authorizeHeader);
        if (StringUtils.hasText(accessTokenFromParam)) {
            accessToken = accessTokenFromParam;
        }
        UNAUTHORIZED.whenEmpty(accessToken);

        AccessTokenUtil.VerifiedToken verifiedToken = getVerifiedToken(accessToken);

        if (access.authorize()) {
            String permissionIdentity = PermissionScanner.getControllerIdentity(clazz) + "_" + method.getName();
            checkUserPermission(verifiedToken, permissionIdentity, request);
        }
    }

    /**
     * 加载启用用户并在只读事务内初始化权限上下文需要的懒集合。
     */
    private UserEntity loadUserWithInitializedAssociations(long userId) {
        boolean includeFactory = dataPermissionProperties != null && dataPermissionProperties.isFactoryDimensionEnabled();
        return transactionTemplate.execute(status -> {
            UserEntity user = userService.getWithEnable(userId);
            if (user.getRoleList() != null) {
                user.getRoleList().size();
            }
            if (user.getDepartmentList() != null) {
                user.getDepartmentList().size();
            }
            if (user.getStorageList() != null) {
                user.getStorageList().size();
            }
            if (includeFactory && user.getFactoryList() != null) {
                user.getFactoryList().size();
            }
            return user;
        });
    }

    /**
     * 验证指定的用户是否有指定权限标识的权限
     */
    public void checkUserPermission(
            AccessTokenUtil.@NotNull VerifiedToken verifiedToken,
            String permissionIdentity,
            HttpServletRequest request
    ) {
        long userId = verifiedToken.getPayloadId();
        UserEntity existUser = loadUserWithInitializedAssociations(userId);
        ensurePermissionContext(existUser, request);
        try {
            exportBypassGuard.guard(request);
            if (existUser.isRootUser()) {
                return;
            }
            PermissionEntity needPermission = permissionService.getPermissionByIdentity(permissionIdentity);
            FORBIDDEN.whenNull(needPermission, "权限不存在");
            if (systemPermissionService.has(existUser, needPermission.getIdentity())) {
                return;
            }
            FORBIDDEN.show(String.format(
                    "你无权访问 %s (%s)", needPermission.getName(), needPermission.getIdentity()
            ));
        } catch (RuntimeException ex) {
            PermissionContextHolder.clear();
            throw ex;
        }
    }

    public AccessTokenUtil.VerifiedToken getVerifiedToken(String accessToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7).trim();
        }
        if (accessTokenRevocationService != null && accessTokenRevocationService.isRevoked(accessToken)) {
            UNAUTHORIZED.show("登录状态已失效，请重新登录");
        }
        String secret = apiConfig != null ? apiConfig.getAccessTokenSecret() : "AIRPOWER";
        AccessTokenUtil.VerifiedToken verifiedToken = AccessTokenUtil.create().verify(accessToken, secret);
        Object tokenType = verifiedToken.getPayload(UserTokenType.TYPE);
        FORBIDDEN.whenNull(tokenType, "无效的令牌类型");
        UserTokenType userTokenType = EnumDictionaries.require(
                UserTokenType.class,
                Integer.parseInt(tokenType.toString()),
                UserTokenType::getKey,
                UserTokenType::getLabel
        );
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
     */
    private void ensurePermissionContext(@NotNull UserEntity user, HttpServletRequest request) {
        Set<String> roleCodes = Set.of();
        if (user.getRoleList() != null) {
            roleCodes = user.getRoleList().stream()
                    .filter(role -> role != null && !Boolean.TRUE.equals(role.getIsDisabled()))
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

        Set<Long> departmentScopeIds = departmentIds;
        if (dataPermissionProperties != null && departmentHierarchyIndex != null
                && dataPermissionProperties.getHierarchy().isDepartmentEnabled()) {
            HierarchyClosure.Closure closure = departmentHierarchyIndex.expandFrom(departmentIds);
            departmentScopeIds = closure.ids();
            if (closure.truncated()) {
                log.warn("部门层级闭包被深度上限截断，本次可见范围可能不完整：seeds={}", departmentIds);
            }
        }

        Set<Long> warehouseIds = Set.of();
        if (user.getStorageList() != null) {
            warehouseIds = user.getStorageList().stream()
                    .filter(Objects::nonNull)
                    .map(StorageEntity::getId)
                    .collect(Collectors.toSet());
        }

        Set<Long> factoryIds = Set.of();
        if (dataPermissionProperties != null && dataPermissionProperties.isFactoryDimensionEnabled()
                && user.getFactoryList() != null) {
            factoryIds = user.getFactoryList().stream()
                    .filter(Objects::nonNull)
                    .map(StorageEntity::getId)
                    .collect(Collectors.toSet());
            if (dataPermissionProperties.getHierarchy().isFactoryEnabled() && storageHierarchyIndex != null) {
                HierarchyClosure.Closure closure = storageHierarchyIndex.expandFrom(factoryIds);
                factoryIds = closure.ids();
                if (closure.truncated()) {
                    log.warn("工厂树闭包被深度上限截断，本次可见范围可能不完整：seeds={}", factoryIds);
                }
            }
        }

        boolean bypass = user.isRootUser() || systemPermissionService.has(
                user, cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities.DATA_PERMISSION_BYPASS
        );

        Long tenantId = tenantContextResolver.resolve(user, request);

        PermissionContext context = new PermissionContext(
                user.getId(),
                tenantId,
                departmentIds,
                departmentScopeIds,
                factoryIds,
                warehouseIds,
                roleCodes,
                bypass
        );

        PermissionContextHolder.open(context);
    }

    @Override
    public void afterCompletion(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull Object handler,
            Exception ex
    ) {
        PermissionContextHolder.clear();
        MaskedInputRegistry.clear();
        Tracer.clear();
        try {
            MDC.remove("X-Trace-ID");
        } catch (Throwable ignored) {
        }
    }
}
