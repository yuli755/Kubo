package cn.hamm.spms.module.mes.plan.mapper;

import cn.hamm.spms.module.mes.plan.PlanEntity;
import cn.hamm.spms.module.mes.plan.PlanService;
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

import java.util.Map;
import java.util.Set;

import static cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate.FRAGMENT_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>生产计划 Mapper 运行时权限回归</h1>
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
@DisplayName("PlanMapper · 真实运行时拦截器")
class PlanMapperRuntimeInterceptorTest {
    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanService planService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS plan ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "status INT, type INT, deliver_time BIGINT, start_time BIGINT, finish_time BIGINT, "
                + "customer_id BIGINT)");
        jdbcTemplate.update("DELETE FROM plan");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO plan (id, tenant_id, creator_id, bill_code, status, type) "
                + "VALUES (1, 10, 7, 'PLAN-001', 2, 1)");
        jdbcTemplate.update("INSERT INTO plan (id, tenant_id, creator_id, bill_code, status, type) "
                + "VALUES (2, 10, 8, 'PLAN-002', 3, 2)");
        jdbcTemplate.update("INSERT INTO plan (id, tenant_id, creator_id, bill_code, status, type) "
                + "VALUES (3, 20, 7, 'PLAN-003', 2, 1)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9701, 'mes.plan', 'plan', 'plan', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9702, 'test-plan-tenant', 9701, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("计划 page 使用业务筛选和租户权限")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<PlanEntity>();
            request.setFilter(new PlanEntity().setType(1));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = planService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("PLAN-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("计划 detail/count 使用同一权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            PlanEntity detail = planMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, planMapper.countByPermission(null, null, null));
            assertEquals(2L, planMapper.selectPageByPermission(null, 3, null, 0, 20)
                    .get(0).getId());
        }
    }

    @Test
    @DisplayName("计划无策略时 fail closed")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, planMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(planMapper.selectDetailByPermission(1L));
        }
    }
}
