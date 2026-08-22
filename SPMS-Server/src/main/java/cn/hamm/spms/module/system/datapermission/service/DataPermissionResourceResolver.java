package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.enums.DataPermissionAction;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * <h1>数据权限资源标识解析</h1>
 *
 * <p>将业务实体/服务映射为数据权限资源标识（resourceKey）。
 * 资源标识约定为 {@code module.entity} 形式，与 {@code DevDataInitRunner}
 * 中注册的 {@code wms.input}、{@code mes.order} 等资源键保持一致。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionResourceResolver {
    private DataPermissionResourceResolver() {
    }

    /**
     * 从业务 Service 解析数据权限资源标识
     *
     * @param serviceClass 业务 Service 类（如 {@code InputService}）
     * @return 资源标识（如 {@code wms.input}）；无法解析时返回 {@code null}
     */
    public static String resolve(Class<?> serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        String name = serviceClass.getSimpleName();
        if (!name.endsWith("Service")) {
            return null;
        }
        String entityName = name.substring(0, name.length() - "Service".length());

        String module = resolveModuleName(serviceClass);
        if (module == null) {
            return null;
        }
        return module + "." + entityName.toLowerCase(Locale.ROOT);
    }

    /**
     * 从 Service 的包路径解析所属业务模块
     */
    private static String resolveModuleName(Class<?> serviceClass) {
        String packageName = serviceClass.getPackageName();
        int idx = packageName.indexOf("module.");
        if (idx < 0) {
            return null;
        }
        String rest = packageName.substring(idx + "module.".length());
        int end = rest.indexOf('.');
        return end < 0 ? rest : rest.substring(0, end);
    }
}