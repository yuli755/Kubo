package cn.hamm.spms.module.wms.inventory.mapper;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
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
 * <h1>库存第二资源的真实 Mapper 运行时回归</h1>
 *
 * <p>不只检查 XML 存在：真实调用会经过 statement 绑定、MyBatis-Plus 数据权限拦截器和 H2。
 * 关系对象在本测试中只断言 ID 投影，避免用未登记 JOIN 表把资源边界掩盖掉；服务层补齐关系的行为
 * 在真实库存读出口中使用受控的 Material/Storage/Structure 服务。</p>
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
@DisplayName("InventoryMapper · 真实运行时拦截器")
class InventoryMapperRuntimeInterceptorTest {
    private static final long TENANT_A = 10L;

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS inventory ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, quantity DOUBLE, "
                + "type INT, storage_id BIGINT, structure_id BIGINT)");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) "
                + "VALUES (1, 10, 7, 101, 5.0, 1, 201, NULL, TRUE)");
        jdbcTemplate.update("INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) "
                + "VALUES (2, 10, 8, 102, 8.0, 1, 202, NULL, FALSE)");
        jdbcTemplate.update("INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) "
                + "VALUES (3, 20, 7, 103, 13.0, 1, 203, NULL, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9201, 'wms.inventory', 'inventory', 'inventory', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9202, 'test-inventory-tenant', 9201, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, TENANT_A, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("库存 page/detail/count/stat 经过同一资源权限绑定")
    void realReadStatementsFilterByTenant() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<InventoryEntity> page = inventoryMapper.selectPageByPermission(1, List.of(), List.of(), 0, 20);
            InventoryEntity detail = inventoryMapper.selectDetailByPermission(1L);
            Long count = inventoryMapper.countByPermission(1, List.of(), List.of());
            var stat = inventoryMapper.statByPermission(1, List.of(), List.of());

            assertEquals(2L, count);
            assertEquals(2, page.size());
            assertEquals(2L, page.get(0).getId(), "实际排序固定为 ID 倒序");
            assertEquals(101L, page.get(1).getMaterial().getId());
            assertEquals(202L, page.get(0).getStorage().getId());
            assertEquals(1L, detail.getId());
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
        }
    }

    @Test
    @DisplayName("库存业务筛选与权限条件同时生效")
    void typeAndTreeFiltersRemainInTheMapperStatement() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<InventoryEntity> page = inventoryMapper.selectPageByPermission(1, List.of(202L), List.of(), 0, 20);
            assertEquals(1, page.size());
            assertEquals(2L, page.get(0).getId());
            assertEquals(1L, inventoryMapper.countByPermission(1, List.of(202L), List.of()));
        }
    }

    @Test
    @DisplayName("库存导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<InventoryEntity> rows = inventoryMapper.selectForExport(1, List.of(), List.of());
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("库存没有可达策略时拒绝全部行")
    void noPolicyDeniesAllRows() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, inventoryMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(inventoryMapper.selectDetailByPermission(1L));
            assertEquals(0, inventoryMapper.selectForExport(1, List.of(), List.of()).size());
            assertEquals(0L, inventoryMapper.statByPermission(1, List.of(), List.of()).getTotal());
        }
    }
}
