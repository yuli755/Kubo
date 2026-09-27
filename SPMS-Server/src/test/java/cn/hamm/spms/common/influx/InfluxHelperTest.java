package cn.hamm.spms.common.influx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>S-1：Flux 标识符白名单单测</h1>
 *
 * 历史查询把 code/uuid 直接插进 Flux 字符串字面量（{@code getFluxQuery}），
 * 锁住两件事：
 * 1. 正常标识符（字母数字 + _ - : .）必须放行，不影响既有采集端查询；
 * 2. 携带引号/换行/括号等 Flux 语法字符的注入载荷必须被拒绝。
 */
class InfluxHelperTest {

    @Test
    void 正常标识符必须放行() {
        assertDoesNotThrow(() -> InfluxHelper.validateFluxIdentifiers("status", "device-01.abc"));
        assertDoesNotThrow(() -> InfluxHelper.validateFluxIdentifiers("report:temp_1", "U_1234567890"));
    }

    @Test
    void 引号闭合注入载荷必须拒绝() {
        // 目标：闭合 r._measurement == "..." 后追加 ` or true` 之类的 Flux 表达式
        assertThrows(RuntimeException.class,
                () -> InfluxHelper.validateFluxIdentifiers("temp\" or \"1\"==\"1", "u1"));
        assertThrows(RuntimeException.class,
                () -> InfluxHelper.validateFluxIdentifiers("temp", "u1\") or (\"1"));
    }

    @Test
    void 换行与括号等Flux语法字符必须拒绝() {
        assertThrows(RuntimeException.class, () -> InfluxHelper.validateFluxIdentifiers("a(b)", "u1"));
        assertThrows(RuntimeException.class, () -> InfluxHelper.validateFluxIdentifiers("a", "u1\n|> limit(n: 9999)"));
        assertThrows(RuntimeException.class, () -> InfluxHelper.validateFluxIdentifiers("a b", "u1"));
    }

    @Test
    void 空值必须拒绝() {
        assertThrows(RuntimeException.class, () -> InfluxHelper.validateFluxIdentifiers(null, "u1"));
        assertThrows(RuntimeException.class, () -> InfluxHelper.validateFluxIdentifiers("c", null));
    }

    @Test
    void 超长标识符必须拒绝() {
        String tooLong = "a".repeat(129);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> InfluxHelper.validateFluxIdentifiers(tooLong, "u1"));
        assertTrue(error.getMessage() == null || !error.getMessage().contains(tooLong),
                "异常信息不得回显完整注入载荷");
    }
}
