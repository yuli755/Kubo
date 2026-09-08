package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>维度开关驱动的拦截 + 掩码回写防护</h1>
 *
 * <p>本类只测"判定"：<b>工厂维度什么时候拦、什么时候放，以及什么算脱敏占位值</b>。
 * 处置（恢复原值 / 拒绝新增）在 {@link SensitiveWriteProtectionTest} 里测，
 * 因为处置需要实体与 Jackson，混在一起会让这两份断言都变得难以读。</p>
 *
 * <p>工厂维度在 D13-B 已落地（默认可用），所以这里两档都要测：维度不可用时拦截、
 * 落地后放行。{@code DimensionGuard.isFactoryAvailable()} 读的是
 * {@code DataPermissionRuntime.properties()} 静态句柄（构造即赋值，跨用例共享），
 * 因此每个用例都显式重建句柄，不依赖上一个用例留下的状态（与
 * {@link ExportColumnMaskerTest} 同一套约定）。</p>
 *
 * @author Hamm.cn
 */
class DimensionAndMaskedWriteGuardTest {

    @Test
    void factorySubjectRejectedOnlyWhenDimensionUnavailable() {
        // 配置句柄为 null 时必须倒向"不可用"（fail-fast 的默认方向：信息不足不放行）
        new DataPermissionRuntime(null, null, null);
        assertFalse(DimensionGuard.isFactoryAvailable(), "无配置句柄时必须倒向不可用");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("FACTORY"), "维度不可用时 FACTORY 策略必须被拒");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("factory"), "大小写不敏感");
        assertNull(DimensionGuard.rejectUnavailableSubject("DEPARTMENT"), "其它维度不受工厂开关影响");
        assertNull(DimensionGuard.rejectUnavailableSubject("TENANT"));
        assertNull(DimensionGuard.rejectUnavailableSubject(null));

        // D13-B 起维度落地（默认 true）：FACTORY 不再被拒
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setFactoryDimensionEnabled(true);
        new DataPermissionRuntime(null, null, properties);
        assertTrue(DimensionGuard.isFactoryAvailable(), "维度落地后必须可用");
        assertNull(DimensionGuard.rejectUnavailableSubject("FACTORY"), "维度落地后 FACTORY 策略放行");
    }

    @Test
    void factoryIdsInConditionTreeRejectedOnlyWhenDimensionUnavailable() {
        new DataPermissionRuntime(null, null, null);
        String reason = DimensionGuard.rejectUnavailableCondition(
                "{\"op\":\"IN\",\"field\":\"factoryIds\",\"value\":[1,2]}");
        assertNotNull(reason, "维度不可用时条件树引用恒空变量会被渲染成恒假条件（全员空结果），必须拒");
        assertTrue(reason.contains("departmentIds"), "报错要给可用的替代维度");
        assertNull(DimensionGuard.rejectUnavailableCondition("{}"));
        assertNull(DimensionGuard.rejectUnavailableCondition(null));
        assertNull(DimensionGuard.rejectUnavailableCondition("{\"field\":\"tenantId\"}"));

        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setFactoryDimensionEnabled(true);
        new DataPermissionRuntime(null, null, properties);
        assertNull(DimensionGuard.rejectUnavailableCondition(
                "{\"op\":\"IN\",\"field\":\"factoryIds\",\"value\":[1,2]}"), "维度落地后 factoryIds 是真实集合，不再拒绝");
    }

    /**
     * 判定口径是读侧与写侧共用的唯一真相源（{@code looksMasked} 在前端有一份对偶实现）。
     */
    @Test
    void maskedPlaceholdersAreRecognised() {
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("138****000"));
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("****"));
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("h:ab12cd34ef56"), "后端 HASH 的产出同样不能回写");
        assertTrue(MaskedWriteGuard.looksLikeMaskedPlaceholder("1******0"), "PARTIAL 的产出");
    }

    @Test
    void realValuesAreNotMistakenForMasks() {
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder("a*b"), "单个星号是真实文本");
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder("13800138000"));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(""));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(null));
        assertFalse(MaskedWriteGuard.looksLikeMaskedPlaceholder(1999.5), "数值没有星号可言");
    }
}
