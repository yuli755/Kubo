package cn.hamm.spms.module.channel.supplier.mapper;

import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.channel.supplier.SupplierService;
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
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>供应商的 Mapper 运行时回归</h1>
 *
 * <p>供应商无跨资源关联主表，本类钉住：page 出口走统一筛选和租户条件、detail/count/stat 经过
 * 同一资源权限绑定、导出取数走同一权限链、无策略时恒假且不回退 JPA。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("SupplierMapper · 真实运行时拦截器")
class SupplierMapperRuntimeInterceptorTest {
    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        SqlFixtures.execute(jdbcTemplate, "sql/mapper-runtime/supplier-mapper-runtime-interceptor.sql");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("供应商 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.spms.framework.curd.model.query.QueryPageRequest<SupplierEntity>();
            request.setFilter(new SupplierEntity().setName("华东"));
            request.setPage(new cn.hamm.spms.framework.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = supplierService.getMapperPage(request);
            assertEquals(1, page.getTotal(), "当前租户下名为「华东」的供应商只有 id=1");
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("S-001", page.getList().get(0).getCode());
        }
    }

    @Test
    @DisplayName("供应商 detail/count 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            SupplierEntity detail = supplierMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, supplierMapper.countByPermission(null));
            var stat = supplierMapper.statByPermission(null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<SupplierEntity> rows = supplierMapper.selectPageByPermission(null, 0, 20);
            assertEquals(2, rows.size());

            assertEquals(2L, supplierService.getMapperCount(null));
            var serviceStat = supplierService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("供应商导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<SupplierEntity> rows = supplierMapper.selectForExport(null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("供应商无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        SqlFixtures.execute(jdbcTemplate, "mapper-runtime/clear-policies.sql");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, supplierMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(supplierMapper.selectDetailByPermission(1L));
            assertEquals(0, supplierMapper.selectForExport(null).size());
            assertEquals(0L, supplierMapper.statByPermission(null).getTotal());
            assertEquals(0L, supplierService.getMapperCount(null));
            assertEquals(0L, supplierService.getMapperStat(null).getTotal());
        }
    }
}
