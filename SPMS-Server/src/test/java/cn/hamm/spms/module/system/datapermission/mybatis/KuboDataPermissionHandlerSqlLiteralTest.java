package cn.hamm.spms.module.system.datapermission.mybatis;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * <h1>S-1：行权限参数 SQL 字面量转义单测</h1>
 *
 * {@code toSafeSql} 会把命名参数直接替换进 SQL 字符串（再交给 JSqlParser 解析），
 * 因此 {@code sqlLiteral} 是唯一防注入点。MySQL 默认模式下反斜杠是串内转义符：
 * 过去只双写单引号时，以 {@code \} 结尾的值会吃掉闭合引号，把后续条件并入字符串
 * 后再开启新的注入面。本测试锁死转义语义（含反斜杠加倍、NUL 拒绝、数值/布尔）。
 */
class KuboDataPermissionHandlerSqlLiteralTest {

    private final KuboDataPermissionHandler handler = mock(KuboDataPermissionHandler.class);

    private Object literal(Object value) throws Exception {
        Method method = KuboDataPermissionHandler.class.getDeclaredMethod("sqlLiteral", Object.class);
        method.setAccessible(true);
        return method.invoke(handler, value);
    }

    @Test
    void 字符串值反斜杠必须加倍() throws Exception {
        // 修复前产出 'a\'：闭合引号被反斜杠吃掉，后续 SQL 被并入字符串继续解析
        assertEquals("'a\\\\'", literal("a\\"));
        // 经典逃逸载荷：`x\' OR 1=1 -- ` —— 修复后反斜杠加倍、单引号双写，整体只是普通字符串
        assertEquals("'x\\\\'' OR 1=1 -- '", literal("x\\' OR 1=1 -- "));
    }

    @Test
    void 字符串值单引号必须双写() throws Exception {
        assertEquals("'O''Brien'", literal("O'Brien"));
    }

    @Test
    void 数值与布尔按字面量输出() throws Exception {
        assertEquals("5", literal(5L));
        assertEquals("2.5", literal(2.5d));
        assertEquals("TRUE", literal(Boolean.TRUE));
        assertEquals("FALSE", literal(Boolean.FALSE));
    }

    @Test
    void 非有限数值必须拒绝() {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> literal(Double.NaN));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void 含NUL字符的字符串必须拒绝() {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> literal("a\u0000b"));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }

    @Test
    void 不可绑定类型必须拒绝() {
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> literal(null));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }
}
