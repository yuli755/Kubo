package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.service.RowPermissionPredicate;

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
            appendGroup(sql, "1 = 0");
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
        if (rule instanceof PermissionRule.Leaf leaf) {
            return renderLeaf(leaf, tableAlias, context, sql, parameters, counter);
        }
        return false;
    }

    private boolean renderLeaf(PermissionRule.Leaf leaf, String tableAlias, PermissionRuleContext context,
                               StringBuilder sql, Map<String, Object> parameters, int[] counter) {
        if (leaf.field() != null && leaf.field().indexOf('.') >= 0) {
            // 单条 SQL 片段拼不出 JOIN，所以关联路径必须在**这里**被明确拒掉。今天没有读路径走渲染器
            // （JPA 侧编译，见 JpaPathResolver），但 P2 换轨会 —— 那时若让它落到下面的 toSnake，
            // storage.id 会变成 storage_id：轻则 SQL 报"列不存在"，重则命中一个同名的别的列（静默放宽范围）。
            throw new PermissionRuleException(null, "SQL 侧不支持关联路径 " + leaf.field()
                    + "：需要显式 JOIN（施工图 D1(b)，P2 换轨时实现），JPA 读侧已支持该写法");
        }
        if (!PermissionRuleSupport.isIdentifier(leaf.field())) {
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
                appendGroup(sql, "1 = 0");
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
            appendGroup(sql, "1 = 0");
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

    private String column(String tableAlias, String field) {
        String physical = PermissionRuleSupport.toSnake(field);
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
