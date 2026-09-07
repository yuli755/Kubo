package cn.hamm.spms.module.wms.move.mapper;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.move.MoveService;
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
 * <h1>移库单 Mapper 运行时权限回归</h1>
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
@DisplayName("MoveMapper · 真实运行时拦截器")
class MoveMapperRuntimeInterceptorTest {
    @Autowired
    private MoveMapper moveMapper;

    @Autowired
    private MoveService moveService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS move ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "status INT, storage_id BIGINT)");
        jdbcTemplate.update("DELETE FROM move");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) "
                + "VALUES (1, 10, 7, 'MOVE-001', 2, 201, TRUE)");
        jdbcTemplate.update("INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) "
                + "VALUES (2, 10, 8, 'MOVE-002', 3, 202, FALSE)");
        jdbcTemplate.update("INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) "
                + "VALUES (3, 20, 7, 'MOVE-003', 2, 203, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9501, 'wms.move', 'move', 'move', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9502, 'test-move-tenant', 9501, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("移库 page 使用业务筛选和租户权限")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<MoveEntity>();
            request.setFilter(new MoveEntity().setStatus(2));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = moveService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("MOVE-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("移库 detail/count/stat 使用同一权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            MoveEntity detail = moveMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, moveMapper.countByPermission(null, null));
            var stat = moveMapper.statByPermission(null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            assertEquals(2L, moveMapper.selectPageByPermission(null, 3, 0, 20)
.get(0).getId());
        }
    }

    @Test
    @DisplayName("移库导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var rows = moveMapper.selectForExport(null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("移库无策略时 fail closed")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, moveMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(moveMapper.selectDetailByPermission(1L));
            assertEquals(0, moveMapper.selectForExport(null, null).size());
            assertEquals(0L, moveMapper.statByPermission(null, null).getTotal());
        }
    }
}
