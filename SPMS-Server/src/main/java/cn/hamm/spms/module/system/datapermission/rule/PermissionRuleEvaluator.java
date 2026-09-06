package cn.hamm.spms.module.system.datapermission.rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * <h1>条件树内存求值器</h1>
 *
 * <p>存在的理由不是给业务用，而是让"语义"有一个不依赖数据库的裁判：</p>
 * <ul>
 *   <li>契约测试用它作为基准，断言 JPA 编译器与 SQL 渲染器在同一棵树上给出一致结果
 *       （P6 gRPC 拆分时，远端实现也必须过同一组用例）；</li>
 *   <li>影子双跑（{@code data_permission_diff_log}）在小样本上按行复算，
 *       可以把"差异是权限造成的还是 SQL 写法造成的"分清楚，而不是只报一个行数差。</li>
 * </ul>
 *
 * <p>语义按 SQL 三值逻辑的保守子集：字段缺失或为 {@code null} 时，除
 * {@code IS NULL} 外一律不成立。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleEvaluator {

    /**
     * 该行是否满足条件树
     */
    public boolean matches(PermissionRule rule, Map<String, Object> row, PermissionRuleContext context) {
        if (rule == null || rule instanceof PermissionRule.AllowAll) {
            return true;
        }
        if (rule instanceof PermissionRule.Deny) {
            return false;
        }
        if (rule instanceof PermissionRule.Group group) {
            if (group.op() == PermissionRuleOp.ANY_OF) {
                for (PermissionRule child : group.children()) {
                    if (matches(child, row, context)) {
                        return true;
                    }
                }
                return false;
            }
            for (PermissionRule child : group.children()) {
                if (!matches(child, row, context)) {
                    return false;
                }
            }
            return true;
        }
        if (rule instanceof PermissionRule.Leaf leaf) {
            return matchesLeaf(leaf, row, context);
        }
        return false;
    }

    private boolean matchesLeaf(PermissionRule.Leaf leaf, Map<String, Object> row, PermissionRuleContext context) {
        // 行里可能用列名或属性名，两种写法都接受（与编译器的列名/属性名互换策略保持一致）
        Object actual = row.containsKey(leaf.field())
                ? row.get(leaf.field())
                : row.get(PermissionRuleSupport.toCamel(leaf.field()));
        if (actual == null) {
            actual = row.get(PermissionRuleSupport.toSnake(leaf.field()));
        }

        if (leaf.op() == PermissionRuleOp.IS_NULL) {
            return actual == null;
        }
        if (leaf.op() == PermissionRuleOp.IS_NOT_NULL) {
            return actual != null;
        }
        if (actual == null) {
            return false;
        }

        Object expected = leaf.contextual()
                ? (leaf.op().needsCollection() ? context.collection(leaf.contextVar()) : context.scalar(leaf.contextVar()))
                : leaf.value();

        switch (leaf.op()) {
            case IN: {
                Collection<?> values = asCollection(expected);
                if (values.isEmpty()) {
                    return false;
                }
                return values.stream().anyMatch(item -> equal(actual, item));
            }
            case NOT_IN: {
                Collection<?> values = asCollection(expected);
                if (values.isEmpty()) {
                    return true;
                }
                return values.stream().noneMatch(item -> equal(actual, item));
            }
            case LIKE: {
                String pattern = String.valueOf(expected);
                String text = String.valueOf(actual);
                return matchesLike(text, pattern);
            }
            default:
                break;
        }

        int comparison = compare(actual, expected);
        if (comparison == null) {
            return false;
        }
        return switch (leaf.op()) {
            case EQ -> comparison == 0;
            case NE -> comparison != 0;
            case GT -> comparison > 0;
            case GE -> comparison >= 0;
            case LT -> comparison < 0;
            case LE -> comparison <= 0;
            default -> false;
        };
    }

    /**
     * {@code %} / {@code _} 通配的最小实现（够用于比对与测试，不追求 SQL 的全部 LIKE 语义）
     */
    private boolean matchesLike(String text, String pattern) {
        String regex = pattern.chars()
                .mapToObj(c -> {
                    char ch = (char) c;
                    if (ch == '%') {
                        return ".*";
                    }
                    if (ch == '_') {
                        return ".";
                    }
                    return java.util.regex.Pattern.quote(String.valueOf(ch));
                })
                .reduce("", (a, b) -> a + b);
        return text.matches(regex);
    }

    /**
     * @return 比较结果；两边类型不可比时返回 null（判定为不成立）
     */
    private Integer compare(Object actual, Object expected) {
        if (expected == null) {
            return null;
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue());
        }
        if (actual instanceof Boolean || expected instanceof Boolean) {
            return Boolean.compare(asBoolean(actual), asBoolean(expected));
        }
        if (actual instanceof Collection || expected instanceof Collection) {
            return null;
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private boolean equal(Object actual, Object expected) {
        if (actual.equals(expected)) {
            return true;
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return actualNumber.doubleValue() == expectedNumber.doubleValue();
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        List<Object> single = new ArrayList<>(1);
        if (value != null) {
            single.add(value);
        }
        return single;
    }
}
