package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import cn.hamm.spms.framework.curd.datapermission.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <h1>数据权限条件注入器（MyBatis-Plus，J4 起只有这一条路）</h1>
 *
 * <p>基于当前请求的 {@link PermissionContext} 和资源标识，把"谁能看哪些行"渲染成 SQL 片段并挂到
 * 调用方的 {@code QueryWrapper} 上（{@link #applyQuery}），供业务 Service 的
 * {@code addSearchPredicate} 叠加使用。</p>
 *
 * <p><b>2026-09-18 J2</b>：注入侧从"编 JPA Criteria 谓词"换成"渲染 SQL 片段挂到 wrapper 上" ——
 * 片段来自读侧同一个 {@link SqlPermissionRuleRenderer}，全仓只剩一份"谁能看哪些行"的实现。
 * <b>2026-09-18 J4</b>：留着服务影子比对的那族 {@code buildPredicates(Root, CriteriaBuilder, …)}
 * 随 {@code spring-boot-starter-data-jpa} 一起删除 —— 影子比对改成把<b>本次渲染出的同一批片段</b>
 * 挂到 wrapper 上数行数（见 {@link ShadowDiffService}），于是本类不再有第二个出口，
 * "一个资源一份判据"在编译期就成立（想编两套也没有第二套 API 可调）。</p>
 *
 * <p><b>模式语义</b>（{@code kubo.data-permission.mode}）：</p>
 * <ul>
 *   <li>{@code OFF} —— 直接短路，行为等价于改造前（回滚位）；</li>
 *   <li>{@code SHADOW} —— 计算条件与差异但<b>不改变查询结果</b>，只输出/打点，用于灰度观察；</li>
 *   <li>{@code ENFORCE} —— 真正注入；绑定失败（归属列不存在、资源未注册且 {@code deny-unregistered=true}）
 *       一律返回空结果集，<b>不再静默放行</b>。</li>
 * </ul>
 *
 * <p><b>历史缺陷（本次修复的动机）</b>：旧实现对"资源未注册""列不存在"都是
 * {@code catch → return 放行}，于是 24 个 Service 接了权限代码、真实表却没有归属列，
 * 结果是<b>结构性空转</b>且无任何告警。现在这类情况统一进
 * {@link DataPermissionAudit#binding} 指标，enforce 前必须清零。</p>
 *
 * <p>注意：JPA 侧只能取实体已映射的字段。{@code tenant_id/creator_id} 在数据库加列后，
 * 必须同时在实体（基类）上映射，否则这里会以 binding 指标暴露 —— 这正是期望的信号，
 * 不要试图用 catch 掩盖。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionSpecification {

    private final DataPermissionService dataPermissionService;
    // 兜底属性名（departmentId / warehouseId / factoryId）以前在这里和 DimensionBindingAnalyzer
    // 各写一遍、靠注释约定同值；§19.35 起统一收在 SubjectDimensionRules，本类不再有副本。

    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final PermissionRuleTreeBuilder ruleTreeBuilder;
    /**
     * 读侧 SQL 片段渲染器（注入侧与影子比对共用同一个：渲染出来的片段就是对账要数的条件）
     */
    private final SqlPermissionRuleRenderer ruleRenderer;

    /**
     * 影子双跑（只观测，不参与判定）。
     *
     * <p>用字段注入 + {@code required = false} 而不是塞进构造器：它缺 Bean 时的正确行为恰好就是"不跑"，
     * 而判定链路上任何<em>会影响结果</em>的东西都不许按这个形状接 —— 这条区别见 {@code DataPermissionDenial}。</p>
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ShadowDiffService shadowDiffService;

    /**
     * 唯一装配构造器；单测也走这里（想省依赖就用 Mockito，别再加"仅供测试"的第二构造器：
     * J4 删掉的那个 JPA 构造器就是这么长出来的）
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DataPermissionSpecification(
            DataPermissionService dataPermissionService,
            DataPermissionProperties properties,
            DataPermissionAudit audit,
            PermissionRuleTreeBuilder ruleTreeBuilder,
            SqlPermissionRuleRenderer ruleRenderer) {
        this.dataPermissionService = dataPermissionService;
        this.properties = properties;
        this.audit = audit;
        this.ruleTreeBuilder = ruleTreeBuilder;
        this.ruleRenderer = ruleRenderer;
    }

    /**
     * 把指定资源的数据权限条件挂到查询条件上
     *
     * @param wrapper     查询条件（调用方的 wrapper；片段会与其他条件 AND 在一起）
     * @param entityClass 实体类（只给影子比对用：它需要知道对哪张表做行数对账）
     * @param resourceKey 数据资源标识（如 {@code wms.inventory}）
     * @param action      数据操作类型
     * @apiNote J2 之前这里返回 {@code List<Predicate>}（JPA Criteria），由引擎塞进 Specification；
     * 现在两步都省了：**渲染出的片段直接挂在调用方的 wrapper 上**。
     * <p>取不到上下文、资源未登记、模式为 OFF / SHADOW 时都不加条件（与旧实现逐条对齐）；
     * ENFORCE 下"本该有条件却一条都没渲染出来"＝绑定失败，返回恒假（不是放行）。</p>
     */
    public void applyQuery(@NotNull QueryWrapper<?> wrapper,
                           @NotNull Class<?> entityClass,
                           @NotNull String resourceKey,
                           @NotNull DataPermissionAction action) {
        DataPermissionProperties.Mode mode = properties.effectiveMode(resourceKey);
        if (mode == DataPermissionProperties.Mode.OFF) {
            return;
        }
        long started = System.nanoTime();
        try {
            inject(wrapper, entityClass, resourceKey, action, mode);
        }
        catch (RuntimeException ex) {
            // 权限计算自身异常属于安全事件：ENFORCE 拒绝，SHADOW 记录后放行
            audit.binding(resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage());
            if (enforced(mode)) {
                deny(wrapper, resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage());
            }
        }
        finally {
            audit.recordDecisionNanos(resourceKey, System.nanoTime() - started);
        }
    }

    /**
     * 渲染并注入（{@link #applyQuery} 的主体，异常策略在外层）
     *
     * @param wrapper     查询条件
     * @param entityClass 实体类
     * @param resourceKey 资源标识
     * @param action      动作
     * @param mode        生效模式
     */
    private void inject(@NotNull QueryWrapper<?> wrapper,
                        @NotNull Class<?> entityClass,
                        @NotNull String resourceKey,
                        @NotNull DataPermissionAction action,
                        @NotNull DataPermissionProperties.Mode mode) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            // 无上下文 = 定时任务/内部调用。不放大权限（不注入），但必须显式记账：
            // 这类调用点应改为构造系统身份上下文，而不是长期依赖此旁路。
            audit.binding(resourceKey, "no-permission-context", "跳过注入，调用方应显式构造系统身份上下文");
            return;
        }
        if (context.bypassDataPermission()) {
            return;
        }

        DataResourceEntity resource;
        try {
            resource = dataPermissionService.requireResource(resourceKey);
        }
        catch (RuntimeException ex) {
            if (enforced(mode) && properties.isDenyUnregistered()) {
                deny(wrapper, resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage());
                return;
            }
            audit.binding(resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage());
            return;
        }

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey, action, context);

        if (policies.isEmpty()) {
            if (dataPermissionService.shouldDenyByDefault(resourceKey, context)) {
                if (enforced(mode)) {
                    deny(wrapper, resourceKey, DataPermissionAudit.REASON_NO_POLICY, "无 QUERY 策略且资源要求默认拒绝");
                    return;
                }
                audit.shadow(resourceKey, DataPermissionAudit.REASON_NO_POLICY, "enforce 后将返回空结果");
            }
            return;
        }

        RenderResult render = renderFromPolicies(resource, policies, context);
        List<RowPermissionPredicate> fragments = render.fragments();

        // 只有「本该产生 SQL 条件却一条都没渲染出来」才算绑定失败；
        // SYSTEM / ROLE 策略语义是"允许全部"，生成不出条件是正确结果，不能误判为异常。
        // expectedConditions() 是 int 计数（"本该产生几条条件"），不是一眼看上去像布尔的开关；
        // 这里必须显式比较，写成 `&& build.expectedConditions()` 是 javac 级别的错误。
        if (fragments.isEmpty() && render.expectedConditions() > 0) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_BINDING_INVALID,
                    render.expectedConditions() + " 条策略在 SQL 侧渲染不出条件（列名/维度登记问题）");
            if (enforced(mode)) {
                deny(wrapper, resourceKey, DataPermissionAudit.REASON_BINDING_INVALID, null);
            }
            return;
        }
        if (fragments.isEmpty()) {
            return;
        }

        if (!enforced(mode)) {
            audit.shadow(resourceKey, "would-filter", describe(fragments));
            submitShadowDiff(entityClass, resourceKey, action, fragments, context);
            return;
        }
        for (RowPermissionPredicate fragment : fragments) {
            RowPermissionPredicate.ApplyFragment applyFragment = fragment.forQueryWrapper();
            wrapper.apply(applyFragment.sql(), applyFragment.values());
        }
    }

    /**
     * 渲染结果
     *
     * @param fragments          渲染出的片段（挂到 wrapper 上的就是它们；影子对账数的也是这一份）
     * @param expectedConditions 本该渲染出条件、却一条都没有的策略条数（识别"静默空转"）
     */
    private record RenderResult(List<RowPermissionPredicate> fragments, int expectedConditions) {
    }

    /**
     * 从策略列表渲染 SQL 片段
     *
     * <p>{@code tableAlias} 传 {@code null}：MyBatis-Plus 生成的语句里表名没有别名，
     * 片段必须用裸列名（渲染器支持"为空表示不带别名"）。</p>
     */
    private RenderResult renderFromPolicies(DataResourceEntity resource,
                                            List<DataPolicyEntity> policies,
                                            PermissionContext context) {
        List<RowPermissionPredicate> fragments = new ArrayList<>();
        int expected = 0;

        for (PermissionRuleTreeBuilder.PolicyRule policyRule : ruleTreeBuilder.build(resource, policies, context)) {
            PermissionRule tree = policyRule.rule();
            if (tree != null) {
                RowPermissionPredicate fragment = ruleRenderer.render(tree, null, new PermissionRuleContext(context));
                if (fragment != null) {
                    fragments.add(fragment);
                }
                else if (policyRule.shouldBind()) {
                    expected++;
                }
            }
            else if (policyRule.shouldBind()) {
                expected++;
            }
        }

        return new RenderResult(List.copyOf(fragments), expected);
    }

    /**
     * 把「enforce 下本应注入的条件」交给影子比对（采样、异步、异常只记账都在 {@link ShadowDiffService} 里）
     *
     * <p>这一步的意义不在"多看一眼日志"：{@code enforce-readiness} 的放开前置要求差异表里有
     * {@code EXPECTED_FILTER} 正向样本（证明"加条件确实会少行"）。产生那些行的只有两条注入路径 ——
     * 这一条（wrapper，见 {@link #applyQuery}）与读侧拦截器那条（{@code KuboDataPermissionHandler}，
     * mapper-read 打开时 page/detail/stat/export 不走 wrapper）。任一条断着，对应流量就没有证据，
     * 而面板上看起来只像"还没跑够几天"。</p>
     *
     * <p>条件不在这儿重写一遍：交出去的就是刚刚渲染、也正被挂到 wrapper 上的那份片段
     * （J4 起影子比对与生产注入共用 {@link SqlPermissionRuleRenderer}；J2 之前 JPA 侧会把同一棵树再编一遍，
     * 那是两套真值并存的时代）。比对侧只做一件事：把同一批片段挂到另一个 wrapper 上数行数。</p>
     *
     * @param entityClass 被过滤的实体类（对账数的是它的表；解析不到仓储会记 {@code no-repository} 并跳过）
     * @param resourceKey 资源标识
     * @param action      动作
     * @param fragments   本次渲染出的片段
     * @param context     主体上下文值对象（身份已在业务线程取好）
     */
    private void submitShadowDiff(Class<?> entityClass,
                                  String resourceKey,
                                  DataPermissionAction action,
                                  List<RowPermissionPredicate> fragments,
                                  PermissionContext context) {
        if (shadowDiffService == null) {
            return;
        }
        shadowDiffService.compare(resourceKey, action, entityClass, fragments, context);
    }

    /**
     * 便于单测注入（生产由容器装配；缺 Bean 就是不跑比对）
     *
     * @param shadowDiffService 影子双跑服务
     */
    void setShadowDiffService(ShadowDiffService shadowDiffService) {
        this.shadowDiffService = shadowDiffService;
    }

    /**
     * 默认拒绝：给 wrapper 挂一个恒假条件，而不是放开原查询
     *
     * @apiNote 恒假片段复用 {@link RowPermissionPredicate#DENY_SQL}（全仓只有这一处字面量）：
     * 哪一处写成 {@code 1 = 1}，表现就是那个分支静默放开全表。
     */
    private void deny(@NotNull QueryWrapper<?> wrapper, String resourceKey, String reason, String detail) {
        audit.deny(resourceKey, reason, detail);
        wrapper.apply(RowPermissionPredicate.DENY_SQL);
    }

    private String describe(List<RowPermissionPredicate> fragments) {
        return fragments.stream().map(RowPermissionPredicate::sql).collect(Collectors.joining(" AND "));
    }
}
