package cn.hamm.spms.module.system.datapermission.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <h1>行级权限 SQL 谓词</h1>
 *
 * <p>只生成带参数占位符的谓词，不把用户输入直接拼接到 SQL 值中。</p>
 *
 * <p>{@link #forMyBatis()} 是本类与 MyBatis 侧唯一的接缝：渲染器产出的是 JPA/命名参数风格
 * （{@code material.tenant_id = :dpRule0}），MyBatis 要 {@code #{dpRule0}}。换写只在这一处做，
 * 并且<b>只换写、不放宽</b>：片段引用了参数表里没有的键、或参数表里有片段没用到的键，都直接抛 ——
 * 后者意味着片段被截断，此时"少一个条件"的表现是<em>多放行一些行</em>，绝不能默认通过。</p>
 *
 * @author Hamm.cn
 */
public record RowPermissionPredicate(String sql, Map<String, Object> parameters) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 片段里的命名参数（渲染器只会产出 {@code :dpRuleN} / {@code :dpRuleN_序号} 这一种形式）
     */
    private static final Pattern NAMED_PARAMETER = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");

    /**
     * 片段里禁止出现的记号：语句分隔与注释。片段最终是拼进 SQL 文本的，这两样一旦出现，
     * 说明它不是渲染器产出的（渲染器的列名走白名单、值一律参数化，两处都产不出这些字符）。
     */
    private static final Pattern FORBIDDEN_TOKEN = Pattern.compile("(;|--|/\\*|\\*/)");

    /**
     * MyBatis 语句里承载片段的参数名；XML 中的 {@code ${fragment}} 必须与此同值（由测试对账）
     */
    public static final String FRAGMENT_KEY = "fragment";

    public RowPermissionPredicate {
        parameters = Map.copyOf(parameters);
    }

    public static RowPermissionPredicate tenant(String tableAlias, String tenantColumn, Long tenantId) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(tenantColumn, "tenantColumn");
        if (tenantId == null) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + tenantColumn + " = :dataPermissionTenantId",
                Map.of("dataPermissionTenantId", tenantId)
        );
    }

    public static RowPermissionPredicate owner(String tableAlias, String ownerColumn, Long userId) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(ownerColumn, "ownerColumn");
        if (userId == null) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + ownerColumn + " = :dataPermissionOwnerId",
                Map.of("dataPermissionOwnerId", userId)
        );
    }

    public static RowPermissionPredicate department(String tableAlias, String departmentColumn, Set<Long> departmentIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(departmentColumn, "departmentColumn");
        if (departmentIds == null || departmentIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + departmentColumn + " IN (:dataPermissionDepartmentIds)",
                Map.of("dataPermissionDepartmentIds", departmentIds)
        );
    }

    public static RowPermissionPredicate factory(String tableAlias, String factoryColumn, Set<Long> factoryIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(factoryColumn, "factoryColumn");
        if (factoryIds == null || factoryIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + factoryColumn + " IN (:dataPermissionFactoryIds)",
                Map.of("dataPermissionFactoryIds", factoryIds)
        );
    }

    public static RowPermissionPredicate warehouse(String tableAlias, String warehouseColumn, Set<Long> warehouseIds) {
        requireIdentifier(tableAlias, "tableAlias");
        requireIdentifier(warehouseColumn, "warehouseColumn");
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return deny();
        }
        return new RowPermissionPredicate(
                tableAlias + "." + warehouseColumn + " IN (:dataPermissionWarehouseIds)",
                Map.of("dataPermissionWarehouseIds", warehouseIds)
        );
    }

    public static RowPermissionPredicate deny() {
        return new RowPermissionPredicate("1 = 0", Map.of());
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " contains an unsafe SQL identifier");
        }
    }

    /**
     * 换写成 MyBatis 的命名参数形式：{@code :dpRule0} → {@code #{dpRule0}}
     *
     * @return 换写后的片段（参数表原样带过去）
     * @throws IllegalArgumentException 片段与参数表不吻合、出现被禁记号、或参数值不是可安全绑定的标量
     */
    public RowPermissionPredicate forMyBatis() {
        if (FORBIDDEN_TOKEN.matcher(sql).find()) {
            throw new IllegalArgumentException("permission fragment contains a forbidden SQL token (';' or a comment)");
        }
        Matcher matcher = NAMED_PARAMETER.matcher(sql);
        StringBuilder translated = new StringBuilder();
        Set<String> referenced = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) {
                throw new IllegalArgumentException("fragment references :" + name + " but no such parameter was provided");
            }
            requireBindable(name, parameters.get(name));
            referenced.add(name);
            matcher.appendReplacement(translated, Matcher.quoteReplacement("#{" + name + "}"));
        }
        matcher.appendTail(translated);
        Set<String> unused = new LinkedHashSet<>(parameters.keySet());
        unused.removeAll(referenced);
        if (!unused.isEmpty()) {
            throw new IllegalArgumentException("parameter map carries keys the fragment never references: " + unused);
        }
        return new RowPermissionPredicate(translated.toString(), parameters);
    }

    /**
     * 组装 MyBatis 语句的参数对象（{@code fragment} + 片段用到的全部命名参数）
     *
     * @return 可直接交给 Mapper 方法的参数 Map
     */
    public Map<String, Object> myBatisStatementParameters() {
        RowPermissionPredicate translated = forMyBatis();
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put(FRAGMENT_KEY, translated.sql());
        statement.putAll(translated.parameters());
        return statement;
    }

    /**
     * 只绑定标量。集合值一律拒绝：渲染器把 {@code IN} 列表逐项展开成 {@code :dpRule0_0} 这样的
     * 独立参数（它自己在注释里写了理由：{@code IN (:list)} 的展开依赖驱动行为，拦截器注入的片段
     * 没有那层处理）。走到这儿还有集合，说明片段不是渲染器产出的，是有人另拼了一份。
     */
    private static void requireBindable(String name, Object value) {
        // 不在这里查 null：紧凑构造器里的 Map.copyOf 已经让 null 值到不了这一步（它抛 NPE）。
        // 少写一条"看起来更严"的分支，是为了让读代码的人不必去猜哪条防线是活的。
        if (value instanceof Collection<?> || value.getClass().isArray()) {
            throw new IllegalArgumentException("parameter " + name + " is a collection; fragments must come from "
                    + "SqlPermissionRuleRenderer, which expands IN lists into scalar parameters");
        }
        if (!(value instanceof Number || value instanceof String || value instanceof Boolean)) {
            throw new IllegalArgumentException("parameter " + name + " has an unbindable type: "
                    + value.getClass().getName());
        }
    }
}
