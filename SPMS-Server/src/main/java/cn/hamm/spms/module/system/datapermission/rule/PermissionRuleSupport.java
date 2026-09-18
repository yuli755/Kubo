package cn.hamm.spms.module.system.datapermission.rule;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <h1>列名 / 属性名互转</h1>
 *
 * <p>{@code data_resource} 里登记的是数据库列名（{@code tenant_id}），而历史策略里也写满了
 * 实体属性名（{@code tenantId}）——<b>注入与渲染只认列名，但解析/校验/盘点阶段必须同时认这两种写法</b>：
 * 否则就会出现"策略配了、条件生成不出来"的静默空转 —— 这正是历史实现最贵的一课。</p>
 *
 * <p>J4（2026-09-18）：本类原先还替 JPA 侧回答"这个登记名能不能 `root.get()` 到"（`findPath`），
 * 那一半随 {@code spring-boot-starter-data-jpa} 一起删除；留下的是纯字符串/标识符规则，
 * 没有一行依赖某个 ORM 的类型系统。</p>
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
     * 一个登记名的候选写法，按优先级排列
     *
     * <p>{@code data_resource} 的 {@code tenant_column} / {@code owner_columns} 存的是**数据库列名**
     * （{@code tenant_id}），而策略的 {@code condition_json} 与实体盘点里写的是**属性名**
     * （{@code tenantId}）：枚举实体字段、比对白名单、生成列名都要把这两边对上。
     * 这个转换表只该有一份 —— 否则就会出现"同一个 {@code tenant_id}，写在 condition_json 里能用、
     * 当租户列登记就静默失效"这种半修状态（本仓踩过）。</p>
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
     * 拆一条登记路径：{@code storage.id} 两段、{@code creatorId} 一段
     *
     * <p>刻意只认"合法标识符用点号连接"这一种形状，不认 {@code a.b.c.d}、不认引号与空格：
     * 关联路径最终要变成本表外键列（{@code storage.id} ⇒ {@code storage_id}），形状必须能被静态盘点复现
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
     * 是否合法 SQL 标识符（列名/表别名；只允许字母、数字、下划线，且不以数字开头）
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
