package cn.hamm.spms.common.interceptor;

import cn.hamm.spms.common.security.AccessTokenUtil;
import cn.hamm.spms.common.tree.HierarchyClosure;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.storage.StorageHierarchyIndex;
import cn.hamm.spms.module.personnel.department.DepartmentHierarchyIndex;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
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

    /**
     * 导出旁路收口的守卫（F6 挂在 checkUserPermission 之后）。
     * 本类用反射手搭 RequestInterceptor，所以新增协作对象必须在这里一起注入，
     * 否则跑到那一行就是 NPE —— 而不是"编译期就发现少给了依赖"，这是字段注入的代价。
     * 不 stub：guard() 是 void，mock 默认什么都不做；多打一个桩反而会被 strict stubs 判成 UnnecessaryStubbing。
     */
    @Mock
    private cn.hamm.spms.module.system.datapermission.service.ExportBypassGuard exportBypassGuard;

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
        injectField(interceptor, "exportBypassGuard", exportBypassGuard);
        // 编程式事务模板：测试里没有真实事务，直通执行回调即可
        injectField(interceptor, "transactionTemplate", new org.springframework.transaction.support.TransactionTemplate() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        });
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
            // 客户端伪造的租户头不应影响结果：解析器返回什么就用什么。
            // 这里刻意不给 request.getHeader 打桩 —— 拦截器根本不该去读那个头，
            // 真读了就是旁路；strict stubs 会替我们把"没读"这件事盯着（打了桩反而会被判多余）。
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

    @Nested
    @org.junit.jupiter.api.DisplayName("部门层级闭包（开关控制）")
    class DepartmentHierarchyExpansion {

        /**
         * 开关关着时必须<b>一次都不碰索引</b>：展开是放宽可见范围的能力，
         * 默认关闭不能只是"结果上等价"，还得是"代价上为零"（否则灰度开关就成了摆设）
         */
        @Test
        void doesNotTouchIndexWhenDisabled() throws Exception {
            UserEntity user = createUser(1L, "张三", false);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);
            DepartmentHierarchyIndex index = org.mockito.Mockito.mock(DepartmentHierarchyIndex.class);
            injectField(interceptor, "dataPermissionProperties", new DataPermissionProperties());
            injectField(interceptor, "departmentHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertEquals(Set.of(10L), context.departmentScopeIds());
            org.mockito.Mockito.verifyNoInteractions(index);
        }

        @Test
        void widensScopeWithDescendantsWhenEnabled() throws Exception {
            UserEntity user = createUser(1L, "张三", false);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            DataPermissionProperties properties = new DataPermissionProperties();
            properties.getHierarchy().setDepartmentEnabled(true);
            DepartmentHierarchyIndex index = org.mockito.Mockito.mock(DepartmentHierarchyIndex.class);
            given(index.expandFrom(any())).willReturn(
                    new HierarchyClosure.Closure(Set.of(10L, 20L, 21L), false));
            injectField(interceptor, "dataPermissionProperties", properties);
            injectField(interceptor, "departmentHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            PermissionContext context = PermissionContextHolder.require();
            assertEquals(Set.of(10L), context.departmentIds(), "直属集合保持原样，供审计与前端对比");
            assertEquals(Set.of(10L, 20L, 21L), context.departmentScopeIds());
            assertTrue(context.coversDepartment(21L), "配在父部门的策略应当覆盖到子部门");
            then(index).should().expandFrom(Set.of(10L));
        }

        /**
         * 脏数据（成环/超深）导致截断时：照常填充范围并把 WARN 打出去，绝不抛异常 ——
         * 这段代码在拦截器里，抛出异常等于全站接口 500
         */
        @Test
        void truncatedClosureStillPopulatesWithoutThrowing() throws Exception {
            UserEntity user = createUser(1L, "张三", false);
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            DataPermissionProperties properties = new DataPermissionProperties();
            properties.getHierarchy().setDepartmentEnabled(true);
            DepartmentHierarchyIndex index = org.mockito.Mockito.mock(DepartmentHierarchyIndex.class);
            given(index.expandFrom(any())).willReturn(new HierarchyClosure.Closure(Set.of(10L, 20L), true));
            injectField(interceptor, "dataPermissionProperties", properties);
            injectField(interceptor, "departmentHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            assertEquals(Set.of(10L, 20L), PermissionContextHolder.require().departmentScopeIds());
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("工厂维度（D13：开关控制）")
    class FactoryResolution {

        private UserEntity userWithFactories() {
            UserEntity user = createUser(1L, "张三", false);
            StorageEntity factory = new StorageEntity().setName("一号工厂");
            factory.setId(30L);
            setField(user, "factoryList", new LinkedHashSet<>(Set.of(factory)));
            return user;
        }

        @Test
        void keepsFactoryIdsEmptyWhenDimensionDisabled() throws Exception {
            UserEntity user = userWithFactories();
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            // D13-B 起默认 factoryDimensionEnabled=true，这里显式关掉它：
            // 维度关闭时不得解析 factoryList，也不得碰树索引（代价为零才叫灰度开关）
            DataPermissionProperties properties = new DataPermissionProperties();
            properties.setFactoryDimensionEnabled(false);
            StorageHierarchyIndex index = org.mockito.Mockito.mock(StorageHierarchyIndex.class);
            injectField(interceptor, "dataPermissionProperties", properties);
            injectField(interceptor, "storageHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            assertEquals(Set.of(), PermissionContextHolder.require().factoryIds());
            org.mockito.Mockito.verifyNoInteractions(index);
        }

        @Test
        void resolvesFactoryIdsWhenDimensionEnabled() throws Exception {
            UserEntity user = userWithFactories();
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            DataPermissionProperties properties = new DataPermissionProperties();
            properties.setFactoryDimensionEnabled(true);
            StorageHierarchyIndex index = org.mockito.Mockito.mock(StorageHierarchyIndex.class);
            injectField(interceptor, "dataPermissionProperties", properties);
            injectField(interceptor, "storageHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            assertEquals(Set.of(30L), PermissionContextHolder.require().factoryIds());
            // 层级展开默认关：不碰索引
            org.mockito.Mockito.verifyNoInteractions(index);
        }

        @Test
        void expandsFactoryScopeWhenHierarchyEnabled() throws Exception {
            UserEntity user = userWithFactories();
            given(verifiedToken.getPayloadId()).willReturn(1L);
            given(userService.getWithEnable(1L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("test:permission").setName("测试权限"));
            given(systemPermissionService.has(any(UserEntity.class), eq("test:permission"))).willReturn(true);

            DataPermissionProperties properties = new DataPermissionProperties();
            properties.setFactoryDimensionEnabled(true);
            properties.getHierarchy().setFactoryEnabled(true);
            StorageHierarchyIndex index = org.mockito.Mockito.mock(StorageHierarchyIndex.class);
            given(index.expandFrom(any())).willReturn(new HierarchyClosure.Closure(Set.of(30L, 31L, 32L), false));
            injectField(interceptor, "dataPermissionProperties", properties);
            injectField(interceptor, "storageHierarchyIndex", index);

            interceptor.checkUserPermission(verifiedToken, "test:permission", request);

            assertEquals(Set.of(30L, 31L, 32L), PermissionContextHolder.require().factoryIds());
            then(index).should().expandFrom(Set.of(30L));
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("权限标识大小写口径（B-1 回归）")
    class IdentityCaseNormalization {
        /**
         * 框架按 URL 派生的标识实体名全小写（material_getPage），库里登记的是实体类名
         * （Material_getPage）。getPermissionByIdentity 靠库内 CI 排序规则能查到行，
         * 但 has() 必须拿库里登记的 identity 去比对——直接拿框架传来的原始串比对，
         * Java 的 equals 区分大小写，所有非 root 用户都会被误判 403（B-1）。
         */
        @Test
        void grantsWhenStoredIdentityDiffersFromUrlDerivedOnlyInCase() throws Exception {
            UserEntity user = createUser(24L, "审计员", false);
            given(verifiedToken.getPayloadId()).willReturn(24L);
            given(userService.getWithEnable(24L)).willReturn(user);
            // 库里登记的是 PascalCase；框架传来的请求标识是小写
            given(permissionService.getPermissionByIdentity("material_getPage"))
                    .willReturn(new PermissionEntity().setIdentity("Material_getPage").setName("物料-分页查询"));
            given(systemPermissionService.has(user, "Material_getPage")).willReturn(true);

            interceptor.checkUserPermission(verifiedToken, "material_getPage", request);

            // 核心断言：has() 收到的是库中登记的 identity，而不是框架原始串
            then(systemPermissionService).should().has(user, "Material_getPage");
        }

        @Test
        void stillDeniesWhenIdentityTrulyAbsent() throws Exception {
            UserEntity user = createUser(24L, "审计员", false);
            given(verifiedToken.getPayloadId()).willReturn(24L);
            given(userService.getWithEnable(24L)).willReturn(user);
            given(permissionService.getPermissionByIdentity("material_delete"))
                    .willReturn(new PermissionEntity().setIdentity("Material_delete").setName("物料-删除"));
            // has() 按库中 identity 仍未命中 → 必须照常 403，修复不能放宽成「查到行就放行」
            RuntimeException error = org.junit.jupiter.api.Assertions.assertThrows(
                    RuntimeException.class,
                    () -> interceptor.checkUserPermission(verifiedToken, "material_delete", request));
            org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(error.getMessage()).contains("无权访问"),
                    "拒绝文案应指明无权访问，实际：" + error.getMessage());
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("上下文残留与重建（B-12 回归）")
    class ContextLeakPrevention {
        /**
         * preHandle 链路里抛异常时 Spring 不会回调本拦截器的 afterCompletion。
         * 鉴权在 ensurePermissionContext 之后抛出（403），若不在此处清上下文，
         * 它会滞留在 Tomcat 线程上，被下一个复用线程的请求继承——上一个请求的
         * userId/租户/bypass 位直接冒充本请求（B12 伪造 X-Tenant-Id 躲过校验即此因）。
         */
        @Test
        void clearsContextWhenAuthorizationThrows() throws Exception {
            UserEntity user = createUser(24L, "审计员", false);
            given(verifiedToken.getPayloadId()).willReturn(24L);
            given(userService.getWithEnable(24L)).willReturn(user);
            given(permissionService.getPermissionByIdentity("material_delete"))
                    .willReturn(new PermissionEntity().setIdentity("Material_delete").setName("物料-删除"));
            // lenient 默认 has()=false → 走到 FORBIDDEN.show 抛出
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> interceptor.checkUserPermission(verifiedToken, "material_delete", request));
            assertTrue(PermissionContextHolder.current().isEmpty(),
                    "鉴权抛出后线程上不得残留上下文，否则下个复用该线程的请求继承本请求身份");
        }

        /**
         * 即使线程上已有「别的请求留下的」上下文，拦截器也必须按本请求用户重建，
         * 而不是短路放行——陈旧上下文接管 = 跨请求越权。
         */
        @Test
        void rebuildsContextOverStaleOne() throws Exception {
            // 模拟残留：上个请求（userId=999、tenant=77、bypass）没清干净
            PermissionContextHolder.open(new PermissionContext(
                    999L, 77L, Set.of(), Set.of(), Set.of(), Set.of(), true));
            UserEntity user = createUser(24L, "审计员", false);
            given(verifiedToken.getPayloadId()).willReturn(24L);
            given(userService.getWithEnable(24L)).willReturn(user);
            given(permissionService.getPermissionByIdentity(anyString()))
                    .willReturn(new PermissionEntity().setIdentity("Material_getPage").setName("物料-分页查询"));
            given(systemPermissionService.has(user, "Material_getPage")).willReturn(true);
            given(tenantContextResolver.resolve(user, request)).willReturn(7L);

            interceptor.checkUserPermission(verifiedToken, "material_getPage", request);

            PermissionContext context = PermissionContextHolder.require();
            assertEquals(24L, context.userId(), "上下文必须按本请求用户重建，不得沿用残留值");
            assertEquals(7L, context.tenantId(), "租户必须重新解析，残留值不算数");
            assertFalse(context.bypassDataPermission(), "残留的 bypass 位不得带进本请求");
            then(tenantContextResolver).should().resolve(user, request);
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