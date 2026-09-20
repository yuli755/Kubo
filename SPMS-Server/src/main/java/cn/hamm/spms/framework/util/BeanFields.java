package cn.hamm.spms.framework.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <h1>实体编码与导出使用的最小反射能力</h1>
 *
 * <p>不依赖 ORM、元数据注解或第三方反射工具。ClassValue 避免按类名缓存混淆不同类加载器；
 * 不在每次读取后重置 Field 的访问标志，避免多个导出线程竞争同一缓存 Field。</p>
 *
 * @author Hamm.cn
 */
public final class BeanFields {
    private static final ClassValue<List<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    int modifiers = field.getModifiers();
                    if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                        fields.add(field);
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private BeanFields() {
    }

    /** 子类字段在前；同名父字段不去重；排除 Java static/transient，而非 JPA 的 @Transient。 */
    public static List<Field> instanceFields(Class<?> type) {
        return FIELDS.get(Objects.requireNonNull(type, "type"));
    }

    /** 直接读取字段而非 getter，不触发额外关系查询；不能读时失败，不伪装成 null。 */
    public static Object read(Object bean, Field field) {
        ensureAccessible(bean, field);
        try {
            return field.get(bean);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("无法读取字段 " + field.getName(), error);
        }
    }

    /** 自动编码的落点；写入失败必须向上传播，不能把未赋值的实体交给持久化层。 */
    public static void write(Object bean, Field field, Object value) {
        ensureAccessible(bean, field);
        try {
            field.set(bean, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("无法写入字段 " + field.getName(), error);
        }
    }

    /** 按类层级查无参 getter 注解，子类优先；保留既有“不扫描接口注解”的口径。 */
    public static <A extends Annotation> A methodAnnotation(Class<?> type, String name, Class<A> annotation) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                A found = current.getDeclaredMethod(name).getAnnotation(annotation);
                if (found != null) {
                    return found;
                }
            } catch (NoSuchMethodException ignored) {
                // 覆写 getter 可以不再标注，继续在父类找原始元数据。
            }
        }
        return null;
    }

    private static void ensureAccessible(Object bean, Field field) {
        Objects.requireNonNull(bean, "bean");
        Objects.requireNonNull(field, "field");
        if (!field.canAccess(bean) && !field.trySetAccessible()) {
            throw new IllegalStateException("字段不可访问: " + field.getDeclaringClass().getName() + '.' + field.getName());
        }
    }
}
