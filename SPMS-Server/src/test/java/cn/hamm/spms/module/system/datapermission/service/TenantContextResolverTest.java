package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.system.datapermission.SystemPermissionIdentities;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.tenant.UserTenantService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;

/**
 * <h1>租户上下文解析测试（决策 D1 的核心安全断言）</h1>
 *
 * <p>这组用例锁的是「租户 ID 不可被前端左右」这一条：
 * 请求头只能表达意图，必须经服务端成员关系校验；校验不过就拒绝，
 * <b>不允许</b>像旧实现那样回落到某个默认值继续跑。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class TenantContextResolverTest {

    @Mock
    private UserTenantService userTenantService;

    @Mock
    private SystemPermissionService systemPermissionService;

    @Mock
    private HttpServletRequest request;

    private DataPermissionProperties properties;
    private TenantContextResolver resolver;

    private static UserEntity normalUser(long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        return user;
    }

    @BeforeEach
    void setUp() {
        properties = new DataPermissionProperties();
        properties.setDefaultTenantId(1L);
        properties.setTenantRequired(false);
        resolver = new TenantContextResolver(userTenantService, properties, systemPermissionService);
        lenient().when(systemPermissionService.has(any(UserEntity.class), anyString())).thenReturn(false);
    }

    @Nested
    @DisplayName("无请求头：取服务端归属结果")
    class WithoutHeader {

        @Test
        @DisplayName("有归属 → 使用用户默认租户")
        void usesDefaultTenantWhenHeaderMissing() {
            UserEntity user = normalUser(7L);
            given(userTenantService.defaultTenantId(7L)).willReturn(42L);
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn(null);

            assertEquals(42L, resolver.resolve(user, request));
        }

        @Test
        @DisplayName("无归属 + tenant-required=false → 回落平台默认租户并继续（迁移回填期行为）")
        void fallsBackToPlatformDefaultBeforeBackfill() {
            UserEntity user = normalUser(7L);
            given(userTenantService.tenantIds(7L)).willReturn(Set.of());
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn(null);

            assertEquals(1L, resolver.resolve(user, request));
        }

        @Test
        @DisplayName("无归属 + tenant-required=true → 拒绝（回填完成后的生产语义）")
        void rejectsWhenMembershipRequiredAndEmpty() {
            properties.setTenantRequired(true);
            UserEntity user = normalUser(7L);
            given(userTenantService.tenantIds(7L)).willReturn(Set.of());
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn(null);

            assertThrows(RuntimeException.class, () -> resolver.resolve(user, request));
        }
    }

    @Nested
    @DisplayName("带请求头：只做候选，必须校验")
    class WithHeader {

        @Test
        @DisplayName("头里的租户属于该用户 → 采纳（等价于切换租户）")
        void acceptsOwnedTenant() {
            UserEntity user = normalUser(7L);
            given(userTenantService.tenantIds(7L)).willReturn(Set.of(5L, 6L));
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn("6");

            assertEquals(6L, resolver.resolve(user, request));
            // 采纳了显式意图，就不该再去查默认租户
            then(userTenantService).should(never()).defaultTenantId(anyLong());
        }

        @Test
        @DisplayName("伪造别人的租户 → 拒绝，而不是回落")
        void rejectsForgedTenant() {
            UserEntity user = normalUser(7L);
            given(userTenantService.tenantIds(7L)).willReturn(Set.of(5L));
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn("999999");

            RuntimeException ex = assertThrows(RuntimeException.class, () -> resolver.resolve(user, request));
            assertEquals(true, ex.getMessage() != null && ex.getMessage().contains("999999"),
                    "拒绝原因应包含被拒绝的租户标识，便于排障");
        }

        @Test
        @DisplayName("非法租户头（非数字 / <=0）→ 拒绝，而不是忽略")
        void rejectsMalformedHeader() {
            UserEntity user = normalUser(7L);
            given(userTenantService.tenantIds(7L)).willReturn(Set.of(5L));
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn("abc;drop");

            assertThrows(RuntimeException.class, () -> resolver.resolve(user, request));

            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn("-3");
            assertThrows(RuntimeException.class, () -> resolver.resolve(user, request));
        }

        @Test
        @DisplayName("持有 bypass 权限的系统账号可跨租户，但必须留痕")
        void allowsPrivilegedCrossTenantAccess() {
            UserEntity user = normalUser(9L);
            given(userTenantService.tenantIds(9L)).willReturn(Set.of(5L));
            given(systemPermissionService.has(eq(user), eq(SystemPermissionIdentities.DATA_PERMISSION_BYPASS))).willReturn(true);
            given(request.getHeader(TenantContextResolver.TENANT_HEADER)).willReturn("77");

            assertEquals(77L, resolver.resolve(user, request));
        }
    }
}
