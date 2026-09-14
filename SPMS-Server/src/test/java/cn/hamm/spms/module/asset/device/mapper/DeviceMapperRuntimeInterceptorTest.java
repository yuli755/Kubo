package cn.hamm.spms.module.asset.device.mapper;

import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.spms.module.asset.device.DeviceEntity;
import cn.hamm.spms.module.asset.device.DeviceService;
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
 * <h1>设备读侧真实 MyBatis-Plus 数据权限拦截器回归</h1>
 *
 * <p>与纯 handler 单测不同，本类真的调用 DeviceMapper、真的让
 * {@code MybatisPlusInterceptor -> MultiDataPermissionHandler -> JSqlParser} 改写 SQL，
 * 再在 H2 上执行结果，覆盖设备的列表、详情、统计、总数与恒假路径。参数列表是 {@code @ManyToMany}，
 * 详情沿 JPA 补齐，因此本类只建 {@code device} 主表、不建中间表。</p>
 *
 * @author zfy
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "airpower.redis.enabled=false",
        "airpower.mqtt.enabled=false",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("DeviceMapper · 真实运行时拦截器")
class DeviceMapperRuntimeInterceptorTest {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS device ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, name VARCHAR(255), code VARCHAR(255), "
                + "uuid VARCHAR(255), api_key VARCHAR(255), status INT, alarm INT, part_count BIGINT, "
                + "is_reporting BOOLEAN, rate INT)");
        addDeviceColumn("create_time", "BIGINT DEFAULT 0");
        addDeviceColumn("update_time", "BIGINT DEFAULT 0");
        addDeviceColumn("is_disabled", "BOOLEAN DEFAULT FALSE");
        addDeviceColumn("is_published", "BOOLEAN DEFAULT FALSE");
        addDeviceColumn("name", "VARCHAR(255)");
        addDeviceColumn("code", "VARCHAR(255)");
        addDeviceColumn("uuid", "VARCHAR(255)");
        addDeviceColumn("api_key", "VARCHAR(255)");
        addDeviceColumn("status", "INT");
        addDeviceColumn("alarm", "INT");
        addDeviceColumn("part_count", "BIGINT");
        addDeviceColumn("is_reporting", "BOOLEAN");
        addDeviceColumn("rate", "INT");
        jdbcTemplate.update("DELETE FROM device");
        jdbcTemplate.update("DELETE FROM data_policy");
        jdbcTemplate.update("DELETE FROM data_resource");
        jdbcTemplate.update("INSERT INTO device (id, tenant_id, creator_id, name, code, uuid, status, alarm, part_count, is_reporting, rate, is_published) "
                + "VALUES (1, 10, 7, '冲床', 'D-001', 'uuid-1', 4, 0, 100, TRUE, 1000, TRUE)");
        jdbcTemplate.update("INSERT INTO device (id, tenant_id, creator_id, name, code, uuid, status, alarm, part_count, is_reporting, rate, is_published) "
                + "VALUES (2, 10, 8, '折弯机', 'D-002', 'uuid-2', 4, 0, 200, TRUE, 1000, FALSE)");
        jdbcTemplate.update("INSERT INTO device (id, tenant_id, creator_id, name, code, uuid, status, alarm, part_count, is_reporting, rate, is_published) "
                + "VALUES (3, 20, 7, '焊接机', 'D-003', 'uuid-3', 4, 0, 300, TRUE, 1000, TRUE)");
        jdbcTemplate.update("INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, "
                        + "owner_columns, supported_actions, status, version_no) "
                        + "VALUES (9001, 'asset.device', 'device', 'device', 'tenant_id', 'creator_id', "
                        + "'QUERY', 'ENABLED', 1)");
        jdbcTemplate.update("INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, "
                        + "condition_json, priority, enabled, version_no) VALUES "
                        + "(9002, 'test-device-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1)");
    }

    private void addDeviceColumn(String name, String definition) {
        jdbcTemplate.execute("ALTER TABLE device ADD COLUMN IF NOT EXISTS " + name + " " + definition);
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
            QueryPageRequest<DeviceEntity> request = new QueryPageRequest<>();
            request.setFilter(new DeviceEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(1));

            var page = deviceService.getMapperPage(request);
            var detail = deviceService.getMapperDetail(1L);
            var stat = deviceService.getMapperStat(new DeviceEntity());

            assertEquals(2, page.getTotal());
            assertEquals(1, page.getList().size());
            assertEquals(1L, detail.getId());
            assertEquals(2L, deviceService.getMapperCount(new DeviceEntity()));
            assertEquals(2L, stat.getTotal());
            assertEquals(1L, stat.getPublishedTotal());
        }
    }

    @Test
    @DisplayName("导出取数走同一权限链：只返回当前租户可见行且固定 id 倒序")
    void exportSelectSharesPermissionPredicate() {
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            var rows = deviceMapper.selectForExport(new DeviceEntity());
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
            Long count = deviceMapper.countByPermissionFragment(
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
            QueryPageRequest<DeviceEntity> request = new QueryPageRequest<>();
            request.setFilter(new DeviceEntity());
            request.setPage(new Page().setPageNum(1).setPageSize(20));

            assertEquals(0, deviceService.getMapperPage(request).getTotal());
            assertNull(deviceService.getMapperDetail(1L));
            assertEquals(0L, deviceService.getMapperCount(new DeviceEntity()));
            assertEquals(0L, deviceService.getMapperStat(new DeviceEntity()).getTotal());
            assertEquals(0, deviceMapper.selectForExport(new DeviceEntity()).size());
        }
    }

    @Test
    @DisplayName("没有可达策略时 enforce 返回恒假，不放开全表")
    void noPolicyDeniesRealMapperSql() {
        jdbcTemplate.update("DELETE FROM data_policy");
        try (PermissionContextHolder.Scope ignored = PermissionContextHolder.open(
                new PermissionContext(7L, 10L, Set.of(), Set.of(), Set.of(), Set.of(), false))) {
            Long count = deviceMapper.countByPermissionFragment(
                    Map.of(RowPermissionPredicate.FRAGMENT_KEY, ""));
            assertEquals(0L, count);
        }
    }
}
