package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;

import java.util.List;

/**
 * <h1>数据字段权限策略提供者接口</h1>
 * <p>统一生产环境和测试环境的实现</p>
 *
 * @author Hamm.cn
 */
public interface DataFieldPolicyProvider {
    /**
     * 根据资源键、字段名、角色编码获取启用的脱敏策略
     *
     * @param resourceKey 数据资源标识（如 wms.input）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表（按优先级降序）
     */
    List<DataFieldPolicyEntity> getPoliciesByResourceFieldRole(String resourceKey, String fieldName, String roleCode);

    /**
     * 清理指定资源的字段策略缓存
     */
    void clearCache(String resourceKey);

    /**
     * 清理所有字段策略缓存
     */
    void clearAllCache();
}