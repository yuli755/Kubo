package cn.hamm.spms;

import cn.hamm.airpower.ai.mcp.McpService;
import cn.hamm.airpower.core.RandomUtil;
import cn.hamm.airpower.curd.permission.PermissionUtil;
import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.common.Configs;
import cn.hamm.spms.module.asset.AssetServices;
import cn.hamm.spms.module.asset.device.DeviceEntity;
import cn.hamm.spms.module.asset.device.DeviceService;
import cn.hamm.spms.module.asset.material.MaterialEntity;
import cn.hamm.spms.module.asset.material.MaterialService;
import cn.hamm.spms.module.asset.material.enums.MaterialType;
import cn.hamm.spms.module.channel.ChannelServices;
import cn.hamm.spms.module.channel.customer.CustomerEntity;
import cn.hamm.spms.module.channel.customer.CustomerService;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceEntity;
import cn.hamm.spms.module.channel.purchaseprice.PurchasePriceService;
import cn.hamm.spms.module.channel.saleprice.SalePriceEntity;
import cn.hamm.spms.module.channel.saleprice.SalePriceService;
import cn.hamm.spms.module.channel.supplier.SupplierEntity;
import cn.hamm.spms.module.channel.supplier.SupplierService;
import cn.hamm.spms.module.chat.ChatServices;
import cn.hamm.spms.module.chat.room.RoomEntity;
import cn.hamm.spms.module.chat.room.RoomService;
import cn.hamm.spms.module.factory.FactoryServices;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.storage.StorageService;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.factory.structure.StructureService;
import cn.hamm.spms.module.iot.IotServices;
import cn.hamm.spms.module.iot.parameter.ParameterEntity;
import cn.hamm.spms.module.iot.parameter.ParameterService;
import cn.hamm.spms.module.iot.report.enums.ReportDataType;
import cn.hamm.spms.module.mes.MesServices;
import cn.hamm.spms.module.mes.bom.BomEntity;
import cn.hamm.spms.module.mes.bom.BomService;
import cn.hamm.spms.module.mes.bom.detail.BomDetailEntity;
import cn.hamm.spms.module.mes.bom.enums.BomType;
import cn.hamm.spms.module.mes.operation.OperationEntity;
import cn.hamm.spms.module.mes.operation.OperationService;
import cn.hamm.spms.module.mes.routing.RoutingEntity;
import cn.hamm.spms.module.mes.routing.RoutingService;
import cn.hamm.spms.module.mes.routing.operation.RoutingOperationEntity;
import cn.hamm.spms.module.personnel.PersonnelServices;
import cn.hamm.spms.module.personnel.department.DepartmentEntity;
import cn.hamm.spms.module.personnel.department.DepartmentService;
import cn.hamm.spms.module.personnel.role.RoleEntity;
import cn.hamm.spms.module.personnel.role.RoleService;
import cn.hamm.spms.module.personnel.user.UserEntity;
import cn.hamm.spms.module.personnel.user.UserService;
import cn.hamm.spms.module.system.SystemServices;
import cn.hamm.spms.module.system.coderule.CodeRuleEntity;
import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.coderule.enums.CodeRuleField;
import cn.hamm.spms.module.system.config.ConfigEntity;
import cn.hamm.spms.module.system.config.ConfigService;
import cn.hamm.spms.module.system.config.enums.ConfigFlag;
import cn.hamm.spms.module.system.menu.MenuService;
import cn.hamm.spms.module.system.permission.PermissionEntity;
import cn.hamm.spms.module.system.permission.PermissionService;
import cn.hamm.spms.module.system.unit.UnitEntity;
import cn.hamm.spms.module.system.unit.UnitService;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static cn.hamm.spms.module.iot.report.ReportConstant.*;

/**
 * <h1>初始化</h1>
 *
 * @author Hamm.cn
 */
@Component
@Slf4j
public class DevDataInitRunner implements CommandLineRunner {
    public static final int TWO = 2;
    private static final String CREATE_DROP = "create-drop";
    private static final String LOCK_FILE = "init.lock";

    @Autowired
    private Environment environment;

    @Override
    public void run(String... args) {
        AppConfig appConfig = Configs.getAppConfig();
        PermissionService permissionService = SystemServices.getPermissionService();
        MenuService menuService = SystemServices.getMenuService();
        McpService.scanMcpMethods("cn.hamm.spms", "cn.hamm.airpower");
        if (!appConfig.getIsDevMode()) {
            log.info("非开发者模式，无需初始化数据");
            return;
        }
        // 判断运行目录是否存在 init.lock 文件，如存在，则不初始化数据
        String ddlAuto = "spring.jpa.hibernate.ddl-auto";
        if (new File(LOCK_FILE).exists() && !CREATE_DROP.equals(environment.getProperty(ddlAuto))) {
            log.info("已存在 init.lock 文件，无需初始化数据");
            return;
        }
        permissionService.initMcpToolPermission(McpService.tools);
        initRootUser();
        initCodeRules();
        initConfigs();
        initParameters();
        permissionService.loadPermission();
        permissionService.ensureDataPermissionSystemPermissions();
        initDataPermissionResources();
        initRootAuthorization();
        menuService.initMenu();
        initDevData();
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(LOCK_FILE).createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void initDataPermissionResources() {
        DataResourceService dataResourceService = SystemServices.getDataResourceService();
        DataPolicyService dataPolicyService = SystemServices.getDataPolicyService();

        // 注册 wms.inventory 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("wms.inventory")).isEmpty()) {
            DataResourceEntity inventoryResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.inventory")
                            .setTableName("inventory")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("wms_inventory_tenant_isolation")
                            .setResourceId(inventoryResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 wms.input 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("wms.input")).isEmpty()) {
            DataResourceEntity inputResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.input")
                            .setTableName("input")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("wms_input_tenant_isolation")
                            .setResourceId(inputResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 wms.output 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("wms.output")).isEmpty()) {
            DataResourceEntity outputResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.output")
                            .setTableName("output")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("wms_output_tenant_isolation")
                            .setResourceId(outputResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 wms.move 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("wms.move")).isEmpty()) {
            DataResourceEntity moveResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.move")
                            .setTableName("move")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("wms_move_tenant_isolation")
                            .setResourceId(moveResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.order 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.order")).isEmpty()) {
            DataResourceEntity orderResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.order")
                            .setTableName("order_table")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            // 创建默认租户隔离策略
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_order_tenant_isolation")
                            .setResourceId(orderResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.plan 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.plan")).isEmpty()) {
            DataResourceEntity planResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.plan")
                            .setTableName("plan")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_plan_tenant_isolation")
                            .setResourceId(planResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.picking 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.picking")).isEmpty()) {
            DataResourceEntity pickingResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.picking")
                            .setTableName("picking")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_picking_tenant_isolation")
                            .setResourceId(pickingResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.bom 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.bom")).isEmpty()) {
            DataResourceEntity bomResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.bom")
                            .setTableName("bom")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_bom_tenant_isolation")
                            .setResourceId(bomResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.routing 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.routing")).isEmpty()) {
            DataResourceEntity routingResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.routing")
                            .setTableName("routing")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_routing_tenant_isolation")
                            .setResourceId(routingResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 mes.operation 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("mes.operation")).isEmpty()) {
            DataResourceEntity operationResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("mes.operation")
                            .setTableName("operation")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("mes_operation_tenant_isolation")
                            .setResourceId(operationResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.sale 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.sale")).isEmpty()) {
            DataResourceEntity saleResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.sale")
                            .setTableName("sale")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_sale_tenant_isolation")
                            .setResourceId(saleResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.purchase 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.purchase")).isEmpty()) {
            DataResourceEntity purchaseResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.purchase")
                            .setTableName("purchase")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_purchase_tenant_isolation")
                            .setResourceId(purchaseResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.supplier 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.supplier")).isEmpty()) {
            DataResourceEntity supplierResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.supplier")
                            .setTableName("supplier")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_supplier_tenant_isolation")
                            .setResourceId(supplierResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.customer 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.customer")).isEmpty()) {
            DataResourceEntity customerResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.customer")
                            .setTableName("customer")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_customer_tenant_isolation")
                            .setResourceId(customerResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.salePrice 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.salePrice")).isEmpty()) {
            DataResourceEntity salePriceResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.salePrice")
                            .setTableName("sale_price")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_sale_price_tenant_isolation")
                            .setResourceId(salePriceResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 channel.purchasePrice 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("channel.purchasePrice")).isEmpty()) {
            DataResourceEntity purchasePriceResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("channel.purchasePrice")
                            .setTableName("purchase_price")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("channel_purchase_price_tenant_isolation")
                            .setResourceId(purchasePriceResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 asset.contract 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("asset.contract")).isEmpty()) {
            DataResourceEntity contractResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("asset.contract")
                            .setTableName("contract")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("asset_contract_tenant_isolation")
                            .setResourceId(contractResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 asset.device 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("asset.device")).isEmpty()) {
            DataResourceEntity deviceResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("asset.device")
                            .setTableName("device")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("asset_device_tenant_isolation")
                            .setResourceId(deviceResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 asset.material 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("asset.material")).isEmpty()) {
            DataResourceEntity materialResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("asset.material")
                            .setTableName("material")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("asset_material_tenant_isolation")
                            .setResourceId(materialResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 factory.storage 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("factory.storage")).isEmpty()) {
            DataResourceEntity storageResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("factory.storage")
                            .setTableName("storage")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("factory_storage_tenant_isolation")
                            .setResourceId(storageResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 factory.structure 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("factory.structure")).isEmpty()) {
            DataResourceEntity structureResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("factory.structure")
                            .setTableName("structure")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("factory_structure_tenant_isolation")
                            .setResourceId(structureResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 personnel.user 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("personnel.user")).isEmpty()) {
            DataResourceEntity userResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("personnel.user")
                            .setTableName("user")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("personnel_user_tenant_isolation")
                            .setResourceId(userResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 personnel.role 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("personnel.role")).isEmpty()) {
            DataResourceEntity roleResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("personnel.role")
                            .setTableName("role")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("personnel_role_tenant_isolation")
                            .setResourceId(roleResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }

        // 注册 personnel.department 数据资源
        if (dataResourceService.filter(new DataResourceEntity().setResourceKey("personnel.department")).isEmpty()) {
            DataResourceEntity departmentResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("personnel.department")
                            .setTableName("department")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            .setSupportedActions("QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT")
                            .setStatus("ENABLED")
            );
            dataPolicyService.addAndGet(
                    new DataPolicyEntity()
                            .setPolicyCode("personnel_department_tenant_isolation")
                            .setResourceId(departmentResource.getId())
                            .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                            .setActionName("QUERY")
                            .setConditionJson("{}")
                            .setPriority(100)
                            .setEnabled(true)
            );
        }
    }

    private void initRootAuthorization() {
        UserService userService = PersonnelServices.getUserService();
        RoleService roleService = PersonnelServices.getRoleService();
        PermissionService permissionService = SystemServices.getPermissionService();
        UserEntity rootUser = userService.getMaybeNull(1L);
        if (rootUser == null) {
            return;
        }
        RoleEntity rootRole = roleService.filter(new RoleEntity().setCode("SYSTEM_ROOT"))
                .stream()
                .findFirst()
                .orElseGet(() -> roleService.addAndGet(new RoleEntity()
                        .setName("系统超级管理员")
                        .setCode("SYSTEM_ROOT")));
        rootRole.setPermissionList(new HashSet<>(permissionService.filter(new PermissionEntity())));
        roleService.updateToDatabase(rootRole);
        Set<RoleEntity> roles = rootUser.getRoleList() == null
                ? new HashSet<>()
                : new HashSet<>(rootUser.getRoleList());
        roles.add(rootRole);
        rootUser.setRoleList(roles);
        userService.updateToDatabase(rootUser);
    }
    private void initParameters() {
        ParameterService parameterService = IotServices.getParameterService();
        ParameterEntity parameter;

        parameter = parameterService.getByCode(REPORT_KEY_OF_STATUS);
        if (Objects.isNull(parameter)) {
            parameterService.addAndGet(new ParameterEntity()
                    .setCode(REPORT_KEY_OF_STATUS)
                    .setLabel("运行状态")
                    .setDataType(ReportDataType.STATUS.getKey())
                    .setIsSystem(true)
            );
        }
        parameter = parameterService.getByCode(REPORT_KEY_OF_ALARM);
        if (Objects.isNull(parameter)) {
            parameterService.addAndGet(new ParameterEntity()
                    .setCode(REPORT_KEY_OF_ALARM)
                    .setLabel("报警状态")
                    .setDataType(ReportDataType.STATUS.getKey())
                    .setIsSystem(true)
            );
        }
        parameter = parameterService.getByCode(REPORT_KEY_OF_PART_COUNT);
        if (Objects.isNull(parameter)) {
            parameterService.addAndGet(new ParameterEntity()
                    .setCode(REPORT_KEY_OF_PART_COUNT)
                    .setLabel("实时产量")
                    .setDataType(ReportDataType.NUMBER.getKey())
                    .setIsSystem(true)
            );
        }
    }

    private void initCodeRules() {
        CodeRuleService codeRuleService = SystemServices.getCodeRuleService();
        CodeRuleField[] codeRuleFields = CodeRuleField.class.getEnumConstants();
        for (CodeRuleField codeRuleField : codeRuleFields) {
            CodeRuleEntity codeRule = codeRuleService.getByRuleField(codeRuleField.getKey());
            if (Objects.isNull(codeRule)) {
                codeRuleService.addAndGet(
                        new CodeRuleEntity()
                                .setIsSystem(true)
                                .setRuleField(codeRuleField.getKey())
                                .setPrefix(codeRuleField.getDefaultPrefix())
                                .setTemplate(codeRuleField.getDefaultSnType().getDefaultTemplate())
                                .setSnType(codeRuleField.getDefaultSnType().getKey())
                );
            }
        }
    }

    private void initRootUser() {
        UserService userService = PersonnelServices.getUserService();
        DepartmentService departmentService = PersonnelServices.getDepartmentService();
        RoomService roomService = ChatServices.getRoomService();

        // 初始化用户
        UserEntity user = userService.getMaybeNull(1L);
        if (Objects.nonNull(user)) {
            return;
        }
        DepartmentEntity department = departmentService.addAndGet(new DepartmentEntity().setCode("a1").setName("生产部"));
        departmentService.addAndGet(new DepartmentEntity().setName("材料部").setCode("aaa1").setParentId(department.getId()));
        String salt = RandomUtil.randomString(UserService.PASSWORD_SALT_LENGTH);
        userService.add(new UserEntity()
                .setNickname("凌小云")
                .setPhone("17666666666")
                .setEmail("admin@hamm.cn")
                .setPassword(PermissionUtil.encodePassword("Aa123456", salt))
                .setSalt(salt)
        );
        userService.addAndGet(new UserEntity()
                        .setNickname("张三")
                        .setPhone("13888888888")
                        .setEmail("admin@hamm.com")
                        .setPassword(PermissionUtil.encodePassword("Aa123456", salt))
                        .setSalt(salt))
                .setDepartmentList(Set.of(department))

        ;
        user = userService.getMaybeNull(1L);

        roomService.add(new RoomEntity()
                .setName("广场")
                .setCode(666)
                .setIsOfficial(true)
                .setIsHot(true).setOwner(user)
        );

        roomService.addAndGet(new RoomEntity()
                .setName("��试")
                .setCode(888)
                .setIsHot(true).setOwner(user)
        );
    }

    private void initConfigs() {
        ConfigService configService = SystemServices.getConfigService();
        ConfigFlag[] configFlags = ConfigFlag.class.getEnumConstants();
        for (ConfigFlag configFlag : configFlags) {
            try {
                ConfigEntity config = configService.get(configFlag);
                if (Objects.nonNull(config)) {
                    continue;
                }
            } catch (RuntimeException exception) {
                log.info("需要初始化配置");
            }
            configService.addAndGet(new ConfigEntity()
                    .setConfig(configFlag.getDefaultValue())
                    .setType(configFlag.getType().getKey())
                    .setName(configFlag.getLabel())
                    .setFlag(configFlag.name())
                    .setDescription(configFlag.getDescription())
            );
        }
    }

    @SuppressWarnings("AlibabaMethodTooLong")
    private void initDevData() {
        DeviceService deviceService = AssetServices.getDeviceService();
        MaterialService materialService = AssetServices.getMaterialService();
        UnitService unitService = SystemServices.getUnitService();
        CustomerService customerService = ChannelServices.getCustomerService();
        SalePriceService salePriceService = ChannelServices.getSalePriceService();
        SupplierService supplierService = ChannelServices.getSupplierService();
        PurchasePriceService purchasePriceService = ChannelServices.getPurchasePriceService();

        StorageService storageService = FactoryServices.getStorageService();
        StructureService structureService = FactoryServices.getStructureService();

        OperationService operationService = MesServices.getOperationService();
        BomService bomService = MesServices.getBomService();
        RoutingService routingService = MesServices.getRoutingService();

        // ��等：��查设备是否已存在
        if (deviceService.filter(new DeviceEntity()).isEmpty()) {
            int deviceCount = 2;
            for (int i = 0; i < deviceCount; i++) {
                deviceService.addAndGet(new DeviceEntity().setCode("Simulator00" + (i + 1)).setName("设备" + (i + 1)));
            }
        }

        // ��等：��查单位是否已存在
        UnitEntity unit = unitService.filter(new UnitEntity().setName("台")).stream().findFirst().orElse(null);
        if (Objects.isNull(unit)) {
            unit = new UnitEntity();
            unit.setName("台");
            unit = unitService.addAndGet(unit);
        }

        // ��等：��查物料是否已存在
        MaterialEntity material = materialService.filter(new MaterialEntity().setName("MacBook Pro M4 Max")).stream().findFirst().orElse(null);
        if (Objects.isNull(material)) {
            material = new MaterialEntity()
                    .setMaterialType(MaterialType.PURCHASE.getKey())
                    .setName("MacBook Pro M4 Max")
                    .setSpc("32G-1TB")
                    .setUnit(unit)
                    .setPurchasePrice(28000D)
                    .setSalePrice(29999D);
            material = materialService.addAndGet(material);
        }

        // ��等：��查客户是否已存在
        CustomerEntity customer = customerService.filter(new CustomerEntity().setName("凌云科技")).stream().findFirst().orElse(null);
        if (Objects.isNull(customer)) {
            customer = new CustomerEntity();
            customer.setName("凌云科技").setPhone("17666666666");
            customer = customerService.addAndGet(customer);
        }

        // ��等：��查供应商是否已存在
        SupplierEntity supplier = supplierService.filter(new SupplierEntity().setName("Apple 中国")).stream().findFirst().orElse(null);
        if (Objects.isNull(supplier)) {
            supplier = new SupplierEntity();
            supplier.setName("Apple 中国").setPhone("17666666666");
            supplier = supplierService.addAndGet(supplier);
        }

        // ��等：价格实体
        if (salePriceService.filter(new SalePriceEntity()).isEmpty()) {
            SalePriceEntity salePrice = new SalePriceEntity();
            salePrice.setCustomer(customer).setPrice(29999D).setMaterial(material);
            salePriceService.addAndGet(salePrice);
        }
        if (purchasePriceService.filter(new PurchasePriceEntity()).isEmpty()) {
            PurchasePriceEntity purchasePrice = new PurchasePriceEntity();
            purchasePrice.setSupplier(supplier).setPrice(28000D).setMaterial(material);
            purchasePriceService.addAndGet(purchasePrice);
        }

        // ��等：��库
        if (storageService.filter(new StorageEntity()).isEmpty()) {
            StorageEntity storage = new StorageEntity().setName("东部大��");
            storageService.addAndGet(storage);

            for (int i = 0; i < TWO; i++) {
                storageService.addAndGet(new StorageEntity()
                        .setParentId(storage.getId())
                        .setName(String.format("东部%s��", (i + 1)))
                );
            }

            storage = new StorageEntity().setName("西部大��");
            storage = storageService.addAndGet(storage);

            for (int i = 0; i < TWO; i++) {
                storageService.addAndGet(new StorageEntity()
                        .setParentId(storage.getId())
                        .setName(String.format("西部%s��", (i + 1)))
                );
            }
        }

        // ��等：工序
        if (operationService.filter(new OperationEntity()).isEmpty()) {
            operationService.addAndGet(new OperationEntity().setName("����安装"));
            operationService.addAndGet(new OperationEntity().setName("��幕组装"));
            operationService.addAndGet(new OperationEntity().setName("系统安装"));
        }
        OperationEntity operationKeyboard = operationService.filter(new OperationEntity().setName("����安装")).stream().findFirst().orElse(null);
        OperationEntity operationScreen = operationService.filter(new OperationEntity().setName("��幕组装")).stream().findFirst().orElse(null);
        OperationEntity operationSystem = operationService.filter(new OperationEntity().setName("系统安装")).stream().findFirst().orElse(null);

        // ��等：物料(����/��幕)
        if (materialService.filter(new MaterialEntity()).size() == 1) { // 只有 MacBook Pro M4 Max
            materialService.addAndGet(new MaterialEntity().setName("����").setMaterialType(MaterialType.PURCHASE.getKey()).setUnit(unit));
            materialService.addAndGet(new MaterialEntity().setName("��幕").setMaterialType(MaterialType.PURCHASE.getKey()).setUnit(unit));
        }
        MaterialEntity materialKeyboard = materialService.filter(new MaterialEntity().setName("����")).stream().findFirst().orElse(null);
        MaterialEntity materialScreen = materialService.filter(new MaterialEntity().setName("��幕")).stream().findFirst().orElse(null);

        // ��等：BOM
        if (bomService.filter(new BomEntity()).isEmpty()) {
            bomService.addAndGet(new BomEntity().setName("��记本电��清单").setType(BomType.NORMAL.getKey()).setDetails(
                    new HashSet<>(Arrays.asList(
                            new BomDetailEntity().setMaterial(materialKeyboard).setQuantity(1D),
                            new BomDetailEntity().setMaterial(materialScreen).setQuantity(1D)
                    ))
            ));

            bomService.addAndGet(new BomEntity().setName("����安装清单").setType(BomType.OPERATION.getKey()).setDetails(
                    new HashSet<>(Collections.singletonList(
                            new BomDetailEntity().setMaterial(materialKeyboard).setQuantity(1D)
                    ))
            ));
            bomService.addAndGet(new BomEntity().setName("��幕组装清单").setType(BomType.OPERATION.getKey()).setDetails(
                    new HashSet<>(Collections.singletonList(
                            new BomDetailEntity().setMaterial(materialScreen).setQuantity(1D)
                    ))
            ));
        }
        BomEntity bomComputer = bomService.filter(new BomEntity().setName("��记本电��清单")).stream().findFirst().orElse(null);
        BomEntity bomKeyboard = bomService.filter(new BomEntity().setName("����安装清单")).stream().findFirst().orElse(null);
        BomEntity bomIdScreen = bomService.filter(new BomEntity().setName("��幕组装清单")).stream().findFirst().orElse(null);

        // ��等：工艺路线
        if (routingService.filter(new RoutingEntity()).isEmpty()) {
            routingService.addAndGet(new RoutingEntity().setBom(bomComputer)
                    .setName("��记本安装")
                    .setMaterial(material)
                    .setDetails(Arrays.asList(
                            new RoutingOperationEntity().setBom(bomKeyboard)
                                    .setIsAutoNext(true)
                                    .setOperation(operationKeyboard),
                            new RoutingOperationEntity().setBom(bomIdScreen)
                                    .setIsAutoNext(true)
                                    .setOperation(operationScreen),
                            new RoutingOperationEntity().setOperation(operationSystem)
                    ))
            );
        }

        // ��等：产线结构
        if (structureService.filter(new StructureEntity()).isEmpty()) {
            StructureEntity structure = new StructureEntity().setName("��记本电��产线");
            structureService.addAndGet(structure);

            for (int i = 0; i < TWO; i++) {
                structureService.addAndGet(new StructureEntity()
                        .setParentId(structure.getId())
                        .setName(String.format("工位%s", (i + 1)))
                        .setOperationList(new HashSet<>(Arrays.asList(
                                operationKeyboard,
                                operationScreen,
                                operationSystem
                        )))
                );
            }
        }
    }
}

