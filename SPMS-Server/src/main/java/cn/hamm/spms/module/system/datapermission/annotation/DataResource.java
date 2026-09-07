package cn.hamm.spms.module.system.datapermission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>读侧 Mapper 的资源绑定</h1>
 *
 * <p>MyBatis 只认识 {@code mappedStatementId}，数据权限只认识
 * {@code data_resource.resource_key}。这个注解是两者之间唯一的代码侧绑定点：
 * 读侧 Mapper 的每一个 XML statement 都会在启动期展开成
 * {@code mappedStatementId → resourceKey}，拦截器不许按类名猜资源，也不许在请求线程临时查表。</p>
 *
 * <p>它只允许标在 {@code KuboBaseMapper} 接口上。真正的扫描范围由
 * {@code kubo.data-permission.mapper-read.enabled} 控制，默认关闭；关闭时注解不会注册任何
 * Mapper Bean，也不会构建这张绑定表。</p>
 *
 * @author Hamm.cn
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataResource {

    /**
     * {@code data_resource.resource_key}
     *
     * @return 资源键
     */
    String key();

    /**
     * 权限 SQL 注入时使用的主表别名；为空时由后续资源适配层使用登记表的 {@code table_alias}
     *
     * @return SQL 主表别名，可为空
     */
    String alias() default "";
}
