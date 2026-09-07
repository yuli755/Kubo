package cn.hamm.spms.module.system.datapermission.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        // 合法形态 = 「合法标识符」或「一跳关联路径」。isIdentifier 继续只认单段（注入形状仍被拒），
        // 点号只在 isPath 这一层被允许，避免把 a;b / a--b 之类顺带放进条件树。
        if (field == null || !PermissionRuleSupport.isPath(field)) {
            errors.add("field 不是合法属性名或关联路径（形如 creatorId / storage.id）：" + field);
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
                    // 白名单直接吃资源登记串，于是也要认维度前缀（WAREHOUSE:storage.id）：
                    // 这里剥掉前缀、只把路径留进白名单，否则"登记对了、校验说没这列"
                    int at = trimmed.indexOf(OwnerColumnRegistration.SEPARATOR);
                    if (at > 0) {
                        trimmed = trimmed.substring(at + 1).trim();
                    }
                    if (PermissionRuleSupport.isPath(trimmed)) {
                        // 与解析同一份展开表：列名/属性名/关联路径的等价写法一次算全，
                        // 不再各调用点自己 camel+snake 拼一遍（那样迟早一边多一边少）
                        expanded.addAll(PermissionRuleSupport.pathVariants(trimmed));
                    }
                    else {
                        expanded.add(trimmed);
                    }
                }
            }
            columns = Set.copyOf(expanded);
        }

        /**
         * 由资源的三份列登记合成白名单（读侧与写侧校验共用这一个入口）
         *
         * @param tenantColumn      租户列登记值
         * @param ownerColumns      归属列登记串（可带维度前缀与点号路径）
         * @param filterableColumns 其它可过滤列
         * @return 白名单
         */
        public static FieldAllowList ofRegistrations(String tenantColumn, String ownerColumns, String filterableColumns) {
            Set<String> tokens = new LinkedHashSet<>();
            addCsv(tokens, tenantColumn);
            addCsv(tokens, ownerColumns);
            addCsv(tokens, filterableColumns);
            return new FieldAllowList(tokens);
        }

        private static void addCsv(Set<String> target, String csv) {
            if (csv == null || csv.isBlank()) {
                return;
            }
            for (String part : csv.split(",")) {
                if (!part.isBlank()) {
                    target.add(part.trim());
                }
            }
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
            if (columns.isEmpty()) {
                return false;
            }
            // 比对也走同一份展开表：条件树里写 storageDetail.id、资源上登记 storage_detail.id，
            // 是同一条路径的两种写法，不该被判成「不在白名单里」
            for (String variant : PermissionRuleSupport.pathVariants(field)) {
                if (columns.contains(variant)) {
                    return true;
                }
            }
            return false;
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
