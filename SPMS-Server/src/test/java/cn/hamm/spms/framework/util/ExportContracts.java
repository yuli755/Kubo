package cn.hamm.spms.framework.util;

import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

import static cn.hamm.spms.framework.util.UtilityContracts.check;
import static cn.hamm.spms.framework.util.UtilityContracts.equal;
import static cn.hamm.spms.framework.util.UtilityContracts.fails;

/**
 * <h1>B2b CSV 与反射契约，不需要 JPA/Spring/第三方注解或测试库</h1>
 *
 * @author Hamm.cn
 */
final class ExportContracts {
    private ExportContracts() {
    }

    static List<UtilityContracts.Case> cases() {
        return List.of(
                new UtilityContracts.Case("csv/formula-triggers-and-idempotence", () -> {
                    for (String value : List.of("=SUM(1)", "+cmd", "-cmd", "@cmd", "\tcmd", "\rcmd", "\ncmd", "  =cmd", "\uFEFF=cmd")) {
                        String protectedValue = CsvText.protectFormula(value);
                        equal("'" + value, protectedValue);
                        equal(protectedValue, CsvText.protectFormula(protectedValue));
                    }
                }),
                new UtilityContracts.Case("csv/safe-text-identity", () -> {
                    for (String value : new String[]{null, "", "客户", "  名称", "'已保护", " x=1"}) {
                        check(value == CsvText.protectFormula(value));
                    }
                }),
                new UtilityContracts.Case("csv/text-tab-and-leading-zeros", () -> equal("列\n\t001234567890123456789", render(CsvWriter.Kind.TEXT, "001234567890123456789"))),
                new UtilityContracts.Case("csv/utf8-no-bom-no-final-newline", () -> {
                    String csv = render(CsvWriter.Kind.TEXT, "中文客户");
                    equal("列\n\t中文客户", csv);
                    check(!csv.startsWith("\uFEFF") && !csv.endsWith("\n"));
                }),
                new UtilityContracts.Case("csv/quotes-comma-newline-roundtrip", () -> {
                    String value = "客户,\"甲\"\r\n第二行";
                    String csv = render(CsvWriter.Kind.TEXT, value);
                    equal("列\n\"\t客户,\"\"甲\"\"\r\n第二行\"", csv);
                    equal(List.of(List.of("列"), List.of("\t" + value)), parse(csv));
                }),
                new UtilityContracts.Case("csv/quoted-formula-is-still-protected", () -> {
                    String value = "=HYPERLINK(\"https://invalid.test\",\"打开\")";
                    equal("\t'" + value, parse(render(CsvWriter.Kind.TEXT, value)).get(1).get(0));
                }),
                new UtilityContracts.Case("csv/column-boundary-cannot-inject-formula", () -> {
                    String value = "安全,=cmd\n@cmd";
                    List<List<String>> rows = parse(render(CsvWriter.Kind.TEXT, value));
                    equal(2, rows.size());
                    equal(1, rows.get(1).size());
                    equal("\t" + value, rows.get(1).get(0));
                }),
                new UtilityContracts.Case("csv/headers-are-quoted-and-protected", () -> {
                    var column = new CsvWriter.Column<Cell>("=标题,一", CsvWriter.Kind.TEXT, Cell::value);
                    equal("\"'=标题,一\"\n\tx", read(CsvWriter.toInputStream(List.of(new Cell("x")), List.of(column))));
                }),
                new UtilityContracts.Case("csv/number-keeps-native-negative", () -> {
                    equal("列\n-12.5", render(CsvWriter.Kind.NUMBER, -12.5));
                    equal("列\n0.000000010", render(CsvWriter.Kind.NUMBER, 1e-8));
                    equal("列\n9223372036854775807", render(CsvWriter.Kind.NUMBER, Long.MAX_VALUE));
                }),
                new UtilityContracts.Case("csv/string-in-number-column-is-not-trusted", () -> {
                    equal("列\n'-1+cmd", render(CsvWriter.Kind.NUMBER, "-1+cmd"));
                    equal("列\n'@cmd", render(CsvWriter.Kind.NUMBER, "@cmd"));
                }),
                new UtilityContracts.Case("csv/non-finite-number-fallback", () -> equal("列\nNaN", render(CsvWriter.Kind.NUMBER, Double.NaN))),
                new UtilityContracts.Case("csv/boolean-and-fallback", () -> {
                    equal("列\n是", render(CsvWriter.Kind.BOOLEAN, true));
                    equal("列\n否", render(CsvWriter.Kind.BOOLEAN, false));
                    equal("列\n'=cmd", render(CsvWriter.Kind.BOOLEAN, "=cmd"));
                }),
                new UtilityContracts.Case("csv/null-and-blank-placeholders", () -> {
                    for (CsvWriter.Kind kind : CsvWriter.Kind.values()) {
                        String expected = kind == CsvWriter.Kind.TEXT ? "列\n\t-" : "列\n-";
                        equal(expected, render(kind, null));
                        equal(expected, render(kind, "  "));
                    }
                }),
                new UtilityContracts.Case("csv/datetime-milliseconds-and-zone", () -> {
                    TimeZone previous = TimeZone.getDefault();
                    try {
                        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
                        equal("列\n\t1970-01-01 08:00:00", render(CsvWriter.Kind.DATETIME, 0L));
                        equal("列\n\t1970-01-01 08:00:01", render(CsvWriter.Kind.DATETIME, 1000L));
                    } finally {
                        TimeZone.setDefault(previous);
                    }
                }),
                new UtilityContracts.Case("csv/datetime-invalid-value-protected", () -> equal("列\n'=cmd", render(CsvWriter.Kind.DATETIME, "=cmd"))),
                new UtilityContracts.Case("csv/dictionary-label-and-unknown-value", () -> {
                    equal("列\n已完成", render(CsvWriter.Kind.DICTIONARY, 1, key -> key == 1 ? "已完成" : null));
                    equal("列\n999", render(CsvWriter.Kind.DICTIONARY, 999, ignored -> null));
                    equal("列\n1", render(CsvWriter.Kind.DICTIONARY, 1));
                }),
                new UtilityContracts.Case("csv/dictionary-derived-text-protected", () -> {
                    equal("列\n'=cmd", render(CsvWriter.Kind.DICTIONARY, 1, ignored -> "=cmd"));
                    equal("列\n'=cmd", render(CsvWriter.Kind.DICTIONARY, "=cmd", ignored -> "无效"));
                }),
                new UtilityContracts.Case("csv/raw-getter-only-format", () -> {
                    equal("列\n0012", render(CsvWriter.Kind.RAW, "0012"));
                    equal("列\n'=cmd", render(CsvWriter.Kind.RAW, "=cmd"));
                    equal("列\n-12", render(CsvWriter.Kind.RAW, -12));
                }),
                new UtilityContracts.Case("csv/order-and-no-unselected-secret", () -> {
                    Map<String, String> row = Map.of("code", "007", "name", "客户", "secret", "NEVER_EXPORT_ME");
                    List<CsvWriter.Column<Map<String, String>>> columns = List.of(
                            new CsvWriter.Column<>("名称", CsvWriter.Kind.TEXT, value -> value.get("name")),
                            new CsvWriter.Column<>("编码", CsvWriter.Kind.TEXT, value -> value.get("code")));
                    String csv = read(CsvWriter.toInputStream(List.of(row), columns));
                    equal("名称,编码\n\t客户,\t007", csv);
                    check(!csv.contains("NEVER_EXPORT_ME"));
                }),
                new UtilityContracts.Case("csv/empty-data-keeps-header", () -> {
                    List<CsvWriter.Column<Cell>> columns = List.of(new CsvWriter.Column<>("名称", CsvWriter.Kind.TEXT, Cell::value));
                    equal("名称", read(CsvWriter.toInputStream(List.of(), columns)));
                }),
                new UtilityContracts.Case("csv/empty-schema-does-not-export-all-fields", () -> {
                    equal("", read(CsvWriter.toInputStream(List.of(), List.of())));
                    equal("\n\n", read(CsvWriter.toInputStream(List.of(new Cell("secret"), new Cell("secret")), List.of())));
                }),
                new UtilityContracts.Case("csv/does-not-mutate-source", () -> {
                    Bean row = new Bean();
                    row.code = "=cmd";
                    Field code = Bean.class.getDeclaredField("code");
                    var column = new CsvWriter.Column<Bean>("编码", CsvWriter.Kind.TEXT, value -> BeanFields.read(value, code));
                    equal("编码\n\t'=cmd", read(CsvWriter.toInputStream(List.of(row), List.of(column))));
                    equal("=cmd", row.code);
                }),
                new UtilityContracts.Case("csv/access-failure-is-not-format-fallback", () -> {
                    var column = new CsvWriter.Column<Cell>("名称", CsvWriter.Kind.TEXT, ignored -> {
                        throw new IllegalStateException("cannot read");
                    });
                    fails(IllegalStateException.class, () -> CsvWriter.toInputStream(List.of(new Cell("x")), List.of(column)));
                }),
                new UtilityContracts.Case("csv/dictionary-callback-failure-propagates", () -> {
                    fails(IllegalStateException.class, () -> render(CsvWriter.Kind.DICTIONARY, 1, ignored -> {
                        throw new IllegalStateException("metadata failure");
                    }));
                    fails(NumberFormatException.class, () -> render(CsvWriter.Kind.DICTIONARY, 1, ignored -> {
                        throw new NumberFormatException("label resolver failed, not an invalid key");
                    }));
                }),
                new UtilityContracts.Case("fields/order-shadowing-and-exclusions", () -> {
                    List<String> fields = BeanFields.instanceFields(Bean.class).stream().map(Field::getName).toList();
                    equal(List.of("code", "name", "parentOnly", "name"), fields);
                    fails(UnsupportedOperationException.class, () -> BeanFields.instanceFields(Bean.class).clear());
                }),
                new UtilityContracts.Case("fields/private-read-write-no-getter-side-effects", () -> {
                    Bean bean = new Bean();
                    Field field = Bean.class.getDeclaredField("code");
                    equal(null, BeanFields.read(bean, field));
                    BeanFields.write(bean, field, "CODE");
                    equal("CODE", BeanFields.read(bean, field));
                }),
                new UtilityContracts.Case("fields/inherited-private-field-write", () -> {
                    Bean bean = new Bean();
                    Field field = Parent.class.getDeclaredField("parentOnly");
                    BeanFields.write(bean, field, "inherited");
                    equal("inherited", BeanFields.read(bean, field));
                }),
                new UtilityContracts.Case("fields/invalid-write-propagates", () -> {
                    Field field = Bean.class.getDeclaredField("code");
                    fails(IllegalArgumentException.class, () -> BeanFields.write(new Bean(), field, 12));
                    fails(IllegalArgumentException.class, () -> BeanFields.read(new Object(), field));
                    fails(NullPointerException.class, () -> BeanFields.read(null, field));
                }),
                new UtilityContracts.Case("fields/concurrent-read-cached-field", ExportContracts::concurrentFields),
                new UtilityContracts.Case("fields/getter-annotation-parent-fallback", () ->
                        equal("parent", BeanFields.methodAnnotation(Bean.class, "getName", Flag.class).value())),
                new UtilityContracts.Case("fields/getter-annotation-child-overrides-parent", () ->
                        equal("child", BeanFields.methodAnnotation(OverrideBean.class, "getName", Flag.class).value())),
                new UtilityContracts.Case("fields/no-getter-or-annotation", () -> {
                    equal(null, BeanFields.methodAnnotation(Bean.class, "missing", Flag.class));
                    equal(null, BeanFields.methodAnnotation(Bean.class, "getCode", Flag.class));
                })
        );
    }

    private static void concurrentFields() throws Exception {
        Bean bean = new Bean();
        bean.code = "fixed";
        Field field = BeanFields.instanceFields(Bean.class).get(0);
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                tasks.add(() -> BeanFields.read(bean, field));
            }
            for (var future : pool.invokeAll(tasks)) {
                equal("fixed", future.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String render(CsvWriter.Kind kind, Object value) throws Exception {
        return render(kind, value, null);
    }

    private static String render(CsvWriter.Kind kind, Object value, IntFunction<String> dictionary) throws Exception {
        var column = new CsvWriter.Column<Cell>("列", kind, Cell::value, dictionary);
        return read(CsvWriter.toInputStream(List.of(new Cell(value)), List.of(column)));
    }

    private static String read(InputStream input) throws Exception {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 小型 CSV 读端探针只用于 round-trip；另有精确字节断言，不靠同一编码器自证。 */
    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (!quoted && (ch == ',' || ch == '\n')) {
                row.add(cell.toString());
                cell.setLength(0);
                if (ch == '\n') {
                    rows.add(row);
                    row = new ArrayList<>();
                }
            } else {
                cell.append(ch);
            }
        }
        check(!quoted);
        row.add(cell.toString());
        rows.add(row);
        return rows;
    }

    private record Cell(Object value) {
    }

    @Retention(RetentionPolicy.RUNTIME)
    private @interface Flag {
        String value();
    }

    private static class Parent {
        private String parentOnly;
        private String name;

        @Flag("parent")
        public String getName() {
            throw new AssertionError("CSV 不应该调用 getter");
        }
    }

    private static class Bean extends Parent {
        private static String ignoredStatic;
        private transient String ignoredTransient;
        private String code;
        private String name;

        public String getCode() {
            throw new AssertionError("CSV 不应该调用 getter");
        }

        @Override
        public String getName() {
            return null;
        }
    }

    private static final class OverrideBean extends Bean {
        @Override
        @Flag("child")
        public String getName() {
            return null;
        }
    }
}
