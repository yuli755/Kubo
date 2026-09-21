package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>接口整体暴露标记</h1>
 *
 * <p>替代旧 {@code cn.hamm.airpower.core.annotation.ExposeAll}。</p>
 *
 * @author Hamm.cn
 */
@Target(METHOD)
@Retention(RUNTIME)
@Inherited
@Documented
public @interface ExposeAll {
    Class<?>[] value() default {};
}
