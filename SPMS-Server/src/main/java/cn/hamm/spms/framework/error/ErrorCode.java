package cn.hamm.spms.framework.error;

import java.util.Objects;

/**
 * <h1>项目错误码的最小行为契约</h1>
 *
 * <p>只保留项目实际使用的断言/抛错能力；不继承旧接口，不承担字典或 HTTP 响应职责。
 * 各次抛错新建异常，传入文案不修改枚举单例。空值和比较口径按既有调用行为保留。</p>
 *
 * @author Hamm.cn
 */
public interface ErrorCode {
    int getCode();
    String getMessage();

    default void show() {
        show(getMessage(), null);
    }

    default void show(String message) {
        show(message, null);
    }

    default void show(String message, Object data) {
        throw new BusinessException(getCode(), message, data);
    }

    default void when(boolean condition) {
        when(condition, getMessage());
    }

    default void when(boolean condition, String message) {
        when(condition, message, null);
    }

    default void when(boolean condition, String message, Object data) {
        if (condition) {
            show(message, data);
        }
    }

    default void whenNull(Object value) {
        whenNull(value, getMessage());
    }

    default void whenNull(Object value, String message) {
        when(value == null, message);
    }

    default void whenNotNull(Object value) {
        whenNotNull(value, getMessage());
    }

    default void whenNotNull(Object value, String message) {
        when(value != null, message);
    }

    default void whenEmpty(Object value) {
        whenEmpty(value, getMessage());
    }

    default void whenEmpty(Object value, String message) {
        String text = value == null ? null : value.toString();
        // 与旧 hasText 相同：空白也视作空；不把空集合/0/false 擅自变成空值。
        when(text == null || text.isBlank(), message);
    }

    default void whenNotEquals(Object first, Object second) {
        whenNotEquals(first, second, getMessage());
    }

    default void whenNotEquals(Object first, Object second, String message) {
        when(!Objects.equals(first, second), message);
    }

    default void whenNotEqualsIgnoreCase(String first, String second) {
        whenNotEqualsIgnoreCase(first, second, getMessage());
    }

    default void whenNotEqualsIgnoreCase(String first, String second, String message) {
        if (first == null || second == null) {
            show(message);
        }
        // 保留旧默认 Locale 的比较口径；本批不借异常迁移调整密码/验证码比较算法。
        when(!first.toLowerCase().equals(second.toLowerCase()), message);
    }
}
