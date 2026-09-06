package cn.hamm.spms.module.system.datapermission.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <h1>条件树校验器</h1>
 *
 * <p>解析只保证"是 JSON"，这里保证"是可安全执行的受控条件"：</p>
 * <ol>
 *   <li>深度、分组子节点数、叶子总数上限 —— 防止一条策略把查询复杂度打爆；</li>
 *   <li>{@code field} 必须是合法标识符<b>且</b>在该资源的白名单内（列白名单来自
 *       {@code data_resource} 的 tenant/owner/filterable 列）—— 白名单为空即全部拒绝，
 *       <b>不做"猜列名"</b>；</li>
 *   <li>{@code contextVar} 只允许当前主体已解析出来的维度；</li>
 *   <li>算子与值形态匹配（{@code IN} 需要集合或上下文集合变量、{@code LIKE} 需要字符串）。</li>
 * </ol>
 *
 * <p>注：值一律走参数绑定，因此<b>没有</b>对字符串值做"关键词黑名单"式检查
 * （那种做法既拦不住真攻击，又会把合法业务数据误杀）。识别符才需要严格校验，
 * 因为它会被拼进 SQL 结构里。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleValidator {

    /**
     * 树最大深度（含叶子）
     */
    public static final int MAX_DEPTH = 8;

    /**
     * 单个分组的子节点上限
     */
    public static final int MAX_CHILDREN = 20;

    /**
     * 整棵树的叶子总数上限（≈ 生成的 SQL 条件数）
     */
    public static final int MAX_LEAVES = 64;

    /**
     * 标识符：字母/数字/下划线，不能以数字开头，不允许点号（防止 {@code a.b} 跨表或注释技巧）
     */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final FieldAllowList allowList;

    public PermissionRuleValidator(FieldAllowList allowList) {
        this.allowList = allowList == null ? FieldAllowList.none() : allowList;
    }

    /**
     * 校验一棵树；不合法抛 {@link PermissionRuleException}
     */
    public void validate(String resourceKey, PermissionRule rule) {
        List<String> errors = collect(resourceKey, rule, 1, new int[]{0});
        if (!errors.isEmpty()) {
            throw new PermissionRuleException(resourceKey, "条件树不合法：" + String.join("；", errors));
        }
    }

    private List<String> collect(String resourceKey, PermissionRule rule, int depth, int[] leafCounter) {
        List<String> errors = new ArrayList<>();
        if (rule instanceof PermissionRule.Group group) {
            if (depth > MAX_DEPTH) {
                errors.add("嵌套深度超过 " + MAX_DEPTH);
                return errors;
            }
            if (group.children().isEmpty() || group.children().size() > MAX_CHILDREN) {
                errors.add("分组子节点数必须在 1.." + MAX_CHILDREN + "，实际 " + group.children().size());
                return errors;
            }
            for (PermissionRule child : group.children()) {
                errors.addAll(collect(resourceKey, child, depth + 1, leafCounter));
            }
            return errors;
        }
        if (rule instanceof PermissionRule.Leaf leaf) {
            leafCounter[0]++;
            if (leafCounter[0] > MAX_LEAVES) {
                errors.add("条件数量超过上限 " + MAX_LEAVES);
                return errors;
            }
            validateField(resourceKey, leaf, errors);
            validateValue(resourceKey, leaf, errors);
            return errors;
        }
        return errors;
    }

    private void validateField(String resourceKey, PermissionRule.Leaf leaf, List<String> errors) {
        String field = leaf.field();
        if (field == null || !PermissionRuleSupport.isIdentifier(field) || !IDENTIFIER.matcher(field).matches()) {
            errors.add("field 不是合法标识符：" + field);
            return;
        }
        if (!allowList.contains(field)) {
            errors.add("field " + field + " 不在资源 " + resourceKey + " 的可过滤列白名单内（白名单：" + allowList + "）");
        }
        if (leaf.contextual() && !PermissionRuleContext.isSupported(leaf.contextVar())) {
            errors.add("contextVar " + leaf.contextVar() + " 不可用，仅支持 " + PermissionRuleContext.supported());
        }
    }

    private void validateValue(String resourceKey, PermissionRule.Leaf leaf, List<String> errors) {
        if (!leaf.op().needsValue()) {
            return;
        }
        if (leaf.contextual()) {
            if (!leaf.op().needsCollection() && !PermissionRuleContext.isCollectionVar(leaf.contextVar())) {
                return;
            }
            if (leaf.op().needsCollection() && !PermissionRuleContext.isCollectionVar(leaf.contextVar())) {
                errors.add(leaf.op() + " 需要集合型 contextVar，" + leaf.contextVar() + " 是标量");
            }
            return;
        }
        Object value = leaf.value();
        if (value == null) {
            errors.add(leaf.op() + " 缺少 value");
            return;
        }
        if (leaf.op().needsCollection() && !(value instanceof List<?>)) {
            errors.add(leaf.op() + " 的 value 必须是数组");
        }
        if (leaf.op() == PermissionRuleOp.LIKE && !(value instanceof String)) {
            errors.add("LIKE 的 value 必须是字符串");
        }
        if (leaf.op().needsCollection() && value instanceof List<?> items && items.isEmpty()) {
            // 空 IN 是配置错误：语义上等价于"该主体没有任何可见范围"，应写成 {"deny": true}
            errors.add(leaf.op() + " 的 value 是空数组；如需恒假请显式写 {\"deny\": true}");
        }
    }

    /**
     * 某资源允许被条件引用的列
     *
     * @param columns 同时登记 snake_case 与 camelCase 两种写法（DB 列名与 JPA 属性名）
     */
    public record FieldAllowList(Set<String> columns) {

        public FieldAllowList {
            Set<String> expanded = new LinkedHashSet<>();
            if (columns != null) {
                for (String column : columns) {
                    if (column == null || column.isBlank()) {
                        continue;
                    }
                    String trimmed = column.trim();
                    expanded.add(trimmed);
                    expanded.add(PermissionRuleSupport.toCamel(trimmed));
                    expanded.add(PermissionRuleSupport.toSnake(trimmed));
                }
            }
            columns = Set.copyOf(expanded);
        }

        public static FieldAllowList none() {
            return new FieldAllowList(Set.of());
        }

        public static FieldAllowList of(String... columnNames) {
            return new FieldAllowList(Set.of(columnNames));
        }

        public static FieldAllowList ofCsv(String csv) {
            Set<String> columns = new LinkedHashSet<>();
            if (csv != null) {
                for (String part : csv.split(",")) {
                    if (!part.isBlank()) {
                        columns.add(part.trim());
                    }
                }
            }
            return new FieldAllowList(columns);
        }

        public boolean contains(String field) {
            return !columns.isEmpty() && columns.contains(field);
        }

        public boolean isEmpty() {
            return columns.isEmpty();
        }

        /**
         * 从两个来源合并：资源的归属列 + 显式登记的可过滤列
         */
        public static FieldAllowList merge(FieldAllowList... parts) {
            Set<String> all = new LinkedHashSet<>();
            for (FieldAllowList part : parts) {
                if (part != null) {
                    all.addAll(part.columns());
                }
            }
            return new FieldAllowList(all);
        }

        @Override
        public String toString() {
            return columns.isEmpty() ? "（未配置任何可过滤列）" : String.join(",", columns);
        }

    }
}
