package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>标记为空字符串搜索字段</h1>
 *
 * <p>如配置为 true，则传入空字符串时仅查询空字符串，不查询所有。
 * 替代旧 {@code airpower.curd.annotation.SearchEmpty}。</p>
 *
 * @author Hamm.cn
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface SearchEmpty {
    /**
     * 空字符串查询
     */
    boolean value() default true;
}
