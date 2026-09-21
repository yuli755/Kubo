package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记字段脱敏</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.core.annotation.Desensitize}。</p>
 *
 * @author Hamm.cn
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface Desensitize {
    /**
     * 脱敏类型
     */
    DesensitizeType value();

    /**
     * 开始保留位数
     */
    int head() default 0;

    /**
     * 结束保留位数
     */
    int tail() default 0;

    /**
     * 脱敏符号
     */
    String symbol() default "*";

    /**
     * 是否替换
     */
    boolean replace() default false;
}
