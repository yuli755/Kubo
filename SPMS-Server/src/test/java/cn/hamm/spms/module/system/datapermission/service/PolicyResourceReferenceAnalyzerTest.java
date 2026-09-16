package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略「指向缺失 / 未启用资源」盘点的测试
 *
 * <p>判据必须与运行期同源：{@code DataPermissionService#findResource} 的
 * {@code !"ENABLED".equals(status) ? null : resource}，以及按 {@code resourceId} 精确取策略。
 * 每条用例都从这两行推出期望，不凭感觉写。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("PolicyResourceReferenceAnalyzer · 策略指向缺失/未启用资源")
class PolicyResourceReferenceAnalyzerTest {

    private static DataPolicyEntity policy(Long id, String code, Long resourceId) {
        return new DataPolicyEntity().setId(id).setPolicyCode(code).setResourceId(resourceId);
    }

    private static DataResourceEntity resource(Long id, String key, String status) {
        return new DataResourceEntity().setId(id).setResourceKey(key).setStatus(status);
    }

    private static Map<Long, DataResourceEntity> byId(DataResourceEntity... resources) {
        Map<Long, DataResourceEntity> map = new LinkedHashMap<>();
        for (DataResourceEntity resource : resources) {
            map.put(resource.getId(), resource);
        }
        return map;
    }

    @Test
    @DisplayName("空入参与空策略列表都不报")
    void emptyInputsAreSafe() {
        assertTrue(PolicyResourceReferenceAnalyzer.risks(null, Map.of()).isEmpty());
        assertTrue(PolicyResourceReferenceAnalyzer.risks(List.of(), Map.of()).isEmpty());
    }

    @Test
    @DisplayName("资源引用为空 ⇒ 孤儿")
    void nullResourceIdIsDangling() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(policy(1L, "p-null", null)), byId(resource(1L, "wms.input", "ENABLED")));
        assertEquals(1, risks.size());
        assertEquals(PolicyResourceReferenceAnalyzer.KIND_DANGLING_RESOURCE, risks.get(0).kind());
        assertEquals("p-null", risks.get(0).policyCode());
    }

    @Test
    @DisplayName("引用的资源 ID 在表里不存在 ⇒ 孤儿")
    void missingResourceIsDangling() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(policy(2L, "p-orphan", 999L)), byId(resource(1L, "wms.input", "ENABLED")));
        assertEquals(1, risks.size());
        assertEquals(PolicyResourceReferenceAnalyzer.KIND_DANGLING_RESOURCE, risks.get(0).kind());
        assertEquals(999L, risks.get(0).resourceId());
    }

    @Test
    @DisplayName("引用的资源存在但 DRAFT / DISABLED ⇒ 未启用")
    void notEnabledResourceIsReported() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(
                        policy(3L, "p-draft", 1L),
                        policy(4L, "p-disabled", 2L),
                        policy(5L, "p-ok", 3L)),
                byId(
                        resource(1L, "wms.input", "DRAFT"),
                        resource(2L, "wms.output", "DISABLED"),
                        resource(3L, "asset.material", "ENABLED")));
        assertEquals(2, risks.size());
        assertEquals("p-draft", risks.get(0).policyCode());
        assertEquals(PolicyResourceReferenceAnalyzer.KIND_RESOURCE_NOT_ENABLED, risks.get(0).kind());
        assertEquals("p-disabled", risks.get(1).policyCode());
        assertEquals(PolicyResourceReferenceAnalyzer.KIND_RESOURCE_NOT_ENABLED, risks.get(1).kind());
    }

    @Test
    @DisplayName("引用的资源存在且 ENABLED ⇒ 不报")
    void enabledResourceIsClean() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(policy(6L, "p-clean", 1L)), byId(resource(1L, "wms.input", "ENABLED")));
        assertTrue(risks.isEmpty());
    }

    @Test
    @DisplayName("资源索引缺失时按孤儿处理（null 安全，不炸）")
    void nullIndexFallsBackToDangling() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(policy(7L, "p-noindex", 1L)), null);
        assertEquals(1, risks.size());
        assertEquals(PolicyResourceReferenceAnalyzer.KIND_DANGLING_RESOURCE, risks.get(0).kind());
    }

    @Test
    @DisplayName("每条策略最多一条风险，孤儿优先于未启用（两者不会同时成立）")
    void oneRiskPerPolicy() {
        List<PolicyResourceReferenceAnalyzer.Risk> risks = PolicyResourceReferenceAnalyzer.risks(
                List.of(policy(8L, "p-mixed", 1L), policy(9L, "p-null", null)),
                byId(resource(1L, "wms.input", "DRAFT")));
        assertEquals(2, risks.size());
        assertTrue(risks.stream().noneMatch(risk -> risk.kind() == null || risk.policyCode() == null));
    }
}
