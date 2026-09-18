package cn.hamm.spms.framework.curd.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>唯一列（J3：{@code @Column(unique = true)} 的接替者）</h1>
 *
 * <p>JPA 时代"这一列不允许重复"由 {@code @Column(unique = true)} 声明，{@code CurdService#checkUnique}
 * 在写入前按它逐字段查重。MyBatis-Plus 的 {@code @TableField} <b>没有</b> unique 属性，
 * 而这件事是业务语义（"物料编码不许重复"），不该随换轨消失 —— 所以注解由本仓自持。</p>
 *
 * <p>与 DDL 的关系：注解只驱动<b>应用层查重</b>（能给出友好报错），并发下的最终防线仍是
 * {@code init.sql} 里的 {@code UNIQUE KEY}。两边都要有：只靠注解挡不住并发，只靠唯一键只能返回 500。</p>
 *
 * @author Arena agent
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Unique {
}
