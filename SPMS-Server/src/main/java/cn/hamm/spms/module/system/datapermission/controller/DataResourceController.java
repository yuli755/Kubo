package cn.hamm.spms.module.system.datapermission.controller;

import cn.hamm.airpower.api.annotation.Api;
import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.spms.base.BaseController;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import cn.hamm.spms.module.system.datapermission.service.DataResourceService;
import cn.hamm.airpower.core.Json;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * <h1>数据资源管理控制器</h1>
 *
 * @author Hamm.cn
 */
@Api("dataResource")
@Description("数据资源")
public class DataResourceController extends BaseController<DataResourceEntity, DataResourceService, DataResourceRepository> {

    /**
     * 列出"配了 QUERY 遮蔽、却没配 EXPORT 明文授权"的字段
     *
     * <p>缺口的代价不是报错，而是<b>静默的整份拒绝</b>：用户点导出才撞 403，
     * 管理员在策略表里则完全看不出问题（两类策略行都在，只是配得不全）。
     * 所以这个查询要挂在资源管理页上，让"配好了"这件事可以被机器核对。</p>
     *
     * <p>权限说明：返回的是跨角色的策略组合信息，因此<b>只放在 dataResource 这个管理端点下</b>
     * （沿用它的权限校验），不挂到 {@code dataFieldPolicy} 那种业务用户也会调的控制器上。</p>
     *
     * @return 缺口条目列表（资源 ID、资源键、字段名、角色、原因）
     */
    /**
     * 列出"动作不会被任何代码消费"的列级策略行
     *
     * <p>这类行的特征是没有失败信号：保存成功、列表可见、界面无变化。enforce 打开前后都一样，
     * 只有把它们数出来摆在管理端，人才会去处理。与导出缺口自查同权限面（{@code dataResource}），
     * 因为返回的同样是跨角色的策略元数据。</p>
     *
     * @return 条目列表（策略 ID、资源、字段、角色、动作）
     */
    @GetMapping("inert-policies")
    @Description("无人消费的列级策略盘点")
    public Json getInertPolicies() {
        return Json.data(service.inertPolicies());
    }

    /**
     * 列出"启用着但永远不会命中"的行级策略
     *
     * <p>行级规则不命中 = 该主体在这个资源上没有防护，而界面显示"已启用" —— 这种反差必须由机器核对，
     * 靠人读 condition_json 与动作名是读不出来的。与 {@code export-gaps} / {@code inert-policies}
     * 同一权限面（返回的都是跨角色的策略元数据）。</p>
     *
     * @return 风险条目列表（策略 ID、资源、编码、类别、原因）
     */
    @GetMapping("row-policy-risks")
    @Description("行级策略永不命中盘点")
    public Json getRowPolicyRisks() {
        return Json.data(service.rowPolicyRisks());
    }

    /**
     * 列出"这一档维度在该资源上没有列可绑"的项
     *
     * <p>这一类问题的运行期表现和"策略写错"一样安静：本该产生的条件绑不上列，enforce 下退化成恒假
     * （该主体一行也查不到）、shadow 下等价于没配，界面上两处都没有提示。区别在于修法：这里要改的是
     * <b>资源的列登记</b>或补关联路径支持，而不是去改策略行。所以必须分开数出来。</p>
     *
     * @return 风险条目（资源、维度、类别、原因）
     */
    @GetMapping("dimension-binding-risks")
    @Description("维度可绑定性盘点")
    public Json getDimensionBindingRisks() {
        return Json.data(service.dimensionBindingRisks());
    }

    @GetMapping("export-gaps")
    @Description("导出明文授权缺口自查")
    public Json getExportGaps() {
        return Json.data(service.exportGaps());
    }

    /**
     * 灰度状态总览：当前配置 + 每个资源此刻实际生效的档位
     *
     * <p>模式与 {@code enforce-resources} 名单在配置文件里、策略在数据库里，页面上原先哪一头都看不出
     * "这个资源现在是 shadow 还是 enforce"，而"逐资源放开"这一步必须看清自己站在哪一格。
     * 档位取自 {@code DataPermissionProperties#effectiveMode} —— <b>面板报的与实际走的是同一份判定</b>，
     * 前端不参与计算（名单规则被抄第二遍，面板就会开始骗人）。</p>
     *
     * <p>只读、不参与任何权限判定；返回的是跨资源的配置与策略计数，因此与其余盘点接口
     * 同一权限面（挂在 {@code dataResource} 下）。</p>
     *
     * @return 总览（全局配置 + 各资源状态 + 未纳管服务清单）
     */
    @GetMapping("runtime-status")
    @Description("灰度状态总览")
    public Json getRuntimeStatus() {
        return Json.data(service.runtimeStatus());
    }

    /**
     * 逐资源放开的前置检查（"现在能不能把这个资源写进 enforce-resources"）
     *
     * <p>名单是把双刃剑：少配一项，防护形同虚设；多配一项，保存报错或列表查空，而代价都要到
     * <em>放开之后</em>才暴露。这里把六件事一次判完并给出一条条可直接照做的结论，其中最容易被跳过、
     * 也最贵的一条是<em>影子证据</em>：只看"差异为 0"是不够的 —— 一行差异都没有，也可能是该主体
     * 根本没被过滤；因此必须存在 {@code EXPECTED_FILTER} 样本，且 {@code ANOMALY/ERROR} 为 0，
     * 读不到差异表时按"无证据"处理而不是"无差异"。</p>
     *
     * <p>只读、<b>不新增任何拦阻</b>（放开与否仍然是配置动作，这里只报数）；与其余盘点接口同一权限面。</p>
     *
     * @param resourceKey 资源标识；省略则返回全部已登记资源
     * @return 检查结论（含 ready 与阻塞原因，前端不参与计算）
     */
    @GetMapping("enforce-readiness")
    @Description("逐资源放开前置检查")
    public Json getEnforceReadiness(String resourceKey) {
        return Json.data(resourceKey == null || resourceKey.isBlank()
                ? service.readinessAll()
                : service.readiness(resourceKey));
    }

    /**
     * 最近的拒绝事件（事后追认：谁在何时、因何、在哪个档位下被数据权限拒绝）
     *
     * <p>与其余盘点接口同一权限面（{@code dataResource}）：这份数据是跨角色的「谁被拒过」，
     * 不能对业务角色开放。取不到表 / 没有记录时返回空数组，前端按「取不到」显示，
     * 不把空结果读成「从没拒绝过」。</p>
     *
     * @param resourceKey 资源标识；省略则返回全量最近记录
     * @return 最近拒绝事件（最多 50 条，最新在前）
     */
    @GetMapping("deny-logs")
    @Description("数据权限拒绝事件")
    public Json getDenyLogs(String resourceKey) {
        return Json.data(service.denyLogs(resourceKey));
    }
}