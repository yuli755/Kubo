package cn.hamm.spms.framework.curd.base;

import cn.hamm.spms.core.ReflectUtil;
import cn.hamm.spms.framework.curd.annotation.InputOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>实体关系字段的元数据（J3）</h1>
 *
 * <p>JPA 时代\"哪个字段是关系\"由 {@code @ManyToOne}/{@code @ManyToMany} 回答；MyBatis-Plus 不认识它们，
 * 而且把对象字段当成了列（{@code storage} → 列 {@code storage}，真列是 {@code storage_id}）——
 * 那不是报错，是<b>每条查询都在查一个不存在的列</b>。所以关系字段统一标
 * {@code @TableField(exist = false)}，映射交给标量外键字段（{@code storageId} → {@code storage_id}）。</p>
 *
 * <p>本类只做两件事，都是\"按字段类型判断\"（不读 JPA 注解，J3 之后实体上没有它们）：</p>
 * <ol>
 *   <li>找出关系字段：字段类型是 {@link CurdEntity} 子类 = 多对一；字段类型是
 *       {@code Collection<CurdEntity 子类>} = 多对多；</li>
 *   <li>把关系对象里的 ID 同步到标量外键字段（写路径的必经点，见 {@link ICurdRepository#save(CurdEntity)}）。</li>
 * </ol>
 *
 * <p><b>为什么读路径也需要它</b>：业务查询走 MyBatis-Plus 的内置语句（{@code selectById}/{@code selectList}/
 * {@code selectPage}），它们只映射\"列\"，不会替你 join 关联表 —— 于是 {@code row.material.code}
 * 这类字段会整个消失，而前端有 17 处按嵌套路径渲染。对象图由
 * {@link RelationHydrator} 在引擎出口按批补全（一次查询一个关系，不是一行一次）。</p>
 *
 * @author Arena agent
 */
public final class RelationMapping {
    /**
     * 每个实体的关系字段（多对一/多对多）缓存：反射遍历一次，之后只查表
     */
    private static final Map<Class<?>, List<Field>> RELATION_CACHE = new ConcurrentHashMap<>();

    /**
     * 每个实体的标量外键字段（{@code materialId}）缓存
     */
    private static final Map<Class<?>, Map<String, Field>> FOREIGN_KEY_CACHE = new ConcurrentHashMap<>();

    private RelationMapping() {
    }

    /**
     * 多对一关系字段（字段类型是 {@link CurdEntity} 子类）
     *
     * @param entityClass 实体类
     * @return 关系字段（没有则空列表）
     */
    public static List<Field> manyToOneFields(Class<?> entityClass) {
        return relationsOf(entityClass).stream().filter(field -> !isCollection(field)).toList();
    }

    /**
     * 多对多关系字段（字段类型是 {@code Collection<CurdEntity 子类>}）
     *
     * @param entityClass 实体类
     * @return 关系字段（没有则空列表）
     */
    public static List<Field> manyToManyFields(Class<?> entityClass) {
        return relationsOf(entityClass).stream().filter(RelationMapping::isCollection).toList();
    }

    /**
     * 标量外键字段（{@code storage} → {@code storageId}）
     *
     * @param entityClass   实体类
     * @param relationField 关系字段名
     * @return 外键字段；没有（实体没补桥接字段）返回 {@code null}
     */
    public static Field foreignKeyField(Class<?> entityClass, String relationField) {
        return FOREIGN_KEY_CACHE
                .computeIfAbsent(entityClass, clazz -> {
                    Map<String, Field> map = new ConcurrentHashMap<>();
                    for (Field field : ReflectUtil.getFieldList(clazz)) {
                        if (Long.class.equals(field.getType()) && field.getName().endsWith("Id")) {
                            map.put(field.getName(), field);
                        }
                    }
                    return map;
                })
                .get(relationField + "Id");
    }

    /**
     * 把关系对象里的 ID 同步到标量外键字段
     *
     * @param entity 实体
     * @apiNote 语义刻意保守：关系对象为 {@code null} 时<b>不动</b>外键字段
     * （引擎的\"null = 不改这一列\"就是靠这个；把 null 当成\"清空外键\"会让局部更新顺手删掉归属）。
     * 空对象（只有 id 之外没有值）按\"没有 ID\"处理 —— 与旧 JPA 路径一致。
     */
    public static void syncForeignKeys(Object entity) {
        if (entity == null) {
            return;
        }
        for (Field relation : manyToOneFields(entity.getClass())) {
            Field foreignKey = foreignKeyField(entity.getClass(), relation.getName());
            if (foreignKey == null) {
                continue;
            }
            Object related = ReflectUtil.getFieldValue(entity, relation);
            if (!(related instanceof CurdEntity<?> relatedEntity)) {
                continue;
            }
            ReflectUtil.setFieldValue(entity, foreignKey, relatedEntity.getId());
        }
    }

    /**
     * 多对多关系字段的目标实体类型（{@code Set<RoleEntity>} → {@code RoleEntity}）
     *
     * @param field 关系字段
     * @return 目标实体类；解析不出来返回 {@code null}
     */
    public static Class<?> targetEntityOf(Field field) {
        if (!isCollection(field)) {
            return field.getType();
        }
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            return null;
        }
        Type argument = parameterized.getActualTypeArguments()[0];
        if (argument instanceof Class<?> clazz) {
            return clazz;
        }
        if (argument instanceof ParameterizedType nested && nested.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    /**
     * 关系字段集合（多对一 + 多对多）
     *
     * @param entityClass 实体类
     * @return 关系字段
     */
    public static List<Field> relationsOf(Class<?> entityClass) {
        return RELATION_CACHE.computeIfAbsent(entityClass, clazz -> {
            List<Field> fields = new ArrayList<>();
            for (Field field : ReflectUtil.getFieldList(clazz)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                // 只作入参的关系对象（@InputOnly）：没有列，也不该有外键字段 —— 显式跳过，
                // 免得"同步失败被静默吞掉"和"这里故意不落库"在代码里长得一样
                if (field.isAnnotationPresent(InputOnly.class)) {
                    continue;
                }
                if (CurdEntity.class.isAssignableFrom(field.getType())) {
                    fields.add(field);
                    continue;
                }
                if (isCollection(field)) {
                    Class<?> target = targetEntityOf(field);
                    if (Objects.nonNull(target) && CurdEntity.class.isAssignableFrom(target)) {
                        fields.add(field);
                    }
                }
            }
            return List.copyOf(fields);
        });
    }

    /**
     * 字段是否是集合（多对多形态）
     *
     * @param field 字段
     * @return 是集合返回 true
     */
    private static boolean isCollection(Field field) {
        return Collection.class.isAssignableFrom(field.getType());
    }
}
