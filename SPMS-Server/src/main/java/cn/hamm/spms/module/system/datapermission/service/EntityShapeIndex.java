package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.rule.PermissionRuleSupport;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.metamodel.ManagedType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <h1>表名 → 实体形状 的索引（只此一份）</h1>
 *
 * <p>行级权限的每个谓词最后都要落到 {@code root.get(属性名)}，于是"这个资源对应的实体上到底有没有这一列"
 * 是所有维度共同的前置问题。{@link FactoryDimensionReadiness} 已经为此写过一遍表名归一化与含父类的字段收集；
 * 若这里再抄一份，两处迟早对"什么算同一个字段"给出不同答案 —— 那正是本仓反复踩过的"第二份解释"。
 * 所以两边共用本类，解析写法本身又共用 {@link PermissionRuleSupport}（列名 / 属性名两种登记法都认）。</p>
 *
 * <p><b>只读 JPA 元模型，不查 {@code information_schema}</b>：沿用 {@code FactoryDimensionReadiness} 的理由 ——
 * 表存在不代表代码能用到它，而查库要背 MySQL / H2 的方言差异，那会让这条校验在自己的测试里假绿。</p>
 *
 * @author Hamm.cn
 */
public final class EntityShapeIndex {

    /**
     * 一个实体"能被 {@code root.get(...)} 取到"的形状
     *
     * @param type         实体类；{@code null} 表示表名没对上任何实体
     * @param attributes   标量属性名（可与值直接比较）
     * @param associations 关系属性名（名字取得到，但不能与 id 直接比较）
     */
    public record Shape(Class<?> type,
                        Set<String> attributes,
                        Set<String> associations,
                        Map<String, Set<String>> associationAttributes) {

        /**
         * 不带关联展开的旧构造：只给"这里不可能出现关联路径"的场合用
         */
        public Shape(Class<?> type, Set<String> attributes, Set<String> associations) {
            this(type, attributes, associations, Map.of());
        }

        /**
         * 登记名（列名或属性名皆可）是否能绑到标量属性上
         *
         * <p>候选写法由 {@link PermissionRuleSupport#pathCandidates(String)} 统一给出：
         * 盘点与运行期必须用同一份规则，否则会出现"这里报了、那里其实绑得上"的假警报。</p>
         *
         * @param registeredName 登记名
         * @return 能绑上返回 true
         */
        public boolean binds(String registeredName) {
            List<String> segments = PermissionRuleSupport.pathSegments(registeredName);
            if (segments.isEmpty()) {
                // 形状不合法（含超过一跳的路径）一律算绑不上：绑不上会被数出来，假绿不会
                return false;
            }
            if (segments.size() == 1) {
                for (String candidate : PermissionRuleSupport.pathCandidates(segments.get(0))) {
                    if (attributes.contains(candidate)) {
                        return true;
                    }
                }
                return false;
            }
            // 关联路径：head 命中关联名、leaf 命中该关联目标上的标量属性，才算绑得上
            for (String head : PermissionRuleSupport.pathCandidates(segments.get(0))) {
                Set<String> targets = associationAttributes.getOrDefault(head, Set.of());
                for (String leaf : PermissionRuleSupport.pathCandidates(segments.get(1))) {
                    if (targets.contains(leaf)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 名字在实体上存在、但形状是关联
         *
         * <p>这一档与"名字写错了"必须分开说：仓库归属在实体上是 {@code @ManyToOne storage}，
         * 名字没写错，缺的是关联路径支持（施工图 D1(b)）。混为一谈会把一个待建设项说成一个配置错误。</p>
         *
         * @param registeredName 登记名
         * @return 命中的关联属性名；没有则 {@code null}
         */
        public String associationNamedLike(String registeredName) {
            for (String candidate : PermissionRuleSupport.pathCandidates(registeredName)) {
                if (associations.contains(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        /**
         * 是否完全没有映射（表名没对上任何实体）
         *
         * @return 无映射返回 true
         */
        public boolean unmapped() {
            return type == null;
        }
    }

    private EntityShapeIndex() {
    }

    /**
     * 物理表名归一化：去空白、转小写、去反引号
     *
     * @param raw 原表名
     * @return 归一化后的表名；入参为 null 时返回空串
     */
    public static String normalizeTableName(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("`", "");
    }

    /**
     * 从容器里取全部受管实体类
     *
     * @param entityManagerFactory 元模型来源，可为 {@code null}
     * @return 实体类集合；拿不到元模型时返回空集合，由调用方决定"信息不足"该怎么处理
     */
    public static Collection<Class<?>> entityClasses(EntityManagerFactory entityManagerFactory) {
        if (entityManagerFactory == null) {
            return List.of();
        }
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (ManagedType<?> managedType : entityManagerFactory.getMetamodel().getManagedTypes()) {
            classes.add(managedType.getJavaType());
        }
        return classes;
    }

    /**
     * 表名（归一化）→ 实体类
     *
     * @param types 实体类集合
     * @return 索引；没有 {@code @Table} 的类不进索引
     */
    public static Map<String, Class<?>> byTable(Collection<Class<?>> types) {
        Map<String, Class<?>> index = new LinkedHashMap<>();
        for (Class<?> type : types) {
            Table table = type.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank()) {
                index.put(normalizeTableName(table.name()), type);
            }
        }
        return index;
    }

    /**
     * 解析实体的标量属性与关系属性
     *
     * @param type 实体类，可为 {@code null}
     * @return 形状；入参为 {@code null} 时两个集合都为空，由调用方按"没有映射"处理
     */
    public static Shape shapeOf(Class<?> type) {
        Set<String> attributes = new LinkedHashSet<>();
        Set<String> associations = new LinkedHashSet<>();
        Map<String, Set<String>> associationAttributes = new LinkedHashMap<>();
        if (type == null) {
            return new Shape(null, attributes, associations, associationAttributes);
        }
        for (Field field : fieldsOf(type)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (isAssociation(field)) {
                associations.add(field.getName());
                // 只再展开这一层：owner_columns 允许登记 storage.id，盘点就必须能判断 storage 上有没有 id。
                // 不做两层以上，与 JpaPathResolver 的一跳封顶保持一致 —— 多展开的那一层是假的能力，
                // 而且自引用实体（父节点指向同表）会在这里绕成死循环
                associationAttributes.put(field.getName(), attributesOf(field.getType()));
                continue;
            }
            if (isMultiValued(field.getType())) {
                // 一对多：join 会放大行数，权限条件走它没有意义，因此不给它登记可绑列
                associations.add(field.getName());
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                // @Transient 不在元模型里，root.get 取不到：把它算成「绑得上」会让盘点说假话
                continue;
            }
            attributes.add(field.getName());
        }
        return new Shape(type, attributes, associations, associationAttributes);
    }

    /**
     * 一个类上可直接绑定的标量属性名（不再向下展开关联）
     *
     * @param type 目标类，可为 null 或内建类型
     * @return 属性名集合；不可下钻时为空集合
     */
    private static Set<String> attributesOf(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        if (type == null || type.isPrimitive() || type.isArray() || type.getName().startsWith("java.")) {
            return names;
        }
        for (Field field : fieldsOf(type)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            if (isAssociation(field) || isMultiValued(field.getType())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }
            names.add(field.getName());
        }
        return names;
    }

    private static boolean isAssociation(Field field) {
        return field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(OneToMany.class)
                || field.isAnnotationPresent(ManyToMany.class);
    }

    /**
     * 集合型字段即使没打注解也不是标量列（{@code UserEntity.storageList} 就是这种）
     */
    private static boolean isMultiValued(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || type.isArray();
    }

    /**
     * 该字段是否显式映射了物理列（供"列名也算数"的判定使用）
     *
     * @param field 字段
     * @return 有 {@code @Column} 返回 true
     */
    public static boolean hasColumnAnnotation(Field field) {
        return field.isAnnotationPresent(Column.class);
    }

    /**
     * 含父类的字段清单：继承来的映射不该被漏掉
     *
     * @param type 实体类
     * @return 字段列表（父类在前无保证，仅用于存在性判断）
     */
    public static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            fields.addAll(List.of(current.getDeclaredFields()));
        }
        return fields;
    }

    /**
     * 实体上是否存在这几个字段名之一
     *
     * @param type  实体类
     * @param names 候选字段名
     * @return 命中任意一个返回 true
     */
    public static boolean hasAnyField(Class<?> type, Set<String> names) {
        for (Field field : fieldsOf(type)) {
            if (names.contains(field.getName())) {
                return true;
            }
        }
        return false;
    }
}
