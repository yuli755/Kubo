package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>只读字段标记</h1>
 *
 * <p>标记为只读的实体属性在写入数据库前将被清空忽略。
 * 替代旧 {@code airpower.core.annotation.ReadOnly}。</p>
 *
 * @author Hamm.cn
 */
@Target(FIELD)
@Retention(RUNTIME)
@Documented
public @interface ReadOnly {
}
