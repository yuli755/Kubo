package cn.hamm.spms.module.system.datapermission.rule;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionSubjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <h1>{@code data_resource.owner_columns} 的登记语法</h1>
 *
 * <p>这个字段历史上只是一串逗号分隔的列名，于是同一串被两处按<b>不同语义</b>读走：
 * {@code buildOwnerPredicate} 拿它比"用户 ID"，部门档又拿它的<b>第一项</b>比"部门 ID"。
 * 一串名字不可能同时是两种东西 —— 而因为它"绑得上"，运行期连绑定失败都不会记（见 §19.25）。
 * 另一头，仓库/工厂两档的属性名写死在代码里，资源登记完全管不到，实体上用 {@code @ManyToOne storage}
 * 表达归属时根本没有入口。</p>
 *
 * <p><b>本类是这两件事的共同解</b>：登记项允许带维度前缀，并允许点号关联路径（施工图 D1(b)）：</p>
 * <ul>
 *   <li>{@code creatorId} —— 裸写法：按今天的语义走（负责人列；部门列仍取第一项），旧配置不用改；</li>
 *   <li>{@code WAREHOUSE:storage.id} —— 指明这一项属于哪一档，且沿关联取列；</li>
 *   <li>{@code DEPARTMENT:departmentId} —— 有了它，部门档就不再和负责人档抢同一列。</li>
 * </ul>
 *
 * <p>维度前缀只接受 {@link DataPermissionSubjectType} 里的名字：写错成 {@code WAREHOUSEX:} 时
 * <b>整串判非法</b>而不是悄悄忽略 —— 悄悄忽略等于"配了没用"，那正是本模块一路在消灭的东西。</p>
 *
 * @author Hamm.cn
 */
public record OwnerColumnRegistration(String dimension, String path) {

    /**
     * 维度前缀的分隔符
     */
    public static final String SEPARATOR = ":";

    /**
     * 一条路径最多几段：2 = 一跳关联封顶（{@code storage.id}）
     *
     * <p>再深就说明该把中间列冗余出来，而不是让权限内核去拼任意深度的 join 树：那种树既没法被静态盘点
     * 复现（面板就会说谎），也容易把执行计划拖没。</p>
     */
    public static final int MAX_SEGMENTS = 2;

    /**
     * 合法登记的逗号串校验
     *
     * @param csv 原始登记值（可为空）
     * @return 非法原因；{@code null} 表示合法
     */
    public static String validate(String csv) {
        if (csv == null || csv.isBlank()) {
            return null;
        }
        for (String raw : csv.split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            String item = raw.trim();
            int at = item.indexOf(SEPARATOR);
            String path = item;
            if (at >= 0) {
                String dimension = item.substring(0, at).trim().toUpperCase(Locale.ROOT);
                path = item.substring(at + 1).trim();
                if (!isKnownDimension(dimension)) {
                    return "维度前缀 " + item.substring(0, at).trim() + " 不是 DataPermissionSubjectType 里的成员（可用："
                            + knownDimensions() + "）";
                }
            }
            if (!PermissionRuleSupport.isPath(path)) {
                // 这里必须把"最多一跳"讲清楚：只说"不合法"，运营会去猜是不是大小写或空格的问题
                return "归属列「" + path + "」不是合法的属性名或关联路径（合法写法：creatorId，或沿关联走一跳的 "
                        + "storage.id；两段以上不支持，请把中间列冗余出来）";
            }
        }
        return null;
    }

    /**
     * 解析登记串
     *
     * @param csv 逗号分隔的登记值，项内可带 {@code DIM:} 前缀
     * @return 解析结果（保持登记顺序）；空白项被跳过
     */
    public static List<OwnerColumnRegistration> parse(String csv) {
        List<OwnerColumnRegistration> items = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return items;
        }
        for (String raw : csv.split(",")) {
            if (raw.isBlank()) {
                continue;
            }
            String item = raw.trim();
            int at = item.indexOf(SEPARATOR);
            if (at > 0) {
                String dimension = item.substring(0, at).trim().toUpperCase(Locale.ROOT);
                String path = item.substring(at + 1).trim();
                if (isKnownDimension(dimension) && PermissionRuleSupport.isPath(path)) {
                    items.add(new OwnerColumnRegistration(dimension, path));
                }
                continue;
            }
            if (PermissionRuleSupport.isPath(item)) {
                items.add(new OwnerColumnRegistration(null, item));
            }
        }
        return items;
    }

    /**
     * 某档专用（带该维度前缀）的登记项
     *
     * @param items     解析结果
     * @param dimension 维度名
     * @return 该维度的路径列表，按登记顺序；没有则空列表
     */
    public static List<String> pathsFor(List<OwnerColumnRegistration> items, String dimension) {
        List<String> paths = new ArrayList<>();
        for (OwnerColumnRegistration item : items) {
            if (item.dimension() != null && item.dimension().equals(dimension)) {
                paths.add(item.path());
            }
        }
        return paths;
    }

    /**
     * 没写维度前缀的登记项 —— 这是"兼容今天语义"的那一半：负责人谓词照旧逐个试
     *
     * @param items 解析结果
     * @return 未标维度的路径列表
     */
    public static List<String> untaggedPaths(List<OwnerColumnRegistration> items) {
        List<String> paths = new ArrayList<>();
        for (OwnerColumnRegistration item : items) {
            if (item.dimension() == null) {
                paths.add(item.path());
            }
        }
        return paths;
    }

    /**
     * 某一档实际要绑的列：有该维度前缀项就用它们，一个都没有才回退到既有取值
     *
     * <p><b>这一句回退是全模块唯一的一份</b>：读侧拼谓词、盘点面板、（将来）保存期校验都只能走这里。
     * 回退是硬要求 —— 现网 24 个资源的 {@code owner_columns} 全是裸写法，一旦"必须带前缀才生效"，
     * 升完版本所有人的 SELF / DEPT 策略当场查空。前缀是给新场景开的一条路，不是把老路堵上。</p>
     *
     * @param items     解析结果
     * @param dimension 档位名（{@code DataPermissionSubjectType#name()}）
     * @param fallback  无该维度显式登记时的既有候选
     * @return 列登记（可能是关联路径）
     */
    public static List<String> candidatesFor(List<OwnerColumnRegistration> items, String dimension,
                                             List<String> fallback) {
        List<String> scoped = pathsFor(items, dimension);
        return scoped.isEmpty() ? fallback : scoped;
    }

    /**
     * {@link #candidatesFor(List, String, List)} 的登记串入口（调用方不必自己先 parse）
     *
     * @param csv      {@code owner_columns} 原始登记值
     * @param dimension 档位名
     * @param fallback 无该维度显式登记时的既有候选
     * @return 列登记（可能是关联路径）
     */
    public static List<String> candidatesFor(String csv, String dimension, List<String> fallback) {
        return candidatesFor(parse(csv), dimension, fallback);
    }

    /**
     * 裸写法（无维度前缀）的登记项，按登记顺序
     *
     * @param csv {@code owner_columns} 原始登记值
     * @return 路径列表；没有裸写法项时为空列表
     */
    public static List<String> untagged(String csv) {
        return untaggedPaths(parse(csv));
    }

    private static boolean isKnownDimension(String dimension) {
        for (DataPermissionSubjectType type : DataPermissionSubjectType.values()) {
            if (type.name().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    private static String knownDimensions() {
        List<String> names = new ArrayList<>();
        for (DataPermissionSubjectType type : DataPermissionSubjectType.values()) {
            names.add(type.name());
        }
        return String.join(" / ", names);
    }
}
