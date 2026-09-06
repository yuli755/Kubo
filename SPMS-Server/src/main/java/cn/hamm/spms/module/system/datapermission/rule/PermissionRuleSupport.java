package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * <h1>列名 / 属性名互转</h1>
 *
 * <p>{@code data_resource} 里登记的是数据库列名（{@code tenant_id}），
 * JPA 侧要的是实体属性名（{@code tenantId}），MyBatis/JSqlParser 侧要的是列名。
 * 条件树必须同时认这两种写法，否则就会出现"策略配了、条件生成不出来"的静默空转
 * —— 这正是历史实现最贵的一课。</p>
 *
 * @author Hamm.cn
 */
public final class PermissionRuleSupport {

    private PermissionRuleSupport() {
    }

    /**
     * snake_case → camelCase（已是 camelCase 时原样返回）
     */
    public static String toCamel(String value) {
        if (value == null || value.indexOf('_') < 0) {
            return value;
        }
        StringBuilder camel = new StringBuilder(value.length());
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
                continue;
            }
            camel.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return camel.toString();
    }

    /**
     * camelCase → snake_case（已含下划线时原样返回）
     */
    public static String toSnake(String value) {
        if (value == null || value.indexOf('_') >= 0) {
            return value;
        }
        StringBuilder snake = new StringBuilder(value.length() + 4);
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (snake.length() > 0) {
                    snake.append('_');
                }
                snake.append(Character.toLowerCase(c));
            }
            else {
                snake.append(c);
            }
        }
        return snake.toString();
    }

    /**
     * 一个登记名在 JPA 侧的候选写法，按优先级排列
     *
     * <p>{@code data_resource} 的 {@code tenant_column} / {@code owner_columns} 存的是**数据库列名**
     * （{@code tenant_id}），而 {@code Root.get(String)} 要的是**实体属性名**（{@code tenantId}）：
     * 名字取不到时 Hibernate 抛 {@code IllegalArgumentException}。这个转换表只该有一份，
     * 因此它同时服务于条件树编译器与主体谓词构建 —— 否则就会出现"同一个 {@code tenant_id}，
     * 写在 condition_json 里能用、当租户列登记就静默失效"这种半修状态（本仓踩过）。</p>
     *
     * @param field 登记名（列名或属性名皆可，允许带空白）
     * @return 候选名列表；入参为空时返回空列表
     */
    public static List<String> pathCandidates(String field) {
        if (field == null || field.isBlank()) {
            return List.of();
        }
        String trimmed = field.trim();
        List<String> candidates = new ArrayList<>(3);
        candidates.add(trimmed);
        String camel = toCamel(trimmed);
        if (!camel.equals(trimmed)) {
            candidates.add(camel);
        }
        String snake = toSnake(trimmed);
        if (!snake.equals(trimmed) && !candidates.contains(snake)) {
            candidates.add(snake);
        }
        return List.copyOf(candidates);
    }

    /**
     * 按候选名依次尝试取路径，取到为止；都取不到返回 {@code null}
     *
     * <p>调用方自己决定"取不到"意味着什么：条件树要报错（不能静默放开），
     * 主体谓词要计入绑定失败数（由上层按 enforce / shadow 分别拒绝或记账）。</p>
     *
     * @param root  查询根
     * @param field 登记名
     * @return 路径；无法解析时 {@code null}
     */
    public static Path<?> findPath(Root<?> root, String field) {
        for (String candidate : pathCandidates(field)) {
            try {
                Path<?> path = root.get(candidate);
                if (path != null) {
                    return path;
                }
            }
            catch (IllegalArgumentException ignored) {
                // 换下一个候选写法
            }
        }
        return null;
    }

    /**
     * 是否合法 SQL/JPA 标识符
     */
    public static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        if (!Character.isLetter(value.charAt(0)) && value.charAt(0) != '_') {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
