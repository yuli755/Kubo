package cn.hamm.spms.module.system.datapermission.service;

import cn.hamm.spms.base.BaseService;
import cn.hamm.spms.module.system.datapermission.entity.DataFieldPolicyEntity;
import cn.hamm.spms.module.system.datapermission.entity.DataResourceEntity;
import cn.hamm.spms.module.system.datapermission.event.DataPermissionEventPublisher;
import cn.hamm.spms.module.system.datapermission.repository.DataFieldPolicyRepository;
import cn.hamm.spms.module.system.datapermission.repository.DataResourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import cn.hamm.spms.module.system.datapermission.mapper.DataFieldPolicyQueryMapper;

/**
 * <h1>数据字段权限策略管理服务</h1>
 *
 * <p><b>为什么用类内缓存而不是 {@code @Cacheable}：</b>Spring 缓存注解会把本 Bean 变成
 * CGLIB 代理，而框架 {@code RootService#getFirstParameterizedTypeClass} 直接
 * {@code (ParameterizedType) getClass().getGenericSuperclass()} —— 代理类的父类是裸类，
 * 强转直接 ClassCastException，且 CurdService 的 add/update/delete 全是 final 无法绕开。
 * 也就是说"缓存注解 + CurdService"组合下所有 CRUD 写入必炸。缓存失效路径本来就是
 * {@code DataPermissionEventListener} 收到 Redis 广播后调 {@code clearCache/clearAllCache}
 * （每个节点各自清），换成类内 Map 后语义完全一致。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
@Profile("!test")
public class DataFieldPolicyService extends BaseService<DataFieldPolicyEntity, DataFieldPolicyRepository> implements DataFieldPolicyProvider {
    private final DataPermissionEventPublisher eventPublisher;
    private final DataResourceRepository resourceRepository;

    /**
     * 带资源 join 的脱敏策略查询（语句在 mapper XML 里；本服务用它取前端动态脱敏规则）
     */
    private final DataFieldPolicyQueryMapper queryMapper;

    /**
     * 类内读缓存：key 与原 Spring 缓存键一致（{@code resourceKey:fieldName:roleCode}、
     * {@code list:resourceKey:action}、{@code all:resourceKey}）。空列表同样缓存
     * （"这个资源压根没配策略"是最常见的情况）。
     */
    private final ConcurrentHashMap<String, List<DataFieldPolicyEntity>> policyCache = new ConcurrentHashMap<>();

    public DataFieldPolicyService(DataPermissionEventPublisher eventPublisher, DataResourceRepository resourceRepository,
                                 DataFieldPolicyQueryMapper queryMapper) {
        this.eventPublisher = eventPublisher;
        this.resourceRepository = resourceRepository;
        this.queryMapper = queryMapper;
    }

    /**
     * 根据资源键、字段名、角色编码获取启用的脱敏策略
     *
     * @param resourceKey 数据资源标识（如 wms.input）
     * @param fieldName   字段名
     * @param roleCode    角色编码
     * @return 策略列表（按优先级降序）
     */
    @Override
    public List<DataFieldPolicyEntity> getPoliciesByResourceFieldRole(
            String resourceKey, String fieldName, String roleCode) {
        return policyCache.computeIfAbsent(resourceKey + ":" + fieldName + ":" + roleCode,
                k -> queryMapper.findByResourceKeyAndFieldNameAndRoleCodeAndEnabledTrue(
                        resourceKey, fieldName, roleCode));
    }

    /**
     * 按资源 / 动作取全部启用策略（列级脱敏热路径）
     *
     * <p>缓存键刻意只由 {@code resourceKey} + {@code actionName} 组成：资源 id 解析、
     * 角色命中、优先级选择里，前两项可以缓存，后两项不行。空列表同样缓存
     * （"这个资源压根没配策略"是最常见的情况），策略新增时由
     * {@code publishFieldPolicyEvent} → {@code clearAllCache()} 失效，不会读到脏数据。</p>
     */
    @Override
    public List<DataFieldPolicyEntity> getEnabledPoliciesByResourceAndAction(String resourceKey, String actionName) {
        return policyCache.computeIfAbsent("list:" + resourceKey + ":" + actionName, k -> {
            DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
            if (resource == null || resource.getId() == null) {
                return Collections.emptyList();
            }
            List<DataFieldPolicyEntity> policies = repository.findByResourceIdAndActionNameAndEnabledTrueOrderByPriorityDesc(
                    resource.getId(), actionName);
            return policies != null ? policies : Collections.emptyList();
        });
    }

    /**
     * 按资源取全部启用策略（跨动作）
     *
     * <p>缓存键用独立前缀 {@code all:}，与 {@code list:<resourceKey>:<action>} 不冲突；
     * 失效路径与它们相同（新增/修改/删除 → {@code clearAllCache()}），
     * 所以盘点结果不会比读侧遮蔽更"陈旧"——两边看到的是同一份缓存语义。</p>
     */
    @Override
    public List<DataFieldPolicyEntity> getEnabledPoliciesByResource(String resourceKey) {
        return policyCache.computeIfAbsent("all:" + resourceKey, k -> {
            DataResourceEntity resource = resourceRepository.getByResourceKey(resourceKey);
            if (resource == null || resource.getId() == null) {
                return Collections.emptyList();
            }
            List<DataFieldPolicyEntity> policies = repository.findByResourceIdAndEnabledTrueOrderByPriorityDesc(resource.getId());
            return policies != null ? policies : Collections.emptyList();
        });
    }

    @Override
    protected void afterAppAdd(long id, DataFieldPolicyEntity entity) {
        super.afterAppAdd(id, entity);
        publishFieldPolicyEvent(entity);
    }

    @Override
    protected void afterAppUpdate(long id, DataFieldPolicyEntity entity) {
        super.afterAppUpdate(id, entity);
        publishFieldPolicyEvent(entity);
    }

    @Override
    protected void beforeDelete(DataFieldPolicyEntity entity) {
        super.beforeDelete(entity);
        // 在删除前发布事件
        publishFieldPolicyEvent(entity);
    }

    private void publishFieldPolicyEvent(DataFieldPolicyEntity policy) {
        String resourceKey = getResourceKey(policy.getResourceId());
        if (resourceKey != null) {
            eventPublisher.publishFieldPolicyChanged(resourceKey);
            log.info("发布字段策略变更事件: resourceKey={}", resourceKey);
        }
    }

    private String getResourceKey(Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        DataResourceEntity resource = resourceRepository.selectById(resourceId);
        return resource != null ? resource.getResourceKey() : null;
    }

    /**
     * 清理指定资源的字段策略缓存（原实现即 allEntries 全清，这里保持一致）
     */
    public void clearCache(String resourceKey) {
        policyCache.clear();
        log.debug("清理字段脱敏策略缓存: {}", resourceKey);
    }

    /**
     * 清理所有字段策略缓存
     */
    public void clearAllCache() {
        policyCache.clear();
        log.debug("清理所有字段脱敏策略缓存");
    }

    /**
     * 某资源某字段是否已登记脱敏策略（初始化幂等守卫专用，理由见
     * {@code DataFieldPolicyRepository#existsByResourceIdAndFieldName}）
     */
    public boolean existsPolicy(Long resourceId, String fieldName) {
        return repository.existsByResourceIdAndFieldName(resourceId, fieldName);
    }
}
