package cn.hamm.spms.module.factory.structure.mapper;

import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.factory.structure.StructureService;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
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
 * <h1>生产单元读侧真实 MyBatis-Plus 数据权限拦截器回归</h1>
 *
 * <p>与纯 handler 单测不同，本类真的调用 StructureMapper、真的让
 * {@code MybatisPlusInterceptor -> MultiDataPermissionHandler -> JSqlParser} 改写 SQL，
 * 再在 H2 上执行结果，覆盖生产单元的列表、详情、统计、总数与恒假路径。生产单元是树形实体，本类只钉扁平的
 * page/detail/count/stat/export 主表读链；树导航走 JPA {@code getList}，工序列表是 {@code @ManyToMany}
 * 沿 JPA 补齐，故本类只建 {@code structure} 主表。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("StructureMapper · 真实运行时拦截器")
class StructureMapperRuntimeInterceptorTest {

    @Autowired
    private StructureMapper structureMapper;

    @Autowired
    private StructureService structureService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS structure ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), "
                + "type INT, parent_id BIGINT)");
        addStructureColumn("create_time", "BIGINT DEFAULT 0");
        addStructureColumn("update_time", "BIGINT DEFAULT 0");
        addStructureColumn("is_disabled", "BOOLEAN DEFAULT FALSE");
        addStructureColumn("is_published", "BOOLEAN DEFAULT FALSE");
        addStructureColumn("code", "VARCHAR(255)");
        addStructureColumn("name", "VARCHAR(255)");
        addStructureColumn("type", "INT");
        addStructureColumn("parent_id", "BIGINT");
        jdbcTemplate.update("DELETE FROM structure");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) "
                + "VALUES (1, 10, 7, 'ST-001', '组装工位', 1, 0, TRUE)");
        jdbcTemplate.update("INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) "
                + "VALUES (2, 10, 8, 'ST-002', '质检工位', 2, 1, FALSE)");
        jdbcTemplate.update("INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) "
                + "VALUES (3, 20, 7, 'ST-003', '包装工位', 3, 0, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9001, 'factory.structure', 'structure', 'structure', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9002, 'test-structure-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    private void addStructureColumn(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE structure ADD COLUMN IF NOT EXISTS " + name + " " + definition);
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
            QueryPageRequest<StructureEntity> request = new QueryPageRequest<>();
            request.setFilter(new StructureEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(1));

            var page = structureService.getMapperPage(request);
            var detail = structureService.getMapperDetail(1L);
            var stat = structureService.getMapperStat(new StructureEntity());

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getList().size());
            assertEquals(1L, detail.getId());
            assertEquals(2L, structureService.getMapperCount(new StructureEntity()));
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            var rows = structureMapper.selectForExport(new StructureEntity());
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
            Long count = structureMapper.countByPermissionFragment(
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
            QueryPageRequest<StructureEntity> request = new QueryPageRequest<>();
            request.setFilter(new StructureEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(20));

            assertEquals(0, structureService.getMapperPage(request).getTotal());
            assertNull(structureService.getMapperDetail(1L));
            assertEquals(0L, structureService.getMapperCount(new StructureEntity()));
            assertEquals(0L, structureService.getMapperStat(new StructureEntity()).getTotal());
            assertEquals(0, structureMapper.selectForExport(new StructureEntity()).size());
        }
    }

    @Test
    @DisplayName("没有可达策略时 enforce 返回恒假，不放开全表")
    void noPolicyDeniesRealMapperSql() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = structureMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(0L, count);
        }
    }
}
