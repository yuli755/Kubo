package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>工厂维度就绪度校验测试（决策 D13 的"开开关前先证明"）</h1>
 *
 * <p>这些用例守的不是"功能对不对"，而是<b>一句配置能不能被安全地打开</b>：
 * {@code factory-dimension-enabled=true} 曾经只是让 {@code DimensionGuard} 放行 FACTORY 策略，
 * 而 {@code factoryIds} 仍恒空、业务实体也没有工厂列 ⇒ 打开后得到的是"假安全"或"全员空结果"。
 * 就绪度校验把这两种结果挡在启动期，所以它的判定表必须被钉住：
 * 太松等于没有，太紧会让人直接放弃接工厂维度（回退成 false 并忘掉这件事）。</p>
 *
 * @author Hamm.cn
 */
@DisplayName("FactoryDimensionReadiness · D13 开关的就绪度校验")
class FactoryDimensionReadinessTest {

    /**
     * 关系已接好的用户实体（字段名走约定 {@code factoryList}）
     */
    @Table(name = "user")
    static class ReadyUserEntity {
        @SuppressWarnings("unused")
        private Set<Long> factoryList;
    }

    /**
     * 没有任何工厂关系字段的用户实体
     */
    @Table(name = "user")
    static class BareUserEntity {
        @SuppressWarnings("unused")
        private Set<Long> storageList;
    }

    @Table(name = "inventory")
    static class InventoryWithFactory {
        @SuppressWarnings("unused")
        private Long factoryId;
    }

    /**
     * 字段名不叫 factoryId，但 {@code @Column(name = "factory_id")} 指到同一列 ——
     * 归属列的物理名才是判定依据，字段名只是本地约定，别把门禁做成拼写检查
     */
    @Table(name = "material")
    static class MaterialWithRenamedColumn {
        @SuppressWarnings("unused")
        @Column(name = "factory_id")
        private Long factoryRef;
    }

    @Table(name = "purchase")
    static class PurchaseWithoutFactory {
        @SuppressWarnings("unused")
        private Long tenantId;
    }

    private static DataResourceEntity resource(String key, String table, String ownerColumns) {
        return new DataResourceEntity().setResourceKey(key).setTableName(table).setOwnerColumns(ownerColumns);
    }

    @Test
    @DisplayName("开关关着 → 一行代码都不执行（默认态必须零成本）")
    void disabledFlagShortCircuits() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setFactoryDimensionEnabled(false);
        // 故意不给 repository 与元模型：关着的时候连依赖都不该被要求
        FactoryDimensionReadiness readiness = new FactoryDimensionReadiness(properties, null, null);

        assertDoesNotThrow(readiness::verifyOnStartup);
    }

    @Test
    @DisplayName("关系字段 + 至少一个资源有工厂列 → 就绪")
    void readyWhenBothLinksExist() {
        List<String> problems = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class, InventoryWithFactory.class),
                List.of(resource("wms.inventory", "inventory", null)));

        assertTrue(problems.isEmpty(), String.join(" / ", problems));
    }

    @Test
    @DisplayName("按 @Column 的物理列名认（字段改名不影响判定）")
    void recognizesColumnByPhysicalName() {
        List<String> problems = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class, MaterialWithRenamedColumn.class),
                List.of(resource("asset.material", "material", null)));

        assertTrue(problems.isEmpty(), String.join(" / ", problems));
    }

    @Test
    @DisplayName("资源在 owner_columns 里显式登记 factory_id → 视为具备")
    void ownerColumnsCountAsReady() {
        List<String> problems = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class, PurchaseWithoutFactory.class),
                List.of(resource("channel.purchase", "purchase", "creator_id, factory_id")));

        assertTrue(problems.isEmpty(), String.join(" / ", problems));
    }

    @Test
    @DisplayName("用户实体没有工厂关系 → 报出来（factoryIds 仍是恒空集合）")
    void reportsMissingUserRelation() {
        List<String> problems = FactoryDimensionReadiness.inspect(
                List.of(BareUserEntity.class, InventoryWithFactory.class),
                List.of(resource("wms.inventory", "inventory", null)));

        assertTrue(problems.stream().anyMatch(p -> p.contains("factoryIds")), String.join(" / ", problems));
    }

    @Test
    @DisplayName("所有资源都没有工厂列 → 报出来；只有一个资源有就放行（不要求全量接入）")
    void requiresAtLeastOneUsableResource() {
        List<String> none = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class, PurchaseWithoutFactory.class),
                List.of(resource("channel.purchase", "purchase", null)));
        assertTrue(none.stream().anyMatch(p -> p.contains("没有任何已注册资源")), String.join(" / ", none));

        List<String> one = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class, PurchaseWithoutFactory.class, InventoryWithFactory.class),
                List.of(
                        resource("channel.purchase", "purchase", null),
                        resource("wms.inventory", "inventory", null)));
        assertTrue(one.isEmpty(), "部分资源未接入是灰度的正常形态，不该拦启动：" + String.join(" / ", one));
    }

    @Test
    @DisplayName("元模型里找不到实体类 → 判不就绪（不许靠猜）")
    void unknownTableIsNotReady() {
        List<String> problems = FactoryDimensionReadiness.inspect(
                List.of(ReadyUserEntity.class),
                List.of(resource("wms.ghost", "ghost_table", null)));

        assertTrue(problems.stream().anyMatch(p -> p.contains("ghost_table")), String.join(" / ", problems));
    }

    @Test
    @DisplayName("没有元模型（EMF 为 null）时倒向不通过，而不是随机放行")
    void missingMetamodelFailsClosed() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setFactoryDimensionEnabled(true);
        FactoryDimensionReadiness readiness = new FactoryDimensionReadiness(properties, null, null);

        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, readiness::verifyOnStartup);
        // 消息里必须带上"缺了什么"和"怎么办"，否则运维只能回来翻源码
        assertTrue(error.getMessage().contains("factory-dimension-enabled"), error.getMessage());
        assertTrue(error.getMessage().contains("UserEntity"), error.getMessage());
    }
}
