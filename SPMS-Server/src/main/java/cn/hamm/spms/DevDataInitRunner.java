package cn.hamm.spms;

import cn.hamm.spms.framework.util.RandomValues;
import cn.hamm.spms.framework.security.PasswordHasher;
import cn.hamm.spms.common.AppConfig;
import cn.hamm.spms.common.Configs;
import cn.hamm.spms.module.asset.AssetServices;
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
import cn.hamm.spms.module.factory.FactoryServices;
import cn.hamm.spms.module.factory.storage.StorageEntity;
import cn.hamm.spms.module.factory.storage.StorageService;
import cn.hamm.spms.module.factory.structure.StructureEntity;
import cn.hamm.spms.module.factory.structure.StructureService;
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
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyProvider;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <h1>初始化</h1>
 *
 * @author Hamm.cn
 */
@Component
@Profile("!test")
@Slf4j
public class DevDataInitRunner implements CommandLineRunner {
    public static final int TWO = 2;
    private static final String CREATE_DROP = "create-drop";
    private static final String LOCK_FILE = "init.lock";

    @Autowired
    private Environment environment;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Override
    public void run(String... args) {
        PermissionService permissionService = SystemServices.getPermissionService();
        // 权限目录同步必须放在下面两道闸**之前**，且与它们无关。
        //
        // 此前它写在闸门之后（与 initRootUser 等开发数据初始化并列），于是：
        // 生产环境 isDevMode=false 直接 return，权限目录永不同步；而
        // RequestInterceptor.checkUserPermission 是从库里查权限、且在 SYSTEM_ROOT
        // 放行之前就 fail-closed 的 —— 结果是任何新增端点在生产上对所有人（含 root）
        // 都返回 403「权限不存在」，只能手工往 permission 表塞数据。
        // 本地开发同理：init.lock 一旦存在，后加的接口也永远扫不进来。
        //
        // 选择改造本 runner 而不是新建一个 CommandLineRunner，是为了不引入 runner 之间
        // 的顺序不确定性：下面的 initRootAuthorization 依赖权限目录已就绪，
        // 同一个 run() 里的先后顺序是确定的，跨 bean 则要额外实现 Ordered 才敢保证。
        permissionService.syncPermissionCatalog();

        AppConfig appConfig = Configs.getAppConfig();
        MenuService menuService = SystemServices.getMenuService();
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
        initRootUser();
        initCodeRules();
        initConfigs();
        // loadPermission() 与 ensureDataPermissionSystemPermissions() 已上移进
        // syncPermissionCatalog()：那里是权限目录的唯一写入口，避免两处各调一半导致口径漂移。
        initDataPermissionResources();
        initDataFieldPolicies();
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
        if (dataResourceService.getByResourceKey("wms.inventory") == null) {
            DataResourceEntity inventoryResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.inventory")
                            .setTableName("inventory")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            // D13-B：工厂归属沿 storage 关联走一跳（InventoryEntity.@ManyToOne storage）
                            .setOwnerColumns("FACTORY:storage.id")
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
        if (dataResourceService.getByResourceKey("wms.input") == null) {
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
        if (dataResourceService.getByResourceKey("wms.output") == null) {
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
        if (dataResourceService.getByResourceKey("wms.move") == null) {
            DataResourceEntity moveResource = dataResourceService.addAndGet(
                    new DataResourceEntity()
                            .setResourceKey("wms.move")
                            .setTableName("move")
                            .setTableAlias("t")
                            .setTenantColumn("tenant_id")
                            // D13-B：工厂归属沿 storage 关联走一跳（MoveEntity.@ManyToOne storage）
                            .setOwnerColumns("FACTORY:storage.id")
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

        // 注册 channel.sale 数据资源
        if (dataResourceService.getByResourceKey("channel.sale") == null) {
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
        if (dataResourceService.getByResourceKey("channel.purchase") == null) {
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
        if (dataResourceService.getByResourceKey("channel.supplier") == null) {
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
        if (dataResourceService.getByResourceKey("channel.customer") == null) {
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
        if (dataResourceService.getByResourceKey("channel.salePrice") == null) {
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
        if (dataResourceService.getByResourceKey("channel.purchasePrice") == null) {
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
        if (dataResourceService.getByResourceKey("asset.contract") == null) {
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

        // 注册 asset.material 数据资源
        if (dataResourceService.getByResourceKey("asset.material") == null) {
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
        if (dataResourceService.getByResourceKey("factory.storage") == null) {
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
        if (dataResourceService.getByResourceKey("factory.structure") == null) {
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
        if (dataResourceService.getByResourceKey("personnel.user") == null) {
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
        if (dataResourceService.getByResourceKey("personnel.role") == null) {
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
        if (dataResourceService.getByResourceKey("personnel.department") == null) {
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

        // 为支持写入操作的资源补充 CREATE/UPDATE/DELETE 策略
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "wms.input");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "wms.output");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "wms.move");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.sale");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.purchase");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.supplier");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.customer");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.salePrice");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "channel.purchasePrice");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "asset.contract");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "asset.material");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "factory.storage");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "factory.structure");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "personnel.department");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "personnel.role");
        addWritePoliciesForResource(dataPolicyService, dataResourceService, "personnel.user");
    }

    /**
     * 为指定资源补充 CREATE/UPDATE/DELETE 策略（若不存在且资源支持该动作）
     */
    private void addWritePoliciesForResource(DataPolicyService dataPolicyService, DataResourceService dataResourceService, String resourceKey) {
        DataResourceEntity resource = dataResourceService.getByResourceKey(resourceKey);
        if (resource == null) {
            return;
        }
        if (resource.getSupportedActions() == null || !resource.getSupportedActions().contains("CREATE")) {
            return;
        }
        long resourceId = resource.getId();
        for (DataPermissionAction action : List.of(DataPermissionAction.CREATE, DataPermissionAction.UPDATE, DataPermissionAction.DELETE)) {
            String policyCode = resourceKey.replace('.', '_') + '_' + action.name().toLowerCase();
            if (!dataPolicyService.existsPolicy(resourceId, action.name(), DataPermissionSubjectType.SYSTEM.name())) {
                dataPolicyService.addAndGet(new DataPolicyEntity()
                        .setPolicyCode(policyCode)
                        .setResourceId(resourceId)
                        .setSubjectType(DataPermissionSubjectType.SYSTEM.name())
                        .setActionName(action.name())
                        .setConditionJson("{}")
                        .setPriority(100)
                        .setEnabled(true));
            }
        }
    }

    private void initDataFieldPolicies() {
        DataFieldPolicyService dataFieldPolicyService = (DataFieldPolicyService) SystemServices.getDataFieldPolicyService();
        DataResourceService dataResourceService = SystemServices.getDataResourceService();

        // 为 channel.salePrice 的 price 字段创建脱敏策略（试点：非 ROOT 角色默认部分遮蔽）
        DataResourceEntity salePriceResource = dataResourceService.getByResourceKey("channel.salePrice");
        if (salePriceResource != null) {
            if (!dataFieldPolicyService.existsPolicy(salePriceResource.getId(), "price")) {
                dataFieldPolicyService.addAndGet(new DataFieldPolicyEntity()
                        .setResource(salePriceResource)
                        .setFieldName("price")
                        .setRoleCode("default")
                        .setActionName("QUERY")
                        .setMaskType("PARTIAL")
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)
                );
            }
        }

        // 为 channel.purchasePrice 的 price 字段创建脱敏策略
        DataResourceEntity purchasePriceResource = dataResourceService.getByResourceKey("channel.purchasePrice");
        if (purchasePriceResource != null) {
            if (!dataFieldPolicyService.existsPolicy(purchasePriceResource.getId(), "price")) {
                dataFieldPolicyService.addAndGet(new DataFieldPolicyEntity()
                        .setResource(purchasePriceResource)
                        .setFieldName("price")
                        .setRoleCode("default")
                        .setActionName("QUERY")
                        .setMaskType("PARTIAL")
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)
                );
            }
        }

        // 为 asset.material 的 purchasePrice / salePrice 字段创建脱敏策略
        DataResourceEntity materialResource = dataResourceService.getByResourceKey("asset.material");
        if (materialResource != null) {
            if (!dataFieldPolicyService.existsPolicy(materialResource.getId(), "purchasePrice")) {
                dataFieldPolicyService.addAndGet(new DataFieldPolicyEntity()
                        .setResource(materialResource)
                        .setFieldName("purchasePrice")
                        .setRoleCode("default")
                        .setActionName("QUERY")
                        .setMaskType("PARTIAL")
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)
                );
            }
            if (!dataFieldPolicyService.existsPolicy(materialResource.getId(), "salePrice")) {
                dataFieldPolicyService.addAndGet(new DataFieldPolicyEntity()
                        .setResource(materialResource)
                        .setFieldName("salePrice")
                        .setRoleCode("default")
                        .setActionName("QUERY")
                        .setMaskType("PARTIAL")
                        .setAllowPlaintext(false)
                        .setPriority(100)
                        .setEnabled(true)
                );
            }
        }
    }

    private void initRootAuthorization() {
        transactionTemplate.executeWithoutResult(status -> initRootAuthorizationInTransaction());
    }

    private void initRootAuthorizationInTransaction() {
        UserService userService = PersonnelServices.getUserService();
        RoleService roleService = PersonnelServices.getRoleService();
        PermissionService permissionService = SystemServices.getPermissionService();
        UserEntity rootUser = userService.getMaybeNull(1L);
        if (rootUser == null) {
            return;
        }
        // 先把既有角色集合"取平"成游离 Set：下面任何一次写操作都可能让持久化上下文
        // 里的实体失效（框架 CurdService 内部各自提交/清理会话），懒集合必须在读的时候
        // 就展开，不能留到写完再 .size()。
        Set<Long> existingRoleIds = new HashSet<>();
        if (rootUser.getRoleList() != null) {
            for (RoleEntity r : rootUser.getRoleList()) {
                existingRoleIds.add(r.getId());
            }
        }
        RoleEntity rootRole = roleService.filter(new RoleEntity().setCode("SYSTEM_ROOT"))
                .stream()
                .findFirst()
                .orElseGet(() -> roleService.addAndGet(new RoleEntity()
                        .setName("系统超级管理员")
                        .setCode("SYSTEM_ROOT")));
        rootRole.setPermissionList(new HashSet<>(permissionService.filter(new PermissionEntity())));
        roleService.updateToDatabase(rootRole);
        if (existingRoleIds.contains(rootRole.getId())) {
            return;
        }
        existingRoleIds.add(rootRole.getId());
        Set<RoleEntity> roles = new HashSet<>();
        for (Long roleId : existingRoleIds) {
            roles.add(roleService.get(roleId));
        }
        UserEntity freshUser = userService.getMaybeNull(1L);
        freshUser.setRoleList(roles);
        userService.updateToDatabase(freshUser);
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

        // 初始化用户
        UserEntity user = userService.getMaybeNull(1L);
        if (Objects.nonNull(user)) {
            return;
        }
        DepartmentEntity department = departmentService.addAndGet(new DepartmentEntity().setCode("a1").setName("生产部"));
        departmentService.addAndGet(new DepartmentEntity().setName("材料部").setCode("aaa1").setParentId(department.getId()));
        String salt = RandomValues.randomString(UserService.PASSWORD_SALT_LENGTH);
        userService.add(new UserEntity()
                .setNickname("凌小云")
                .setPhone("17666666666")
                .setEmail("admin@hamm.cn")
                .setPassword(PasswordHasher.encode("Aa123456", salt))
                .setSalt(salt)
        );
        userService.addAndGet(new UserEntity()
                        .setNickname("张三")
                        .setPhone("13888888888")
                        .setEmail("admin@hamm.com")
                        .setPassword(PasswordHasher.encode("Aa123456", salt))
                        .setSalt(salt))
                .setDepartmentList(Set.of(department));
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
        MaterialService materialService = AssetServices.getMaterialService();
        UnitService unitService = SystemServices.getUnitService();
        CustomerService customerService = ChannelServices.getCustomerService();
        SalePriceService salePriceService = ChannelServices.getSalePriceService();
        SupplierService supplierService = ChannelServices.getSupplierService();
        PurchasePriceService purchasePriceService = ChannelServices.getPurchasePriceService();

        StorageService storageService = FactoryServices.getStorageService();
        StructureService structureService = FactoryServices.getStructureService();

        // 幂等：检查单位是否已存在
        UnitEntity unit = unitService.filter(new UnitEntity().setName("台")).stream().findFirst().orElse(null);
        if (Objects.isNull(unit)) {
            unit = new UnitEntity();
            unit.setName("台");
            unit = unitService.addAndGet(unit);
        }

        // 幂等：检查物料是否已存在
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

        // 幂等：检查客户是否已存在
        CustomerEntity customer = customerService.filter(new CustomerEntity().setName("凌云科技")).stream().findFirst().orElse(null);
        if (Objects.isNull(customer)) {
            customer = new CustomerEntity();
            customer.setName("凌云科技").setPhone("17666666666");
            customer = customerService.addAndGet(customer);
        }

        // 幂等：检查供应商是否已存在
        SupplierEntity supplier = supplierService.filter(new SupplierEntity().setName("Apple 中国")).stream().findFirst().orElse(null);
        if (Objects.isNull(supplier)) {
            supplier = new SupplierEntity();
            supplier.setName("Apple 中国").setPhone("17666666666");
            supplier = supplierService.addAndGet(supplier);
        }

        // 幂等：价格实体
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

        // 幂等：仓库
        if (storageService.filter(new StorageEntity()).isEmpty()) {
            StorageEntity storage = new StorageEntity().setName("东部大仓");
            storageService.addAndGet(storage);

            for (int i = 0; i < TWO; i++) {
                storageService.addAndGet(new StorageEntity()
                        .setParentId(storage.getId())
                        .setName(String.format("东部%s仓", (i + 1)))
                );
            }

            storage = new StorageEntity().setName("西部大仓");
            storage = storageService.addAndGet(storage);

            for (int i = 0; i < TWO; i++) {
                storageService.addAndGet(new StorageEntity()
                        .setParentId(storage.getId())
                        .setName(String.format("西部%s仓", (i + 1)))
                );
            }
        }

        // 幂等：产线结构
        if (structureService.filter(new StructureEntity()).isEmpty()) {
            StructureEntity structure = new StructureEntity().setName("笔记本电脑产线");
            structureService.addAndGet(structure);

            for (int i = 0; i < TWO; i++) {
                structureService.addAndGet(new StructureEntity()
                        .setParentId(structure.getId())
                        .setName(String.format("工位%s", (i + 1)))
                );
            }
        }
    }
}

