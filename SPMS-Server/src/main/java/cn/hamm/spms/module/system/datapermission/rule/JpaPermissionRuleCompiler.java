package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <h1>条件树 → JPA Criteria 编译器</h1>
 *
 * <p>读链路（当前是 JPA）用这个实现；P2 换轨到 MyBatis 后由
 * {@link SqlPermissionRuleRenderer} 生成同样语义的 SQL 片段，两者共用
 * {@link PermissionRuleParser} / {@link PermissionRuleValidator}，
 * 因此不会出现"两套实现判定不一致"。</p>
 *
 * <h2>三处容易踩空的地方</h2>
 * <ol>
 *   <li><b>列名 vs 属性名</b>：{@code data_resource} 里存的是 DB 列名（{@code tenant_id}），
 *       而 {@code root.get("tenant_id")} 在 JPA 里必然抛
 *       {@code IllegalArgumentException} —— 这正是历史上"接了权限代码却零效果"的根因之一。
 *       这里按 snake→camel 逐一尝试，仍取不到就抛 {@link PermissionRuleException}（不再静默跳过）；</li>
 *   <li><b>空集合</b>：{@code IN ()} 必须编译成<b>恒假</b>，不能因为"没值可拼"而返回无条件；</li>
 *   <li><b>类型</b>：策略里的 {@code 7} 会被解析成 Long，而实体字段可能是 Integer，
 *       因此比较前按路径的 Java 类型做一次收敛。</li>
 * </ol>
 *
 * @author Hamm.cn
 */
public final class JpaPermissionRuleCompiler {

    /**
     * 编译条件树为 JPA 谓词
     *
     * @param rule    已通过校验的条件树
     * @param root    查询根
     * @param builder CriteriaBuilder
     * @param context 上下文变量来源
     * @return 谓词；{@code null} 表示该树不产生条件（AllowAll）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate compile(PermissionRule rule, Root<?> root, CriteriaBuilder builder, PermissionRuleContext context) {
        if (rule == null || !rule.contributes()) {
            return null;
        }
        if (rule instanceof PermissionRule.Deny) {
            return builder.disjunction();
        }
        if (rule instanceof PermissionRule.Group group) {
            List<Predicate> children = new ArrayList<>();
            for (PermissionRule child : group.children()) {
                Predicate predicate = compile(child, root, builder, context);
                if (predicate == null) {
                    // 子节点是 AllowAll：ALL_OF 忽略它；ANY_OF 里出现 AllowAll 则整组恒真（不必再加条件）
                    if (group.op() == PermissionRuleOp.ANY_OF) {
                        return null;
                    }
                    continue;
                }
                children.add(predicate);
            }
            if (children.isEmpty()) {
                return null;
            }
            return group.op() == PermissionRuleOp.ANY_OF
                    ? builder.or(children.toArray(new Predicate[0]))
                    : builder.and(children.toArray(new Predicate[0]));
        }
        if (rule instanceof PermissionRule.Leaf leaf) {
            return compileLeaf(leaf, root, builder, context);
        }
        return null;
    }

    private Predicate compileLeaf(PermissionRule.Leaf leaf, Root<?> root, CriteriaBuilder builder, PermissionRuleContext context) {
        Path<?> path = resolvePath(root, leaf.field());

        if (leaf.op() == PermissionRuleOp.IS_NULL) {
            return builder.isNull(path);
        }
        if (leaf.op() == PermissionRuleOp.IS_NOT_NULL) {
            return builder.isNotNull(path);
        }

        if (leaf.contextual()) {
            return compileContextualLeaf(leaf, path, builder, context);
        }
        return compileConstantLeaf(leaf, path, builder);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate compileContextualLeaf(PermissionRule.Leaf leaf, Path<?> path, CriteriaBuilder builder, PermissionRuleContext context) {
        String variable = leaf.contextVar();
        if (leaf.op().needsCollection()) {
            Collection<?> values = context.collection(variable);
            if (values.isEmpty()) {
                return leaf.op() == PermissionRuleOp.NOT_IN ? null : builder.disjunction();
            }
            // CriteriaBuilder.In 的类型参数无法从 Path<?> 的捕获类型推出，这里用裸类型：
            // 运行时只关心 "列 IN (值…)"，值已由 coerce 收敛到路径类型。
            CriteriaBuilder.In in = builder.in(path);
            for (Object value : values) {
                in.value(builder.literal(coerce(value, path.getJavaType())));
            }
            return leaf.op() == PermissionRuleOp.NOT_IN ? builder.not(in) : in;
        }

        Object value = context.scalar(variable);
        if (value == null) {
            // 上下文缺这一维：宁可查不到，也不要放开范围
            return builder.disjunction();
        }
        return switch (leaf.op()) {
            case EQ -> builder.equal(path, coerce(value, path.getJavaType()));
            case NE -> builder.notEqual(path, coerce(value, path.getJavaType()));
            case IN, NOT_IN -> builder.disjunction();
            default -> compare(path, builder, leaf.op(), coerce(value, comparableType(path)));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate compileConstantLeaf(PermissionRule.Leaf leaf, Path<?> path, CriteriaBuilder builder) {
        Object value = leaf.value();
        switch (leaf.op()) {
            case IN:
            case NOT_IN: {
                Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
                if (values.isEmpty()) {
                    return leaf.op() == PermissionRuleOp.NOT_IN ? null : builder.disjunction();
                }
                CriteriaBuilder.In in = builder.in(path);
                for (Object item : values) {
                    in.value(builder.literal(coerce(item, path.getJavaType())));
                }
                return leaf.op() == PermissionRuleOp.NOT_IN ? builder.not(in) : in;
            }
            case LIKE: {
                String pattern = String.valueOf(value);
                return builder.like(asString(path), pattern);
            }
            case EQ:
                return builder.equal(path, coerce(value, path.getJavaType()));
            case NE:
                return builder.notEqual(path, coerce(value, path.getJavaType()));
            default:
                return compare(path, builder, leaf.op(), coerce(value, comparableType(path)));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Predicate compare(Path<?> path, CriteriaBuilder builder, PermissionRuleOp op, Object value) {
        Expression left = path;
        Comparable right = value instanceof Comparable<?> comparable ? comparable : String.valueOf(value);
        return switch (op) {
            case GT -> builder.greaterThan(left, right);
            case GE -> builder.greaterThanOrEqualTo(left, right);
            case LT -> builder.lessThan(left, right);
            case LE -> builder.lessThanOrEqualTo(left, right);
            default -> throw new PermissionRuleException(null, "不支持的比较算子：" + op);
        };
    }

    @SuppressWarnings("unchecked")
    private Expression<String> asString(Path<?> path) {
        if (String.class.equals(path.getJavaType())) {
            // Path<Object> → Expression<String> 不是直接可转的，必须经 Expression<?> 中继
            return (Expression<String>) (Expression<?>) path;
        }
        return path.as(String.class);
    }

    /**
     * 比较运算要求表达式与值同型；实体上多为 Long/Integer/String，取路径类型即可
     */
    private Class<?> comparableType(Path<?> path) {
        Class<?> type = path.getJavaType();
        if (Number.class.isAssignableFrom(type) || String.class.equals(type) || Boolean.class.equals(type)) {
            return type;
        }
        return String.class;
    }

    /**
     * 依次尝试原名、snake→camel、camel→snake，取到为止；都取不到抛异常（不静默跳过）
     */
    private Path<?> resolvePath(Root<?> root, String field) {
        // 候选名列表来自 PermissionRuleSupport：与主体谓词构建同一份规则，两处不会一边修一边没修
        // 标量与关联路径都走同一个解析器：条件树里写 storage.id 与主体谓词里写 storage.id 必须是同一形状
        Path<?> path = JpaPathResolver.resolve(root, field);
        if (path != null) {
            return path;
        }
        throw new PermissionRuleException(null, "条件列 " + field + " 在实体上不存在（试过 "
                + PermissionRuleSupport.pathVariants(field) + "）；请把该列登记进 data_resource 的可过滤列白名单，"
                + "或用关联路径写法（如 storage.id，沿 @ManyToOne 走一跳）");
    }

    /**
     * 数值型收敛：策略里的 JSON 数字一律是 Long，实体字段可能是 Integer/Double
     */
    private Object coerce(Object value, Class<?> targetType) {
        if (value == null || targetType == null || targetType.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (Long.class.equals(targetType) || long.class.equals(targetType)) {
                return number.longValue();
            }
            if (Integer.class.equals(targetType) || int.class.equals(targetType)) {
                return number.intValue();
            }
            if (Double.class.equals(targetType) || double.class.equals(targetType)) {
                return number.doubleValue();
            }
            if (Float.class.equals(targetType) || float.class.equals(targetType)) {
                return number.floatValue();
            }
            if (Short.class.equals(targetType) || short.class.equals(targetType)) {
                return number.shortValue();
            }
        }
        if (String.class.equals(targetType)) {
            return String.valueOf(value);
        }
        if (Boolean.class.equals(targetType) && value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return value;
    }
}
