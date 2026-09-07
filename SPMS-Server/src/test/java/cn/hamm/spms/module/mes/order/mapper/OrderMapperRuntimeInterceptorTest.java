package cn.hamm.spms.module.mes.order.mapper;

import cn.hamm.spms.module.mes.order.OrderEntity;
import cn.hamm.spms.module.mes.order.OrderService;
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
 * <h1>生产订单 Mapper 运行时权限回归</h1>
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
@DisplayName("OrderMapper · 真实运行时拦截器")
class OrderMapperRuntimeInterceptorTest {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS orders ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "status INT, type INT, start_time BIGINT, finish_time BIGINT, deliver_time BIGINT, "
                + "quantity DOUBLE, finish_quantity DOUBLE, ng_quantity DOUBLE, material_id BIGINT, "
                + "plan_id BIGINT, customer_id BIGINT)");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO orders (id, tenant_id, creator_id, bill_code, status, type, quantity) "
                + "VALUES (1, 10, 7, 'ORDER-001', 2, 1, 10)");
        jdbcTemplate.update("INSERT INTO orders (id, tenant_id, creator_id, bill_code, status, type, quantity) "
                + "VALUES (2, 10, 8, 'ORDER-002', 3, 2, 20)");
        jdbcTemplate.update("INSERT INTO orders (id, tenant_id, creator_id, bill_code, status, type, quantity) "
                + "VALUES (3, 20, 7, 'ORDER-003', 2, 1, 30)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9601, 'mes.order', 'orders', 'orders', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9602, 'test-order-tenant', 9601, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("订单 page 使用业务筛选和租户权限")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.airpower.curd.model.query.QueryPageRequest<OrderEntity>();
            request.setFilter(new OrderEntity().setType(1));
            request.setPage(new cn.hamm.airpower.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = orderService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("ORDER-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("订单 detail/count 使用同一权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            OrderEntity detail = orderMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, orderMapper.countByPermission(null, null, null));
            assertEquals(2L, orderMapper.selectPageByPermission(null, 3, null, 0, 20)
                    .get(0).getId());
        }
    }

    @Test
    @DisplayName("订单无策略时 fail closed")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, orderMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(orderMapper.selectDetailByPermission(1L));
        }
    }
}
