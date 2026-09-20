package cn.hamm.spms.framework.util;

/**
 * <h1>CSV 文本单元格的公式防护与语法引用</h1>
 *
 * <p>公式防护与 CSV 引用不是同一件事：双引号不会阻止 Excel 执行公式。
 * 原 JPA 导出前的实体清洗和新 CSV 编码器复用同一防护，不维护两份触发字符表。</p>
 *
 * @author Hamm.cn
 */
public final class CsvText {
    private CsvText() {
    }

    /**
     * 对危险文本前缀添加单引号；已保护文本幂等，安全/null 值返回原引用。
     * 除既有触发字符外，覆盖 LF 与空白/BOM 后的公式符号，防止借前导空白绕过。
     */
    public static String protectFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int first = 0;
        while (first < value.length() && (Character.isWhitespace(value.charAt(first)) || value.charAt(first) == '\uFEFF')) {
            first++;
        }
        if (isTrigger(value.charAt(0)) || (first < value.length() && isTrigger(value.charAt(first)))) {
            return "'" + value;
        }
        return value;
    }

    /** CSV 引用：保留逗号、双引号、CR/LF，不能再用空格替换原始数据；本方法不负责公式防护。 */
    public static String quote(String value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static boolean isTrigger(char value) {
        return value == '=' || value == '+' || value == '-' || value == '@'
                || value == '\t' || value == '\r' || value == '\n';
    }
}
