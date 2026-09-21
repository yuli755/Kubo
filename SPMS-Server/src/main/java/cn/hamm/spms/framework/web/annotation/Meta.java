package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>实体元数据字段标记</h1>
 *
 * <p>替代旧 {@code airpower.core.annotation.Meta}。</p>
 *
 * @author Hamm.cn
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Documented
public @interface Meta {
}
