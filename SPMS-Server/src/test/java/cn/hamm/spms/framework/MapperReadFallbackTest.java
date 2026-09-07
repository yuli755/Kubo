package cn.hamm.spms.framework;

import cn.hamm.airpower.curd.model.query.Page;
import cn.hamm.airpower.curd.model.query.QueryPageRequest;
import cn.hamm.airpower.curd.model.query.Sort;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.asset.material.mapper.MaterialMapper;
import cn.hamm.spms.module.mes.order.OrderService;
import cn.hamm.spms.module.mes.order.mapper.OrderMapper;
import cn.hamm.spms.module.mes.picking.PickingService;
import cn.hamm.spms.module.mes.picking.mapper.PickingMapper;
import cn.hamm.spms.module.mes.plan.PlanService;
import cn.hamm.spms.module.mes.plan.mapper.PlanMapper;
import cn.hamm.spms.module.system.datapermission.config.MapperReadConfiguration;
import cn.hamm.spms.module.wms.input.InputEntity;
import cn.hamm.spms.module.wms.input.InputService;
import cn.hamm.spms.module.wms.input.mapper.InputMapper;
import cn.hamm.spms.module.wms.inventory.InventoryEntity;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.inventory.mapper.InventoryMapper;
import cn.hamm.spms.module.wms.move.MoveService;
import cn.hamm.spms.module.wms.move.mapper.MoveMapper;
import cn.hamm.spms.module.wms.output.OutputService;
import cn.hamm.spms.module.wms.output.mapper.OutputMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>Mapper 读侧关闭时的 JPA 回退（八资源）</h1>
 *
 * <p>八资源的读 Controller 都是「{@code if (!service.isMapperReadEnabled()) return super.*}」，
 * 而 {@code isMapperReadEnabled()} 是「{@code mapper != null}」。所以「回退到底有没有生效」等价于
 * 三个可证事实：开关关掉时（1）容器里没有任何读侧 Mapper Bean、（2）八个服务都报告 {@code false}、
 * （3）服务出口真能走通 JPA 并返回数据。本类钉死 {@code mapper-read.enabled=false}，把这三件事做成回归，
 * 而不是"看着应该会回退"。</p>
 *
 * <p>档位同时钉在 {@code kubo.data-permission.mode=off}（同 {@link MybatisJpaCoexistenceTest} 的口径）：
 * 这里要验的是"回到换轨前那条 JPA 路"，掺进权限判定只会让失败原因分不清是谁。mode=off 下 JPA 不查策略、
 * 不做租户过滤，正好证明回退后行为与换轨前逐字节相同。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "airpower.redis.enabled=false",
        "airpower.mqtt.enabled=false",
        "kubo.data-permission.mode=off",
        "kubo.data-permission.mapper-read.enabled=false"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("Mapper 读侧关闭 · JPA 回退（八资源）")
class MapperReadFallbackTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InputService inputService;

    @Autowired
    private OutputService outputService;

    @Autowired
    private MoveService moveService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlanService planService;

    @Autowired
    private PickingService pickingService;

    @BeforeEach
    void seed() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS input ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), "
                + "status INT, type INT, purchase_id BIGINT, structure_id BIGINT, order_id BIGINT, move_id BIGINT)");
        jdbcTemplate.update("DELETE FROM input");
        jdbcTemplate.update("INSERT INTO input (id, tenant_id, creator_id, bill_code, status, type) "
                + "VALUES (1, 10, 7, 'IN-001', 2, 1)");
        jdbcTemplate.update("INSERT INTO input (id, tenant_id, creator_id, bill_code, status, type) "
                + "VALUES (2, 20, 8, 'IN-002', 3, 2)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS inventory ("
                + "id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, "
                + "is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, "
                + "tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, quantity DOUBLE, "
                + "type INT, storage_id BIGINT, structure_id BIGINT)");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type) "
                + "VALUES (1, 10, 7, 101, 5.0, 1)");
        jdbcTemplate.update("INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type) "
                + "VALUES (2, 20, 8, 102, 8.0, 1)");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM input");
        jdbcTemplate.update("DELETE FROM inventory");
    }

    @Test
    @DisplayName("开关关掉时八个读侧 Mapper 与注册配置都不在容器里")
    void readMappersAreNotRegisteredWhenFlagOff() {
        assertEquals(0, applicationContext.getBeanNamesForType(MaterialMapper.class).length,
                "MaterialMapper 默认关却注册：开关被绕过");
        assertEquals(0, applicationContext.getBeanNamesForType(InventoryMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(InputMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(OutputMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(MoveMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(OrderMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(PlanMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(PickingMapper.class).length);
        assertEquals(0, applicationContext.getBeanNamesForType(MapperReadConfiguration.class).length,
                "@ConditionalOnProperty 的键名或 havingValue 漂了，注册配置被加载");
    }

    @Test
    @DisplayName("八个服务都报告 isMapperReadEnabled=false，Controller 因此全部走 super.* 回退")
    void allServicesReportMapperReadDisabled() {
        assertFalse(materialService.isMapperReadEnabled());
        assertFalse(inventoryService.isMapperReadEnabled());
        assertFalse(inputService.isMapperReadEnabled());
        assertFalse(outputService.isMapperReadEnabled());
        assertFalse(moveService.isMapperReadEnabled());
        assertFalse(orderService.isMapperReadEnabled());
        assertFalse(planService.isMapperReadEnabled());
        assertFalse(pickingService.isMapperReadEnabled());
    }

    @Test
    @DisplayName("入库单 JPA 回退功能级：getMapperPage/getMapperDetail 走 JPA（mode=off 无租户过滤）")
    void inputJpaFallbackIsFunctional() {
        QueryPageRequest<InputEntity> request = new QueryPageRequest<>();
        request.setFilter(new InputEntity());
        request.setPage(new Page().setPageNum(1).setPageSize(20));
        request.setSort(new Sort());

        var page = inputService.getMapperPage(request);
        assertEquals(2, page.getTotal(), "回退应走 JPA 全量（mode=off 不做租户过滤）");
        assertTrue(page.getList().stream().anyMatch(e -> "IN-001".equals(e.getBillCode())),
                "JPA 回退应读到 IN-001");
        assertTrue(page.getList().stream().anyMatch(e -> "IN-002".equals(e.getBillCode())),
                "JPA 回退应读到 IN-002");

        InputEntity detail = inputService.getMapperDetail(1L);
        assertEquals(1L, detail.getId());
        assertEquals("IN-001", detail.getBillCode());
    }

    @Test
    @DisplayName("库存 JPA 回退功能级：getMapperCount/getMapperDetail 走 JPA")
    void inventoryJpaFallbackIsFunctional() {
        assertEquals(2L, inventoryService.getMapperCount(new InventoryEntity()),
                "回退应走 JPA filter().size()（mode=off 无租户过滤）");

        InventoryEntity detail = inventoryService.getMapperDetail(1L);
        assertEquals(1L, detail.getId());
    }
}
