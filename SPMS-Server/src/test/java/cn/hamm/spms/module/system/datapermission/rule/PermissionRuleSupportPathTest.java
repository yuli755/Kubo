package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * <h1>登记名 → JPA 路径的候选名规则</h1>
 *
 * <p>这里钉的是"列名登记法"与"属性名登记法"都必须能绑上条件。它之所以值得单独立一个文件：
 * {@code data_resource.tenant_column} 的缺省值就是列名风格的 {@code tenant_id}，而运营看到的是
 * 数据库里的列，几乎必然按列名填 —— 填对了名字却静默不生效，是这套配置最容易踩的空转。</p>
 *
 * @author Hamm.cn
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionRuleSupport · 列名与属性名都要能解析")
class PermissionRuleSupportPathTest {

    @Mock
    private Root<Object> root;

    @Mock
    private Path<Object> path;

    @Test
    @DisplayName("登记列名 → 候选名是 原名 + camelCase")
    void columnStyleProducesCamelCandidate() {
        assertEquals(List.of("tenant_id", "tenantId"), PermissionRuleSupport.pathCandidates("tenant_id"));
    }

    @Test
    @DisplayName("登记属性名 → 候选名是 原名 + snake_case（反方向也要能救）")
    void attributeStyleProducesSnakeCandidate() {
        assertEquals(List.of("tenantId", "tenant_id"), PermissionRuleSupport.pathCandidates("tenantId"));
    }

    @Test
    @DisplayName("空白与 null 输入 → 不抛异常，候选名去空白且不重复")
    void blankInputsAreSafe() {
        assertEquals(List.of(), PermissionRuleSupport.pathCandidates(null));
        assertEquals(List.of(), PermissionRuleSupport.pathCandidates("   "));
        assertEquals(List.of("creator_id", "creatorId"), PermissionRuleSupport.pathCandidates("  creator_id  "));
        assertEquals(List.of("id"), PermissionRuleSupport.pathCandidates("id"));
    }

    @Test
    @DisplayName("原名取不到但 camel 取得到 → 返回 camel 的路径")
    void fallsBackToCamelWhenColumnStyleMissing() {
        given(root.get("tenant_id")).willThrow(new IllegalArgumentException("Unable to locate Attribute [tenant_id]"));
        given(root.get("tenantId")).willReturn(path);

        assertSame(path, PermissionRuleSupport.findPath(root, "tenant_id"));
    }

    @Test
    @DisplayName("两种写法都取不到 → 返回 null（由调用方决定报错还是计入绑定失败）")
    void returnsNullWhenNothingResolves() {
        lenient().when(root.get(anyString())).thenThrow(new IllegalArgumentException("Unable to locate Attribute"));

        assertNull(PermissionRuleSupport.findPath(root, "warehouse_id"));
    }

    @Test
    @DisplayName("空登记名 → 直接 null，不去调 root.get")
    void blankFieldShortCircuits() {
        assertNull(PermissionRuleSupport.findPath(root, "  "));
        verifyNoInteractions(root);
    }
}
