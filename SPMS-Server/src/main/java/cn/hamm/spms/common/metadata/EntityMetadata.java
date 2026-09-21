package cn.hamm.spms.common.metadata;

import cn.hamm.spms.framework.util.BeanFields;
import cn.hamm.spms.framework.util.CsvWriter;
import cn.hamm.spms.framework.web.annotation.Description;
import cn.hamm.spms.framework.web.annotation.Dictionary;
import cn.hamm.spms.framework.web.annotation.Export;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;

/**
 * <h1>实体元数据解析器与导出列配置提取</h1>
 *
 * <p>提供自持实体描述提取、字段描述读取与 CSV 导出列配置构建。
 * 完全基于 {@link Description}、{@link Export}、{@link Dictionary} 注解与自持反射工具。</p>
 *
 * @author Hamm.cn
 */
public final class EntityMetadata {
    private EntityMetadata() {
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
     * 列选择与排序提取：必须有 public getXxx；getter 优先决定选择/排序/移除；
     * 格式取字段 @Export（getter-only 列按 RAW 输出），sort 降序、同序稳定。
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
