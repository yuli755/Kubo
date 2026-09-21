package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>CSV 导出列注解</h1>
 *
 * <p>替代旧 {@code airpower.core.annotation.Export}。</p>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Export {
    /**
     * 列数据类型
     */
    Type value() default Type.TEXT;

    /**
     * 导出列排序
     */
    int sort() default 0;

    /**
     * 是否移除
     */
    boolean remove() default false;

    /**
     * <h1>列数据类型</h1>
     */
    enum Type {
        /**
         * 普通文本
         */
        TEXT,

        /**
         * 时间日期
         */
        DATETIME,

        /**
         * 数字
         */
        NUMBER,

        /**
         * 字典
         */
        DICTIONARY,

        /**
         * 布尔值
         */
        BOOLEAN
    }
}
