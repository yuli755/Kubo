package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.config.DataPermissionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * <h1>数据权限运行时句柄</h1>
 *
 * <p>Jackson 序列化器由 Jackson 自己实例化（不是 Spring Bean），无法直接注入依赖，
 * 因此在这里暴露只读静态句柄。仅服务于序列化期的脱敏判定，不要拿它做别的事。</p>
 *
 * <p>启动完成前（容器尚未回调构造）三个句柄可能为 null，此时按「不脱敏但记账」处理，
 * 与 {@link DataPermissionProperties} 的 SHADOW 语义一致。</p>
 *
 * @author Hamm.cn
 */
@Component
public class DataPermissionRuntime {

    private static ColumnMaskingService columnMaskingService;
    private static DataPermissionAudit audit;
    private static DataPermissionProperties properties;

    @Autowired
    public DataPermissionRuntime(
            ColumnMaskingService columnMaskingService,
            DataPermissionAudit audit,
            DataPermissionProperties properties) {
        DataPermissionRuntime.columnMaskingService = columnMaskingService;
        DataPermissionRuntime.audit = audit;
        DataPermissionRuntime.properties = properties;
    }

    /**
     * @return 列级脱敏服务，容器未就绪时为 null
     */
    public static ColumnMaskingService maskingService() {
        return columnMaskingService;
    }

    /**
     * @return 审计打点组件，容器未就绪时为 null
     */
    public static DataPermissionAudit audit() {
        return audit;
    }

    /**
     * @return 配置，容器未就绪时为 null
     */
    public static DataPermissionProperties properties() {
        return properties;
    }
}
