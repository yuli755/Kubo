package cn.hamm.spms.common.interceptor;

import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.airpower.core.DictionaryUtil;
import cn.hamm.airpower.curd.interceptor.CurdRequestInterceptor;
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
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
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
@Slf4j
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

    @Autowired
    private TenantContextResolver tenantContextResolver;

    @Autowired
    private cn.hamm.spms.module.system.datapermission.service.ExportBypassGuard exportBypassGuard;

    @Autowired
    private DataPermissionProperties dataPermissionProperties;

    @Autowired
    private DepartmentHierarchyIndex departmentHierarchyIndex;

    @Autowired
    private cn.hamm.spms.module.factory.storage.StorageHierarchyIndex storageHierarchyIndex;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private cn.hamm.spms.common.security.AccessTokenRevocationService accessTokenRevocationService;

    /**
     * 加载启用用户并<b>在只读事务内</b>初始化权限上下文需要的懒集合。
     *
     * <p>airpower 的 service 查询在非事务下走独立会话，返回实体是游离态；
     * 在事务外遍历 {@code roleList} 会抛 LazyInitializationException
     * （真实环境表现为每个鉴权请求 500，单测因 mock 普通 Set 而测不出）。
     * 这里用编程式事务（不给 UserService 引入 CGLIB 代理——它的 final
     * 方法会在未注入字段的代理实例上执行）完成集合 hydrate。</p>
     */
    private UserEntity loadUserWithInitializedAssociations(long userId) {
        boolean includeFactory = dataPermissionProperties != null && dataPermissionProperties.isFactoryDimensionEnabled();
        return transactionTemplate.execute(status -> {
            UserEntity user = userService.getWithEnable(userId);
            // JPA 加载后集合恒为空集而非 null；这里的判空与 ensurePermissionContext 的防御口径一致
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
        UserEntity existUser = loadUserWithInitializedAssociations(userId);
        ensurePermissionContext(existUser, request);
        // 导出走文件流、不经过 Jackson，是列级脱敏的已知旁路：在这里统一收口
        // （为什么放拦截器而不是各 controller：BaseController 全局排掉了 Curd.Export，
        //  只有个别实体通过 @Extends 加回来，逐个加钩子必然漏；旁路漏一个就等于没做）
        exportBypassGuard.guard(request);
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
        // 框架按「Authorization 头 = 裸令牌」设计，但前端 axios 与 OpenAPI 契约（Bearer securityScheme）
        // 都带 "Bearer " 前缀。在这里统一剥掉，两种写法都能验证；否则所有 Bearer 请求都会被判「身份令牌无效」。
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7).trim();
        }
        // 登出吊销：签名有效但已被 /user/logout 拉黑的令牌在这里拦下（自包含令牌自身无状态，
        // 不查名单的话注销只是"客户端不记事"，服务端仍认）。
        if (accessTokenRevocationService != null && accessTokenRevocationService.isRevoked(accessToken)) {
            UNAUTHORIZED.show("登录状态已失效，请重新登录");
        }
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

        // DEPARTMENT 策略是否连带子部门，取决于开关：展开是**放宽**可见范围（配在父部门的策略会盖住整棵子树），
        // 而现网既有策略大概都是按"只影响直属部门"配的 ⇒ 默认关，先影子比对再逐资源放开。
        Set<Long> departmentScopeIds = departmentIds;
        if (dataPermissionProperties != null && departmentHierarchyIndex != null
                && dataPermissionProperties.getHierarchy().isDepartmentEnabled()) {
            HierarchyClosure.Closure closure = departmentHierarchyIndex.expandFrom(departmentIds);
            departmentScopeIds = closure.ids();
            if (closure.truncated()) {
                // 只在脏数据（环）或树深得离谱时发生；此时可见范围会偏小（安全侧），但必须留下痕迹，
                // 否则排错的人只会看到"某人莫名其妙少看了两个部门"
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

        // 工厂（FACTORY）维度（D13）：factoryIds 只在维度落地（factory-dimension-enabled）后才解析，
        // 否则保持恒空 —— 默认关闭的代价必须是零（多一次懒加载查询也算代价，不只是「结果上等价」）。
        // 工厂 = 仓库/工厂树顶层节点；hierarchy.factory-enabled 打开时再向下闭包到整棵子树（放宽，默认关）。
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

        // 租户 ID 只信任服务端解析结果（user_tenant 关系），请求头仅作切换意图（决策 D1）
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
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) {
        PermissionContextHolder.clear();
        // 掩码登记是 ThreadLocal：线程池会复用线程，忘了清等于把上一个人的"这字段是掩码"
        // 送给下一个人 —— 表现是别人的保存莫名其妙把价格改回了旧值
        MaskedInputRegistry.clear();
    }
}


