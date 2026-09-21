package cn.hamm.spms.framework.web.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <h1>端点权限控制注解</h1>
 *
 * <p>标记在控制器类或端点方法上，声明是否要求登录认证及 RBAC 权限授权。
 * 替代旧 {@code cn.hamm.airpower.curd.permission.Permission}。</p>
 *
 * @author Hamm.cn
 */
@Target({METHOD, TYPE})
@Retention(RUNTIME)
@Inherited
@Documented
public @interface Permission {
    /**
     * 是否需要登录，默认需要
     */
    boolean login() default true;

    /**
     * 是否需要 RBAC 权限授权，默认需要
     */
    boolean authorize() default true;
}
