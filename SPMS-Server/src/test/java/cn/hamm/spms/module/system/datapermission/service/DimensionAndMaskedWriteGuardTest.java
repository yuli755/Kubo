package cn.hamm.spms.module.system.datapermission.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h1>未落地维度拦截 + 掩码回写防护</h1>
 *
 * <p>本类只测"判定"：<b>什么算未落地维度、什么算脱敏占位值</b>。
 * 处置（恢复原值 / 拒绝新增）在 {@link SensitiveWriteProtectionTest} 里测，
 * 因为处置需要实体与 Jackson，混在一起会让这两份断言都变得难以读。</p>
 *
 * @author Hamm.cn
 */
class DimensionAndMaskedWriteGuardTest {

    @Test
    void factorySubjectIsRejectedWhileDimensionIsUnimplemented() {
        // 无 Spring 上下文时配置句柄为 null，必须落到"不可用"这一侧（fail-fast 的默认方向）
        assertFalse(DimensionGuard.isFactoryAvailable(), "user_factory 建表前该维度必须保持不可用");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("FACTORY"), "FACTORY 策略必须被拒");
        assertNotNull(DimensionGuard.rejectUnavailableSubject("factory"), "大小写不敏感");
        assertNull(DimensionGuard.rejectUnavailableSubject("DEPARTMENT"), "已落地维度不受影响");
        assertNull(DimensionGuard.rejectUnavailableSubject("TENANT"));
        assertNull(DimensionGuard.rejectUnavailableSubject(null));
    }

    @Test
    void factoryIdsInConditionTreeIsRejected() {
        String reason = DimensionGuard.rejectUnavailableCondition(
                "{\"op\":\"IN\",\"field\":\"factoryIds\",\"value\":[1,2]}");
        assertNotNull(reason, "条件树引用恒空变量会被渲染成恒假条件（全员空结果），必须拒");
        assertTrue(reason.contains("departmentIds"), "报错要给可用的替代维度");
        assertNull(DimensionGuard.rejectUnavailableCondition("{}"));
        assertNull(DimensionGuard.rejectUnavailableCondition(null));
        assertNull(DimensionGuard.rejectUnavailableCondition("{\"field\":\"tenantId\"}"));
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
