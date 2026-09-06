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
     * 按资源解析当前登录用户在<b>全部字段</b>上生效的脱敏策略
     *
     * <p>存在的理由不是省流量，而是编辑表单必须一次知道"这个资源里哪些字段对我受限"：
     * 逐字段查会让一个 8 字段的表单发 8 个请求，而表单恰恰是最需要这份信息的地方。</p>
     *
     * <p>返回<b>字段名 → 生效策略</b>，没有策略的字段不出现在返回里。前端
     * （{@code useMaskPolicy}）据此区分"没策略"（明文）与"接口失败"（一律遮蔽）——
     * 不能拿"还不知道"当"可以看"。</p>
     *
     * <p>前端的用法是<b>提示 + 数值字段提交前拦截</b>，不是"把受限字段从提交体里删掉"：
     * 整实体更新语义下删字段等于把该列写成 {@code null}。字符串敏感字段由服务端
     * {@code SensitiveWriteProtection} 用库中原值兜底；数值敏感字段（{@code Double price}）
     * 没法这么兜（实体的 {@code @NotNull(groups = WhenUpdate)} 会把置空报成"不能为空"），
     * 因此在反序列化阶段就明确拒绝。</p>
     *
     * @param resourceKey 数据资源标识（如 personnel.user）
     * @return 字段名 → 生效策略；资源未注册或无策略时为空对象
     */
    @GetMapping("my-mask-map")
    @Description("按资源获取当前用户全部字段的脱敏策略")
    public Json getMyMaskMap(@RequestParam String resourceKey) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        return Json.data(columnMaskingService.getEffectiveMaskPolicyMap(resourceKey, context));
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