package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灰度状态面板的提示判据测试
 *
 * <p>这些句子讲的是"配置与策略数量之间的关系"，是管理员决定要不要放开下一个资源的依据。
 * 判据做成静态方法正是为了能在不起容器的情况下钉住它 —— 面板报错了不致命，
 * <b>面板骗人才致命</b>。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataResourceService · 灰度状态提示判据")
class DataResourceRuntimeNotesTest {

    private static DataResourceEntity resource(String status) {
        return new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus(status);
    }

    @Test
    @DisplayName("配置与策略都齐：一条提示都不给（噪音会让整个面板被忽略）")
    void cleanResourceHasNoNotes() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.ENFORCE, 3L, 2L, 0L, properties);

        assertEquals(List.of(), notes);
    }

    @Test
    @DisplayName("已进 enforce 名单却没有写侧策略：说清「保存会被直接拒」")
    void enforceWithoutWritePolicy() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.ENFORCE, 3L, 0L, 0L, properties);

        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("直接拒"), notes.toString());
    }

    @Test
    @DisplayName("还在 shadow：同一条事实要说成「放开前先补」，而不是吓人说已经坏了")
    void shadowWithoutWritePolicy() {
        DataPermissionProperties properties = new DataPermissionProperties();

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.SHADOW, 3L, 0L, 0L, properties);

        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("放开前"), notes.toString());
    }

    @Test
    @DisplayName("未启用的资源：把 deny-unregistered 的实际后果写出来")
    void disabledResourceExplainsBothDirections() {
        DataPermissionProperties allow = new DataPermissionProperties();
        List<String> allowNotes = DataResourceService.runtimeNotes(
                resource("DRAFT"), DataPermissionProperties.Mode.SHADOW, 1L, 1L, 0L, allow);
        assertEquals(1, allowNotes.size());
        assertTrue(allowNotes.get(0).contains("会放行"), allowNotes.toString());

        DataPermissionProperties deny = new DataPermissionProperties();
        deny.setDenyUnregistered(true);
        List<String> denyNotes = DataResourceService.runtimeNotes(
                resource("DRAFT"), DataPermissionProperties.Mode.SHADOW, 1L, 1L, 0L, deny);
        assertTrue(denyNotes.get(0).contains("会拒"), denyNotes.toString());
    }

    @Test
    @DisplayName("deny-unregistered=true 且没有 QUERY 策略：预告「查空」而不是「报错」")
    void denyUnregisteredWithoutQueryPolicy() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setDenyUnregistered(true);

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.ENFORCE, 0L, 1L, 0L, properties);

        assertTrue(notes.stream().anyMatch(n -> n.contains("查空")), notes.toString());
    }

    @Test
    @DisplayName("全局已经是 enforce 时，名单是「例外清单」：出现在名单里不代表变化")
    void listSemanticsUnderGlobalEnforce() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        properties.setEnforceResources(new java.util.LinkedHashSet<>(List.of("wms.input")));

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.ENFORCE, 3L, 2L, 0L, properties);

        assertTrue(notes.stream().anyMatch(n -> n.contains("例外清单")), notes.toString());
    }

    @Test
    @DisplayName("绑定风险条数要如实报出来：enforce 下它等于「查空」")
    void bindingRisksAreSurfaced() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);

        List<String> notes = DataResourceService.runtimeNotes(
                resource("ENABLED"), DataPermissionProperties.Mode.ENFORCE, 3L, 2L, 4L, properties);

        assertTrue(notes.stream().anyMatch(n -> n.contains("4 条")), notes.toString());
    }

    @Test
    @DisplayName("资源键为 null 时不抛（脏行也不该让面板整块打不开）")
    void nullResourceKeyIsSafe() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setEnforceResources(new java.util.LinkedHashSet<>(List.of("wms.input")));
        DataResourceEntity broken = new DataResourceEntity().setStatus("ENABLED");

        List<String> notes = DataResourceService.runtimeNotes(
                broken, DataPermissionProperties.Mode.SHADOW, 0L, 0L, 0L, properties);

        // 只该有「尚无 UPDATE/DELETE 策略」这一条：名单判定被 null 挡住，不该冒出「例外清单」那种话
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).contains("放开前"), notes.toString());
    }
}
