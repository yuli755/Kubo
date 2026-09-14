package cn.hamm.spms.module.mes.operation.mapper;

import cn.hamm.spms.module.mes.operation.OperationEntity;
import cn.hamm.spms.module.mes.operation.OperationService;
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
 * <h1>工序的 Mapper 运行时回归</h1>
 *
 * <p>工序是单表、无关联主表，本类钉住：page 出口走统一筛选和租户条件、detail/count/stat 经过同一
 * 资源权限绑定、导出取数走同一权限链、无策略时恒假且不回退 JPA。</p>
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
@DisplayName("OperationMapper · 真实运行时拦截器")
class OperationMapperRuntimeInterceptorTest {
    @Autowired
    private OperationMapper operationMapper;

    @Autowired
    private OperationService operationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS operation ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, name VARCHAR(255), code VARCHAR(255))");
        jdbcTemplate.update("DELETE FROM operation");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO operation (id, tenant_id, creator_id, name, code, is_published) "
                + "VALUES (1, 10, 7, '键盘安装', 'OP-001', TRUE)");
        jdbcTemplate.update("INSERT INTO operation (id, tenant_id, creator_id, name, code, is_published) "
                + "VALUES (2, 10, 8, '屏幕组装', 'OP-002', FALSE)");
        jdbcTemplate.update("INSERT INTO operation (id, tenant_id, creator_id, name, code, is_published) "
                + "VALUES (3, 20, 7, '键盘安装', 'OP-003', TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9901, 'mes.operation', 'operation', 'operation', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9902, 'test-operation-tenant', 9901, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("工序 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<OperationEntity>();
            request.setFilter(new OperationEntity().setName("键盘"));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = operationService.getMapperPage(request);
            assertEquals(1, page.getTotal(), "当前租户下名为「键盘」的工序只有 id=1");
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("OP-001", page.getList().get(0).getCode());
        }
    }

    @Test
    @DisplayName("工序 detail/count/stat 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            OperationEntity detail = operationMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, operationMapper.countByPermission(null));
            var stat = operationMapper.statByPermission(null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<OperationEntity> rows = operationMapper.selectPageByPermission(null, 0, 20);
            assertEquals(2, rows.size());

            assertEquals(2L, operationService.getMapperCount(null));
            var serviceStat = operationService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("工序导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<OperationEntity> rows = operationMapper.selectForExport(null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("工序无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, operationMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(operationMapper.selectDetailByPermission(1L));
            assertEquals(0, operationMapper.selectForExport(null).size());
            assertEquals(0L, operationMapper.statByPermission(null).getTotal());
            assertEquals(0L, operationService.getMapperCount(null));
            assertEquals(0L, operationService.getMapperStat(null).getTotal());
        }
    }
}
