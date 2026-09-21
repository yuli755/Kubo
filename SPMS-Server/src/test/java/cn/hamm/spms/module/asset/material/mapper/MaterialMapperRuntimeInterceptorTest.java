package cn.hamm.spms.module.asset.material.mapper;

import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import cn.hamm.spms.framework.query.Page;
import cn.hamm.spms.framework.query.QueryPageRequest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h1>真实 MyBatis-Plus 数据权限拦截器回归</h1>
 *
 * <p>与纯 handler 单测不同，本类真的调用 Mapper、真的让
 * {@code MybatisPlusInterceptor -> MultiDataPermissionHandler -> JSqlParser} 改写 SQL，
 * 再在 H2 上执行结果，覆盖 Material 的列表、详情、统计、总数与恒假路径。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("MaterialMapper · 真实运行时拦截器")
class MaterialMapperRuntimeInterceptorTest {

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS material ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, name VARCHAR(255), code VARCHAR(255), "
                + "spc VARCHAR(255), material_type INT, use_type INT, unit_id BIGINT, "
                + "purchase_price DOUBLE, sale_price DOUBLE)");
        addMaterialColumn("create_time", "BIGINT DEFAULT 0");
        addMaterialColumn("update_time", "BIGINT DEFAULT 0");
        addMaterialColumn("is_disabled", "BOOLEAN DEFAULT FALSE");
        addMaterialColumn("is_published", "BOOLEAN DEFAULT FALSE");
        addMaterialColumn("name", "VARCHAR(255)");
        addMaterialColumn("code", "VARCHAR(255)");
        addMaterialColumn("spc", "VARCHAR(255)");
        addMaterialColumn("material_type", "INT");
        addMaterialColumn("use_type", "INT");
        addMaterialColumn("unit_id", "BIGINT");
        addMaterialColumn("purchase_price", "DOUBLE");
        addMaterialColumn("sale_price", "DOUBLE");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS unit ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "name VARCHAR(255), code VARCHAR(255))");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS create_time BIGINT DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS update_time BIGINT DEFAULT 0");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS is_disabled BOOLEAN DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS name VARCHAR(255)");
        jdbcTemplate.execute("ALTER TABLE unit ADD COLUMN IF NOT EXISTS code VARCHAR(255)");
        jdbcTemplate.update("DELETE FROM material");
        jdbcTemplate.update("DELETE FROM unit");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO unit (id, name, code) VALUES (100, 'piece', 'PCS')");
        jdbcTemplate.update("INSERT INTO material (id, tenant_id, creator_id, name, code, material_type, use_type, unit_id, is_published) "
                + "VALUES (1, 10, 7, 'A', 'A-001', 1, 1, 100, TRUE)");
        jdbcTemplate.update("INSERT INTO material (id, tenant_id, creator_id, name, code, material_type, use_type, unit_id, is_published) "
                + "VALUES (2, 10, 8, 'B', 'B-001', 1, 1, 100, FALSE)");
        jdbcTemplate.update("INSERT INTO material (id, tenant_id, creator_id, name, code, material_type, use_type, unit_id, is_published) "
                + "VALUES (3, 20, 7, 'C', 'C-001', 2, 1, 100, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9001, 'asset.material', 'material', 'material', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9002, 'test-material-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    private void addMaterialColumn(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE material ADD COLUMN IF NOT EXISTS " + name + " " + definition);
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    @Test
    @DisplayName("真实 page/detail/stat/count 出口共用拦截器条件")
    void realReadExitsSharePermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            QueryPageRequest<MaterialEntity> request = new QueryPageRequest<>();
            request.setFilter(new MaterialEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(1));

            var page = materialService.getMapperPage(request);
            var detail = materialService.getMapperDetail(1L);
            var stat = materialService.getMapperStat(new MaterialEntity());

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getList().size());
            assertEquals(1L, detail.getId());
            assertEquals(2L, materialService.getMapperCount(new MaterialEntity()));
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            var rows = materialMapper.selectForExport(new MaterialEntity());
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("租户条件由运行时拦截器注入，受限主体只数到自己的两行")
    void interceptorFiltersRealMapperSql() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = materialMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(2L, count);
        }
    }

    @Test
    @DisplayName("没有可达策略时真实读出口全部恒假")
    void noPolicyDeniesAllRealReadExits() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            QueryPageRequest<MaterialEntity> request = new QueryPageRequest<>();
            request.setFilter(new MaterialEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(20));

            assertEquals(0, materialService.getMapperPage(request).getTotal());
            assertNull(materialService.getMapperDetail(1L));
            assertEquals(0L, materialService.getMapperCount(new MaterialEntity()));
            assertEquals(0L, materialService.getMapperStat(new MaterialEntity()).getTotal());
            assertEquals(0, materialMapper.selectForExport(new MaterialEntity()).size());
        }
    }

    @Test
    @DisplayName("没有可达策略时 enforce 返回恒假，不放开全表")
    void noPolicyDeniesRealMapperSql() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = materialMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(0L, count);
        }
    }
}
