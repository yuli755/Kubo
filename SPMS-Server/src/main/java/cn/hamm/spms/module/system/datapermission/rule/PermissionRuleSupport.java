package cn.hamm.spms.module.system.datapermission.rule;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * 拆一条登记路径：{@code storage.id} 两段、{@code creatorId} 一段
     *
     * <p>刻意只认"合法标识符用点号连接"这一种形状，不认 {@code a.b.c.d}、不认引号与空格：
     * 关联路径要变成 {@code root.join(...).get(...)}，形状必须能被静态盘点复现
     * （见 {@code EntityShapeIndex}），宽松一点就会变成"运行期能跑、面板说没配"。</p>
     *
     * @param path 登记路径
     * @return 段列表；非法或空时返回空列表
     */
    public static List<String> pathSegments(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        // limit=-1：保留尾部空段，让 "a." / "a..b" 被下面的逐段校验拒掉。
        // 用默认 split 会把尾部的点悄悄吞成 "a"，等于替配置兜了一个它自己看不见的底
        String[] parts = path.trim().split("\\.", -1);
        if (parts.length == 0 || parts.length > OwnerColumnRegistration.MAX_SEGMENTS) {
            return List.of();
        }
        List<String> segments = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!isIdentifier(part)) {
                return List.of();
            }
            segments.add(part);
        }
        return List.copyOf(segments);
    }

    /**
     * 是否是合法的属性名或关联路径
     *
     * @param path 登记路径
     * @return 合法返回 true
     */
    public static boolean isPath(String path) {
        return !pathSegments(path).isEmpty();
    }

    /**
     * 一条路径的所有等价写法：每一段各自按 camel / snake 展开后组合
     *
     * <p>白名单比对用这个，避免"运营按列名登记、条件树按属性名写"时两边对不上；
     * 同时它也是 {@code FieldAllowList} 与分析器共用的展开表，只此一份。</p>
     *
     * @param path 登记路径
     * @return 等价写法集合（含原样）；非法入参返回空集合
     */
    public static Set<String> pathVariants(String path) {
        List<String> segments = pathSegments(path);
        if (segments.isEmpty()) {
            return Set.of();
        }
        List<List<String>> perSegment = new ArrayList<>(segments.size());
        for (String segment : segments) {
            Set<String> forms = new LinkedHashSet<>();
            forms.add(segment);
            forms.add(toCamel(segment));
            forms.add(toSnake(segment));
            perSegment.add(new ArrayList<>(forms));
        }
        Set<String> variants = new LinkedHashSet<>();
        join(0, perSegment, new ArrayList<>(), variants);
        return Set.copyOf(variants);
    }

    private static void join(int depth, List<List<String>> perSegment, List<String> current, Set<String> out) {
        if (depth == perSegment.size()) {
            out.add(String.join(".", current));
            return;
        }
        for (String form : perSegment.get(depth)) {
            current.add(form);
            join(depth + 1, perSegment, current, out);
            current.remove(current.size() - 1);
        }
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
