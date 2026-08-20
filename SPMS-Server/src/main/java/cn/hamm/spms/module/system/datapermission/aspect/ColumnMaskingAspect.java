package cn.hamm.spms.module.system.datapermission.aspect;

import cn.hamm.airpower.core.Json;
import cn.hamm.spms.module.system.datapermission.context.PermissionContext;
import cn.hamm.spms.module.system.datapermission.context.PermissionContextHolder;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.service.ColumnMaskingService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

/**
 * <h1>列级脱敏 AOP 切面</h1>
 *
 * <p>拦截 Controller 方法返回的 {@link Json} 结果，对带有 {@code @SensitiveField} 注解的实体字段
 * 应用脱敏策略。脱敏规则由 {@code data_field_policy} 表配置。</p>
 *
 * @author Hamm.cn
 */
@Aspect
@Component
public class ColumnMaskingAspect {

    private final ColumnMaskingService columnMaskingService;

    public ColumnMaskingAspect(ColumnMaskingService columnMaskingService) {
        this.columnMaskingService = columnMaskingService;
    }

    /**
     * 拦截所有 Controller 层方法返回的 Json 结果
     */
    @Around("within(cn.hamm.spms.module..*Controller)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        PermissionContext context = PermissionContextHolder.current().orElse(null);
        if (context == null || context.bypassDataPermission()) {
            return result;
        }

        if (result instanceof Json jsonResult) {
            Object data = jsonResult.getData();
            if (data != null) {
                applyMasking(data, context);
            }
        }

        return result;
    }

    /**
     * 递归对数据对象应用脱敏
     */
    private void applyMasking(Object data, PermissionContext context) {
        if (data instanceof Collection<?> collection) {
            for (Object item : collection) {
                applyMaskingOnObject(item, context);
            }
        } else if (data instanceof Map<?, ?> map) {
            if (map.containsKey("list")) {
                Object list = map.get("list");
                if (list instanceof Collection<?> collection) {
                    for (Object item : collection) {
                        applyMaskingOnObject(item, context);
                    }
                }
            } else {
                applyMaskingOnObject(data, context);
            }
        } else {
            applyMaskingOnObject(data, context);
        }
    }

    /**
     * 对单个对象应用脱敏
     */
    private void applyMaskingOnObject(Object obj, PermissionContext context) {
        if (obj == null) {
            return;
        }
        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            cn.hamm.spms.module.system.datapermission.annotation.SensitiveField annotation =
                    field.getAnnotation(cn.hamm.spms.module.system.datapermission.annotation.SensitiveField.class);
            if (annotation == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                String resourceKey = annotation.resourceKey();
                String fieldName = field.getName();

                Object maskedValue = columnMaskingService.maskFieldValue(
                        resourceKey, fieldName, value, context
                );
                if (maskedValue != null && !maskedValue.equals(value)) {
                    field.set(obj, maskedValue);
                }
            } catch (IllegalAccessException ignored) {
                // 跳过无法访问的字段
            }
        }
    }
}