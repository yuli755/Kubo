package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <h1>数据字段权限策略管理控制器</h1>
 *
 * @author Hamm.cn
 */
@Api("dataFieldPolicy")
@Description("数据字段权限策略")
@RestController
public class DataFieldPolicyController extends BaseController<DataFieldPolicyEntity, DataFieldPolicyService, DataFieldPolicyRepository> {

    /**
     * 根据资源键、字段名、角色编码获取脱敏策略
     *
     * @param resourceKey 数据资源标识（如 wms.input）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表
     */
    @GetMapping("by-resource-field-role")
    @Description("按资源/字段/角色获取脱敏策略")
    public Json getByResourceFieldRole(
            @RequestParam String resourceKey,
            @RequestParam String fieldName,
            @RequestParam(required = false) String roleCode) {
        List<DataFieldPolicyEntity> policies = service.getPoliciesByResourceFieldRole(
                resourceKey, fieldName, roleCode);
        return Json.data(policies);
    }
}