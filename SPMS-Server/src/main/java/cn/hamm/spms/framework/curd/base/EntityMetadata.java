package cn.hamm.spms.framework.curd.base;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>实体元数据：表名 / 列名 / 实体类集合（J3，只认 MyBatis-Plus）</h1>
 *
 * <p>JPA 时代这些问题的答案是注解：{@code @Table(name)}、{@code @Column(name)}、以及
 * {@code EntityManagerFactory.getMetamodel().getManagedTypes()}（"有哪些实体"）。J3 把实体注解换成
 * MyBatis-Plus 之后，运行时和门禁都必须换口径 —— 而且只能有<b>一份</b>口径，否则
 * "盘点说绑不上、运行期其实绑得上"这类第二解释会立刻回来。本类就是那一份：</p>
 *
 * <ul>
 *   <li>{@link #tableName(Class)}：{@code @TableName} 的值；缺省按 MP 的规则（简单类名驼峰转下划线）；</li>
 *   <li>{@link #columnName(Field)}：{@code @TableField("x")} 的值；缺省按 MP 的规则（字段名驼峰转下划线）；</li>
 *   <li>{@link #isColumn(Field)}：MP 眼里它是不是列 —— 静态/合成字段不是，标了
 *       {@code @TableField(exist = false)} 的不是（关系对象、查询专用字段），集合不是，
 *       指向另一个实体的对象也不是；</li>
 *   <li>{@link #entityClasses()}：容器里"哪些类是实体" —— 扫 {@code @TableName}，不再问 JPA 元模型
 *       （注解删掉之后元模型是空的，继续问它会让所有盘点全量报"未映射"）。</li>
 * </ul>
 *
 * <p>扫描结果缓存：类集合在一次运行里不会变，而盘点/形状索引会被反复调用。</p>
 *
 * @author Arena agent
 */
public final class EntityMetadata {
    /**
     * 实体类扫描的根包（本仓实体都住在 module 下）
     */
    private static final String ENTITY_SCAN_PACKAGE = "cn.hamm.spms.module";

    /**
     * 实体类缓存（扫描一次）
     */
    private static volatile List<Class<?>> entityClassesCache;

    /**
     * 类 → 列字段（含父类，父类在前）
     */
    private static final Map<Class<?>, List<Field>> COLUMN_FIELDS_CACHE = new ConcurrentHashMap<>();

    private EntityMetadata() {
    }

    /**
     * 表名（MP 口径）
     *
     * @param entityClass 实体类
     * @return 表名；{@code null} 入参返回空串
     */
    public static String tableName(@Nullable Class<?> entityClass) {
        if (Objects.isNull(entityClass)) {
            return "";
        }
        TableName annotation = entityClass.getAnnotation(TableName.class);
        if (Objects.nonNull(annotation) && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return camelToUnderline(entityClass.getSimpleName());
    }

    /**
     * 列名（MP 口径：显式 {@code @TableField("x")} 优先，否则字段名转下划线）
     *
     * @param field 字段
     * @return 列名
     */
    public static String columnName(@NotNull Field field) {
        TableField annotation = field.getAnnotation(TableField.class);
        if (Objects.nonNull(annotation) && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return camelToUnderline(field.getName());
    }

    /**
     * 这个字段在 MP 眼里是不是列
     *
     * @param field 字段
     * @return 是列返回 true
     */
    public static boolean isColumn(@NotNull Field field) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || Modifier.isTransient(field.getModifiers())) {
            return false;
        }
        TableField annotation = field.getAnnotation(TableField.class);
        if (Objects.nonNull(annotation) && !annotation.exist()) {
            return false;
        }
        Class<?> type = field.getType();
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            // 集合关系：不是本表列（多对多的中间表由 RelationHydrator 负责）
            return false;
        }
        // 关系对象（多对一）一律已经标了 exist = false；这里再挡一层，避免"忘了标"时把对象当列
        return !CurdEntity.class.isAssignableFrom(type);
    }

    /**
     * 实体的列字段（含父类；父类在前，与 MP 收集字段的顺序一致）
     *
     * @param entityClass 实体类
     * @return 列字段
     */
    public static List<Field> columnFields(@Nullable Class<?> entityClass) {
        if (Objects.isNull(entityClass)) {
            return List.of();
        }
        return COLUMN_FIELDS_CACHE.computeIfAbsent(entityClass, clazz -> {
            List<Field> fields = new ArrayList<>();
            for (Field field : fieldsOf(clazz)) {
                if (isColumn(field)) {
                    fields.add(field);
                }
            }
            return List.copyOf(fields);
        });
    }

    /**
     * 实体的列名集合
     *
     * @param entityClass 实体类
     * @return 列名
     */
    public static Set<String> columns(@Nullable Class<?> entityClass) {
        Set<String> columns = new LinkedHashSet<>();
        for (Field field : columnFields(entityClass)) {
            columns.add(columnName(field));
        }
        return columns;
    }

    /**
     * 含父类的字段清单（父类在前）
     *
     * @param type 类
     * @return 字段
     */
    public static List<Field> fieldsOf(@Nullable Class<?> type) {
        if (Objects.isNull(type)) {
            return List.of();
        }
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> current = type; Objects.nonNull(current) && !Object.class.equals(current); current = current.getSuperclass()) {
            chain.add(0, current);
        }
        List<Field> fields = new ArrayList<>();
        for (Class<?> current : chain) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
        }
        return fields;
    }

    /**
     * 容器里的实体类集合（扫 {@code @TableName}，不依赖 JPA 元模型）
     *
     * @return 实体类；扫描失败返回空集合（由调用方决定"信息不足"怎么处理）
     */
    public static Collection<Class<?>> entityClasses() {
        List<Class<?>> cached = entityClassesCache;
        if (Objects.nonNull(cached)) {
            return cached;
        }
        synchronized (EntityMetadata.class) {
            if (Objects.nonNull(entityClassesCache)) {
                return entityClassesCache;
            }
            List<Class<?>> classes = new ArrayList<>();
            try {
                ClassPathScanningCandidateComponentProvider provider =
                        new ClassPathScanningCandidateComponentProvider(false);
                provider.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
                for (BeanDefinition definition : provider.findCandidateComponents(ENTITY_SCAN_PACKAGE)) {
                    String className = definition.getBeanClassName();
                    if (Objects.isNull(className)) {
                        continue;
                    }
                    classes.add(Class.forName(className, false, EntityMetadata.class.getClassLoader()));
                }
            }
            catch (RuntimeException | ClassNotFoundException | LinkageError ignored) {
                // 扫不到就是空集合：调用方按"没有实体信息"处理，不要在这里猜
            }
            entityClassesCache = List.copyOf(classes);
            return entityClassesCache;
        }
    }

    /**
     * 表名（归一化）→ 实体类
     *
     * @param types 实体类集合
     * @return 索引
     */
    public static Map<String, Class<?>> byTable(@NotNull Collection<Class<?>> types) {
        Map<String, Class<?>> index = new java.util.LinkedHashMap<>();
        for (Class<?> type : types) {
            String table = tableName(type);
            if (!table.isBlank()) {
                index.put(table.toLowerCase(Locale.ROOT), type);
            }
        }
        return index;
    }

    /**
     * 驼峰转下划线（与 MP 的列名规则一致）
     *
     * @param value 原串
     * @return 下划线形式
     */
    public static String camelToUnderline(@NotNull String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }
}
