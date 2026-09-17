package cn.hamm.spms.module.factory.storage.mapper;

import cn.hamm.spms.framework.curd.model.query.Page;
import cn.hamm.spms.framework.curd.model.query.QueryPageRequest;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.storage.StorageService;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
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
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>仓库读侧真实 MyBatis-Plus 数据权限拦截器回归</h1>
 *
 * <p>与纯 handler 单测不同，本类真的调用 StorageMapper、真的让
 * {@code MybatisPlusInterceptor -> MultiDataPermissionHandler -> JSqlParser} 改写 SQL，
 * 再在 H2 上执行结果，覆盖仓库的列表、详情、统计、总数与恒假路径。仓库是树形实体，本类只钉扁平的
 * page/detail/count/stat/export 主表读链；树导航走 JPA {@code getList}，不在本类覆盖范围内。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("StorageMapper · 真实运行时拦截器")
class StorageMapperRuntimeInterceptorTest {

    @Autowired
    private StorageMapper storageMapper;

    @Autowired
    private StorageService storageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        SqlFixtures.execute(jdbcTemplate, "sql/mapper-runtime/storage-mapper-runtime-interceptor.sql");
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
            QueryPageRequest<StorageEntity> request = new QueryPageRequest<>();
            request.setFilter(new StorageEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(1));

            var page = storageService.getMapperPage(request);
            var detail = storageService.getMapperDetail(1L);
            var stat = storageService.getMapperStat(new StorageEntity());

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getList().size());
            assertEquals(1L, detail.getId());
            assertEquals(2L, storageService.getMapperCount(new StorageEntity()));
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            var rows = storageMapper.selectForExport(new StorageEntity());
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
            Long count = storageMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(2L, count);
        }
    }

    @Test
    @DisplayName("没有可达策略时真实读出口全部恒假")
    void noPolicyDeniesAllRealReadExits() {
        SqlFixtures.execute(jdbcTemplate, "mapper-runtime/clear-policies.sql");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            QueryPageRequest<StorageEntity> request = new QueryPageRequest<>();
            request.setFilter(new StorageEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(20));

            assertEquals(0, storageService.getMapperPage(request).getTotal());
            assertNull(storageService.getMapperDetail(1L));
            assertEquals(0L, storageService.getMapperCount(new StorageEntity()));
            assertEquals(0L, storageService.getMapperStat(new StorageEntity()).getTotal());
            assertEquals(0, storageMapper.selectForExport(new StorageEntity()).size());
        }
    }

    @Test
    @DisplayName("没有可达策略时 enforce 返回恒假，不放开全表")
    void noPolicyDeniesRealMapperSql() {
        SqlFixtures.execute(jdbcTemplate, "mapper-runtime/clear-policies.sql");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = storageMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(0L, count);
        }
    }
}
