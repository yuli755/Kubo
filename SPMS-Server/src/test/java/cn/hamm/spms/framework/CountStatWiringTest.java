package cn.hamm.spms.framework;

import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.asset.material.MaterialController;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.mes.order.OrderController;
import cn.hamm.spms.module.mes.order.OrderService;
import cn.hamm.spms.module.mes.picking.PickingController;
import cn.hamm.spms.module.mes.picking.PickingService;
import cn.hamm.spms.module.mes.plan.PlanController;
import cn.hamm.spms.module.mes.plan.PlanService;
import cn.hamm.spms.module.wms.input.InputController;
import cn.hamm.spms.module.wms.input.InputService;
import cn.hamm.spms.module.wms.inventory.InventoryController;
import cn.hamm.spms.module.wms.inventory.InventoryService;
import cn.hamm.spms.module.wms.move.MoveController;
import cn.hamm.spms.module.wms.move.MoveService;
import cn.hamm.spms.module.wms.output.OutputController;
import cn.hamm.spms.module.wms.output.OutputService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <h1>八资源 count/stat 接线自检</h1>
 *
 * <p>七个资源运行时测试逐资源断言了 Mapper 的 {@code statByPermission} 与 service 的
 * {@code getMapperCount/getMapperStat} 同值；本类补的是<b>全量接线</b>这一面：开关打开时，
 * 八个 Controller 都暴露 {@code getCount/getStat} 端点、八个 Service 都有同名的公开方法且返回
 * 类型统一（{@code long} / {@link ReadStat}）、八个 Service 都报告 {@code isMapperReadEnabled()==true}。
 * 任何一处漏接（端点没加、方法名漂了、返回类型不一致）都会在这里红，而不是等真实前端联调才暴露。</p>
 *
 * <p>本类只反射、不查数据、不开权限上下文，因此不 seed、不依赖 data_policy；档位钉在
 * {@code mapper-read.enabled=true}（这是八个运行时测试已证明能启动的组合），web 环境用 MOCK。</p>
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
@DisplayName("八资源 count/stat 接线自检")
class CountStatWiringTest {

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

    @Test
    @DisplayName("八个 Controller 都暴露 getCount/getStat 端点")
    void allControllersExposeCountAndStatEndpoints() {
        assertEndpoint(materialController.getClass(), "getCount");
        assertEndpoint(materialController.getClass(), "getStat");
        assertEndpoint(inventoryController.getClass(), "getCount");
        assertEndpoint(inventoryController.getClass(), "getStat");
        assertEndpoint(inputController.getClass(), "getCount");
        assertEndpoint(inputController.getClass(), "getStat");
        assertEndpoint(outputController.getClass(), "getCount");
        assertEndpoint(outputController.getClass(), "getStat");
        assertEndpoint(moveController.getClass(), "getCount");
        assertEndpoint(moveController.getClass(), "getStat");
        assertEndpoint(orderController.getClass(), "getCount");
        assertEndpoint(orderController.getClass(), "getStat");
        assertEndpoint(planController.getClass(), "getCount");
        assertEndpoint(planController.getClass(), "getStat");
        assertEndpoint(pickingController.getClass(), "getCount");
        assertEndpoint(pickingController.getClass(), "getStat");
    }

    @Test
    @DisplayName("八个 Service 都有 getMapperCount/getMapperStat，返回类型统一")
    void allServicesExposeUniformCountStatMethods() {
        assertServiceMethods(materialService.getClass());
        assertServiceMethods(inventoryService.getClass());
        assertServiceMethods(inputService.getClass());
        assertServiceMethods(outputService.getClass());
        assertServiceMethods(moveService.getClass());
        assertServiceMethods(orderService.getClass());
        assertServiceMethods(planService.getClass());
        assertServiceMethods(pickingService.getClass());
    }

    @Test
    @DisplayName("八个 Service 在开关打开时都报告 isMapperReadEnabled=true")
    void allServicesReportMapperReadEnabled() {
        assertTrue(materialService.isMapperReadEnabled());
        assertTrue(inventoryService.isMapperReadEnabled());
        assertTrue(inputService.isMapperReadEnabled());
        assertTrue(outputService.isMapperReadEnabled());
        assertTrue(moveService.isMapperReadEnabled());
        assertTrue(orderService.isMapperReadEnabled());
        assertTrue(planService.isMapperReadEnabled());
        assertTrue(pickingService.isMapperReadEnabled());
    }

    private static void assertEndpoint(Class<?> controllerClass, String path) {
        for (Method method : controllerClass.getMethods()) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            if (mapping != null && Arrays.asList(mapping.value()).contains(path)) {
                return;
            }
        }
        fail(controllerClass.getSimpleName() + " 缺少端点 " + path);
    }

    private static void assertServiceMethods(Class<?> serviceClass) {
        Method count = findMethod(serviceClass, "getMapperCount");
        assertEquals(long.class, count.getReturnType(),
                serviceClass.getSimpleName() + ".getMapperCount 应返回 long");
        Method stat = findMethod(serviceClass, "getMapperStat");
        assertEquals(ReadStat.class, stat.getReturnType(),
                serviceClass.getSimpleName() + ".getMapperStat 应返回 ReadStat");
    }

    private static Method findMethod(Class<?> clazz, String name) {
        return Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(clazz.getSimpleName() + " 缺少方法 " + name));
    }
}
