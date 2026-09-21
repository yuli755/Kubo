package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>模糊搜索字段标记</h1>
 *
 * <p>标记在实体字段上，指定 JPA 动态查询时按前缀模糊或全模糊 LIKE 匹配。
 * 替代旧 {@code airpower.curd.annotation.Search}。</p>
 *
 * @author Hamm.cn
 */
@Target({METHOD, FIELD})
@Retention(RUNTIME)
@Documented
public @interface Search {
    /**
     * 是否全模糊查询（%value%），默认 false 为左模糊（value%）
     */
    boolean fullLike() default false;
}
