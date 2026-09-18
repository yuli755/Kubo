package cn.hamm.spms.framework.curd.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>仅入参的关系对象（J3）</h1>
 *
 * <p>J3 的规矩是：实体上的关系对象字段（{@code MaterialEntity material}）本身不是列，
 * 它的值由一根标量外键列承载（{@code materialId} → {@code material_id}），
 * 写路径从对象同步到标量、读路径从标量回填对象。</p>
 *
 * <p>但有一类关系字段天生没有列，也不该有：<b>客户端只在请求里带一次</b>，
 * 用来表达"这一步用什么参数"，服务端消费完就结束。典型是入库明细上的
 * {@code storage}（"这次入库进哪个仓库"）—— 值最终落在库存行上，明细表里没有仓库列。</p>
 *
 * <p>标了这个注解即声明三件事，缺一条都会变成静默故障：</p>
 * <ol>
 *   <li>不是列（仍要配 {@code @TableField(exist = false)}，否则 MyBatis-Plus 会去查同名列）；</li>
 *   <li>不参与外键同步（{@code RelationMapping} 会跳过它，不会因为"没有 storageId"而留下半个写路径）；</li>
 *   <li>不参与对象图回填（{@code RelationHydrator} 没有列可读，跳过是唯一正确行为）。</li>
 * </ol>
 *
 * <p>门禁 {@code maskContract.spec.ts} 会要求：每个多对一关系字段要么有桥接标量外键字段，
 * 要么标了本注解 —— 让"忘了补外键字段"和"故意只作入参"这两件事在代码里长得不一样。</p>
 *
 * @author Arena agent
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InputOnly {
}
