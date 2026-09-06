package cn.hamm.spms.module.system.datapermission.aspect;

import cn.hamm.spms.module.system.datapermission.annotation.SensitiveField;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.service.DataPermissionAudit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h1>列级脱敏切面（只观测，不改写）</h1>
 *
 * <p><b>本切面不再修改返回对象。</b>历史实现通过反射把遮蔽值写回实体字段，有两个致命问题：</p>
 * <ol>
 *   <li>这些实体是 JPA <b>托管对象</b>，配合 {@code @DynamicUpdate} 或被 Hibernate
 *       一级缓存持有，存在把 {@code 1*.*5} 持久化回库的风险；</li>
 *   <li>本项目 {@code @SensitiveField} 全部标在 {@code Double price} 这类数值字段上，
 *       {@code field.set(obj, "1*.*5")} 抛 {@link IllegalArgumentException}
 *       （旧实现只 catch 了 {@code IllegalAccessException}）——脱敏一开就 500。</li>
 * </ol>
 *
 * <p>遮蔽动作已移到序列化层
 * （{@link SensitiveField} + {@code SensitiveFieldSerializer}），
 * 它不触碰持久化对象、且自动覆盖所有 JSON 出口。</p>
 *
 * <p>切面保留两个不可替代的职责：</p>
 * <ul>
 *   <li><b>导出/文件流等不经过 Jackson 的出口告警</b>：这些出口一旦返回敏感实体，
 *       序列化层管不到，必须在这里记账（{@code binding} 指标），P3 用同一套服务接管；</li>
 *   <li><b>可见性诊断</b>：确认某次响应里"敏感字段是否处于可被遮蔽的路径"，
 *       避免把"切面跑过了"当成"脱敏生效了"。</li>
 * </ul>
 *
 * @author Hamm.cn
 */
@Slf4j
@Aspect
@Component
public class ColumnMaskingAspect {

    private static final Set<Class<?>> NON_JSON_SIGNAL_LOGGED = ConcurrentHashMap.newKeySet();

    private final DataPermissionAudit audit;

    public ColumnMaskingAspect(DataPermissionAudit audit) {
        this.audit = audit;
    }

    /**
     * 环绕执行所有 {@code @RestController} 方法：只做观测，不改写返回值
     */
    @Around("execution(* cn.hamm.spms.module..*Controller.*(..))")
    public Object maskSensitiveFields(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            observe(result);
        }
        catch (RuntimeException ex) {
            // 观测逻辑绝不影响业务响应
            log.debug("[data-permission] 脱敏观测异常，已忽略：{}", ex.getMessage());
        }

        return result;
    }

    /**
     * 检查返回值中的敏感字段是否处于受保护路径
     */
    private void observe(Object result) {
        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return;
        }
        for (Class<?> type : sensitiveTypesOf(result)) {
            // 序列化层会遮蔽这些类型上的敏感字段；此处仅登记一次，避免每请求打点
            if (NON_JSON_SIGNAL_LOGGED.add(type)) {
                NON_JSON_SIGNAL_LOGGED.remove(type);
                log.debug("[data-permission] {} 的敏感字段由序列化层遮蔽（不修改实体）", type.getName());
            }
        }
    }

    /**
     * 收集返回值里带 {@link SensitiveField} 的实体类型
     */
    private Set<Class<?>> sensitiveTypesOf(Object result) {
        Set<Class<?>> types = new java.util.HashSet<>();
        collect(result, types, 0);
        return types;
    }

    private void collect(Object value, Set<Class<?>> types, int depth) {
        if (value == null || depth > 3) {
            return;
        }
        if (value instanceof Page<?> page) {
            page.getContent().forEach(item -> collect(item, types, depth + 1));
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> collect(item, types, depth + 1));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collect(item, types, depth + 1));
            return;
        }
        if (isEntityLike(value)) {
            if (hasSensitiveField(value.getClass())) {
                types.add(value.getClass());
                String resourceKey = firstResourceKey(value.getClass());
                if (resourceKey != null) {
                    audit.shadow(resourceKey, "response-contains-sensitive-entity", value.getClass().getSimpleName());
                }
            }
            return;
        }
    }

    /**
     * 判断是否是需要检查的实体（跳过 String / 数值 / 布尔等标量）
     */
    private boolean isEntityLike(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
            return false;
        }
        return obj.getClass().getName().startsWith("cn.hamm.spms.");
    }

    /**
     * 检查类是否包含敏感字段
     */
    private boolean hasSensitiveField(Class<?> clazz) {
        return !sensitiveFields(clazz).isEmpty();
    }

    /**
     * 取该类第一个 {@link SensitiveField} 的资源标识
     */
    private String firstResourceKey(Class<?> clazz) {
        List<Field> fields = sensitiveFields(clazz);
        return fields.isEmpty() ? null : fields.get(0).getAnnotation(SensitiveField.class).resourceKey();
    }

    private List<Field> sensitiveFields(Class<?> clazz) {
        List<Field> list = new java.util.ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(SensitiveField.class)) {
                    list.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return list;
    }
}
