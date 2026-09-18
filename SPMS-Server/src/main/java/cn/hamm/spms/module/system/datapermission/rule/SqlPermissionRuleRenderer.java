package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;
import java.util.ArrayList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <h1>条件树 → SQL 片段渲染器（P2 换轨用）</h1>
 *
 * <p>产出 {@link RowPermissionPredicate}：带<b>命名参数占位符</b>的 SQL 片段 + 参数表。
 * 与 JPA 编译器共用同一棵 {@link PermissionRule}，所以"JPA 侧能看到的行"和
 * "MyBatis 侧能看到的行"在语义上必然一致 —— 这是把 {@code page/detail/stat/export}
 * 四个出口统一到一套判定的前提。</p>
 *
 * <h2>为什么不内联字面量</h2>
 * <p>直接把值拼进 SQL 文本（{@code tenant_id = 5}）看着省事，但它把"列名可拼"错误地
 * 推广成"值也可以拼"，一旦有策略允许字符串比较就是注入口。这里坚持：
 * <b>标识符白名单校验后拼接，值一律参数化</b>。调用方若无法绑定命名参数
 * （例如裸字符串拼进 XML），必须拒绝而不是退化成内联，见 {@link #render}。</p>
 *
 * @author Hamm.cn
 */
public final class SqlPermissionRuleRenderer {

    private static final String PARAM_PREFIX = "dpRule";

    /**
     * 渲染结果携带的参数数量上限（超过说明条件树过大，应在校验期就被挡住）
     */
    public static final int MAX_PARAMETERS = 128;

    /**
     * 渲染条件树
     *
     * @param rule      条件树（已通过 {@link PermissionRuleValidator}）
     * @param tableAlias 表别名；为空表示不带别名
     * @param context   上下文变量来源
     * @return SQL 片段与参数；{@code null} 表示无条件（AllowAll）
     */
    public RowPermissionPredicate render(PermissionRule rule, String tableAlias, PermissionRuleContext context) {
        if (rule == null || !rule.contributes()) {
            return null;
        }
        StringBuilder sql = new StringBuilder();
        Map<String, Object> parameters = new LinkedHashMap<>();
        int[] counter = {0};
        boolean rendered = renderNode(rule, tableAlias, context, sql, parameters, counter);
        if (!rendered) {
            return null;
        }
        if (parameters.size() > MAX_PARAMETERS) {
            throw new PermissionRuleException(null, "条件参数超过 " + MAX_PARAMETERS + " 个，请拆分策略");
        }
        return new RowPermissionPredicate(sql.toString(), parameters);
    }

    private boolean renderNode(PermissionRule rule, String tableAlias, PermissionRuleContext context,
                               StringBuilder sql, Map<String, Object> parameters, int[] counter) {
        if (rule instanceof PermissionRule.Deny) {
            appendGroup(sql, RowPermissionPredicate.DENY_SQL);
            return true;
        }
        if (rule instanceof PermissionRule.AllowAll) {
            return false;
        }
        if (rule instanceof PermissionRule.Group group) {
            String joiner = group.op() == PermissionRuleOp.ANY_OF ? " OR " : " AND ";
            StringBuilder inner = new StringBuilder();
            boolean any = false;
            for (PermissionRule child : group.children()) {
                StringBuilder childSql = new StringBuilder();
                if (!renderNode(child, tableAlias, context, childSql, parameters, counter)) {
                    // AllowAll 子节点：AND 忽略，OR 使整组恒真
                    if (group.op() == PermissionRuleOp.ANY_OF) {
                        return false;
                    }
                    continue;
                }
                if (any) {
                    inner.append(joiner);
                }
                inner.append(childSql);
                any = true;
            }
            if (!any) {
                return false;
            }
            appendGroup(sql, inner.toString());
            return true;
        }
        if (rule instanceof PermissionRule.Dimension dimension) {
            return renderDimension(dimension, tableAlias, sql, parameters, counter);
        }
        if (rule instanceof PermissionRule.Leaf leaf) {
            return renderLeaf(leaf, tableAlias, context, sql, parameters, counter);
        }
        return false;
    }

    /**
     * 主体维度 → SQL 片段（本仓唯一的条件注入出口，读的是策略建树器产出的那棵树）
     *
     * <p>SQL 侧没有实体元模型可问，所以"挑第一个绑得上的候选"在这里退化成"取登记的第一个"。
     * 两者不一致的情形只有一种：登记的第一个候选在数据库里有列、在这个实体的 JPA 映射里却没有
     * —— 那时 JPA 侧会跳到第二个候选去过滤，SQL 侧仍按第一个过滤，行数因此不同。这个差异
     * <b>正是要暴露的东西</b>（它就是"实体没映射归属列"），而 JPA 侧那条 binding 计数已经先响了；
     * 不要在这里加"两边对齐"的补丁去把它抹平。</p>
     */
    private boolean renderDimension(PermissionRule.Dimension dimension, String tableAlias, StringBuilder sql,
                                    Map<String, Object> parameters, int[] counter) {
        if (!dimension.hasCandidates()) {
            return false;
        }
        List<String> candidates = dimension.orAllCandidates()
                ? dimension.candidateFields()
                : List.of(dimension.candidateFields().get(0));
        if (dimension.value() == null) {
            // 与 Leaf 同口径：这一维没取到值就恒假。JPA 侧对应"与该列比 null"，同样一行不给看
            appendGroup(sql, RowPermissionPredicate.DENY_SQL);
            return true;
        }
        List<String> parts = new ArrayList<>();
        for (String candidate : candidates) {
            String column = column(tableAlias, candidate);
            String name = nextParameter(counter);
            parameters.put(name, dimension.value());
            parts.add(column + " " + operator(dimension.op()) + " :" + name);
        }
        appendGroup(sql, String.join(" OR ", parts));
        return true;
    }

    private boolean renderLeaf(PermissionRule.Leaf leaf, String tableAlias, PermissionRuleContext context,
                               StringBuilder sql, Map<String, Object> parameters, int[] counter) {
        if (!PermissionRuleSupport.isIdentifier(resolveOneHopRelation(leaf.field()))) {
            throw new PermissionRuleException(null, "非法列名：" + leaf.field());
        }
        String column = column(tableAlias, leaf.field());

        if (leaf.op() == PermissionRuleOp.IS_NULL) {
            appendGroup(sql, column + " IS NULL");
            return true;
        }
        if (leaf.op() == PermissionRuleOp.IS_NOT_NULL) {
            appendGroup(sql, column + " IS NOT NULL");
            return true;
        }

        Object value = leaf.contextual() ? resolve(leaf, context) : leaf.value();

        if (leaf.op() == PermissionRuleOp.IN || leaf.op() == PermissionRuleOp.NOT_IN) {
            Collection<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
            if (values.isEmpty()) {
                if (leaf.op() == PermissionRuleOp.NOT_IN) {
                    return false;
                }
                appendGroup(sql, RowPermissionPredicate.DENY_SQL);
                return true;
            }
            // 逐项命名参数，而不是 "IN (:list)" 的单参数集合展开：
            // 后者依赖驱动/框架的展开行为，MP 拦截器注入的片段没有那层处理，
            // 逐位占位符是各驱动都支持、也最不容易被误用的写法。
            String name = nextParameter(counter);
            StringBuilder placeholders = new StringBuilder();
            int index = 0;
            for (Object item : values) {
                if (index > 0) {
                    placeholders.append(", ");
                }
                String itemParam = name + "_" + index;
                parameters.put(itemParam, item);
                placeholders.append(':').append(itemParam);
                index++;
            }
            appendGroup(sql, column + (leaf.op() == PermissionRuleOp.NOT_IN ? " NOT IN (" : " IN (")
                    + placeholders + ")");
            return true;
        }

        if (value == null) {
            // 上下文维度缺失：宁可恒假
            appendGroup(sql, RowPermissionPredicate.DENY_SQL);
            return true;
        }

        String name = nextParameter(counter);
        parameters.put(name, value);
        appendGroup(sql, column + " " + operator(leaf.op()) + " :" + name);
        return true;
    }

    private Object resolve(PermissionRule.Leaf leaf, PermissionRuleContext context) {
        if (leaf.op().needsCollection()) {
            return context.collection(leaf.contextVar());
        }
        return context.scalar(leaf.contextVar());
    }

    /**
     * 一跳关联路径 → 本表外键列：{@code storage.id} → {@code storage_id}
     *
     * <p><b>为什么在渲染器里做</b>：P2（J2）之后读路径不再由 JPA 编译，片段要落到 MyBatis-Plus 的
     * wrapper / Mapper 上，两者都只认本表列 —— 单条 SQL 片段拼不出 JOIN。而 Hibernate 对
     * {@code @ManyToOne} 的 {@code .get("id")} 本来也是直取外键列（没有 join），所以
     * "一跳的 .id" 在语义上就是 FK 列，转写不改变判据。</p>
     *
     * <p>为什么只有一跳的 {@code .id}：{@code storage.name} 这类要 JOIN 才能比较，
     * {@code a.b.c} 是两跳 —— 这两类仍旧抛（宁可响亮地失败，也不要编出一个查不到数据的条件）。</p>
     *
     * @param field 规则里的字段写法（可能是属性名，也可能是关联路径）
     * @return 可以直接当列名用的写法（仍走 {@link PermissionRuleSupport#toSnake}）
     */
    private static String resolveOneHopRelation(String field) {
        if (field == null || field.indexOf('.') < 0) {
            return field;
        }
        int dot = field.indexOf('.');
        String head = field.substring(0, dot);
        String tail = field.substring(dot + 1);
        if ("id".equals(tail)) {
            return head + "Id";
        }
        throw new PermissionRuleException(null, "SQL 侧不支持关联路径 " + field
                + "：只有一跳的 .id 能落到本表外键列（storage.id → storage_id），"
                + "比较关联表上的其它列需要显式 JOIN");
    }

    private String column(String tableAlias, String field) {
        String physical = PermissionRuleSupport.toSnake(resolveOneHopRelation(field));
        if (!PermissionRuleSupport.isIdentifier(physical)) {
            throw new PermissionRuleException(null, "非法列名：" + physical);
        }
        if (tableAlias == null || tableAlias.isBlank()) {
            return physical;
        }
        if (!PermissionRuleSupport.isIdentifier(tableAlias)) {
            throw new PermissionRuleException(null, "非法表别名：" + tableAlias);
        }
        return tableAlias + "." + physical;
    }

    private String operator(PermissionRuleOp op) {
        return switch (op) {
            case EQ -> "=";
            case NE -> "<>";
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
            case LIKE -> "LIKE";
            default -> throw new PermissionRuleException(null, "该算子不能直接渲染为二元比较：" + op);
        };
    }

    private String nextParameter(int[] counter) {
        return PARAM_PREFIX + (counter[0]++);
    }

    private void appendGroup(StringBuilder sql, String fragment) {
        if (sql.length() > 0) {
            sql.append(" AND ");
        }
        sql.append('(').append(fragment).append(')');
    }
}
