package cn.hamm.spms.module.channel.purchaseprice.mapper;

import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceService;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate.FRAGMENT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>采购价的 Mapper 运行时回归</h1>
 *
 * <p>采购价无业务筛选，本类钉住：page 出口走租户条件、detail/count/stat 经过同一资源权限绑定、
 * 导出取数走同一权限链、无策略时恒假且不回退 JPA。关联主表（物料/供应商）只投影 ID。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "airpower.redis.enabled=false",
        "airpower.mqtt.enabled=false",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("PurchasePriceMapper · 真实运行时拦截器")
class PurchasePriceMapperRuntimeInterceptorTest {
    @Autowired
    private PurchasePriceMapper purchasePriceMapper;

    @Autowired
    private PurchasePriceService purchasePriceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS purchase_price ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, supplier_id BIGINT, price DOUBLE)");
        jdbcTemplate.update("DELETE FROM purchase_price");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) "
                + "VALUES (1, 10, 7, 101, 201, 12.5, TRUE)");
        jdbcTemplate.update("INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) "
                + "VALUES (2, 10, 8, 102, 202, 8.8, FALSE)");
        jdbcTemplate.update("INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) "
                + "VALUES (3, 20, 7, 103, 203, 99.9, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9801, 'channel.purchasePrice', 'purchase_price', 'purchase_price', 'tenant_id', "
                        + "'creator_id', 'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9802, 'test-purchase-price-tenant', 9801, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("采购价 page 使用统一租户条件，关联主表只投影 ID")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<PurchasePriceEntity> rows = purchasePriceMapper.selectPageByPermission(null, 0, 20);
            assertEquals(2, rows.size(), "当前租户仅两行采购价");
            PurchasePriceEntity first = rows.get(0);
            assertEquals(2L, first.getId());
            assertNotNull(first.getMaterial());
            assertEquals(102L, first.getMaterial().getId());
            assertNotNull(first.getSupplier());
            assertEquals(202L, first.getSupplier().getId());
        }
    }

    @Test
    @DisplayName("采购价 detail/count 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            PurchasePriceEntity detail = purchasePriceMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, purchasePriceMapper.countByPermission(null));
            var stat = purchasePriceMapper.statByPermission(null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<PurchasePriceEntity> rows = purchasePriceMapper.selectPageByPermission(null, 0, 20);
            assertEquals(2, rows.size());

            assertEquals(2L, purchasePriceService.getMapperCount(null));
            var serviceStat = purchasePriceService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("采购价导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<PurchasePriceEntity> rows = purchasePriceMapper.selectForExport(null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("采购价无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, purchasePriceMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(purchasePriceMapper.selectDetailByPermission(1L));
            assertEquals(0, purchasePriceMapper.selectForExport(null).size());
            assertEquals(0L, purchasePriceMapper.statByPermission(null).getTotal());
            assertEquals(0L, purchasePriceService.getMapperCount(null));
            assertEquals(0L, purchasePriceService.getMapperStat(null).getTotal());
        }
    }
}
