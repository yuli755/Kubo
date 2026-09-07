package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService.ReadinessCheck;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService.ReadinessInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「能不能放开这个资源」的前置检查判据测试
 *
 * <p>放开名单是全流程里唯一<em>一次改配置就能把生产打坏</em>的动作：少配一项是"以为有防护"，
 * 多配一项是"保存报错 / 列表查空"。判据做成静态方法，是为了不起容器也能把这些边界钉住 ——
 * 面板与检查报错了不致命，<b>骗人才致命</b>。</p>
 *
 * <p>重点盯两条容易被"顺手改宽松"的规矩：影子证据要的是<em>正向样本</em>（有 EXPECTED_FILTER、
 * 无 ANOMALY/ERROR），"没有差异记录"和"读不到差异表"都<em>不等于</em>安全。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("DataResourceService · 放开前置检查判据")
class DataResourceReadinessTest {

    private static DataResourceEntity enabledResource() {
        return new DataResourceEntity().setId(1L).setResourceKey("wms.input").setStatus("ENABLED");
    }

    private static DataPermissionProperties enforce() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setMode(DataPermissionProperties.Mode.ENFORCE);
        return properties;
    }

    /**
     * 一个"什么都没缺"的输入：影子有正向样本、策略齐、维度能绑
     */
    private static ReadinessInput ready(long query, long write, long binding, boolean exportGap,
                                        boolean tableAvailable, long anomalies, long filtered) {
        return new ReadinessInput(enabledResource(), DataPermissionProperties.Mode.ENFORCE,
                query, write, binding, exportGap, tableAvailable, anomalies, filtered);
    }

    private static ReadinessInput allGreen() {
        return ready(3L, 2L, 0L, false, true, 0L, 7L);
    }

    private static String detailOf(List<ReadinessCheck> checks, String code) {
        return checks.stream().filter(c -> code.equals(c.code())).map(ReadinessCheck::detail).findFirst().orElse("");
    }

    @Test
    @DisplayName("策略齐、维度能绑、影子有正向样本：可以放开")
    void greenLight() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(allGreen(), enforce());

        assertTrue(DataResourceService.readyOf(checks), checks.toString());
        assertEquals(0, DataResourceService.blockersOf(checks).size(), checks.toString());
    }

    @Test
    @DisplayName("差异全为「没有差异」：不能放开——0 差异也可能意味着该主体根本没被过滤")
    void consistentIsNotEnough() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 2L, 0L, false, true, 0L, 0L), enforce());

        ReadinessCheck shadow = checks.stream()
                .filter(c -> "SHADOW_EVIDENCE".equals(c.code())).findFirst().orElseThrow();
        assertFalse(shadow.passed());
        // 文案必须点明"要的是少行样本"，否则下一个人会把它改成「差异为 0 就通过」
        assertTrue(shadow.detail().contains("EXPECTED_FILTER"), shadow.detail());
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("有 ANOMALY 样本：不放开，并把条数报出来")
    void anomalyBlocks() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 2L, 0L, false, true, 2L, 7L), enforce());

        assertTrue(detailOf(checks, "SHADOW_EVIDENCE").contains("2"), detailOf(checks, "SHADOW_EVIDENCE"));
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("读不到差异表：按「无证据」处理，不得当作「没有差异」")
    void missingDiffTableIsNotClean() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 2L, 0L, false, false, 0L, 0L), enforce());

        String detail = detailOf(checks, "SHADOW_EVIDENCE");
        assertTrue(detail.contains("不能视为"), detail);
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("查询侧一条策略都没有：放开只是换个假绿灯")
    void noQueryPolicy() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(0L, 2L, 0L, false, true, 0L, 7L), enforce());

        assertFalse(checks.stream().anyMatch(c -> "QUERY_POLICY".equals(c.code()) && c.passed()));
        assertTrue(DataResourceService.blockersOf(checks).stream()
                .anyMatch(text -> text.contains("假绿灯")), DataResourceService.blockersOf(checks).toString());
    }

    @Test
    @DisplayName("没有 UPDATE/DELETE 策略：点名「保存与删除会被守卫拒掉」")
    void noWritePolicy() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 0L, 0L, false, true, 0L, 7L), enforce());

        String detail = detailOf(checks, "WRITE_POLICY");
        assertTrue(detail.contains("DataPermissionGuard"), detail);
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("维度绑不上列：报条数，并指出去哪一格清零")
    void unbindableDimension() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 2L, 2L, false, true, 0L, 7L), enforce());

        String detail = detailOf(checks, "DIMENSIONS_BINDABLE");
        assertTrue(detail.contains("2 条"), detail);
        assertTrue(detail.contains("恒假"), detail);
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("导出缺明文授权：提醒但不阻塞（enforce 下整份拒绝是 fail-closed 的预期）")
    void exportGapWarnsButDoesNotBlock() {
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                ready(3L, 2L, 0L, true, true, 0L, 7L), enforce());

        ReadinessCheck export = checks.stream()
                .filter(c -> "EXPORT_GRANT".equals(c.code())).findFirst().orElseThrow();
        assertFalse(export.passed());
        assertFalse(export.blocking(), "导出缺口只应提醒，放开它不该被这条挡住");
        assertFalse(DataResourceService.blockersOf(checks).stream()
                .anyMatch(text -> text.contains("EXPORT")), DataResourceService.blockersOf(checks).toString());
        assertTrue(DataResourceService.readyOf(checks), checks.toString());
    }

    @Test
    @DisplayName("资源没登记或已停用：第一条阻塞就是它，别让人去查策略")
    void unregisteredResource() {
        ReadinessInput input = new ReadinessInput(
                new DataResourceEntity().setResourceKey("wms.input").setStatus("MISSING"),
                DataPermissionProperties.Mode.ENFORCE, 3L, 2L, 0L, false, true, 0L, 7L);

        List<ReadinessCheck> checks = DataResourceService.readinessChecks(input, enforce());

        assertEquals("REGISTERED_ENABLED", checks.get(0).code());
        assertFalse(checks.get(0).passed());
        assertTrue(DataResourceService.blockersOf(checks).get(0).contains("未登记"),
                DataResourceService.blockersOf(checks).toString());
    }

    @Test
    @DisplayName("灰度提示非空即视为未就绪：面板与检查用同一份判据，不许两头说法不一")
    void notesReuseKeepsPanelAndCheckerConsistent() {
        // shadow 档 + 没有写侧策略：runtimeNotes 会给出「放开前必须先补」
        DataPermissionProperties properties = new DataPermissionProperties();
        List<ReadinessCheck> checks = DataResourceService.readinessChecks(
                new ReadinessInput(enabledResource(), DataPermissionProperties.Mode.SHADOW,
                        3L, 0L, 0L, false, true, 0L, 7L), properties);

        String detail = detailOf(checks, "CONFIG_NOTES");
        assertTrue(detail.contains("放开前必须先补"), detail);
        assertFalse(DataResourceService.readyOf(checks));
    }

    @Test
    @DisplayName("检查项顺序固定：新增判据不许插到中间（面板与测试都按位置认）")
    void checkOrderIsStable() {
        List<String> codes = DataResourceService.readinessChecks(allGreen(), enforce())
                .stream().map(ReadinessCheck::code).toList();

        assertEquals(List.of("REGISTERED_ENABLED", "DIMENSIONS_BINDABLE", "QUERY_POLICY", "WRITE_POLICY",
                "EXPORT_GRANT", "SHADOW_EVIDENCE", "CONFIG_NOTES"), codes);
    }

    @Test
    @DisplayName("一项都没有时不许默认通过（records 空表意味着判据没跑，不是全绿）")
    void emptyChecksAreNotReady() {
        assertFalse(DataResourceService.readyOf(List.of()));
        assertFalse(DataResourceService.readyOf(null));
        assertEquals(List.of(), DataResourceService.blockersOf(List.of()));
    }
}
