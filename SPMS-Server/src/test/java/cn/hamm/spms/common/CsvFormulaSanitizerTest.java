package cn.hamm.spms.common;

import lombok.Data;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>CsvFormulaSanitizer 契约测试（CSV 公式注入防护）</h1>
 *
 * <p>锁住三条底线：危险首字符（= + - @ \t \r）被前缀单引号；无危险值时不复制（原引用返回）；
 * 只改副本、绝不改原实体（避免把转义写回库）。</p>
 */
class CsvFormulaSanitizerTest {

    /** 供测试的简单实体（含继承层次的 String 字段，模拟真实 JPA 实体） */
    @Data
    @Accessors(chain = true)
    static class Base {
        private String remark;
    }

    @Data
    @Accessors(chain = true)
    static class Pojo extends Base {
        private String name;
        private String code;
        private Integer qty;
    }

    @Test
    @DisplayName("= + - @ \\t \\r 六种危险首字符均被前缀单引号")
    void sanitizesAllTriggers() {
        assertEquals("'=cmd", CsvFormulaSanitizer.sanitizeValue("=cmd"));
        assertEquals("'+8613", CsvFormulaSanitizer.sanitizeValue("+8613"));
        assertEquals("'-1", CsvFormulaSanitizer.sanitizeValue("-1"));
        assertEquals("'@import", CsvFormulaSanitizer.sanitizeValue("@import"));
        assertEquals("'\tabc", CsvFormulaSanitizer.sanitizeValue("\tabc"));
        assertEquals("'\rabc", CsvFormulaSanitizer.sanitizeValue("\rabc"));
    }

    @Test
    @DisplayName("普通值/空值不转义，原引用返回")
    void leavesSafeValues() {
        String safe = "正常名称";
        assertSame(safe, CsvFormulaSanitizer.sanitizeValue(safe));
        assertSame(null, CsvFormulaSanitizer.sanitizeValue(null));
        assertSame("", CsvFormulaSanitizer.sanitizeValue(""));
    }

    @Test
    @DisplayName("无危险值时 sanitize 原样返回入参引用（不复制）")
    void noCopyWhenClean() {
        List<Pojo> list = Arrays.asList(new Pojo().setName("正常").setCode("C1"), new Pojo().setName("B"));
        List<Pojo> result = CsvFormulaSanitizer.sanitize(list);
        assertSame(list, result);
    }

    @Test
    @DisplayName("含危险值时在副本上转义，原实体不被改动")
    void sanitizesOnCopyWithoutMutatingOriginal() {
        Pojo original = new Pojo().setName("=SUM(A1)").setCode("C1").setRemark("正常备注");
        List<Pojo> result = CsvFormulaSanitizer.sanitize(List.of(original));

        Pojo sanitized = result.get(0);
        assertNotSame(original, sanitized);
        assertEquals("'=SUM(A1)", sanitized.getName());
        // 原实体不被改动（转义不写回库）
        assertEquals("=SUM(A1)", original.getName());
        // 未触发转义的字段保持原值
        assertEquals("C1", sanitized.getCode());
    }

    @Test
    @DisplayName("继承层次的 String 字段（父类 remark）也被转义")
    void sanitizesInheritedFields() {
        Pojo original = new Pojo().setName("正常").setRemark("+8613800138000");
        Pojo sanitized = CsvFormulaSanitizer.sanitize(List.of(original)).get(0);
        assertEquals("'+8613800138000", sanitized.getRemark());
        assertEquals("+8613800138000", original.getRemark());
    }

    @Test
    @DisplayName("非 String 字段（Integer qty）不参与、不受影响")
    void ignoresNonStringFields() {
        Pojo original = new Pojo().setName("-1").setQty(42);
        Pojo sanitized = CsvFormulaSanitizer.sanitize(List.of(original)).get(0);
        assertEquals("'-1", sanitized.getName());
        assertTrue(sanitized.getQty() == 42);
    }
}
