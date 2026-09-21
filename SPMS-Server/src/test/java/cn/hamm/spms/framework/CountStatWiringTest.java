package cn.hamm.spms.framework;

import cn.hamm.spms.framework.query.QueryListRequest;
import cn.hamm.spms.base.mapper.ReadStat;
import cn.hamm.spms.module.asset.contract.ContractController;
import cn.hamm.spms.module.asset.contract.ContractService;
import cn.hamm.spms.module.asset.material.MaterialController;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.channel.customer.CustomerController;
import cn.hamm.spms.module.channel.customer.CustomerService;
import cn.hamm.spms.module.channel.purchase.PurchaseController;
import cn.hamm.spms.module.channel.purchase.PurchaseService;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceController;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceService;
import cn.hamm.spms.module.channel.sale.SaleController;
import cn.hamm.spms.module.channel.sale.SaleService;
import cn.hamm.spms.module.channel.saleprice.SalePriceController;
import cn.hamm.spms.module.channel.saleprice.SalePriceService;
import cn.hamm.spms.module.channel.supplier.SupplierController;
import cn.hamm.spms.module.channel.supplier.SupplierService;
import cn.hamm.spms.module.factory.storage.StorageController;
import cn.hamm.spms.module.factory.storage.StorageService;
import cn.hamm.spms.module.factory.structure.StructureController;
import cn.hamm.spms.module.factory.structure.StructureService;
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
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * <h1>十四资源 count/stat 接线自检</h1>
 *
 * <p>十四个资源运行时测试逐资源断言了 Mapper 的 {@code statByPermission} 与 service 的
 * {@code getMapperCount/getMapperStat} 同值；本类补的是<b>全量接线</b>这一面：开关打开时，
 * 十四个 Controller 都暴露 {@code getCount/getStat} 端点、十四个 Service 都有同名的公开方法且返回
 * 类型统一（{@code long} / {@link ReadStat}）、十四个 Service 都报告 {@code isMapperReadEnabled()==true}、
 * 十四个 Controller 都覆写了 {@code export(QueryListRequest)}（导出换轨的入口）、十四个读侧 Mapper XML
 * 只注册 SELECT 语句（写路径不许混入读侧 Mapper）。任何一处漏接或漂移都会在这里红，
 * 而不是等真实前端联调才暴露。</p>
 *
 * <p>本类只反射/扫资源、不查数据、不开权限上下文，因此不 seed、不依赖 data_policy；档位钉在
 * {@code mapper-read.enabled=true}（这是十四个运行时测试已证明能启动的组合），web 环境用 MOCK。</p>
 *
 * @author Hamm.cn
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "kubo.data-permission.mode=enforce",
        "kubo.data-permission.mapper-read.enabled=true"
}, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("十四资源 count/stat 接线自检")
class CountStatWiringTest {

    @Autowired
    private MaterialController materialController;
    @Autowired
    private PurchaseController purchaseController;
    @Autowired
    private SaleController saleController;
    @Autowired
    private CustomerController customerController;
    @Autowired
    private SupplierController supplierController;
    @Autowired
    private PurchasePriceController purchasePriceController;
    @Autowired
    private SalePriceController salePriceController;
    @Autowired
    private InventoryController inventoryController;
    @Autowired
    private InputController inputController;
    @Autowired
    private OutputController outputController;
    @Autowired
    private MoveController moveController;
    @Autowired
    private ContractController contractController;
    @Autowired
    private StorageController storageController;
    @Autowired
    private StructureController structureController;

    @Autowired
    private MaterialService materialService;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private PurchasePriceService purchasePriceService;
    @Autowired
    private SalePriceService salePriceService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InputService inputService;
    @Autowired
    private OutputService outputService;
    @Autowired
    private MoveService moveService;
    @Autowired
    private ContractService contractService;
    @Autowired
    private StorageService storageService;
    @Autowired
    private StructureService structureService;

    @Test
    @DisplayName("十四个 Controller 都暴露 getCount/getStat 端点")
    void allControllersExposeCountAndStatEndpoints() {
        assertEndpoint(materialController, "getCount");
        assertEndpoint(materialController, "getStat");
        assertEndpoint(inventoryController, "getCount");
        assertEndpoint(inventoryController, "getStat");
        assertEndpoint(inputController, "getCount");
        assertEndpoint(inputController, "getStat");
        assertEndpoint(outputController, "getCount");
        assertEndpoint(outputController, "getStat");
        assertEndpoint(moveController, "getCount");
        assertEndpoint(moveController, "getStat");
        assertEndpoint(purchaseController, "getCount");
        assertEndpoint(purchaseController, "getStat");
        assertEndpoint(saleController, "getCount");
        assertEndpoint(saleController, "getStat");
        assertEndpoint(customerController, "getCount");
        assertEndpoint(customerController, "getStat");
        assertEndpoint(supplierController, "getCount");
        assertEndpoint(supplierController, "getStat");
        assertEndpoint(purchasePriceController, "getCount");
        assertEndpoint(purchasePriceController, "getStat");
        assertEndpoint(salePriceController, "getCount");
        assertEndpoint(salePriceController, "getStat");
        assertEndpoint(contractController, "getCount");
        assertEndpoint(contractController, "getStat");
        assertEndpoint(storageController, "getCount");
        assertEndpoint(storageController, "getStat");
        assertEndpoint(structureController, "getCount");
        assertEndpoint(structureController, "getStat");
    }

    @Test
    @DisplayName("十四个 Service 都有 getMapperCount/getMapperStat，返回类型统一")
    void allServicesExposeUniformCountStatMethods() {
        assertServiceMethods(materialService);
        assertServiceMethods(inventoryService);
        assertServiceMethods(inputService);
        assertServiceMethods(outputService);
        assertServiceMethods(moveService);
        assertServiceMethods(purchaseService);
        assertServiceMethods(saleService);
        assertServiceMethods(customerService);
        assertServiceMethods(supplierService);
        assertServiceMethods(purchasePriceService);
        assertServiceMethods(salePriceService);
        assertServiceMethods(contractService);
        assertServiceMethods(storageService);
        assertServiceMethods(structureService);
    }

    @Test
    @DisplayName("十四个 Service 在开关打开时都报告 isMapperReadEnabled=true")
    void allServicesReportMapperReadEnabled() {
        assertTrue(materialService.isMapperReadEnabled());
        assertTrue(inventoryService.isMapperReadEnabled());
        assertTrue(inputService.isMapperReadEnabled());
        assertTrue(outputService.isMapperReadEnabled());
        assertTrue(moveService.isMapperReadEnabled());
        assertTrue(purchaseService.isMapperReadEnabled());
        assertTrue(saleService.isMapperReadEnabled());
        assertTrue(customerService.isMapperReadEnabled());
        assertTrue(supplierService.isMapperReadEnabled());
        assertTrue(purchasePriceService.isMapperReadEnabled());
        assertTrue(salePriceService.isMapperReadEnabled());
        assertTrue(contractService.isMapperReadEnabled());
        assertTrue(storageService.isMapperReadEnabled());
        assertTrue(structureService.isMapperReadEnabled());
    }

    @Test
    @DisplayName("十四个 Controller 都覆写了 export(QueryListRequest)（导出换轨入口）")
    void allControllersOverrideExportMethod() {
        assertOverridesExport(materialController);
        assertOverridesExport(inventoryController);
        assertOverridesExport(inputController);
        assertOverridesExport(outputController);
        assertOverridesExport(moveController);
        assertOverridesExport(purchaseController);
        assertOverridesExport(saleController);
        assertOverridesExport(customerController);
        assertOverridesExport(supplierController);
        assertOverridesExport(purchasePriceController);
        assertOverridesExport(salePriceController);
        assertOverridesExport(contractController);
        assertOverridesExport(storageController);
        assertOverridesExport(structureController);
    }

    @Test
    @DisplayName("十四个读侧 Mapper XML 只注册 SELECT 语句，写语句不许混入")
    void readMapperXmlRegistersOnlySelectStatements() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/**/*Mapper.xml");
        assertFalse(resources.length == 0, "classpath 下应能找到读侧 Mapper XML");
        for (Resource resource : resources) {
            String xml = resource.getContentAsString(StandardCharsets.UTF_8);
            assertNoWriteStatement(resource.getFilename(), xml);
        }
    }

    private static void assertOverridesExport(Object controller) {
        Class<?> targetClass = AopUtils.getTargetClass(controller);
        try {
            // getDeclaredMethod 只找本类声明的：没覆写 export 的 Controller 走父类默认 JPA 导出，
            // 这里会直接 NoSuchMethodException，正是我们要防的“悄悄走回框架默认导出”。
            targetClass.getDeclaredMethod("export", QueryListRequest.class);
        } catch (NoSuchMethodException e) {
            fail(targetClass.getSimpleName() + " 未覆写 export(QueryListRequest)，导出会走框架默认 JPA 路径");
        }
    }

    /**
     * 断言一份 Mapper XML 没有写语句（insert/update/delete）。
     *
     * <p>MyBatis-Plus 会给 {@code BaseMapper} 的抽象方法生成写语句，但那不是我们手写的 XML 语句；
     * 这里只看自己仓库 {@code mapper} 目录下的 {@code *Mapper.xml}，与 {@code KuboBaseMapper} 纪律二对齐：
     * 读侧 Mapper 只许承载 SELECT。剥掉 XML 注释后按标签名匹配，避免 DOCTYPE/DTD 与注释干扰。</p>
     */
    private static void assertNoWriteStatement(String filename, String xml) {
        String withoutComments = XML_COMMENT.matcher(xml).replaceAll("");
        assertFalse(WRITE_STATEMENT.matcher(withoutComments).find(),
                filename + " 出现了写语句标签（insert/update/delete），读侧 Mapper 只许 SELECT");
    }

    private static final Pattern XML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern WRITE_STATEMENT = Pattern.compile("<\\s*(insert|update|delete)\\b", Pattern.CASE_INSENSITIVE);

    private static void assertEndpoint(Object controller, String path) {
        // Controller 会被 Spring 用 CGLIB 代理（@Validated/@Permission 触发），代理类的重写方法不带
        // @PostMapping，必须解包到目标类再反射，否则会误报“缺少端点”。
        Class<?> controllerClass = AopUtils.getTargetClass(controller);
        for (Method method : controllerClass.getMethods()) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            if (mapping != null && Arrays.asList(mapping.value()).contains(path)) {
                return;
            }
        }
        fail(controllerClass.getSimpleName() + " 缺少端点 " + path);
    }

    private static void assertServiceMethods(Object service) {
        Class<?> serviceClass = AopUtils.getTargetClass(service);
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
