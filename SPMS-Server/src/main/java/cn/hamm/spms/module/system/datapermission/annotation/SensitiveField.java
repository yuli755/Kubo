package cn.hamm.spms.module.system.datapermission.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h1>敏感字段标记</h1>
 *
 * <p>标记在 Entity 的字段上，表示该字段可能需要根据数据权限策略进行脱敏处理。
 * 脱敏策略由 {@code data_field_policy} 表配置决定。</p>
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
public @interface SensitiveField {
    /**
     * 所属数据资源标识，对应 {@code data_resource.resource_key}
     */
    String resourceKey();
}