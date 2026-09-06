package cn.hamm.spms.module.system.tenant;

import cn.hamm.spms.base.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <h1>用户-租户服务</h1>
 *
 * <p>提供「租户 ID 的服务端可信解析」能力，替代历史上直接采信
 * {@code X-Tenant-Id} 请求头的做法（见《供应链权限治理二次改造审计与实施计划》§2.1 目标一）。</p>
 *
 * <p><b>性能说明</b>：当前每个请求解析租户会走一次 {@code user_tenant} 唯一索引查询，
 * 未加缓存；接入权限规则三级缓存（阶段 P4）时一并纳入缓存与失效范围。</p>
 *
 * @author Hamm.cn
 */
@Slf4j
@Service
public class UserTenantService extends BaseService<UserTenantEntity, UserTenantRepository> {

    /**
     * 查询用户归属的租户 ID 集合
     *
     * @param userId 用户 ID
     * @return 租户 ID 集合，无归属时为空集合
     */
    @Transactional(readOnly = true)
    public Set<Long> tenantIds(long userId) {
        List<UserTenantEntity> relations = repository.findByUserId(userId);
        if (relations == null || relations.isEmpty()) {
            return Set.of();
        }
        return relations.stream()
                .filter(Objects::nonNull)
                .map(UserTenantEntity::getTenantId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 查询用户的默认租户
     *
     * @param userId 用户 ID
     * @return 默认租户 ID；未显式设置时回落到归属集合中的最小 ID，完全无归属返回 null
     */
    @Transactional(readOnly = true)
    public Long defaultTenantId(long userId) {
        UserTenantEntity preferred = repository.findByUserIdAndIsDefaultTrue(userId);
        if (preferred != null && preferred.getTenantId() != null) {
            return preferred.getTenantId();
        }
        return tenantIds(userId).stream().min(Long::compare).orElse(null);
    }

    /**
     * 判断用户是否属于指定租户
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 是否属于
     */
    @Transactional(readOnly = true)
    public boolean isMember(long userId, long tenantId) {
        return tenantIds(userId).contains(tenantId);
    }
}
