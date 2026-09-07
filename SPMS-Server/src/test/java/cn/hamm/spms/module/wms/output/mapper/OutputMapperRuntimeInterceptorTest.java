package cn.hamm.spms.module.wms.output.mapper;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.wms.output.OutputEntity;
import cn.hamm.spms.module.wms.output.OutputService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate.FRAGMENT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>出库单 Mapper 运行时权限回归</h1>
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
@DisplayName("OutputMapper · 真实运行时拦截器")
class OutputMapperRuntimeInterceptorTest {
    @Autowired
    private OutputMapper outputMapper;

    @Autowired
    private OutputService outputService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS output ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "status INT, type INT, sale_id BIGINT, move_id BIGINT, picking_id BIGINT)");
        jdbcTemplate.update("DELETE FROM output");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) "
                + "VALUES (1, 10, 7, 'OUT-001', 2, 1, TRUE)");
        jdbcTemplate.update("INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) "
                + "VALUES (2, 10, 8, 'OUT-002', 3, 2, FALSE)");
        jdbcTemplate.update("INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) "
                + "VALUES (3, 20, 7, 'OUT-003', 2, 1, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9401, 'wms.output', 'output', 'output', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9402, 'test-output-tenant', 9401, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("出库 page 使用业务筛选和租户权限")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<OutputEntity>();
            request.setFilter(new OutputEntity().setType(1));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = outputService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("OUT-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("出库 detail/count/stat 使用同一权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            OutputEntity detail = outputMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, outputMapper.countByPermission(null, null, null));
            var stat = outputMapper.statByPermission(null, null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            assertEquals(1L, outputMapper.selectPageByPermission(null, 2, null, 0, 20)
                    .get(0).getId());

            // service 层走同一 shadow 提交/路由，与 Mapper 直取同值
            assertEquals(2L, outputService.getMapperCount(null));
            var serviceStat = outputService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("出库导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var rows = outputMapper.selectForExport(null, null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("出库无策略时 fail closed")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, outputMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(outputMapper.selectDetailByPermission(1L));
            assertEquals(0, outputMapper.selectForExport(null, null, null).size());
            assertEquals(0L, outputMapper.statByPermission(null, null, null).getTotal());
            assertEquals(0L, outputService.getMapperCount(null));
            assertEquals(0L, outputService.getMapperStat(null).getTotal());
        }
    }
}
