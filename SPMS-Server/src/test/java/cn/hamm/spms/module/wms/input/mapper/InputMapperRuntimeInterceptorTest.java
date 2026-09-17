package cn.hamm.spms.module.wms.input.mapper;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.InputService;
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
 * <h1>入库单第三资源的真实 Mapper 运行时回归</h1>
 *
 * <p>同时保留 Service page 出口和 Mapper detail/count 断言：真实业务入口切换到 Mapper，
 * 无权详情不回退；关联单据不在入库 SQL 中 JOIN。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("InputMapper · 真实运行时拦截器")
class InputMapperRuntimeInterceptorTest {
    @Autowired
    private InputMapper inputMapper;

    @Autowired
    private InputService inputService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        SqlFixtures.execute(jdbcTemplate, "sql/mapper-runtime/input-mapper-runtime-interceptor.sql");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("入库 page 使用统一筛选和租户条件")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.spms.framework.curd.model.query.QueryPageRequest<InputEntity>();
            request.setFilter(new InputEntity().setType(1));
            request.setPage(new cn.hamm.spms.framework.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = inputService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("IN-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("入库 detail/count 经过同一资源权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            InputEntity detail = inputMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, inputMapper.countByPermission(null, null, null));
            var stat = inputMapper.statByPermission(null, null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            List<InputEntity> rows = inputMapper.selectPageByPermission(null, 2, null, 0, 20);
            assertEquals(1, rows.size());
            assertEquals(1L, rows.get(0).getId());

            // service 层走同一 shadow 提交/路由，与 Mapper 直取同值
            assertEquals(2L, inputService.getMapperCount(null));
            var serviceStat = inputService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("入库导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            List<InputEntity> rows = inputMapper.selectForExport(null, null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }

    @Test
    @DisplayName("入库无策略时恒假，不返回跨租户详情")
    void noPolicyDeniesAllRows() {
        SqlFixtures.execute(jdbcTemplate, "mapper-runtime/clear-policies.sql");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            assertEquals(0L, inputMapper.countByPermissionFragment(Map.of(FRAGMENT_KEY, "")));
            assertNull(inputMapper.selectDetailByPermission(1L));
            assertEquals(0, inputMapper.selectForExport(null, null, null).size());
            assertEquals(0L, inputMapper.statByPermission(null, null, null).getTotal());
            assertEquals(0L, inputService.getMapperCount(null));
            assertEquals(0L, inputService.getMapperStat(null).getTotal());
        }
    }
}
