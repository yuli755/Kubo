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
     * 按资源与动作获取全部启用的策略（不分字段、不分角色）
     *
     * <p>这是列级脱敏的<b>热路径</b>入口：{@code SensitiveFieldSerializer} 会对
     * 结果集里的<b>每一行、每一个敏感字段</b>各解析一次策略，若每个字段都回表查询，
     * 一千行 × 三个敏感字段就是数千条 SQL，enforce 一开就把列表接口拖死。</p>
     *
     * <p><b>缓存键只允许包含 resourceKey + actionName，绝不能带用户身份</b>：
     * 身份相关（角色命中、优先级选择）必须由调用方在内存里做。
     * 把 {@code matchesSubject(...)} 之类放进 {@code @Cacheable} 方法内部，
     * 就会重现"甲用户的策略被乙用户命中"的串号事故
     * （参见 {@code JpaPermissionRuleProvider} 里的同类教训）。</p>
     *
     * @param resourceKey 数据资源标识（如 channel.salePrice）
     * @param actionName  动作名（如 QUERY）；资源未注册时返回空列表
     * @return 启用中的策略列表，按 priority 降序
     */
    List<DataFieldPolicyEntity> getEnabledPoliciesByResourceAndAction(String resourceKey, String actionName);

    /**
     * 按资源取全部启用策略（跨动作），管理端盘点用
     *
     * <p>与 {@code getEnabledPoliciesByResourceAndAction} 同一份"什么算启用策略"的解释，
     * 只是不按动作过滤 —— 盘点"没人读的策略"必须看到所有动作，包括拼错的那些。
     * 缓存键同样只含资源标识，绝不含用户身份。</p>
     *
     * @param resourceKey 数据资源标识；未注册时返回空列表
     * @return 该资源全部启用中的策略，按 priority 降序
     */
    List<DataFieldPolicyEntity> getEnabledPoliciesByResource(String resourceKey);

    /**
     * 清理指定资源的字段策略缓存
     */
    void clearCache(String resourceKey);

    /**
     * 清理所有字段策略缓存
     */
    void clearAllCache();
}