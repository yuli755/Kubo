package cn.hamm.spms.module.channel.purchase.mapper;

import cn.hamm.spms.module.channel.purchase.PurchaseEntity;
import cn.hamm.spms.module.channel.purchase.PurchaseService;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>采购单的 Mapper 运行时回归</h1>
 *
 * <p>采购单无跨资源关联主表，本类钉住：page 出口走统一筛选和租户条件、detail/count/stat 经过
 * 同一资源权限绑定、导出取数走同一权限链、无策略时恒假且不回退 JPA。</p>
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
@DisplayName("PurchaseMapper · 真实运行时拦截器")
class PurchaseMapperRuntimeInterceptorTest {
    @Autowired
    private PurchaseMapper purchaseMapper;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS purchase ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "reason VARCHAR(255), total_price DOUBLE, total_real_price DOUBLE, status INT)");
        jdbcTemplate.update("DELETE FROM purchase");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO purchase (id, tenant_id, creator_id, bill_code, reason, status, is_published) "
                + "VALUES (1, 10, 7, 'PU-001', '备货', 2, TRUE)");
        jdbcTemplate.update("INSERT INTO purchase (id, tenant_id, creator_id, bill_code, reason, status, is_published) "
                + "VALUES (2, 10, 8, 'PU-002', '急单', 3, FALSE)");
        jdbcTemplate.update("INSERT INTO purchase (id, tenant_id, creator_id, bill_code, reason, status, is_published) "
                + "VALUES (3, 20, 7, 'PU-003', '备货', 2, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9401, 'channel.purchase', 'purchase', 'purchase', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9402, 'test-purchase-tenant', 9401, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("采购 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.spms.framework.query.QueryPageRequest<PurchaseEntity>();
            request.setFilter(new PurchaseEntity().setStatus(2));
            request.setPage(new cn.hamm.spms.framework.query.Page().setPageNum(1).setPageSize(20));

            var page = purchaseService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("PU-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("采购 detail/count 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            PurchaseEntity detail = purchaseMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, purchaseMapper.countByPermission(null, null, null));
            var stat = purchaseMapper.statByPermission(null, null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<PurchaseEntity> rows = purchaseMapper.selectPageByPermission(null, null, 2, 0, 20);
            assertEquals(1, rows.size());
            assertEquals(1L, rows.get(0).getId());

            // service 层走同一 shadow 提交/路由，与 Mapper 直取同值
            assertEquals(2L, purchaseService.getMapperCount(null));
            var serviceStat = purchaseService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("采购导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<PurchaseEntity> rows = purchaseMapper.selectForExport(null, null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("采购无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, purchaseMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(purchaseMapper.selectDetailByPermission(1L));
            assertEquals(0, purchaseMapper.selectForExport(null, null, null).size());
            assertEquals(0L, purchaseMapper.statByPermission(null, null, null).getTotal());
            assertEquals(0L, purchaseService.getMapperCount(null));
            assertEquals(0L, purchaseService.getMapperStat(null).getTotal());
        }
    }
}
