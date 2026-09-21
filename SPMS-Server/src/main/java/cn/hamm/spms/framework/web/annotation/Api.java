package cn.hamm.spms.framework.web.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>自持 API 控制器注解</h1>
 *
 * <p>组合 {@link RestController} 与 {@link RequestMapping}，指定端点路由路径。
 * 替代旧 {@code airpower.api.annotation.Api}。</p>
 *
 * @author Hamm.cn
 */
@Target(TYPE)
@Retention(RUNTIME)
@Inherited
@Documented
@RestController
@RequestMapping
public @interface Api {
    @AliasFor(annotation = RequestMapping.class, attribute = "path")
    String value() default "";
}
