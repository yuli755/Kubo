package cn.hamm.spms.common.metadata;

import cn.hamm.spms.framework.util.CsvWriter;

import java.lang.reflect.Field;
import java.util.List;

/**
 * <h1>实体元数据兼容类</h1>
 *
 * @deprecated 请直接使用 {@link EntityMetadata}
 * @author Hamm.cn
 */
@Deprecated
public final class LegacyEntityMetadata {
    private LegacyEntityMetadata() {
    }

    public static String description(Class<?> type) {
        return EntityMetadata.description(type);
    }

    public static String description(Field field) {
        return EntityMetadata.description(field);
    }

    public static <T> List<CsvWriter.Column<T>> exportColumns(Class<T> type) {
        return EntityMetadata.exportColumns(type);
    }
}
