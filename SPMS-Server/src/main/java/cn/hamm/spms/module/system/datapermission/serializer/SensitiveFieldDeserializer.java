package cn.hamm.spms.module.system.datapermission.serializer;

import cn.hamm.spms.module.system.datapermission.service.MaskedInputRegistry;
import cn.hamm.spms.module.system.datapermission.service.MaskedWriteGuard;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>敏感字段的写入侧反序列化器</h1>
 *
 * <p>解决的问题很具体：列级脱敏一旦对某角色生效，详情接口给他的是
 * {@code phone = "138****000"}；而编辑表单是"整实体回填再整实体提交"的，
 * 用户哪怕只改了昵称，这串掩码也会原样回来。不处理就有两类事故：
 * 字符串列被掩码覆盖（不可逆损坏），数值列（{@code Double price}）则在 Jackson
 * 阶段就 {@code InvalidFormatException}，整条记录改不动、报错还跟权限无关。</p>
 *
 * <p>处置：识别出掩码 → 登记到 {@link MaskedInputRegistry} → 按目标类型分流：</p>
 * <ul>
 *   <li><b>字符串字段</b>（phone / email / idCard）：返回 {@code null} 不污染数据，
 *       由 {@code SensitiveWriteProtection} 在更新时用库中原值覆盖、新增时拒绝。
 *       登记必须发生在返回 null <b>之前</b>：置 null 之后"用户主动清空"与
 *       "受限未提交真值"在实体上就长得一模一样，而两者处置相反；</li>
 *   <li><b>非字符串字段</b>（price）：直接报"该字段受限"。置 null 会被实体上的
 *       {@code @NotNull(groups = WhenUpdate)} 拦成"采购单价不能为空"这种与权限无关的假错误；
 *       用 sentinel（0 / 空串）兜底，则一旦恢复链路某处没接上就静默污染金额。
 *       两害相权：宁可让受限角色这条记录改不动（界面有受限提示条说明原因）；</li>
 *   <li><b>真实值</b>一律交回 Jackson 按目标类型解析，不做任何宽容兜底 ——
 *       类型错误就该暴露成错误，而不是被静默吞掉。</li>
 * </ul>
 *
 * <p>字段名与宿主类型通过 {@link ContextualDeserializer} 在装配期取，而不是运行时
 * 从 {@code DeserializationContext} 里猜：后者在不同 Jackson 版本上的可得性不一致，
 * 而"是哪个字段"正是本类唯一需要的外部信息，取错就等于登记错、恢复错。
 * 这与读侧 {@link SensitiveFieldSerializer} 用 {@code ContextualSerializer} 是对称的。</p>
 *
 * <p>报错文案能否原样到达用户，取决于框架对 {@code HttpMessageNotReadableException}
 * 的包装，这里不承诺；能承诺的是<b>不会静默写坏数据</b>。</p>
 *
 * @author Hamm.cn
 */
public class SensitiveFieldDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {

    /**
     * 宿主实体类（登记键的一部分）；无上下文时为 null
     */
    private final Class<?> ownerType;

    /**
     * JSON 属性名（与 {@link SensitiveFieldSerializer} 写出的名字同源）
     */
    private final String propertyName;

    /**
     * 目标类型是否是字符串族 —— 决定"置 null 交恢复"还是"当场拒绝"
     */
    private final boolean textualTarget;

    private final JavaType propertyType;

    /**
     * 无参构造：Jackson 需要它能实例化一个"未装配"的默认反序列化器
     */
    public SensitiveFieldDeserializer() {
        this(null, null, false, null);
    }

    private SensitiveFieldDeserializer(Class<?> ownerType, String propertyName,
                                       boolean textualTarget, JavaType propertyType) {
        this.ownerType = ownerType;
        this.propertyName = propertyName;
        this.textualTarget = textualTarget;
        this.propertyType = propertyType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        if (property == null) {
            // 顶层直接反序列化敏感类型（本项目不存在该入口）：保持未装配状态，
            // 由 deserialize() 里的"上下文缺失"分支兜住
            return this;
        }
        JavaType type = property.getType();
        Class<?> raw = (type == null) ? null : type.getRawClass();
        boolean textual = raw != null && CharSequence.class.isAssignableFrom(raw);
        return new SensitiveFieldDeserializer(property.getDeclaringClass(), property.getName(), textual, type);
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING
                && MaskedWriteGuard.looksLikeMaskedPlaceholder(parser.getText())) {
            return handleMaskedPlaceholder(parser, context);
        }
        return parseRealValue(parser, context);
    }

    /**
     * 收到脱敏占位值：登记，再按目标类型分流
     */
    private Object handleMaskedPlaceholder(JsonParser parser, DeserializationContext context) throws IOException {
        if (ownerType == null || propertyName == null) {
            // 认不出"是哪个字段"就没法登记，也就没法恢复。此时唯一安全的选择是失败，
            // 而不是把掩码原样返回（掩码入库不可逆）
            PARAM_INVALID.show("敏感字段上下文缺失，无法判定该脱敏值应否落库，请重试或联系管理员");
            return null;
        }
        MaskedInputRegistry.mark(ownerType, propertyName);
        if (textualTarget) {
            return null;
        }
        PARAM_INVALID.show("字段[" + propertyName + "]对当前角色受限，无法以脱敏占位值提交；"
                + "请申请明文权限，或由具备明文权限的角色修改该字段");
        return null;
    }

    /**
     * 真实值走 Jackson 的正常解析路径（保留类型错误）
     */
    private Object parseRealValue(JsonParser parser, DeserializationContext context) throws IOException {
        if (propertyType != null) {
            return context.readValue(parser, propertyType);
        }
        // 无装配上下文时不猜类型：交给 Jackson 按节点原值处理，字段赋值时的强制转换照旧
        return parser.getCodec().readTree(parser);
    }
}
