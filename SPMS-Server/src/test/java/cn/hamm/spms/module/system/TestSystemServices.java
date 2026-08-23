package cn.hamm.spms.module.system;

import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.config.ConfigService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionGuard;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import cn.hamm.spms.module.system.datapermission.service.TestDataFieldPolicyService;
import cn.hamm.spms.module.system.file.FileService;
import cn.hamm.spms.module.system.menu.MenuService;
import cn.hamm.spms.module.system.permission.PermissionService;
import cn.hamm.spms.module.system.unit.UnitService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import jakarta.annotation.PostConstruct;

/**
 * <h1>测试环境服务整合助手类</h1>
 * <p>提供测试所需的空实现服务，避免 NullPointerException</p>
 *
 * @author Hamm.cn
 */
@Profile("test")
@Configuration
public class TestSystemServices {
    
    private final CodeRuleService codeRuleService;
    private final ConfigService configService;
    private final FileService fileService;
    private final MenuService menuService;
    private final PermissionService permissionService;
    private final UnitService unitService;
    private final DataResourceService dataResourceService;
    private final DataPolicyService dataPolicyService;
    private final TestDataFieldPolicyService dataFieldPolicyService;
    private final DataPermissionGuard dataPermissionGuard;

    public TestSystemServices(
            CodeRuleService codeRuleService,
            ConfigService configService,
            FileService fileService,
            MenuService menuService,
            PermissionService permissionService,
            UnitService unitService,
            DataResourceService dataResourceService,
            DataPolicyService dataPolicyService,
            TestDataFieldPolicyService dataFieldPolicyService,
            DataPermissionGuard dataPermissionGuard
    ) {
        this.codeRuleService = codeRuleService;
        this.configService = configService;
        this.fileService = fileService;
        this.menuService = menuService;
        this.permissionService = permissionService;
        this.unitService = unitService;
        this.dataResourceService = dataResourceService;
        this.dataPolicyService = dataPolicyService;
        this.dataFieldPolicyService = dataFieldPolicyService;
        this.dataPermissionGuard = dataPermissionGuard;
    }

    @PostConstruct
    public void init() {
        SystemServices.codeRuleService = codeRuleService;
        SystemServices.configService = configService;
        SystemServices.fileService = fileService;
        SystemServices.menuService = menuService;
        SystemServices.permissionService = permissionService;
        SystemServices.unitService = unitService;
        SystemServices.dataResourceService = dataResourceService;
        SystemServices.dataPolicyService = dataPolicyService;
        SystemServices.dataFieldPolicyService = dataFieldPolicyService;
        SystemServices.dataPermissionGuard = dataPermissionGuard;
    }
}