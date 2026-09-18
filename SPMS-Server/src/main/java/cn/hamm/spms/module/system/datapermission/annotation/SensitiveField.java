package cn.hamm.spms.module.system.datapermission.annotation;

import cn.hamm.spms.module.system.datapermission.serializer.SensitiveFieldSerializer;
import cn.hamm.spms.module.system.datapermission.serializer.SensitiveFieldDeserializer;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
 * <p><b>同一个注解也管写入</b>（{@link SensitiveFieldDeserializer} +
 * {@code SensitiveWriteProtection}）：编辑表单是"整实体回填再整实体提交"的，
 * 受限时前端拿到的就是 {@code 138****000}，原样提交会出两类事故 ——
 * 字符串列被掩码覆盖（不可逆损坏）、数值列在反序列化就 400（整条记录改不动）。
 * 因此这里额外声明写侧语义，而不是让每个业务 service 自己记得处理。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 *   @SensitiveField(resourceKey = "wms.inventory")
 *   private Double price;   // 列名 = 驼峰转下划线（J3 起实体不再写 @Column）
 * }</pre>
 *
 * @author Hamm.cn
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveFieldSerializer.class)
@JsonDeserialize(using = SensitiveFieldDeserializer.class)
public @interface SensitiveField {
    /**
     * 所属数据资源标识，对应 {@code data_resource.resource_key}
     */
    String resourceKey();

    /**
     * 更新时把"提交回来的脱敏占位值"恢复为库中原值
     *
     * <p>默认 true：受限角色点"保存"只改了别的字段时，不该顺手把该列写成 {@code ****}。
     * 置 false 表示"这个字段允许被写成掩码"——本项目里没有这种合理场景，
     * 留这个开关是为了让将来的导入/同步等入口能显式表达差异，而不是靠注释约定。</p>
     */
    boolean restoreOnMasked() default true;
}