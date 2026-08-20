package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

/**
 * <h1>ColumnMaskingService 单元测试</h1>
 *
 * <p>测试脱敏策略：FULL（完全遮蔽）、PARTIAL（部分遮蔽）、REPLACE（替换表达式）。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
class ColumnMaskingServiceTest {

    @Mock
    private DataResourceRepository resourceRepository;

    @Mock
    private DataFieldPolicyRepository fieldPolicyRepository;

    private ColumnMaskingService maskingService;

    private PermissionContext context;

    @BeforeEach
    void setUp() {
        maskingService = new ColumnMaskingService(resourceRepository, fieldPolicyRepository);
        context = new PermissionContext(
                1L, 100L, Set.of(1L), Set.of(), Set.of(), Set.of("planner"), false
        );
    }

    @Nested
    class ResourceNotFound {
        @Test
        void returnsOriginalValueWhenResourceNotFound() {
            String value = "12345.67";
            Object result = maskingService.maskFieldValue(
                    "unknown.resource", "price", value, context
            );
            assertEquals(value, result, "资源未注册时应返回原值");
        }
    }

    @Nested
    class FullMasking {
        @Test
        void masksValueWithStars() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("FULL")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", 29999D, context
            );
            assertEquals("****", result, "FULL 脱敏应返回星号");
        }
    }

    @Nested
    class PartialMasking {
        @Test
        void preservesFirstAndLastChar() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.purchasePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("PARTIAL")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.purchasePrice", "price", "28000.0", context
            );
            String str = (String) result;
            assertNotNull(str);
            assertTrue(str.startsWith("2"), "PARTIAL 脱敏应保留首字符");
            assertTrue(str.endsWith("0"), "PARTIAL 脱敏应保留尾字符");
            assertTrue(str.contains("*"), "PARTIAL 脱敏中间应有星号");
        }

        @Test
        void shortStringBecomesDoubleStar() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.purchasePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("PARTIAL")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.purchasePrice", "price", "12", context
            );
            assertEquals("**", result, "长度≤2 的字符串 PARTIAL 脱敏应返回 **");
        }
    }

    @Nested
    class ReplaceMasking {
        @Test
        void replacesWithExpression() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("REPLACE")
                    .setMaskExpression("***")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", 29999D, context
            );
            assertEquals("***", result, "REPLACE 脱敏应返回指定表达式");
        }

        @Test
        void fallsBackToStarsWhenNoExpression() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("REPLACE")
                    .setMaskExpression(null)
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", 29999D, context
            );
            assertEquals("****", result, "REPLACE 无表达式时应回退到星号");
        }
    }

    @Nested
    class AllowPlaintext {
        @Test
        void returnsOriginalValueWhenAllowed() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setAllowPlaintext(true)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Double original = 29999D;
            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", original, context
            );
            assertEquals(original, result, "allowPlaintext=true 时应返回原值");
        }
    }

    @Nested
    class NullValue {
        @Test
        void returnsNullWhenInputIsNull() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("planner")
                    .setMaskType("FULL")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", null, context
            );
            assertNull(result, "null 输入应返回 null");
        }
    }

    @Nested
    class NoPolicies {
        @Test
        void returnsOriginalWhenNoPoliciesConfigured() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);
            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of());

            Double original = 29999D;
            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", original, context
            );
            assertEquals(original, result, "无策略时应返回原值");
        }
    }

    @Nested
    class RoleMismatch {
        @Test
        void skipsPolicyWhenRoleDoesNotMatch() {
            DataResourceEntity resource = new DataResourceEntity().setId(1L);
            given(resourceRepository.getByResourceKey("channel.salePrice")).willReturn(resource);

            // 策略要求 "admin" 角色，但上下文只有 "planner"
            DataFieldPolicyEntity policy = new DataFieldPolicyEntity()
                    .setFieldName("price")
                    .setRoleCode("admin")
                    .setMaskType("FULL")
                    .setAllowPlaintext(false)
                    .setPriority(100)
                    .setEnabled(true);

            given(fieldPolicyRepository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(1L, "QUERY"))
                    .willReturn(List.of(policy));

            Double original = 29999D;
            Object result = maskingService.maskFieldValue(
                    "channel.salePrice", "price", original, context
            );
            assertEquals(original, result, "角色不匹配时应返回原值");
        }
    }
}