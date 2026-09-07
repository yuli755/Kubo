package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>enforce 配置启动闸门测试</h1>
 *
 * <p>{@link DataPermissionEnforceReadiness} 把原先"面板报红、YAML 仍可照样启动"的软约束升级成
 * 启动期硬门禁。测试只喂配置与 readiness 报告，不起 Spring：这样能把名单语义和阻塞传播钉在
 * 一个没有数据库噪声的地方。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataPermissionEnforceReadiness · enforce 配置闸门")
class DataPermissionEnforceReadinessTest {

    private static DataResourceService.Readiness ready(String resourceKey) {
        return new DataResourceService.Readiness(resourceKey, "ENFORCE", true, List.of(), List.of());
    }

    private static DataResourceService.Readiness blocked(String resourceKey, String reason) {
        return new DataResourceService.Readiness(resourceKey, "ENFORCE", false,
                List.of(reason), List.of());
    }

    private static DataPermissionProperties properties(DataPermissionProperties.Mode mode,
                                                       String... resourceKeys) {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(mode);
        properties.setEnforceResources(new java.util.LinkedHashSet<>(Set.of(resourceKeys)));
        return properties;
    }

    @Test
    @DisplayName("OFF/空名单 SHADOW 档短路：默认态不访问数据库")
    void inactiveModesSkipGate() {
        assertFalse(DataPermissionEnforceReadiness.requiresValidation(
                properties(DataPermissionProperties.Mode.OFF, "missing.resource")));
        assertFalse(DataPermissionEnforceReadiness.requiresValidation(
                properties(DataPermissionProperties.Mode.SHADOW)));

        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.OFF, "missing.resource"),
                List.of(blocked("missing.resource", "故意失败")));

        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("实际 enforce 配置才触发启动期查询")
    void activeModesRequireGate() {
        assertTrue(DataPermissionEnforceReadiness.requiresValidation(
                properties(DataPermissionProperties.Mode.SHADOW, "asset.material")));
        assertTrue(DataPermissionEnforceReadiness.requiresValidation(
                properties(DataPermissionProperties.Mode.ENFORCE)));
    }

    @Test
    @DisplayName("SHADOW 且名单为空：没有资源实际进入 enforce，不触发校验")
    void shadowWithoutEarlyReleaseSkipsGate() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.SHADOW),
                List.of(blocked("asset.material", "影子证据不足")));

        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("SHADOW 名单是提前放开清单：名单内资源不 ready 就拒绝启动")
    void shadowEarlyReleaseRequiresReadiness() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.SHADOW, "asset.material"),
                List.of(blocked("asset.material", "SHADOW_EVIDENCE：没有 EXPECTED_FILTER")));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("asset.material"), problems.toString());
        assertTrue(problems.get(0).contains("EXPECTED_FILTER"), problems.toString());
    }

    @Test
    @DisplayName("名单内资源 ready：SHADOW 提前放开可以启动")
    void shadowEarlyReleasePassesWhenReady() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.SHADOW, "asset.material"),
                List.of(ready("asset.material")));

        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("ENFORCE 非空名单只校验名单内资源，名单外仍保持 SHADOW")
    void enforceListOnlyChecksListedResources() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.ENFORCE, "asset.material"),
                List.of(ready("asset.material"), blocked("wms.inventory", "名单外资源的影子问题")));

        assertEquals(List.of(), problems);
    }

    @Test
    @DisplayName("ENFORCE 空名单表示全部已登记资源：任何一个不 ready 都不能启动")
    void enforceEmptyListChecksAllRegisteredResources() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.ENFORCE),
                List.of(ready("asset.material"), blocked("wms.inventory", "ORM_PARITY：读不到对账表")));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("wms.inventory"), problems.toString());
        assertTrue(problems.get(0).contains("读不到对账表"), problems.toString());
    }

    @Test
    @DisplayName("名单包含未登记资源：不许把拼写错误当成通过")
    void unknownConfiguredResourceBlocks() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.ENFORCE, "asset.matrial"),
                List.of(ready("asset.material")));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("未登记"), problems.toString());
    }

    @Test
    @DisplayName("空资源键：不许把空值解释成全量放开")
    void blankConfiguredResourceBlocks() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.SHADOW, ""), List.of());

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("空资源键"), problems.toString());
    }

    @Test
    @DisplayName("readiness 只返回 not-ready 却不带 blocker：闸门按不完整输出拒绝")
    void incompleteReadinessOutputBlocks() {
        List<String> problems = DataPermissionEnforceReadiness.validate(
                properties(DataPermissionProperties.Mode.SHADOW, "asset.material"),
                List.of(new DataResourceService.Readiness("asset.material", "ENFORCE", false, List.of(), List.of())));

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("判据输出不完整"), problems.toString());
    }
}
