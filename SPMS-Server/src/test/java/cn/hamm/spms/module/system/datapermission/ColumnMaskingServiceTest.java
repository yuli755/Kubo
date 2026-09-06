package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

/**
 * <h1>ColumnMaskingService 单元测试</h1>
 *
 * <p>测试脱敏策略：FULL / PARTIAL / REPLACE / LAST4 / HASH，以及"未知类型安全失败"。</p>
 *
 * <p><b>依赖已换成 {@link DataFieldPolicyProvider}</b>：列级脱敏在序列化层被逐行逐字段调用，
 * 直接打 Repository 会把列表接口压垮；provider 的缓存键只有 {@code resourceKey + actionName}，
 * 因此角色命中与优先级选择必须由 {@code ColumnMaskingService} 在内存里完成 ——
 * 下面 {@code CacheIsIdentityFree} 就是钉住这件事的回归测试（当年行权限缓存串号就是
 * 把身份判断写进了被缓存的方法里）。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class ColumnMaskingServiceTest {

    @Mock
    private DataFieldPolicyProvider fieldPolicyProvider;

    private ColumnMaskingService maskingService;

    private PermissionContext context;

    @BeforeEach
    void setUp() {
        maskingService = new ColumnMaskingService(fieldPolicyProvider);
        context = new PermissionContext(
                1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
        );
    }

    /**
     * 让 provider 返回给定策略（等价于"该资源在 QUERY 动作下配了这些策略"）
     */
    private void givenPolicies(String resourceKey, DataFieldPolicyEntity... policies) {
        given(fieldPolicyProvider.getEnabledPoliciesByResourceAndAction(resourceKey, "QUERY"))
                .willReturn(policies.length == 0 ? List.of() : Arrays.asList(policies));
    }

    /**
     * 默认针对 price 字段（列表/详情的主战场）
     */
    private DataFieldPolicyEntity policy(String roleCode, String maskType, String expression, boolean allowPlaintext) {
        return policy("price", roleCode, maskType, expression, allowPlaintext);
    }

    /**
     * 注意 fieldName 必须与 maskFieldValue 传的字段名一致：
     * getEffectiveMaskPolicy 会先按字段过滤，对不上就等于"没配策略"（原值输出）
     */
    private DataFieldPolicyEntity policy(String fieldName, String roleCode, String maskType, String expression, boolean allowPlaintext) {
        return new DataFieldPolicyEntity()
                .setFieldName(fieldName)
                .setRoleCode(roleCode)
                .setMaskType(maskType)
                .setMaskExpression(expression)
                .setAllowPlaintext(allowPlaintext)
                .setPriority(100)
                .setEnabled(true);
    }

    @Nested
    class ResourceNotFound {
        @Test
        void returnsOriginalValueWhenResourceNotFound() {
            givenPolicies("unknown.resource");
            String value = "12345.67";
            Object result = maskingService.maskFieldValue("unknown.resource", "price", value, context);
            assertEquals(value, result, "资源未注册时应返回原值");
        }
    }

    @Nested
    class FullMasking {
        @Test
        void masksValueWithStars() {
            givenPolicies("channel.salePrice", policy("planner", "FULL", null, false));
            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "FULL 脱敏应返回星号");
        }

        @Test
        void ignoresOriginalValueCompletely() {
            givenPolicies("channel.salePrice", policy("planner", "FULL", "受限", false));
            // FULL 刻意忽略原值（含长度），避免从星号个数反推价格位数
            assertEquals("受限", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context));
        }
    }

    @Nested
    class PartialMasking {
        @Test
        void preservesFirstAndLastChar() {
            givenPolicies("channel.purchasePrice", policy("planner", "PARTIAL", null, false));
            String result = (String) maskingService.maskFieldValue("channel.purchasePrice", "price", "28000.0", context);
            assertTrue(result.startsWith("2"), "PARTIAL 脱敏应保留首字符");
            assertTrue(result.endsWith("0"), "PARTIAL 脱敏应保留尾字符");
            assertTrue(result.contains("*"), "PARTIAL 脱敏中间应有星号");
        }

        @Test
        void shortStringBecomesDoubleStar() {
            givenPolicies("channel.purchasePrice", policy("planner", "PARTIAL", null, false));
            assertEquals("**", maskingService.maskFieldValue("channel.purchasePrice", "price", "12", context),
                    "长度≤2 的字符串 PARTIAL 脱敏应返回 **");
        }

        @Test
        void starCountCappedAtSix() {
            givenPolicies("channel.purchasePrice", policy("planner", "PARTIAL", null, false));
            assertEquals("1******0", maskingService.maskFieldValue("channel.purchasePrice", "price", "13800138000", context),
                    "星号数量封顶 6，长字段不泄漏长度信息");
        }
    }

    @Nested
    class ReplaceMasking {
        @Test
        void replacesWithExpression() {
            givenPolicies("channel.salePrice", policy("planner", "REPLACE", "***", false));
            assertEquals("***", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "REPLACE 脱敏应返回指定表达式");
        }

        @Test
        void fallsBackToStarsWhenNoExpression() {
            givenPolicies("channel.salePrice", policy("planner", "REPLACE", null, false));
            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "REPLACE 无表达式时应回退到星号");
        }
    }

    @Nested
    class Last4AndHash {
        @Test
        void last4KeepsOnlyTailFour() {
            givenPolicies("personnel.user", policy("phone", "planner", "LAST4", null, false));
            assertEquals("*******8000", maskingService.maskFieldValue("personnel.user", "phone", "13800138000", context));
        }

        @Test
        void last4FallsBackWhenTooShort() {
            givenPolicies("personnel.user", policy("phone", "planner", "LAST4", "不可见", false));
            assertEquals("不可见", maskingService.maskFieldValue("personnel.user", "phone", "1234", context),
                    "长度不足 4 时不能把原值当尾四位输出");
        }

        @Test
        void hashOutputsIrreversibleDigestPrefix() {
            givenPolicies("personnel.user", policy("idCard", "planner", "HASH", null, false));
            Object first = maskingService.maskFieldValue("personnel.user", "idCard", "110101199001011234", context);
            Object second = maskingService.maskFieldValue("personnel.user", "idCard", "110101199001011234", context);
            assertTrue(((String) first).startsWith("h:"), "HASH 结果带 h: 前缀");
            assertEquals(((String) first).length(), 14, "h: + 12 位十六进制");
            assertEquals(first, second, "同值同码：可以比对但不能还原");
            assertNotEquals("110101199001011234", first);
        }

        @Test
        void unknownTypeFailsClosed() {
            givenPolicies("channel.salePrice", policy("planner", "TELEPATHY", null, false));
            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "未配置或配置了未知类型：安全失败，绝不回退原值");
        }
    }

    @Nested
    class AllowPlaintext {
        @Test
        void returnsOriginalValueWhenAllowed() {
            givenPolicies("channel.salePrice", policy("planner", "FULL", null, true));
            Double original = 29999D;
            assertEquals(original, maskingService.maskFieldValue("channel.salePrice", "price", original, context),
                    "allowPlaintext=true 时应返回原值");
        }
    }

    @Nested
    class NullValue {
        @Test
        void returnsNullWhenInputIsNull() {
            givenPolicies("channel.salePrice", policy("planner", "FULL", null, false));
            assertNull(maskingService.maskFieldValue("channel.salePrice", "price", null, context),
                    "null 输入应返回 null");
        }
    }

    @Nested
    class NoPolicies {
        @Test
        void returnsOriginalWhenNoPoliciesConfigured() {
            givenPolicies("channel.salePrice");
            Double original = 29999D;
            assertEquals(original, maskingService.maskFieldValue("channel.salePrice", "price", original, context),
                    "无策略时应返回原值");
        }
    }

    @Nested
    class RoleMismatch {
        @Test
        void skipsPolicyWhenRoleDoesNotMatch() {
            givenPolicies("channel.salePrice", policy("admin", "FULL", null, false));
            Double original = 29999D;
            assertEquals(original, maskingService.maskFieldValue("channel.salePrice", "price", original, context),
                    "角色不匹配时应返回原值");
        }

        @Test
        void defaultRoleWildcardAppliesToEveryone() {
            givenPolicies("channel.salePrice", policy("default", "FULL", null, false));
            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "roleCode=default 是通配兜底");
        }
    }

    @Nested
    class CacheIsIdentityFree {
        /**
         * 同一份（被缓存的）策略列表，必须按调用者身份解析出不同结论。
         *
         * <p>如果有人把角色判断挪进 {@code @Cacheable} 的方法内部，第一个用户的结论就会被
         * 所有后续用户命中 —— 这条测试会先炸。</p>
         */
        @Test
        void resolvesRoleSpecificPolicyFromSharedPolicyList() {
            givenPolicies("channel.salePrice",
                    policy("planner", "FULL", null, false),
                    policy("finance", "REPLACE", "仅限财务", false));

            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context));

            PermissionContext financeContext = new PermissionContext(
                    2L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("finance"), false
            );
            assertEquals("仅限财务",
                    maskingService.maskFieldValue("channel.salePrice", "price", 29999D, financeContext),
                    "另一个角色在同一次缓存结果上得到不同结论");
        }

        @Test
        void higherPriorityPolicyWinsForSameRole() {
            DataFieldPolicyEntity low = policy("planner", "PARTIAL", null, false);
            low.setPriority(10);
            givenPolicies("channel.salePrice", low, policy("planner", "FULL", null, false));
            assertEquals("****", maskingService.maskFieldValue("channel.salePrice", "price", 29999D, context),
                    "同角色多条策略时取 priority 最大的那条");
        }
    }
}
