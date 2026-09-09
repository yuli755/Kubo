package cn.hamm.spms.module.mes.routing.mapper;

import cn.hamm.spms.module.mes.routing.RoutingEntity;
import cn.hamm.spms.module.mes.routing.RoutingService;
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
 * <h1>生产工艺的 Mapper 运行时回归</h1>
 *
 * <p>工艺的 {@code material}/{@code bom} 为 EAGER 关联、{@code details} 由 {@code afterAppGet} 水合，
 * 本类只钉主表读链：page 出口走统一筛选和租户条件、detail/count/stat 经过同一资源权限绑定、
 * 导出取数走同一权限链、无策略时恒假且不回退 JPA。</p>
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
@DisplayName("RoutingMapper · 真实运行时拦截器")
class RoutingMapperRuntimeInterceptorTest {
    @Autowired
    private RoutingMapper routingMapper;

    @Autowired
    private RoutingService routingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS routing ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), "
                + "status INT, is_routing_bom BOOLEAN DEFAULT FALSE, material_id BIGINT, bom_id BIGINT)");
        jdbcTemplate.update("DELETE FROM routing");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO routing (id, tenant_id, creator_id, code, name, status, is_published, "
                        + "is_routing_bom, material_id, bom_id) "
                        + "VALUES (1, 10, 7, 'RT-001', '笔记本安装', 1, TRUE, TRUE, 11, 21)");
        jdbcTemplate.update("INSERT INTO routing (id, tenant_id, creator_id, code, name, status, is_published, "
                        + "is_routing_bom, material_id, bom_id) "
                        + "VALUES (2, 10, 8, 'RT-002', '键盘安装', 2, FALSE, FALSE, 12, NULL)");
        jdbcTemplate.update("INSERT INTO routing (id, tenant_id, creator_id, code, name, status, is_published, "
                        + "is_routing_bom, material_id, bom_id) "
                        + "VALUES (3, 20, 7, 'RT-003', '笔记本安装', 1, TRUE, TRUE, 13, 23)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (10001, 'mes.routing', 'routing', 'routing', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(10002, 'test-routing-tenant', 10001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("工艺 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<RoutingEntity>();
            request.setFilter(new RoutingEntity().setName("笔记本"));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = routingService.getMapperPage(request);
            assertEquals(1, page.getTotal(), "当前租户下名为「笔记本」的工艺只有 id=1");
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("RT-001", page.getList().get(0).getCode());
        }
    }

    @Test
    @DisplayName("工艺 detail/count/stat 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            RoutingEntity detail = routingMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, routingMapper.countByPermission(null));
            var stat = routingMapper.statByPermission(null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<RoutingEntity> rows = routingMapper.selectPageByPermission(null, 0, 20);
            assertEquals(2, rows.size());

            assertEquals(2L, routingService.getMapperCount(null));
            var serviceStat = routingService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("工艺导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<RoutingEntity> rows = routingMapper.selectForExport(null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("工艺无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, routingMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(routingMapper.selectDetailByPermission(1L));
            assertEquals(0, routingMapper.selectForExport(null).size());
            assertEquals(0L, routingMapper.statByPermission(null).getTotal());
            assertEquals(0L, routingService.getMapperCount(null));
            assertEquals(0L, routingService.getMapperStat(null).getTotal());
        }
    }
}
