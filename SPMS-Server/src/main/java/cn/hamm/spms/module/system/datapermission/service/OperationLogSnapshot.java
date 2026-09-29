package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.framework.json.JsonCodec;
import cn.hamm.spms.framework.web.annotation.Desensitize;
import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Transient;

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
 * {@code before_snapshot}/{@code after_snapshot} 使用。刻意不直接 {@code JsonCodec.write(entity)}：</p>
 * <ol>
 *   <li>JPA 实体的 {@code @ManyToOne}/{@code @OneToMany}/{@code @ManyToMany} 关系一旦被序列化，
 *       会触发懒加载甚至循环引用，在审计线程上抛 {@code LazyInitializationException}；</li>
 *   <li>审计只关心「这一行的字段值变了没」，不关心它关联了谁——关系对象留 ID 即可。</li>
 * </ol>
 *
 * <p>序列化失败（异常字段类型、代理对象等）一律降级为占位串，绝不让审计把业务写动作打断。</p>
 *
 * <h2>快照是一份「离境副本」，所以敏感值一律摘要</h2>
 *
 * <p>{@code before_snapshot}/{@code after_snapshot} 会被 {@code GET /dataResource/operation-logs}
 * 原样返回，也会随备份、导出、日志采集一起离开数据库。因此这里的取舍比接口响应更保守：</p>
 * <ol>
 *   <li>{@code @Transient} 字段<b>整条不收</b>。它们不是持久化状态（是请求载体），
 *       而且本项目里恰好放着最不该留的东西——{@code UserEntity.oldPassword}（用户手输的原文密码）、
 *       {@code UserEntity.code}（邮箱验证码）。审计要回答的是「这一行改了什么」，不是「请求带了什么」；</li>
 *   <li>{@code @SensitiveField} / {@code @Desensitize} / {@code @JsonIgnore} /
 *       {@code @JsonProperty(access = WRITE_ONLY)} 字段收<b>摘要</b>而不是原值
 *       （{@link MaskedValueDigest}）。这四类注解各自表达的正是「不要把这个值明文送出去」，
 *       审计快照就是一次送出去。</li>
 * </ol>
 *
 * <p>为什么用摘要而不是常量占位：常量会让「改价前」「改价后」两份快照完全一样，
 * 字段变更 Diff 于是把真实发生过的改动报成「没有变动」——假阴性比不报告更坏。
 * 摘要保留了「变没变」，同时不留原值。</p>
 *
 * <p>为什么不复用 {@code ColumnMaskingService.maskFieldValue}：那个口径取决于当前请求者的角色策略，
 * 于是「谁改的」会决定库里存不存明文（管理员改一次就永久落下明文价格）。
 * 审计需要与观看者无关的稳定口径，详见 {@link MaskedValueDigest} 的说明。</p>
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
            return JsonCodec.write(scalarFields(entity));
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
     * 收集实体（含父类）里的标量字段，跳过静态、合成、瞬态、集合与关系字段；
     * 敏感字段收摘要而非原值（见类注释）。
     */
    private static Map<String, Object> scalarFields(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        Class<?> current = entity.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (shouldSkip(field)) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    map.putIfAbsent(field.getName(),
                            isNeverPlaintext(field) ? MaskedValueDigest.ofValue(value) : value);
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
     *
     * <p>{@code @Transient} 在这里被丢掉是<b>安全</b>考虑而不只是「它不入库」：
     * 本项目把用户手输的原文密码、邮箱验证码放在瞬态字段上做请求载体，
     * 快照按反射收字段，不看这些语义就会把它们写进审计表。</p>
     */
    private static boolean shouldSkip(Field field) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return true;
        }
        if (field.isAnnotationPresent(Transient.class)) {
            return true;
        }
        Class<?> type = field.getType();
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            return true;
        }
        return !isScalar(type);
    }

    /**
     * 判定一个字段是否「绝不能明文进快照」。
     *
     * <p>四类注解，语义都是「这个值不要原样送出去」：</p>
     * <ul>
     *   <li>{@code @SensitiveField} —— 策略驱动的列级脱敏（价格、身份证、邮箱、手机号）；</li>
     *   <li>{@code @Desensitize} —— 框架级固定脱敏（中文姓名、身份证、自定义掩码）；</li>
     *   <li>{@code @JsonIgnore} —— 双向都不序列化（密码盐）；</li>
     *   <li>{@code @JsonProperty(access = WRITE_ONLY)} —— 只进不出（登录密码）。
     *       {@code READ_ONLY} 不算：那是「只出不进」，本来就是给响应看的。</li>
     * </ul>
     *
     * <p>注意这四类注解只作用于 Jackson 的 HTTP 序列化路径，对反射读字段毫无约束——
     * 审计快照走的正是反射，所以必须在这里显式认一遍，否则「接口不返回」会被误当成「哪儿都不会出现」。</p>
     */
    private static boolean isNeverPlaintext(Field field) {
        if (field.isAnnotationPresent(SensitiveField.class) || field.isAnnotationPresent(Desensitize.class)
                || field.isAnnotationPresent(JsonIgnore.class)) {
            return true;
        }
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        return property != null && property.access() == JsonProperty.Access.WRITE_ONLY;
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
