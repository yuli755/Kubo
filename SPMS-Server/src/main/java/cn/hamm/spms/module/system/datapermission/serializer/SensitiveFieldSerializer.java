package cn.hamm.spms.module.system.datapermission.serializer;

import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import cn.hamm.spms.framework.curd.datapermission.PermissionContext;
import cn.hamm.spms.framework.curd.datapermission.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionRuntime;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;

/**
 * <h1>敏感字段序列化器（列级脱敏的正确落点）</h1>
 *
 * <p><b>为什么在序列化层，而不是 AOP 反射改实体字段</b>：</p>
 * <ol>
 *   <li>AOP 改的是 JPA <b>托管实体</b>，配合 {@code @DynamicUpdate} 与被 Hibernate
 *       一级缓存持有的场景，存在把 {@code 138****0000} 写回数据库的风险；</li>
 *   <li>本项目被标记的字段是 {@code Double price} 这类数值字段，
 *       反射 {@code field.set(obj, "1*.*5")} 会抛
 *       {@link IllegalArgumentException}（旧实现只 catch 了 {@code IllegalAccessException}），
 *       结果是接口 500 而不是脱敏 —— 这是"看起来实现了、其实一开就炸"的典型；</li>
 *   <li>序列化层不触碰持久化对象，任何 JSON 出口（列表/详情/统计）自动一致。</li>
 * </ol>
 *
 * <p><b>未覆盖出口</b>：导出走文件流、不经过 Jackson，因此导出必须单独接管
 * （见推进路线 P3：{@code /export} 端点复用同一套 {@link ColumnMaskingService}）。
 * 这条限制请写进 PRD，不要当成已完成能力。</p>
 *
 * <p>脱敏后的值以字符串写出（JSON 合法），前端由 {@code v-mask}/{@code my-mask}
 * 契约按"已脱敏文本"渲染；如需保持数值类型可改为输出 {@code null} + 可见性元数据，
 * 属 D11 决策的一部分。</p>
 *
 * @author Hamm.cn
 */
public class SensitiveFieldSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private final String resourceKey;
    private final String fieldName;

    /**
     * 无上下文构造：Jackson 需要可实例化的默认构造器
     */
    public SensitiveFieldSerializer() {
        this(null, null);
    }

    private SensitiveFieldSerializer(String resourceKey, String fieldName) {
        this.resourceKey = resourceKey;
        this.fieldName = fieldName;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (resourceKey == null || fieldName == null) {
            // 拿不到字段元信息（理论上不会发生）：宁可原样输出也不误伤，但要记账
            recordBinding("sensitive-annotation-without-context", "resourceKey=" + resourceKey + " field=" + fieldName);
            gen.writePOJO(value);
            return;
        }

        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            // 系统身份或无上下文：不脱敏，但明文访问要留痕
            recordPlaintextAccess();
            gen.writePOJO(value);
            return;
        }

        ColumnMaskingService maskingService = DataPermissionRuntime.maskingService();
        if (maskingService == null) {
            // 容器未就绪（极早期请求），保守原样输出并记账
            recordBinding("masking-service-unavailable", null);
            gen.writePOJO(value);
            return;
        }

        Object masked;
        try {
            masked = maskingService.maskFieldValue(resourceKey, fieldName, value, context);
        }
        catch (RuntimeException ex) {
            // 脱敏失败必须安全失败：不回退原值，改为遮蔽
            recordBinding("mask-failed", ex.getMessage());
            gen.writeString("****");
            return;
        }

        if (masked == value) {
            recordPlaintextAccess();
            gen.writePOJO(value);
            return;
        }
        recordMasked();
        gen.writeString(String.valueOf(masked));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property) throws JsonMappingException {
        if (property == null) {
            return this;
        }
        SensitiveField annotation = property.getAnnotation(SensitiveField.class);
        if (annotation == null && property.getMember() != null) {
            annotation = property.getMember().getAnnotation(SensitiveField.class);
        }
        if (annotation == null) {
            return provider.findValueSerializer(property.getType(), property);
        }
        return new SensitiveFieldSerializer(annotation.resourceKey(), property.getName());
    }

    private void recordMasked() {
        DataPermissionAudit audit = DataPermissionRuntime.audit();
        if (audit != null) {
            audit.shadow(resourceKey, DataPermissionAudit.REASON_MASKED, fieldName);
        }
    }

    private void recordPlaintextAccess() {
        DataPermissionProperties properties = DataPermissionRuntime.properties();
        DataPermissionAudit audit = DataPermissionRuntime.audit();
        if (properties != null && audit != null && properties.isAuditPlaintextAccess()) {
            audit.shadow(resourceKey, DataPermissionAudit.REASON_PLAINTEXT_ACCESSED, fieldName);
        }
    }

    private void recordBinding(String reason, String detail) {
        DataPermissionAudit audit = DataPermissionRuntime.audit();
        if (audit != null) {
            audit.binding(resourceKey == null ? "unknown" : resourceKey, reason, detail);
        }
    }
}
