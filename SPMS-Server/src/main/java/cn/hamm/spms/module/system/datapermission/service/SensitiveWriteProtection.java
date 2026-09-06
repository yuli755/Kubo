package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.hamm.airpower.exception.Errors.PARAM_INVALID;

/**
 * <h1>敏感字段的写侧统一处置</h1>
 *
 * <p>把"提交回来的是脱敏占位值"这件事的处置收在一处，理由有两层：</p>
 * <ol>
 *   <li><b>不能散到各业务 service</b>：散着写就一定会有人忘，忘掉的后果是把
 *       {@code 138****000} 写进真实手机号列，且不可逆；</li>
 *   <li><b>判定必须与读侧同源</b>：什么算掩码，由 {@link MaskedWriteGuard} 说了算，
 *       前端 {@code looksMasked()} 与这里对同一批用例（有契约测试）。</li>
 * </ol>
 *
 * <p>处置规则很窄，刻意不做任何"猜"：</p>
 * <ul>
 *   <li>更新：该字段在本次请求里<b>确实收到过掩码</b>（{@link MaskedInputRegistry}）
 *       且注解 {@code restoreOnMasked=true} → 用库里的值覆盖，其余字段照常保存；</li>
 *   <li>新增：没有"原值"可恢复 → 明确拒绝，报错说明该字段受限；</li>
 *   <li>用户主动清空（提交 {@code null}）<b>不算</b>掩码，照写不误 ——
 *       这正是需要登记而不能只看实体的原因。</li>
 * </ul>
 *
 * <p>反射结果按类缓存（{@link ConcurrentHashMap}），只在首次遇到该实体类时扫一遍继承链；
 * 缓存内容不含任何用户相关信息。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
public final class SensitiveWriteProtection {

    /**
     * 单个敏感字段的绑定信息
     *
     * @param field          反射字段（已 setAccessible）
     * @param jsonName       JSON 属性名（{@code @JsonProperty} 优先，与序列化层、策略表 fieldName 同源）
     * @param resourceKey    数据资源标识
     * @param restoreOnMasked 是否允许在更新时用库中原值兜底
     */
    public record Binding(Field field, String jsonName, String resourceKey, boolean restoreOnMasked) {
    }

    private static final Map<Class<?>, List<Binding>> BINDINGS = new ConcurrentHashMap<>();

    private SensitiveWriteProtection() {
    }

    /**
     * 该实体类是否带需要写侧保护的字段
     */
    public static boolean guards(Class<?> type) {
        return !bindingsFor(type).isEmpty();
    }

    /**
     * 收集实体类（含父类）上所有 {@link SensitiveField} 字段
     */
    public static List<Binding> bindingsFor(Class<?> type) {
        return BINDINGS.computeIfAbsent(type, SensitiveWriteProtection::scan);
    }

    private static List<Binding> scan(Class<?> type) {
        List<Binding> found = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                SensitiveField annotation = field.getAnnotation(SensitiveField.class);
                if (annotation == null) {
                    continue;
                }
                if (field.getType().isPrimitive()) {
                    // 基本类型无法承载"未提供"（null），恢复语义无从表达 —— 配置错误必须响亮失败
                    throw new IllegalStateException("@SensitiveField 不能标在基本类型上: "
                            + type.getName() + '.' + field.getName());
                }
                field.setAccessible(true);
                // 登记键必须与序列化器写出的 JSON 属性名同源：@JsonProperty 改名过就得用改名后的，
                // 否则"读侧遮了、写侧认不出"，掩码会直接落库
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                String jsonName = (jsonProperty != null && !jsonProperty.value().isEmpty())
                        ? jsonProperty.value() : field.getName();
                found.add(new Binding(field, jsonName, annotation.resourceKey(), annotation.restoreOnMasked()));
            }
        }
        return found.isEmpty() ? Collections.emptyList() : List.copyOf(found);
    }

    /**
     * 更新前：把"提交回来的是掩码"的字段恢复为库中原值
     *
     * @param incoming 本次提交的实体（可能已由框架合并过）
     * @param stored   库里的实体；为 null 时不做任何事
     * @return 是否发生过恢复（用于日志与影子指标）
     */
    public static <E> boolean restoreOnUpdate(E incoming, E stored) {
        if (incoming == null || stored == null || !MaskedInputRegistry.hasAny()) {
            return false;
        }
        boolean restored = false;
        for (Binding binding : bindingsFor(incoming.getClass())) {
            if (!binding.restoreOnMasked() || !MaskedInputRegistry.isMarked(incoming.getClass(), binding.jsonName())) {
                continue;
            }
            try {
                binding.field().set(incoming, binding.field().get(stored));
                restored = true;
                log.info("敏感字段[{}.{}]提交值为脱敏占位，已按策略恢复库中原值（resourceKey={}）",
                        incoming.getClass().getSimpleName(), binding.jsonName(), binding.resourceKey());
            }
            catch (IllegalAccessException ex) {
                // 反射失败不能退化成"把掩码写进去"，也不能让业务彻底不可用：这里选择拒绝本次写入
                PARAM_INVALID.show("敏感字段写入保护不可用，请联系管理员（" + ex.getClass().getSimpleName() + "）");
                return false;
            }
        }
        return restored;
    }

    /**
     * 新增前：掩码值一律拒绝（没有原值可恢复）
     */
    public static void rejectMaskedOnAdd(Object incoming) {
        if (incoming == null || !MaskedInputRegistry.hasAny()) {
            return;
        }
        for (Binding binding : bindingsFor(incoming.getClass())) {
            if (MaskedInputRegistry.isMarked(incoming.getClass(), binding.jsonName())) {
                PARAM_INVALID.show("字段[" + binding.jsonName() + "]对当前角色受限，无法以脱敏占位值创建记录；"
                        + "请申请明文权限，或先创建再让有权限的角色补录该字段");
                return;
            }
        }
    }

    /**
     * 本次请求里，该实体类是否有敏感字段被提交成掩码（用于决定要不要回表取原值）
     */
    public static boolean hasMaskedInput(Class<?> type) {
        if (!MaskedInputRegistry.hasAny()) {
            return false;
        }
        for (Binding binding : bindingsFor(type)) {
            if (MaskedInputRegistry.isMarked(type, binding.jsonName())) {
                return true;
            }
        }
        return false;
    }
}
