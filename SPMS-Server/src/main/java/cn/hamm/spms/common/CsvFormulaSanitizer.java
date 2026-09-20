package cn.hamm.spms.common;

import cn.hamm.spms.framework.util.CsvText;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>CSV 公式注入防护（CWE-1236）</h1>
 *
 * <p>JPA 回退仍走框架 CSV；Mapper 导出已改为 {@code EntityCsvExport}。两条路径共用 {@link CsvText}，
 * 文本字段（客户名/物料名/备注等，用户可写）若以 {@code =}{@code +}{@code -}{@code @}{@code \t}{@code \r}
 * 开头，用户用 Excel/WPS 打开导出的 CSV 时会被解释为<b>公式/外部链接执行</b>。本工具在导出前把这类
 * 单元格前缀单引号 {@code '}（Excel 的「按文本处理」标记），堵住公式执行。</p>
 *
 * <h2>为什么「遍历全部 String 字段」而不是只盯 {@code @Export}</h2>
 * <p>框架 {@code toCsvInputStream} 的字段选择是内部反射，本仓库无法在无框架源码下保证「逐字段一致」；
 * 而<b>多转义无害、漏转义有害</b>——本工具在<b>副本</b>上把「所有 String 字段」的危险前缀都转义，
 * 非导出字段转义了也不出现在 CSV 里，导出字段则必定被覆盖。宁可多转义、不可漏。</p>
 *
 * <h2>绝不改原对象</h2>
 * <p>与 {@code ExportColumnMasker} 同款「复制实体」模式：只在需要转义时才复制（{@link BeanUtils}），
 * 原实体可能仍在本事务持久化上下文里，直接 {@code field.set(entity, …)} 会把 {@code '} 写进库。</p>
 *
 * <h2>幂等性</h2>
 * <p>已前缀单引号的文本不再命中危险前缀，因此重复清洗不会增加第二个单引号。
 * 编码器仍在最终单元格处防护，覆盖字典标签、格式回退等并非 String 字段的文本来源。</p>
 *
 * @author Hamm.cn
 */
public final class CsvFormulaSanitizer {

    /** 每个类「所有 String 字段」的缓存（含继承层次），避免重复反射 */
    private static final Map<Class<?>, List<Field>> STRING_FIELDS_CACHE = new ConcurrentHashMap<>();

    private CsvFormulaSanitizer() {
    }

    /**
     * 对一批待导出的实体做公式注入防护。无任何字段需要转义时<b>原样返回入参引用</b>（不复制）。
     *
     * @param entities 待导出的实体列表（可能为 null / 空）
     * @param <E>      实体类型
     * @return 转义后的列表
     */
    public static <E> List<E> sanitize(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        List<E> out = new ArrayList<>(entities.size());
        boolean changed = false;
        for (E entity : entities) {
            E sanitized = sanitizeOne(entity);
            out.add(sanitized);
            if (sanitized != entity) {
                changed = true;
            }
        }
        return changed ? out : entities;
    }

    /**
     * 单个值转义：危险首字符前缀单引号。无需转义时原引用返回（含 null 原样返回）。
     *
     * @param value 原始值
     * @return 转义后的值（无需转义时返回原引用）
     */
    static String sanitizeValue(String value) {
        return CsvText.protectFormula(value);
    }

    private static <E> E sanitizeOne(E entity) {
        if (entity == null) {
            return null;
        }
        List<Field> fields = stringFields(entity.getClass());
        if (fields.isEmpty()) {
            return entity;
        }
        E copy = null;
        for (Field field : fields) {
            String value;
            try {
                value = (String) field.get(entity);
            } catch (IllegalAccessException ignored) {
                continue;
            }
            if (value == null) {
                continue;
            }
            String sanitized = sanitizeValue(value);
            if (sanitized == value) {
                continue;
            }
            if (copy == null) {
                copy = copy(entity);
            }
            try {
                field.set(copy, sanitized);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(
                        "CSV 公式转义失败：无法写回 " + entity.getClass().getSimpleName() + '.' + field.getName(), error);
            }
        }
        return copy == null ? entity : copy;
    }

    /** 收集一个类（含父类）的所有非静态 String 字段。 */
    private static List<Field> stringFields(Class<?> clazz) {
        return STRING_FIELDS_CACHE.computeIfAbsent(clazz, CsvFormulaSanitizer::collectStringFields);
    }

    private static List<Field> collectStringFields(Class<?> clazz) {
        List<Field> result = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType() == String.class && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    result.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <E> E copy(E entity) {
        Class<?> type = entity.getClass();
        try {
            E instance = (E) BeanUtils.instantiateClass(type);
            BeanUtils.copyProperties(entity, instance);
            return instance;
        } catch (RuntimeException error) {
            throw new IllegalStateException(
                    "CSV 公式转义需要复制实体（避免把转义写回库），但 " + type.getName()
                            + " 无法按无参构造复制：" + error.getMessage(), error);
        }
    }
}
