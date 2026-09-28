package cn.hamm.spms.module.system.datapermission.mybatis;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRule;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleContext;
import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleTreeBuilder;
import cn.hamm.spms.module.system.datapermission.rule.SqlPermissionRuleRenderer;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionService;
import cn.hamm.spms.module.system.datapermission.service.DataResourceBindingRegistry;
import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * <h1>MyBatis-Plus 读侧数据权限处理器</h1>
 *
 * <p>拦截器拿到的事实是 {@code mappedStatementId}。本类只接受
 * {@link DataResourceBindingRegistry} 的精确绑定，不从类名、SQL 表名或请求参数猜资源；
 * 猜不到时 enforce 返回恒假条件。</p>
 *
 * <p>策略树由 {@link PermissionRuleTreeBuilder} 合成一次，再交给
 * {@link SqlPermissionRuleRenderer} 渲染。MyBatis-Plus 的
 * {@link MultiDataPermissionHandler} 只能返回 JSqlParser AST，不能把新的 MyBatis 参数映射追加到
 * 已生成的 {@code BoundSql}，所以这里把渲染器已经校验过的服务端值安全地落成 AST 字面量。
 * 这不是把请求字符串拼进 SQL：列名仍由白名单渲染器产生，值只允许 Number/String/Boolean，
 * 字符串按 SQL 标准转义后才进入解析器；解析失败仍按模式语义处理，enforce 恒假。</p>
 *
 * @author Hamm.cn
 */
public final class KuboDataPermissionHandler implements MultiDataPermissionHandler {

    private final Supplier<DataResourceBindingRegistry> bindingsSupplier;
    private final DataPermissionService dataPermissionService;
    private final DataPermissionProperties properties;
    private final DataPermissionAudit audit;
    private final PermissionRuleTreeBuilder ruleTreeBuilder;
    private final SqlPermissionRuleRenderer renderer;

    /**
     * 生产构造器接收延迟绑定：SqlSessionFactory 创建 MybatisPlusInterceptor 时，
     * 绑定注册表还要从该工厂构建，不能在 Bean 创建期反向要求工厂。
     */
    public KuboDataPermissionHandler(Supplier<DataResourceBindingRegistry> bindingsSupplier,
                                     DataPermissionService dataPermissionService,
                                     DataPermissionProperties properties,
                                     DataPermissionAudit audit,
                                     PermissionRuleTreeBuilder ruleTreeBuilder,
                                     SqlPermissionRuleRenderer renderer) {
        this.bindingsSupplier = Objects.requireNonNull(bindingsSupplier, "bindingsSupplier");
        this.dataPermissionService = dataPermissionService;
        this.properties = properties;
        this.audit = audit;
        this.ruleTreeBuilder = ruleTreeBuilder;
        this.renderer = renderer;
    }

    /**
     * 纯单测构造器；生产装配使用上面的延迟 Supplier，避免 SqlSessionFactory 循环依赖。
     */
    public KuboDataPermissionHandler(DataResourceBindingRegistry bindings,
                                     DataPermissionService dataPermissionService,
                                     DataPermissionProperties properties,
                                     DataPermissionAudit audit,
                                     PermissionRuleTreeBuilder ruleTreeBuilder,
                                     SqlPermissionRuleRenderer renderer) {
        this(() -> bindings, dataPermissionService, properties, audit, ruleTreeBuilder, renderer);
    }

    /**
     * MyBatis-Plus 回调：返回要与原 WHERE 合并的权限表达式。
     *
     * @param table             当前 SELECT 主表
     * @param where             原查询 WHERE（只用于框架合并，本处理器不重写它）
     * @param mappedStatementId MyBatis statement ID
     * @return 权限表达式；shadow/off/显式放行时为 {@code null}
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataPermissionProperties.Mode mode = properties.effectiveMode(resourceKeyOf(mappedStatementId));
        if (mode == DataPermissionProperties.Mode.OFF) {
            return null;
        }
        long started = System.nanoTime();
        String resourceKey = resourceKeyOf(mappedStatementId);
        try {
            return apply(table, mappedStatementId, resourceKey, mode);
        }
        catch (RuntimeException ex) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage());
            return enforced(mode)
                    ? deny(resourceKey, DataPermissionAudit.REASON_PARSE_FAILED, ex.getMessage())
                    : null;
        }
        finally {
            audit.recordDecisionNanos(resourceKey, System.nanoTime() - started);
        }
    }

    private Expression apply(Table table,
                              String mappedStatementId,
                              String resourceKey,
                              DataPermissionProperties.Mode mode) {
        DataResourceBindingRegistry.ResourceBinding binding = bindings().bindingFor(mappedStatementId);
        if (binding == null || !StringUtils.hasText(resourceKey)) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_UNREGISTERED,
                    "mappedStatementId 未绑定到唯一数据资源：" + mappedStatementId);
            return enforced(mode)
                    ? deny(resourceKey, DataPermissionAudit.REASON_UNREGISTERED, mappedStatementId)
                    : null;
        }

        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null) {
            audit.binding(resourceKey, "no-permission-context", "读侧 Mapper 请求没有主体上下文");
            return enforced(mode)
                    ? deny(resourceKey, "no-permission-context", "mappedStatementId=" + mappedStatementId)
                    : null;
        }
        if (context.bypassDataPermission()) {
            return null;
        }

        DataResourceEntity resource;
        try {
            resource = dataPermissionService.requireResource(resourceKey);
        }
        catch (RuntimeException ex) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage());
            return enforced(mode)
                    ? deny(resourceKey, DataPermissionAudit.REASON_UNREGISTERED, ex.getMessage())
                    : null;
        }
        verifyTableBinding(table, binding, resource);

        List<DataPolicyEntity> policies = dataPermissionService.getPolicies(resourceKey,
                DataPermissionAction.QUERY, context);
        if (policies.isEmpty()) {
            if (!enforced(mode)) {
                audit.shadow(resourceKey, DataPermissionAudit.REASON_NO_POLICY, "enforce 后将返回空结果");
                return null;
            }
            return deny(resourceKey, DataPermissionAudit.REASON_NO_POLICY, "没有当前主体可达的 QUERY 策略");
        }

        List<PermissionRuleTreeBuilder.PolicyRule> policyRules = ruleTreeBuilder.build(resource, policies, context);
        List<String> sqlFragments = new ArrayList<>(policyRules.size());
        int expectedConditions = 0;
        for (PermissionRuleTreeBuilder.PolicyRule policyRule : policyRules) {
            PermissionRule rule = policyRule.rule();
            if (rule == null || !rule.contributes()) {
                if (policyRule.shouldBind()) {
                    expectedConditions++;
                }
                continue;
            }
            RowPermissionPredicate rendered = renderer.render(rule, tableQualifier(table, binding, resource),
                    new PermissionRuleContext(context));
            if (rendered == null) {
                if (policyRule.shouldBind()) {
                    expectedConditions++;
                }
                continue;
            }
            // 触发 RowPermissionPredicate 的最后一道类型/参数/禁止记号校验；这里不使用其 #{...} 文本，
            // 因为 MultiDataPermissionHandler 没有向 BoundSql 追加 ParameterMapping 的能力。
            rendered.forMyBatis();
            sqlFragments.add(toSafeSql(rendered));
        }

        if (sqlFragments.isEmpty() && expectedConditions > 0) {
            audit.binding(resourceKey, DataPermissionAudit.REASON_BINDING_INVALID,
                    expectedConditions + " 条策略无法渲染成 MyBatis 条件");
            return enforced(mode)
                    ? deny(resourceKey, DataPermissionAudit.REASON_BINDING_INVALID, null)
                    : null;
        }
        if (sqlFragments.isEmpty()) {
            // SYSTEM / ROLE 的无条件策略是合法的全量放行，不把它误记成归属列绑定失败。
            return null;
        }

        Expression expression = parseExpression(String.join(" AND ", sqlFragments));
        if (!enforced(mode)) {
            audit.shadow(resourceKey, "would-filter", expression.toString());
            return null;
        }
        return expression;
    }

    private DataResourceBindingRegistry bindings() {
        DataResourceBindingRegistry registry = bindingsSupplier.get();
        if (registry == null) {
            throw new IllegalStateException("data resource binding registry is not ready");
        }
        return registry;
    }

    private String resourceKeyOf(String mappedStatementId) {
        DataResourceBindingRegistry.ResourceBinding binding = bindings().bindingFor(mappedStatementId);
        return binding == null ? mappedStatementId : binding.resourceKey();
    }

    private void verifyTableBinding(Table table,
                                    DataResourceBindingRegistry.ResourceBinding binding,
                                    DataResourceEntity resource) {
        if (table == null || !StringUtils.hasText(resource.getTableName())) {
            throw new IllegalStateException("权限资源缺少可验证的主表绑定：" + binding.resourceKey());
        }
        String actualTable = normalizeIdentifier(table.getName());
        if (!resource.getTableName().equalsIgnoreCase(actualTable)) {
            throw new IllegalStateException("mappedStatement " + binding.resourceKey() + " 的 SQL 主表 "
                    + actualTable + " 与资源登记表 " + resource.getTableName() + " 不一致");
        }
    }

    /**
     * JSqlParser 对带引号的物理表可能保留标识符定界符；资源登记保存的是裸表名。
     * 这里只规范化解析器返回的表标识符，不放宽资源绑定，也不处理任意 SQL 文本。
     */
    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        String value = identifier.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '`' && last == '`')
                    || (first == '"' && last == '"')
                    || (first == '[' && last == ']')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String tableQualifier(Table table,
                                  DataResourceBindingRegistry.ResourceBinding binding,
                                  DataResourceEntity resource) {
        if (table.getAlias() != null && StringUtils.hasText(table.getAlias().getName())) {
            return table.getAlias().getName();
        }
        if (StringUtils.hasText(binding.tableAlias())) {
            return binding.tableAlias();
        }
        if (StringUtils.hasText(resource.getTableAlias())) {
            return resource.getTableAlias();
        }
        return table.getName();
    }

    private Expression parseExpression(String sql) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        }
        catch (Exception ex) {
            throw new IllegalStateException("权限 SQL 条件无法解析：" + sql, ex);
        }
    }

    /**
     * 将已由 renderer 校验的命名参数转换为解析器字面量。
     * key 按长度倒序替换，避免 {@code dpRule0} 先误伤 {@code dpRule0_0}。
     */
    private String toSafeSql(RowPermissionPredicate rendered) {
        String sql = rendered.sql();
        return rendered.parameters().entrySet().stream()
                .sorted(Map.Entry.<String, Object>comparingByKey(Comparator.comparingInt(String::length).reversed()))
                .reduce(sql, (current, entry) -> current.replace(":" + entry.getKey(), sqlLiteral(entry.getValue())),
                        (left, right) -> right);
    }

    private String sqlLiteral(Object value) {
        if (value instanceof Number number) {
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("permission parameter must be finite: " + value);
            }
            try {
                return new BigDecimal(number.toString()).toPlainString();
            }
            catch (NumberFormatException ex) {
                throw new IllegalArgumentException("permission parameter is not a SQL number: " + value, ex);
            }
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        if (value instanceof String text) {
            // S-1：MySQL 默认模式把反斜杠当串内转义符，仅双写单引号不够——
            // 以 `\` 结尾的值会吃掉闭合引号、让后续 SQL 拼进字符串继续解析。
            // 反斜杠必须先加倍；\0 会截断字符串参数，直接拒绝。
            if (text.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("permission parameter must not contain NUL");
            }
            return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'";
        }
        throw new IllegalArgumentException("permission parameter has an unbindable type: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private Expression deny(String resourceKey, String reason, String detail) {
        audit.deny(resourceKey, reason, detail);
        return parseExpression(RowPermissionPredicate.DENY_SQL);
    }

    private boolean enforced(DataPermissionProperties.Mode mode) {
        return mode == DataPermissionProperties.Mode.ENFORCE;
    }
}
