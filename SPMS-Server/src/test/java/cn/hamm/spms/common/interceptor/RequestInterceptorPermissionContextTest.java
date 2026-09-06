package cn.hamm.spms.common.interceptor;

import cn.hamm.airpower.core.AccessTokenUtil;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.personnel.user.token.PersonalTokenService;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.SystemPermissionService;
import cn.hamm.spms.module.system.datapermission.service.TenantContextResolver;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * <h1>RequestInterceptor 数据权限上下文测试</h1>
 *
 * <p>验证 {@link PermissionContext} 在请求拦截时是否正确填充和清理。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class RequestInterceptorPermissionContextTest {

    @Mock
    private PermissionService permissionService;

    @Mock
    private UserService userService;

    @Mock
    private PersonalTokenService personalTokenService;

    @Mock
    private SystemPermissionService systemPermissionService;

    @Mock
    private TenantContextResolver tenantContextResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AccessTokenUtil.VerifiedToken verifiedToken;

    private RequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new RequestInterceptor();
        // 注入 mock 依赖（通过反射）
        injectField(interceptor, "permissionService", permissionService);
        injectField(interceptor, "userService", userService);
        injectField(interceptor, "personalTokenService", personalTokenService);
        injectField(interceptor, "systemPermissionService", systemPermissionService);
        injectField(interceptor, "tenantContextResolver", tenantContextResolver);
        // 默认：服务端解析出的租户为 1（具体语义见 TenantContextResolverTest）
        lenient().when(tenantContextResolver.resolve(any(UserEntity.class), any(HttpServletRequest.class))).thenReturn(1L);
        PermissionContextHolder.clear();
        // ensurePermissionContext 总会检查 bypass 权限，需要默认 stub
        lenient().when(systemPermissionService.has(any(UserEntity.class), anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        PermissionContextHolder.clear();
    }

    @Nested
    class ContextPopulation {
        @Test
        void setsPermissionContextForNormalUser() throws Exception {
            UserEntity user = createUser(1L, "张三", false);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertEquals(1L, context.userId());
            assertFalse(context.bypassDataPermission());
            assertEquals(1, context.roleCodes().size());
            assertTrue(context.roleCodes().contains("planner"));
            assertEquals(1, context.departmentIds().size());
            assertTrue(context.departmentIds().contains(10L));
        }

        @Test
        void setsBypassForRootUser() throws Exception {
            UserEntity rootUser = createUser(1L, "管理员", true);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(rootUser);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertTrue(context.bypassDataPermission(), "root 用户应绕过数据权限");
        }

        @Test
        void setsBypassForDataPermissionBypassRole() throws Exception {
            UserEntity user = createUser(2L, "审计员", false);
            given(verifiedToken.getPayloadId()).willReturn(2L);
            given(userService.getWithEnable(2L)).willReturn(user);
            given(systemPermissionService.has(user, SystemPermissionIdentities.DATA_PERMISSION_BYPASS))
                    .willReturn(true);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertTrue(context.bypassDataPermission(), "拥有 data-permission:bypass 的用户应绕过数据权限");
        }

        /**
         * 决策 D1：租户只信任服务端解析结果。
         *
         * 旧实现是「从 X-Tenant-Id 请求头解析、缺省回落 0L」，等于把租户隔离交给前端，
         * 一旦业务表补上 tenant_id 就是横向越权入口。这里改为断言「拦截器把解析职责
         * 委托给 TenantContextResolver，并把它的结果原样写进上下文」。
         * 解析规则本身（成员校验、非法头拒绝、无归属处理）见 TenantContextResolverTest。
         */
        @Test
        void delegatesTenantResolutionToServerSideResolver() throws Exception {
            UserEntity user = createUser(1L, "张三", false);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);
            // 客户端伪造的租户头不应影响结果：解析器返回什么就用什么
            given(request.getHeader("X-Tenant-Id")).willReturn("999999");
            given(tenantContextResolver.resolve(any(UserEntity.class), any(HttpServletRequest.class))).willReturn(7L);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertEquals(7L, context.tenantId(), "上下文租户必须来自服务端解析结果");
            then(tenantContextResolver).should().resolve(user, request);
            then(request).should(never()).getParameter("tenantId");
        }
    }

    @Nested
    class ContextCleanup {
        @Test
        void clearsContextAfterCompletion() {
            // 先设置一个上下文
            PermissionContext context = new PermissionContext(
                    1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
            );
            PermissionContextHolder.open(context);

            interceptor.afterCompletion(request, response, new Object(), null);

            assertTrue(PermissionContextHolder.current().isEmpty(), "请求完成后应清理上下文");
        }
    }

    private UserEntity createUser(long id, String nickname, boolean isRoot) {
        RoleEntity role = new RoleEntity().setCode("planner").setName("计划员");
        role.setId(100L);

        DepartmentEntity dept = new DepartmentEntity().setCode("a1").setName("生产部");
        dept.setId(10L);

        UserEntity user = new UserEntity();
        user.setId(id);
        user.setNickname(nickname);
        if (isRoot) {
            PermissionEntity rootPerm = new PermissionEntity();
            rootPerm.setIdentity(SystemPermissionIdentities.ROOT);
            RoleEntity rootRole = new RoleEntity().setCode("SYSTEM_ROOT").setName("系统超级管理员");
            rootRole.setId(999L);
            setField(rootRole, "permissionList", new LinkedHashSet<>(Set.of(rootPerm)));
            setField(user, "roleList", new LinkedHashSet<>(Set.of(role, rootRole)));
        } else {
            setField(user, "roleList", new LinkedHashSet<>(Set.of(role)));
        }
        setField(user, "departmentList", new LinkedHashSet<>(Set.of(dept)));
        return user;
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getSuperclass() != null
                    ? findField(target.getClass(), fieldName)
                    : target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), name);
            }
            throw e;
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}