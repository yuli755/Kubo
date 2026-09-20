package cn.hamm.spms.framework.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * <h1>只依赖 JDK 的 CSV 编码器</h1>
 *
 * <p>列选择由上层提供，编码器不猜注解或遍历额外字段。保留 TEXT 的制表符、中文布尔值、
 * 缺值横杠、毫秒时间戳等既有口径；UTF-8 编码且不加 BOM、不加末尾空行。</p>
 *
 * @author Hamm.cn
 */
public final class CsvWriter {
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** RAW 只用于旧元数据的 getter-only 列：旧实现对这种列不套字段格式。 */
    public enum Kind {
        TEXT, NUMBER, BOOLEAN, DATETIME, DICTIONARY, RAW
    }

    /** 元数据与实际取值解耦；字典标签查不到时返回 null，由编码器按文本安全回退。 */
    public record Column<T>(String header, Kind kind, Function<? super T, ?> reader, IntFunction<String> dictionary) {
        public Column {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reader, "reader");
        }

        public Column(String header, Kind kind, Function<? super T, ?> reader) {
            this(header, kind, reader, null);
        }
    }

    private CsvWriter() {
    }

    /**
     * 将已完成权限过滤/脱敏的行编码为流，不修改行对象。
     * 空列配置仍输出空表，不擅自导出“所有字段”；读取字段失败不产出半份成功文件。
     */
    public static <T> InputStream toInputStream(Iterable<T> rows, List<Column<T>> columns) {
        Objects.requireNonNull(rows, "rows");
        List<Column<T>> schema = List.copyOf(columns);
        ZoneId zone = ZoneId.systemDefault();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < schema.size(); i++) {
                if (i > 0) {
                    writer.write(',');
                }
                writer.write(CsvText.quote(CsvText.protectFormula(schema.get(i).header())));
            }
            for (T row : rows) {
                Objects.requireNonNull(row, "CSV 行不能为空");
                writer.write('\n');
                for (int i = 0; i < schema.size(); i++) {
                    if (i > 0) {
                        writer.write(',');
                    }
                    Column<T> column = schema.get(i);
                    // 取值异常不进入格式回退：不能把反射失败或权限错误伪装成普通文本。
                    Object raw = column.reader().apply(row);
                    writer.write(CsvText.quote(format(raw, column, zone)));
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException("CSV 编码失败", error);
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static String format(Object raw, Column<?> column, ZoneId zone) {
        if (raw == null || raw.toString().isBlank()) {
            return column.kind() == Kind.TEXT ? "\t-" : "-";
        }
        String value = raw.toString();
        return switch (column.kind()) {
            case TEXT -> "\t" + CsvText.protectFormula(value);
            case NUMBER -> raw instanceof Number ? number(raw) : CsvText.protectFormula(value);
            case BOOLEAN -> raw instanceof Boolean flag ? (flag ? "是" : "否") : CsvText.protectFormula(value);
            case DATETIME -> {
                long timestamp;
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException error) {
                    yield CsvText.protectFormula(value);
                }
                yield "\t" + DATETIME.format(Instant.ofEpochMilli(timestamp).atZone(zone));
            }
            case DICTIONARY -> {
                if (column.dictionary() == null) {
                    yield CsvText.protectFormula(value);
                }
                int key;
                try {
                    key = Integer.parseInt(value);
                } catch (NumberFormatException error) {
                    yield CsvText.protectFormula(value);
                }
                // 只允许“输入不是数字”回退；标签回调自身的异常必须传播，不能被误当成坏 key。
                String label = column.dictionary().apply(key);
                yield CsvText.protectFormula(label == null ? value : label);
            }
            case RAW -> raw instanceof Number ? value : CsvText.protectFormula(value);
        };
    }

    private static String number(Object value) {
        if (value instanceof Double number) {
            return Double.isFinite(number) ? BigDecimal.valueOf(number).toPlainString() : CsvText.protectFormula(number.toString());
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) ? BigDecimal.valueOf(number).toPlainString() : CsvText.protectFormula(number.toString());
        }
        if (value instanceof Long number) {
            return BigDecimal.valueOf(number).toPlainString();
        }
        return value.toString();
    }
}
