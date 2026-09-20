package cn.hamm.spms.common.metadata;

import cn.hamm.airpower.core.annotation.Description;
import cn.hamm.airpower.core.annotation.Dictionary;
import cn.hamm.airpower.core.annotation.Export;
import cn.hamm.spms.framework.util.BeanFields;
import cn.hamm.spms.framework.util.CsvWriter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * <h1>退场期间仍需保留的实体元数据边界</h1>
 *
 * <p>只读取旧注解，不调用旧反射、字典或 CSV 工具。三种注解依赖在此明确记账，
 * B3 换注解时改这一处；不得把它放进无第三方依赖的 framework 伪装成已清零。</p>
 *
 * @author Hamm.cn
 */
public final class LegacyEntityMetadata {
    private LegacyEntityMetadata() {
    }

    /** 类描述（Description 可继承），没有注解才回退类名，空描述仍保留。 */
    public static String description(Class<?> type) {
        Description description = type.getAnnotation(Description.class);
        return description == null ? type.getSimpleName() : description.value();
    }

    /** 字段描述只读字段注解；不使用 getter 的文案代替字段文案。 */
    public static String description(Field field) {
        Description description = field.getAnnotation(Description.class);
        return description == null ? field.getName() : description.value();
    }

    /**
     * 保留 6.3.1 的列选择：必须有 public getXxx；getter 优先决定选择/排序/移除；
     * 格式依旧取字段 @Export（getter-only 列按 RAW 输出），sort 按实际实现降序、同序稳定。
     */
    public static <T> List<CsvWriter.Column<T>> exportColumns(Class<T> type) {
        List<OrderedColumn<T>> columns = new ArrayList<>();
        for (Field field : BeanFields.instanceFields(type)) {
            String getter = "get" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            try {
                type.getMethod(getter);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            Export onField = field.getAnnotation(Export.class);
            Export selection = BeanFields.methodAnnotation(type, getter, Export.class);
            if (selection == null) {
                selection = onField;
            }
            if (selection == null || selection.remove()) {
                continue;
            }
            CsvWriter.Kind kind = onField == null ? CsvWriter.Kind.RAW : CsvWriter.Kind.valueOf(onField.value().name());
            CsvWriter.Column<T> column = new CsvWriter.Column<>(description(field), kind,
                    row -> BeanFields.read(row, field), dictionary(field));
            columns.add(new OrderedColumn<>(selection.sort(), column));
        }
        columns.sort(Comparator.comparingInt((OrderedColumn<T> column) -> column.order()).reversed());
        return columns.stream().map(OrderedColumn::column).toList();
    }

    private static IntFunction<String> dictionary(Field field) {
        Dictionary annotation = field.getAnnotation(Dictionary.class);
        if (annotation == null) {
            return null;
        }
        // 类型仍由旧 @Dictionary 约束。没有把 IDictionary 换名包装成自有接口。
        var values = annotation.value().getEnumConstants();
        return key -> {
            if (values != null) {
                for (var value : values) {
                    if (value.getKey() == key) {
                        return value.getLabel();
                    }
                }
            }
            return null;
        };
    }

    private record OrderedColumn<T>(int order, CsvWriter.Column<T> column) {
    }
}
