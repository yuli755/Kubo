package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.airpower.core.Json;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h1>操作审计快照的安全序列化</h1>
 *
 * <p>把实体在某个时点的<b>标量字段</b>快照成 JSON 文本，供 {@code operation_log} 的
 * {@code before_snapshot}/{@code after_snapshot} 使用。刻意不直接 {@code Json.toString(entity)}：</p>
 * <ol>
 *   <li>JPA 实体的 {@code @ManyToOne}/{@code @OneToMany}/{@code @ManyToMany} 关系一旦被序列化，
 *       会触发懒加载甚至循环引用，在审计线程上抛 {@code LazyInitializationException}；</li>
 *   <li>审计只关心「这一行的字段值变了没」，不关心它关联了谁——关系对象留 ID 即可。</li>
 * </ol>
 *
 * <p>序列化失败（异常字段类型、代理对象等）一律降级为占位串，绝不让审计把业务写动作打断。</p>
 *
 * @author Hamm.cn
 */
public final class OperationLogSnapshot {

    private OperationLogSnapshot() {
    }

    /**
     * 把一个实体快照成 JSON 文本；无法序列化时返回占位串，不抛。
     *
     * @param entity 实体（可为 null）
     * @return JSON 快照；{@code entity == null} 时返回 null
     */
    public static String of(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            return Json.toString(scalarFields(entity));
        }
        catch (RuntimeException ex) {
            return "{\"snapshot-error\":\"" + safeClassName(entity) + "\"}";
        }
    }

    /**
     * 取一个实体的人类可读摘要（编码/名称），用于管理页一眼认出是哪条记录。
     *
     * <p>依次尝试 {@code getCode()} / {@code getName()}，都没有就退回实体类名 + ID。</p>
     *
     * @param entity 实体（可为 null）
     * @return 摘要串；{@code entity == null} 时返回 null
     */
    public static String labelOf(Object entity) {
        if (entity == null) {
            return null;
        }
        for (String getter : new String[]{"getCode", "getName"}) {
            Object value = invokeGetter(entity, getter);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        Object id = invokeGetter(entity, "getId");
        return id == null ? safeClassName(entity) : safeClassName(entity) + "#" + id;
    }

    /**
     * 收集实体（含父类）里的标量字段，跳过静态、瞬态、集合与关系字段。
     */
    private static Map<String, Object> scalarFields(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            // 越过 airpower 框架基类：它的内部字段（如 proxy 状态）不该进快照
            if (current.getName().startsWith("cn.hamm.airpower.")) {
                break;
            }
            for (Field field : current.getDeclaredFields()) {
                if (shouldSkip(field)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    map.putIfAbsent(field.getName(), field.get(entity));
                }
                catch (IllegalAccessException ignored) {
                    // 读不到就跳过该字段，不让一个字段坏掉整份快照
                }
            }
            current = current.getSuperclass();
        }
        return map;
    }

    /**
     * 判定一个字段是否该跳过：静态、合成、瞬态、集合/Map、以及一切实体/关系类型。
     */
    private static boolean shouldSkip(Field field) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return true;
        }
        Class<?> type = field.getType();
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        return !isScalar(type);
    }

    /**
     * 标量类型 = 基本类型/包装、String、枚举、BigDecimal、时间等；其余（实体/关系）一律不算。
     */
    private static boolean isScalar(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()) {
            return true;
        }
        if (type == String.class || Number.class.isAssignableFrom(type) || Boolean.class == type
                || Character.class == type) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.time.") || name.startsWith("java.math.")
                || name.startsWith("java.util.Date");
    }

    private static Object invokeGetter(Object entity, String methodName) {
        try {
            Method method = entity.getClass().getMethod(methodName);
            return method.invoke(entity);
        }
        catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String safeClassName(Object entity) {
        return entity.getClass().getSimpleName();
    }
}
