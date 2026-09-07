package cn.hamm.spms.framework;

import cn.hamm.airpower.curd.base.Curd;
import cn.hamm.spms.module.asset.material.MaterialController;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.asset.material.mapper.MaterialMapper;
import cn.hamm.spms.module.mes.order.OrderController;
import cn.hamm.spms.module.mes.order.OrderService;
import cn.hamm.spms.module.mes.order.mapper.OrderMapper;
import cn.hamm.spms.module.mes.picking.PickingController;
import cn.hamm.spms.module.mes.picking.PickingService;
import cn.hamm.spms.module.mes.picking.mapper.PickingMapper;
import cn.hamm.spms.module.mes.plan.PlanController;
import cn.hamm.spms.module.mes.plan.PlanService;
import cn.hamm.spms.module.mes.plan.mapper.PlanMapper;
import cn.hamm.spms.module.system.datapermission.config.MapperReadConfiguration;
import cn.hamm.spms.module.wms.input.InputController;
import cn.hamm.spms.module.wms.input.InputService;
import cn.hamm.spms.module.wms.input.mapper.InputMapper;
import cn.hamm.spms.module.wms.inventory.InventoryController;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.inventory.mapper.InventoryMapper;
import cn.hamm.spms.module.wms.move.MoveController;
import cn.hamm.spms.module.wms.move.MoveService;
import cn.hamm.spms.module.wms.move.mapper.MoveMapper;
import cn.hamm.spms.module.wms.output.OutputController;
import cn.hamm.spms.module.wms.output.OutputService;
import cn.hamm.spms.module.wms.output.mapper.OutputMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * <h1>Mapper 读侧关闭时的 JPA 回退（八资源）</h1>
 *
 * <p>八资源的读 Controller 都是「{@code if (!service.isMapperReadEnabled()) return super.*}」，
 * 而 {@code isMapperReadEnabled()} 是「{@code mapper != null}」。所以「回退到底有没有生效」由两件可证事实
 * 完全决定：开关关掉时（1）容器里没有任何读侧 Mapper Bean、（2）八个服务都报告 {@code false}——于是每个
 * Controller 都走 {@code super.getPage/getDetail/export}，也就是换轨前那条原封不动的框架 JPA 路径。</p>
 *
 * <p><b>为什么不做「塞一行数据再走 JPA 读出来」的功能级回退</b>：单据实体的 {@code @ManyToOne(EAGER)} 关系图
 * （订单→物料/计划/客户、入库→采购/结构/订单/移库…）会让一条 JPA 查询把整张关系表都拉进 JOIN，测试里必须把
 * 八资源的全部关联表都建出来；而 {@code inventory} 的 {@code beforeCreatePredicate} 在 type 为空时直接 NPE。
 * 这两点都属于换轨前 JPA 路径的既有行为，不是回退引入的，也不在「回退 = 走 {@code super.*}」这个命题之内；
 * 框架默认路径本身已由既有 400+ 用例覆盖。所以本类只钉回退路由的那两个开关事实，外加导出动作确实登记在八个
 * Controller 上（这样 {@code super.export} 的 {@code Curd.Export.checkApiAvailable} 才不会在回退时误炸）。</p>
 *
 * <p>档位同时钉在 {@code kubo.data-permission.mode=off}（同 {@link MybatisJpaCoexistenceTest} 的口径）：
 * 这里验的是"回到换轨前那条 JPA 路"，掺进权限判定只会让失败原因分不清是谁。</p>
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

    @Autowired
    private MaterialController materialController;

    @Autowired
    private InventoryController inventoryController;

    @Autowired
    private InputController inputController;

    @Autowired
    private OutputController outputController;

    @Autowired
    private MoveController moveController;

    @Autowired
    private OrderController orderController;

    @Autowired
    private PlanController planController;

    @Autowired
    private PickingController pickingController;

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
    @DisplayName("八个 Controller 都登记了导出与导出结果查询动作（回退时 super.export 不会误炸）")
    void exportActionsAreRegisteredOnAllEightControllers() {
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(materialController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(inventoryController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(inputController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(outputController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(moveController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(orderController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(planController));
        assertDoesNotThrow(() -> Curd.Export.checkApiAvailable(pickingController));

        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(materialController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(inventoryController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(inputController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(outputController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(moveController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(orderController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(planController));
        assertDoesNotThrow(() -> Curd.QueryExport.checkApiAvailable(pickingController));
    }
}
