package cn.hamm.spms.framework.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * <h1>不依赖框架字典接口的枚举查找与选项投影</h1>
 *
 * <p>字段名由调用方显式提供，不再反射 SerializableLambda 猜 getter 名称。
 * 因此普通枚举也能使用；现有实体上的校验注解可以留待元数据批次单独迁移。</p>
 *
 * @author Hamm.cn
 */
public final class EnumValues {
    private EnumValues() {
    }

    /** 字段描述保留泛型，不使用字符串反射读取枚举属性。 */
    public record Column<E>(String name, Function<E, ?> reader) {
        public Column {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(reader, "reader");
        }
    }

    /** 显式声明一个选项字段。 */
    public static <E> Column<E> column(String name, Function<E, ?> reader) {
        return new Column<>(name, reader);
    }

    /** 同值时取声明顺序中的第一个；未知值交由调用方的错误边界处理。 */
    public static <E extends Enum<E>> Optional<E> find(Class<E> type, int key, ToIntFunction<E> keyReader) {
        for (E value : type.getEnumConstants()) {
            if (keyReader.applyAsInt(value) == key) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** 枚举声明顺序不变，允许字段值为 null（不能用 Map.of 代替）。 */
    @SafeVarargs
    public static <E extends Enum<E>> List<Map<String, Object>> options(Class<E> type, Column<E>... columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (E value : type.getEnumConstants()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Column<E> column : columns) {
                row.put(column.name(), column.reader().apply(value));
            }
            rows.add(row);
        }
        return rows;
    }
}
