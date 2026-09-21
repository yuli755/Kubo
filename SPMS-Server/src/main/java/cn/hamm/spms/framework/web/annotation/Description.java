package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>类、方法或属性的中文描述文案</h1>
 *
 * <p>替代旧 {@code airpower.core.annotation.Description}。</p>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD, TYPE, PARAMETER})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Description {
    /**
     * 描述文案
     */
    String value() default "";
}
