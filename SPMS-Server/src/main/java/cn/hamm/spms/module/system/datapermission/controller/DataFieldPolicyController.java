package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.Json;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import cn.hamm.spms.module.system.datapermission.service.DataFieldPolicyService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private ColumnMaskingService columnMaskingService;

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

    /**
     * 按资源/字段解析当前登录用户生效的脱敏策略
     *
     * <p>角色取自服务端权限上下文（{@link PermissionContextHolder}），
     * 不信任前端提交的 {@code roleCode}，避免用户通过手工传参绕过脱敏。</p>
     *
     * @param resourceKey 数据资源标识（如 channel.salePrice）
     * @param fieldName   字段名（如 price）
     * @return 生效策略；无需脱敏（当前用户无匹配策略）时返回 {@code null}
     */
    @GetMapping("my-mask")
    @Description("按资源/字段获取当前用户生效的脱敏策略")
    public Json getMyMask(
            @RequestParam String resourceKey,
            @RequestParam String fieldName) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        DataFieldPolicyEntity policy = columnMaskingService.getEffectiveMaskPolicy(resourceKey, fieldName, context);
        return Json.data(policy);
    }
}