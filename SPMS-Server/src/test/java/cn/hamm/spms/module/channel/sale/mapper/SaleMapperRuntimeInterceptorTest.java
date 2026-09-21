package cn.hamm.spms.module.channel.sale.mapper;

import cn.hamm.spms.module.channel.sale.SaleEntity;
import cn.hamm.spms.module.channel.sale.SaleService;
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
 * <h1>销售单的 Mapper 运行时回归</h1>
 *
 * <p>销售单主表只投影客户 ID（详情阶段由客户服务补齐，不在销售 SQL 中 JOIN），本类钉住：
 * page 出口走统一筛选和租户条件、detail/count/stat 经过同一资源权限绑定、导出取数走同一
 * 权限链、无策略时恒假且不回退 JPA。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("SaleMapper · 真实运行时拦截器")
class SaleMapperRuntimeInterceptorTest {
    @Autowired
    private SaleMapper saleMapper;

    @Autowired
    private SaleService saleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS sale ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "reason VARCHAR(255), total_price DOUBLE, status INT, customer_id BIGINT)");
        jdbcTemplate.update("DELETE FROM sale");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) "
                + "VALUES (1, 10, 7, 'SA-001', '分销', 2, 9001, TRUE)");
        jdbcTemplate.update("INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) "
                + "VALUES (2, 10, 8, 'SA-002', '直销', 3, 9002, FALSE)");
        jdbcTemplate.update("INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) "
                + "VALUES (3, 20, 7, 'SA-003', '分销', 2, 9003, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9501, 'channel.sale', 'sale', 'sale', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9502, 'test-sale-tenant', 9501, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("销售 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.spms.framework.query.QueryPageRequest<SaleEntity>();
            request.setFilter(new SaleEntity().setStatus(2));
            request.setPage(new cn.hamm.spms.framework.query.Page().setPageNum(1).setPageSize(20));

            var page = saleService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("SA-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("销售 detail/count 经过同一资源权限绑定，且客户只投影 ID")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            SaleEntity detail = saleMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(9001L, detail.getCustomer().getId(), "客户关联只投影 ID，详情由服务补齐");
            assertEquals(2L, saleMapper.countByPermission(null, null, null));
            var stat = saleMapper.statByPermission(null, null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<SaleEntity> rows = saleMapper.selectPageByPermission(null, null, 2, 0, 20);
            assertEquals(1, rows.size());
            assertEquals(1L, rows.get(0).getId());

            // service 层走同一 shadow 提交/路由，与 Mapper 直取同值
            assertEquals(2L, saleService.getMapperCount(null));
            var serviceStat = saleService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("销售导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<SaleEntity> rows = saleMapper.selectForExport(null, null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("销售无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, saleMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(saleMapper.selectDetailByPermission(1L));
            assertEquals(0, saleMapper.selectForExport(null, null, null).size());
            assertEquals(0L, saleMapper.statByPermission(null, null, null).getTotal());
            assertEquals(0L, saleService.getMapperCount(null));
            assertEquals(0L, saleService.getMapperStat(null).getTotal());
        }
    }
}
