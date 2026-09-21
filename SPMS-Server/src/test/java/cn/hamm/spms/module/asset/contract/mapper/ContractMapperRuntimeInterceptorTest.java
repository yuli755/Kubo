package cn.hamm.spms.module.asset.contract.mapper;

import cn.hamm.spms.framework.query.Page;
import cn.hamm.spms.framework.query.QueryPageRequest;
import cn.hamm.spms.module.asset.contract.ContractEntity;
import cn.hamm.spms.module.asset.contract.ContractService;
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
 * <h1>合同读侧真实 MyBatis-Plus 数据权限拦截器回归</h1>
 *
 * <p>与纯 handler 单测不同，本类真的调用 ContractMapper、真的让
 * {@code MybatisPlusInterceptor -> MultiDataPermissionHandler -> JSqlParser} 改写 SQL，
 * 再在 H2 上执行结果，覆盖合同的列表、详情、统计、总数与恒假路径。附件与参与方是 {@code @ManyToMany}，
 * 详情沿 JPA 补齐，因此本类只建 {@code contract} 主表、不建中间表。</p>
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
@DisplayName("ContractMapper · 真实运行时拦截器")
class ContractMapperRuntimeInterceptorTest {

    @Autowired
    private ContractMapper contractMapper;

    @Autowired
    private ContractService contractService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS contract ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), "
                + "content TEXT, start_time BIGINT, end_time BIGINT, type INT, status INT)");
        addContractColumn("create_time", "BIGINT DEFAULT 0");
        addContractColumn("update_time", "BIGINT DEFAULT 0");
        addContractColumn("is_disabled", "BOOLEAN DEFAULT FALSE");
        addContractColumn("is_published", "BOOLEAN DEFAULT FALSE");
        addContractColumn("code", "VARCHAR(255)");
        addContractColumn("name", "VARCHAR(255)");
        addContractColumn("content", "TEXT");
        addContractColumn("start_time", "BIGINT");
        addContractColumn("end_time", "BIGINT");
        addContractColumn("type", "INT");
        addContractColumn("status", "INT");
        jdbcTemplate.update("DELETE FROM contract");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) "
                + "VALUES (1, 10, 7, 'C-001', '采购合同A', '内容A', 1000, 2000, 1, 1, TRUE)");
        jdbcTemplate.update("INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) "
                + "VALUES (2, 10, 8, 'C-002', '采购合同B', '内容B', 1000, 2000, 1, 1, FALSE)");
        jdbcTemplate.update("INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) "
                + "VALUES (3, 20, 7, 'C-003', '销售合同C', '内容C', 1000, 2000, 2, 1, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9001, 'asset.contract', 'contract', 'contract', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9002, 'test-contract-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    private void addContractColumn(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE contract ADD COLUMN IF NOT EXISTS " + name + " " + definition);
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
            QueryPageRequest<ContractEntity> request = new QueryPageRequest<>();
            request.setFilter(new ContractEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(1));

            var page = contractService.getMapperPage(request);
            var detail = contractService.getMapperDetail(1L);
            var stat = contractService.getMapperStat(new ContractEntity());

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getList().size());
            assertEquals(1L, detail.getId());
            assertEquals(2L, contractService.getMapperCount(new ContractEntity()));
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            var rows = contractMapper.selectForExport(new ContractEntity());
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
            Long count = contractMapper.countByPermissionFragment(
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
            QueryPageRequest<ContractEntity> request = new QueryPageRequest<>();
            request.setFilter(new ContractEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(20));

            assertEquals(0, contractService.getMapperPage(request).getTotal());
            assertNull(contractService.getMapperDetail(1L));
            assertEquals(0L, contractService.getMapperCount(new ContractEntity()));
            assertEquals(0L, contractService.getMapperStat(new ContractEntity()).getTotal());
            assertEquals(0, contractMapper.selectForExport(new ContractEntity()).size());
        }
    }

    @Test
    @DisplayName("没有可达策略时 enforce 返回恒假，不放开全表")
    void noPolicyDeniesRealMapperSql() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = contractMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(0L, count);
        }
    }
}
