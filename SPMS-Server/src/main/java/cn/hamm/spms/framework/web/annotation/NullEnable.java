package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>为 null 时依然存储</h1>
 *
 * <p>即使字段为 null，依然保持更新到数据库。
 * 替代旧 {@code airpower.curd.annotation.NullEnable}。</p>
 *
 * @author Hamm.cn
 */
@Target(FIELD)
@Retention(RUNTIME)
@Inherited
@Documented
public @interface NullEnable {
    /**
     * true 时，则将保存 null 到数据库
     */
    boolean value() default true;
}
