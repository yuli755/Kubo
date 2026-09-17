package cn.hamm.spms.module.system;

import cn.hamm.spms.module.system.coderule.CodeRuleService;
import cn.hamm.spms.module.system.config.ConfigService;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyProvider;
import cn.hamm.spms.module.system.datapermission.service.DataPolicyService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import cn.hamm.spms.module.system.file.FileService;
import cn.hamm.spms.module.system.menu.MenuService;
import cn.hamm.spms.module.system.permission.PermissionService;
import cn.hamm.spms.module.system.unit.UnitService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * <h1>服务整合助手类</h1>
 *
 * @author Hamm.cn
 */
@Profile("!test")
@Component
public class SystemServices {
    @Getter
    static CodeRuleService codeRuleService;

    @Getter
    static ConfigService configService;

    @Getter
    static FileService fileService;

    @Getter
    static MenuService menuService;

    @Getter
    static PermissionService permissionService;

    @Getter
    static UnitService unitService;

    @Getter
    static DataResourceService dataResourceService;

    @Getter
    static DataPolicyService dataPolicyService;

    @Getter
    static DataFieldPolicyProvider dataFieldPolicyService;

    @Autowired
    private void initService(
            CodeRuleService codeRuleService,
            ConfigService configService,
            FileService fileService,
            MenuService menuService,
            PermissionService permissionService,
            UnitService unitService,
            DataResourceService dataResourceService,
            DataPolicyService dataPolicyService,
            DataFieldPolicyProvider dataFieldPolicyService,
            DataFieldPolicyProvider dataFieldPolicyService
    ) {
        SystemServices.codeRuleService = codeRuleService;
        SystemServices.configService = configService;
        SystemServices.fileService = fileService;
        SystemServices.menuService = menuService;
        SystemServices.permissionService = permissionService;
        SystemServices.unitService = unitService;
        SystemServices.dataResourceService = dataResourceService;
        SystemServices.dataPolicyService = dataPolicyService;
        SystemServices.dataFieldPolicyService = dataFieldPolicyService;
    }
}
