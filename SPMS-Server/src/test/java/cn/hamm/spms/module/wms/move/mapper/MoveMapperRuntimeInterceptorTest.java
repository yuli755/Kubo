package cn.hamm.spms.module.wms.move.mapper;

import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.wms.move.MoveEntity;
import cn.hamm.spms.module.wms.move.MoveService;
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
import cn.hamm.spms.support.SqlFixtures;

/**
 * <h1>移库单 Mapper 运行时权限回归</h1>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("MoveMapper · 真实运行时拦截器")
class MoveMapperRuntimeInterceptorTest {
    @Autowired
    private MoveMapper moveMapper;

    @Autowired
    private MoveService moveService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        SqlFixtures.execute(jdbcTemplate, "sql/mapper-runtime/move-mapper-runtime-interceptor.sql");
    }

    @AfterEach
    void clearContext() {
        PermissionContextHolder.clear();
    }

    private PermissionContext context() {
        return new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false);
    }

    @Test
    @DisplayName("移库 page 使用业务筛选和租户权限")
    void servicePageUsesMapperPermission() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var request = new cn.hamm.spms.framework.curd.model.query.QueryPageRequest<MoveEntity>();
            request.setFilter(new MoveEntity().setStatus(2));
            request.setPage(new cn.hamm.spms.framework.curd.model.query.Page().setPageNum(1).setPageSize(20));

            var page = moveService.getMapperPage(request);
            assertEquals(1, page.getTotal());
            assertEquals(1L, page.getList().get(0).getId());
            assertEquals("MOVE-001", page.getList().get(0).getBillCode());
        }
    }

    @Test
    @DisplayName("移库 detail/count/stat 使用同一权限绑定")
    void detailAndCountArePermissionAware() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            MoveEntity detail = moveMapper.selectDetailByPermission(1L);
            assertEquals(1L, detail.getId());
            assertEquals(2L, moveMapper.countByPermission(null, null));
            var stat = moveMapper.statByPermission(null, null);
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal(), "当前租户两行中仅 id=1 已发布");
            assertEquals(2L, moveMapper.selectPageByPermission(null, 3, 0, 20)
.get(0).getId());

            // service 层走同一 shadow 提交/路由，与 Mapper 直取同值
            assertEquals(2L, moveService.getMapperCount(null));
            var serviceStat = moveService.getMapperStat(null);
            assertEquals(2L, serviceStat.getTotal());
            assertEquals(1L, serviceStat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("移库导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(context())) {
            var rows = moveMapper.selectForExport(null, null);
            assertEquals(2, rows.size());
            assertEquals(2L, rows.get(0).getId());
            assertEquals(1L, rows.get(1).getId());
        }
    }
}
