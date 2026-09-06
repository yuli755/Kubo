package cn.hamm.spms.module.system.datapermission.annotation;

import cn.hamm.spms.module.system.datapermission.serializer.SensitiveFieldSerializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>敏感字段标记</h1>
 *
 * <p>标记在 Entity 的字段上，表示该字段需要根据数据权限策略动态脱敏，
 * 策略由 {@code data_field_policy} 表配置决定。</p>
 *
 * <p>脱敏在 <b>Jackson 序列化层</b>由 {@link SensitiveFieldSerializer} 执行：
 * 不修改实体对象本身，避免把遮蔽值写回数据库，也避免往数值字段写字符串
 * 触发 {@link IllegalArgumentException}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 *   @SensitiveField(resourceKey = "wms.inventory")
 *   @Column(...)
 *   private Double price;
 * }</pre>
 *
 * @author Hamm.cn
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveFieldSerializer.class)
public @interface SensitiveField {
    /**
     * 所属数据资源标识，对应 {@code data_resource.resource_key}
     */
    String resourceKey();
}